package com.ztxa.client.util;

import java.nio.ByteBuffer;

/**
 * 字节转换工具类
 */
public class BytesDecimalismUtils {
    
    /**
     * 将int转换为4字节数组（大端序）
     * @param value 整数值
     * @return 4字节数组
     */
    public static byte[] intTo4Bytes(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }
    
    /**
     * 将long转换为8字节数组（大端序）
     * @param value 长整数值
     * @return 8字节数组
     */
    public static byte[] longTo8Bytes(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }
    
    /**
     * 将4字节数组转换为int（大端序）
     * @param bytes 字节数组
     * @return 整数值
     */
    public static int bytesToInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }
    
    /**
     * 将8字节数组转换为long（大端序）
     * @param bytes 字节数组
     * @return 长整数值
     */
    public static long bytesToLong(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getLong();
    }
    
    /**
     * 合并多个字节数组
     * @param arrays 多个字节数组
     * @return 合并后的字节数组
     */
    public static byte[] merge(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] array : arrays) {
            totalLength += array.length;
        }
        
        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        
        return result;
    }
}
