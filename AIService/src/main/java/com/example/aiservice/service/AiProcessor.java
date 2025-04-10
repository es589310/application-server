package com.example.aiservice.service;

import com.example.aiservice.client.GeminiAIClient;
import com.example.aiservice.dto.AIAnalysisRequest;
import com.example.aiservice.entity.AiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class AiProcessor {
    private final GeminiAIClient geminiAIClient;

    @Value("${gemini.model}")
    private String model;

    public Flux<AiResponse> processWithAi(String content, String analysisType) {
        log.info("AiProcessor: Mətn təhlili başlayır, content={}, analysisType={}", content, analysisType);

        if (content == null || content.trim().isEmpty()) {
            log.warn("AiProcessor: Təhlil ediləcək mətn boşdur və ya null-dur");
            return Flux.just(AiResponse.builder()
                    .success(false)
                    .message("Təhlil ediləcək mətn boşdur")
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        // Mətni sətirlərə bölürük (əgər birdən çox element təhlil ediləcəksə)
        String[] contentLines = content.split("\n");

        return Flux.fromArray(contentLines)
                .parallel() // Paralel icra üçün axını bölürük
                .runOn(Schedulers.parallel()) // Paralel thread hovuzunda işləyir
                .flatMap(line -> processSingleLine(line.trim(), analysisType)) // Hər sətri təhlil edirik
                .sequential() // Nəticələri ardıcıl Flux-a çeviririk
                .switchIfEmpty(Flux.just(AiResponse.builder()
                        .success(false)
                        .message("Təhlil üçün uyğun mətn tapılmadı")
                        .createdAt(LocalDateTime.now())
                        .build()))
                .onErrorResume(e -> {
                    log.error("AiProcessor: Təhlil zamanı xəta: {}", e.getMessage(), e);
                    return Flux.just(AiResponse.builder()
                            .success(false)
                            .message("Təhlil xətası: " + e.getMessage())
                            .createdAt(LocalDateTime.now())
                            .build());
                });
    }

    private Mono<AiResponse> processSingleLine(String contentLine, String analysisType) {
        if (contentLine.isEmpty()) {
            return Mono.just(AiResponse.builder()
                    .success(false)
                    .message("Boş sətir")
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        String prompt = generatePrompt(contentLine, analysisType);
        AIAnalysisRequest request = AIAnalysisRequest.builder()
                .pdfId(null)
                .extractedText(prompt)
                .analysisType(analysisType)
                .model(model)
                .build();

        log.info("AiProcessor: Gemini AI-yə sorğu göndərilir, model={}, prompt={}", model, prompt);

        return geminiAIClient.generateText(request)
                .map(response -> {
                    log.info("AiProcessor: Gemini AI-dən cavab alındı: {}", response);
                    String analysisResult = response.getExtractedMetadata() != null
                            ? response.getExtractedMetadata().toString()
                            : "Təhlil nəticəsi yoxdur";

                    if ("METADATA_COMPLETION".equals(analysisType) && analysisResult.startsWith("[")) {
                        try {
                            return AiResponse.builder()
                                    .analysisResult(analysisResult) // JSON string olaraq saxlanılır
                                    .success(true)
                                    .message("Uğurlu")
                                    .createdAt(LocalDateTime.now())
                                    .build();
                        } catch (Exception e) {
                            log.error("JSON parse hatası: {}", e.getMessage());
                        }
                    }

                    return AiResponse.builder()
                            .analysisResult(analysisResult)
                            .success(true)
                            .message("Uğurlu")
                            .createdAt(LocalDateTime.now())
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("AiProcessor: Gemini AI təhlili uğursuz oldu: {}", e.getMessage(), e);
                    return Mono.just(AiResponse.builder()
                            .success(false)
                            .message("Gemini AI xətası: " + e.getMessage())
                            .createdAt(LocalDateTime.now())
                            .build());
                });
    }

    private String generatePrompt(String content, String analysisType) {
        log.debug("Mətn üçün prompt yaradılır: {}, analysisType: {}", content, analysisType);
        if ("METADATA_COMPLETION".equals(analysisType)) {
            return String.format("""
            Aşağıdakı mətni təhlil et və hər tranzaksiyanı bu kateqoriyalardan birinə aid et:
            - Restaurant
            - Cafe
            - Kommunal ödənişlər
            - Bank ödənişləri (rəsmi bank köçürmələri, məsələn, SWIFT, IBAN ödənişləri)
            - Taksi ödənişləri (məsələn, Bolt, Yango)
            - Alış-veriş (mağazalar, marketlər, məsələn, Favorit Market, Ərzaq Mağaza)
            - Aptek (məsələn, Zeytun Aptek, Buta Aptek)
            - Qida çatdırılması (məsələn, Wolt, Volt kuryer xidməti kimi)
            - Mobil ödənişlər (məsələn, Bakcell)
            - Texnologiya/Aboneliklər (məsələn, Apple, Microsoft, Spotify, Google)
            - Səyahət (məsələn, Kiwi.com)
            - Şəxsi köçürmələr (məsələn, Necibe Xala, Fərid H. kimi şəxslərə ödənişlər)
            - Bank xidmət haqları/Komissiyalar (məsələn, Conversion fee, Prime üçün xidmət haqqı)
            - Yanacaq (məsələn, Azpetrol)
            - Parkinq (məsələn, azparking)
            - İctimai nəqliyyat (məsələn, BakıKart)
            - Onlayn ödənişlər (məsələn, MilliÖn, eManat)
            
            Əgər tranzaksiya heç bir kateqoriyaya uyğun gəlmirsə və ya məqsədi aydın deyilsə, onu "Digər" kimi qeyd et.
            "Purchase" ilə başlayan tranzaksiyalar üçün "Purchase" sözündən sonrakı sözləri nəzərə alaraq kateqoriyanı müəyyənləşdir (məsələn, "Purchase APPLE.COM" → "Texnologiya/Aboneliklər").
            
            "Məbləğ" sahəsinə əsasən gəlir və xərcləri nəzərə al:
            - Əgər "Məbləğ" "+" ilə başlayırsa, onu "gəlir" kimi təsnif et.
            - Əgər "Məbləğ" "-" ilə başlayırsa, onu "xərc" kimi təsnif et.
            - "+" və ya "-" işarələrini məbləği "gəlir" və ya "xərc"ə təyin edərkən sil.
            
            Hər tranzaksiyanı tranzaksiya tarixinə əsasən ayrıca qeyd et, hətta eyni gündə birdən çox tranzaksiya olsa belə. Eyni tarix üçün gəlirləri və ya xərcləri bir girişdə birləşdirmə.
            Hər giriş üçün aşağıdakıları daxil et:
            - "date": "Tarix"dən çıxarılıb, "DD-MM-YYYY" formatında (vaxtı nəzərə alma).
            - "category": Müəyyən edilmiş kateqoriya.
            - "income": "Məbləğ" müsbətdirsə, məbləği qeyd et, əks halda 0.00.
            - "expense": "Məbləğ" mənfidirsə, məbləği qeyd et, əks halda 0.00.
            - "balance": "Balans" sahəsindəki dəyər, əgər varsa; yoxdursa, null olaraq qalsın.
            - "description": "Təyinat" sahəsindəki dəqiq dəyər, tranzaksiya təsvirini göstərir.
            
            Xüsusi hallar:
            - "Şəxsi vəsaitlərin qalıq faizi": "Bank xidmət haqları/Komissiyalar" və ya aydın deyilsə "Digər" kimi qəbul et, adətən gəlir.
            - "Prime üçün xidmət haqqı": "Bank xidmət haqları/Komissiyalar" kimi qəbul et, adətən xərc.
            - "Conversion fee": "Bank xidmət haqları/Komissiyalar" kimi qəbul et, adətən xərc.
    
            Nəticəni JSON formatında qaytar. Əgər heç bir düzgün tranzaksiya tapılmasa, boş JSON massivi (`[]`) qaytar.
    
            Mətn: %s
            """, content);
        }
        return String.format("""
        Siz %s təhlili aparan AI assistentsiniz.
        Aşağıdakı mətni təhlil edin:
    
        Mətn: %s
        """, analysisType, content);
    }}