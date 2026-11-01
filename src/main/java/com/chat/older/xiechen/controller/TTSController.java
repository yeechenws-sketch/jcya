package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.service.SystemConfigService;
import com.chat.older.xiechen.service.TTSService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TTSController {

    @Autowired
    private TTSService ttsService;

    @Autowired
    private SystemConfigService systemConfigService;

    @GetMapping(value = "/api/tts/speak")
    public ResponseEntity<byte[]> speak(@RequestParam String text) {
        if (!systemConfigService.isTtsEnabled()) {
            return ResponseEntity.status(503).build();
        }
        try {
            byte[] audioData = ttsService.textToSpeech(text);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .body(audioData);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}