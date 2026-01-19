package com.ztxa.client.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * 进程间通信服务，用于处理单实例参数传递
 */
public class IpcService {
    private static final Logger logger = LoggerFactory.getLogger(IpcService.class);
    private static final int IPC_PORT = 9123; // IPC 专用端口
    private static ServerSocket serverSocket;
    private static boolean running = false;

    /**
     * 启动 IPC 服务端（主实例调用）
     */
    public static void startServer(Consumer<String> messageHandler) {
        if (running) return;
        
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(IPC_PORT, 50, InetAddress.getByName("127.0.0.1"));
                running = true;
                logger.info("IPC 服务端已启动，监听端口: {}", IPC_PORT);
                
                while (running) {
                    try (Socket socket = serverSocket.accept();
                         BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                        
                        String message = in.readLine();
                        if (message != null) {
                            logger.info("收到 IPC 消息: {}", message);
                            messageHandler.accept(message);
                        }
                    } catch (Exception e) {
                        if (running) {
                            logger.error("处理 IPC 客户端连接失败", e);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("启动 IPC 服务端失败", e);
            }
        }, "IPC-Server").start();
    }

    /**
     * 发送消息给主实例（从实例调用）
     */
    public static boolean sendMessage(String message) {
        try (Socket socket = new Socket("127.0.0.1", IPC_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(message);
            logger.info("成功发送 IPC 消息: {}", message);
            return true;
        } catch (Exception e) {
            logger.error("发送 IPC 消息失败: {}", e.getMessage());
            return false;
        }
    }

    public static void stopServer() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (Exception e) {
            // ignore
        }
    }
}
