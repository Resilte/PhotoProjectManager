package com.photomanager.ui;

import com.photomanager.service.AppConfig;

import javax.swing.*;
import java.awt.*;

/**
 * 百度网盘应用配置对话框
 * 让用户可以图形化地填入 App Key 和 Secret Key
 */
public class BaiduConfigDialog extends JDialog {

    public BaiduConfigDialog(Frame parent) {
        super(parent, "配置百度网盘应用", true);
        setLayout(new BorderLayout(8, 8));
        setResizable(false);

        AppConfig cfg = AppConfig.getInstance();

        // 说明面板
        JPanel infoPanel = new JPanel(new BorderLayout(5, 5));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));
        JTextArea infoArea = new JTextArea(
            "获取 App Key 和 Secret Key 的步骤：\n" +
            "1. 访问 https://pan.baidu.com/union 并登录百度账号\n" +
            "2. 点击「创建应用」，应用类型选「桌面应用」\n" +
            "3. 创建完成后，在「应用信息」页面复制 App Key 和 Secret Key\n" +
            "4. 将下方回调地址填到应用设置中（如已填可跳过）\n" +
            "\n回调地址（需填到应用设置中）：oob"
        );
        infoArea.setEditable(false);
        infoArea.setBackground(null);
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        infoPanel.add(infoArea, BorderLayout.CENTER);

        // 表单面板
        JPanel formPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        formPanel.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        formPanel.add(new JLabel("App Key："));
        JTextField keyField = new JTextField(cfg.getBaiduAppKey(), 30);
        formPanel.add(keyField);

        formPanel.add(new JLabel("Secret Key："));
        JPasswordField secretField = new JPasswordField(cfg.getBaiduSecretKey(), 30);
        formPanel.add(secretField);

        // 按钮面板
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        btnPanel.setBorder(BorderFactory.createEmptyBorder(8, 16, 12, 16));

        JButton saveBtn = new JButton("保存");
        JButton cancelBtn = new JButton("取消");

        saveBtn.addActionListener(e -> {
            String key = keyField.getText().trim();
            String secret = new String(secretField.getPassword()).trim();
            if (key.isEmpty() || secret.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "App Key 和 Secret Key 不能为空！",
                    "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            cfg.setBaiduAppKey(key);
            cfg.setBaiduSecretKey(secret);
            cfg.save();
            JOptionPane.showMessageDialog(this,
                "保存成功！现在可以点击「百度网盘 → 登录授权」了。",
                "成功", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        });

        cancelBtn.addActionListener(e -> dispose());

        btnPanel.add(cancelBtn);
        btnPanel.add(saveBtn);

        add(infoPanel, BorderLayout.NORTH);
        add(formPanel, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(parent);
    }
}
