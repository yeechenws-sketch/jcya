package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.service.IpLocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class LocationController {

    @Autowired
    private IpLocationService ipLocationService;


    @GetMapping("/test")
    public String testLocation() {
        // 强制指定哈尔滨的公网 IP 供测试使用
        String testIp = "1.58.0.0";
        String result = ipLocationService.getCityName(testIp);
        return "测试 IP: " + testIp + " | 预期结果: 哈尔滨市 | 实际解析结果: " + result;
    }


    @GetMapping("/get-city")
    public String getCity(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        } else if (ip.contains(",")) {
            ip = ip.split(",")[0];
        }

        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            String mockIp = "1.58.0.0"; // 哈尔滨 IP
            System.out.println("检测到本地访问，模拟哈尔滨定位测试...");
            return ipLocationService.getCityName(mockIp);
        }

        return ipLocationService.getCityName(ip);
    }

}