package com.wlf.translateprovider.utils;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

/**
 * 文件操作工具类
 */
public class FileUtil {
    private static final SimpleDateFormat SF = new SimpleDateFormat("yyyyMMdd_HHmmss");

    /**
     * 读取文件内容为二进制数组
     *
     * @param filePath
     * @return
     * @throws IOException
     */
    public static byte[] read(String filePath) throws IOException {

        InputStream in = new FileInputStream(filePath);
        byte[] data = inputStream2ByteArray(in);
        in.close();

        return data;
    }

    /**
     * 流转二进制数组
     *
     * @param in
     * @return
     * @throws IOException
     */
    static byte[] inputStream2ByteArray(InputStream in) throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024 * 4];
        int n = 0;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /**
     * 保存文件
     *
     * @param filePath
     * @param fileName
     * @param content
     */
    public static String save(String filePath, String fileName, byte[] content) {
        String fullFileName = null;
        try {
            File filedir = new File(filePath);
            if (!filedir.exists()) {
                filedir.mkdirs();
            }
            File file = new File(filedir, fileName);
            OutputStream os = new FileOutputStream(file);
            os.write(content, 0, content.length);
            os.flush();
            os.close();
            fullFileName = file.getPath();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fullFileName;
    }

    public static String createFileName(String fileName) {
        String newFileName = null;
        Random random = new Random();
        String fileExtend = fileName.substring(fileName.lastIndexOf(".") + 1);
        newFileName = SF.format(new Date()) + fileExtend;
        return newFileName;
    }
}
