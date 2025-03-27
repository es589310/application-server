package com.example.pdfprocessorservice.util;

import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;

@Service
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
    public BufferedImage enhanceImage(BufferedImage image) {
        return binarizeAndEnhance(image);
    }
}
