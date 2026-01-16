package com.qoder.client.database;

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
        
        // 创建下载任务表
        stmt.execute(
            "CREATE TABLE IF NOT EXISTS download_tasks (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    file_id TEXT," +
            "    file_name TEXT NOT NULL," +
            "    file_path TEXT NOT NULL," +
            "    file_size INTEGER NOT NULL," +
            "    downloaded_size INTEGER DEFAULT 0," +
            "    save_path TEXT NOT NULL," +
            "    status TEXT NOT NULL," +
            "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    UNIQUE(file_path)" +
            ")"
        );
        
        // 迁移：如果表已存在但没有file_id列，则添加
        try {
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(download_tasks)");
            boolean hasFileId = false;
            while (rs.next()) {
                if ("file_id".equals(rs.getString("name"))) {
                    hasFileId = true;
                    break;
                }
            }
            if (!hasFileId) {
                System.out.println("Migrating database: adding file_id column...");
                stmt.execute("ALTER TABLE download_tasks ADD COLUMN file_id TEXT");
            }
        } catch (SQLException e) {
            System.err.println("Migration check failed: " + e.getMessage());
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
