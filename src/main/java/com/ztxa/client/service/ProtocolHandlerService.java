package com.ztxa.client.service;

import com.ztxa.client.model.FileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 处理自定义协议 ztxa://
 * 格式：ztxa://download?fileId=xxx&fileName=xxx&fileSize=xxx
 */
public class ProtocolHandlerService {
    private static final Logger logger = LoggerFactory.getLogger(ProtocolHandlerService.class);
    private static final String SCHEME = "ztxa://";
    private static final String ACTION_DOWNLOAD = "download";

    public static FileInfo parseUrl(String url) {
        if (url == null || !url.startsWith(SCHEME)) {
            return null;
        }

        try {
            String path = url.substring(SCHEME.length());
            if (!path.startsWith(ACTION_DOWNLOAD)) {
                return null;
            }

            int queryStart = path.indexOf('?');
            if (queryStart == -1) {
                return null;
            }

            String queryString = path.substring(queryStart + 1);
            Map<String, String> params = parseQueryString(queryString);

            String fileId = params.get("fileId");
            String fileName = params.get("fileName");
            String fileSizeStr = params.get("fileSize");

            if (fileId == null || fileName == null || fileSizeStr == null) {
                logger.warn("协议参数不完整: {}", url);
                return null;
            }

            FileInfo info = new FileInfo();
            info.setFileId(fileId);
            info.setFileName(URLDecoder.decode(fileName, StandardCharsets.UTF_8));
            info.setFileSize(Long.parseLong(fileSizeStr));
            info.setFilePath(info.getFileName()); // 默认相对路径同名
            
            return info;
        } catch (Exception e) {
            logger.error("解析协议 URL 失败: " + url, e);
            return null;
        }
    }

    private static Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                params.put(pair.substring(0, idx), pair.substring(idx + 1));
            }
        }
        return params;
    }
}
