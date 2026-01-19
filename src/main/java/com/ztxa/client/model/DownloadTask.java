package com.ztxa.client.model;

import javafx.beans.property.*;

public class DownloadTask {
    private String taskId;            // 任务唯一ID（时间戳）
    private final StringProperty fileName;
    private final LongProperty fileSize;
    private final LongProperty downloadedSize;
    private final DoubleProperty progress;
    private final StringProperty status;
    private final StringProperty speed;
    private String fileId;        // 文件ID（用于下载）
    private String filePath;      // 文件路径（用于显示）
    private String savePath;
    private volatile boolean paused;
    private volatile boolean cancelled;
    
    public enum Status {
        WAITING("等待中"),
        DOWNLOADING("下载中"),
        PAUSED("已暂停"),
        COMPLETED("已完成"),
        FAILED("失败"),
        CANCELLED("已取消");
        
        private final String text;
        
        Status(String text) {
            this.text = text;
        }
        
        public String getText() {
            return text;
        }
    }

    public DownloadTask(String fileName, String filePath, long fileSize, String savePath) {
        this.taskId = String.valueOf(System.currentTimeMillis());  // 生成时间戳作为 taskId
        this.fileName = new SimpleStringProperty(fileName);
        this.filePath = filePath;
        this.fileSize = new SimpleLongProperty(fileSize);
        this.downloadedSize = new SimpleLongProperty(0);
        this.progress = new SimpleDoubleProperty(0.0);
        this.status = new SimpleStringProperty(Status.WAITING.getText());
        this.speed = new SimpleStringProperty("0 KB/s");
        this.savePath = savePath;
        this.paused = false;
        this.cancelled = false;
    }
    
    // 新构造函数：支持fileId（通过布尔值区分）
    public DownloadTask(String fileId, String fileName, long fileSize, String savePath, boolean useFileId) {
        this.taskId = String.valueOf(System.currentTimeMillis());  // 生成时间戳作为 taskId
        if (useFileId) {
            this.fileId = fileId;
            this.filePath = fileId; // 兼容旧代码
        } else {
            this.filePath = fileId; // 当作路径使用
        }
        this.fileName = new SimpleStringProperty(fileName);
        this.fileSize = new SimpleLongProperty(fileSize);
        this.downloadedSize = new SimpleLongProperty(0);
        this.progress = new SimpleDoubleProperty(0.0);
        this.status = new SimpleStringProperty(Status.WAITING.getText());
        this.speed = new SimpleStringProperty("0 KB/s");
        this.savePath = savePath;
        this.paused = false;
        this.cancelled = false;
    }

    public String getTaskId() {
        return taskId;
    }
    
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getFileName() {
        return fileName.get();
    }

    public StringProperty fileNameProperty() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }
    
    public String getFileId() {
        return fileId;
    }
    
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public long getFileSize() {
        return fileSize.get();
    }

    public LongProperty fileSizeProperty() {
        return fileSize;
    }

    public long getDownloadedSize() {
        return downloadedSize.get();
    }

    public void setDownloadedSize(long size) {
        this.downloadedSize.set(size);
        this.progress.set((double) size / fileSize.get());
    }

    public LongProperty downloadedSizeProperty() {
        return downloadedSize;
    }

    public double getProgress() {
        return progress.get();
    }

    public DoubleProperty progressProperty() {
        return progress;
    }

    public String getStatus() {
        return status.get();
    }

    public void setStatus(Status status) {
        this.status.set(status.getText());
    }

    public StringProperty statusProperty() {
        return status;
    }

    public String getSpeed() {
        return speed.get();
    }

    public void setSpeed(String speed) {
        this.speed.set(speed);
    }

    public StringProperty speedProperty() {
        return speed;
    }

    public String getSavePath() {
        return savePath;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
