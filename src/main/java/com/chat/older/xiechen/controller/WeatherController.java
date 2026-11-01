package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.service.IpLocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    @Autowired
    private IpLocationService ipLocationService;

    private final String API_KEY = "9FypCocudu5kaL5UCc2TJJ7smdZLxyGm";

    @GetMapping("/now")
    public Map<String, Object> getNowWeather(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            ip = ip.split(",")[0].trim();
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        
        System.out.println("🌐 获取到的IP: " + ip);

        String pureCity = ipLocationService.getCityForWeather(ip);

        if (pureCity == null || "本地网络".equals(pureCity) || "".equals(pureCity) || "解析异常".equals(pureCity)) {
            pureCity = "哈尔滨";
        }

        RestTemplate rest = new RestTemplate();
        Map<String, Object> resultMap = new HashMap<>();

        try {
            String searchUrl = "https://openapi.weathercn.com/locations/v1/cities/translate?q="
                    + pureCity + "&apikey=" + API_KEY + "&language=zh-cn";

            System.out.println("🔍 IP定位结果 [" + pureCity + "]，正在同步至华风官方位置...");

            List<Map<String, Object>> locationList = rest.getForObject(searchUrl, List.class);

            if (locationList == null || locationList.isEmpty()) {
                throw new Exception("华风无法识别该城市名: " + pureCity);
            }

            String realKey = locationList.get(0).get("Key").toString();
            System.out.println(" 匹配成功，官方 ID 为: " + realKey);

            String weatherUrl = "https://openapi.weathercn.com/forecasts/v1/hourly/1hour/"
                    + realKey + "?apikey=" + API_KEY + "&language=zh-cn&metric=true";

            List<Map<String, Object>> weatherResponse = rest.getForObject(weatherUrl, List.class);

            if (weatherResponse != null && !weatherResponse.isEmpty()) {
                Map<String, Object> data = weatherResponse.get(0);
                String weatherText = data.get("IconPhrase").toString();
                Map<String, Object> tempMap = (Map<String, Object>) data.get("Temperature");
                double tempVal = Double.parseDouble(tempMap.get("Value").toString());

                resultMap.put("city", pureCity);
                resultMap.put("weatherText", weatherText);
                resultMap.put("temperature", (int)Math.round(tempVal));
                resultMap.put("tip", "当前" + pureCity + "天气冷，请注意保暖。");
            }
        } catch (Exception e) {
            System.err.println("❌ 链路失败: " + e.getMessage());
            resultMap.put("city", pureCity);
            resultMap.put("weatherText", "多云");
            resultMap.put("temperature", -10);
            resultMap.put("tip", "正在尝试连接气象台，请注意防寒。");
        }
        return resultMap;
    }


    private String getElderlyTip(String text, double temp) {
        if (text.contains("雨")) return "外面下雨了，地滑，尽量别出门，必须出门请带伞。";
        if (text.contains("雪")) return "下雪了，地面很湿滑，一定要穿防滑鞋。";
        if (temp < 0) return "天寒地冻，出门请戴好帽子和围巾，别冻着。";
        return "天气不错，适合去楼下走走，多晒晒太阳。";
    }
}