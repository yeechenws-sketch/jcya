package com.chat.older.xiechen.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chat.older.xiechen.dao.NewsMapper;
import com.chat.older.xiechen.model.News;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class NewsCrawlerService {

    private static final Logger log = LoggerFactory.getLogger(NewsCrawlerService.class);

    @Autowired
    private NewsMapper newsMapper;
    @Autowired
    private NewsClassifier newsClassifier;
    @Autowired
    private NewsImageService newsImageService;
    @Autowired
    private NewsContentProcessor contentProcessor;
    @Autowired
    private SystemConfigService systemConfigService;

    @Value("${news.crawler.enabled:true}")
    private boolean crawlerEnabled;
    
    @Value("${news.crawler.max-threads:1}")
    private int maxThreads;
    
    @Value("${news.crawler.request-delay:15000}")
    private int requestDelay;
    
    @Value("${news.crawler.max-requests:50}")
    private int maxRequests;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final List<String> FIXED_CATEGORIES = Arrays.asList("推荐", "财经", "科技热点", "国际", "军事", "体育", "娱乐", "历史", "美食", "旅游", "健康");
    
    private static final int REQUEST_TIMEOUT_MS = 10000;
    private static final int MAX_ARTICLES_PER_SOURCE = 1;
    private static final int MEMORY_CHECK_INTERVAL = 2;
    private static final int MAX_MEMORY_PERCENT = 75;
    
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean manualTrigger = new AtomicBoolean(false);
    private HttpClient httpClient;
    private int articleProcessedCount = 0;

    @Scheduled(cron = "0 0 23 * * ?")
    public void startCrawlTask23() {
        executeCrawlTask("23点爬虫任务", false);
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void startCrawlTask0() {
        executeCrawlTask("凌晨0点爬虫任务", false);
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void startCrawlTask3() {
        executeCrawlTask("凌晨3点爬虫任务", false);
    }

    @Scheduled(cron = "0 0 4 * * ?")
    public void startCrawlTask4() {
        executeCrawlTask("凌晨4点爬虫任务", false);
    }
    
    public Map<String, Object> getCrawlerStatus() {
        Map<String, Object> status = new HashMap<>();
        boolean dbEnabled = systemConfigService.isCrawlerEnabled();
        status.put("enabled", dbEnabled && crawlerEnabled);
        status.put("configEnabled", crawlerEnabled);
        status.put("dbEnabled", dbEnabled);
        status.put("running", isRunning.get());
        status.put("manualTrigger", manualTrigger.get());
        status.put("maxThreads", maxThreads);
        status.put("requestDelay", requestDelay);
        status.put("maxRequests", maxRequests);
        return status;
    }
    
    public String triggerManualCrawl() {
        if (!crawlerEnabled) {
            return "爬虫功能已禁用，请在配置中启用";
        }
        
        if (isRunning.get()) {
            return "爬虫任务正在运行中，请稍后再试";
        }
        
        new Thread(() -> {
            executeCrawlTask("手动触发爬虫任务", true);
        }).start();
        
        return "爬虫任务已在后台启动";
    }

    private synchronized HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .build();
        }
        return httpClient;
    }

    public void executeCrawlTask(String taskName, boolean isManual) {
        boolean dbEnabled = systemConfigService.isCrawlerEnabled();
        if (!dbEnabled && !isManual) {
            log.info("[{}] 爬虫功能已在数据库中禁用，跳过执行", taskName);
            return;
        }
        
        if (!crawlerEnabled && !isManual) {
            log.info("[{}] 爬虫功能已在配置文件中禁用，跳过执行", taskName);
            return;
        }

        if (!isManual && !isWithinAllowedTimeWindow()) {
            log.info("[{}] 当前时间不在允许的爬取时间窗口内，跳过执行", taskName);
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("[{}] 爬虫任务已在运行中，跳过本次执行", taskName);
            return;
        }

        if (isManual) {
            manualTrigger.set(true);
        }

        log.info("[{}] 开始执行爬虫任务 (手动触发: {})", taskName, isManual);
        long startTime = System.currentTimeMillis();
        int totalSuccess = 0;
        int totalFailed = 0;
        articleProcessedCount = 0;

        try {
            checkMemoryAndGc();
            
            log.info("[{}] ========== 第一阶段：RSS订阅源 ==========", taskName);
            List<RssSource> rssSources = getRssSources();
            for (RssSource source : rssSources) {
                if (!isManual && !isWithinAllowedTimeWindow()) {
                    log.info("超出时间窗口，停止爬取");
                    break;
                }

                if (!checkMemoryAvailable()) {
                    log.warn("内存不足，停止爬取");
                    break;
                }

                try {
                    int count = fetchFromRss(source);
                    totalSuccess += count;
                    if (count > 0) {
                        log.info("RSS源 [{}] 获取 {} 条新闻，等待冷却...", source.name, count);
                        safeSleep(requestDelay);
                    }
                } catch (Exception e) {
                    log.error("RSS源 [{}] 获取失败: {}", source.name, e.getMessage());
                    totalFailed++;
                    safeSleep(5000);
                }
            }

            log.info("[{}] ========== 第二阶段：网页爬取源 ==========", taskName);
            List<WebsiteConfig> webSources = getWebSources();
            for (WebsiteConfig site : webSources) {
                if (!isManual && !isWithinAllowedTimeWindow()) {
                    log.info("超出时间窗口，停止爬取");
                    break;
                }

                if (!checkMemoryAvailable()) {
                    log.warn("内存不足，停止爬取");
                    break;
                }

                try {
                    log.info("开始爬取: {} ({})", site.name, site.url);
                    CrawlResult result = crawlWebsite(site);
                    totalSuccess += result.successCount;
                    totalFailed += result.failCount;
                    
                    log.info("完成爬取: {}，成功 {} 条，失败 {} 条", 
                        site.name, result.successCount, result.failCount);
                    
                    if (result.successCount > 0) {
                        log.info("站点爬取完成，等待冷却 {} 秒...", requestDelay / 1000);
                        safeSleep(requestDelay);
                    } else {
                        safeSleep(5000);
                    }
                } catch (Exception e) {
                    log.error("爬取 {} 异常: {}", site.name, e.getMessage());
                    totalFailed++;
                    safeSleep(5000);
                }
            }

        } catch (Exception e) {
            log.error("[{}] 爬虫任务执行异常: {}", taskName, e.getMessage(), e);
        } finally {
            cleanup();
            manualTrigger.set(false);
            isRunning.set(false);
            long duration = System.currentTimeMillis() - startTime;
            log.info("[{}] 爬虫任务完成，总耗时: {}ms，成功: {} 条，失败: {} 条", 
                taskName, duration, totalSuccess, totalFailed);
        }
    }

    private List<RssSource> getRssSources() {
        List<RssSource> sources = new ArrayList<>();

        sources.add(new RssSource(
            "中国新闻网-头条",
            "https://www.chinanews.com.cn/rss/news.xml",
            "中国新闻网头条新闻"
        ));

        sources.add(new RssSource(
            "人民网-要闻",
            "http://www.people.com.cn/rss/politics.xml",
            "人民网要闻频道"
        ));

        sources.add(new RssSource(
            "新华网-头条",
            "http://www.news.cn/rss/politics.xml",
            "新华网头条新闻"
        ));

        sources.add(new RssSource(
            "央视网-新闻",
            "http://news.cctv.com/rss/index.xml",
            "央视网新闻频道"
        ));

        sources.add(new RssSource(
            "环球网-资讯",
            "https://rss.huanqiu.com/world.xml",
            "环球网国际资讯"
        ));

        sources.add(new RssSource(
            "人民网-健康",
            "http://health.people.com.cn/rss/health.xml",
            "人民日报健康频道"
        ));

        return sources;
    }

    private int fetchFromRss(RssSource source) {
        log.info("开始获取RSS: {} ({})", source.name, source.url);
        int successCount = 0;
        InputStream is = null;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(source.url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/rss+xml, application/xml, text/xml")
                    .timeout(java.time.Duration.ofSeconds(REQUEST_TIMEOUT_MS / 1000))
                    .GET()
                    .build();

            HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("RSS请求失败: {} - HTTP {}", source.name, response.statusCode());
                return 0;
            }

            String xmlContent = response.body();
            if (xmlContent == null || xmlContent.isEmpty()) {
                log.warn("RSS内容为空: {}", source.name);
                return 0;
            }

            SyndFeedInput input = new SyndFeedInput();
            is = new ByteArrayInputStream(xmlContent.getBytes("UTF-8"));
            SyndFeed feed = input.build(new XmlReader(is));
            
            log.info("RSS [{}] 共 {} 条条目", source.name, feed.getEntries().size());

            int processed = 0;
            for (SyndEntry entry : feed.getEntries()) {
                if (processed >= MAX_ARTICLES_PER_SOURCE) {
                    log.info("已达到单源最大处理数量({})", MAX_ARTICLES_PER_SOURCE);
                    break;
                }

                String articleUrl = entry.getLink();
                if (articleUrl == null || articleUrl.isEmpty()) {
                    continue;
                }

                if (newsMapper.countByUrl(articleUrl) > 0) {
                    continue;
                }

                String title = entry.getTitle();
                if (title == null || title.length() < 5) {
                    continue;
                }

                String description = entry.getDescription() != null 
                    ? entry.getDescription().getValue() 
                    : "";

                if (!isContentHealthy(title, description)) {
                    log.debug("内容不符合健康标准: {}", title);
                    continue;
                }

                String content = fetchArticleContent(articleUrl);
                if (content == null || content.length() < 50) {
                    content = description;
                }

                if (content == null || content.length() < 50) {
                    continue;
                }

                String aiJson = newsClassifier.classify(title);
                JSONObject aiRes = JSON.parseObject(aiJson);
                String category = aiRes.getString("category");
                String location = aiRes.getString("location");
                
                if (!FIXED_CATEGORIES.contains(category)) {
                    category = "推荐";
                }

                String formattedContent = contentProcessor.cleanAndFormat(content);

                String localImg = null;
                try {
                    String searchKey = "全国".equals(location) 
                        ? title.substring(0, Math.min(title.length(), 6)) 
                        : location;
                    localImg = newsImageService.searchAndDownload(null, searchKey);
                } catch (Exception e) {
                    log.debug("图片处理失败: {}", e.getMessage());
                }

                News news = new News();
                news.setTitle(title);
                news.setContent(formattedContent);
                news.setCategory(category);
                news.setLocation(location);
                news.setSource(source.name);
                news.setOriginalUrl(articleUrl);
                news.setLocalImagePath(localImg);
                
                if (entry.getPublishedDate() != null) {
                    news.setPublishTime(LocalDateTime.ofInstant(
                        entry.getPublishedDate().toInstant(), 
                        ZoneId.systemDefault()
                    ));
                } else {
                    news.setPublishTime(LocalDateTime.now());
                }
                
                newsMapper.insertNews(news);
                log.info("✅ RSS入库成功: {} - {}", source.name, title);
                successCount++;
                processed++;
                articleProcessedCount++;
                
                if (articleProcessedCount % MEMORY_CHECK_INTERVAL == 0) {
                    checkMemoryAndGc();
                }
                
                safeSleep(requestDelay);
            }

        } catch (Exception e) {
            log.error("RSS解析失败 [{}]: {}", source.name, e.getMessage());
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception e) {}
            }
        }

        return successCount;
    }

    private String fetchArticleContent(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(REQUEST_TIMEOUT_MS)
                    .maxBodySize(1024 * 1024)
                    .followRedirects(true)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .get();

            String[] contentSelectors = {
                ".left_zw", ".content_content", "article",
                ".article-content", ".content", ".main-atc",
                ".rm_txt_con", "#article"
            };

            for (String selector : contentSelectors) {
                String text = doc.select(selector).text();
                if (text != null && text.length() > 50) {
                    String result = text.trim();
                    doc = null;
                    return result;
                }
            }

            doc = null;
            return null;
        } catch (Exception e) {
            log.debug("获取文章内容失败: {} | {}", url, e.getMessage());
            return null;
        }
    }

    private List<WebsiteConfig> getWebSources() {
        List<WebsiteConfig> websites = new ArrayList<>();

        websites.add(new WebsiteConfig(
            "新浪新闻", "https://news.sina.com.cn/",
            new String[]{"a[href*='sina.com.cn/c/']"},
            new String[]{"h1.main-title", "h1", ".title"},
            new String[]{"#article", ".article", "article"},
            "新浪新闻综合频道"
        ));

        websites.add(new WebsiteConfig(
            "网易新闻", "https://news.163.com/",
            new String[]{"a[href*='news.163.com/article/']"},
            new String[]{"h1.post_title", "h1", ".title"},
            new String[]{".post_body", ".content", "article"},
            "网易新闻头条"
        ));

        websites.add(new WebsiteConfig(
            "腾讯新闻", "https://news.qq.com/",
            new String[]{"a[href*='new.qq.com/rain/']"},
            new String[]{"h1", ".title"},
            new String[]{".content-article", ".article", "article"},
            "腾讯新闻"
        ));

        websites.add(new WebsiteConfig(
            "央视新闻", "https://ysxw.cctv.cn/",
            new String[]{"a[href*='cctv.cn/']"},
            new String[]{"h1", ".title"},
            new String[]{".cnt_bd", ".content", "article"},
            "央视新闻"
        ));

        websites.add(new WebsiteConfig(
            "新华网", "https://www.xinhuanet.com/",
            new String[]{"a[href*='xinhuanet.com/']"},
            new String[]{"h1", ".title"},
            new String[]{".main-atc", ".article", "article"},
            "新华网"
        ));

        websites.add(new WebsiteConfig(
            "澎湃新闻", "https://www.thepaper.cn/",
            new String[]{"a[href*='thepaper.cn/newsDetail_forward_']"},
            new String[]{"h1", ".title"},
            new String[]{".index_main_cnt__", ".content", "article"},
            "澎湃新闻"
        ));

        websites.add(new WebsiteConfig(
            "搜狐新闻", "https://news.sohu.com/",
            new String[]{"a[href*='sohu.com/a/']"},
            new String[]{"h1", ".title"},
            new String[]{"article", ".content"},
            "搜狐新闻"
        ));

        websites.add(new WebsiteConfig(
            "环球网", "https://www.huanqiu.com/",
            new String[]{"a[href*='huanqiu.com/article/']"},
            new String[]{"h1", ".title"},
            new String[]{"section", ".content", "article"},
            "环球网"
        ));

        websites.add(new WebsiteConfig(
            "界面新闻", "https://www.jiemian.com/",
            new String[]{"a[href*='jiemian.com/article/']"},
            new String[]{"h1", ".title"},
            new String[]{".article-content", ".content", "article"},
            "界面新闻"
        ));

        websites.add(new WebsiteConfig(
            "中国新闻网", "https://www.chinanews.com/",
            new String[]{"a[href*='chinanews.com.cn/news/']"},
            new String[]{"h1", ".title"},
            new String[]{".left_zw", ".content", "article"},
            "中国新闻网"
        ));

        websites.add(new WebsiteConfig(
            "光明网", "https://www.gmw.cn/",
            new String[]{"a[href*='gmw.cn/01gmrb/html/']", "a[href*='gmw.cn/']"},
            new String[]{"h1", ".title"},
            new String[]{".article-content", ".content", "article"},
            "光明网"
        ));

        websites.add(new WebsiteConfig(
            "凤凰网", "https://www.ifeng.com/",
            new String[]{"a[href*='ifeng.com/c/']"},
            new String[]{"h1", ".title"},
            new String[]{".yc_con", ".content", "article"},
            "凤凰网"
        ));

        websites.add(new WebsiteConfig(
            "人民网-健康", "http://health.people.com.cn/",
            new String[]{"a[href*='people.com.cn/health/']", "a[href*='people.com.cn/n1/']"},
            new String[]{"h1", ".title"},
            new String[]{".article", ".content", "article"},
            "人民网健康频道"
        ));

        return websites;
    }

    private boolean isWithinAllowedTimeWindow() {
        LocalTime now = LocalTime.now();
        
        boolean inWindow1 = now.isAfter(LocalTime.of(23, 0)) || now.isBefore(LocalTime.of(1, 0));
        boolean inWindow2 = !now.isBefore(LocalTime.of(3, 0)) && now.isBefore(LocalTime.of(5, 0));

        return inWindow1 || inWindow2;
    }

    private boolean checkMemoryAvailable() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long usedPercent = (usedMemory * 100) / maxMemory;
        
        if (usedPercent > MAX_MEMORY_PERCENT) {
            log.warn("内存使用率过高: {}%，尝试GC...", usedPercent);
            
            System.gc();
            try { Thread.sleep(5000); } catch (Exception e) {}
            
            usedMemory = runtime.totalMemory() - runtime.freeMemory();
            usedPercent = (usedMemory * 100) / maxMemory;
            log.info("GC后内存使用: {}%", usedPercent);
            
            if (usedPercent > MAX_MEMORY_PERCENT) {
                log.error("GC后内存仍然过高: {}%，停止爬取", usedPercent);
                return false;
            }
        }
        return true;
    }

    private void checkMemoryAndGc() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long usedPercent = (usedMemory * 100) / maxMemory;
        
        log.info("当前内存使用: {}/{} ({}%)", 
            formatMemory(usedMemory), formatMemory(maxMemory), usedPercent);
        
        if (usedPercent > 60) {
            log.info("内存使用超过60%，执行GC...");
            System.gc();
            try { Thread.sleep(3000); } catch (Exception e) {}
            
            usedMemory = runtime.totalMemory() - runtime.freeMemory();
            usedPercent = (usedMemory * 100) / maxMemory;
            log.info("GC后内存使用: {}%", usedPercent);
        }
    }

    private String formatMemory(long bytes) {
        return String.format("%.1fMB", bytes / 1024.0 / 1024.0);
    }

    private CrawlResult crawlWebsite(WebsiteConfig site) {
        CrawlResult result = new CrawlResult();
        Document doc = null;
        
        try {
            doc = fetchDocument(site.url);
            if (doc == null) {
                log.warn("无法获取首页: {}", site.url);
                return result;
            }

            Set<String> articleUrls = extractArticleUrls(doc, site);
            log.info("从 {} 发现 {} 个文章链接", site.name, articleUrls.size());

            if (articleUrls.isEmpty()) {
                log.warn("未从 {} 发现任何文章链接", site.name);
                return result;
            }

            int processed = 0;
            for (String articleUrl : articleUrls) {
                if (!checkMemoryAvailable()) {
                    log.warn("内存不足，停止处理");
                    break;
                }

                if (processed >= MAX_ARTICLES_PER_SOURCE) {
                    log.info("已达到单站点最大处理数量({})", MAX_ARTICLES_PER_SOURCE);
                    break;
                }

                if (newsMapper.countByUrl(articleUrl) > 0) {
                    continue;
                }

                if (processArticle(articleUrl, site)) {
                    result.successCount++;
                    processed++;
                    articleProcessedCount++;
                    
                    if (articleProcessedCount % MEMORY_CHECK_INTERVAL == 0) {
                        checkMemoryAndGc();
                    }
                    
                    safeSleep(requestDelay);
                } else {
                    result.failCount++;
                }
            }

        } catch (Exception e) {
            log.error("爬取站点 {} 异常: {}", site.name, e.getMessage());
            result.failCount++;
        } finally {
            doc = null;
        }

        return result;
    }

    private Set<String> extractArticleUrls(Document doc, WebsiteConfig site) {
        Set<String> urls = new LinkedHashSet<>();

        for (String selector : site.linkSelectors) {
            try {
                Elements links = doc.select(selector);
                for (Element link : links) {
                    String url = link.attr("abs:href");
                    if (isValidArticleUrl(url)) {
                        urls.add(url);
                    }
                }
            } catch (Exception e) {
                log.debug("选择器 {} 提取失败: {}", selector, e.getMessage());
            }
        }

        return urls;
    }

    private boolean processArticle(String articleUrl, WebsiteConfig site) {
        Document doc = null;
        try {
            doc = fetchDocument(articleUrl);
            if (doc == null) {
                return false;
            }

            String title = extractTitle(doc, site.titleSelectors);
            String content = extractContent(doc, site.contentSelectors);

            if (title == null || title.length() < 5) {
                log.debug("标题无效: {}", articleUrl);
                return false;
            }

            if (content == null || content.length() < 50) {
                log.debug("内容不足: {}", articleUrl);
                return false;
            }

            if (!isContentHealthy(title, content)) {
                log.debug("内容不符合健康标准: {}", title);
                return false;
            }

            String aiJson = newsClassifier.classify(title);
            JSONObject aiRes = JSON.parseObject(aiJson);
            String category = aiRes.getString("category");
            String location = aiRes.getString("location");
            
            if (!FIXED_CATEGORIES.contains(category)) {
                category = "推荐";
            }

            String formattedContent = contentProcessor.cleanAndFormat(content);

            String searchKey = "全国".equals(location) 
                ? title.substring(0, Math.min(title.length(), 6)) 
                : location;
            String localImg = null;
            try {
                String originalImg = doc.select("article img, .content img, .article-content img").attr("abs:src");
                localImg = newsImageService.searchAndDownload(originalImg, searchKey);
            } catch (Exception e) {
                log.debug("图片处理失败: {}", e.getMessage());
            }

            News news = new News();
            news.setTitle(title);
            news.setContent(formattedContent);
            news.setCategory(category);
            news.setLocation(location);
            news.setSource(site.name);
            news.setOriginalUrl(articleUrl);
            news.setLocalImagePath(localImg);
            news.setPublishTime(LocalDateTime.now());
            
            newsMapper.insertNews(news);
            log.info("✅ 入库成功: {} - {}", site.name, title);

            return true;

        } catch (Exception e) {
            log.error("处理文章失败: {} | 错误: {}", articleUrl, e.getMessage());
            return false;
        } finally {
            doc = null;
        }
    }

    private Document fetchDocument(String url) {
        int maxRetries = 2;
        for (int i = 0; i < maxRetries; i++) {
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(REQUEST_TIMEOUT_MS)
                        .maxBodySize(1024 * 1024)
                        .followRedirects(true)
                        .ignoreContentType(true)
                        .ignoreHttpErrors(true)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        .get();
                return doc;
            } catch (IOException e) {
                log.warn("获取页面失败(第{}次): {} | 错误: {}", i + 1, url, e.getMessage());
                if (i < maxRetries - 1) {
                    safeSleep(5000);
                }
            }
        }
        return null;
    }

    private String extractTitle(Document doc, String[] selectors) {
        for (String selector : selectors) {
            try {
                String text = doc.select(selector).text();
                if (text != null && !text.isEmpty()) {
                    return text.trim();
                }
            } catch (Exception e) {
            }
        }
        return doc.title();
    }

    private String extractContent(Document doc, String[] selectors) {
        for (String selector : selectors) {
            try {
                String text = doc.select(selector).text();
                if (text != null && text.length() > 50) {
                    return text.trim();
                }
            } catch (Exception e) {
            }
        }
        return null;
    }

    private boolean isValidArticleUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false;
        }
        if (url.contains("javascript:") || url.contains("#")) {
            return false;
        }
        if (url.endsWith(".jpg") || url.endsWith(".png") || url.endsWith(".gif") || url.endsWith(".pdf")) {
            return false;
        }
        return true;
    }

    private boolean isContentHealthy(String title, String content) {
        String[] unhealthyKeywords = {
            "赌博", "博彩", "彩票投注", "六合彩",
            "色情", "成人", "裸体",
            "暴力", "血腥", "恐怖袭击",
            "毒品", "吸毒", "贩毒",
            "诈骗", "传销", "非法集资"
        };

        String combinedText = (title + " " + content).toLowerCase();
        for (String keyword : unhealthyKeywords) {
            if (combinedText.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    private void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("线程休眠被中断");
        }
    }

    private void cleanup() {
        log.info("开始清理爬虫资源...");
        try {
            httpClient = null;
            System.runFinalization();
            System.gc();
            log.info("资源清理完成");
        } catch (Exception e) {
            log.warn("资源清理异常: {}", e.getMessage());
        }
    }

    private static class RssSource {
        final String name;
        final String url;
        final String description;

        RssSource(String name, String url, String description) {
            this.name = name;
            this.url = url;
            this.description = description;
        }
    }

    private static class CrawlResult {
        int successCount = 0;
        int failCount = 0;
    }

    private static class WebsiteConfig {
        final String name;
        final String url;
        final String[] linkSelectors;
        final String[] titleSelectors;
        final String[] contentSelectors;
        final String description;

        WebsiteConfig(String name, String url, String[] linkSelectors, 
                      String[] titleSelectors, String[] contentSelectors, String description) {
            this.name = name;
            this.url = url;
            this.linkSelectors = linkSelectors;
            this.titleSelectors = titleSelectors;
            this.contentSelectors = contentSelectors;
            this.description = description;
        }
    }
}
