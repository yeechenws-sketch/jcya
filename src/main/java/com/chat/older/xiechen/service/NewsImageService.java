package com.chat.older.xiechen.service;

import com.alibaba.fastjson2.JSON;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NewsImageService {

    @Autowired
    private UploadService uploadService;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String DEFAULT_IMG_PATH = "/news/default.jpg";
    private static final int MAX_RETRY = 3;
    private static final long DOWNLOAD_DELAY = 500;


    public String searchAndDownload(String remoteUrl, String keyword) {
        String searchImgUrl = searchImage(keyword);
        if (isValidUrl(searchImgUrl)) {
            String uploadResult = downloadAndUpload(searchImgUrl);
            if (isValidUrl(uploadResult)) {
                return uploadResult;
            }
        }
        System.out.println("🔍 搜索失败，尝试使用原图: " + remoteUrl);
        if (isValidUrl(remoteUrl)) {
            String uploadResult = downloadAndUpload(remoteUrl);
            if (isValidUrl(uploadResult)) {
                return uploadResult;
            }
        }
        return DEFAULT_IMG_PATH;
    }


    private String searchImage(String keyword) {
        if (!isValidKeyword(keyword)) {
            return null;
        }

        String sogouImgUrl = searchSogouImage(keyword);
        if (isValidUrl(sogouImgUrl)) {
            return sogouImgUrl;
        }

        String bingImgUrl = searchBingImage(keyword);
        if (isValidUrl(bingImgUrl)) {
            return bingImgUrl;
        }
        return null;
    }


    private String searchSogouImage(String keyword) {
        String url = "https://pic.sogou.com/pic/searchList.jsp?v=5&keyword=" + keyword;
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Referer", "https://www.sogou.com/")
                    .timeout(5000)
                    .maxBodySize(0)
                    .execute();

            String html = response.body();
            Pattern pattern = Pattern.compile("\"oriPicUrl\":\"(.*?)\"");
            Matcher matcher = pattern.matcher(html);

            if (matcher.find()) {

                String imgUrl = matcher.group(1)
                        .replace("\\u002F", "/")
                        .replace("\\/", "/");
                return imgUrl;
            }
        } catch (Exception e) {
            System.err.println("❌ 搜狗提取失败: " + e.getMessage());
        }
        return null;
    }


    private String searchBingImage(String keyword) {
        try {
            String bingUrl = "https://cn.bing.com/images/search?q=" + keyword;
            Document doc = Jsoup.connect(bingUrl).userAgent(USER_AGENT).timeout(5000).get();
            String mAttr = doc.select(".iusc").first().attr("m");
            if (isValidUrl(mAttr)) {
                String murl = JSON.parseObject(mAttr).getString("murl");
                return isValidUrl(murl) ? murl : null;
            }
        } catch (Exception e) {
            System.err.println("❌ Bing搜图失败: " + e.getMessage());
        }
        return null;
    }

    public String downloadAndUpload(String remoteImgUrl) {
        if (remoteImgUrl == null) return "/news/default.jpg";

        remoteImgUrl = remoteImgUrl.replace("\\u002F", "/")
                .replace("\\/", "/")
                .replace("\\\\", "\\");

        if (!remoteImgUrl.startsWith("http://") && !remoteImgUrl.startsWith("https://")) {
            System.err.println("❌ URL格式非法: " + remoteImgUrl);
            return "/news/default.jpg";
        }

        String suffix = ".jpg";
        if (remoteImgUrl.contains(".")) {
            String temp = remoteImgUrl.substring(remoteImgUrl.lastIndexOf(".")).toLowerCase();
            if (temp.contains("?")) temp = temp.substring(0, temp.indexOf("?"));
            if (temp.length() <= 5) suffix = temp;
        }
        String fileName = UUID.randomUUID().toString().replace("-", "") + suffix;

        int retryCount = 3;
        while (retryCount > 0) {
            InputStream in = null;
            try {
                Thread.sleep(500);
                in = Jsoup.connect(remoteImgUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", "https://www.baidu.com/")
                        .header("Accept", "image/webp,image/png,image/jpeg,*/*;q=0.8")
                        .header("Connection", "close")
                        .ignoreContentType(true)
                        .timeout(10000)
                        .execute()
                        .bodyStream();

                String uploadUrl = uploadService.upload(in, fileName);
                if (uploadUrl != null) {
                    return uploadUrl;
                }

            } catch (Exception e) {
                retryCount--;
                System.err.println("⚠️ 下载失败，剩余重试次数: " + retryCount + " | 异常: " + e.getMessage());
            } finally {
                if (in != null) {
                    try { in.close(); } catch (IOException e) {}
                }
            }
        }
        return "/news/default.jpg";
    }


    private boolean isValidUrl(String url) {
        return Optional.ofNullable(url)
                .map(u -> !u.isEmpty() && !"abs:src".equals(u) && u.startsWith("http"))
                .orElse(false);
    }

    private boolean isValidKeyword(String keyword) {
        return Optional.ofNullable(keyword)
                .map(k -> !k.isEmpty() && k.length() >= 2)
                .orElse(false);
    }


    private String getFileSuffix(String url) {
        if (url.contains(".")) {
            String temp = url.substring(url.lastIndexOf(".")).toLowerCase();
            if (temp.contains("?")) {
                temp = temp.substring(0, temp.indexOf("?"));
            }
            if (temp.length() > 1 && temp.length() <= 5) {
                return temp;
            }
        }
        return ".jpg";
    }


    private String getReferer(String url) {
        if (url.contains("sogou")) {
            return "https://pic.sogou.com/";
        } else if (url.contains("bing")) {
            return "https://cn.bing.com/";
        } else {
            return "https://www.baidu.com/";
        }
    }
}