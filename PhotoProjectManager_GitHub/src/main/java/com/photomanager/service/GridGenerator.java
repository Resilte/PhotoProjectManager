package com.photomanager.service;

import com.photomanager.db.DatabaseManager;
import com.photomanager.model.PhotoFile;
import com.photomanager.model.Project;
import com.photomanager.model.StyleFolder;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 九宫格排版生成器（完整重写版）
 *
 * 修复内容：
 * 1. 序号标注：使用 Graphics2D 在 BufferedImage 上预渲染序号，
 *    彻底摆脱对系统 TrueType 字体的依赖，序号一定能显示
 * 2. 横/竖幅识别：按宽高比严格判断，阈值可配置
 * 3. 排版坐标：统一以 PDF 左下角为原点重新核算，横竖分别排版
 * 4. 进度回调：生成过程中通过 Consumer<Integer> 报告 0~100 的进度
 * 5. 卡顿问题：生成本身仍是同步的，由调用方用 SwingWorker 包装（见 GridProgressDialog）
 */
public class GridGenerator {

    /* ========== 常量 ========== */

    // A4 尺寸（pt）
    private static final float A4_W = 595.0f;
    private static final float A4_H = 842.0f;

    // 页边距（pt）
    private static final float MARGIN = 24.0f;

    // 格子间距（pt）
    private static final float GAP = 8.0f;

    // 默认背景色（米白色）
    private static final int[] DEFAULT_BG = {245, 240, 235};

    // 序号字体大小（pt，在 BufferedImage 上绘制时用）
    private static final int SEQ_FONT_SIZE = 28;

    // 横竖判断阈值
    private static final float LANDSCAPE_RATIO = 1.05f;   // width/height > 1.05 → 横幅
    private static final float PORTRAIT_RATIO  = 0.95f;   // width/height < 0.95 → 竖幅

    // 从文件名末尾提取序号的正则：最后一个非数字字符后的数字
    // 支持 "xxx_1.jpg" / "xxx1.jpg" / "xxx 1.jpg" 等格式
    private static final Pattern SEQ_PATTERN = Pattern.compile("(\\d+)(?=\\.[^.]+$)");

    /* ========== 序号提取 ========== */

    /**
     * 从文件名提取序号（如 "金色华尔兹_杂志风_1.jpg" → "1"）
     * 如果提取失败返回空字符串
     */
    public static String extractSequenceNumber(String fileName) {
        if (fileName == null || fileName.isEmpty()) return "";
        // 先去掉路径
        String name = new File(fileName).getName();
        Matcher m = SEQ_PATTERN.matcher(name);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    /* ========== 入口方法 ========== */

    /**
     * 生成九宫格 PDF（完整版，支持进度回调）
     *
     * @param projectRootPath  项目根目录
     * @param backgroundColor  RGB 背景色，null 时使用默认米白色
     * @param backgroundImagePath  背景图片路径，null 时使用纯色背景
     * @param progressCallback  进度回调，接收 0~100 的整数，null 时不回调
     * @return 生成的 selection 目录路径
     */
    public static String generateGridPdf(String projectRootPath,
                                         int[] backgroundColor,
                                         String backgroundImagePath,
                                         Consumer<Integer> progressCallback) throws IOException {

        DatabaseManager db = DatabaseManager.getInstance();
        Project project = null;
        for (Project p : db.getAllProjects()) {
            if (p.getRootPath().equals(projectRootPath)) {
                project = p;
                break;
            }
        }
        if (project == null) {
            throw new IOException("未在数据库中找到该项目，请先导入项目。");
        }

        File rootDir = new File(projectRootPath);
        File selectionDir = new File(rootDir, "selection");
        if (!selectionDir.exists()) selectionDir.mkdirs();

        // 背景色
        int bgR = DEFAULT_BG[0], bgG = DEFAULT_BG[1], bgB = DEFAULT_BG[2];
        if (backgroundColor != null && backgroundColor.length >= 3) {
            bgR = backgroundColor[0];
            bgG = backgroundColor[1];
            bgB = backgroundColor[2];
        }

        // 预加载背景图片
        BufferedImage bgImage = null;
        if (backgroundImagePath != null && !backgroundImagePath.isEmpty()) {
            File bgFile = new File(backgroundImagePath);
            if (bgFile.exists()) {
                try {
                    BufferedImage raw = ImageIO.read(bgFile);
                    if (raw != null) {
                        bgImage = scaleToFill(raw, (int) A4_W, (int) A4_H);
                    }
                } catch (IOException e) {
                    System.err.println("背景图片加载失败: " + e.getMessage());
                }
            }
        }

        List<StyleFolder> styleFolders = db.getStyleFolders(project.getId());

        // 先计算总照片数（用于进度计算）
        int totalPhotos = 0;
        for (StyleFolder sf : styleFolders) {
            totalPhotos += db.getPhotoFiles(sf.getId()).size();
        }

        int processedPhotos = 0;

        for (StyleFolder sf : styleFolders) {
            List<PhotoFile> photos = db.getPhotoFiles(sf.getId());
            if (photos.isEmpty()) continue;

            // 按横竖分组
            List<PhotoFile> landscape = new ArrayList<>();
            List<PhotoFile> portrait  = new ArrayList<>();

            for (PhotoFile pf : photos) {
                BufferedImage img = safeReadImage(pf.getFilePath());
                if (img == null) continue;
                float ratio = (float) img.getWidth() / img.getHeight();
                if (ratio >= LANDSCAPE_RATIO) {
                    landscape.add(pf);
                } else {
                    portrait.add(pf);
                }
            }

            File styleOutDir = new File(selectionDir, sf.getStyleName());
            if (!styleOutDir.exists()) styleOutDir.mkdirs();

            // 生成横幅 PDF（每页3张，纵向排列）
            if (!landscape.isEmpty()) {
                String outPath = new File(styleOutDir,
                        sf.getStyleName() + "_横幅.pdf").getAbsolutePath();
                processedPhotos = generatePdfPage(
                        landscape, outPath, 3,
                        bgR, bgG, bgB, bgImage,
                        true,  // isLandscapeLayout
                        processedPhotos, totalPhotos, progressCallback);
            }

            // 生成竖幅 PDF（每页9张，3×3网格）
            if (!portrait.isEmpty()) {
                String outPath = new File(styleOutDir,
                        sf.getStyleName() + "_竖幅.pdf").getAbsolutePath();
                processedPhotos = generatePdfPage(
                        portrait, outPath, 9,
                        bgR, bgG, bgB, bgImage,
                        false, // isPortraitLayout
                        processedPhotos, totalPhotos, progressCallback);
            }
        }

        // 确保进度走到 100
        if (progressCallback != null) {
            progressCallback.accept(100);
        }

        return selectionDir.getAbsolutePath();
    }

    /**
     * 无进度回调的简化入口（向后兼容）
     */
    public static String generateGridPdf(String projectRootPath,
                                         int[] backgroundColor,
                                         String backgroundImagePath) throws IOException {
        return generateGridPdf(projectRootPath, backgroundColor, backgroundImagePath, null);
    }

    /* ========== 核心：生成单个 PDF ========== */

    /**
     * 生成 PDF（支持横幅/竖幅两种排版）
     *
     * @return 处理完的照片数（用于进度累计）
     */
    private static int generatePdfPage(List<PhotoFile> photos,
                                      String outputPath,
                                      int perPage,
                                      int bgR, int bgG, int bgB,
                                      BufferedImage bgImage,
                                      boolean isLandscapeLayout,
                                      int processedSoFar,
                                      int totalPhotos,
                                      Consumer<Integer> progressCallback) throws IOException {

        // 将背景图转为临时 PNG 文件（供 PDFBox 读取）
        File tmpBgFile = null;
        if (bgImage != null) {
            tmpBgFile = File.createTempFile("grid_bg_", ".png");
            tmpBgFile.deleteOnExit();
            ImageIO.write(bgImage, "png", tmpBgFile);
        }

        try (PDDocument document = new PDDocument()) {

            PDImageXObject bgPdImage = null;
            if (tmpBgFile != null) {
                bgPdImage = PDImageXObject.createFromFile(tmpBgFile.getAbsolutePath(), document);
            }

            int totalPages = (int) Math.ceil((double) photos.size() / perPage);

            for (int pageIdx = 0; pageIdx < totalPages; pageIdx++) {

                PDPage page = new PDPage(new PDRectangle(A4_W, A4_H));
                document.addPage(page);

                try (PDPageContentStream cs = new PDPageContentStream(document, page)) {

                    // —— 1. 绘制背景 ——
                    if (bgPdImage != null) {
                        // 先绘制背景图片（整页覆盖）
                        cs.drawImage(bgPdImage, 0, 0, A4_W, A4_H);
                        // 半透明白色底板：用 PDExtendedGraphicsState 正确设置 alpha
                        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                        gs.setNonStrokingAlphaConstant(0.60f);
                        cs.setGraphicsStateParameters(gs);
                        cs.setNonStrokingColor(1f, 1f, 1f); // 白色，透明度由 gs 控制
                        cs.addRect(MARGIN, MARGIN,
                                A4_W - 2 * MARGIN, A4_H - 2 * MARGIN);
                        cs.fill();
                        // 恢复不透明，避免影响后续照片绘制
                        PDExtendedGraphicsState gsOpaque = new PDExtendedGraphicsState();
                        gsOpaque.setNonStrokingAlphaConstant(1.0f);
                        cs.setGraphicsStateParameters(gsOpaque);
                    } else {
                        // 纯色背景：直接填充中间区域
                        cs.setNonStrokingColor(bgR / 255f, bgG / 255f, bgB / 255f);
                        cs.addRect(MARGIN, MARGIN,
                                A4_W - 2 * MARGIN, A4_H - 2 * MARGIN);
                        cs.fill();
                    }

                    // —— 2. 绘制照片 ——
                    if (isLandscapeLayout) {
                        drawLandscapePage(cs, document, photos, pageIdx, perPage,
                                bgR, bgG, bgB);
                    } else {
                        drawPortraitPage(cs, document, photos, pageIdx, perPage,
                                bgR, bgG, bgB);
                    }
                }
            }

            document.save(outputPath);
        } finally {
            if (tmpBgFile != null && tmpBgFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmpBgFile.delete();
            }
        }

        // 更新进度（本组照片全部处理完）
        int newProcessed = processedSoFar + photos.size();
        if (progressCallback != null && totalPhotos > 0) {
            int pct = Math.min(100, (newProcessed * 100) / totalPhotos);
            progressCallback.accept(pct);
        }

        return newProcessed;
    }

    /* ========== 横幅排版：每页3张，纵向排列 ========== */

    private static void drawLandscapePage(PDPageContentStream cs,
                                          PDDocument document,
                                          List<PhotoFile> photos,
                                          int pageIdx,
                                          int perPage,
                                          int bgR, int bgG, int bgB) throws IOException {

        // 可用区域
        float availW = A4_W - 2 * MARGIN;
        float availH = A4_H - 2 * MARGIN;
        float cellH  = (availH - (perPage - 1) * GAP) / perPage;
        float cellW  = availW;  // 横幅：整页宽度

        for (int i = 0; i < perPage; i++) {
            int idx = pageIdx * perPage + i;
            if (idx >= photos.size()) break;

            // PDF 坐标：左下角为原点，从上往下排
            float x = MARGIN;
            float y = A4_H - MARGIN - (i + 1) * cellH - i * GAP;

            PhotoFile pf = photos.get(idx);
            BufferedImage src = safeReadImage(pf.getFilePath());
            if (src == null) continue;

            // 带序号的预处理图片
            BufferedImage labeled = drawSequenceLabel(src, pf.getFileName());

            // 计算绘制尺寸（等比缩放，居中）
            float imgRatio  = (float) labeled.getWidth() / labeled.getHeight();
            float cellRatio = cellW / cellH;
            float drawW, drawH, drawX, drawY;

            if (imgRatio > cellRatio) {
                // 图片更宽 → 按宽度适配
                drawW = cellW;
                drawH = cellW / imgRatio;
                drawX = x;
                drawY = y + (cellH - drawH) / 2;
            } else {
                // 图片更高 → 按高度适配
                drawH = cellH;
                drawW = cellH * imgRatio;
                drawX = x + (cellW - drawW) / 2;
                drawY = y;
            }

            PDImageXObject pdImg = imageToPdImage(labeled, document);
            cs.drawImage(pdImg, drawX, drawY, drawW, drawH);
        }
    }

    /* ========== 竖幅排版：每页9张，3×3 网格 ========== */

    private static void drawPortraitPage(PDPageContentStream cs,
                                         PDDocument document,
                                         List<PhotoFile> photos,
                                         int pageIdx,
                                         int perPage,
                                         int bgR, int bgG, int bgB) throws IOException {

        int cols = 3;
        int rows = 3;
        float availW = A4_W - 2 * MARGIN;
        float availH = A4_H - 2 * MARGIN;
        float cellW = (availW - (cols - 1) * GAP) / cols;
        float cellH = (availH - (rows - 1) * GAP) / rows;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int i = row * cols + col;
                int idx = pageIdx * perPage + i;
                if (idx >= photos.size()) break;

                // PDF 坐标：左下角为原点
                // row=0 是最下面一行，row=2 是最上面一行
                float x = MARGIN + col * (cellW + GAP);
                float y = MARGIN + (rows - 1 - row) * (cellH + GAP);

                PhotoFile pf = photos.get(idx);
                BufferedImage src = safeReadImage(pf.getFilePath());
                if (src == null) continue;

                BufferedImage labeled = drawSequenceLabel(src, pf.getFileName());

                float imgRatio  = (float) labeled.getWidth() / labeled.getHeight();
                float cellRatio = cellW / cellH;
                float drawW, drawH, drawX, drawY;

                if (imgRatio > cellRatio) {
                    drawW = cellW;
                    drawH = cellW / imgRatio;
                    drawX = x;
                    drawY = y + (cellH - drawH) / 2;
                } else {
                    drawH = cellH;
                    drawW = cellH * imgRatio;
                    drawX = x + (cellW - drawW) / 2;
                    drawY = y;
                }

                PDImageXObject pdImg = imageToPdImage(labeled, document);
                cs.drawImage(pdImg, drawX, drawY, drawW, drawH);
            }
        }
    }

    /* ========== 序号标注：在 BufferedImage 上用 Graphics2D 绘制 ========== */

    /**
     * 在图片右下角绘制序号标签，返回新的 BufferedImage
     */
    private static BufferedImage drawSequenceLabel(BufferedImage src, String fileName) {
        String seq = extractSequenceNumber(fileName);
        if (seq.isEmpty()) {
            return src; // 无序号，返回原图
        }

        // 在内存中缩放原图到合理尺寸（避免超大图占用太多内存）
        int maxDim = 1800;
        BufferedImage workImg;
        if (src.getWidth() > maxDim || src.getHeight() > maxDim) {
            float scale = maxDim / (float) Math.max(src.getWidth(), src.getHeight());
            int w = Math.round(src.getWidth() * scale);
            int h = Math.round(src.getHeight() * scale);
            BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();
            workImg = scaled;
        } else {
            // 原图复制一份（避免修改原始 BufferedImage）
            workImg = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = workImg.createGraphics();
            g.drawImage(src, 0, 0, null);
            g.dispose();
        }

        // 在 workImg 右下角绘制序号
        Graphics2D g2 = workImg.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 字号：按图片宽度的 1/12 计算，最小 16，最大 72
        int fontSize = Math.max(16, Math.min(72, workImg.getWidth() / 12));
        g2.setFont(new Font("SansSerif", Font.BOLD, fontSize));

        FontMetrics fm = g2.getFontMetrics();
        int textW = fm.stringWidth(seq);
        int textH = fm.getHeight();

        // 标签尺寸和位置（右下角，留 8px 边距）
        int padding = Math.max(6, fontSize / 4);
        int labelW = textW + padding * 2;
        int labelH = textH + padding;
        int labelX = workImg.getWidth() - labelW - 8;
        int labelY = workImg.getHeight() - labelH - 8;

        // 半透明黑色圆角背景
        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRoundRect(labelX, labelY, labelW, labelH, padding, padding);

        // 白色文字
        g2.setColor(Color.WHITE);
        g2.drawString(seq, labelX + padding, labelY + padding + fm.getAscent());

        g2.dispose();

        return workImg;
    }

    /* ========== 工具方法 ========== */

    /**
     * 将 BufferedImage 转为 PDFBox 可用的 PDImageXObject
     * （通过内存中的 PNG 编码中转，避免写入磁盘）
     */
    private static PDImageXObject imageToPdImage(BufferedImage img, PDDocument document) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        byte[] pngBytes = baos.toByteArray();
        return PDImageXObject.createFromByteArray(document, pngBytes, "photo_" + System.nanoTime());
    }

    /**
     * 安全读取图片（返回 null 而不是抛异常）
     */
    private static BufferedImage safeReadImage(String path) {
        if (path == null) return null;
        try {
            BufferedImage img = ImageIO.read(new File(path));
            if (img == null) return null;
            // EXIF 方向纠正
            img = correctExifOrientation(path, img);
            return img;
        } catch (IOException e) {
            System.err.println("读取图片失败: " + path + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * 根据 EXIF Orientation 标签旋转图片
     */
    private static BufferedImage correctExifOrientation(String imagePath, BufferedImage img) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new File(imagePath));
            ExifIFD0Directory exifDir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exifDir == null) return img;
            int orientation = exifDir.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            if (orientation <= 1) return img; // 1 = 正常

            int angle = 0;
            boolean flipH = false;
            switch (orientation) {
                case 2: flipH = true; break;
                case 3: angle = 180; break;
                case 4: flipH = true; angle = 180; break;
                case 5: flipH = true; angle = 270; break;
                case 6: angle = 90; break;
                case 7: flipH = true; angle = 90; break;
                case 8: angle = 270; break;
                default: return img;
            }

            int w = img.getWidth();
            int h = img.getHeight();
            int newW = (angle == 90 || angle == 270) ? h : w;
            int newH = (angle == 90 || angle == 270) ? w : h;

            BufferedImage rotated = new BufferedImage(newW, newH, img.getType());
            Graphics2D g = rotated.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(newW / 2.0, newH / 2.0);
            if (flipH) g.scale(-1, 1);
            g.rotate(Math.toRadians(angle));
            g.translate(-w / 2.0, -h / 2.0);
            g.drawImage(img, 0, 0, null);
            g.dispose();
            return rotated;
        } catch (Exception e) {
            // EXIF 读取失败，返回原图
            return img;
        }
    }

    /**
     * 将图片缩放为 cover 模式（填满目标尺寸，多余部分居中裁切）
     */
    private static BufferedImage scaleToFill(BufferedImage src, int targetW, int targetH) {
        if (src == null) return null;

        int srcW = src.getWidth();
        int srcH = src.getHeight();
        float srcRatio   = (float) srcW / srcH;
        float targetRatio = (float) targetW / targetH;

        int cropW, cropH, cropX, cropY;

        if (srcRatio > targetRatio) {
            // 源图更宽 → 按高度裁切左右
            cropH = srcH;
            cropW = Math.round(srcH * targetRatio);
        } else {
            // 源图更高 → 按宽度裁切上下
            cropW = srcW;
            cropH = Math.round(srcW / targetRatio);
        }

        cropX = (srcW - cropW) / 2;
        cropY = (srcH - cropH) / 2;

        BufferedImage cropped = src.getSubimage(cropX, cropY, cropW, cropH);

        BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(cropped, 0, 0, targetW, targetH, null);
        g.dispose();

        return scaled;
    }
}
