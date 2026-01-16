package com.qoder.client.service;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

public class InstanceLockService {
    private static final String LOCK_FILE_NAME = ".lock";
    private static FileLock lock;
    private static FileChannel channel;

    public static boolean acquireLock(String configDir) {
        try {
            File dir = new File(configDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File lockFile = new File(dir, LOCK_FILE_NAME);
            // 如果程序异常退出导致文件存在也没关系，FileLock 是由操作系统内核管理的
            channel = new RandomAccessFile(lockFile, "rw").getChannel();
            lock = channel.tryLock();

            if (lock != null) {
                // 成功获得锁
                // 注册钩子，在程序退出时释放
                Runtime.getRuntime().addShutdownHook(new Thread(InstanceLockService::releaseLock));
                return true;
            }
        } catch (Exception e) {
            System.err.println("尝试获取实例锁时发生错误: " + e.getMessage());
        }
        return false;
    }

    public static void releaseLock() {
        try {
            if (lock != null) lock.release();
            if (channel != null) channel.close();
        } catch (Exception e) {
            // 忽略关闭时的异常
        }
    }
}
