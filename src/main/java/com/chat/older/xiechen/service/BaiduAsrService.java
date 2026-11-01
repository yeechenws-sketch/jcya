package com.chat.older.xiechen.service;

import com.baidu.aip.speech.AipSpeech;
import jakarta.annotation.PostConstruct;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;


@Service
public class BaiduAsrService {

    @Value("${baidu.tts.appId}")
    private String appId;

    @Value("${baidu.tts.apiKey}")
    private String apiKey;

    @Value("${baidu.tts.secretKey}")
    private String secretKey;

    // 百度语音客户端实例
    private AipSpeech client;


    @PostConstruct
    public void init() {
        client = new AipSpeech(appId, apiKey, secretKey);
        client.setConnectionTimeoutInMillis(2000);
        client.setSocketTimeoutInMillis(60000);
    }


    public String transcribe(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            System.err.println("【百度ASR】错误：接收到的音频数据为空");
            return "";
        }

        HashMap<String, Object> options = new HashMap<>();
        options.put("dev_pid", 1537);
        options.put("speech_rate", 1);

        try {

            JSONObject res = client.asr(audioData, "wav", 16000, options);

            if (res == null) {
                System.err.println("【百度ASR】错误：百度服务器无响应");
                return "";
            }

            int errNo = res.optInt("err_no", -1);

            if (errNo == 0) {
                JSONArray resultArr = res.optJSONArray("result");
                if (resultArr != null && !resultArr.isEmpty()) {
                    String text = resultArr.optString(0, "").trim();
                    System.out.println("【百度ASR】识别成功 → " + text);
                    return text;
                } else {
                    System.out.println("【百度ASR】识别完成，但未听清内容");
                    return "";
                }
            }

            String errMsg = res.optString("err_msg", "未知错误");
            String sn = res.optString("sn", "n/a");
            System.err.println("【百度ASR】识别失败！错误码：" + errNo + " | 错误信息：" + errMsg + " | 请求ID：" + sn);

            return "";

        } catch (Exception e) {
            System.err.println("【百度ASR】系统异常：" + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }
}