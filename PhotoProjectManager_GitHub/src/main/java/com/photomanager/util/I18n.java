package com.photomanager.util;

import java.io.*;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Properties;

public class I18n {
    private static final String LANG_DIR = "lang/";
    private static final Properties props = new Properties();
    private static final Map<String, String> langMap = new HashMap<>();

    static {
        langMap.put("en_US", "English");
        langMap.put("zh_CN", "中文");
        langMap.put("ja_JP", "日本語");
        // 根据系统语言自动选择，默认中文
        String sysLang = Locale.getDefault().getLanguage();
        if ("en".equals(sysLang)) {
            loadLanguage("en_US");
        } else if ("ja".equals(sysLang)) {
            loadLanguage("ja_JP");
        } else {
            loadLanguage("zh_CN");
        }
    }

    public static void loadLanguage(String langCode) {
        props.clear();
        String path = LANG_DIR + langCode + ".lan";
        try (InputStream is = I18n.class.getClassLoader().getResourceAsStream(path)) {
            if (is != null) {
                props.load(new InputStreamReader(is, "UTF-8"));
            }
        } catch (IOException e) {
            System.err.println("Failed to load language: " + path);
        }
    }

    public static String get(String key) {
        return props.getProperty(key, key);
    }

    public static String get(String key, Object... args) {
        String pattern = get(key);
        return MessageFormat.format(pattern, args);
    }

    public static Map<String, String> getAvailableLanguages() {
        return langMap;
    }
}
