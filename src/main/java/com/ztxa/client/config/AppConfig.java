package com.ztxa.client.config;

import com.google.gson.Gson;
import com.ztxa.client.database.ConfigDAO;
import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Base64;

public class AppConfig {
    private static final String CONFIG_DIR = System.getProperty("user.home") + File.separator + ".file-transfer-client";
    private static final String CONFIG_FILE = CONFIG_DIR + File.separator + "config.json";
    private static final String APP_KEY_FILE = CONFIG_DIR + File.separator + "appkey.txt";
    
    private String serverHost = "localhost";
    private int serverHttpPort = 8522;
    private int serverTcpPort = 9122;
    private String downloadPath = System.getProperty("user.home") + File.separator + "Downloads";
    private int pollInterval = 5; // 秒
    private int maxConcurrentDownloads = 3; // 最大同时下载数
    private String fileExistsBehavior = "SKIP"; // 文件存在时的行为：SKIP (跳过), OVERWRITE (覆盖)
    private String appKey;
    
    private static AppConfig instance;
    private static final Gson gson = new Gson();
    private static final ConfigDAO configDAO = new ConfigDAO();
    
    private AppConfig() {
        ensureConfigDir();
        loadOrGenerateAppKey();
        loadFromDatabase();
    }
    
    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }
    
    private void ensureConfigDir() {
        File dir = new File(CONFIG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
    
    private void loadOrGenerateAppKey() {
        File keyFile = new File(APP_KEY_FILE);
        if (keyFile.exists()) {
            try {
                appKey = new String(Files.readAllBytes(keyFile.toPath())).trim();
            } catch (IOException e) {
                generateAndSaveAppKey();
            }
        } else {
            generateAndSaveAppKey();
        }
    }
    
    private void loadFromDatabase() {
        serverHost = configDAO.getConfig("serverHost", serverHost);
        serverHttpPort = configDAO.getIntConfig("serverHttpPort", serverHttpPort);
        serverTcpPort = configDAO.getIntConfig("serverTcpPort", serverTcpPort);
        downloadPath = configDAO.getConfig("downloadPath", downloadPath);
        pollInterval = configDAO.getIntConfig("pollInterval", pollInterval);
        maxConcurrentDownloads = configDAO.getIntConfig("maxConcurrentDownloads", maxConcurrentDownloads);
        fileExistsBehavior = configDAO.getConfig("fileExistsBehavior", fileExistsBehavior);
    }
    
    private void generateAndSaveAppKey() {
        // 生成32字节的随机密钥
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        appKey = Base64.getEncoder().encodeToString(keyBytes);
        
        try {
            Files.write(Paths.get(APP_KEY_FILE), appKey.getBytes());
        } catch (IOException e) {
            System.err.println("Failed to save app key: " + e.getMessage());
        }
    }
    
    public String getServerUrl() {
        return "http://" + serverHost +":" + serverHttpPort;
    }
    
    public String getServerHost() {
        return serverHost;
    }
    
    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
        configDAO.saveConfig("serverHost", serverHost);
    }
    
    public int getServerHttpPort() {
        return serverHttpPort;
    }
    
    public void setServerHttpPort(int serverHttpPort) {
        this.serverHttpPort = serverHttpPort;
        configDAO.saveConfig("serverHttpPort", String.valueOf(serverHttpPort));
    }
    
    public int getServerTcpPort() {
        return serverTcpPort;
    }
    
    public void setServerTcpPort(int serverTcpPort) {
        this.serverTcpPort = serverTcpPort;
        configDAO.saveConfig("serverTcpPort", String.valueOf(serverTcpPort));
    }
    
    public void setServerUrl(String serverUrl) {
        // 兼容旧的setServerUrl方法，解析URL
        try {
            serverUrl = serverUrl.replace("http://", "").replace("https://", "");
            String[] parts = serverUrl.split(":");
            if (parts.length >= 1) {
                setServerHost(parts[0]);
            }
            if (parts.length >= 2) {
                setServerHttpPort(Integer.parseInt(parts[1].split("/")[0]));
            }
        } catch (Exception e) {
            System.err.println("Invalid server URL format: " + e.getMessage());
        }
    }
    
    public String getDownloadPath() {
        return downloadPath;
    }
    
    public void setDownloadPath(String downloadPath) {
        this.downloadPath = downloadPath;
        configDAO.saveConfig("downloadPath", downloadPath);
    }
    
    public int getPollInterval() {
        return pollInterval;
    }
    
    public void setPollInterval(int pollInterval) {
        this.pollInterval = pollInterval;
        configDAO.saveConfig("pollInterval", String.valueOf(pollInterval));
    }
    
    public int getMaxConcurrentDownloads() {
        return maxConcurrentDownloads;
    }
    
    public void setMaxConcurrentDownloads(int maxConcurrentDownloads) {
        this.maxConcurrentDownloads = maxConcurrentDownloads;
        configDAO.saveConfig("maxConcurrentDownloads", String.valueOf(maxConcurrentDownloads));
    }
    
    public String getFileExistsBehavior() {
        return fileExistsBehavior;
    }
    
    public void setFileExistsBehavior(String fileExistsBehavior) {
        this.fileExistsBehavior = fileExistsBehavior;
        configDAO.saveConfig("fileExistsBehavior", fileExistsBehavior);
    }
    
    public String getAppKey() {
        return appKey;
    }
    
    public void setAppKey(String appKey) {
        this.appKey = appKey;
        try {
            Files.write(Paths.get(APP_KEY_FILE), appKey.getBytes());
        } catch (IOException e) {
            System.err.println("Failed to save app key: " + e.getMessage());
        }
    }
    
    public String getConfigDir() {
        return CONFIG_DIR;
    }
}
