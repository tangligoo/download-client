# JavaFX 文件传输客户端

这是一个基于JavaFX的文件传输客户端项目,支持系统托盘、定时轮询、断点续传等功能。

## 功能特性

1. **AppKey身份验证**: 客户端自动生成唯一的AppKey,每次请求都会携带此密钥进行身份验证
2. **系统托盘**: 启动后自动最小化到系统托盘,支持右键菜单(传输列表、设置、退出)
3. **定时轮询**: 每隔5秒(可配置)向服务器获取文件列表
4. **传输列表**: 显示所有下载任务的进度、速度、状态
5. **断点续传**: 支持TCP协议下载文件,断点续传功能,下载中断后可继续

## 项目结构

```
qoder/
├── src/main/java/
│   └── com/qoder/
│       ├── client/              # 客户端代码
│       │   ├── FileTransferApp.java          # 主应用程序
│       │   ├── config/
│       │   │   └── AppConfig.java            # 配置管理
│       │   ├── model/
│       │   │   ├── FileInfo.java             # 文件信息模型
│       │   │   └── DownloadTask.java         # 下载任务模型
│       │   ├── service/
│       │   │   ├── FileListService.java      # 文件列表服务
│       │   │   └── FileDownloadService.java  # 文件下载服务
│       │   └── ui/
│       │       ├── TransferListController.java   # 传输列表控制器
│       │       └── SettingsController.java       # 设置控制器
│       └── server/              # 服务端示例代码
│           ├── FileTransferServer.java       # 服务端主程序
│           └── model/
│               └── FileInfo.java             # 文件信息模型
├── src/main/resources/
│   └── fxml/
│       ├── transfer-list.fxml   # 传输列表界面
│       └── settings.fxml        # 设置界面
└── pom.xml                      # Maven配置文件
```

## 运行要求

- JDK 17 或更高版本
- Maven 3.6 或更高版本

## 运行步骤

### 1. 编译项目

```bash
mvn clean package
```

### 2. 启动服务端

```bash
# 编译服务端
mvn compile

# 运行服务端
mvn exec:java -Dexec.mainClass="com.qoder.server.FileTransferServer"
```

服务端会自动创建 `./share` 目录作为共享文件夹。你可以将需要传输的文件放入该目录。

服务端监听端口:
- HTTP API端口: 8080
- TCP下载端口: 9090

### 3. 启动客户端

```bash
mvn javafx:run
```

客户端启动后会:
1. 自动最小化到系统托盘
2. 生成AppKey(保存在 `~/.file-transfer-client/appkey.txt`)
3. 每5秒轮询一次服务端文件列表
4. 如果有新文件,自动打开传输列表并开始下载

### 4. 使用客户端

- **查看传输列表**: 双击托盘图标或右键选择"传输列表"
- **修改设置**: 右键托盘图标,选择"设置"
  - 配置服务器地址
  - 修改下载路径
  - 调整轮询间隔
- **退出程序**: 右键托盘图标,选择"退出"

## 配置说明

配置文件保存在: `~/.file-transfer-client/config.json`

默认配置:
- 服务器地址: http://localhost:8080
- 下载路径: ~/Downloads
- 轮询间隔: 5秒

## 测试步骤

1. 启动服务端
2. 在 `./share` 目录中放入测试文件
3. 启动客户端
4. 等待客户端轮询到文件(最多5秒)
5. 传输列表会自动打开并开始下载

## 断点续传测试

1. 开始下载一个大文件
2. 在下载过程中,在传输列表中右键点击任务,选择"取消"
3. 稍后再次启动下载(将文件重新放入share目录或重启客户端)
4. 客户端会自动从上次中断的位置继续下载

## API接口说明

### HTTP API

**获取文件列表**
```
GET /api/files
Headers: 
  X-App-Key: <your-app-key>

Response 200:
[
  {
    "fileName": "test.txt",
    "filePath": "test.txt",
    "fileSize": 1024,
    "checksum": "...",
    "timestamp": 1234567890
  }
]

Response 204: 没有文件
Response 401: AppKey无效
```

### TCP协议

**下载文件请求格式**
```
请求: appKey|filePath|startPosition
响应: OK 或 ERROR: <错误信息>
然后传输文件字节流
```

## 注意事项

1. 服务端的AppKey验证是简化实现,首次连接会自动注册。生产环境应该实现完整的注册流程
2. 文件校验使用的是简单的时间戳,生产环境应该使用MD5或SHA256
3. 系统托盘在某些Linux桌面环境可能不支持
4. Windows系统确保有系统托盘显示权限

## 技术栈

- JavaFX 21.0.1 - GUI框架
- Apache HttpClient 5 - HTTP客户端
- Gson - JSON解析
- Java AWT - 系统托盘支持
- Java NIO - 文件操作

## 许可证

MIT License
