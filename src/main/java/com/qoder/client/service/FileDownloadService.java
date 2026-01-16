package com.qoder.client.service;

import com.qoder.client.config.AppConfig;
import com.qoder.client.model.DownloadTask;
import com.qoder.client.util.BytesDecimalismUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileDownloadService {
    private static final Logger logger = LoggerFactory.getLogger(FileDownloadService.class);
    private static final int BUFFER_SIZE = 8192;
    
    // 存储每个任务的 Socket，用于取消时关闭
    private final Map<DownloadTask, Socket> activeSockets = new ConcurrentHashMap<>();
    
    public void downloadFile(DownloadTask task, DownloadProgressListener listener) {
        logger.info("[下载入口] 启动下载线程: fileName={}, fileId={}, fileSize={}, status={}", 
            task.getFileName(), task.getFileId(), task.getFileSize(), task.getStatus());
        
        new Thread(() -> {
            try {
                logger.debug("[下载线程] 线程已启动，准备调用 doDownload(): {}", task.getFileName());
                doDownload(task, listener);
            } catch (Exception e) {
                task.setStatus(DownloadTask.Status.FAILED);
                logger.error("[下载线程] 下载异常: fileName={}", task.getFileName(), e);
                if (listener != null) {
                    listener.onError(task, e);
                }
            } finally {
                // 清理资源
                activeSockets.remove(task);
                logger.debug("[下载线程] 线程结束: {}", task.getFileName());
            }
        }).start();
        
        logger.debug("[下载入口] 下载线程已提交: {}", task.getFileName());
    }
    
    private void doDownload(DownloadTask task, DownloadProgressListener listener) throws Exception {
        AppConfig config = AppConfig.getInstance();
        String serverHost = config.getServerHost();
        int tcpPort = config.getServerTcpPort();
        
        // 优先使用fileId，如果没有则使用filePath
        String downloadIdentifier = task.getFileId() != null ? task.getFileId() : task.getFilePath();
        logger.debug("准备下载: downloadIdentifier={}, serverHost={}, tcpPort={}", downloadIdentifier, serverHost, tcpPort);
        
        File saveFile = new File(task.getSavePath());
        File parentDir = saveFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
            logger.debug("创建目录: {}", parentDir.getAbsolutePath());
        }
        
        // 检查已下载的大小
        long downloadedSize = 0;
        if (saveFile.exists()) {
            downloadedSize = saveFile.length();
            task.setDownloadedSize(downloadedSize);
            logger.debug("文件已存在，续传位置: {} bytes", downloadedSize);
        }
        
        // 如果已经下载完成
        if (downloadedSize >= task.getFileSize()) {
            logger.info("文件已下载完成，跳过: fileName={}, fileSize={}", task.getFileName(), task.getFileSize());
            task.setDownloadedSize(task.getFileSize());
            task.setStatus(DownloadTask.Status.COMPLETED);
            if (listener != null) {
                listener.onCompleted(task);
            }
            return;
        }
        
        task.setStatus(DownloadTask.Status.DOWNLOADING);
        logger.info("开始下载: fileId={}, fileName={}, startPosition={}", downloadIdentifier, task.getFileName(), downloadedSize);
        
        Socket socket = null;
        RandomAccessFile raf = null;
        try {
            logger.debug("连接服务器: {}:{}", serverHost, tcpPort);
            socket = new Socket(serverHost, tcpPort);
            activeSockets.put(task, socket);  // 保存 Socket 引用
            logger.debug("连接成功，Socket: {}", socket);
            
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            raf = new RandomAccessFile(saveFile, "rw");
            
            // 构建请求数据: appKey|fileId|startPosition
            String requestData = config.getAppKey() + "|" + downloadIdentifier + "|" + downloadedSize;
            byte[] dataBytes = requestData.getBytes(StandardCharsets.UTF_8);
            
            // 构建简化协议包：
            // 0x20 0x20 (2字节命令)
            // 4字节数据长度（大端序）
            // 数据内容
            byte[] packet = BytesDecimalismUtils.merge(
                new byte[]{(byte) 0x20, (byte) 0x20},  // 命令标识
                BytesDecimalismUtils.intTo4Bytes(dataBytes.length),  // 数据长度
                dataBytes                               // 数据内容
            );
            
            logger.debug("发送协议包: 总长度={}, 数据长度={}, 数据内容={}", packet.length, dataBytes.length, requestData);
            
            // 发送请求
            out.write(packet);
            out.flush();
            logger.debug("协议包已发送，等待服务端响应...");
            
            // 读取响应状态（假设服务端返回 UTF-8 字符串）
            DataInputStream dataIn = new DataInputStream(in);
            logger.debug("开始读取服务端响应...");
            String response = dataIn.readUTF();
            logger.info("服务端响应: {}", response);
            
            if (!response.equals("OK")) {
                logger.error("服务端错误: {}", response);
                throw new IOException("Server error: " + response);
            }
            
            // 从断点位置继续写入
            raf.seek(downloadedSize);
            logger.debug("定位文件指针到: {} bytes", downloadedSize);
            
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long lastUpdateTime = System.currentTimeMillis();
            long lastDownloadedSize = downloadedSize;
            long totalRead = 0;
            
            logger.debug("开始接收文件数据，缓冲区大小: {} bytes", BUFFER_SIZE);
            
            while ((bytesRead = dataIn.read(buffer)) != -1) {
                totalRead += bytesRead;
                
                // 检查取消状态
                if (task.isCancelled()) {
                    task.setStatus(DownloadTask.Status.CANCELLED);
                    logger.info("下载已取消: fileName={}, 已接收: {} bytes", task.getFileName(), totalRead);
                    break;
                }
                
                // 检查暂停状态
                while (task.isPaused()) {
                    task.setStatus(DownloadTask.Status.PAUSED);
                    Thread.sleep(100);
                    
                    // 在暂停期间也要检查取消
                    if (task.isCancelled()) {
                        task.setStatus(DownloadTask.Status.CANCELLED);
                        logger.info("暂停期间被取消: fileName={}", task.getFileName());
                        break;
                    }
                }
                
                // 如果在暂停时被取消，退出外层循环
                if (task.isCancelled()) {
                    break;
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
                    
                    logger.debug("下载进度: {}/{} bytes, 速度: {} KB/s", 
                        downloadedSize, task.getFileSize(), String.format("%.2f", speedKB));
                    
                    lastUpdateTime = currentTime;
                    lastDownloadedSize = downloadedSize;
                    
                    if (listener != null) {
                        listener.onProgress(task);
                    }
                }
            }
            
            // 关闭文件写入
            if (raf != null) {
                raf.close();
            }
            
            // 检查是否下载完成
            if (!task.isCancelled()) {
                if (downloadedSize >= task.getFileSize()) {
                    task.setStatus(DownloadTask.Status.COMPLETED);
                    task.setSpeed("0 KB/s");
                    logger.info("下载完成: fileName={}, downloadedSize={}, fileSize={}", 
                        task.getFileName(), downloadedSize, task.getFileSize());
                    if (listener != null) {
                        listener.onCompleted(task);
                    }
                } else {
                    // 连接提前关闭但未下载完成
                    logger.warn("连接关闭但未下载完成: fileName={}, downloadedSize={}, fileSize={}",
                        task.getFileName(), downloadedSize, task.getFileSize());
                    // 保持当前状态，允许用户继续下载
                }
            }
            
        } catch (Exception e) {
            if (task.isCancelled()) {
                // 取消导致的异常，设置状态为已取消
                task.setStatus(DownloadTask.Status.CANCELLED);
                task.setSpeed("0 KB/s");
                logger.info("下载被取消: fileName={}", task.getFileName());
                // 通知监听器（可选）
                if (listener != null) {
                    listener.onProgress(task);  // 更新 UI
                }
            } else {
                task.setStatus(DownloadTask.Status.FAILED);
                logger.error("下载失败: fileName={}", task.getFileName(), e);
                throw e;
            }
        } finally {
            // 确保关闭所有资源
            activeSockets.remove(task);
            
            // 关闭 RandomAccessFile
            if (raf != null) {
                try {
                    raf.close();
                    logger.debug("已关闭 RandomAccessFile: fileName={}", task.getFileName());
                } catch (IOException e) {
                    logger.warn("关闭 RandomAccessFile 时发生错误", e);
                }
            }
            
            // 关闭 Socket
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                    logger.debug("已关闭 Socket: fileName={}", task.getFileName());
                }
            } catch (IOException e) {
                logger.warn("关闭 Socket 时发生错误", e);
            }
        }
    }
    
    /**
     * 强制取消下载（关闭 Socket 连接）
     */
    public void cancelDownload(DownloadTask task) {
        task.setCancelled(true);
        task.setStatus(DownloadTask.Status.CANCELLED);  // 立即设置状态
        task.setSpeed("0 KB/s");  // 清零速度
        
        Socket socket = activeSockets.get(task);
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
                logger.info("强制关闭 Socket 以取消下载: fileName={}", task.getFileName());
            } catch (IOException e) {
                logger.error("关闭 Socket 失败", e);
            }
        } else {
            logger.info("任务已取消: fileName={}, 无活动连接", task.getFileName());
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
