package com.photomanager.service;

import java.io.*;
import java.util.Properties;

/**
 * 应用全局配置（暗色模式 / 背景图片 / 百度网盘 等）
 * 持久化到 data/app_config.properties
 */
public class AppConfig {

    private static AppConfig instance;

    private boolean darkMode = false;
    private String backgroundImagePath = "";

    // 百度网盘配置（图形化配置，无需改代码）
    private String baiduAppKey = "";
    private String baiduSecretKey = "";
    private String baiduAccessToken = "";

    private final String configPath;

    private AppConfig(String appDir) {
        this.configPath = new File(appDir, "data/app_config.properties").getAbsolutePath();
        load();
    }

    public static AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig(System.getProperty("user.dir"));
        }
        return instance;
    }

    private void load() {
        File f = new File(configPath);
        if (!f.exists()) return;
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(f)) {
            props.load(fis);
            darkMode = "true".equalsIgnoreCase(props.getProperty("dark.mode", "false"));
            backgroundImagePath = props.getProperty("background.image.path", "");
            baiduAppKey = props.getProperty("baidu.app.key", "");
            baiduSecretKey = props.getProperty("baidu.secret.key", "");
            baiduAccessToken = props.getProperty("baidu.access.token", "");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save() {
        File f = new File(configPath);
        f.getParentFile().mkdirs();
        Properties props = new Properties();
        props.setProperty("dark.mode", String.valueOf(darkMode));
        props.setProperty("background.image.path", backgroundImagePath);
        props.setProperty("baidu.app.key", baiduAppKey);
        props.setProperty("baidu.secret.key", baiduSecretKey);
        props.setProperty("baidu.access.token", baiduAccessToken);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            props.store(fos, "App Configuration");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // =========== getters / setters ===========

    public boolean isDarkMode() { return darkMode; }
    public void setDarkMode(boolean v) { this.darkMode = v; }

    public String getBackgroundImagePath() { return backgroundImagePath; }
    public void setBackgroundImagePath(String v) { this.backgroundImagePath = v; }

    /** 是否已设置背景图片 */
    public boolean hasBackgroundImage() {
        return backgroundImagePath != null && !backgroundImagePath.isEmpty()
                && new File(backgroundImagePath).exists();
    }

    // =========== 百度网盘配置 ===========

    public String getBaiduAppKey() { return baiduAppKey; }
    public void setBaiduAppKey(String v) { this.baiduAppKey = v; }

    public String getBaiduSecretKey() { return baiduSecretKey; }
    public void setBaiduSecretKey(String v) { this.baiduSecretKey = v; }

    public String getBaiduAccessToken() { return baiduAccessToken; }
    public void setBaiduAccessToken(String v) { this.baiduAccessToken = v; }

    /** 是否已配置百度网盘 */
    public boolean isBaiduConfigured() {
        return baiduAppKey != null && baiduAppKey.length() > 0
            && baiduSecretKey != null && baiduSecretKey.length() > 0;
    }
}
