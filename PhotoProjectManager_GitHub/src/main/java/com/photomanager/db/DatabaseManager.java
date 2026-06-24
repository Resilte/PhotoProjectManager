package com.photomanager.db;

import com.photomanager.model.PhotoFile;
import com.photomanager.model.Project;
import com.photomanager.model.Project.Status;
import com.photomanager.model.StyleFolder;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * SQLite 数据库管理器
 * 存储项目结构信息
 */
public class DatabaseManager {
    private static DatabaseManager instance;
    private Connection connection;
    private final String dbPath;

    public DatabaseManager(String dbPath) {
        this.dbPath = dbPath;
        initDatabase();
    }

    public static DatabaseManager getInstance(String dbPath) {
        if (instance == null) {
            instance = new DatabaseManager(dbPath);
        }
        return instance;
    }

    public static DatabaseManager getInstance() {
        return instance;
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        }
        return connection;
    }

    private void initDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            Statement stmt = conn.createStatement();

            stmt.execute("CREATE TABLE IF NOT EXISTS projects (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT NOT NULL," +
                    "root_path TEXT NOT NULL UNIQUE," +
                    "create_time TEXT," +
                    "modify_time TEXT," +
                    "deadline TEXT," +
                    "manual_status TEXT)");

            // 旧数据库迁移：兼容添加新字段
            try { stmt.execute("ALTER TABLE projects ADD COLUMN deadline TEXT"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE projects ADD COLUMN manual_status TEXT"); } catch (SQLException ignored) {}

            stmt.execute("CREATE TABLE IF NOT EXISTS style_folders (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "project_id INTEGER NOT NULL," +
                    "style_name TEXT NOT NULL," +
                    "folder_path TEXT NOT NULL," +
                    "modify_time TEXT," +
                    "file_count INTEGER DEFAULT 0," +
                    "FOREIGN KEY(project_id) REFERENCES projects(id) ON DELETE CASCADE)");

            stmt.execute("CREATE TABLE IF NOT EXISTS photo_files (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "style_folder_id INTEGER NOT NULL," +
                    "file_name TEXT NOT NULL," +
                    "file_path TEXT NOT NULL UNIQUE," +
                    "file_size INTEGER DEFAULT 0," +
                    "create_time TEXT," +
                    "modify_time TEXT," +
                    "FOREIGN KEY(style_folder_id) REFERENCES style_folders(id) ON DELETE CASCADE)");

            stmt.close();
            conn.close();
        } catch (Exception e) {
            throw new RuntimeException("数据库初始化失败: " + e.getMessage(), e);
        }
    }

    // ==================== Project CRUD ====================

    public List<Project> getAllProjects() {
        List<Project> projects = new ArrayList<>();
        String sql = "SELECT * FROM projects ORDER BY modify_time DESC";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                projects.add(mapProject(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return projects;
    }

    public List<Project> searchProjects(String keyword) {
        List<Project> projects = new ArrayList<>();
        String sql = "SELECT * FROM projects WHERE name LIKE ? ORDER BY modify_time DESC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + keyword + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                projects.add(mapProject(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return projects;
    }

    public List<Project> getProjectsByDateRange(LocalDateTime from, LocalDateTime to) {
        List<Project> projects = new ArrayList<>();
        String sql = "SELECT * FROM projects WHERE modify_time BETWEEN ? AND ? ORDER BY modify_time DESC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                projects.add(mapProject(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return projects;
    }

    public int addProject(Project project) {
        String sql = "INSERT OR IGNORE INTO projects (name, root_path, create_time, modify_time, deadline, manual_status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, project.getName());
            ps.setString(2, project.getRootPath());
            ps.setString(3, project.getCreateTime().toString());
            ps.setString(4, project.getModifyTime().toString());
            ps.setString(5, project.getDeadline() != null ? project.getDeadline().toString() : null);
            ps.setString(6, project.getManualStatus() != null ? project.getManualStatus().name() : null);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public void updateProject(Project project) {
        String sql = "UPDATE projects SET name=?, modify_time=?, deadline=?, manual_status=? WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, project.getName());
            ps.setString(2, project.getModifyTime().toString());
            ps.setString(3, project.getDeadline() != null ? project.getDeadline().toString() : null);
            ps.setString(4, project.getManualStatus() != null ? project.getManualStatus().name() : null);
            ps.setInt(5, project.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** 仅更新 deadline 和 manual_status 字段 */
    public void updateProjectSchedule(int projectId, LocalDate deadline, Project.Status manualStatus) {
        String sql = "UPDATE projects SET deadline=?, manual_status=? WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deadline != null ? deadline.toString() : null);
            ps.setString(2, manualStatus != null ? manualStatus.name() : null);
            ps.setInt(3, projectId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 删除项目记录（级联删除 style_folders 和 photo_files）
     */
    public void deleteProject(int projectId) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 先删 photo_files
                String delFiles = "DELETE FROM photo_files WHERE style_folder_id IN (SELECT id FROM style_folders WHERE project_id=?)";
                try (PreparedStatement ps = conn.prepareStatement(delFiles)) {
                    ps.setInt(1, projectId);
                    ps.executeUpdate();
                }
                // 再删 style_folders
                String delFolders = "DELETE FROM style_folders WHERE project_id=?";
                try (PreparedStatement ps = conn.prepareStatement(delFolders)) {
                    ps.setInt(1, projectId);
                    ps.executeUpdate();
                }
                // 最后删 project
                String delProject = "DELETE FROM projects WHERE id=?";
                try (PreparedStatement ps = conn.prepareStatement(delProject)) {
                    ps.setInt(1, projectId);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ==================== StyleFolder CRUD ====================

    public List<StyleFolder> getStyleFolders(int projectId) {
        List<StyleFolder> folders = new ArrayList<>();
        String sql = "SELECT * FROM style_folders WHERE project_id=? ORDER BY style_name";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, projectId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                folders.add(mapStyleFolder(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return folders;
    }

    public int addStyleFolder(StyleFolder folder) {
        String sql = "INSERT OR IGNORE INTO style_folders (project_id, style_name, folder_path, modify_time, file_count) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, folder.getProjectId());
            ps.setString(2, folder.getStyleName());
            ps.setString(3, folder.getFolderPath());
            ps.setString(4, folder.getModifyTime() != null ? folder.getModifyTime().toString() : LocalDateTime.now().toString());
            ps.setInt(5, folder.getFileCount());
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public void deleteStyleFoldersByProject(int projectId) {
        // 先删除关联的 photo_files
        String delFiles = "DELETE FROM photo_files WHERE style_folder_id IN (SELECT id FROM style_folders WHERE project_id=?)";
        String delFolders = "DELETE FROM style_folders WHERE project_id=?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement(delFiles);
                 PreparedStatement ps2 = conn.prepareStatement(delFolders)) {
                ps1.setInt(1, projectId);
                ps1.executeUpdate();
                ps2.setInt(1, projectId);
                ps2.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ==================== PhotoFile CRUD ====================

    public java.util.List<PhotoFile> getPhotoFiles(int styleFolderId) {
        java.util.List<PhotoFile> files = new java.util.ArrayList<>();
        String sql = "SELECT * FROM photo_files WHERE style_folder_id=? ORDER BY file_name";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, styleFolderId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                files.add(mapPhotoFile(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return files;
    }

    public int addPhotoFile(PhotoFile file) {
        String sql = "INSERT OR IGNORE INTO photo_files (style_folder_id, file_name, file_path, file_size, create_time, modify_time) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, file.getStyleFolderId());
            ps.setString(2, file.getFileName());
            ps.setString(3, file.getFilePath());
            ps.setLong(4, file.getFileSize());
            ps.setString(5, file.getCreateTime().toString());
            ps.setString(6, file.getModifyTime().toString());
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public void deletePhotoFilesByStyleFolder(int styleFolderId) {
        String sql = "DELETE FROM photo_files WHERE style_folder_id=?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, styleFolderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ==================== Scan File System ====================

    /**
     * 扫描项目目录并同步到数据库（批量插入优化）
     */
    public void scanAndSyncProject(int projectId, String rootPath) {
        File rootDir = new File(rootPath);
        if (!rootDir.exists() || !rootDir.isDirectory()) return;

        // 删除旧数据
        deleteStyleFoldersByProject(projectId);

        File[] styleDirs = rootDir.listFiles(File::isDirectory);
        if (styleDirs == null) return;

        String styleInsertSql = "INSERT OR IGNORE INTO style_folders (project_id, style_name, folder_path, modify_time, file_count) VALUES (?, ?, ?, ?, ?)";
        String photoInsertSql = "INSERT OR IGNORE INTO photo_files (style_folder_id, file_name, file_path, file_size, create_time, modify_time) VALUES (?, ?, ?, ?, ?, ?)";

        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false); // 统一事务，大幅提升速度

            try (PreparedStatement psStyle = conn.prepareStatement(styleInsertSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement psPhoto = conn.prepareStatement(photoInsertSql)) {

                for (File styleDir : styleDirs) {
                    String nowStr = LocalDateTime.now().toString();

                    psStyle.setInt(1, projectId);
                    psStyle.setString(2, styleDir.getName());
                    psStyle.setString(3, styleDir.getAbsolutePath());
                    psStyle.setString(4, nowStr);
                    psStyle.setInt(5, 0); // file_count 稍后更新
                    psStyle.executeUpdate();

                    int sfId = -1;
                    try (ResultSet rs = psStyle.getGeneratedKeys()) {
                        if (rs.next()) sfId = rs.getInt(1);
                    }
                    if (sfId == -1) continue;

                    File[] photos = styleDir.listFiles((dir, name) -> {
                        String lower = name.toLowerCase();
                        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                               lower.endsWith(".png") || lower.endsWith(".gif") ||
                               lower.endsWith(".bmp") || lower.endsWith(".webp") ||
                               lower.endsWith(".tiff") || lower.endsWith(".tif") ||
                               lower.endsWith(".raw") || lower.endsWith(".cr2") ||
                               lower.endsWith(".nef") || lower.endsWith(".arw") ||
                               lower.endsWith(".dng");
                    });

                    int photoCount = photos != null ? photos.length : 0;

                    // 更新 file_count
                    try (PreparedStatement psUpdateCount = conn.prepareStatement(
                            "UPDATE style_folders SET file_count=? WHERE id=?")) {
                        psUpdateCount.setInt(1, photoCount);
                        psUpdateCount.setInt(2, sfId);
                        psUpdateCount.executeUpdate();
                    }

                    // 批量插入照片记录
                    if (photos != null) {
                        for (File photo : photos) {
                            String timeStr = LocalDateTime.ofInstant(
                                    new Date(photo.lastModified()).toInstant(), ZoneId.systemDefault()).toString();
                            psPhoto.setInt(1, sfId);
                            psPhoto.setString(2, photo.getName());
                            psPhoto.setString(3, photo.getAbsolutePath());
                            psPhoto.setLong(4, photo.length());
                            psPhoto.setString(5, timeStr);
                            psPhoto.setString(6, timeStr);
                            psPhoto.addBatch();
                        }
                        psPhoto.executeBatch();
                    }
                }
            }
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
            try { if (conn != null) conn.rollback(); } catch (SQLException ignored) {}
        } finally {
            try { if (conn != null) { conn.setAutoCommit(true); conn.close(); } } catch (SQLException ignored) {}
        }
    }

    // ==================== Mapping ====================

    private Project mapProject(ResultSet rs) throws SQLException {
        Project p = new Project();
        p.setId(rs.getInt("id"));
        p.setName(rs.getString("name"));
        p.setRootPath(rs.getString("root_path"));
        String ct = rs.getString("create_time");
        String mt = rs.getString("modify_time");
        if (ct != null) p.setCreateTime(LocalDateTime.parse(ct));
        if (mt != null) p.setModifyTime(LocalDateTime.parse(mt));
        String dl = rs.getString("deadline");
        if (dl != null) p.setDeadline(LocalDate.parse(dl));
        String ms = rs.getString("manual_status");
        if (ms != null) {
            try { p.setManualStatus(Project.Status.valueOf(ms)); } catch (IllegalArgumentException ignored) {}
        }
        return p;
    }

    private StyleFolder mapStyleFolder(ResultSet rs) throws SQLException {
        StyleFolder sf = new StyleFolder();
        sf.setId(rs.getInt("id"));
        sf.setProjectId(rs.getInt("project_id"));
        sf.setStyleName(rs.getString("style_name"));
        sf.setFolderPath(rs.getString("folder_path"));
        String mt = rs.getString("modify_time");
        if (mt != null) sf.setModifyTime(LocalDateTime.parse(mt));
        sf.setFileCount(rs.getInt("file_count"));
        return sf;
    }

    private PhotoFile mapPhotoFile(ResultSet rs) throws SQLException {
        PhotoFile pf = new PhotoFile();
        pf.setId(rs.getInt("id"));
        pf.setStyleFolderId(rs.getInt("style_folder_id"));
        pf.setFileName(rs.getString("file_name"));
        pf.setFilePath(rs.getString("file_path"));
        pf.setFileSize(rs.getLong("file_size"));
        String ct = rs.getString("create_time");
        String mt = rs.getString("modify_time");
        if (ct != null) pf.setCreateTime(LocalDateTime.parse(ct));
        if (mt != null) pf.setModifyTime(LocalDateTime.parse(mt));
        return pf;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
