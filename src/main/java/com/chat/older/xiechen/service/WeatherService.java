package com.chat.older.xiechen.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WeatherService {

    private final String API_KEY = "9FypCocudu5kaL5UCc2TJJ7smdZLxyGm";

    public String getWeatherByCity(String cityName) {
        if (cityName == null || cityName.isEmpty()) {
            return "无法获取城市信息";
        }

        RestTemplate rest = new RestTemplate();
        try {
            String searchUrl = "https://openapi.weathercn.com/locations/v1/cities/translate?q="
                    + cityName + "&apikey=" + API_KEY + "&language=zh-cn";

            List<Map<String, Object>> locationList = rest.getForObject(searchUrl, List.class);
            if (locationList == null || locationList.isEmpty()) {
                return cityName + "的天气暂时查询不到";
            }
            String realKey = locationList.get(0).get("Key").toString();

            String weatherUrl = "https://openapi.weathercn.com/forecasts/v1/hourly/1hour/"
                    + realKey + "?apikey=" + API_KEY + "&language=zh-cn&metric=true";

            List<Map<String, Object>> weatherResponse = rest.getForObject(weatherUrl, List.class);
            if (weatherResponse != null && !weatherResponse.isEmpty()) {
                Map<String, Object> data = weatherResponse.get(0);
                String weatherText = data.get("IconPhrase").toString();
                Map<String, Object> tempMap = (Map<String, Object>) data.get("Temperature");
                double tempVal = Double.parseDouble(tempMap.get("Value").toString());

                return String.format("%s当前天气：%s，气温：%d度。", cityName, weatherText, (int)Math.round(tempVal));
            }
        } catch (Exception e) {
            System.err.println("天气查询出错: " + e.getMessage());
        }
        return "暂时无法连接气象台，请稍后再试。";
    }
}