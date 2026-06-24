package com.photomanager.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * 文件整理工具：
 * 1. 按日期归档 — 在每个风格文件夹下按文件修改日期创建 yyyy-MM-dd 子目录并移入
 * 2. 按风格重命名 — 将风格文件夹内图片重命名为「风格名-序号.扩展名」，按修改时间排序
 */
public class FileOrganizer {

    private static boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
               lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".tiff") ||
               lower.endsWith(".webp") || lower.endsWith(".tif") || lower.endsWith(".raw") ||
               lower.endsWith(".cr2") || lower.endsWith(".nef") || lower.endsWith(".arw") || lower.endsWith(".dng");
    }

    private static LocalDate getFileModifyDate(File file) {
        try {
            Path path = Paths.get(file.getAbsolutePath());
            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            return attr.lastModifiedTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (IOException e) {
            return LocalDate.of(1970, 1, 1);
        }
    }

    /**
     * 按日期归档：每个风格文件夹下按修改日期创建子目录并移入文件。
     */
    public static String organizeByDate(String projectRootPath) throws IOException {
        File root = new File(projectRootPath);
        if (!root.isDirectory()) throw new IOException("Project root not found: " + projectRootPath);

        StringBuilder report = new StringBuilder();
        report.append("=== Date Organization Report ===\n");
        report.append("Project: ").append(root.getName()).append("\n\n");

        int totalMoved = 0;
        File[] styleFolders = root.listFiles(File::isDirectory);
        if (styleFolders == null) return "No style folders found.";

        Arrays.sort(styleFolders, Comparator.comparing(File::getName));

        for (File styleDir : styleFolders) {
            if (styleDir.getName().startsWith("selection") || styleDir.getName().startsWith(".")) continue;

            String todayDate = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            // 跳过已经是日期目录的子目录
            if (styleDir.getName().matches("\\d{4}-\\d{2}-\\d{2}")) continue;

            report.append("Style: ").append(styleDir.getName()).append("\n");

            File[] files = styleDir.listFiles(f -> f.isFile() && isImageFile(f.getName()));
            if (files == null || files.length == 0) continue;

            Map<LocalDate, List<File>> dateGroups = new TreeMap<>();
            for (File file : files) {
                dateGroups.computeIfAbsent(getFileModifyDate(file), k -> new ArrayList<>()).add(file);
            }

            for (Map.Entry<LocalDate, List<File>> entry : dateGroups.entrySet()) {
                String dateDirName = entry.getKey().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                File dateDir = new File(styleDir, dateDirName);
                if (!dateDir.exists()) dateDir.mkdir();

                for (File file : entry.getValue()) {
                    File dest = new File(dateDir, file.getName());
                    if (!dest.exists()) {
                        Files.move(file.toPath(), dest.toPath());
                        report.append("  [").append(dateDirName).append("] ").append(file.getName()).append("\n");
                        totalMoved++;
                    }
                }
            }
        }

        report.append("\nTotal files organized: ").append(totalMoved).append("\n");
        return report.toString();
    }

    /**
     * 按风格重命名：将每个风格文件夹下的图片重命名为「风格名-序号.扩展名」
     * 按文件修改时间排序，序号从 001 开始三位置零填充。
     * 已按此格式命名的文件不会被重复处理。
     */
    public static String renameByStyle(String projectRootPath) throws IOException {
        File root = new File(projectRootPath);
        if (!root.isDirectory()) throw new IOException("Project root not found: " + projectRootPath);

        StringBuilder report = new StringBuilder();
        report.append("=== Style Rename Report ===\n");
        report.append("Project: ").append(root.getName()).append("\n\n");

        int totalRenamed = 0;
        File[] styleFolders = root.listFiles(File::isDirectory);
        if (styleFolders == null) return "No style folders found.";

        Arrays.sort(styleFolders, Comparator.comparing(File::getName));

        for (File styleDir : styleFolders) {
            String styleName = styleDir.getName();
            if (styleName.startsWith("selection") || styleName.startsWith(".")) continue;
            if (styleName.matches("\\d{4}-\\d{2}-\\d{2}")) continue;

            report.append("Style: ").append(styleName).append("\n");

            File[] files = styleDir.listFiles(f -> f.isFile() && isImageFile(f.getName()));
            if (files == null || files.length == 0) continue;

            // 按修改时间排序
            Arrays.sort(files, Comparator.comparing(FileOrganizer::getFileModifyDate));

            int index = 1;
            for (File file : files) {
                String name = file.getName();
                String ext = "";
                int dotIdx = name.lastIndexOf('.');
                if (dotIdx > 0) ext = name.substring(dotIdx).toLowerCase();
                else ext = ".jpg";

                // 跳过已按此格式命名的文件（风格名-NNN.ext）
                String expectedNewName = styleName + "-" + String.format("%03d", index) + ext;
                if (name.equals(expectedNewName)) { index++; continue; }

                File dest = new File(styleDir, expectedNewName);
                // 处理重名冲突
                int conflict = 1;
                while (dest.exists()) {
                    expectedNewName = styleName + "-" + String.format("%03d", index) + "_" + conflict + ext;
                    dest = new File(styleDir, expectedNewName);
                    conflict++;
                }

                Files.move(file.toPath(), dest.toPath());
                report.append("  ").append(name).append("  ->  ").append(expectedNewName).append("\n");
                totalRenamed++;
                index++;
            }
        }

        report.append("\nTotal files renamed: ").append(totalRenamed).append("\n");
        return report.toString();
    }
}
