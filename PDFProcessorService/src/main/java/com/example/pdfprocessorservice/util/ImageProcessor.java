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


/*
Əlavə təklif
package com.example.pdfprocessorservice.util;

import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;

@Service
public class ImageProcessor {

    public BufferedImage enhanceImage(BufferedImage image) {
        // 1. Binarlaşdırma
        BufferedImage binarizedImage = binarizeAndEnhance(image);
        // 2. Kontrast artırma
        return adjustContrast(binarizedImage);
    }

    private BufferedImage binarizeAndEnhance(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage processedImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);

        Graphics2D graphics = processedImage.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();

        return processedImage;
    }

    private BufferedImage adjustContrast(BufferedImage image) {
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
        WritableRaster raster = image.getRaster();
        WritableRaster resultRaster = result.getRaster();

        // Sadə kontrast artırma (thresholding)
        int threshold = 128; // Orta boz rəng dəyəri
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int[] pixel = raster.getPixel(x, y, (int[]) null);
                int gray = (pixel[0] + pixel[1] + pixel[2]) / 3; // RGB-dən boz rəngə çevir
                if (gray > threshold) {
                    resultRaster.setPixel(x, y, new int[]{255, 255, 255}); // Ağ
                } else {
                    resultRaster.setPixel(x, y, new int[]{0, 0, 0}); // Qara
                }
            }
        }

        return result;
    }
}
 */