package com.example.pdfprocessorservice.util;

import org.springframework.stereotype.Service;
import java.awt.*;
import java.awt.image.BufferedImage;

@Service
public class ImageProcessor {

    public BufferedImage binarizeAndEnhance(BufferedImage image) {
        int width = image.getWidth(); // Giriş şəklinin enini alır
        int height = image.getHeight(); // Giriş şəklinin hündürlüyünü alır
        BufferedImage processedImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY); // Yeni bir qara-ağ (binar) şəkil obyekti yaradır

        Graphics2D graphics = processedImage.createGraphics(); // Yeni şəkil üzərində çəkmək üçün qrafik konteksti yaradır
        graphics.drawImage(image, 0, 0, null); // Orijinal şəkli yeni binar şəkilə kopyalayır (binarlaşdırma avtomatik olur)
        graphics.dispose(); // Qrafik resurslarını təmizləyir, yaddaş sızmasının qarşısını alır

        return processedImage;
    }

    public BufferedImage enhanceImage(BufferedImage image) {
        return binarizeAndEnhance(image);
    }
}