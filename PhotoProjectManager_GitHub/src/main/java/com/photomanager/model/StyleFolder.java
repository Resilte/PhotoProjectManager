package com.photomanager.model;

import java.time.LocalDateTime;

/**
 * 风格文件夹实体（二级目录）
 */
public class StyleFolder {
    private int id;
    private int projectId;
    private String styleName;
    private String folderPath;
    private LocalDateTime modifyTime;
    private int fileCount;

    public StyleFolder() {}

    public StyleFolder(int projectId, String styleName, String folderPath) {
        this.projectId = projectId;
        this.styleName = styleName;
        this.folderPath = folderPath;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getProjectId() { return projectId; }
    public void setProjectId(int projectId) { this.projectId = projectId; }

    public String getStyleName() { return styleName; }
    public void setStyleName(String styleName) { this.styleName = styleName; }

    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }

    public LocalDateTime getModifyTime() { return modifyTime; }
    public void setModifyTime(LocalDateTime modifyTime) { this.modifyTime = modifyTime; }

    public int getFileCount() { return fileCount; }
    public void setFileCount(int fileCount) { this.fileCount = fileCount; }

    @Override
    public String toString() {
        return styleName;
    }
}
