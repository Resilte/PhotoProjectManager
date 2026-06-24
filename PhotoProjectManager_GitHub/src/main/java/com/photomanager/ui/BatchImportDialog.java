package com.photomanager.ui;

import com.photomanager.db.DatabaseManager;
import com.photomanager.model.Project;
import com.photomanager.service.ProjectService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入对话框 - 选择总文件夹，预览所有子文件夹，
 * 勾选需要导入的项目后一键批量导入。
 */
public class BatchImportDialog extends JDialog {

    private final DatabaseManager db;
    private final ProjectService projectService;
    private final DefaultTableModel tableModel;
    private final JTable folderTable;
    private final JLabel pathLabel;
    private final JButton importBtn;
    private File currentRootDir;

    // 列定义
    private static final String[] COLUMNS = {"导入", "项目名称", "照片数", "文件夹路径"};
    private static final int COL_CHECK = 0;
    private static final int COL_NAME  = 1;
    private static final int COL_COUNT = 2;
    private static final int COL_PATH  = 3;

    public BatchImportDialog(Frame owner, DatabaseManager db, ProjectService projectService) {
        super(owner, "批量导入项目", true);
        this.db = db;
        this.projectService = projectService;

        setSize(780, 520);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        // ===== 顶部：路径选择区 =====
        JPanel topPanel = new JPanel(new BorderLayout(10, 0));
        topPanel.setOpaque(false);

        JButton selectBtn = new JButton("选择总文件夹");
        selectBtn.setFocusPainted(false);
        selectBtn.setBackground(new Color(70, 130, 200));
        selectBtn.setForeground(Color.WHITE);
        selectBtn.setOpaque(true);
        selectBtn.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        selectBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        pathLabel = new JLabel("未选择文件夹");
        pathLabel.setForeground(new Color(120, 120, 120));
        pathLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        topPanel.add(selectBtn, BorderLayout.WEST);
        topPanel.add(pathLabel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // ===== 中间：子文件夹表格 =====
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public Class<?> getColumnClass(int col) {
                return col == COL_CHECK ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int col) {
                return col == COL_CHECK;
            }
        };

        folderTable = new JTable(tableModel);
        folderTable.setRowHeight(36);
        folderTable.setShowGrid(false);
        folderTable.setIntercellSpacing(new Dimension(0, 0));
        folderTable.getTableHeader().setReorderingAllowed(false);
        folderTable.getTableHeader().setBackground(new Color(240, 240, 245));
        folderTable.getTableHeader().setFont(folderTable.getTableHeader().getFont().deriveFont(13f));
        folderTable.setFont(folderTable.getFont().deriveFont(12.5f));

        // 列宽
        folderTable.getColumnModel().getColumn(COL_CHECK).setPreferredWidth(50);
        folderTable.getColumnModel().getColumn(COL_CHECK).setMaxWidth(60);
        folderTable.getColumnModel().getColumn(COL_NAME).setPreferredWidth(200);
        folderTable.getColumnModel().getColumn(COL_COUNT).setPreferredWidth(70);
        folderTable.getColumnModel().getColumn(COL_COUNT).setMaxWidth(90);
        folderTable.getColumnModel().getColumn(COL_PATH).setPreferredWidth(400);

        // 隔行变色
        folderTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                         boolean isSelected, boolean hasFocus,
                                                         int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 249, 252));
                }
                ((JLabel) c).setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(folderTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 230)));
        add(scrollPane, BorderLayout.CENTER);

        // ===== 底部：操作按钮区 =====
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        // 左侧：全选/反选
        JPanel leftBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftBtnPanel.setOpaque(false);

        JButton selectAllBtn = new JButton("全选");
        styleSmallBtn(selectAllBtn);
        selectAllBtn.addActionListener(e -> setAllChecked(true));

        JButton deselectAllBtn = new JButton("取消全选");
        styleSmallBtn(deselectAllBtn);
        deselectAllBtn.addActionListener(e -> setAllChecked(false));

        JButton invertBtn = new JButton("反选");
        styleSmallBtn(invertBtn);
        invertBtn.addActionListener(e -> invertSelection());

        leftBtnPanel.add(selectAllBtn);
        leftBtnPanel.add(deselectAllBtn);
        leftBtnPanel.add(invertBtn);

        // 右侧：导入/取消
        JPanel rightBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightBtnPanel.setOpaque(false);

        importBtn = new JButton("导入选中项目");
        importBtn.setFocusPainted(false);
        importBtn.setBackground(new Color(60, 170, 90));
        importBtn.setForeground(Color.WHITE);
        importBtn.setOpaque(true);
        importBtn.setBorder(BorderFactory.createEmptyBorder(8, 22, 8, 22));
        importBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        importBtn.setEnabled(false);

        JButton cancelBtn = new JButton("取消");
        cancelBtn.setFocusPainted(false);
        cancelBtn.setBorder(BorderFactory.createEmptyBorder(8, 22, 8, 22));
        cancelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        rightBtnPanel.add(importBtn);
        rightBtnPanel.add(cancelBtn);

        bottomPanel.add(leftBtnPanel, BorderLayout.WEST);
        bottomPanel.add(rightBtnPanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        // ===== 事件绑定 =====
        selectBtn.addActionListener(e -> chooseRootFolder());
        importBtn.addActionListener(e -> doBatchImport());
        cancelBtn.addActionListener(e -> dispose());
    }

    /** 选择总文件夹 */
    private void chooseRootFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择存放所有项目的总文件夹");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentRootDir = chooser.getSelectedFile();
            pathLabel.setText(currentRootDir.getAbsolutePath());
            pathLabel.setForeground(new Color(50, 50, 50));
            scanSubFolders();
        }
    }

    /** 扫描总文件夹下的所有子文件夹（后台执行，不卡 UI） */
    private void scanSubFolders() {
        tableModel.setRowCount(0);
        importBtn.setEnabled(false);
        if (currentRootDir == null || !currentRootDir.isDirectory()) return;

        File[] subDirs = currentRootDir.listFiles((FileFilter) file ->
                file.isDirectory() && !file.getName().startsWith(".")
        );
        if (subDirs == null || subDirs.length == 0) {
            importBtn.setEnabled(false);
            return;
        }

        // 先显示文件夹列表，照片数显示"..."，后台统计
        for (File dir : subDirs) {
            tableModel.addRow(new Object[]{true, dir.getName(), "...", dir.getAbsolutePath()});
        }

        // 后台统计照片数
        final File[] dirsSnapshot = subDirs;
        new SwingWorker<Void, Integer>() {
            @Override
            protected Void doInBackground() throws Exception {
                for (int i = 0; i < dirsSnapshot.length; i++) {
                    int count = countPhotos(dirsSnapshot[i]);
                    final int row = i;
                    final int c = count;
                    SwingUtilities.invokeLater(() ->
                        tableModel.setValueAt(c, row, COL_COUNT)
                    );
                }
                return null;
            }

            @Override
            protected void done() {
                importBtn.setEnabled(tableModel.getRowCount() > 0);
            }
        }.execute();
    }

    /** 递归统计文件夹中的照片数量 */
    private int countPhotos(File dir) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) {
                count += countPhotos(f);
            } else {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                    name.endsWith(".png") || name.endsWith(".bmp") ||
                    name.endsWith(".webp") || name.endsWith(".tiff") ||
                    name.endsWith(".tif") || name.endsWith(".heic") ||
                    name.endsWith(".avif")) {
                    count++;
                }
            }
        }
        return count;
    }

    /** 全选 / 全不选 */
    private void setAllChecked(boolean checked) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(checked, i, COL_CHECK);
        }
    }

    /** 反选 */
    private void invertSelection() {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Boolean val = (Boolean) tableModel.getValueAt(i, COL_CHECK);
            tableModel.setValueAt(!val, i, COL_CHECK);
        }
    }

    /** 执行批量导入 */
    private void doBatchImport() {
        List<String> selectedPaths = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Boolean checked = (Boolean) tableModel.getValueAt(i, COL_CHECK);
            if (checked) {
                selectedPaths.add((String) tableModel.getValueAt(i, COL_PATH));
            }
        }
        if (selectedPaths.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请至少选择一个项目！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 进度对话框
        JDialog progressDialog = createProgressDialog(selectedPaths.size());
        JProgressBar progressBar = (JProgressBar) ((Container) progressDialog.getContentPane()).getComponent(1);
        JLabel progressLabel = (JLabel) ((Container) progressDialog.getContentPane()).getComponent(0);
        progressDialog.setVisible(true);

        new SwingWorker<Void, String>() {
            private int successCount = 0;
            private int skipCount = 0;

            @Override
            protected Void doInBackground() {
                for (int i = 0; i < selectedPaths.size(); i++) {
                    String path = selectedPaths.get(i);
                    int pct = (int) ((i + 1) * 100.0 / selectedPaths.size());
                    progressBar.setValue(pct);
                    progressLabel.setText("正在导入 (" + (i + 1) + "/" + selectedPaths.size() + "): " +
                            new File(path).getName());
                    try {
                        Project p = projectService.addProject(path);
                        if (p != null) successCount++;
                        else skipCount++;
                    } catch (Exception ex) {
                        skipCount++;
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                JOptionPane.showMessageDialog(
                        BatchImportDialog.this,
                        "批量导入完成！\n成功导入：" + successCount + " 个项目" +
                                (skipCount > 0 ? "\n已跳过（重复）：" + skipCount + " 个项目" : ""),
                        "导入结果",
                        JOptionPane.INFORMATION_MESSAGE
                );
                dispose();
            }
        }.execute();
    }

    /** 创建进度对话框 */
    private JDialog createProgressDialog(int total) {
        JDialog dlg = new JDialog(this, "正在导入...", true);
        dlg.setSize(380, 130);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout(10, 10));
        ((JPanel) dlg.getContentPane()).setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JLabel label = new JLabel("准备导入...");
        label.setFont(label.getFont().deriveFont(13f));
        dlg.add(label, BorderLayout.NORTH);

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        bar.setPreferredSize(new Dimension(100, 22));
        dlg.add(bar, BorderLayout.CENTER);

        JButton cancelBtn = new JButton("后台运行");
        cancelBtn.addActionListener(e -> dlg.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.setOpaque(false);
        south.add(cancelBtn);
        dlg.add(south, BorderLayout.SOUTH);

        return dlg;
    }

    /** 小按钮样式 */
    private void styleSmallBtn(JButton btn) {
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        btn.setBackground(new Color(240, 240, 245));
        btn.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 210)));
    }
}
