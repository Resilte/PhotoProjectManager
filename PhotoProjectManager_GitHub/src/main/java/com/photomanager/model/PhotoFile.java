package com.photomanager.model;

import java.time.LocalDateTime;

/**
 * 图片文件实体
 */
public class PhotoFile {
    private int id;
    private int styleFolderId;
    private String fileName;
    private String filePath;
    private long fileSize;
    private LocalDateTime createTime;
    private LocalDateTime modifyTime;

    public PhotoFile() {}

    public PhotoFile(int styleFolderId, String fileName, String filePath,
                     long fileSize, LocalDateTime createTime, LocalDateTime modifyTime) {
        this.styleFolderId = styleFolderId;
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.createTime = createTime;
        this.modifyTime = modifyTime;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getStyleFolderId() { return styleFolderId; }
    public void setStyleFolderId(int styleFolderId) { this.styleFolderId = styleFolderId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getModifyTime() { return modifyTime; }
    public void setModifyTime(LocalDateTime modifyTime) { this.modifyTime = modifyTime; }

    /**
     * 格式化文件大小
     */
    public String getFileSizeFormatted() {
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
    }

    @Override
    public String toString() {
        return fileName;
    }
}
