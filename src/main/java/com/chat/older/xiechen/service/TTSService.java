package com.chat.older.xiechen.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class TTSService {

    @Value("${baidu.tts.apiKey}")
    private String apiKey;

    @Value("${baidu.tts.secretKey}")
    private String secretKey;

    @Value("${baidu.tts.cuid}")
    private String cuid;
    public String getAccessToken() throws Exception {
        String authUrl = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials"
                + "&client_id=" + apiKey + "&client_secret=" + secretKey;

        URL url = new URL(authUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String result = br.readLine();
            return result.split("\"access_token\":\"")[1].split("\"")[0];
        }
    }


    public byte[] textToSpeech(String text) throws Exception {
        String token = getAccessToken();
        String ttsUrl = "https://tsn.baidu.com/text2audio";

        // 百度推荐对 tex 参数进行 2 次 urlencode
        String encodedText = URLEncoder.encode(URLEncoder.encode(text, "UTF-8"), "UTF-8");

        // 拼接请求参数
        String params = "tex=" + encodedText
                + "&tok=" + token
                + "&cuid=" + cuid
                + "&ctp=1"
                + "&lan=zh"
                + "&per=4"
                + "&aue=6"
                + "&spd=5"
                + "&pit=5"
                + "&vol=5";

        URL url = new URL(ttsUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.getOutputStream().write(params.getBytes(StandardCharsets.UTF_8));
        String contentType = conn.getContentType();
        if (contentType != null && contentType.contains("audio")) {
            byte[] audioData = conn.getInputStream().readAllBytes();
            return audioData;
        } else {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                throw new RuntimeException("百度TTS报错: " + br.readLine());
            }
        }
    }
}