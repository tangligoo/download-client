package com.qoder.client;

import com.qoder.client.config.AppConfig;
import com.qoder.client.model.FileInfo;
import com.qoder.client.service.FileListService;
import com.qoder.client.service.InstanceLockService;
import com.qoder.client.ui.CustomTrayMenu;
import com.qoder.client.ui.SettingsController;
import com.qoder.client.ui.TransferListController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.kordamp.bootstrapfx.BootstrapFX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class FileTransferApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(FileTransferApp.class);
    private Stage primaryStage;
    private Stage transferStage;
    private TransferListController transferController;
    private TrayIcon trayIcon;
    private CustomTrayMenu customTrayMenu;
    private FileListService fileListService;
    private Timer pollTimer;
    
    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        
        // 1. 检查单实例锁
        AppConfig config = AppConfig.getInstance();
        if (!InstanceLockService.acquireLock(config.getConfigDir())) {
            showAlreadyRunningAlert();
            Platform.exit();
            System.exit(0);
            return;
        }
        
        // 设置系统托盘
        Platform.setImplicitExit(false);
        setupSystemTray();
        
        // 初始化服务
        fileListService = new FileListService();
        
        // 启动定时任务
        startPollingTask();
        
        // 不显示主窗口,直接最小化到托盘
        primaryStage.setTitle("文件传输客户端");
        primaryStage.hide();
    }
    
    private void showAlreadyRunningAlert() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("程序已运行");
        alert.setHeaderText(null);
        alert.setContentText("文件传输客户端已经在运行中，请检查系统托盘。");
        alert.showAndWait();
    }

    private void setupSystemTray() {
        if (!SystemTray.isSupported()) {
            System.err.println("系统托盘不支持!");
            return;
        }
        
        try {
            // 创建托盘图标
            java.awt.Image trayImage = createTrayImage();
            SystemTray tray = SystemTray.getSystemTray();
            
            // 创建 TrayIcon (不传 PopupMenu)
            trayIcon = new TrayIcon(trayImage, "文件传输客户端");
            trayIcon.setImageAutoSize(true);
            
            // 鼠标事件监听
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        // 右键点击：在 JavaFX 线程中显示自定义菜单
                        double screenX = e.getXOnScreen();
                        double screenY = e.getYOnScreen();
                        Platform.runLater(() -> showCustomMenu(screenX, screenY));
                    }
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                        // 左键双击：打开传输列表
                        Platform.runLater(() -> showTransferList());
                    }
                }
            });
            
            tray.add(trayIcon);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showCustomMenu(double x, double y) {
        if (customTrayMenu == null) {
            customTrayMenu = new CustomTrayMenu(
                this::showTransferList,
                this::showSettings,
                this::handleExit
            );
        }
        
        if (customTrayMenu.isShowing()) {
            customTrayMenu.hide();
        }
        customTrayMenu.showMenu(x, y);
    }

    private void handleExit() {
        stopPollingTask();
        if (fileListService != null) {
            fileListService.close();
        }
        Platform.exit();
        System.exit(0);
    }
    
    private java.awt.Image createTrayImage() {
        // 使用 64x64 的高分辨率画布，让图标在缩放时更清晰
        int size = 64;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        
        // 开启抗锯齿，使边缘更平滑
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        
        // 绘制一个蓝色圆形背景 (尽量填满 64x64)
        g.setColor(new java.awt.Color(0, 120, 215));
        g.fillOval(2, 2, size - 4, size - 4);
        
        // 绘制白色的下载箭头
        g.setColor(java.awt.Color.WHITE);
        // 缩放箭头坐标以适应 64x64，稍微调大一点
        int[] xPoints = {32, 16, 26, 26, 38, 38, 48};
        int[] yPoints = {52, 34, 34, 12, 12, 34, 34};
        g.fillPolygon(xPoints, yPoints, 7);
        
        g.dispose();
        return image;
    }
    
    private void startPollingTask() {
        AppConfig config = AppConfig.getInstance();
        pollTimer = new Timer(true);
        int intervalSeconds = config.getPollInterval();
        logger.info("启动文件轮询任务，间隔: {} 秒", intervalSeconds);
        
        pollTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    logger.debug("开始轮询文件列表...");
                    List<FileInfo> fileList = fileListService.fetchFileList();
                    
                    if (fileList != null && !fileList.isEmpty()) {
                        logger.info("轮询到 {} 个文件", fileList.size());
                        Platform.runLater(() -> {
                            // 只有在有新任务时才自动打开窗口
                            if (transferController != null) {
                                int beforeSize = transferController.getTasks().size();
                                transferController.addDownloadTasks(fileList);
                                int afterSize = transferController.getTasks().size();
                                
                                logger.debug("任务数量: {} -> {}", beforeSize, afterSize);
                                
                                // 如果添加了新任务且窗口未显示，则打开窗口
                                if (afterSize > beforeSize && (transferStage == null || !transferStage.isShowing())) {
                                    logger.info("检测到新任务，打开传输列表窗口");
                                    showTransferList();
                                }
                            } else {
                                // 首次有文件时才打开
                                logger.info("首次检测到文件，打开传输列表窗口");
                                showTransferList();
                                if (transferController != null) {
                                    transferController.addDownloadTasks(fileList);
                                }
                            }
                        });
                    } else {
                        logger.debug("暂无文件");
                    }
                } catch (Exception e) {
                    logger.error("轮询文件列表失败", e);
                }
            }
        }, 1000, intervalSeconds * 1000L);
    }
    
    private void stopPollingTask() {
        if (pollTimer != null) {
            pollTimer.cancel();
        }
    }
    
    private void showTransferList() {
        if (transferStage == null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/transfer-list.fxml"));
                Scene scene = new Scene(loader.load());
                scene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
                scene.getStylesheets().add(getClass().getResource("/css/transfer-list.css").toExternalForm());
                
                transferController = loader.getController();
                transferStage = new Stage();
                transferStage.setTitle("文件传输列表");
                transferStage.setScene(scene);
                
                // 关闭窗口时隐藏而不是退出
                transferStage.setOnCloseRequest(e -> {
                    e.consume();
                    transferStage.hide();
                });
                
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        
        if (!transferStage.isShowing()) {
            transferStage.show();
            transferStage.toFront();
        }
    }
    
    private void showSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
            scene.getStylesheets().add(getClass().getResource("/css/settings.css").toExternalForm());
            
            SettingsController controller = loader.getController();
            Stage settingsStage = new Stage();
            controller.setStage(settingsStage);
            
            settingsStage.setTitle("设置");
            settingsStage.setScene(scene);
            settingsStage.setResizable(false);
            settingsStage.show();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
