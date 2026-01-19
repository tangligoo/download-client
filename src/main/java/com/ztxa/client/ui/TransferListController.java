package com.ztxa.client.ui;

import com.ztxa.client.config.AppConfig;
import com.ztxa.client.database.DownloadTaskDAO;
import com.ztxa.client.model.DownloadTask;
import com.ztxa.client.model.FileInfo;
import com.ztxa.client.service.FileDownloadService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.ProgressBarTableCell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TransferListController {
    private static final Logger logger = LoggerFactory.getLogger(TransferListController.class);
    @FXML
    private TableView<DownloadTask> taskTableView;
    @FXML
    private TableColumn<DownloadTask, String> fileNameColumn;
    @FXML
    private TableColumn<DownloadTask, String> sizeColumn;
    @FXML
    private TableColumn<DownloadTask, Double> progressColumn;
    @FXML
    private TableColumn<DownloadTask, String> speedColumn;
    @FXML
    private TableColumn<DownloadTask, String> statusColumn;
    
    private ObservableList<DownloadTask> tasks;
    private FileDownloadService downloadService;
    private DownloadTaskDAO taskDAO;
    
    public TransferListController() {
        this.tasks = FXCollections.observableArrayList();
        this.downloadService = new FileDownloadService();
        this.taskDAO = new DownloadTaskDAO();
    }
    
    @FXML
    public void initialize() {
        // 从数据库加载任务
        loadTasksFromDatabase();
        
        fileNameColumn.setCellValueFactory(cellData -> cellData.getValue().fileNameProperty());
        
        sizeColumn.setCellValueFactory(cellData -> {
            long size = cellData.getValue().getFileSize();
            return new SimpleStringProperty(formatSize(size));
        });
        
        progressColumn.setCellValueFactory(cellData -> cellData.getValue().progressProperty().asObject());
        progressColumn.setCellFactory(ProgressBarTableCell.forTableColumn());
        
        speedColumn.setCellValueFactory(cellData -> cellData.getValue().speedProperty());
        statusColumn.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        
        taskTableView.setItems(tasks);
        
        // 1. 设置行右键菜单 (仅在文件行上显示)
        setupRowContextMenu();
        
        // 2. 设置表格空白区域右键菜单
        setupTableContextMenu();

        // 3. 启动等待中的下载 (支持重启自动恢复)
        startPendingDownloads();
    }

    private void setupRowContextMenu() {
        taskTableView.setRowFactory(tv -> {
            TableRow<DownloadTask> row = new TableRow<>();
            ContextMenu rowMenu = new ContextMenu();
            
            MenuItem pauseItem = new MenuItem("暂停");
            MenuItem resumeItem = new MenuItem("继续");
            MenuItem cancelItem = new MenuItem("取消");
            MenuItem removeItem = new MenuItem("删除");
            removeItem.getStyleClass().add("menu-item-danger");
            removeItem.setStyle("-fx-text-fill: #d9534f;"); // 强制代码设置颜色
            
            pauseItem.setOnAction(e -> pauseSelectedTask());
            resumeItem.setOnAction(e -> resumeSelectedTask());
            cancelItem.setOnAction(e -> cancelSelectedTask());
            removeItem.setOnAction(e -> removeSelectedTask());
            
            rowMenu.getItems().addAll(pauseItem, resumeItem, cancelItem, new SeparatorMenuItem(), removeItem);
            
            // 只有当行不为空时才显示菜单
            row.contextMenuProperty().bind(
                javafx.beans.binding.Bindings.when(row.emptyProperty())
                .then((ContextMenu)null)
                .otherwise(rowMenu)
            );
            return row;
        });
    }

    private void setupTableContextMenu() {
        ContextMenu tableMenu = new ContextMenu();
        
        MenuItem pauseAllItem = new MenuItem("全部暂停");
        MenuItem resumeAllItem = new MenuItem("全部继续");
        MenuItem cancelAllItem = new MenuItem("全部取消");
        MenuItem removeAllItem = new MenuItem("全部删除");
        removeAllItem.getStyleClass().add("menu-item-danger");
        removeAllItem.setStyle("-fx-text-fill: #d9534f;"); // 强制代码设置颜色
        
        pauseAllItem.setOnAction(e -> pauseAllTasks());
        resumeAllItem.setOnAction(e -> resumeAllTasks());
        cancelAllItem.setOnAction(e -> cancelAllTasks());
        removeAllItem.setOnAction(e -> removeAllTasks());
        
        tableMenu.getItems().addAll(pauseAllItem, resumeAllItem, cancelAllItem, new SeparatorMenuItem(), removeAllItem);
        
        // 设置给 TableView
        taskTableView.setContextMenu(tableMenu);
    }

    private void pauseAllTasks() {
        for (DownloadTask task : tasks) {
            if (task.getStatus().equals(DownloadTask.Status.DOWNLOADING.getText())) {
                task.setPaused(true);
            }
        }
    }

    private void resumeAllTasks() {
        for (DownloadTask task : tasks) {
            if (task.getStatus().equals(DownloadTask.Status.PAUSED.getText())) {
                task.setPaused(false);
                // 重新启动下载线程
                startDownload(task);
            }
        }
    }

    private void cancelAllTasks() {
        logger.info("取消所有任务");
        // 只取消正在进行中的任务（DOWNLOADING/WAITING/PAUSED），不影响已完成/失败的任务
        List<DownloadTask> tasksToCancel = tasks.stream()
                .filter(task -> {
                    String status = task.getStatus();
                    return status.equals(DownloadTask.Status.DOWNLOADING.getText()) ||
                           status.equals(DownloadTask.Status.WAITING.getText()) ||
                           status.equals(DownloadTask.Status.PAUSED.getText());
                })
                .toList();
        
        logger.info("取消 {} 个正在进行的任务", tasksToCancel.size());
        
        for (DownloadTask task : tasksToCancel) {
            // 强制取消下载（关闭 Socket）
            downloadService.cancelDownload(task);
        }
        
        // 立即刷新 UI 和更新数据库
        Platform.runLater(() -> {
            taskTableView.refresh();
            for (DownloadTask task : tasksToCancel) {
                taskDAO.updateTaskProgress(task.getTaskId(), task.getDownloadedSize(), task.getStatus());
            }
        });
    }

    private void removeAllTasks() {
        // 只删除已完成、失败或取消的任务
        List<DownloadTask> toRemove = tasks.stream()
            .filter(task -> {
                String status = task.getStatus();
                return status.equals(DownloadTask.Status.COMPLETED.getText()) ||
                       status.equals(DownloadTask.Status.FAILED.getText()) ||
                       status.equals(DownloadTask.Status.CANCELLED.getText());
            })
            .toList();
            
        if (toRemove.isEmpty() && !tasks.isEmpty()) {
            showError("提示", "没有可删除的任务（请先取消正在运行的任务）");
            return;
        }

        for (DownloadTask task : toRemove) {
            tasks.remove(task);
            taskDAO.deleteTask(task.getTaskId());
        }
    }
    
    private void loadTasksFromDatabase() {
        List<DownloadTask> savedTasks = taskDAO.getAllTasks();
        logger.info("从数据库加载 {} 个任务", savedTasks.size());
        
        List<DownloadTask> resumedTasks = new ArrayList<>();
        List<DownloadTask> otherTasks = new ArrayList<>();

        for (DownloadTask task : savedTasks) {
            String status = task.getStatus();
            logger.debug("加载任务: fileName={}, status={}", task.getFileName(), status);
            
            // 如果任务在退出前处于下载中，恢复为等待中，并标记为高优先级
            if (status.equals(DownloadTask.Status.DOWNLOADING.getText())) {
                task.setStatus(DownloadTask.Status.WAITING);
                logger.info("将中断的任务恢复为等待状态（高优先级）: {}", task.getFileName());
                resumedTasks.add(task);
            } else {
                otherTasks.add(task);
            }
        }
        
        // 核心修复：将之前正在下载的任务放在列表最前面，确保 startPendingDownloads 优先启动它们
        tasks.addAll(resumedTasks);
        tasks.addAll(otherTasks);
    }
    
    public void addDownloadTasks(List<FileInfo> fileList) {
        logger.info("添加下载任务: {} 个文件", fileList.size());
        AppConfig config = AppConfig.getInstance();
        int addedCount = 0;
        List<DownloadTask> newTasks = new ArrayList<>();
        
        // 第一步：批量创建任务，不立即开始下载
        for (FileInfo fileInfo : fileList) {
            // 基础校验：必须有文件名
            if (fileInfo.getFileName() == null || fileInfo.getFileName().trim().isEmpty()) {
                logger.warn("跳过无效任务：文件名为空, fileId={}", fileInfo.getFileId());
                continue;
            }
            
            // 拼接本地保存路径：下载根目录 + 相对目录 + 文件名
            String relativeDir = fileInfo.getFilePath();
            if (relativeDir == null) relativeDir = "";
            
            // 规范化相对路径
            relativeDir = relativeDir.replace("/", File.separator).replace("\\", File.separator);
            
            // 去掉开头的分隔符，避免出现双斜杠
            while (relativeDir.startsWith(File.separator)) {
                relativeDir = relativeDir.substring(1);
            }
            
            File downloadRoot = new File(config.getDownloadPath());
            File saveFile;
            
            if (relativeDir.isEmpty()) {
                saveFile = new File(downloadRoot, fileInfo.getFileName());
            } else {
                if (!relativeDir.endsWith(File.separator)) {
                    relativeDir += File.separator;
                }
                saveFile = new File(downloadRoot, relativeDir + fileInfo.getFileName());
            }
            
            // 自动避让同名文件夹冲突：如果该路径已经是一个文件夹，则为文件重命名（例如 electron -> electron(1)）
            if (saveFile.exists() && saveFile.isDirectory()) {
                int count = 1;
                String baseName = fileInfo.getFileName();
                String extension = "";
                int dotIndex = baseName.lastIndexOf('.');
                if (dotIndex > 0) {
                    extension = baseName.substring(dotIndex);
                    baseName = baseName.substring(0, dotIndex);
                }
                
                File tempFile = saveFile;
                while (tempFile.exists() && tempFile.isDirectory()) {
                    tempFile = new File(saveFile.getParentFile(), baseName + "(" + count + ")" + extension);
                    count++;
                }
                saveFile = tempFile;
                logger.info("检测到同名文件夹冲突，自动更名为: {}", saveFile.getName());
            }
            
            String savePath = saveFile.getAbsolutePath();

            // 检查是否有正在处理的任务
            // 根据用户要求：只判断相同目录下的同名文件。即使 fileId 相同，如果保存路径不同也视为不同任务。
            boolean isProcessing = tasks.stream()
                    .anyMatch(task -> {
                        // 通过本地保存路径来唯一判断任务，这能保证“同目录下同名文件”的唯一性
                        if (task.getSavePath() != null && task.getSavePath().equals(savePath)) {
                            String status = task.getStatus();
                            return status.equals(DownloadTask.Status.DOWNLOADING.getText()) ||
                                   status.equals(DownloadTask.Status.PAUSED.getText()) ||
                                   status.equals(DownloadTask.Status.WAITING.getText());
                        }
                        return false;
                    });
            
            // 如果正在处理，跳过
            if (isProcessing) {
                logger.debug("该路径文件正在处理中，跳过: {}", savePath);
                continue;
            }
            
            // 已完成/已取消/失败的任务保留，新文件作为新任务添加
            logger.info("添加新下载任务: fileName={}, savePath={}", fileInfo.getFileName(), savePath);
            
            DownloadTask task;
            
            // 如果有fileId，使用新构造函数保存 fileId 和 相对路径 filePath
            if (fileInfo.getFileId() != null) {
                task = new DownloadTask(
                        fileInfo.getFileId(),
                        fileInfo.getFileName(),
                        fileInfo.getFilePath(),
                        fileInfo.getFileSize(),
                        savePath
                );
            } else {
                // 兼容旧版本，使用filePath
                task = new DownloadTask(
                        fileInfo.getFileName(),
                        fileInfo.getFilePath(),
                        fileInfo.getFileSize(),
                        savePath
                );
            }
            
            tasks.add(task);
            taskDAO.saveTask(task); // 保存到数据库
            newTasks.add(task);
            addedCount++;
        }
        
        logger.info("实际添加了 {} 个新任务", addedCount);
        
        // 第二步：根据并发控制开始下载
        startPendingDownloads();
    }
    
    private void startDownload(DownloadTask task) {
        logger.info("开始下载: fileName={}, fileId={}, fileSize={}", task.getFileName(), task.getFileId(), task.getFileSize());
        downloadService.downloadFile(task, new FileDownloadService.DownloadProgressListener() {
            @Override
            public void onProgress(DownloadTask task) {
                Platform.runLater(() -> {
                    taskTableView.refresh();
                    taskDAO.updateTaskProgress(task.getTaskId(), task.getDownloadedSize(), task.getStatus());
                });
            }
            
            @Override
            public void onCompleted(DownloadTask task) {
                Platform.runLater(() -> {
                    taskTableView.refresh();
                    taskDAO.updateTaskProgress(task.getTaskId(), task.getDownloadedSize(), task.getStatus());
                    // 下载完成后，尝试启动下一个等待中的任务
                    startPendingDownloads();
                });
            }
            
            @Override
            public void onError(DownloadTask task, Exception e) {
                Platform.runLater(() -> {
                    taskTableView.refresh();
                    taskDAO.updateTaskProgress(task.getTaskId(), task.getDownloadedSize(), task.getStatus());
                    showError("下载失败", task.getFileName() + " 下载失败: " + e.getMessage());
                    // 错误后，尝试启动下一个等待中的任务
                    startPendingDownloads();
                });
            }
        });
    }
    
    /**
     * 启动等待中的下载任务（根据并发控制）
     */
    private void startPendingDownloads() {
        AppConfig config = AppConfig.getInstance();
        int maxConcurrent = config.getMaxConcurrentDownloads();
        
        // 统计当前正在下载的任务数
        long downloadingCount = tasks.stream()
                .filter(task -> task.getStatus().equals(DownloadTask.Status.DOWNLOADING.getText()))
                .count();
        
        logger.debug("当前下载中: {} 个, 最大并发: {}", downloadingCount, maxConcurrent);
        
        // 如果已达到最大并发数，返回
        if (downloadingCount >= maxConcurrent) {
            logger.debug("已达到最大并发数，等待中...");
            return;
        }
        
        // 找出所有等待中的任务
        List<DownloadTask> waitingTasks = tasks.stream()
                .filter(task -> task.getStatus().equals(DownloadTask.Status.WAITING.getText()))
                .limit(maxConcurrent - downloadingCount)  // 只启动需要的数量
                .toList();
        
        logger.info("启动 {} 个等待中的任务", waitingTasks.size());
        
        // 启动等待中的任务
        for (DownloadTask task : waitingTasks) {
            startDownload(task);
        }
    }
    
    private void pauseSelectedTask() {
        DownloadTask task = taskTableView.getSelectionModel().getSelectedItem();
        if (task != null) {
            String status = task.getStatus();
            logger.debug("暂停任务: fileName={}, currentStatus={}", task.getFileName(), status);
            
            if (status.equals(DownloadTask.Status.DOWNLOADING.getText()) ||
                status.equals(DownloadTask.Status.WAITING.getText())) {
                task.setPaused(true);
                logger.info("任务已暂停: {}", task.getFileName());
            }
        }
    }
    
    private void resumeSelectedTask() {
        DownloadTask task = taskTableView.getSelectionModel().getSelectedItem();
        if (task != null && task.getStatus().equals(DownloadTask.Status.PAUSED.getText())) {
            task.setPaused(false);
            // 重新启动下载线程
            startDownload(task);
        }
    }
    
    private void cancelSelectedTask() {
        DownloadTask task = taskTableView.getSelectionModel().getSelectedItem();
        if (task != null) {
            logger.info("取消任务: fileName={}, status={}", task.getFileName(), task.getStatus());
            // 强制取消下载（关闭 Socket）
            downloadService.cancelDownload(task);
            
            // 立即刷新 UI 和更新数据库
            Platform.runLater(() -> {
                taskTableView.refresh();
                taskDAO.updateTaskProgress(task.getTaskId(), task.getDownloadedSize(), task.getStatus());
            });
        }
    }
    
    private void removeSelectedTask() {
        DownloadTask task = taskTableView.getSelectionModel().getSelectedItem();
        if (task != null) {
            String status = task.getStatus();
            if (status.equals(DownloadTask.Status.COMPLETED.getText()) ||
                status.equals(DownloadTask.Status.FAILED.getText()) ||
                status.equals(DownloadTask.Status.CANCELLED.getText())) {
                tasks.remove(task);
                taskDAO.deleteTask(task.getTaskId()); // 从数据库删除
            } else {
                showError("无法删除", "请先取消正在运行的任务");
            }
        }
    }
    
    private String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }
    
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public ObservableList<DownloadTask> getTasks() {
        return tasks;
    }
}
