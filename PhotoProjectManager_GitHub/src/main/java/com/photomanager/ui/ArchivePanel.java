package com.photomanager.ui;

import com.photomanager.db.DatabaseManager;
import com.photomanager.service.BaiduNetdiskService;
import com.photomanager.util.I18n;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 压缩包管理面板 - 查看所有项目压缩包，
 * 右键可上传到百度网盘并生成分享链接。
 */
public class ArchivePanel extends JPanel {

    private final DatabaseManager db;
    private final BaiduNetdiskService baiduService;
    private final DefaultTableModel tableModel;
    private final JTable archiveTable;
    private final JLabel statusLabel;

    public ArchivePanel(DatabaseManager db, BaiduNetdiskService baiduService) {
        this.db = db;
        this.baiduService = baiduService;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ===== 顶部：标题 + 刷新按钮 =====
        JPanel topPanel = new JPanel(new BorderLayout(10, 0));
        topPanel.setOpaque(false);

        JLabel titleLabel = new JLabel(I18n.get("archive.title"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(16f));

        JButton refreshBtn = new JButton(I18n.get("archive.refresh"));
        refreshBtn.setFocusPainted(false);
        refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshBtn.addActionListener(e -> refreshList());

        topPanel.add(titleLabel, BorderLayout.WEST);
        topPanel.add(refreshBtn, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // ===== 中间：压缩包表格 =====
        String[] columns = {
                I18n.get("archive.col.filename"),
                I18n.get("archive.col.size"),
                I18n.get("archive.col.date"),
                I18n.get("archive.col.path")
        };
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        archiveTable = new JTable(tableModel);
        archiveTable.setRowHeight(32);
        archiveTable.setShowGrid(false);
        archiveTable.setIntercellSpacing(new Dimension(0, 0));
        archiveTable.getTableHeader().setReorderingAllowed(false);
        archiveTable.getTableHeader().setFont(archiveTable.getTableHeader().getFont().deriveFont(13f));

        // 列宽
        archiveTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        archiveTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        archiveTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        archiveTable.getColumnModel().getColumn(3).setPreferredWidth(400);

        // 右键菜单
        JPopupMenu popupMenu = createPopupMenu();
        archiveTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopup(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                showPopup(e);
            }
            private void showPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = archiveTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        archiveTable.setRowSelectionInterval(row, row);
                        popupMenu.show(archiveTable, e.getX(), e.getY());
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(archiveTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 230)));
        add(scrollPane, BorderLayout.CENTER);

        // ===== 底部：状态栏 =====
        statusLabel = new JLabel(I18n.get("archive.click_to_refresh"));
        statusLabel.setForeground(new Color(120, 120, 120));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 0));
        add(statusLabel, BorderLayout.SOUTH);

        // 初始加载
        refreshList();
    }

    /** 刷新压缩包列表 */
    public void refreshList() {
        tableModel.setRowCount(0);
        List<File> zipFiles = findArchiveFiles();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (File f : zipFiles) {
            String sizeStr = formatFileSize(f.length());
            String timeStr = sdf.format(new Date(f.lastModified()));
            tableModel.addRow(new Object[]{
                    f.getName(),
                    sizeStr,
                    timeStr,
                    f.getAbsolutePath()
            });
        }

        statusLabel.setText(I18n.get("archive.found_zip", zipFiles.size()));
    }

    /** 查找所有项目压缩包（.zip 文件） */
    private List<File> findArchiveFiles() {
        List<File> result = new ArrayList<>();

        // 从数据库获取所有项目的路径，查找对应的 .zip 文件
        List<com.photomanager.model.Project> projects = db.getAllProjects();
        for (com.photomanager.model.Project p : projects) {
            String rootPath = p.getRootPath();
            // 项目压缩包：项目路径 + ".zip"
            File projectZip = new File(rootPath + ".zip");
            if (projectZip.exists()) {
                result.add(projectZip);
            }
            // 选片压缩包：项目路径/选片文件夹.zip（文件名固定，与语言无关）
            File selectionZip = new File(rootPath, "选片文件夹.zip");
            if (selectionZip.exists()) {
                result.add(selectionZip);
            }
        }

        // 也扫描当前工作目录下的 .zip 文件
        String appDir = System.getProperty("user.dir");
        File dir = new File(appDir);
        File[] zipFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".zip"));
        if (zipFiles != null) {
            for (File f : zipFiles) {
                if (!result.contains(f)) {
                    result.add(f);
                }
            }
        }

        return result;
    }

    /** 创建右键菜单 */
    private JPopupMenu createPopupMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem uploadItem = new JMenuItem(I18n.get("archive.upload_to_baidu"));
        uploadItem.addActionListener(e -> uploadSelected());

        JMenuItem shareItem = new JMenuItem(I18n.get("archive.gen_share_link"));
        shareItem.addActionListener(e -> generateShareLink());

        JMenuItem openDirItem = new JMenuItem(I18n.get("archive.open_folder"));
        openDirItem.addActionListener(e -> openSelectedFileDir());

        JMenuItem deleteItem = new JMenuItem(I18n.get("archive.delete_archive"));
        deleteItem.addActionListener(e -> deleteSelected());

        menu.add(uploadItem);
        menu.add(shareItem);
        menu.addSeparator();
        menu.add(openDirItem);
        menu.add(deleteItem);

        return menu;
    }

    /** 上传选中压缩包到百度网盘 */
    private void uploadSelected() {
        int row = archiveTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, I18n.get("archive.select_first"),
                    I18n.get("msg.info"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        String filePath = (String) tableModel.getValueAt(row, 3);
        File file = new File(filePath);

        if (!baiduService.isAuthorized()) {
            JOptionPane.showMessageDialog(this,
                    I18n.get("archive.login_first"),
                    I18n.get("msg.warning"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 询问远程路径
        String defaultRemotePath = "/apps/PhotoManager/" + file.getName();
        String remotePath = (String) JOptionPane.showInputDialog(this,
                I18n.get("archive.enter_remote_path"),
                I18n.get("archive.upload_to_baidu"),
                JOptionPane.QUESTION_MESSAGE, null, null, defaultRemotePath);
        if (remotePath == null || remotePath.trim().isEmpty()) return;

        // 后台上传
        JDialog progressDialog = createProgressDialog(I18n.get("archive.uploading"));
        JLabel label = (JLabel) ((Container) progressDialog.getContentPane()).getComponent(0);
        JProgressBar bar = (JProgressBar) ((Container) progressDialog.getContentPane()).getComponent(1);
        progressDialog.setVisible(true);

        new SwingWorker<String, String>() {
            @Override
            protected String doInBackground() throws Exception {
                publish(I18n.get("archive.uploading") + ": " + file.getName() + " (" + formatFileSize(file.length()) + ")");
                return baiduService.uploadFile(filePath, remotePath);
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String msg : chunks) {
                    label.setText(msg);
                }
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    String result = get();
                    JOptionPane.showMessageDialog(ArchivePanel.this,
                            I18n.get("archive.upload_done") + result,
                            I18n.get("archive.upload_done"),
                            JOptionPane.INFORMATION_MESSAGE);
                    // 自动生成分享链接
                    int choice = JOptionPane.showConfirmDialog(ArchivePanel.this,
                            I18n.get("archive.gen_link_question"),
                            I18n.get("archive.gen_share_link"),
                            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (choice == JOptionPane.YES_OPTION) {
                        generateShareLinkForPath(remotePath);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ArchivePanel.this,
                            I18n.get("archive.upload_failed") + ex.getMessage(),
                            I18n.get("msg.error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** 生成分享链接 */
    private void generateShareLink() {
        int row = archiveTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, I18n.get("archive.select_first"),
                    I18n.get("msg.info"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        String filePath = (String) tableModel.getValueAt(row, 3);
        File file = new File(filePath);

        if (!baiduService.isAuthorized()) {
            JOptionPane.showMessageDialog(this,
                    I18n.get("archive.login_first"),
                    I18n.get("msg.warning"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        String defaultRemotePath = "/apps/PhotoManager/" + file.getName();
        String remotePath = (String) JOptionPane.showInputDialog(this,
                I18n.get("archive.enter_share_path"),
                I18n.get("archive.gen_share_link"),
                JOptionPane.QUESTION_MESSAGE, null, null, defaultRemotePath);
        if (remotePath == null || remotePath.trim().isEmpty()) return;

        generateShareLinkForPath(remotePath);
    }

    /** 为指定远程路径生成分享链接 */
    private void generateShareLinkForPath(String remotePath) {
        JDialog progressDialog = createProgressDialog(I18n.get("archive.gening_link"));
        JLabel label = (JLabel) ((Container) progressDialog.getContentPane()).getComponent(0);
        progressDialog.setVisible(true);

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return baiduService.createShareLink(remotePath);
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    String shareLink = get();
                    if (shareLink != null && shareLink.startsWith("http")) {
                        // 成功，复制到剪贴板
                        Toolkit.getDefaultToolkit().getSystemClipboard()
                                .setContents(new StringSelection(shareLink), null);
                        JOptionPane.showMessageDialog(ArchivePanel.this,
                                I18n.get("archive.link_copied") + shareLink,
                                I18n.get("archive.gen_share_link"),
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(ArchivePanel.this,
                                I18n.get("archive.gen_link_failed") + shareLink,
                                I18n.get("msg.error"), JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ArchivePanel.this,
                            I18n.get("archive.gen_link_failed") + ex.getMessage(),
                            I18n.get("msg.error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** 打开选中文件所在文件夹 */
    private void openSelectedFileDir() {
        int row = archiveTable.getSelectedRow();
        if (row < 0) return;
        String filePath = (String) tableModel.getValueAt(row, 3);
        File file = new File(filePath);
        if (file.exists()) {
            try {
                Desktop desktop = Desktop.getDesktop();
                desktop.open(file.getParentFile());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        I18n.get("archive.cannot_open_folder") + ex.getMessage(),
                        I18n.get("msg.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /** 删除选中压缩包 */
    private void deleteSelected() {
        int row = archiveTable.getSelectedRow();
        if (row < 0) return;

        String fileName = (String) tableModel.getValueAt(row, 0);
        String filePath = (String) tableModel.getValueAt(row, 3);

        int choice = JOptionPane.showConfirmDialog(this,
                I18n.get("archive.confirm_delete", fileName),
                I18n.get("archive.delete_archive"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            File file = new File(filePath);
            if (file.delete()) {
                JOptionPane.showMessageDialog(this,
                        I18n.get("archive.delete_success"),
                        I18n.get("msg.info"), JOptionPane.INFORMATION_MESSAGE);
                refreshList();
            } else {
                JOptionPane.showMessageDialog(this,
                        I18n.get("archive.delete_failed"),
                        I18n.get("msg.error"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /** 创建进度对话框 */
    private JDialog createProgressDialog(String title) {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), title, true);
        dlg.setSize(400, 120);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout(10, 10));
        ((JPanel) dlg.getContentPane()).setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JLabel label = new JLabel(I18n.get("archive.please_wait"));
        label.setFont(label.getFont().deriveFont(13f));
        dlg.add(label, BorderLayout.NORTH);

        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setPreferredSize(new Dimension(100, 20));
        dlg.add(bar, BorderLayout.CENTER);

        return dlg;
    }

    /** 格式化文件大小 */
    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024.0));
        return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    /** 用于复制到剪贴板 */
    private static class StringSelection implements Transferable {
        private final String data;

        public StringSelection(String data) {
            this.data = data;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.stringFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor.equals(DataFlavor.stringFlavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) {
            return data;
        }
    }
}
