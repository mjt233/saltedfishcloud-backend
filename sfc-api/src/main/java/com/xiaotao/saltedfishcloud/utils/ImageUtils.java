package com.xiaotao.saltedfishcloud.utils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ImageUtils {
    /**
     * 生成图片缩略图
     * @param imageInputStream  图片输入流
     * @param maxThumbSize      最大的长宽值，压缩生成的缩略图长或宽必有一个小于该值
     * @param outputStream      接收缩略图的输出流
     * @throws IOException      IO异常
     */
    public static void generateThumbnail(InputStream imageInputStream, int maxThumbSize, OutputStream outputStream) throws IOException {
        final BufferedImage image = ImageIO.read(imageInputStream);
        final int originWidth = image.getWidth(null);
        final int originHeight = image.getHeight(null);

        final int rate = Math.max(
                originHeight / maxThumbSize,
                originWidth / maxThumbSize
        );
        int newWidth;
        int newHeight;
        if (rate <= 0) {
            newHeight = originHeight;
            newWidth = originWidth;
        } else {
            newHeight = originHeight / rate;
            newWidth = originWidth / rate;
        }
        final BufferedImage newImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        final Image scaledImage = image.getScaledInstance(newWidth, newHeight, BufferedImage.SCALE_SMOOTH);
        newImage.getGraphics().drawImage(scaledImage,0,0,null);
        ImageIO.write(newImage, "jpg", outputStream);
    }
}
