package com.ztxa.client.model;

public class FileInfo {
    private String fileId;        // 文件ID（后端SpringBoot返回）
    private String fileName;      // 文件名
    private String filePath;      // 文件路径（可选，用于显示）
    private long fileSize;        // 文件大小
    private String checksum;      // 文件校验和
    private long timestamp;       // 时间戳
    private String fileType;      // 文件类型（file/folder）

    public FileInfo() {
    }

    public FileInfo(String fileName, String filePath, long fileSize, String checksum) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.checksum = checksum;
        this.timestamp = System.currentTimeMillis();
        this.fileType = "file";
    }
    
    // 新构造函数：支持fileId
    public FileInfo(String fileId, String fileName, long fileSize) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.timestamp = System.currentTimeMillis();
        this.fileType = "file";
    }
    
    public String getFileId() {
        return fileId;
    }
    
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }
    
    public String getFileType() {
        return fileType;
    }
    
    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getFormattedSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.2f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", fileSize / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", fileSize / (1024.0 * 1024 * 1024));
        }
    }
}
