package com.ztxa.client.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 注册自定义协议 ztxa:// 到操作系统
 */
public class ProtocolRegistrationService {
    private static final Logger logger = LoggerFactory.getLogger(ProtocolRegistrationService.class);

    public static void register() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            registerWindows();
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            registerLinux();
        } else {
            logger.warn("当前操作系统不支持自动注册协议: {}", os);
        }
    }

    private static void registerLinux() {
        try {
            String home = System.getProperty("user.home");
            File appsDir = new File(home, ".local/share/applications");
            if (!appsDir.exists()) appsDir.mkdirs();

            File desktopFile = new File(appsDir, "ztxa-client.desktop");
            
            String javaHome = System.getProperty("java.home");
            String javaExe = javaHome + File.separator + "bin" + File.separator + "java";
            String classPath = System.getProperty("java.class.path");
            String mainClass = "com.ztxa.client.Launcher";
            
            // 构造启动命令
            String execCommand = String.format("%s -cp %s %s %%u", javaExe, classPath, mainClass);

            String content = "[Desktop Entry]\n" +
                    "Name=Download Client\n" +
                    "Exec=" + execCommand + "\n" +
                    "Type=Application\n" +
                    "Terminal=false\n" +
                    "MimeType=x-scheme-handler/ztxa;\n";

            try (FileWriter writer = new FileWriter(desktopFile)) {
                writer.write(content);
            }

            // 执行注册命令
            executeCommand("update-desktop-database", appsDir.getAbsolutePath());
            executeCommand("xdg-mime", "default", "ztxa-client.desktop", "x-scheme-handler/ztxa");

            logger.info("已成功在 Linux 注册协议处理器: ztxa://");
        } catch (Exception e) {
            logger.error("注册 Linux 协议失败", e);
        }
    }

    private static void registerWindows() {
        try {
            String javaHome = System.getProperty("java.home");
            String javaExe = javaHome + File.separator + "bin" + File.separator + "javaw.exe";
            String classPath = System.getProperty("java.class.path");
            String mainClass = "com.ztxa.client.Launcher";
            
            String command = "\"" + javaExe + "\" -cp \"" + classPath + "\" " + mainClass + " \"%1\"";
            
            executeRegCommand("add", "HKCR\\ztxa", "/ve", "/t", "REG_SZ", "/d", "URL:ztxa Protocol", "/f");
            executeRegCommand("add", "HKCR\\ztxa", "/v", "URL Protocol", "/t", "REG_SZ", "/d", "", "/f");
            executeRegCommand("add", "HKCR\\ztxa\\shell\\open\\command", "/ve", "/t", "REG_SZ", "/d", command, "/f");
            
            logger.info("已成功在 Windows 注册协议处理器: ztxa://");
        } catch (Exception e) {
            logger.error("注册 Windows 协议失败", e);
        }
    }

    private static void executeRegCommand(String... args) throws IOException, InterruptedException {
        String[] fullArgs = new String[args.length + 1];
        fullArgs[0] = "reg";
        System.arraycopy(args, 0, fullArgs, 1, args.length);
        executeCommand(fullArgs);
    }

    private static void executeCommand(String... args) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(args).start();
        process.waitFor();
    }
}
