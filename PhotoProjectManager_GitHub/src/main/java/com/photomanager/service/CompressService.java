package com.photomanager.service;

import com.photomanager.util.FileUtil;

import java.io.File;
import java.io.IOException;

/**
 * 压缩服务
 */
public class CompressService {

    /**
     * 压缩整个项目
     */
    public static String compressProject(String projectRootPath) throws IOException {
        File rootDir = new File(projectRootPath);
        String zipPath = projectRootPath + ".zip";
        FileUtil.zipDirectory(projectRootPath, zipPath);
        return zipPath;
    }

    /**
     * 压缩选片文件夹
     */
    public static String compressSelection(String projectRootPath) throws IOException {
        File selectionDir = new File(projectRootPath, "选片文件夹");
        if (!selectionDir.exists()) {
            throw new IOException("选片文件夹不存在，请先生成选片宫格");
        }
        String zipPath = selectionDir.getAbsolutePath() + ".zip";
        FileUtil.zipDirectory(selectionDir.getAbsolutePath(), zipPath);
        return zipPath;
    }
}
