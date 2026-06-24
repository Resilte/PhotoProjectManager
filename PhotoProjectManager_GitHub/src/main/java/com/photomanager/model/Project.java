package com.photomanager.model;

import com.photomanager.util.I18n;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 摄影项目实体
 */
public class Project {
    private int id;
    private String name;
    private String rootPath;
    private LocalDateTime createTime;
    private LocalDateTime modifyTime;

    /** 工期截止日期 (DDL)，null 表示未设置 */
    private LocalDate deadline;

    /**
     * 项目状态枚举
     * URGENT   = 紧急（距 DDL 1-3 天），红色
     * PENDING  = 暂缓（距 DDL 3-7 天），黄色
     * IDLE     = 闲置（距 DDL > 7 天），蓝色
     * OTHER    = 其他（未设置 DDL），灰色
     * DONE     = 完成，绿色
     */
    public enum Status {
        URGENT("紧急", "status.urgent"),
        PENDING("暂缓", "status.pending"),
        IDLE("闲置", "status.idle"),
        OTHER("其他", "status.other"),
        DONE("完成", "status.done");

        private final String label;
        private final String i18nKey;
        Status(String label, String i18nKey) { this.label = label; this.i18nKey = i18nKey; }
        public String getLabel() { return I18n.get(i18nKey); }
        public String getOriginalLabel() { return label; }

        public static Status fromLabel(String label) {
            for (Status s : values()) {
                if (s.label.equals(label)) return s;
            }
            return OTHER;
        }
    }

    /** 手动指定的覆盖状态，null 表示由 DDL 自动推算 */
    private Status manualStatus;

    public Project() {}

    public Project(String name, String rootPath) {
        this.name = name;
        this.rootPath = rootPath;
        File f = new File(rootPath);
        if (f.exists()) {
            this.createTime = LocalDateTime.ofInstant(
                    new Date(f.lastModified()).toInstant(), ZoneId.systemDefault());
            this.modifyTime = createTime;
        } else {
            this.createTime = LocalDateTime.now();
            this.modifyTime = LocalDateTime.now();
        }
    }

    // ========== 基本 getter/setter ==========

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRootPath() { return rootPath; }
    public void setRootPath(String rootPath) { this.rootPath = rootPath; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getModifyTime() { return modifyTime; }
    public void setModifyTime(LocalDateTime modifyTime) { this.modifyTime = modifyTime; }

    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }

    public Status getManualStatus() { return manualStatus; }
    public void setManualStatus(Status manualStatus) { this.manualStatus = manualStatus; }

    /**
     * 计算当前有效状态：
     * - 若手动设置了状态，优先返回手动状态
     * - 否则根据 DDL 距今天数自动推算
     */
    public Status getEffectiveStatus() {
        if (manualStatus != null) return manualStatus;
        if (deadline == null) return Status.OTHER;
        long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), deadline);
        if (daysLeft <= 0) return Status.URGENT;      // 已过期也算紧急
        if (daysLeft <= 3) return Status.URGENT;
        if (daysLeft <= 7) return Status.PENDING;
        return Status.IDLE;
    }

    @Override
    public String toString() {
        return name;
    }
}
