package com.chat.older.xiechen.service;

import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;

@Service
public class IpLocationService {

    private Searcher searcher;

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("ip2region_v4.xdb");
            InputStream inputStream = resource.getInputStream();
            byte[] cBuff = FileCopyUtils.copyToByteArray(inputStream);
            searcher = Searcher.newWithBuffer(cBuff);
            System.out.println("✅ Ip2region 数据库(v4)加载成功！");
        } catch (Exception e) {
            System.err.println("❌ 数据库加载失败，请检查文件名是否为 ip2region_v4.xdb 且放在 resources 目录下");
        }
    }

    public String getCityName(String ip) {
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return "本地网络";
        }

        try {
            String region = searcher.search(ip.trim());
            System.out.println("🔍 收到 IP [" + ip + "] 原始解析字符串: " + region);
            if (region == null || region.isEmpty()) {
                return "未知地址";
            }
            String[] parts = region.split("\\|");
            if (parts.length >= 4) {
                String province = parts[2];
                String city = parts[3];

                if ("0".equals(city) || city.isEmpty()) {
                    // 省份也可能为空，返回国家名
                    if ("0".equals(province) || province.isEmpty()) {
                        return parts.length >= 1 ? parts[0] : "未知地址";
                    }
                    return province;
                }
                return city;
            } else if (parts.length >= 2) {
                return parts[parts.length - 1];
            }

            return region; // 实在没法拆分就返回原始值
        } catch (Exception e) {
            System.err.println("解析 IP 发生异常: " + e.getMessage());
            return "解析异常";
        }
    }
    public String getCityForWeather(String ip) {
        String city = getCityName(ip);
        if (city.equals("本地网络") || city.equals("解析异常") || city.equals("未知地址") || city.equals("0")) {
            return "哈尔滨";
        }
        city = city.replace("市", "");
        if (city.contains("电信") || city.contains("联通") || city.contains("移动") || city.contains("铁通")) {
            return "哈尔滨";
        }
        return city;
    }

}