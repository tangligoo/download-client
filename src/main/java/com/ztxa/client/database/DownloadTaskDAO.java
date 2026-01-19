package com.ztxa.client.database;

import com.ztxa.client.model.DownloadTask;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DownloadTaskDAO {
    private final DatabaseManager dbManager;
    
    public DownloadTaskDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }
    
    public void saveTask(DownloadTask task) {
        String sql = "INSERT OR REPLACE INTO download_tasks " +
                     "(task_id, file_id, file_name, file_path, file_size, downloaded_size, save_path, status, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, task.getTaskId());
            pstmt.setString(2, task.getFileId());
            pstmt.setString(3, task.getFileName());
            pstmt.setString(4, task.getFilePath());
            pstmt.setLong(5, task.getFileSize());
            pstmt.setLong(6, task.getDownloadedSize());
            pstmt.setString(7, task.getSavePath());
            pstmt.setString(8, task.getStatus());
            pstmt.setLong(9, Long.parseLong(task.getTaskId()));  // created_at 使用 taskId 的时间戳
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to save task: " + e.getMessage());
        }
    }
    
    public void updateTaskProgress(String taskId, long downloadedSize, String status) {
        String sql = "UPDATE download_tasks SET downloaded_size = ?, status = ?, updated_at = CURRENT_TIMESTAMP " +
                     "WHERE task_id = ?";
        
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setLong(1, downloadedSize);
            pstmt.setString(2, status);
            pstmt.setString(3, taskId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to update task: " + e.getMessage());
        }
    }
    
    public List<DownloadTask> getAllTasks() {
        List<DownloadTask> tasks = new ArrayList<>();
        String sql = "SELECT * FROM download_tasks ORDER BY created_at DESC";
        
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                String fileId = rs.getString("file_id");
                DownloadTask task;
                
                if (fileId != null && !fileId.isEmpty()) {
                    // 使用fileId构造
                    task = new DownloadTask(
                        fileId,
                        rs.getString("file_name"),
                        rs.getLong("file_size"),
                        rs.getString("save_path"),
                        true
                    );
                } else {
                    // 兼容旧数据，使用filePath构造
                    task = new DownloadTask(
                        rs.getString("file_name"),
                        rs.getString("file_path"),
                        rs.getLong("file_size"),
                        rs.getString("save_path")
                    );
                }
                
                // 设置 taskId
                task.setTaskId(rs.getString("task_id"));
                task.setDownloadedSize(rs.getLong("downloaded_size"));
                
                // 设置状态
                String status = rs.getString("status");
                for (DownloadTask.Status s : DownloadTask.Status.values()) {
                    if (s.getText().equals(status)) {
                        task.setStatus(s);
                        break;
                    }
                }
                
                tasks.add(task);
            }
        } catch (SQLException e) {
            System.err.println("Failed to get tasks: " + e.getMessage());
        }
        
        return tasks;
    }
    
    /**
     * 分页查询任务（优化大数据量查询）
     * @param limit 每页数量
     * @param offset 偏移量
     * @return 任务列表
     */
    public List<DownloadTask> getTasksPaged(int limit, int offset) {
        List<DownloadTask> tasks = new ArrayList<>();
        String sql = "SELECT * FROM download_tasks ORDER BY created_at DESC LIMIT ? OFFSET ?";
        
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            pstmt.setInt(2, offset);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                String fileId = rs.getString("file_id");
                DownloadTask task;
                
                if (fileId != null && !fileId.isEmpty()) {
                    // 使用fileId构造
                    task = new DownloadTask(
                        fileId,
                        rs.getString("file_name"),
                        rs.getLong("file_size"),
                        rs.getString("save_path"),
                        true
                    );
                } else {
                    // 兼容旧数据，使用filePath构造
                    task = new DownloadTask(
                        rs.getString("file_name"),
                        rs.getString("file_path"),
                        rs.getLong("file_size"),
                        rs.getString("save_path")
                    );
                }
                
                task.setDownloadedSize(rs.getLong("downloaded_size"));
                
                // 设置状态
                String status = rs.getString("status");
                for (DownloadTask.Status s : DownloadTask.Status.values()) {
                    if (s.getText().equals(status)) {
                        task.setStatus(s);
                        break;
                    }
                }
                
                tasks.add(task);
            }
        } catch (SQLException e) {
            System.err.println("Failed to get paged tasks: " + e.getMessage());
        }
        
        return tasks;
    }
    
    /**
     * 获取任务总数
     * @return 任务总数
     */
    public int getTaskCount() {
        String sql = "SELECT COUNT(*) FROM download_tasks";
        
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Failed to get task count: " + e.getMessage());
        }
        
        return 0;
    }
    
    public DownloadTask getTaskByFilePath(String filePath) {
        String sql = "SELECT * FROM download_tasks WHERE file_path = ? OR file_id = ?";
        
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, filePath);
            pstmt.setString(2, filePath); // 也通过fileId查找
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                String fileId = rs.getString("file_id");
                DownloadTask task;
                
                if (fileId != null && !fileId.isEmpty()) {
                    task = new DownloadTask(
                        fileId,
                        rs.getString("file_name"),
                        rs.getLong("file_size"),
                        rs.getString("save_path"),
                        true
                    );
                } else {
                    task = new DownloadTask(
                        rs.getString("file_name"),
                        rs.getString("file_path"),
                        rs.getLong("file_size"),
                        rs.getString("save_path")
                    );
                }
                
                task.setDownloadedSize(rs.getLong("downloaded_size"));
                
                // 设置状态
                String status = rs.getString("status");
                for (DownloadTask.Status s : DownloadTask.Status.values()) {
                    if (s.getText().equals(status)) {
                        task.setStatus(s);
                        break;
                    }
                }
                
                return task;
            }
        } catch (SQLException e) {
            System.err.println("Failed to get task: " + e.getMessage());
        }
        
        return null;
    }
    
    public void deleteTask(String taskId) {
        String sql = "DELETE FROM download_tasks WHERE task_id = ?";
        
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, taskId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to delete task: " + e.getMessage());
        }
    }
    
    public void deleteCompletedTasks() {
        String sql = "DELETE FROM download_tasks WHERE status = ?";
        
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, DownloadTask.Status.COMPLETED.getText());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to delete completed tasks: " + e.getMessage());
        }
    }
}
