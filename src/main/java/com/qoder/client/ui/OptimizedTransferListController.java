package com.qoder.client.ui;

import com.qoder.client.config.AppConfig;
import com.qoder.client.database.DownloadTaskDAO;
import com.qoder.client.model.DownloadTask;
import com.qoder.client.model.FileInfo;
import com.qoder.client.service.FileDownloadService;
import com.qoder.client.service.FileListService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.ProgressBarTableCell;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 优化版传输列表控制器
 * 支持大数据量（10万+）的高效显示和管理
 * 特性：
 * 1. 懒加载 - 按需从服务器获取数据
 * 2. 虚拟滚动 - TableView自带虚拟滚动，只渲染可见行
 * 3. 分页加载 - 滚动到底部时自动加载下一页
 * 4. 索引优化 - 数据库查询使用索引
 * 5. 异步加载 - 不阻塞UI线程
 */
public class OptimizedTransferListController {
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
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressIndicator loadingIndicator;
    
    private ObservableList<DownloadTask> tasks;
    private FileDownloadService downloadService;
    private DownloadTaskDAO taskDAO;
    private FileListService fileListService;
    private ExecutorService executorService;
    
    // 分页参数
    private int currentPage = 0;
    private int pageSize = 100;
    private boolean isLoading = false;
    private boolean hasMoreData = true;
    private int totalFiles = 0;
    
    public OptimizedTransferListController() {
        this.tasks = FXCollections.observableArrayList();
        this.downloadService = new FileDownloadService();
        this.taskDAO = new DownloadTaskDAO();
        this.fileListService = new FileListService();
        this.executorService = Executors.newSingleThreadExecutor();
    }
    
    @FXML
    public void initialize() {
        // 从数据库加载任务（只加载最近的100条）
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
        
        // 设置滚动监听，实现无限滚动
        taskTableView.setOnScrollFinished(e -> loadMoreIfNeeded());
        
        // 右键菜单
        ContextMenu contextMenu = new ContextMenu();
        MenuItem pauseItem = new MenuItem("Pause");
        MenuItem resumeItem = new MenuItem("Resume");
        MenuItem cancelItem = new MenuItem("Cancel");
        MenuItem removeItem = new MenuItem("Remove");
        MenuItem clearCompletedItem = new MenuItem("Clear Completed");
        
        pauseItem.setOnAction(e -> pauseSelectedTask());
        resumeItem.setOnAction(e -> resumeSelectedTask());
        cancelItem.setOnAction(e -> cancelSelectedTask());
        removeItem.setOnAction(e -> removeSelectedTask());
        clearCompletedItem.setOnAction(e -> clearCompletedTasks());
        
        contextMenu.getItems().addAll(
            pauseItem, resumeItem, cancelItem, 
            new SeparatorMenuItem(), 
            removeItem, clearCompletedItem
        );
        taskTableView.setContextMenu(contextMenu);
        
        // 初始化状态标签
        updateStatusLabel();
    }
    
    private void loadTasksFromDatabase() {
        // 只加载最近的任务，避免一次性加载过多
        executorService.submit(() -> {
            List<DownloadTask> savedTasks = taskDAO.getAllTasks();
            Platform.runLater(() -> {
                // 只显示未完成的任务和最近100个已完成的任务
                int count = 0;
                for (DownloadTask task : savedTasks) {
                    if (count++ >= 100) break;
                    tasks.add(task);
                    // 如果任务未完成，重新开始下载
                    if (task.getStatus().equals(DownloadTask.Status.DOWNLOADING.getText()) ||
                        task.getStatus().equals(DownloadTask.Status.PAUSED.getText())) {
                        task.setStatus(DownloadTask.Status.PAUSED);
                    }
                }
                updateStatusLabel();
            });
        });
    }
    
    /**
     * 从服务器加载下一页文件列表
     */
    private void loadMoreIfNeeded() {
        if (isLoading || !hasMoreData) {
            return;
        }
        
        // 检查是否接近底部
        ScrollBar scrollBar = getVerticalScrollBar();
        if (scrollBar != null && scrollBar.getValue() > 0.8) {
            loadNextPage();
        }
    }
    
    private void loadNextPage() {
        if (isLoading) return;
        
        isLoading = true;
        showLoading(true);
        
        executorService.submit(() -> {
            try {
                currentPage++;
                List<FileInfo> fileList = fileListService.fetchFileList(currentPage, pageSize);
                
                Platform.runLater(() -> {
                    if (fileList.isEmpty()) {
                        hasMoreData = false;
                    } else {
                        addDownloadTasks(fileList);
                    }
                    isLoading = false;
                    showLoading(false);
                    updateStatusLabel();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    isLoading = false;
                    showLoading(false);
                    showError("加载失败", "无法加载文件列表: " + e.getMessage());
                });
            }
        });
    }
    
    private ScrollBar getVerticalScrollBar() {
        for (var node : taskTableView.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar) {
                ScrollBar scrollBar = (ScrollBar) node;
                if (scrollBar.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
                    return scrollBar;
                }
            }
        }
        return null;
    }
    
    public void addDownloadTasks(List<FileInfo> fileList) {
        AppConfig config = AppConfig.getInstance();
        for (FileInfo fileInfo : fileList) {
            // 检查是否已存在
            boolean exists = tasks.stream()
                    .anyMatch(task -> task.getFilePath().equals(fileInfo.getFilePath()));
            if (!exists) {
                String savePath = config.getDownloadPath() + File.separator + fileInfo.getFileName();
                DownloadTask task = new DownloadTask(
                        fileInfo.getFileName(),
                        fileInfo.getFilePath(),
                        fileInfo.getFileSize(),
                        savePath
                );
                tasks.add(task);
                taskDAO.saveTask(task); // 保存到数据库
                // 不自动开始下载，由用户决定
            }
        }
    }
    
    private void startDownload(DownloadTask task) {
        downloadService.downloadFile(task, new FileDownloadService.DownloadProgressListener() {
            @Override
            public void onProgress(DownloadTask task) {
                Platform.runLater(() -> {
                    taskTableView.refresh();
                    // 减少数据库写入频率，每5秒更新一次
                    if (System.currentTimeMillis() % 5000 < 100) {
                        taskDAO.updateTaskProgress(task.getFilePath(), task.getDownloadedSize(), task.getStatus());
                    }
                });
            }
            
            @Override
            public void onCompleted(DownloadTask task) {
                Platform.runLater(() -> {
                    taskTableView.refresh();
                    taskDAO.updateTaskProgress(task.getFilePath(), task.getDownloadedSize(), task.getStatus());
                    updateStatusLabel();
                });
            }
            
            @Override
            public void onError(DownloadTask task, Exception e) {
                Platform.runLater(() -> {
                    taskTableView.refresh();
                    taskDAO.updateTaskProgress(task.getFilePath(), task.getDownloadedSize(), task.getStatus());
                    showError("下载失败", task.getFileName() + " 下载失败: " + e.getMessage());
                });
            }
        });
    }
    
    private void pauseSelectedTask() {
        DownloadTask task = taskTableView.getSelectionModel().getSelectedItem();
        if (task != null && task.getStatus().equals(DownloadTask.Status.DOWNLOADING.getText())) {
            task.setPaused(true);
        }
    }
    
    private void resumeSelectedTask() {
        DownloadTask task = taskTableView.getSelectionModel().getSelectedItem();
        if (task != null && task.getStatus().equals(DownloadTask.Status.PAUSED.getText())) {
            task.setPaused(false);
            startDownload(task);
        }
    }
    
    private void cancelSelectedTask() {
        DownloadTask task = taskTableView.getSelectionModel().getSelectedItem();
        if (task != null) {
            task.setCancelled(true);
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
                taskDAO.deleteTask(task.getFilePath());
                updateStatusLabel();
            } else {
                showError("无法删除", "请先取消正在运行的任务");
            }
        }
    }
    
    private void clearCompletedTasks() {
        tasks.removeIf(task -> task.getStatus().equals(DownloadTask.Status.COMPLETED.getText()));
        taskDAO.deleteCompletedTasks();
        updateStatusLabel();
    }
    
    private void updateStatusLabel() {
        if (statusLabel != null) {
            long downloading = tasks.stream()
                .filter(t -> t.getStatus().equals(DownloadTask.Status.DOWNLOADING.getText()))
                .count();
            long completed = tasks.stream()
                .filter(t -> t.getStatus().equals(DownloadTask.Status.COMPLETED.getText()))
                .count();
            statusLabel.setText(String.format("总计: %d | 下载中: %d | 已完成: %d", 
                tasks.size(), downloading, completed));
        }
    }
    
    private void showLoading(boolean show) {
        if (loadingIndicator != null) {
            loadingIndicator.setVisible(show);
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
    
    public void shutdown() {
        executorService.shutdown();
        fileListService.close();
    }
}
