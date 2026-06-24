package com.photomanager.service;

import java.io.*;
import java.util.Properties;

/**
 * 工期排单默认值设置
 * 持久化保存到 data/schedule_settings.properties
 *
 * 各状态天数约束：
 *   紧急（URGENT）：1 ~ 3 天
 *   暂缓（PENDING）：4 ~ 7 天（且必须 > 紧急上限）
 *   闲置（IDLE）：8 ~ 365 天（且必须 > 暂缓上限）
 */
public class ScheduleSettings {

    private static ScheduleSettings instance;

    /** 快速标记"紧急"时的默认天数 */
    private int urgentDefaultDays = 2;
    /** 快速标记"暂缓"时的默认天数 */
    private int pendingDefaultDays = 4;
    /** 快速标记"闲置"时的默认天数 */
    private int idleDefaultDays = 10;

    // ---- 各状态的硬性约束范围 ----
    public static final int URGENT_MIN = 1;
    public static final int URGENT_MAX = 3;

    public static final int PENDING_MIN = 4;
    public static final int PENDING_MAX = 7;

    public static final int IDLE_MIN = 8;
    public static final int IDLE_MAX = 365;

    private final String settingsPath;

    private ScheduleSettings(String appDir) {
        this.settingsPath = new File(appDir, "data/schedule_settings.properties").getAbsolutePath();
        load();
        // 加载后校正当值（若从配置文件读取到了越界值则回正）
        validateAndCorrect();
    }

    public static ScheduleSettings getInstance() {
        if (instance == null) {
            instance = new ScheduleSettings(System.getProperty("user.dir"));
        }
        return instance;
    }

    private void load() {
        File f = new File(settingsPath);
        if (!f.exists()) return;
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(f)) {
            props.load(fis);
            urgentDefaultDays  = parseInt(props.getProperty("urgent.default.days"),  urgentDefaultDays);
            pendingDefaultDays = parseInt(props.getProperty("pending.default.days"), pendingDefaultDays);
            idleDefaultDays    = parseInt(props.getProperty("idle.default.days"),    idleDefaultDays);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save() {
        validateAndCorrect();
        File f = new File(settingsPath);
        f.getParentFile().mkdirs();
        Properties props = new Properties();
        props.setProperty("urgent.default.days",  String.valueOf(urgentDefaultDays));
        props.setProperty("pending.default.days", String.valueOf(pendingDefaultDays));
        props.setProperty("idle.default.days",    String.valueOf(idleDefaultDays));
        try (FileOutputStream fos = new FileOutputStream(f)) {
            props.store(fos, "Schedule Settings");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 校正当值：确保每个值在其合法范围内
     */
    private void validateAndCorrect() {
        urgentDefaultDays  = clamp(urgentDefaultDays,  URGENT_MIN, URGENT_MAX);
        pendingDefaultDays = clamp(pendingDefaultDays, PENDING_MIN, PENDING_MAX);
        idleDefaultDays    = clamp(idleDefaultDays,    IDLE_MIN, IDLE_MAX);
    }

    /**
     * 验证一个值是否在其合法范围。返回 null 表示合法，否则返回错误消息。
     */
    public static String validateUrgent(int value) {
        if (value < URGENT_MIN || value > URGENT_MAX)
            return "紧急天数必须在 " + URGENT_MIN + " ~ " + URGENT_MAX + " 之间";
        return null;
    }

    public static String validatePending(int value) {
        if (value < PENDING_MIN || value > PENDING_MAX)
            return "暂缓天数必须在 " + PENDING_MIN + " ~ " + PENDING_MAX + " 之间";
        return null;
    }

    public static String validateIdle(int value) {
        if (value < IDLE_MIN || value > IDLE_MAX)
            return "闲置天数必须在 " + IDLE_MIN + " ~ " + IDLE_MAX + " 之间";
        return null;
    }

    private int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    private int parseInt(String val, int def) {
        if (val == null) return def;
        try { return Integer.parseInt(val.trim()); } catch (NumberFormatException e) { return def; }
    }

    // ========== getters / setters ==========

    public int getUrgentDefaultDays()  { return urgentDefaultDays; }
    public int getPendingDefaultDays() { return pendingDefaultDays; }
    public int getIdleDefaultDays()    { return idleDefaultDays; }

    /**
     * 设置紧急天数（须在 1~3 范围）
     * @return 合法时返回 null，越界时返回错误消息
     */
    public String setUrgentDefaultDays(int v) {
        String err = validateUrgent(v);
        if (err != null) return err;
        this.urgentDefaultDays = v;
        return null;
    }

    /**
     * 设置暂缓天数（须在 4~7 范围）
     * @return 合法时返回 null，越界时返回错误消息
     */
    public String setPendingDefaultDays(int v) {
        String err = validatePending(v);
        if (err != null) return err;
        this.pendingDefaultDays = v;
        return null;
    }

    /**
     * 设置闲置天数（须在 8~365 范围）
     * @return 合法时返回 null，越界时返回错误消息
     */
    public String setIdleDefaultDays(int v) {
        String err = validateIdle(v);
        if (err != null) return err;
        this.idleDefaultDays = v;
        return null;
    }
}
