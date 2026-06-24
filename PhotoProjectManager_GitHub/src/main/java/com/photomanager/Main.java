package com.photomanager;

import com.formdev.flatlaf.FlatLightLaf;
import com.photomanager.ui.MainFrame;

import javax.swing.*;

/**
 * 入口类
 */
public class Main {
    public static void main(String[] args) {
        // 设置现代 FlatLaf 主题
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            // 回退默认
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
        }

        // 设置中文字体
        UIManager.put("defaultFont", new java.awt.Font("Microsoft YaHei", java.awt.Font.PLAIN, 13));

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
