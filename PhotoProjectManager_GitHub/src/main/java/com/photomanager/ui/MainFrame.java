package com.photomanager.ui;

import com.photomanager.db.DatabaseManager;
import com.photomanager.model.PhotoFile;
import com.photomanager.model.Project;
import com.photomanager.model.Project.Status;
import com.photomanager.model.StyleFolder;
import com.photomanager.service.*;
import com.photomanager.util.FileUtil;
import com.photomanager.util.I18n;
import com.formdev.flatlaf.*;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;

public class MainFrame extends JFrame {
    private final DatabaseManager db;
    private final ProjectService projectService;
    private final BaiduNetdiskService baiduService;

    private JTextField searchField;
    private JTextField dateFromField;
    private JTextField dateToField;
    private JTable fileTable;
    private DefaultTableModel fileTableModel;
    private JTree projectTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private Project currentProject;
    private List<Project> currentProjectList;
    private JSplitPane splitPane;

    // 工期排单面板
    private SchedulePanel schedulePanel;
    // 主内容区 tab
    private JTabbedPane mainTabs;

    // 背景图片
    private BufferedImage backgroundImage;
    private JPanel rootContentPane;

    // 应用配置
    private final AppConfig appConfig;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final double[] TABLE_COL_RATIOS = {0.35, 0.12, 0.20, 0.33};

    public MainFrame() {
        String appDir = System.getProperty("user.dir");
        File dbDir = new File(appDir, "data");
        if (!dbDir.exists()) dbDir.mkdir();
        String dbPath = new File(dbDir, "photomanager.db").getAbsolutePath();
        this.db = DatabaseManager.getInstance(dbPath);
        this.projectService = new ProjectService(db);
        this.baiduService = new BaiduNetdiskService();
        this.appConfig = AppConfig.getInstance();

        // 应用暗色模式
        applyDarkMode();

        loadBackgroundImage();
        initUI();
        loadProjects();
    }

    // ========== 暗色模式 ==========

    private void applyDarkMode() {
        try {
            if (appConfig.isDarkMode()) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
        } catch (Exception e) {
            // FlatLaf 不可用时回退到系统默认
            System.err.println("FlatLaf 初始化失败: " + e.getMessage());
        }
    }

    private void toggleDarkMode() {
        appConfig.setDarkMode(!appConfig.isDarkMode());
        appConfig.save();
        JOptionPane.showMessageDialog(this, "主题将在下次启动时生效。", "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    // ========== 背景图片 ==========

    private void loadBackgroundImage() {
        if (appConfig.hasBackgroundImage()) {
            try {
                BufferedImage raw = ImageIO.read(new File(appConfig.getBackgroundImagePath()));
                if (raw != null) {
                    scaleBackgroundImage(raw);
                }
            } catch (IOException e) {
                backgroundImage = null;
                System.err.println("加载背景图片失败: " + e.getMessage());
            }
        }
    }

    /** 缩放背景图片以适应当前窗口，保持窗口尺寸缓存 */
    private void scaleBackgroundImage(BufferedImage raw) {
        Dimension size = getSize();
        if (size.width <= 0 || size.height <= 0) {
            size = new Dimension(1200, 750);
        }
        scaleTo(raw, size.width, size.height);
    }

    private void scaleTo(BufferedImage src, int w, int h) {
        if (w <= 0 || h <= 0) return;
        // 使用 cover 模式：填充整个区域，居中裁切
        float srcRatio = (float) src.getWidth() / src.getHeight();
        float targetRatio = (float) w / h;
        int cropW, cropH, cropX, cropY;

        if (srcRatio > targetRatio) {
            cropH = src.getHeight();
            cropW = Math.round(cropH * targetRatio);
            cropX = (src.getWidth() - cropW) / 2;
            cropY = 0;
        } else {
            cropW = src.getWidth();
            cropH = Math.round(cropW / targetRatio);
            cropX = 0;
            cropY = (src.getHeight() - cropH) / 2;
        }

        BufferedImage cropped = src.getSubimage(
                Math.max(0, cropX), Math.max(0, cropY),
                Math.min(cropW, src.getWidth() - Math.max(0, cropX)),
                Math.min(cropH, src.getHeight() - Math.max(0, cropY)));
        backgroundImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = backgroundImage.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(cropped, 0, 0, w, h, null);
        g2.dispose();
    }

    /** 选择背景图片 */
    private void chooseBackgroundImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择窗口背景图片");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "图片文件 (*.jpg, *.jpeg, *.png, *.bmp, *.gif)", "jpg", "jpeg", "png", "bmp", "gif"));
        if (appConfig.hasBackgroundImage()) {
            chooser.setCurrentDirectory(new File(appConfig.getBackgroundImagePath()).getParentFile());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            appConfig.setBackgroundImagePath(path);
            appConfig.save();
            try {
                BufferedImage raw = ImageIO.read(new File(path));
                if (raw != null) {
                    scaleBackgroundImage(raw);
                }
            } catch (IOException e) {
                backgroundImage = null;
            }
            // 刷新背景
            if (rootContentPane != null) {
                rootContentPane.repaint();
            }
        }
    }

    /** 清除背景图片 */
    private void clearBackgroundImage() {
        backgroundImage = null;
        appConfig.setBackgroundImagePath("");
        appConfig.save();
        if (rootContentPane != null) {
            rootContentPane.repaint();
        }
    }

    // ========== UI 初始化 ==========

    private void initUI() {
        setTitle(I18n.get("app.title"));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 750);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(800, 500));
        setJMenuBar(createMenuBar());

        // 根面板 —— 支持背景图片绘制
        rootContentPane = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (backgroundImage != null) {
                    g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
                }
            }
        };

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(createLeftPanel());
        splitPane.setRightComponent(createMainTabs());
        splitPane.setDividerLocation(250);
        splitPane.setResizeWeight(0.21);
        splitPane.setContinuousLayout(true);

        // 让分割面板略微透明以展示背景
        splitPane.setOpaque(false);
        splitPane.setBorder(null);

        rootContentPane.add(splitPane, BorderLayout.CENTER);
        setContentPane(rootContentPane);

        // 窗口大小变化时重新缩放背景
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                adjustTableColumns();
                // 重新缩放背景图片
                if (appConfig.hasBackgroundImage()) {
                    try {
                        BufferedImage raw = ImageIO.read(new File(appConfig.getBackgroundImagePath()));
                        if (raw != null) {
                            scaleTo(raw, getWidth(), getHeight());
                            rootContentPane.repaint();
                        }
                    } catch (IOException ex) {
                        // ignore
                    }
                }
            }
        });
    }

    /** 创建右侧带 Tab 的主内容区 */
    private JTabbedPane createMainTabs() {
        mainTabs = new JTabbedPane();
        mainTabs.setOpaque(appConfig.isDarkMode());

        // Tab 0：文件浏览
        mainTabs.addTab(I18n.get("tab.file_browse"), createRightPanel());

        // Tab 1：工期排单
        schedulePanel = new SchedulePanel(db);
        mainTabs.addTab(I18n.get("tab.schedule"), schedulePanel);

        // Tab 2：压缩包管理
        ArchivePanel archivePanel = new ArchivePanel(db, baiduService);
        mainTabs.addTab(I18n.get("tab.archive"), archivePanel);

        mainTabs.addChangeListener(e -> {
            int idx = mainTabs.getSelectedIndex();
            if (idx == 1) {
                schedulePanel.refresh();
            } else if (idx == 2) {
                archivePanel.refreshList();
            }
        });

        return mainTabs;
    }

    private void adjustTableColumns() {
        if (fileTable == null) return;
        int tableWidth = fileTable.getWidth();
        if (tableWidth <= 0) return;
        for (int i = 0; i < TABLE_COL_RATIOS.length && i < fileTable.getColumnCount(); i++) {
            TableColumn col = fileTable.getColumnModel().getColumn(i);
            col.setPreferredWidth((int) (tableWidth * TABLE_COL_RATIOS[i]));
        }
    }

    private void reloadUIText() {
        setTitle(I18n.get("app.title"));
        setJMenuBar(createMenuBar());
        getContentPane().removeAll();
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(createLeftPanel());
        splitPane.setRightComponent(createMainTabs());
        splitPane.setDividerLocation(250);
        splitPane.setResizeWeight(0.21);
        splitPane.setContinuousLayout(true);
        splitPane.setOpaque(false);
        splitPane.setBorder(null);
        getContentPane().add(splitPane, BorderLayout.CENTER);
        revalidate();
        repaint();
        loadProjects();
        addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) { adjustTableColumns(); }
        });
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // ===== 项目菜单 =====
        JMenu projectMenu = new JMenu(I18n.get("menu.project"));

        JMenuItem addItem = new JMenuItem("导入单个项目...");
        addItem.addActionListener(e -> addProject());
        projectMenu.add(addItem);

        JMenuItem batchImportItem = new JMenuItem("批量导入总文件夹...");
        batchImportItem.setForeground(new Color(40, 100, 200));
        batchImportItem.addActionListener(e -> batchImportProjects());
        projectMenu.add(batchImportItem);

        projectMenu.addSeparator();

        JMenuItem refreshItem = new JMenuItem(I18n.get("menu.project.refresh"));
        refreshItem.addActionListener(e -> refreshCurrentProject());
        projectMenu.add(refreshItem);

        menuBar.add(projectMenu);

        JMenu toolMenu = new JMenu(I18n.get("menu.tools"));
        JMenuItem gridItem = new JMenuItem(I18n.get("menu.tools.grid"));
        gridItem.addActionListener(e -> generateGrid());
        JMenuItem organizeItem = new JMenuItem(I18n.get("menu.tools.organize"));
        organizeItem.addActionListener(e -> organizeFiles());
        JMenuItem compressAll = new JMenuItem(I18n.get("menu.tools.compress_all"));
        compressAll.addActionListener(e -> compressProject());
        JMenuItem compressSel = new JMenuItem(I18n.get("menu.tools.compress_sel"));
        compressSel.addActionListener(e -> compressSelection());
        toolMenu.add(gridItem);
        toolMenu.add(organizeItem);
        toolMenu.addSeparator();
        toolMenu.add(compressAll);
        toolMenu.add(compressSel);
        menuBar.add(toolMenu);

        // ---- 外观菜单（暗色模式 + 背景图片） ----
        JMenu appearanceMenu = new JMenu(I18n.get("menu.appearance"));
        JCheckBoxMenuItem darkModeItem = new JCheckBoxMenuItem(I18n.get("appearance.dark_mode"), appConfig.isDarkMode());
        darkModeItem.addActionListener(e -> {
            appConfig.setDarkMode(darkModeItem.isSelected());
            appConfig.save();
            // 实时切换 FlatLaf 主题
            try {
                if (darkModeItem.isSelected()) {
                    FlatDarkLaf.setup();
                } else {
                    FlatLightLaf.setup();
                }
                SwingUtilities.updateComponentTreeUI(this);
                // 更新分割面板等的不透明度
                splitPane.setOpaque(darkModeItem.isSelected());
                mainTabs.setOpaque(darkModeItem.isSelected());
                // 刷新树以更新颜色
                loadProjects();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "主题切换失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        });
        appearanceMenu.add(darkModeItem);

        JMenuItem bgImageItem = new JMenuItem(I18n.get("appearance.set_bg"));
        bgImageItem.addActionListener(e -> chooseBackgroundImage());
        appearanceMenu.add(bgImageItem);

        JMenuItem clearBgItem = new JMenuItem(I18n.get("appearance.clear_bg"));
        clearBgItem.addActionListener(e -> clearBackgroundImage());
        appearanceMenu.add(clearBgItem);

        menuBar.add(appearanceMenu);

        // ---- 工期排单菜单 ----
        JMenu scheduleMenu = new JMenu(I18n.get("menu.schedule"));
        JMenuItem openSchedule = new JMenuItem(I18n.get("menu.schedule.open"));
        openSchedule.addActionListener(e -> {
            if (mainTabs != null) {
                mainTabs.setSelectedIndex(1);
                schedulePanel.refresh();
            }
        });
        JMenuItem scheduleSettings = new JMenuItem(I18n.get("menu.schedule.settings"));
        scheduleSettings.addActionListener(e -> {
            if (schedulePanel != null) schedulePanel.openSettingsDialogPublic();
        });
        scheduleMenu.add(openSchedule);
        scheduleMenu.add(scheduleSettings);
        menuBar.add(scheduleMenu);

        JMenu baiduMenu = new JMenu(I18n.get("menu.baidu"));
        
        // 配置应用（必须先配置 App Key 和 Secret Key）
        JMenuItem configItem = new JMenuItem(I18n.get("baidu.config"));
        configItem.setForeground(new Color(40, 100, 200));
        configItem.addActionListener(e -> configBaiduApp());
        baiduMenu.add(configItem);
        
        baiduMenu.addSeparator();
        
        JMenuItem loginItem = new JMenuItem(I18n.get("menu.baidu.login"));
        loginItem.addActionListener(e -> loginBaidu());
        JMenuItem tokenItem = new JMenuItem(I18n.get("menu.baidu.token"));
        tokenItem.addActionListener(e -> setTokenManually());
        JMenuItem uploadProj = new JMenuItem(I18n.get("menu.baidu.upload_proj"));
        uploadProj.addActionListener(e -> uploadProject());
        JMenuItem uploadSel = new JMenuItem(I18n.get("menu.baidu.upload_sel"));
        uploadSel.addActionListener(e -> uploadSelection());
        baiduMenu.add(loginItem);
        baiduMenu.add(tokenItem);
        baiduMenu.addSeparator();
        baiduMenu.add(uploadProj);
        baiduMenu.add(uploadSel);
        menuBar.add(baiduMenu);
        JMenu langMenu = new JMenu(I18n.get("menu.language"));
        JMenuItem zhItem = new JMenuItem(I18n.get("menu.lang.zh"));
        zhItem.addActionListener(e -> switchLang("zh_CN"));
        JMenuItem enItem = new JMenuItem(I18n.get("menu.lang.en"));
        enItem.addActionListener(e -> switchLang("en_US"));
        JMenuItem jaItem = new JMenuItem(I18n.get("menu.lang.ja"));
        jaItem.addActionListener(e -> switchLang("ja_JP"));
        langMenu.add(zhItem);
        langMenu.add(enItem);
        langMenu.add(jaItem);
        menuBar.add(langMenu);

        return menuBar;
    }

    private void switchLang(String code) {
        I18n.loadLanguage(code);
        reloadUIText();
    }

    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(I18n.get("panel.projects")));
        panel.setOpaque(false);

        rootNode = new DefaultMutableTreeNode(I18n.get("panel.projects"));
        treeModel = new DefaultTreeModel(rootNode);
        projectTree = new JTree(treeModel);
        projectTree.setRootVisible(true);
        projectTree.setShowsRootHandles(true);
        projectTree.setOpaque(false);

        // 自定义树节点渲染器
        projectTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                    boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                setOpaque(false);
                if (value instanceof DefaultMutableTreeNode) {
                    Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                    if (userObj instanceof Project) {
                        Project p = (Project) userObj;
                        Status st = p.getEffectiveStatus();
                        String dot = getStatusDot(st);
                        setText(dot + " " + p.getName());
                        if (!sel) setForeground(getStatusTextColor(st));
                    }
                }
                return this;
            }

            private String getStatusDot(Status s) {
                switch (s) {
                    case URGENT:  return "\u25CF";
                    case PENDING: return "\u25CF";
                    case IDLE:    return "\u25CF";
                    case DONE:    return "\u25CF";
                    default:      return "\u25CB";
                }
            }

            private Color getStatusTextColor(Status s) {
                switch (s) {
                    case URGENT:  return new Color(200, 40, 40);
                    case PENDING: return new Color(180, 120, 0);
                    case IDLE:    return new Color(30, 100, 200);
                    case DONE:    return new Color(40, 150, 60);
                    default:      return Color.DARK_GRAY;
                }
            }
        });

        projectTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) projectTree.getLastSelectedPathComponent();
            if (node == null) return;
            Object userObj = node.getUserObject();
            if (userObj instanceof Project) {
                loadFileTable((Project) userObj);
                if (mainTabs != null) mainTabs.setSelectedIndex(0);
            } else if (userObj instanceof StyleFolder) {
                StyleFolder sf = (StyleFolder) userObj;
                DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
                if (parent != null && parent.getUserObject() instanceof Project) {
                    loadFileTable((Project) parent.getUserObject(), sf);
                    if (mainTabs != null) mainTabs.setSelectedIndex(0);
                }
            }
        });

        projectTree.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { handleTreeMouseEvent(e); }
            public void mouseReleased(MouseEvent e) { handleTreeMouseEvent(e); }
        });

        JScrollPane treeScroll = new JScrollPane(projectTree);
        treeScroll.setOpaque(false);
        treeScroll.getViewport().setOpaque(false);
        panel.add(treeScroll, BorderLayout.CENTER);
        return panel;
    }

    private void handleTreeMouseEvent(MouseEvent e) {
        int row = projectTree.getRowForLocation(e.getX(), e.getY());
        if (row != -1) {
            projectTree.setSelectionRow(row);
            if (e.isPopupTrigger()) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) projectTree.getLastSelectedPathComponent();
                if (node != null && node.getUserObject() instanceof Project) {
                    showProjectPopup(e.getComponent(), e.getX(), e.getY(), (Project) node.getUserObject());
                }
            }
        }
    }

    private void showProjectPopup(Component invoker, int x, int y, Project project) {
        JPopupMenu popup = new JPopupMenu();

        JMenuItem deleteItem = new JMenuItem(I18n.get("menu.project.delete_record"));
        deleteItem.addActionListener(ev -> deleteProjectRecord(project));
        popup.add(deleteItem);

        popup.addSeparator();

        JMenuItem organizeItem = new JMenuItem(I18n.get("menu.tools.organize"));
        organizeItem.addActionListener(ev -> organizeProjectFiles(project));
        popup.add(organizeItem);

        JMenuItem gridItem = new JMenuItem(I18n.get("menu.tools.grid"));
        gridItem.addActionListener(ev -> generateGrid(project));
        popup.add(gridItem);

        JMenuItem compressItem = new JMenuItem(I18n.get("menu.tools.compress_all"));
        compressItem.addActionListener(ev -> compressProjectAll(project));
        popup.add(compressItem);

        popup.addSeparator();

        // ---- 工期快速操作 ----
        JMenu scheduleMenu = new JMenu("工期操作");
        ScheduleSettings ss = ScheduleSettings.getInstance();

        JMenuItem setDdlItem = new JMenuItem("设置截止日期...");
        setDdlItem.addActionListener(ev -> {
            mainTabs.setSelectedIndex(1);
            schedulePanel.refresh();
            schedulePanel.openDdlDialogForProject(project);
        });
        scheduleMenu.add(setDdlItem);

        scheduleMenu.addSeparator();

        JMenuItem urgentItem = new JMenuItem("紧急（+" + ss.getUrgentDefaultDays() + "天）");
        urgentItem.setForeground(new Color(200, 40, 40));
        urgentItem.addActionListener(ev -> {
            db.updateProjectSchedule(project.getId(), LocalDate.now().plusDays(ss.getUrgentDefaultDays()), null);
            project.setDeadline(LocalDate.now().plusDays(ss.getUrgentDefaultDays()));
            project.setManualStatus(null);
            loadProjects();
        });
        scheduleMenu.add(urgentItem);

        JMenuItem pendingItem = new JMenuItem("暂缓（+" + ss.getPendingDefaultDays() + "天）");
        pendingItem.setForeground(new Color(180, 120, 0));
        pendingItem.addActionListener(ev -> {
            db.updateProjectSchedule(project.getId(), LocalDate.now().plusDays(ss.getPendingDefaultDays()), null);
            project.setDeadline(LocalDate.now().plusDays(ss.getPendingDefaultDays()));
            project.setManualStatus(null);
            loadProjects();
        });
        scheduleMenu.add(pendingItem);

        JMenuItem idleItem = new JMenuItem("闲置（+" + ss.getIdleDefaultDays() + "天）");
        idleItem.setForeground(new Color(30, 100, 200));
        idleItem.addActionListener(ev -> {
            db.updateProjectSchedule(project.getId(), LocalDate.now().plusDays(ss.getIdleDefaultDays()), null);
            project.setDeadline(LocalDate.now().plusDays(ss.getIdleDefaultDays()));
            project.setManualStatus(null);
            loadProjects();
        });
        scheduleMenu.add(idleItem);

        JMenuItem doneItem = new JMenuItem("标记完成");
        doneItem.setForeground(new Color(40, 150, 60));
        doneItem.addActionListener(ev -> {
            db.updateProjectSchedule(project.getId(), project.getDeadline(), Status.DONE);
            project.setManualStatus(Status.DONE);
            loadProjects();
        });
        scheduleMenu.add(doneItem);

        popup.add(scheduleMenu);

        popup.addSeparator();

        JMenuItem refreshItem = new JMenuItem(I18n.get("menu.project.refresh"));
        refreshItem.addActionListener(ev -> refreshProject(project));
        popup.add(refreshItem);

        popup.show(invoker, x, y);
    }

    private void deleteProjectRecord(Project project) {
        int confirm = JOptionPane.showConfirmDialog(this,
                I18n.get("msg.delete_project_confirm") + " " + project.getName(),
                I18n.get("msg.confirm"), JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            db.deleteProject(project.getId());
            loadProjects();
            fileTableModel.setRowCount(0);
            fileTableModel.fireTableDataChanged();
        }
    }

    private void organizeProjectFiles(Project project) {
        int ret = JOptionPane.showConfirmDialog(this, I18n.get("msg.organize_confirm"),
                I18n.get("menu.tools.organize"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ret != JOptionPane.OK_OPTION) return;
        try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            String report = FileOrganizer.renameByStyle(project.getRootPath());
            setCursor(Cursor.getDefaultCursor());
            projectService.refreshProject(project.getId(), project.getRootPath());
            loadProjects();
            JOptionPane.showMessageDialog(this, I18n.get("msg.organize_done") + "\n" + report,
                    I18n.get("msg.done"), JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this, I18n.get("msg.organize_failed") + ": " + ex.getMessage(),
                    I18n.get("msg.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    // ========== 九宫格生成（增强：支持背景图片选择） ==========

    private void generateGrid(Project project) {
        showGridDialog(project);
    }

    private void generateGrid() {
        if (currentProject == null) {
            JOptionPane.showMessageDialog(this, I18n.get("msg.select_project_first"),
                    I18n.get("msg.warning"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        showGridDialog(currentProject);
    }

    /** 九宫格对话框：支持纯色背景 / 图片背景 */
    private void showGridDialog(Project project) {
        JPanel dialogPanel = new JPanel();
        dialogPanel.setLayout(new BoxLayout(dialogPanel, BoxLayout.Y_AXIS));
        dialogPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        // 默认背景信息
        JLabel infoLabel = new JLabel("默认为米白色背景 (RGB: 245, 240, 235)");
        infoLabel.setForeground(Color.GRAY);
        infoLabel.setFont(infoLabel.getFont().deriveFont(11f));
        infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        dialogPanel.add(infoLabel);
        dialogPanel.add(Box.createVerticalStrut(8));

        // ---- 背景图片选择 ----
        JPanel bgImagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        bgImagePanel.setBorder(BorderFactory.createTitledBorder("背景图片（可选）"));
        JLabel bgPathLabel = new JLabel("未选择");
        bgPathLabel.setForeground(Color.GRAY);
        bgPathLabel.setFont(bgPathLabel.getFont().deriveFont(11f));
        bgImagePanel.add(bgPathLabel);

        final String[] selectedBgPath = { null };  // 可变引用

        JButton chooseBgBtn = new JButton("选择背景图片...");
        chooseBgBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("选择九宫格背景图片");
            chooser.setFileFilter(new FileNameExtensionFilter(
                    "图片文件 (*.jpg, *.jpeg, *.png, *.bmp)", "jpg", "jpeg", "png", "bmp"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                selectedBgPath[0] = chooser.getSelectedFile().getAbsolutePath();
                bgPathLabel.setText("已选择: " + chooser.getSelectedFile().getName());
                bgPathLabel.setForeground(new Color(40, 130, 60));
            }
        });
        bgImagePanel.add(chooseBgBtn);

        JButton clearBgBtn = new JButton("清除");
        clearBgBtn.addActionListener(e -> {
            selectedBgPath[0] = null;
            bgPathLabel.setText("未选择");
            bgPathLabel.setForeground(Color.GRAY);
        });
        bgImagePanel.add(clearBgBtn);

        bgImagePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        dialogPanel.add(bgImagePanel);
        dialogPanel.add(Box.createVerticalStrut(8));

        // ---- 纯色背景（仅在没有背景图片时生效） ----
        JPanel colorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        colorPanel.setBorder(BorderFactory.createTitledBorder("纯色背景（RGB）"));
        JTextField rField = new JTextField("245", 3);
        JTextField gField = new JTextField("240", 3);
        JTextField bField = new JTextField("235", 3);
        colorPanel.add(new JLabel("R:"));
        colorPanel.add(rField);
        colorPanel.add(new JLabel("G:"));
        colorPanel.add(gField);
        colorPanel.add(new JLabel("B:"));
        colorPanel.add(bField);
        colorPanel.add(new JLabel("  (图片背景优先)"));
        colorPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        dialogPanel.add(colorPanel);

        int result = JOptionPane.showConfirmDialog(this, dialogPanel,
                "九宫格排版 - " + project.getName(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        try {
            int[] bg = null;
            if (selectedBgPath[0] == null || selectedBgPath[0].isEmpty()) {
                bg = new int[]{
                        Integer.parseInt(rField.getText().trim()),
                        Integer.parseInt(gField.getText().trim()),
                        Integer.parseInt(bField.getText().trim())};
            }

            // 使用进度对话框后台生成九宫格（避免界面卡顿）
            GridProgressDialog progressDialog = new GridProgressDialog(
                    this, project.getRootPath(), bg, selectedBgPath[0]);
            progressDialog.setVisible(true);
            // 生成完毕后刷新项目树
            projectService.refreshProject(project.getId(), project.getRootPath());
            loadProjects();
        } catch (Exception ex) {
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this, I18n.get("msg.failed") + ex.getMessage(),
                    I18n.get("msg.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void compressProjectAll(Project project) {
        try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            String zip = CompressService.compressProject(project.getRootPath());
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this, I18n.get("msg.compress_done") + zip,
                    I18n.get("msg.done"), JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this, I18n.get("msg.compress_failed") + ": " + ex.getMessage(),
                    I18n.get("msg.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshProject(Project project) {
        projectService.refreshProject(project.getId(), project.getRootPath());
        loadProjects();
        loadFileTable(project);
    }

    private void loadProjects() {
        rootNode.removeAllChildren();
        currentProjectList = db.getAllProjects();
        for (Project p : currentProjectList) {
            DefaultMutableTreeNode projectNode = new DefaultMutableTreeNode(p);
            List<StyleFolder> folders = db.getStyleFolders(p.getId());
            if (folders.isEmpty()) {
                // 没有分类时添加占位子节点，使项目节点可展开
                DefaultMutableTreeNode placeholder = new DefaultMutableTreeNode("（暂无分类，右键项目 → 刷新）");
                placeholder.setAllowsChildren(false);
                projectNode.add(placeholder);
            } else {
                for (StyleFolder sf : folders) {
                    projectNode.add(new DefaultMutableTreeNode(sf));
                }
            }
            rootNode.add(projectNode);
        }
        treeModel.reload();

        // 等树渲染完毕后再展开所有行
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < projectTree.getRowCount(); i++) {
                projectTree.expandRow(i);
            }
        });
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(createSearchPanel(), BorderLayout.NORTH);

        fileTableModel = new DefaultTableModel(
                new String[]{I18n.get("col.filename"), I18n.get("col.size"),
                        I18n.get("col.date"), I18n.get("col.path")}, 0) {
            public boolean isCellEditable(int row, int column) { return false; }
        };
        fileTable = new JTable(fileTableModel);
        fileTable.setRowHeight(24);
        fileTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        fileTable.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) showPopup(e); }
            public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showPopup(e); }
            private void showPopup(MouseEvent e) {
                int row = fileTable.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    fileTable.setRowSelectionInterval(row, row);
                    JPopupMenu popup = createFilePopupMenu(row);
                    popup.show(fileTable, e.getX(), e.getY());
                }
            }
        });

        fileTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = fileTable.getSelectedRow();
                    if (row >= 0) {
                        String path = (String) fileTableModel.getValueAt(row, 3);
                        FileUtil.openFile(path);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(fileTable);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) { adjustTableColumns(); }
        });
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        panel.setBorder(BorderFactory.createTitledBorder(I18n.get("panel.search.title")));
        panel.setOpaque(false);

        panel.add(new JLabel(I18n.get("panel.search.label")));
        searchField = new JTextField(18);
        panel.add(searchField);

        JButton searchBtn = new JButton(I18n.get("panel.search.btn"));
        searchBtn.addActionListener(e -> doSearch());
        panel.add(searchBtn);

        panel.add(new JLabel("  " + I18n.get("panel.search.from")));
        dateFromField = new JTextField(10);
        dateFromField.setToolTipText("yyyy-MM-dd");
        panel.add(dateFromField);

        panel.add(new JLabel(I18n.get("panel.search.to")));
        dateToField = new JTextField(10);
        dateToField.setToolTipText("yyyy-MM-dd");
        panel.add(dateToField);

        JButton dateBtn = new JButton(I18n.get("panel.search.filter"));
        dateBtn.addActionListener(e -> doDateFilter());
        panel.add(dateBtn);

        JButton clearBtn = new JButton(I18n.get("panel.search.clear"));
        clearBtn.addActionListener(e -> {
            searchField.setText("");
            dateFromField.setText("");
            dateToField.setText("");
            loadProjects();
        });
        panel.add(clearBtn);
        return panel;
    }

    private JPopupMenu createFilePopupMenu(int row) {
        JPopupMenu popup = new JPopupMenu();
        String fileName = (String) fileTableModel.getValueAt(row, 0);
        String filePath = (String) fileTableModel.getValueAt(row, 3);

        JMenuItem deleteFile = new JMenuItem(I18n.get("popup.delete_file"));
        deleteFile.addActionListener(e -> deleteFileAction(filePath, fileName));
        popup.add(deleteFile);

        JMenuItem renameFile = new JMenuItem(I18n.get("popup.rename_file"));
        renameFile.addActionListener(e -> renameFileAction(filePath, fileName));
        popup.add(renameFile);

        popup.addSeparator();

        JMenuItem copyPath = new JMenuItem(I18n.get("popup.copy_path"));
        copyPath.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(filePath), null);
            JOptionPane.showMessageDialog(this, I18n.get("msg.path_copied"),
                    I18n.get("msg.info"), JOptionPane.INFORMATION_MESSAGE);
        });
        popup.add(copyPath);

        JMenuItem openExplorer = new JMenuItem(I18n.get("popup.open_explorer"));
        openExplorer.addActionListener(e -> FileUtil.openInExplorer(filePath));
        popup.add(openExplorer);

        JMenuItem openFile = new JMenuItem(I18n.get("popup.open_file"));
        openFile.addActionListener(e -> FileUtil.openFile(filePath));
        popup.add(openFile);

        popup.addSeparator();

        JMenu projectMenu = new JMenu(I18n.get("popup.project_tools"));
        JMenuItem organizeItem = new JMenuItem(I18n.get("menu.tools.organize"));
        organizeItem.addActionListener(e -> {
            if (currentProject != null) organizeProjectFiles(currentProject);
        });
        projectMenu.add(organizeItem);
        JMenuItem gridItem = new JMenuItem(I18n.get("menu.tools.grid"));
        gridItem.addActionListener(e -> {
            if (currentProject != null) generateGrid(currentProject);
        });
        projectMenu.add(gridItem);
        JMenuItem compressItem = new JMenuItem(I18n.get("menu.tools.compress_all"));
        compressItem.addActionListener(e -> {
            if (currentProject != null) compressProjectAll(currentProject);
        });
        projectMenu.add(compressItem);
        JMenuItem deleteRecordItem = new JMenuItem(I18n.get("menu.project.delete_record"));
        deleteRecordItem.addActionListener(e -> {
            if (currentProject != null) deleteProjectRecord(currentProject);
        });
        projectMenu.add(deleteRecordItem);
        popup.add(projectMenu);

        return popup;
    }

    private void deleteFileAction(String filePath, String fileName) {
        int confirm = JOptionPane.showConfirmDialog(this,
                I18n.get("msg.delete_file_confirm") + " " + fileName,
                I18n.get("msg.confirm"), JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        java.io.File f = new java.io.File(filePath);
        if (f.delete()) {
            if (currentProject != null) {
                projectService.refreshProject(currentProject.getId(), currentProject.getRootPath());
                loadProjects();
                loadFileTable(currentProject);
            }
        } else {
            JOptionPane.showMessageDialog(this, I18n.get("msg.delete_failed") + ": " + fileName,
                    I18n.get("msg.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void renameFileAction(String filePath, String fileName) {
        java.io.File f = new java.io.File(filePath);
        if (!f.exists()) return;
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) ext = fileName.substring(dot);
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String newName = JOptionPane.showInputDialog(this,
                I18n.get("msg.rename_prompt"), base);
        if (newName == null || newName.trim().isEmpty()) return;
        newName = newName.trim() + ext;
        java.io.File newFile = new java.io.File(f.getParent(), newName);
        if (newFile.exists()) {
            JOptionPane.showMessageDialog(this, I18n.get("msg.rename_exists"),
                    I18n.get("msg.warning"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (f.renameTo(newFile)) {
            if (currentProject != null) {
                projectService.refreshProject(currentProject.getId(), currentProject.getRootPath());
                loadProjects();
                loadFileTable(currentProject);
            }
        } else {
            JOptionPane.showMessageDialog(this, I18n.get("msg.rename_failed"),
                    I18n.get("msg.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addProject() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("导入项目 — 选择项目根文件夹");
        chooser.setApproveButtonText("导入此文件夹");
        chooser.setApproveButtonToolTipText("将选中的文件夹作为新项目导入");
        // 设置默认路径为上次使用的路径
        if (currentProject != null && currentProject.getRootPath() != null) {
            File lastDir = new File(currentProject.getRootPath()).getParentFile();
            if (lastDir != null && lastDir.exists()) {
                chooser.setCurrentDirectory(lastDir);
            }
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            Project project = projectService.addProject(path);
            if (project != null) {
                loadProjects();
                JOptionPane.showMessageDialog(this,
                        I18n.get("msg.project_added") + project.getName() + "\nStyle folders: " +
                                db.getStyleFolders(project.getId()).size(),
                        I18n.get("msg.success"), JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    /** 批量导入总文件夹下的所有子文件夹作为项目 */
    private void batchImportProjects() {
        BatchImportDialog dlg = new BatchImportDialog(this, db, projectService);
        dlg.setVisible(true);
        loadProjects(); // 导入完成后刷新项目列表
    }

    private void refreshCurrentProject() {
        if (currentProject == null) {
            JOptionPane.showMessageDialog(this, I18n.get("msg.select_project_first"),
                    I18n.get("msg.warning"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        projectService.refreshProject(currentProject.getId(), currentProject.getRootPath());
        loadProjects();
        loadFileTable(currentProject);
    }

    private void loadFileTable(Project project) {
        loadFileTable(project, null);
    }

    private void loadFileTable(Project project, StyleFolder filterFolder) {
        currentProject = project;
        fileTableModel.setRowCount(0);
        List<StyleFolder> folders;
        if (filterFolder != null) {
            folders = Collections.singletonList(filterFolder);
        } else {
            folders = db.getStyleFolders(project.getId());
        }
        for (StyleFolder sf : folders) {
            List<PhotoFile> photos = db.getPhotoFiles(sf.getId());
            for (PhotoFile photo : photos) {
                fileTableModel.addRow(new Object[]{
                        photo.getFileName(),
                        photo.getFileSizeFormatted(),
                        photo.getModifyTime() != null ?
                                photo.getModifyTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "",
                        photo.getFilePath()
                });
            }
        }
        fileTableModel.fireTableDataChanged();
        adjustTableColumns();
        fileTable.revalidate();
        fileTable.repaint();
    }

    private void doSearch() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) { loadProjects(); return; }
        rootNode.removeAllChildren();
        currentProjectList = db.searchProjects(keyword);
        for (Project p : currentProjectList) {
            DefaultMutableTreeNode pNode = new DefaultMutableTreeNode(p);
            for (StyleFolder sf : db.getStyleFolders(p.getId())) {
                pNode.add(new DefaultMutableTreeNode(sf));
            }
            rootNode.add(pNode);
        }
        treeModel.reload();
        for (int i = 0; i < projectTree.getRowCount(); i++) projectTree.expandRow(i);
    }

    private void doDateFilter() {
        try {
            String f = dateFromField.getText().trim();
            String t = dateToField.getText().trim();
            LocalDateTime from = f.isEmpty() ? LocalDateTime.of(2000, 1, 1, 0, 0)
                    : LocalDate.parse(f, DATE_FMT).atStartOfDay();
            LocalDateTime to = t.isEmpty() ? LocalDateTime.now()
                    : LocalDate.parse(t, DATE_FMT).atTime(LocalTime.MAX);

            rootNode.removeAllChildren();
            currentProjectList = db.getProjectsByDateRange(from, to);
            for (Project p : currentProjectList) {
                DefaultMutableTreeNode pNode = new DefaultMutableTreeNode(p);
                for (StyleFolder sf : db.getStyleFolders(p.getId())) {
                    pNode.add(new DefaultMutableTreeNode(sf));
                }
                rootNode.add(pNode);
            }
            treeModel.reload();
            for (int i = 0; i < projectTree.getRowCount(); i++) projectTree.expandRow(i);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, I18n.get("msg.date_error"),
                    I18n.get("msg.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void organizeFiles() {
        if (currentProject == null) {
            JOptionPane.showMessageDialog(this, I18n.get("msg.select_project_first"),
                    I18n.get("msg.warning"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        int ret = JOptionPane.showConfirmDialog(this, I18n.get("msg.organize_confirm"),
                I18n.get("menu.tools.organize"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ret != JOptionPane.OK_OPTION) return;

        try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            String report = FileOrganizer.renameByStyle(currentProject.getRootPath());
            setCursor(Cursor.getDefaultCursor());
            projectService.refreshProject(currentProject.getId(), currentProject.getRootPath());
            loadProjects();
            JOptionPane.showMessageDialog(this, I18n.get("msg.organize_done") + "\n" + report,
                    I18n.get("msg.done"), JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this, I18n.get("msg.failed") + ex.getMessage(),
                    I18n.get("msg.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void compressProject() {
        if (currentProject == null) {
            JOptionPane.showMessageDialog(this, I18n.get("msg.select_project_first"),
                    I18n.get("msg.warning"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            String zip = CompressService.compressProject(currentProject.getRootPath());
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this, I18n.get("msg.compress_done") + zip,
                    I18n.get("msg.done"), JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this, I18n.get("msg.failed") + ex.getMessage(),
                    I18n.get("msg.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void compressSelection() {
        if (currentProject == null) {
            JOptionPane.showMessageDialog(this, I18n.get("msg.select_project_first"),
                    I18n.get("msg.warning"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            String zip = CompressService.compressSelection(currentProject.getRootPath());
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this, I18n.get("msg.compress_done") + zip,
                    I18n.get("msg.done"), JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this, I18n.get("msg.failed") + ex.getMessage(),
                    I18n.get("msg.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 打开百度网盘应用配置对话框
     */
    private void configBaiduApp() {
        BaiduConfigDialog dialog = new BaiduConfigDialog(this);
        dialog.setVisible(true);
    }

    /**
     * 检查百度网盘是否已配置，如果未配置则自动弹出配置对话框
     * @return true if configured, false if user cancelled
     */
    private boolean ensureBaiduConfig() {
        if (!baiduService.isConfigured()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "检测到百度网盘应用尚未配置。\n\n是否现在配置 App Key 和 Secret Key？",
                    "配置百度网盘应用",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            
            if (choice == JOptionPane.YES_OPTION) {
                configBaiduApp();
                // 配置后再次检查
                if (!baiduService.isConfigured()) {
                    JOptionPane.showMessageDialog(this,
                            "配置未完成，无法进行百度网盘操作。",
                            "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private void loginBaidu() {
        // 检查是否已配置，如果未配置则自动弹出配置对话框
        if (!ensureBaiduConfig()) {
            return;
        }
        
        String url = baiduService.getAuthUrl();

        // 用可复制的对话框显示验证链接
        JTextArea ta = new JTextArea(url);
        ta.setEditable(false);
        ta.setCursor(new Cursor(Cursor.TEXT_CURSOR));
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setRows(3);
        ta.setColumns(60);
        ta.selectAll();

        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(560, 80));

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(new JLabel("<html>第一步：复制下方链接，在浏览器中打开<br>第二步：登录百度账号，点击同意授权<br>第三步：百度会直接显示授权码，复制后粘贴到下方输入框</html>"),
                BorderLayout.NORTH);
        panel.add(sp, BorderLayout.CENTER);

        // 复制按钮
        JButton copyBtn = new JButton("复制链接");
        copyBtn.addActionListener(e -> {
            java.awt.datatransfer.StringSelection ss =
                    new java.awt.datatransfer.StringSelection(ta.getText());
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
            copyBtn.setText("已复制");
        });
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnBar.add(copyBtn);
        panel.add(btnBar, BorderLayout.SOUTH);

        JOptionPane.showMessageDialog(this, panel,
                "百度网盘授权", JOptionPane.INFORMATION_MESSAGE);

        String code = JOptionPane.showInputDialog(this, "请输入授权码（百度授权页面上显示的 code）：");
        if (code != null && !code.trim().isEmpty()) {
            try {
                if (baiduService.exchangeToken(code.trim())) {
                    JOptionPane.showMessageDialog(this, I18n.get("msg.login_success"),
                            I18n.get("msg.success"), JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, I18n.get("msg.login_failed"),
                            I18n.get("msg.error"), JOptionPane.ERROR_MESSAGE);
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, I18n.get("msg.network_error") + ex.getMessage(),
                        I18n.get("msg.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void setTokenManually() {
        String token = JOptionPane.showInputDialog(this, I18n.get("msg.enter_token"));
        if (token != null && !token.trim().isEmpty()) {
            baiduService.setAccessToken(token.trim());
            JOptionPane.showMessageDialog(this, I18n.get("msg.token_set"),
                    I18n.get("msg.success"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void uploadProject() {
        if (currentProject == null || !checkBaiduAuth()) return;
        try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            String zip = CompressService.compressProject(currentProject.getRootPath());
            String remote = "/apps/PhotoManager/" + currentProject.getName() + ".zip";
            baiduService.uploadFile(zip, remote);
            String link = baiduService.createShareLink(remote);
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this, I18n.get("msg.upload_done") + link,
                    I18n.get("msg.done"), JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this, I18n.get("msg.failed") + ex.getMessage(),
                    I18n.get("msg.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void uploadSelection() {
        if (currentProject == null || !checkBaiduAuth()) return;
        try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            String zip = CompressService.compressSelection(currentProject.getRootPath());
            String remote = "/apps/PhotoManager/" + currentProject.getName() + "_selection.zip";
            baiduService.uploadFile(zip, remote);
            String link = baiduService.createShareLink(remote);
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this, I18n.get("msg.upload_done") + link,
                    I18n.get("msg.done"), JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            setCursor(Cursor.getDefaultCursor());
            JOptionPane.showMessageDialog(this, I18n.get("msg.failed") + ex.getMessage(),
                    I18n.get("msg.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean checkBaiduAuth() {
        // 如果已有 access token，直接跳过配置检查（上传/分享等操作不需要 App Key）
        if (baiduService.isAuthorized()) {
            return true;
        }
        // 没有 token，需要配置并登录
        if (!ensureBaiduConfig()) {
            return false;
        }
        if (!baiduService.isAuthorized()) {
            JOptionPane.showMessageDialog(this, I18n.get("msg.login_first"),
                    I18n.get("msg.warning"), JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }
}
