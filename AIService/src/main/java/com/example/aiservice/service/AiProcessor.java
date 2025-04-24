package com.example.aiservice.service;

import com.example.aiservice.client.GeminiAIClient;
import com.example.aiservice.dto.AIAnalysisRequest;
import com.example.aiservice.dto.AIAnalysisResponse;
import com.example.aiservice.entity.AiRequest;
import com.example.aiservice.entity.AiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class AiProcessor {
    private final GeminiAIClient geminiAIClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.model}")
    private String model;

    private List<String> splitContent(String content) {
        List<String> chunks = new ArrayList<>();
        String[] lines = content.split("\n");

        int chunkSize = Math.max(20, Math.min(50, lines.length / 5));
        log.info("Calculated chunk size: {}", chunkSize);

        for (int i = 0; i < lines.length; i += chunkSize) {
            List<String> chunkLines = new ArrayList<>();
            for (int j = i; j < Math.min(i + chunkSize, lines.length); j++) {
                String line = lines[j].trim();
                if (line.matches("\\d{2}-\\d{2}-\\d{4}\\s+\\d{2}:\\d{2}:\\d{2}\\s+.+\\s+[-+]\\d+\\.\\d{2}")) {
                    chunkLines.add(line);
                } else {
                    log.debug("Invalid chunk line: {}", line);
                }
            }
            String chunk = String.join("\n", chunkLines).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
                log.debug("Chunk {} created with {} lines: {}", chunks.size(), chunkLines.size(), chunk);
            }
        }

        if (chunks.isEmpty() && !content.isEmpty()) {
            log.warn("No chunks created, adding entire content as single chunk");
            chunks.add(content);
        }

        log.info("Split content into {} chunks", chunks.size());
        return chunks;
    }

    // JSON tranzaksiyalarının etibarlılığını yoxlayır
    private boolean isValidTransaction(String json) {
        try {
            List<Object> jsonList = objectMapper.readValue(json, List.class);
            return !jsonList.isEmpty() && jsonList.stream().allMatch(this::isValidTransactionObject);
        } catch (Exception e) {
            log.debug("Failed to parse JSON: {}, error: {}", json, e.getMessage());
            return false;
        }
    }

    // Tək tranzaksiya obyektinin etibarlılığını yoxlayır
    private boolean isValidTransactionObject(Object transaction) {
        try {
            String json = objectMapper.writeValueAsString(transaction);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return map.containsKey("date") && map.containsKey("description") &&
                    map.containsKey("income") && map.containsKey("expense") &&
                    map.containsKey("category");
        } catch (Exception e) {
            return false;
        }
    }

    // Cavabları birləşdirir, təkrarları aradan qaldırır
    private String combineResponses(List<String> analysisResults, String content) {
        List<Map<String, Object>> combinedList = new ArrayList<>();
        for (String result : analysisResults) {
            try {
                List<Map<String, Object>> chunkList = objectMapper.readValue(result, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>(){});
                for (Map<String, Object> transaction : chunkList) {
                    boolean exists = combinedList.stream().anyMatch(existing ->
                            existing.get("timestamp").equals(transaction.get("timestamp")) &&
                                    existing.get("description").equals(transaction.get("description")) &&
                                    existing.get("income").equals(transaction.get("income")) &&
                                    existing.get("expense").equals(transaction.get("expense"))
                    );
                    if (!exists) {
                        combinedList.add(transaction);
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse chunk JSON: {}, error: {}", result, e.getMessage());
            }
        }

        int inputTransactionCount = (int) Arrays.stream(content.split("\n"))
                .filter(line -> line.matches("\\d{2}-\\d{2}-\\d{4}\\s+\\d{2}:\\d{2}:\\d{2}\\s+.+\\s+[-+]\\d+\\.\\d{2}"))
                .count();
        if (combinedList.size() < inputTransactionCount) {
            log.warn("Output transaction count ({}) is less than input count ({})", combinedList.size(), inputTransactionCount);
        }

        try {
            String finalResult = objectMapper.writeValueAsString(combinedList);
            log.info("Combined result: length={}", finalResult.length());
            return finalResult;
        } catch (Exception e) {
            log.error("Failed to serialize combined JSON: {}", e.getMessage());
            return "[]";
        }
    }

    // Tək chunk-u emal edir
    private CompletableFuture<String> processChunk(String chunk, String analysisType, int chunkIndex) {
        return CompletableFuture.supplyAsync(() -> {
            log.debug("Processing chunk {}", chunkIndex + 1);
            log.debug("Chunk {} input: {}", chunkIndex + 1, chunk); // Log chunk input
            String prompt = generatePrompt(chunk, analysisType);
            AIAnalysisRequest request = AIAnalysisRequest.builder()
                    .pdfId(null)
                    .extractedText(prompt)
                    .analysisType(analysisType)
                    .model(model)
                    .build();

            try {
                AIAnalysisResponse response = geminiAIClient.generateText(request);
                String metadata = response.getExtractedMetadata() != null ? response.getExtractedMetadata().toString() : "[]";
                log.debug("Raw AI response for chunk {}: {}", chunkIndex + 1, metadata); // Log raw response
                String analysisResult = metadata;

                // JSON bloğunu temizle
                if (metadata.contains("```json")) {
                    analysisResult = metadata.substring(metadata.indexOf("```json") + 7, metadata.lastIndexOf("```")).trim();
                } else if (metadata.startsWith("[")) {
                    analysisResult = metadata.trim();
                } else {
                    analysisResult = "[]";
                }

                analysisResult = analysisResult.replaceAll("//.*?\n", "").replaceAll("```", "").trim();

                if (analysisResult.isEmpty() || analysisResult.equals("[]")) {
                    log.debug("Empty JSON array for chunk {}: {}", chunkIndex + 1, analysisResult);
                    return "[]";
                }
                if (!isValidTransaction(analysisResult)) {
                    log.debug("Invalid transactions for chunk {}: {}", chunkIndex + 1, analysisResult);
                    return "[]";
                }

                log.info("Processed chunk {}: input={}, output={}", chunkIndex + 1, chunk, analysisResult);
                return analysisResult;
            } catch (Exception e) {
                log.error("Gemini AI request failed for chunk {}: {}", chunkIndex + 1, e.getMessage());
                return "[]";
            }
        });
    }

    private String preprocessContent(String content) {
        String[] lines = content.split("\n");
        List<String> processedLines = new ArrayList<>();

        // Yenilənmiş regex
        Pattern transactionPattern = Pattern.compile(
                "(?s)(\\d{2}-\\d{2}-\\d{4}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(.+?)(?=\\s+[-+]\\d+\\.\\d{2}|\\z)\\s+([-+]\\d+\\.\\d{2})"
        );
        Matcher matcher = transactionPattern.matcher(content);

        while (matcher.find()) {
            String timestamp = matcher.group(1); // Tarix və saat
            String description = matcher.group(2).trim(); // Təsvir
            String amount = matcher.group(3); // Məbləğ
            String combinedLine = String.format("%s %s %s", timestamp, description, amount);
            processedLines.add(combinedLine);
            log.debug("Processed transaction: {}", combinedLine);
        }

        String result = String.join("\n", processedLines);
        log.info("Filtered lines count: {}", processedLines.size());
        return result;
    }


    @Async("aiTaskExecutor")
    public CompletableFuture<AiResponse> processWithAi(String content, String analysisType, AiRequest aiRequest) {
        log.info("Processing content, length: {}, analysisType: {}", content != null ? content.length() : 0, analysisType);
        log.info("Input content: {}", content); // Log full input

        if (content == null || content.trim().isEmpty()) {
            log.warn("Content is empty or null");
            return CompletableFuture.completedFuture(
                    AiResponse.builder()
                            .request(aiRequest)
                            .success(false)
                            .message("Content is empty")
                            .createdAt(LocalDateTime.now())
                            .build()
            );
        }

        try {
            // 1. Məlumatı təmizlə
            content = preprocessContent(content);
            log.info("Preprocessed content, length: {}", content.length());

            // 2. Təmizlənmiş məlumatı chunk-lara böl
            List<String> chunks = splitContent(content); // 0 ötürürük, çünki chunkSize dinamik hesablanır
            if (chunks.isEmpty()) {
                log.warn("No chunks created after splitting content");
                return CompletableFuture.completedFuture(
                        AiResponse.builder()
                                .request(aiRequest)
                                .success(false)
                                .message("No valid transactions found")
                                .createdAt(LocalDateTime.now())
                                .build()
                );
            }

            // 3. Chunk-ları paralel emal et
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                futures.add(processChunk(chunks.get(i), analysisType, i));
            }

            CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            List<String> analysisResults = allFutures.thenApply(v ->
                    futures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList())
            ).join();

            // 4. Cavabları birləşdir
            String combinedResult = combineResponses(analysisResults, content);
            return CompletableFuture.completedFuture(
                    AiResponse.builder()
                            .request(aiRequest)
                            .analysisResult(combinedResult.isEmpty() ? "[]" : combinedResult)
                            .success(!combinedResult.equals("[]"))
                            .message(combinedResult.equals("[]") ? "No valid results" : "Success")
                            .createdAt(LocalDateTime.now())
                            .build()
            );
        } catch (Exception e) {
            log.error("Processing failed: {}", e.getMessage());
            return CompletableFuture.completedFuture(
                    AiResponse.builder()
                            .request(aiRequest)
                            .success(false)
                            .message("Processing error: " + e.getMessage())
                            .createdAt(LocalDateTime.now())
                            .build()
            );
        }
    }

    private String generatePrompt(String content, String analysisType) {
        log.debug("Generating prompt for text: {}, analysisType: {}", content, analysisType);
        if ("METADATA_COMPLETION".equals(analysisType)) {
            return String.format("""
            Analyze the following text and categorize each transaction. Some transaction descriptions may span multiple lines (e.g., '*4836 ilə bitən virtual kartıma', 'PBMB C2C Other Cards2'). Combine these lines into a single description before processing.

            Categorize each transaction into one of these categories:
            - Restaurant (e.g., Wolt, Mado)
            - Cafe (e.g., Starbucks, ABB CAFE BOTANIST)
            - Shopping (stores, markets, e.g., Favorit Market, Ərzaq Mağaza)
            - Technology/Subscriptions (e.g., Apple, Google)
            - Utilities (e.g., KATV KaTv, Azərsu, Azərişıq)
            - Financial (e.g., bank transfers, card-related transactions like *4836 ilə bitən virtual kartıma, PBMB C2C Other Cards2, SWIFT köçürmə, eManat, *N001 nömrəli biznes hesabımdan)
            - Telecom (e.g., Bakcell +994558587754)
            - Fees (e.g., Prime üçün xidmət haqqı)
            - Interest (e.g., Şəxsi vəsaitlərin qalıq faizi)
            - Other (if unclear or does not fit above categories)

            Parse each line of the input as a separate transaction, extracting:
            - Date: The first field in "DD-MM-YYYY" format.
            - Timestamp: The full date and time in "DD-MM-YYYY HH:MM:SS" format.
            - Description: The text between the timestamp and the amount, combining multiple lines if necessary.
            - Amount: The number starting with "+" or "-", with two decimal places.

            Based on the "Amount" field, account for income and expenses:
            - If "Amount" starts with "+", classify it as "income."
            - If "Amount" starts with "-", classify it as "expense."
            - Remove "+" or "-" signs and preserve the exact amount (e.g., -0.80 becomes expense: 0.80).

            Include the following for each entry:
            - "date": Derived from "Date," in "DD-MM-YYYY" format.
            - "timestamp": Full date and time in "DD-MM-YYYY HH:MM:SS" format.
            - "category": The determined category.
            - "income": If "Amount" is positive, record the exact amount; otherwise, 0.00.
            - "expense": If "Amount" is negative, record the absolute value of the amount; otherwise, 0.00.
            - "description": The exact value from the description field, including all words and special characters.

            Return the result in JSON format. If no valid transactions are found, return an empty JSON array (`[]`).

            Text: %s
            """, content);
        }
        return String.format("""
        Analyze the following transactions in chronological order:
        - Parse each line as a separate transaction, combining multi-line descriptions if necessary.
        - Extract: date (DD-MM-YYYY), timestamp (DD-MM-YYYY HH:MM:SS), description, amount (+/-).
        - Categorize into: Restaurant, Cafe, Shopping, Technology/Subscriptions, Utilities, Financial, Telecom, Fees, Interest, Other.
        - Return JSON with fields: date, timestamp, category, income, expense, description.
        Text: %s
        """, content);
    }
}