package com.example.aiservice.controller;

import com.example.aiservice.entity.AiRequest;
import com.example.aiservice.entity.AiResponse;
import com.example.aiservice.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    @PostMapping("/process")
    public AiResponse processPdfContent(@RequestBody AiRequest request) {
        return aiService.processPdfContent(request);
    }



}
