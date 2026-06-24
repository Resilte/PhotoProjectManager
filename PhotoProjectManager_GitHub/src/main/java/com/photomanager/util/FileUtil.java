package com.photomanager.util;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileUtil {

    public static void openInExplorer(String filePath) {
        try {
            String cmd = "explorer.exe /select,\"" + filePath + "\"";
            Runtime.getRuntime().exec(cmd);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void openFile(String filePath) {
        try {
            Desktop.getDesktop().open(new File(filePath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void zipDirectory(String sourceDir, String outputZip) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputZip))) {
            Files.walk(sourcePath).filter(path -> !Files.isDirectory(path)).forEach(path -> {
                try {
                    String relativePath = sourcePath.relativize(path).toString();
                    ZipEntry entry = new ZipEntry(relativePath);
                    zos.putNextEntry(entry);
                    Files.copy(path, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
