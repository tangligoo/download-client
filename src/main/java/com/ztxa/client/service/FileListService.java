package com.ztxa.client.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.reflect.TypeToken;
import com.ztxa.client.config.AppConfig;
import com.ztxa.client.model.FileInfo;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class FileListService {
    private static final Logger logger = LoggerFactory.getLogger(FileListService.class);
    // 自定义 Gson，处理空字符串转 long 的问题
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(long.class, (JsonDeserializer<Long>) (json, typeOfT, context) -> {
                try {
                    String value = json.getAsString();
                    return (value == null || value.trim().isEmpty()) ? 0L : Long.parseLong(value);
                } catch (Exception e) {
                    return 0L;
                }
            })
            .registerTypeAdapter(Long.class, (JsonDeserializer<Long>) (json, typeOfT, context) -> {
                try {
                    String value = json.getAsString();
                    return (value == null || value.trim().isEmpty()) ? 0L : Long.parseLong(value);
                } catch (Exception e) {
                    return 0L;
                }
            })
            .create();
    private CloseableHttpClient httpClient;
    private static final int DEFAULT_PAGE_SIZE = 100; // 每页数量
    
    public FileListService() {
        this.httpClient = HttpClients.createDefault();
        logger.info("文件列表服务已初始化");
    }
    
    /**
     * 获取文件列表（流式消费模式）
     * 
     * <p>后端设计：每次请求返回最多 pageSize 个文件，并从服务端内存中移除已返回的文件。</p>
     * <p>客户端需要循环调用此方法，直到返回的文件数量 < pageSize，说明已获取所有文件。</p>
     * 
     * @param page 页码（从1开始，但后端实际不使用此参数进行分页）
     * @param pageSize 每页数量（建议 100）
     * @return 文件列表，如果返回数量 < pageSize，说明已是最后一批
     */
    public List<FileInfo> fetchFileList(int page, int pageSize) {
        AppConfig config = AppConfig.getInstance();
        String url = config.getServerUrl() + "/downloadApi/files?page=" + page + "&pageSize=" + pageSize;
        logger.debug("请求文件列表: {} (page={}, pageSize={})", url, page, pageSize);
        
        HttpGet request = new HttpGet(url);
        request.setHeader("X-App-Key", config.getAppKey());
        
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getCode();
            logger.debug("文件列表响应状态码: {}", statusCode);
            
            if (statusCode == 200) {
                String json = EntityUtils.toString(response.getEntity());
                logger.debug("后端返回的文件列表数据: {}", json);
                // 后端返回 Set，但 JSON 解析为 List 也完全兼容
                Type listType = new TypeToken<List<FileInfo>>(){}.getType();
                List<FileInfo> files = gson.fromJson(json, listType);
                logger.info("成功获取文件列表，共 {} 个文件", files != null ? files.size() : 0);
                return files;
            } else if (statusCode == 204) {
                logger.info("服务端返回 204 No Content，暂无文件");
                return new ArrayList<>();
            } else {
                logger.error("获取文件列表失败，状态码: {}", statusCode);
                return new ArrayList<>();
            }
        } catch (IOException | ParseException e) {
            logger.error("请求文件列表发生异常", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 获取文件详情（通过 fileId）
     * @param fileId 文件ID
     * @return FileInfo 对象，如果不存在则返回 null
     */
    public FileInfo prepareDownload(String fileId) {
        AppConfig config = AppConfig.getInstance();
        String url = config.getServerUrl() + "/downloadApi/files/" + fileId + "/prepare";
        logger.debug("准备下载文件: fileId={}, url={}", fileId, url);
        
        HttpGet request = new HttpGet(url);
        request.setHeader("X-App-Key", config.getAppKey());
        
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            if (response.getCode() == 200) {
                String json = EntityUtils.toString(response.getEntity());
                logger.debug("后端返回的文件详情数据: {}", json);
                FileInfo fileInfo = gson.fromJson(json, FileInfo.class);
                logger.info("文件准备成功: {} ({})", fileInfo.getFileName(), fileInfo.getFileSize());
                return fileInfo;
            } else if (response.getCode() == 404) {
                logger.warn("文件不存在: {}", fileId);
            } else {
                logger.error("准备下载失败，状态码: {}", response.getCode());
            }
        } catch (IOException | ParseException e) {
            logger.error("请求文件详情失败: fileId={}", fileId, e);
        }
        return null;
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
                logger.info("文件列表服务已关闭");
            }
        } catch (IOException e) {
            logger.error("关闭 HTTP 客户端失败", e);
        }
    }
}
