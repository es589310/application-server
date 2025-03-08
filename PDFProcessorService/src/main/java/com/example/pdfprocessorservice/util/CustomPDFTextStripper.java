package com.example.pdfprocessorservice.util;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomPDFTextStripper extends PDFTextStripper {

    private final Map<String, Boolean> boldTextMap = new HashMap<>();

    public CustomPDFTextStripper() throws IOException {
    }


    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        if (!text.trim().isEmpty()) {
            boolean isBold = isBoldFont(textPositions);
            boldTextMap.put(text, isBold);
        }
        super.writeString(text, textPositions);
    }

    private boolean isBoldFont(List<TextPosition> textPositions) {
        for (TextPosition text : textPositions) {
            PDFont font = text.getFont();
            String fontName = font.getName().toLowerCase();
            if (fontName.contains("bold")) {
                return true;
            }
        }
        return false;
    }

    public Map<String, Boolean> getBoldTextMap() {
        return boldTextMap;
    }
}
