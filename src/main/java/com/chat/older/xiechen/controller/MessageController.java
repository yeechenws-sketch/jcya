package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.MessageArchiveMapper;
import com.chat.older.xiechen.dao.MessageMapper;
import com.chat.older.xiechen.model.Message;
import com.chat.older.xiechen.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/msg")
public class MessageController {
    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private MessageArchiveMapper messageArchiveMapper;

    @GetMapping("/history")
    public Result getHistory(@RequestParam Long otherId, HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("未登录");

        List<Map<String, Object>> list = messageMapper.getChatHistory(me.getId(), otherId);
        return Result.success(list);
    }

    @GetMapping("/unread/detail")
    public Result getUnreadDetail(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("未登录");

        List<Map<String, Object>> detail = messageMapper.getUnreadDetail(user.getId());
        return Result.success(detail);
    }

    @GetMapping("/unread/count")
    public Result getUnreadCount(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("未登录");

        List<Map<String, Object>> detail = messageMapper.getUnreadDetail(user.getId());
        int totalCount = 0;
        for (Map<String, Object> item : detail) {
            Object count = item.get("count");
            if (count != null) {
                totalCount += ((Number) count).intValue();
            }
        }
        return Result.success(totalCount);
    }

    @PostMapping("/read")
    public Result markAsRead(@RequestParam Long fromId, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("未登录");
        messageMapper.markAsRead(user.getId(), fromId);

        return Result.success("已标记为已读");
    }

    @PostMapping("/clearSpecific")
    public Result clearSpecific(@RequestParam Long otherId, HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("未登录");

        messageMapper.markFromDeleted(me.getId(), otherId);
        messageMapper.markToDeleted(me.getId(), otherId);

        messageMapper.physicalDelete();

        return Result.success("您的记录已清除");
    }

    @PostMapping("/save")
    public Result saveMessage(@RequestBody Message msg, HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("未登录");

        msg.setIsRead(0);
        messageMapper.insertMessage(msg);
        return Result.success(msg);
    }

    @GetMapping("/archive")
    public Result getArchiveMessages(HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("未登录");

        List<Map<String, Object>> archiveMessages = messageArchiveMapper.getArchiveMessagesByUserId(me.getId());
        return Result.success(archiveMessages);
    }

    @GetMapping("/archive/contacts")
    public Result getArchiveContacts(HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("未登录");

        List<Map<String, Object>> contacts = messageArchiveMapper.getArchiveContactsByUserId(me.getId());
        return Result.success(contacts);
    }

    @GetMapping("/archive/history")
    public Result getArchiveHistory(@RequestParam Long otherId, HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("未登录");

        List<Map<String, Object>> messages = messageArchiveMapper.getArchiveMessagesByUserIdAndOtherId(me.getId(), otherId);
        return Result.success(messages);
    }
}