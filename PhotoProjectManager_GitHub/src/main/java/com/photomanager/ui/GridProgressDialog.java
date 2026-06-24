package com.photomanager.ui;

import com.photomanager.service.GridGenerator;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * 九宫格生成进度对话框
 * 使用 SwingWorker 在后台线程生成，避免界面卡顿
 */
public class GridProgressDialog extends JDialog {

    private final String projectRootPath;
    private final int[] backgroundColor;
    private final String backgroundImagePath;
    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final JButton cancelBtn;
    private SwingWorker<String, String> worker;
    private volatile boolean cancelled = false;

    public GridProgressDialog(Frame parent,
                               String projectRootPath,
                               int[] backgroundColor,
                               String backgroundImagePath) {
        super(parent, "正在生成九宫格", true);
        this.projectRootPath = projectRootPath;
        this.backgroundColor = backgroundColor;
        this.backgroundImagePath = backgroundImagePath;

        setLayout(new BorderLayout(12, 12));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));
        setResizable(false);

        // 状态文字
        statusLabel = new JLabel("正在准备...", JLabel.CENTER);
        statusLabel.setFont(statusLabel.getFont().deriveFont(13f));
        add(statusLabel, BorderLayout.NORTH);

        // 进度条
        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(380, 22));
        progressBar.setBorderPainted(true);
        progressBar.setStringPainted(true);
        progressBar.setForeground(new Color(60, 140, 220));
        add(progressBar, BorderLayout.CENTER);

        // 取消按钮
        cancelBtn = new JButton("取消");
        cancelBtn.addActionListener(e -> {
            cancelled = true;
            if (worker != null) worker.cancel(true);
            dispose();
        });
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.add(cancelBtn);
        add(btnPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(parent);

        // 启动后台任务
        startGeneration();
    }

    private void startGeneration() {
        worker = new SwingWorker<String, String>() {

            @Override
            protected String doInBackground() throws Exception {
                // 进度回调：更新进度条
                java.util.function.Consumer<Integer> callback = pct -> {
                    if (cancelled) return;
                    int p = Math.max(0, Math.min(100, pct));
                    publish("进度: " + p + "%");
                    setProgress(p);
                };

                setProgress(0);
                publish("正在扫描项目照片...");

                String outDir = GridGenerator.generateGridPdf(
                        projectRootPath,
                        backgroundColor,
                        backgroundImagePath,
                        callback
                );

                if (cancelled) return null;
                setProgress(100);
                return outDir;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                if (cancelled) return;
                String latest = chunks.get(chunks.size() - 1);
                statusLabel.setText(latest);
            }

            @Override
            protected void done() {
                try {
                    if (cancelled) return;
                    String outDir = get(); // 阻塞直到完成
                    if (outDir != null) {
                        progressBar.setValue(100);
                        statusLabel.setText("生成完成！");
                        cancelBtn.setText("打开输出文件夹");
                        cancelBtn.removeActionListener(cancelBtn.getActionListeners()[0]);
                        cancelBtn.addActionListener(e -> {
                            openFolder(outDir);
                            dispose();
                        });
                        // 3秒后自动关闭
                        Timer timer = new Timer(3000, e -> dispose());
                        timer.setRepeats(false);
                        timer.start();
                    }
                } catch (Exception ex) {
                    if (!cancelled) {
                        statusLabel.setText("生成失败: " + ex.getMessage());
                        cancelBtn.setText("关闭");
                    }
                }
            }
        };

        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                int v = (Integer) evt.getNewValue();
                progressBar.setValue(v);
            }
        });

        worker.execute();
    }

    /**
     * 用系统默认文件管理器打开文件夹
     */
    private void openFolder(String path) {
        try {
            Desktop desktop = Desktop.getDesktop();
            desktop.open(new File(path));
        } catch (Exception e) {
            // 备用方案：用 explorer 打开
            try {
                Runtime.getRuntime().exec("explorer /e, \"" + path + "\"");
            } catch (Exception ignored) {}
        }
    }
}
