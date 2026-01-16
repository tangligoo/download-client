package com.qoder.server.model;

public class FileInfo {
    private String fileName;
    private String filePath;
    private long fileSize;
    private String checksum;
    private long timestamp;

    public FileInfo() {
    }

    public FileInfo(String fileName, String filePath, long fileSize, String checksum) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.checksum = checksum;
        this.timestamp = System.currentTimeMillis();
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
}
