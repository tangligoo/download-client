package com.ztxa.client.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.kordamp.bootstrapfx.BootstrapFX;

public class CustomTrayMenu extends Stage {

    private final Runnable showTransferListAction;
    private final Runnable showSettingsAction;
    private final Runnable exitAction;

    public CustomTrayMenu(Runnable showTransferListAction, Runnable showSettingsAction, Runnable exitAction) {
        this.showTransferListAction = showTransferListAction;
        this.showSettingsAction = showSettingsAction;
        this.exitAction = exitAction;
        
        // 创建一个隐藏的 Utility 阶段作为所有者，防止在任务栏显示图标
        Stage dummyOwner = new Stage();
        dummyOwner.initStyle(StageStyle.UTILITY);
        dummyOwner.setOpacity(0);
        dummyOwner.setHeight(0);
        dummyOwner.setWidth(0);
        dummyOwner.setX(-10000); // 移出屏幕
        dummyOwner.show(); 

        initOwner(dummyOwner);
        initStyle(StageStyle.UNDECORATED);
        setAlwaysOnTop(true);
        initUI();
        setupAutoHide();
    }

    private void initUI() {
        VBox root = new VBox(2);
        root.setPadding(new Insets(5));
        
        // 模仿菜单外观的样式
        root.setStyle("-fx-background-color: white; " +
                     "-fx-border-color: #e0e0e0; " +
                     "-fx-border-width: 1; " +
                     "-fx-background-radius: 8; " +
                     "-fx-border-radius: 8; " +
                     "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 10, 0, 0, 4);");

        Button transferButton = createMenuButton("传输列表");
        transferButton.setOnAction(e -> {
            hide();
            if (showTransferListAction != null) showTransferListAction.run();
        });

        Button settingsButton = createMenuButton("设置");
        settingsButton.setOnAction(e -> {
            hide();
            if (showSettingsAction != null) showSettingsAction.run();
        });

        Button exitButton = createMenuButton("退出");
        exitButton.setStyle(exitButton.getStyle() + "-fx-text-fill: #d9534f;"); // 退出按钮红色
        exitButton.setOnAction(e -> {
            hide();
            if (exitAction != null) exitAction.run();
        });

        root.getChildren().addAll(transferButton, settingsButton, exitButton);
        
        Scene scene = new Scene(root);
        scene.setFill(null); // 设置场景透明以支持圆角
        scene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
        // 如果有通用的CSS也可以加上
        
        setScene(scene);
    }

    private Button createMenuButton(String text) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setPrefHeight(35);
        button.setStyle("-fx-background-color: transparent; " +
                       "-fx-border-color: transparent; " +
                       "-fx-alignment: center-left; " +
                       "-fx-padding: 0 15 0 15; " +
                       "-fx-font-size: 13px; " +
                       "-fx-cursor: hand;");
        
        button.setOnMouseEntered(e -> button.setStyle(button.getStyle().replace("transparent", "#f0f7ff") + "-fx-text-fill: #1976d2;"));
        button.setOnMouseExited(e -> button.setStyle(button.getStyle().replace("#f0f7ff", "transparent").replace("-fx-text-fill: #1976d2;", "")));
        
        return button;
    }

    private void setupAutoHide() {
        focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                hide();
            }
        });
    }

    public void showMenu(double screenX, double screenY) {
        // 先显示以计算尺寸
        setOpacity(0);
        show();
        
        Platform.runLater(() -> {
            double width = getWidth();
            double height = getHeight();
            
            // 默认显示在鼠标上方
            double x = screenX - width / 2;
            double y = screenY - height - 5;
            
            // 边缘检测
            if (y < 0) y = screenY + 5;
            
            setX(x);
            setY(y);
            setOpacity(1);
            toFront();
            requestFocus();
        });
    }
}
