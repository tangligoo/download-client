package com.qoder.client.ui;

import com.qoder.client.config.AppConfig;
import com.qoder.client.database.DownloadTaskDAO;
import com.qoder.client.model.DownloadTask;
import com.qoder.client.model.FileInfo;
import com.qoder.client.service.FileDownloadService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.ProgressBarTableCell;

import java.io.File;
import java.util.List;

public class TransferListController {
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
            }
        }
    }

    private void cancelAllTasks() {
        for (DownloadTask task : tasks) {
            task.setCancelled(true);
        }
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
            taskDAO.deleteTask(task.getFilePath());
        }
    }
    
    private void loadTasksFromDatabase() {
        List<DownloadTask> savedTasks = taskDAO.getAllTasks();
        for (DownloadTask task : savedTasks) {
            tasks.add(task);
            // 如果任务未完成，重新开始下载
            if (task.getStatus().equals(DownloadTask.Status.DOWNLOADING.getText()) ||
                task.getStatus().equals(DownloadTask.Status.PAUSED.getText())) {
                task.setStatus(DownloadTask.Status.PAUSED);
                startDownload(task);
            }
        }
    }
    
    public void addDownloadTasks(List<FileInfo> fileList) {
        AppConfig config = AppConfig.getInstance();
        for (FileInfo fileInfo : fileList) {
            // 使用fileId作为唯一标识
            String identifier = fileInfo.getFileId() != null ? fileInfo.getFileId() : fileInfo.getFilePath();
            
            // 检查是否已存在（通过fileId或filePath）
            boolean exists = tasks.stream()
                    .anyMatch(task -> {
                        if (task.getFileId() != null && fileInfo.getFileId() != null) {
                            return task.getFileId().equals(fileInfo.getFileId());
                        }
                        return task.getFilePath().equals(identifier);
                    });
                    
            if (!exists) {
                String savePath = config.getDownloadPath() + File.separator + fileInfo.getFileName();
                DownloadTask task;
                
                // 如果有fileId，使用fileId构造函数
                if (fileInfo.getFileId() != null) {
                    task = new DownloadTask(
                            fileInfo.getFileId(),
                            fileInfo.getFileName(),
                            fileInfo.getFileSize(),
                            savePath,
                            true  // useFileId = true
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
                startDownload(task);
            }
        }
    }
    
    private void startDownload(DownloadTask task) {
        downloadService.downloadFile(task, new FileDownloadService.DownloadProgressListener() {
            @Override
            public void onProgress(DownloadTask task) {
                Platform.runLater(() -> {
                    taskTableView.refresh();
                    taskDAO.updateTaskProgress(task.getFilePath(), task.getDownloadedSize(), task.getStatus());
                });
            }
            
            @Override
            public void onCompleted(DownloadTask task) {
                Platform.runLater(() -> {
                    taskTableView.refresh();
                    taskDAO.updateTaskProgress(task.getFilePath(), task.getDownloadedSize(), task.getStatus());
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
                taskDAO.deleteTask(task.getFilePath()); // 从数据库删除
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
