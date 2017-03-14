package com.flarejaven.example.jnithread.util;

import android.content.Context;

import com.flarejaven.example.jnithread.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by root on 17-3-14.
 */

public class ZipUtils {

    private static final String BASE_DIR = "/storage/emulated/0/DynamicFaceSticker/";
    public static final String STICKERS_DIR = BASE_DIR + "stickers/";

    public static String getStickersDirectory(String assetName) {
        return STICKERS_DIR + assetName;
    }

    /**
     * 解压assets的zip压缩文件到指定目录
     * @param context 上下文对象
     * @param assetName 压缩文件名
     * @param outputDirectory 输出目录
     * @param isReWrite 是否覆盖
     * @return path outputDirectory/assetName
     * @throws IOException
     */
    public static String unZip(Context context, String assetName, String outputDirectory, boolean isReWrite) throws IOException {
        // 创建解压目标目录
        String dir = outputDirectory + assetName;
        File file = new File(dir);
        // 如果目标目录不存在，则创建
        if (!file.exists()) {
            file.mkdirs();
        } else if (!isReWrite) {
            return dir;
        }
        // 打开压缩文件
        InputStream inputStream = context.getAssets().open(assetName + ".zip");
        ZipInputStream zipInputStream = new ZipInputStream(inputStream);
        // 读取一个进入点
        ZipEntry zipEntry = zipInputStream.getNextEntry();
        // 使用1M buffer
        byte[] buffer = new byte[1024 * 1024];
        // 解压时字节计数
        int count = 0;
        // 如果进入点为空说明已经遍历完所有压缩包中文件和目录
        while (zipEntry != null) {
            // 如果是一个目录
            if (zipEntry.isDirectory()) {
                file = new File(dir + File.separator + zipEntry.getName());
                // 文件需要覆盖或者是文件不存在
                if (isReWrite || !file.exists()) {
                    file.mkdir();
                }
            } else {
                // 如果是文件
                file = new File(dir + File.separator + zipEntry.getName());
                // 文件需要覆盖或者文件不存在，则解压文件
                if (isReWrite || !file.exists()) {
                    file.createNewFile();
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    while ((count = zipInputStream.read(buffer)) > 0) {
                        fileOutputStream.write(buffer, 0, count);
                    }
                    fileOutputStream.close();
                }
            }
            // 定位到下一个文件入口
            zipEntry = zipInputStream.getNextEntry();
        }
        zipInputStream.close();
        return dir;
    }

}
