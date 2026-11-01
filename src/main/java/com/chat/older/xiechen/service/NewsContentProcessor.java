package com.chat.older.xiechen.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface NewsContentProcessor {
    @SystemMessage("""
        你是一个专业的新闻排版编辑。请对原始抓取的新闻正文进行清洗和规范化排版。
        
        【处理规则】
        1. 剔除噪声：删除所有类似“上图是”、“点击播放视频”、“责任编辑：”、“(完)”、“扫码关注”等无关字段。
        2. 逻辑分段：保持文章原意，将长篇内容按逻辑重新切分为段落。
        3. 首行缩进：【核心要求】每个段落的开头必须精确包含 2 个全角空格（　　）进行首行缩进。
        4. 段落分隔：段落与段落之间使用单行换行。
        
        直接返回排版后的正文，不要包含任何自我介绍或解释。
        """)
    String cleanAndFormat(@UserMessage String rawContent);
}