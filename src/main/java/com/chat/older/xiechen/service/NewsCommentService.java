package com.chat.older.xiechen.service;

import com.chat.older.xiechen.dao.NewsMapper;
import com.chat.older.xiechen.model.NewsComment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NewsCommentService {

    @Autowired
    private NewsMapper newsMapper;

    // 获取新闻的评论树
    public List<NewsComment> getCommentsByNewsId(Long newsId) {
        List<NewsComment> rootComments = newsMapper.getRootCommentsByNewsId(newsId);
        for (NewsComment comment : rootComments) {
            List<NewsComment> replies = newsMapper.getRepliesByCommentId(comment.getId());
            comment.setReplies(replies);
        }

        return rootComments;
    }

    public void postComment(NewsComment comment) {
        newsMapper.insertComment(comment);
    }

    public void likeComment(Long commentId) {
        newsMapper.incrementCommentLike(commentId);
    }
}