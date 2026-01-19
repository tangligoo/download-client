package com.ztxa.client.database;

import java.io.File;
import java.sql.*;

public class DatabaseManager {
    private static final String DB_DIR = System.getProperty("user.home") + File.separator + ".file-transfer-client";
    private static final String DB_FILE = DB_DIR + File.separator + "database.db";
    private static DatabaseManager instance;
    private Connection connection;
    
    private DatabaseManager() {
        initDatabase();
    }
    
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }
    
    private void initDatabase() {
        try {
            // 确保目录存在
            File dir = new File(DB_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            // 连接数据库
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_FILE);
            
            // 创建表
            createTables();
            
            System.out.println("Database initialized: " + DB_FILE);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private void createTables() throws SQLException {
        Statement stmt = connection.createStatement();
        
        // 创建配置表
        stmt.execute(
            "CREATE TABLE IF NOT EXISTS config (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    key TEXT UNIQUE NOT NULL," +
            "    value TEXT NOT NULL," +
            "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")"
        );
        
        // 为config表创建索引
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_config_key ON config(key)");
        
        // 创建下载任务表（新版本：使用时间戳作为主键）
        stmt.execute(
            "CREATE TABLE IF NOT EXISTS download_tasks ("
            + "    task_id TEXT PRIMARY KEY,"  // 使用时间戳作为主键
            + "    file_id TEXT,"
            + "    file_name TEXT NOT NULL,"
            + "    file_path TEXT NOT NULL,"
            + "    file_size INTEGER NOT NULL,"
            + "    downloaded_size INTEGER DEFAULT 0,"
            + "    save_path TEXT NOT NULL,"
            + "    status TEXT NOT NULL,"
            + "    created_at INTEGER NOT NULL,"  // 创建时间（毫秒时间戳）
            + "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
            + ")"
        );
        
        // 迁移：检查旧表结构并升级
        try {
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(download_tasks)");
            boolean hasTaskId = false;
            boolean hasCreatedAtInteger = false;
            
            while (rs.next()) {
                String columnName = rs.getString("name");
                String columnType = rs.getString("type");
                
                if ("task_id".equals(columnName)) {
                    hasTaskId = true;
                }
                if ("created_at".equals(columnName) && "INTEGER".equals(columnType)) {
                    hasCreatedAtInteger = true;
                }
            }
            
            // 如果旧表结构不同，需要迁移
            if (!hasTaskId || !hasCreatedAtInteger) {
                System.out.println("检测到旧的表结构，开始迁移数据库...");
                
                // 备份旧表
                stmt.execute("ALTER TABLE download_tasks RENAME TO download_tasks_old");
                
                // 创建新表（使用时间戳主键）
                stmt.execute(
                    "CREATE TABLE download_tasks ("
                    + "    task_id TEXT PRIMARY KEY,"
                    + "    file_id TEXT,"
                    + "    file_name TEXT NOT NULL,"
                    + "    file_path TEXT NOT NULL,"
                    + "    file_size INTEGER NOT NULL,"
                    + "    downloaded_size INTEGER DEFAULT 0,"
                    + "    save_path TEXT NOT NULL,"
                    + "    status TEXT NOT NULL,"
                    + "    created_at INTEGER NOT NULL,"
                    + "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                    + ")"
                );
                
                // 复制数据（生成 task_id）
                stmt.execute(
                    "INSERT INTO download_tasks (task_id, file_id, file_name, file_path, file_size, downloaded_size, save_path, status, created_at, updated_at) "
                    + "SELECT "
                    + "    CAST((julianday(COALESCE(created_at, CURRENT_TIMESTAMP)) - 2440587.5) * 86400000 AS INTEGER) || '_' || id AS task_id, "  // 生成时间戳_id 作为 task_id
                    + "    file_id, file_name, file_path, file_size, downloaded_size, save_path, status, "
                    + "    CAST((julianday(COALESCE(created_at, CURRENT_TIMESTAMP)) - 2440587.5) * 86400000 AS INTEGER) AS created_at, "
                    + "    updated_at "
                    + "FROM download_tasks_old"
                );
                
                // 删除旧表
                stmt.execute("DROP TABLE download_tasks_old");
                
                System.out.println("数据库迁移完成，已使用时间戳主键");
            }
        } catch (SQLException e) {
            System.err.println("数据库迁移失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        // 为下载任务表创建索引
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_task_file_id ON download_tasks(file_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_task_file_path ON download_tasks(file_path)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_task_status ON download_tasks(status)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_task_created_at ON download_tasks(created_at DESC)");
        
        stmt.close();
    }
    
    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection("jdbc:sqlite:" + DB_FILE);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return connection;
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
