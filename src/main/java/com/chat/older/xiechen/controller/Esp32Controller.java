package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.ChatRecordMapper;
import com.chat.older.xiechen.model.ChatRecord;
import com.chat.older.xiechen.service.BaiduAsrService;
import com.chat.older.xiechen.service.HealthDataIntegratedAiService;
import com.chat.older.xiechen.service.TTSService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/esp32")
@CrossOrigin
public class Esp32Controller {

    @Resource
    private BaiduAsrService baiduAsrService;

    @Resource
    private HealthDataIntegratedAiService healthDataIntegratedAiService;

    @Resource
    private ChatRecordMapper chatRecordMapper;

    @Resource
    private TTSService ttsService;

    private static final Long DEFAULT_USER_ID = 1L;

    @PostMapping("/asr")
    public Result<String> asr(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return Result.error("音频文件为空");
            }
            byte[] bytes = file.getBytes();
            String text = baiduAsrService.transcribe(bytes);
            if (text == null || text.isEmpty()) {
                return Result.error("未识别到文字");
            }
            return Result.success(text);
        } catch (Exception e) {
            return Result.error("识别失败: " + e.getMessage());
        }
    }

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        try {
            String message = request.get("message");
            if (message == null || message.trim().isEmpty()) {
                return Result.error("消息不能为空");
            }

            Long userId = DEFAULT_USER_ID;

            ChatRecord userRec = new ChatRecord(null, userId, "user", message, LocalDateTime.now());
            chatRecordMapper.insertRecord(userRec);

            String aiReply = healthDataIntegratedAiService.chatWithHealthData(userId, message);

            ChatRecord aiRec = new ChatRecord(null, userId, "ai", aiReply, LocalDateTime.now());
            chatRecordMapper.insertRecord(aiRec);

            Map<String, Object> data = new HashMap<>();
            data.put("reply", aiReply);
            return Result.success(data);
        } catch (Exception e) {
            return Result.error("AI 对话失败: " + e.getMessage());
        }
    }

    @GetMapping("/tts")
    public ResponseEntity<byte[]> tts(@RequestParam String text) {
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
