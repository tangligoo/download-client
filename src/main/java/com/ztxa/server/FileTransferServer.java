package com.ztxa.server;

import com.google.gson.Gson;
import com.ztxa.server.model.FileInfo;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * 文件传输服务端示例
 * 提供HTTP API获取文件列表和TCP协议下载文件
 */
public class FileTransferServer {
    private static final int HTTP_PORT = 8080;
    private static final int TCP_PORT = 9090;
    private static final String SHARE_DIR = "./share"; // 共享文件目录
    
    // 模拟存储的appkey(实际应该存储在数据库中)
    private static final Set<String> VALID_APP_KEYS = new HashSet<>();
    
    private static final Gson gson = new Gson();
    
    public static void main(String[] args) throws IOException {
        // 创建共享目录
        File shareDir = new File(SHARE_DIR);
        if (!shareDir.exists()) {
            shareDir.mkdirs();
        }
        
        System.out.println("文件传输服务端启动中...");
        System.out.println("共享目录: " + shareDir.getAbsolutePath());
        System.out.println("HTTP端口: " + HTTP_PORT);
        System.out.println("TCP端口: " + TCP_PORT);
        
        // 启动HTTP服务
        startHttpServer();
        
        // 启动TCP服务
        startTcpServer();
    }
    
    private static void startHttpServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
        server.createContext("/api/files", new FileListHandler());
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
        System.out.println("HTTP服务已启动");
    }
    
    private static void startTcpServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
                System.out.println("TCP服务已启动");
                
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleTcpClient(clientSocket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    static class FileListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            
            // 验证AppKey
            String appKey = exchange.getRequestHeaders().getFirst("X-App-Key");
            if (appKey == null || appKey.isEmpty()) {
                sendResponse(exchange, 401, "Missing App Key");
                return;
            }
            
            // 首次请求自动注册appkey(实际应该有注册流程)
            VALID_APP_KEYS.add(appKey);
            
            // 获取共享目录中的文件列表
            List<FileInfo> fileList = getFileList();
            
            if (fileList.isEmpty()) {
                exchange.sendResponseHeaders(204, -1);
            } else {
                String json = gson.toJson(fileList);
                sendResponse(exchange, 200, json);
            }
        }
        
        private List<FileInfo> getFileList() {
            List<FileInfo> fileList = new ArrayList<>();
            File shareDir = new File(SHARE_DIR);
            
            File[] files = shareDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        FileInfo info = new FileInfo();
                        info.setFileName(file.getName());
                        info.setFilePath(file.getName()); // 相对路径
                        info.setFileSize(file.length());
                        info.setChecksum(String.valueOf(file.lastModified())); // 简单使用时间戳
                        info.setTimestamp(System.currentTimeMillis());
                        fileList.add(info);
                    }
                }
            }
            
            return fileList;
        }
        
        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            byte[] bytes = response.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
    
    private static void handleTcpClient(Socket clientSocket) {
        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {
            
            // 读取请求: appKey|filePath|startPosition
            String request = in.readUTF();
            String[] parts = request.split("\\|"); 
            
            if (parts.length != 3) {
                out.writeUTF("ERROR: Invalid request format");
                return;
            }
            
            String appKey = parts[0];
            String filePath = parts[1];
            long startPosition = Long.parseLong(parts[2]);
            
            // 验证AppKey
            if (!VALID_APP_KEYS.contains(appKey)) {
                out.writeUTF("ERROR: Invalid App Key");
                return;
            }
            
            // 检查文件是否存在
            File file = new File(SHARE_DIR, filePath);
            if (!file.exists() || !file.isFile()) {
                out.writeUTF("ERROR: File not found");
                return;
            }
            
            // 检查起始位置是否有效
            if (startPosition < 0 || startPosition > file.length()) {
                out.writeUTF("ERROR: Invalid start position");
                return;
            }
            
            out.writeUTF("OK");
            out.flush();
            
            // 发送文件内容
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(startPosition);
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                
                while ((bytesRead = raf.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                
                out.flush();
                System.out.println("文件传输完成: " + filePath + " (从位置 " + startPosition + ")");
            }
            
        } catch (Exception e) {
            System.err.println("处理客户端请求失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
