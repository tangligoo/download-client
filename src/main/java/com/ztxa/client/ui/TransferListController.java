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
    private TableView<DownloadTask> activeTaskTableView;
    @FXML
    private TableColumn<DownloadTask, String> activeFileNameColumn;
    @FXML
    private TableColumn<DownloadTask, String> activeSizeColumn;
    @FXML
    private TableColumn<DownloadTask, Double> activeProgressColumn;
    @FXML
    private TableColumn<DownloadTask, String> activeSpeedColumn;
    @FXML
    private TableColumn<DownloadTask, String> activeStatusColumn;

    @FXML
    private TableView<DownloadTask> historyTaskTableView;
    @FXML
    private TableColumn<DownloadTask, String> historyFileNameColumn;
    @FXML
    private TableColumn<DownloadTask, String> historySizeColumn;
    @FXML
    private TableColumn<DownloadTask, Double> historyProgressColumn;
    @FXML
    private TableColumn<DownloadTask, String> historyStatusColumn;
    
    private ObservableList<DownloadTask> activeTasks;
    private ObservableList<DownloadTask> historyTasks;
    private FileDownloadService downloadService;
    private DownloadTaskDAO taskDAO;
    
    public TransferListController() {
        this.activeTasks = FXCollections.observableArrayList();
        this.historyTasks = FXCollections.observableArrayList();
        this.downloadService = new FileDownloadService();
        this.taskDAO = new DownloadTaskDAO();
    }
    
    @FXML
    public void initialize() {
        // 从数据库加载任务
        loadTasksFromDatabase();
        
        // 设置正在下载表格
        setupTableColumns(activeFileNameColumn, activeSizeColumn, activeProgressColumn, activeStatusColumn);
        activeSpeedColumn.setCellValueFactory(cellData -> cellData.getValue().speedProperty());
        activeTaskTableView.setItems(activeTasks);
        
        // 设置下载历史表格
        setupTableColumns(historyFileNameColumn, historySizeColumn, historyProgressColumn, historyStatusColumn);
        historyTaskTableView.setItems(historyTasks);
        
        // 1. 设置右键菜单
        setupContextMenus();

        // 2. 启动等待中的下载 (支持重启自动恢复)
        startPendingDownloads();
    }

    private void setupTableColumns(TableColumn<DownloadTask, String> nameCol, 
                                 TableColumn<DownloadTask, String> sizeCol, 
                                 TableColumn<DownloadTask, Double> progressCol, 
                                 TableColumn<DownloadTask, String> statusCol) {
        nameCol.setCellValueFactory(cellData -> cellData.getValue().fileNameProperty());
        sizeCol.setCellValueFactory(cellData -> new SimpleStringProperty(formatSize(cellData.getValue().getFileSize())));
        progressCol.setCellValueFactory(cellData -> cellData.getValue().progressProperty().asObject());
        progressCol.setCellFactory(ProgressBarTableCell.forTableColumn());
        statusCol.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
    }

    private void setupContextMenus() {
        // 正在下载表格的菜单
        setupRowContextMenu(activeTaskTableView, true);
        setupTableContextMenu(activeTaskTableView, true);
        
        // 历史记录表格的菜单
        setupRowContextMenu(historyTaskTableView, false);
        setupTableContextMenu(historyTaskTableView, false);
    }

    private void setupRowContextMenu(TableView<DownloadTask> tableView, boolean isActive) {
        tableView.setRowFactory(tv -> {
            TableRow<DownloadTask> row = new TableRow<>();
            ContextMenu rowMenu = new ContextMenu();
            
            if (isActive) {
                MenuItem pauseItem = new MenuItem("暂停");
                MenuItem resumeItem = new MenuItem("继续");
                MenuItem cancelItem = new MenuItem("取消");
                
                pauseItem.setOnAction(e -> pauseSelectedTask(tableView));
                resumeItem.setOnAction(e -> resumeSelectedTask(tableView));
                cancelItem.setOnAction(e -> cancelSelectedTask(tableView));
                
                rowMenu.getItems().addAll(pauseItem, resumeItem, cancelItem, new SeparatorMenuItem());
            }
            
            MenuItem removeItem = new MenuItem("删除记录");
            removeItem.getStyleClass().add("menu-item-danger");
            removeItem.setStyle("-fx-text-fill: #d9534f;");
            removeItem.setOnAction(e -> removeSelectedTask(tableView));
            
            rowMenu.getItems().add(removeItem);
            
            row.contextMenuProperty().bind(
                javafx.beans.binding.Bindings.when(row.emptyProperty())
                .then((ContextMenu)null)
                .otherwise(rowMenu)
            );
            return row;
        });
    }

    private void setupTableContextMenu(TableView<DownloadTask> tableView, boolean isActive) {
        ContextMenu tableMenu = new ContextMenu();
        
        if (isActive) {
            MenuItem pauseAllItem = new MenuItem("全部暂停");
            MenuItem resumeAllItem = new MenuItem("全部继续");
            MenuItem cancelAllItem = new MenuItem("全部取消");
            
            pauseAllItem.setOnAction(e -> pauseAllTasks());
            resumeAllItem.setOnAction(e -> resumeAllTasks());
            cancelAllItem.setOnAction(e -> cancelAllTasks());
            
            tableMenu.getItems().addAll(pauseAllItem, resumeAllItem, cancelAllItem, new SeparatorMenuItem());
        }
        
        MenuItem removeAllItem = new MenuItem(isActive ? "清空列表 (仅限已结束任务)" : "清空历史记录");
        removeAllItem.getStyleClass().add("menu-item-danger");
        removeAllItem.setStyle("-fx-text-fill: #d9534f;");
        removeAllItem.setOnAction(e -> {
            if (isActive) removeAllFinishedFromActive();
            else removeAllHistory();
        });
        
        tableMenu.getItems().add(removeAllItem);
        tableView.setContextMenu(tableMenu);
    }

    private void pauseAllTasks() {
        for (DownloadTask task : activeTasks) {
            if (task.getStatus().equals(DownloadTask.Status.DOWNLOADING.getText())) {
                task.setPaused(true);
            }
        }
    }

    private void resumeAllTasks() {
        for (DownloadTask task : activeTasks) {
            if (task.getStatus().equals(DownloadTask.Status.PAUSED.getText())) {
                task.setPaused(false);
                // 重新启动下载线程
                startDownload(task);
            }
        }
    }

    private void cancelAllTasks() {
        logger.info("取消所有进行中的任务");
        List<DownloadTask> tasksToCancel = new ArrayList<>(activeTasks);
        
        for (DownloadTask task : tasksToCancel) {
            downloadService.cancelDownload(task);
        }
        
        Platform.runLater(() -> {
            activeTaskTableView.refresh();
            historyTaskTableView.refresh();
        });
    }

    private void removeAllFinishedFromActive() {
        List<DownloadTask> toMove = activeTasks.stream()
            .filter(task -> {
                String status = task.getStatus();
                return status.equals(DownloadTask.Status.COMPLETED.getText()) ||
                       status.equals(DownloadTask.Status.FAILED.getText()) ||
                       status.equals(DownloadTask.Status.CANCELLED.getText());
            })
            .toList();
            
        for (DownloadTask task : toMove) {
            activeTasks.remove(task);
            if (!historyTasks.contains(task)) {
                historyTasks.add(0, task);
            }
        }
    }

    private void removeAllHistory() {
        for (DownloadTask task : historyTasks) {
            taskDAO.deleteTask(task.getTaskId());
        }
        historyTasks.clear();
    }
    
    private void loadTasksFromDatabase() {
        // 优化建议：加载全部正在下载的任务，但只加载最近的 500 条历史记录
        List<DownloadTask> savedTasks = taskDAO.getAllTasks();
        logger.info("从数据库加载 {} 个任务", savedTasks.size());
        
        for (DownloadTask task : savedTasks) {
            String status = task.getStatus();
            
            // 归类到 active 或 history
            if (status.equals(DownloadTask.Status.DOWNLOADING.getText()) ||
                status.equals(DownloadTask.Status.WAITING.getText()) ||
                status.equals(DownloadTask.Status.PAUSED.getText())) {
                
                // 重启恢复逻辑：DOWNLOADING -> WAITING
                if (status.equals(DownloadTask.Status.DOWNLOADING.getText())) {
                    task.setStatus(DownloadTask.Status.WAITING);
                }
                activeTasks.add(task);
            } else {
                // 限制历史记录显示数量（可选，这里先全部加载，后续可优化为分页）
                if (historyTasks.size() < 1000) {
                    historyTasks.add(task);
                }
            }
        }
    }
    
    public void addDownloadTasks(List<FileInfo> fileList) {
        logger.info("添加下载任务: {} 个文件", fileList.size());
        AppConfig config = AppConfig.getInstance();
        int addedCount = 0;
        
        for (FileInfo fileInfo : fileList) {
            if (fileInfo.getFileName() == null || fileInfo.getFileName().trim().isEmpty()) continue;
            
            // 路径处理逻辑保持不变
            String relativeDir = fileInfo.getFilePath();
            if (relativeDir == null) relativeDir = "";
            relativeDir = relativeDir.replace("/", File.separator).replace("\\", File.separator);
            while (relativeDir.startsWith(File.separator)) relativeDir = relativeDir.substring(1);
            
            File downloadRoot = new File(config.getDownloadPath());
            File saveFile;
            if (relativeDir.isEmpty()) {
                saveFile = new File(downloadRoot, fileInfo.getFileName());
            } else {
                if (!relativeDir.endsWith(File.separator)) relativeDir += File.separator;
                saveFile = new File(downloadRoot, relativeDir + fileInfo.getFileName());
            }
            
            if (saveFile.exists() && saveFile.isDirectory()) {
                // 自动重命名逻辑保持不变
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
            }
            String savePath = saveFile.getAbsolutePath();

            // 检查 activeTasks 中是否已有相同路径的任务
            boolean isProcessing = activeTasks.stream()
                    .anyMatch(task -> task.getSavePath() != null && task.getSavePath().equals(savePath));
            
            if (isProcessing) continue;
            
            DownloadTask task = new DownloadTask(
                    fileInfo.getFileId(),
                    fileInfo.getFileName(),
                    fileInfo.getFilePath(),
                    fileInfo.getFileSize(),
                    savePath
            );
            
            activeTasks.add(0, task); // 新任务放在最前面
            taskDAO.saveTask(task);
            addedCount++;
        }
        
        startPendingDownloads();
    }
    
    private void startDownload(DownloadTask task) {
        downloadService.downloadFile(task, new FileDownloadService.DownloadProgressListener() {
            @Override
            public void onProgress(DownloadTask task) {
                Platform.runLater(() -> {
                    activeTaskTableView.refresh();
                    taskDAO.updateTaskProgress(task.getTaskId(), task.getDownloadedSize(), task.getStatus());
                });
            }
            
            @Override
            public void onCompleted(DownloadTask task) {
                Platform.runLater(() -> {
                    moveToHistory(task);
                    startPendingDownloads();
                });
            }
            
            @Override
            public void onError(DownloadTask task, Exception e) {
                Platform.runLater(() -> {
                    moveToHistory(task);
                    showError("下载失败", task.getFileName() + " 下载失败: " + e.getMessage());
                    startPendingDownloads();
                });
            }
        });
    }

    private void moveToHistory(DownloadTask task) {
        activeTasks.remove(task);
        if (!historyTasks.contains(task)) {
            historyTasks.add(0, task);
        }
        taskDAO.updateTaskProgress(task.getTaskId(), task.getDownloadedSize(), task.getStatus());
        activeTaskTableView.refresh();
        historyTaskTableView.refresh();
    }
    
    private void startPendingDownloads() {
        AppConfig config = AppConfig.getInstance();
        int maxConcurrent = config.getMaxConcurrentDownloads();
        
        long downloadingCount = activeTasks.stream()
                .filter(task -> task.getStatus().equals(DownloadTask.Status.DOWNLOADING.getText()))
                .count();
        
        if (downloadingCount >= maxConcurrent) return;
        
        List<DownloadTask> waitingTasks = activeTasks.stream()
                .filter(task -> task.getStatus().equals(DownloadTask.Status.WAITING.getText()))
                .limit(maxConcurrent - downloadingCount)
                .toList();
        
        for (DownloadTask task : waitingTasks) {
            startDownload(task);
        }
    }
    
    private void pauseSelectedTask(TableView<DownloadTask> tableView) {
        DownloadTask task = tableView.getSelectionModel().getSelectedItem();
        if (task != null) {
            String status = task.getStatus();
            if (status.equals(DownloadTask.Status.DOWNLOADING.getText()) ||
                status.equals(DownloadTask.Status.WAITING.getText())) {
                task.setPaused(true);
            }
        }
    }
    
    private void resumeSelectedTask(TableView<DownloadTask> tableView) {
        DownloadTask task = tableView.getSelectionModel().getSelectedItem();
        if (task != null && task.getStatus().equals(DownloadTask.Status.PAUSED.getText())) {
            task.setPaused(false);
            startDownload(task);
        }
    }
    
    private void cancelSelectedTask(TableView<DownloadTask> tableView) {
        DownloadTask task = tableView.getSelectionModel().getSelectedItem();
        if (task != null) {
            downloadService.cancelDownload(task);
            Platform.runLater(() -> moveToHistory(task));
        }
    }
    
    private void removeSelectedTask(TableView<DownloadTask> tableView) {
        DownloadTask task = tableView.getSelectionModel().getSelectedItem();
        if (task != null) {
            String status = task.getStatus();
            if (status.equals(DownloadTask.Status.COMPLETED.getText()) ||
                status.equals(DownloadTask.Status.FAILED.getText()) ||
                status.equals(DownloadTask.Status.CANCELLED.getText())) {
                
                activeTasks.remove(task);
                historyTasks.remove(task);
                taskDAO.deleteTask(task.getTaskId());
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
    
    public ObservableList<DownloadTask> getActiveTasks() {
        return activeTasks;
    }
    
    public ObservableList<DownloadTask> getHistoryTasks() {
        return historyTasks;
    }
}
