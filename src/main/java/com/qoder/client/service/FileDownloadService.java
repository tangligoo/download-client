package com.qoder.client.service;

import com.qoder.client.config.AppConfig;
import com.qoder.client.model.DownloadTask;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileDownloadService {
    private static final int BUFFER_SIZE = 8192;
    
    public void downloadFile(DownloadTask task, DownloadProgressListener listener) {
        new Thread(() -> {
            try {
                doDownload(task, listener);
            } catch (Exception e) {
                task.setStatus(DownloadTask.Status.FAILED);
                if (listener != null) {
                    listener.onError(task, e);
                }
            }
        }).start();
    }
    
    private void doDownload(DownloadTask task, DownloadProgressListener listener) throws Exception {
        AppConfig config = AppConfig.getInstance();
        String serverHost = config.getServerHost();
        int tcpPort = config.getServerTcpPort();
        
        // 优先使用fileId，如果没有则使用filePath
        String downloadIdentifier = task.getFileId() != null ? task.getFileId() : task.getFilePath();
        
        File saveFile = new File(task.getSavePath());
        File parentDir = saveFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        // 检查已下载的大小
        long downloadedSize = 0;
        if (saveFile.exists()) {
            downloadedSize = saveFile.length();
            task.setDownloadedSize(downloadedSize);
        }
        
        // 如果已经下载完成
        if (downloadedSize >= task.getFileSize()) {
            task.setDownloadedSize(task.getFileSize());
            task.setStatus(DownloadTask.Status.COMPLETED);
            if (listener != null) {
                listener.onCompleted(task);
            }
            return;
        }
        
        task.setStatus(DownloadTask.Status.DOWNLOADING);
        
        try (Socket socket = new Socket(serverHost, tcpPort);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream());
             RandomAccessFile raf = new RandomAccessFile(saveFile, "rw")) {
            
            // 发送请求: appKey|fileId(或filePath)|startPosition
            String request = config.getAppKey() + "|" + downloadIdentifier + "|" + downloadedSize;
            out.writeUTF(request);
            out.flush();
            
            // 读取响应状态
            String response = in.readUTF();
            if (!response.equals("OK")) {
                throw new IOException("Server error: " + response);
            }
            
            // 从断点位置继续写入
            raf.seek(downloadedSize);
            
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long lastUpdateTime = System.currentTimeMillis();
            long lastDownloadedSize = downloadedSize;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                if (task.isCancelled()) {
                    task.setStatus(DownloadTask.Status.CANCELLED);
                    break;
                }
                
                while (task.isPaused()) {
                    task.setStatus(DownloadTask.Status.PAUSED);
                    Thread.sleep(100);
                }
                
                if (task.getStatus().equals(DownloadTask.Status.PAUSED.getText())) {
                    task.setStatus(DownloadTask.Status.DOWNLOADING);
                }
                
                raf.write(buffer, 0, bytesRead);
                downloadedSize += bytesRead;
                task.setDownloadedSize(downloadedSize);
                
                // 计算速度
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastUpdateTime >= 1000) {
                    long timeDiff = currentTime - lastUpdateTime;
                    long sizeDiff = downloadedSize - lastDownloadedSize;
                    double speedKB = (sizeDiff / 1024.0) / (timeDiff / 1000.0);
                    task.setSpeed(String.format("%.2f KB/s", speedKB));
                    
                    lastUpdateTime = currentTime;
                    lastDownloadedSize = downloadedSize;
                    
                    if (listener != null) {
                        listener.onProgress(task);
                    }
                }
            }
            
            if (!task.isCancelled() && downloadedSize >= task.getFileSize()) {
                task.setStatus(DownloadTask.Status.COMPLETED);
                task.setSpeed("0 KB/s");
                if (listener != null) {
                    listener.onCompleted(task);
                }
            }
            
        } catch (Exception e) {
            task.setStatus(DownloadTask.Status.FAILED);
            throw e;
        }
    }
    
    private String extractHost(String url) {
        // 从URL中提取主机名
        String host = url.replace("http://", "").replace("https://", "");
        int colonIndex = host.indexOf(':');
        int slashIndex = host.indexOf('/');
        
        if (colonIndex > 0) {
            host = host.substring(0, colonIndex);
        } else if (slashIndex > 0) {
            host = host.substring(0, slashIndex);
        }
        
        return host;
    }
    
    public interface DownloadProgressListener {
        void onProgress(DownloadTask task);
        void onCompleted(DownloadTask task);
        void onError(DownloadTask task, Exception e);
    }
}
