package com.chat.older.xiechen.service;

import cn.hutool.core.date.ChineseDate;
import cn.hutool.core.date.DateUtil;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Component
public class AiTools {
    @Resource
    private WeatherService weatherService;

    @Value("${doubao.api-key}")
    private String doubaoApiKey;

    @Value("${doubao.base-url}")
    private String doubaoBaseUrl;

    @Value("${doubao.search-model}")
    private String searchModel;

    private HttpClient httpClient;

    public AiTools() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Tool("联网搜索：用于查询最新的养老政策、医保报销、新闻事件及本地便民服务信息。")
    public String searchWeb(String query) {
        try {
            String requestBody = String.format("""
                    {
                        "model": "%s",
                        "stream": false,
                        "thinking": {"type": "disabled"},
                        "tools": [
                            {"type": "web_search"}
                        ],
                        "input": [
                            {
                                "role": "user",
                                "content": [
                                    {
                                        "type": "input_text",
                                        "text": "%s"
                                    }
                                ]
                            }
                        ]
                    }
                    """,
                    searchModel,
                    query.replace("\"", "\\\"").replace("\n", "\\n")
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(doubaoBaseUrl + "/responses"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + doubaoApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String responseBody = response.body();
                int outputTextStart = responseBody.indexOf("\"type\": \"output_text\"");
                if (outputTextStart != -1) {
                    int textStart = responseBody.indexOf("\"text\": \"", outputTextStart);
                    if (textStart != -1) {
                        textStart += "\"text\": \"".length();
                        int textEnd = findJsonStringEnd(responseBody, textStart);
                        if (textEnd != -1) {
                            String content = responseBody.substring(textStart, textEnd)
                                    .replace("\\n", "\n")
                                    .replace("\\\"", "\"")
                                    .replace("\\\\", "\\");
                            return content;
                        }
                    }
                }
                int contentStart = responseBody.indexOf("\"content\": [");
                if (contentStart != -1) {
                    int innerTextStart = responseBody.indexOf("\"text\": \"", contentStart);
                    if (innerTextStart != -1) {
                        innerTextStart += "\"text\": \"".length();
                        int innerTextEnd = findJsonStringEnd(responseBody, innerTextStart);
                        if (innerTextEnd != -1) {
                            return responseBody.substring(innerTextStart, innerTextEnd)
                                    .replace("\\n", "\n")
                                    .replace("\\\"", "\"");
                        }
                    }
                }
                return "搜索完成但结果解析失败。原始响应前500字符: " + 
                       responseBody.substring(0, Math.min(500, responseBody.length()));
            } else {
                System.err.println("[Doubao Web Search] Failed with status: " + response.statusCode());
                return "搜索暂时不可用，建议稍后重试或咨询社区工作人员。";
            }
        } catch (Exception e) {
            System.err.println("[Doubao Web Search] Exception: " + e.getMessage());
            return "搜索服务暂时繁忙，我会尽力用已有知识为您解答。";
        }
    }

    private int findJsonStringEnd(String str, int start) {
        int i = start;
        while (i < str.length()) {
            char c = str.charAt(i);
            if (c == '\\' && i + 1 < str.length()) {
                i += 2;
                continue;
            }
            if (c == '"') {
                return i;
            }
            i++;
        }
        return -1;
    }

    @Tool("获取当前北京时间及星期，用于告知今天日期。")
    public String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss EEEE"));
    }

    @Tool("获取天气：根据城市名查询实时天气。")
    public String getWeather(String city) {
        return weatherService.getWeatherByCity(city);
    }

    @Tool("节日查询：获取传统节日（如春节、中秋、重阳等）的准确公历日期及倒计时。")
    public String getFestivalInfo(String festivalName) {
        LocalDate today = LocalDate.now();
        int year = today.getYear();

        Map<String, int[]> festivals = new HashMap<>();
        festivals.put("春节", new int[]{1, 1});
        festivals.put("元宵节", new int[]{1, 15});
        festivals.put("端午节", new int[]{5, 5});
        festivals.put("中秋节", new int[]{8, 15});
        festivals.put("重阳节", new int[]{9, 9});
        festivals.put("腊八节", new int[]{12, 8});

        if (!festivals.containsKey(festivalName)) {
            return "这个日子我得查查，但我知道今年的春节是2月17日。";
        }

        int[] md = festivals.get(festivalName);
        ChineseDate cd = new ChineseDate(year, md[0], md[1]);
        LocalDate target = LocalDate.parse(DateUtil.formatDate(cd.getGregorianDate()));

        if (target.isBefore(today)) {
            cd = new ChineseDate(year + 1, md[0], md[1]);
            target = LocalDate.parse(DateUtil.formatDate(cd.getGregorianDate()));
        }

        long days = ChronoUnit.DAYS.between(today, target);
        return String.format("今年的%s是%s，还有%d天。那是农历的%s。",
                festivalName, target, days, cd.toString());
    }

    @Tool("倒计时计算：计算从今天到任意指定公历日期（格式：yyyy-MM-dd）还有多少天。")
    public String getDaysCountdown(String targetDateStr) {
        try {
            LocalDate today = LocalDate.now();
            LocalDate target = LocalDate.parse(targetDateStr);
            long days = ChronoUnit.DAYS.between(today, target);
            return days >= 0 ? "还有" + days + "天。" : "已经过去" + Math.abs(days) + "天了。";
        } catch (Exception e) {
            return "日期格式不对。";
        }
    }
}
