package com.example.pdfprocessorservice.util;

import java.awt.*;
import java.awt.image.BufferedImage;

public class ImageProcessor {

    public BufferedImage binarizeAndEnhance(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage processedImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);

        Graphics2D graphics = processedImage.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();

        return processedImage;
    }
}
