package com.photomanager.service;

import com.photomanager.db.DatabaseManager;
import com.photomanager.model.Project;

import javax.swing.*;
import java.io.File;

/**
 * 项目管理服务（业务逻辑层）
 */
public class ProjectService {

    private final DatabaseManager db;

    public ProjectService(DatabaseManager db) {
        this.db = db;
    }

    /**
     * 添加新项目（扫描目录结构并入库）
     */
    public Project addProject(String rootPath) {
        File rootDir = new File(rootPath);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            JOptionPane.showMessageDialog(null, "目录不存在: " + rootPath, "错误", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        Project project = new Project(rootDir.getName(), rootPath);
        int projectId = db.addProject(project);
        if (projectId == -1) {
            // 可能已存在
            JOptionPane.showMessageDialog(null, "该项目已存在数据库中", "提示", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        project.setId(projectId);

        // 扫描并同步目录结构
        db.scanAndSyncProject(projectId, rootPath);
        return project;
    }

    /**
     * 刷新项目（重新扫描目录）
     */
    public void refreshProject(int projectId, String rootPath) {
        db.scanAndSyncProject(projectId, rootPath);
    }
}
