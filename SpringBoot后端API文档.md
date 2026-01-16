# SpringBoot后端API接口文档

## 🎯 网盘模式说明

用户选择文件/文件夹后，后端返回文件列表（包含fileId），JavaFX客户端通过fileId下载文件。

---

## 📡 HTTP API接口

### 1. 获取文件列表（支持文件夹）

**接口地址**：`GET /api/files`

**请求头**：
```http
X-App-Key: {appKey}
```

**请求参数**：
```
folderId (可选): 文件夹ID，不传则返回根目录
recursive (可选): 是否递归获取子文件夹，默认false
page (可选): 页码，从1开始
pageSize (可选): 每页数量，默认100
```

**响应示例**：
```json
[
  {
    "fileId": "file_123456",
    "fileName": "文档.pdf",
    "filePath": "/documents/文档.pdf",
    "fileSize": 1048576,
    "fileType": "file",
    "checksum": "md5_hash_here",
    "timestamp": 1705324800000
  },
  {
    "fileId": "folder_789",
    "fileName": "图片",
    "filePath": "/图片",
    "fileSize": 0,
    "fileType": "folder",
    "timestamp": 1705324800000
  }
]
```

---

### 2. 获取文件总数

**接口地址**：`GET /api/files/count`

**请求头**：
```http
X-App-Key: {appKey}
```

**响应示例**：
```json
100000
```

---

### 3. 文件下载准备（可选）

**接口地址**：`GET /api/files/{fileId}/prepare`

**说明**：获取文件元数据，用于断点续传

**响应示例**：
```json
{
  "fileId": "file_123456",
  "fileName": "大文件.zip",
  "fileSize": 104857600,
  "checksum": "sha256_hash"
}
```

---

## 🔌 TCP下载服务

### 端口配置
- 默认端口：`9090`
- 可在客户端设置中修改

### 协议格式

#### 1. 客户端请求
```
格式：appKey|fileId|startPosition
示例：abc123xyz|file_123456|0
```

**字段说明**：
- `appKey`: 身份验证密钥
- `fileId`: 文件ID（后端返回的唯一标识）
- `startPosition`: 断点位置（字节偏移量）

#### 2. 服务端响应
```
第一步：返回状态
- "OK" - 验证通过，开始传输
- "ERROR:message" - 错误信息

第二步：传输文件数据（二进制流）
从startPosition位置开始发送文件内容
```

---

## 🏗️ SpringBoot实现示例

### 1. FileController.java

```java
@RestController
@RequestMapping("/api/files")
public class FileController {
    
    @Autowired
    private FileService fileService;
    
    /**
     * 获取文件列表
     */
    @GetMapping
    public ResponseEntity<List<FileInfoDTO>> getFileList(
            @RequestHeader("X-App-Key") String appKey,
            @RequestParam(required = false) String folderId,
            @RequestParam(defaultValue = "false") boolean recursive,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        
        // 验证AppKey
        if (!fileService.validateAppKey(appKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        // 获取文件列表
        List<FileInfoDTO> files = fileService.getFileList(
            folderId, recursive, page, pageSize
        );
        
        if (files.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        
        return ResponseEntity.ok(files);
    }
    
    /**
     * 获取文件总数
     */
    @GetMapping("/count")
    public ResponseEntity<Integer> getFileCount(
            @RequestHeader("X-App-Key") String appKey,
            @RequestParam(required = false) String folderId) {
        
        if (!fileService.validateAppKey(appKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        int count = fileService.getFileCount(folderId);
        return ResponseEntity.ok(count);
    }
    
    /**
     * 获取文件信息
     */
    @GetMapping("/{fileId}/prepare")
    public ResponseEntity<FileInfoDTO> prepareDownload(
            @RequestHeader("X-App-Key") String appKey,
            @PathVariable String fileId) {
        
        if (!fileService.validateAppKey(appKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        FileInfoDTO fileInfo = fileService.getFileInfo(fileId);
        if (fileInfo == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(fileInfo);
    }
}
```

### 2. FileInfoDTO.java

```java
@Data
public class FileInfoDTO {
    private String fileId;        // 文件ID
    private String fileName;      // 文件名
    private String filePath;      // 显示路径
    private Long fileSize;        // 文件大小
    private String fileType;      // file/folder
    private String checksum;      // 文件校验和
    private Long timestamp;       // 时间戳
}
```

### 3. TcpDownloadServer.java

```java
@Component
public class TcpDownloadServer {
    
    @Value("${tcp.download.port:9090}")
    private int port;
    
    @Autowired
    private FileService fileService;
    
    @PostConstruct
    public void start() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("TCP Download Server started on port " + port);
                
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleClient(clientSocket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private void handleClient(Socket socket) {
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            
            // 读取请求：appKey|fileId|startPosition
            String request = in.readUTF();
            String[] parts = request.split("\\|");
            
            if (parts.length != 3) {
                out.writeUTF("ERROR:Invalid request format");
                return;
            }
            
            String appKey = parts[0];
            String fileId = parts[1];
            long startPosition = Long.parseLong(parts[2]);
            
            // 验证AppKey
            if (!fileService.validateAppKey(appKey)) {
                out.writeUTF("ERROR:Invalid AppKey");
                return;
            }
            
            // 获取文件
            File file = fileService.getPhysicalFile(fileId);
            if (file == null || !file.exists()) {
                out.writeUTF("ERROR:File not found");
                return;
            }
            
            // 发送OK状态
            out.writeUTF("OK");
            out.flush();
            
            // 传输文件（从startPosition开始）
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(startPosition);
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = raf.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
```

### 4. FileService.java

```java
@Service
public class FileService {
    
    @Autowired
    private FileRepository fileRepository;
    
    @Value("${file.storage.path}")
    private String storagePath;
    
    /**
     * 验证AppKey
     */
    public boolean validateAppKey(String appKey) {
        // 从数据库或配置中验证
        return "your-secret-app-key".equals(appKey);
    }
    
    /**
     * 获取文件列表
     */
    public List<FileInfoDTO> getFileList(
            String folderId, boolean recursive, int page, int pageSize) {
        
        // 从数据库查询
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        List<FileEntity> files;
        
        if (folderId == null) {
            files = fileRepository.findByParentIdIsNull(pageable);
        } else {
            if (recursive) {
                files = fileRepository.findByParentIdRecursive(folderId, pageable);
            } else {
                files = fileRepository.findByParentId(folderId, pageable);
            }
        }
        
        return files.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取文件总数
     */
    public int getFileCount(String folderId) {
        if (folderId == null) {
            return (int) fileRepository.countByParentIdIsNull();
        }
        return (int) fileRepository.countByParentId(folderId);
    }
    
    /**
     * 获取文件信息
     */
    public FileInfoDTO getFileInfo(String fileId) {
        FileEntity file = fileRepository.findById(fileId).orElse(null);
        return file != null ? toDTO(file) : null;
    }
    
    /**
     * 获取物理文件
     */
    public File getPhysicalFile(String fileId) {
        FileEntity fileEntity = fileRepository.findById(fileId).orElse(null);
        if (fileEntity == null) {
            return null;
        }
        return new File(storagePath + "/" + fileEntity.getStoragePath());
    }
    
    private FileInfoDTO toDTO(FileEntity entity) {
        FileInfoDTO dto = new FileInfoDTO();
        dto.setFileId(entity.getId());
        dto.setFileName(entity.getFileName());
        dto.setFilePath(entity.getFilePath());
        dto.setFileSize(entity.getFileSize());
        dto.setFileType(entity.isDirectory() ? "folder" : "file");
        dto.setChecksum(entity.getChecksum());
        dto.setTimestamp(entity.getCreatedTime().getTime());
        return dto;
    }
}
```

---

## 🗄️ 数据库设计

```sql
CREATE TABLE file_metadata (
    id VARCHAR(50) PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    storage_path VARCHAR(1000),     -- 实际存储路径
    file_size BIGINT NOT NULL,
    is_directory BOOLEAN DEFAULT FALSE,
    parent_id VARCHAR(50),
    checksum VARCHAR(64),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parent_id (parent_id),
    INDEX idx_file_path (file_path)
);

CREATE TABLE app_keys (
    id INT PRIMARY KEY AUTO_INCREMENT,
    app_key VARCHAR(100) UNIQUE NOT NULL,
    user_id INT,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 🔐 安全建议

1. **AppKey管理**
   - 使用强随机生成的密钥
   - 定期轮换密钥
   - 支持多个密钥（便于灰度升级）

2. **传输加密**
   - 使用TLS/SSL加密TCP连接
   - HTTP API使用HTTPS

3. **访问控制**
   - 文件级别的权限验证
   - 限流和防DDoS

4. **文件校验**
   - 传输后校验MD5/SHA256
   - 检测文件完整性

---

## 📊 性能优化

1. **分页加载**
   - 客户端按需请求
   - 服务端限制每页最大100条

2. **缓存策略**
   - Redis缓存文件列表
   - 本地缓存文件元数据

3. **并发下载**
   - 支持多线程下载
   - TCP连接池

4. **断点续传**
   - 服务端支持Range请求
   - 客户端记录下载进度

---

## 🧪 测试示例

### cURL测试

```bash
# 获取文件列表
curl -H "X-App-Key: your-app-key" \
  http://localhost:8080/api/files

# 获取文件总数
curl -H "X-App-Key: your-app-key" \
  http://localhost:8080/api/files/count

# 获取特定文件夹
curl -H "X-App-Key: your-app-key" \
  "http://localhost:8080/api/files?folderId=folder_123"
```

### TCP测试（使用telnet）

```bash
telnet localhost 9090
# 发送：your-app-key|file_123456|0
# 接收：OK + 文件数据流
```

---

## 🎉 总结

JavaFX客户端现在完全支持：
- ✅ 通过文件ID下载
- ✅ 兼容旧的文件路径模式
- ✅ 数据库自动迁移
- ✅ 文件夹递归下载
- ✅ 断点续传
- ✅ 大数据量优化

后端只需实现上述API即可完美对接！
