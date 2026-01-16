package com.qoder.client.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.qoder.client.config.AppConfig;
import com.qoder.client.model.FileInfo;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class FileListService {
    private static final Gson gson = new Gson();
    private CloseableHttpClient httpClient;
    private static final int DEFAULT_PAGE_SIZE = 100; // 每页数量
    
    public FileListService() {
        this.httpClient = HttpClients.createDefault();
    }
    
    /**
     * 获取文件列表（分页）
     * @param page 页码（从1开始）
     * @param pageSize 每页数量
     * @return 文件列表
     */
    public List<FileInfo> fetchFileList(int page, int pageSize) {
        AppConfig config = AppConfig.getInstance();
        String url = config.getServerUrl() + "/api/files?page=" + page + "&pageSize=" + pageSize;
        
        HttpGet request = new HttpGet(url);
        request.setHeader("X-App-Key", config.getAppKey());
        
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            if (statusCode == 200) {
                String json = EntityUtils.toString(response.getEntity());
                Type listType = new TypeToken<List<FileInfo>>(){}.getType();
                return gson.fromJson(json, listType);
            } else if (statusCode == 204) {
                // 没有文件
                return new ArrayList<>();
            } else {
                System.err.println("Failed to fetch file list: " + statusCode);
                return new ArrayList<>();
            }
        } catch (IOException | ParseException e) {
            System.err.println("Error fetching file list: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * 获取文件总数
     * @return 文件总数
     */
    public int fetchFileCount() {
        AppConfig config = AppConfig.getInstance();
        String url = config.getServerUrl() + "/api/files/count";
        
        HttpGet request = new HttpGet(url);
        request.setHeader("X-App-Key", config.getAppKey());
        
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            if (response.getCode() == 200) {
                String json = EntityUtils.toString(response.getEntity());
                return Integer.parseInt(json);
            }
        } catch (IOException | ParseException | NumberFormatException e) {
            System.err.println("Error fetching file count: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * 获取所有文件列表（兼容旧版本，不推荐用于大数据量）
     * @return 文件列表
     */
    public List<FileInfo> fetchFileList() {
        return fetchFileList(1, DEFAULT_PAGE_SIZE);
    }
    
    public void close() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
