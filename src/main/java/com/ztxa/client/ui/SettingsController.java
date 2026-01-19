package com.ztxa.client.ui;

import com.ztxa.client.config.AppConfig;
import com.ztxa.client.service.ProtocolRegistrationService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;

public class SettingsController {
    @FXML
    private TextField serverHostField;
    @FXML
    private TextField httpPortField;
    @FXML
    private TextField tcpPortField;
    @FXML
    private TextField downloadPathField;
    @FXML
    private TextField pollIntervalField;
    @FXML
    private TextField maxConcurrentField;
    @FXML
    private TextField appKeyField;
    
    private Stage stage;
    
    @FXML
    public void initialize() {
        AppConfig config = AppConfig.getInstance();
        serverHostField.setText(config.getServerHost());
        httpPortField.setText(String.valueOf(config.getServerHttpPort()));
        tcpPortField.setText(String.valueOf(config.getServerTcpPort()));
        downloadPathField.setText(config.getDownloadPath());
        pollIntervalField.setText(String.valueOf(config.getPollInterval()));
        maxConcurrentField.setText(String.valueOf(config.getMaxConcurrentDownloads()));
        appKeyField.setText(config.getAppKey());
    }
    
    @FXML
    private void handleBrowse() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择下载目录");
        
        File currentDir = new File(downloadPathField.getText());
        if (currentDir.exists()) {
            chooser.setInitialDirectory(currentDir);
        }
        
        File selectedDir = chooser.showDialog(stage);
        if (selectedDir != null) {
            downloadPathField.setText(selectedDir.getAbsolutePath());
        }
    }
    
    @FXML
    private void handleSave() {
        try {
            String serverHost = serverHostField.getText().trim();
            int httpPort = Integer.parseInt(httpPortField.getText().trim());
            int tcpPort = Integer.parseInt(tcpPortField.getText().trim());
            String downloadPath = downloadPathField.getText().trim();
            int pollInterval = Integer.parseInt(pollIntervalField.getText().trim());
            int maxConcurrent = Integer.parseInt(maxConcurrentField.getText().trim());
            String appKey = appKeyField.getText().trim();
            
            if (serverHost.isEmpty()) {
                showError("服务器地址不能为空");
                return;
            }
            
            if (httpPort < 1 || httpPort > 65535) {
                showError("HTTP端口必须在1到65535之间");
                return;
            }
            
            if (tcpPort < 1 || tcpPort > 65535) {
                showError("TCP端口必须在1到65535之间");
                return;
            }
            
            if (downloadPath.isEmpty()) {
                showError("下载路径不能为空");
                return;
            }
            
            if (pollInterval < 1) {
                showError("轮询间隔必须大于0");
                return;
            }
            
            if (maxConcurrent < 1 || maxConcurrent > 10) {
                showError("最大并发下载数必须在1到10之间");
                return;
            }
            
            if (appKey.isEmpty()) {
                showError("AppKey不能为空");
                return;
            }
            
            AppConfig config = AppConfig.getInstance();
            config.setServerHost(serverHost);
            config.setServerHttpPort(httpPort);
            config.setServerTcpPort(tcpPort);
            config.setDownloadPath(downloadPath);
            config.setPollInterval(pollInterval);
            config.setMaxConcurrentDownloads(maxConcurrent);
            config.setAppKey(appKey);
            
            showInfo("设置保存成功");
            if (stage != null) {
                stage.close();
            }
        } catch (NumberFormatException e) {
            showError("端口、间隔和并发数必须是有效的数字");
        }
    }
    
    @FXML
    private void handleCancel() {
        if (stage != null) {
            stage.close();
        }
    }

    @FXML
    private void handleRegisterProtocol() {
        ProtocolRegistrationService.register();
        showInfo("协议注册请求已发送。如果成功，您现在可以在浏览器中使用 ztxa://download?fileId=xxx&fileName=xxx&fileSize=xxx 格式唤起下载。");
    }
    
    public void setStage(Stage stage) {
        this.stage = stage;
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
