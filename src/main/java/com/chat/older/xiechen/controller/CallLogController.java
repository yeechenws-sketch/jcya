package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.CallLogMapper;
import com.chat.older.xiechen.dao.MessageMapper;
import com.chat.older.xiechen.model.CallLog;
import com.chat.older.xiechen.model.Message;
import com.chat.older.xiechen.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/call")
public class CallLogController {

    @Autowired
    private CallLogMapper callLogMapper;

    @Autowired
    private MessageMapper messageMapper;

    @PostMapping("/log")
    public Result saveCallLog(@RequestBody CallLog callLog, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("未登录");

        callLog.setStatus(callLog.getDuration() > 0 ? 1 : 0);
        callLog.setStartTime(new Date(callLog.getStartTime().getTime()));
        callLog.setEndTime(new Date(callLog.getEndTime().getTime()));
        callLogMapper.insert(callLog);

        if (user.getId().equals(callLog.getFromId())) {
            Message msg = new Message();
            msg.setFromId(callLog.getFromId());
            msg.setToId(callLog.getToId());
            msg.setMsgType("call_event");

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
            String startStr = sdf.format(callLog.getStartTime());
            String endStr = sdf.format(callLog.getEndTime());

            String typeName = "video".equals(callLog.getType()) ? "视频通话" : "语音通话";
            String content;

            if (callLog.getDuration() > 0) {
                long totalSeconds = callLog.getDuration();
                long min = totalSeconds / 60;
                long sec = totalSeconds % 60;
                String durationStr = (min > 0 ? min + "分" : "") + sec + "秒";
                content = String.format("%s %s-%s (时长 %s)", typeName, startStr, endStr, durationStr);
            } else {
                content = String.format("%s未接通 (%s)", typeName, startStr);
            }

            msg.setContent(content);
            messageMapper.insertMessage(msg);
        }

        return Result.success("记录已保存");
    }

    @PostMapping("/logs")
    public Result saveCallLogFromWebRTC(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("未登录");

        try {
            Long fromId = Long.valueOf(params.get("fromId").toString());
            Long toId = Long.valueOf(params.get("toId").toString());
            String type = params.get("type").toString();
            Integer duration = params.get("duration") != null ? Integer.valueOf(params.get("duration").toString()) : 0;
            Integer status = params.get("status") != null ? Integer.valueOf(params.get("status").toString()) : 0;
            String direction = params.get("direction") != null ? params.get("direction").toString() : "outgoing";

            CallLog callLog = new CallLog();
            callLog.setFromId(fromId);
            callLog.setToId(toId);
            callLog.setType(type);
            callLog.setDuration(duration);
            callLog.setStatus(status);
            callLog.setDirection(direction);
            
            Date now = new Date();
            callLog.setStartTime(new Date(now.getTime() - duration * 1000L));
            callLog.setEndTime(now);
            
            callLogMapper.insert(callLog);

            if (user.getId().equals(fromId)) {
                Message msg = new Message();
                msg.setFromId(fromId);
                msg.setToId(toId);
                msg.setMsgType("call_event");

                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm");
                String timeStr = sdf.format(now);

                String typeName = "video".equals(type) ? "视频通话" : "语音通话";
                String content;

                if (duration > 0) {
                    long min = duration / 60;
                    long sec = duration % 60;
                    String durationStr = (min > 0 ? min + "分" : "") + sec + "秒";
                    content = String.format("[%s] %s，时长 %s", typeName, timeStr, durationStr);
                } else if (status == 2) {
                    content = String.format("[%s] 对方已拒绝 (%s)", typeName, timeStr);
                } else {
                    content = String.format("[%s] 未接通 (%s)", typeName, timeStr);
                }

                msg.setContent(content);
                messageMapper.insertMessage(msg);
            }

            return Result.success(callLog);
        } catch (Exception e) {
            return Result.error("保存通话记录失败: " + e.getMessage());
        }
    }

    @GetMapping("/logs")
    public Result getCallLogs(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("未登录");

        List<CallLog> logs = callLogMapper.findForFrontend(user.getId());
        return Result.success(logs);
    }

    @GetMapping("/missed-count")
    public Result getMissedCallsCount(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("未登录");

        int count = callLogMapper.countMissedCalls(user.getId());
        return Result.success(count);
    }

    @PostMapping("/mark-read")
    public Result markMissedCallsAsRead(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("未登录");

        int updated = callLogMapper.markMissedCallsAsRead(user.getId());
        return Result.success("已标记 " + updated + " 条未接来电为已读");
    }

    @GetMapping("/history")
    public Result getCallHistory(@RequestParam(required = false, defaultValue = "20") Integer limit, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("未登录");

        List<CallLog> logs = callLogMapper.findByUserId(user.getId());
        
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (CallLog log : logs) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", log.getId());
            item.put("fromId", log.getFromId());
            item.put("toId", log.getToId());
            item.put("type", log.getType());
            item.put("duration", log.getDuration());
            item.put("startTime", log.getStartTime());
            item.put("endTime", log.getEndTime());
            item.put("direction", log.getDirection());
            item.put("status", log.getStatus());
            
            String typeName = "video".equals(log.getType()) ? "视频通话" : "语音通话";
            String statusText;
            if (log.getStatus() == 1) {
                long min = log.getDuration() / 60;
                long sec = log.getDuration() % 60;
                String durationStr = (min > 0 ? min + "分" : "") + sec + "秒";
                statusText = "已接通 " + durationStr;
            } else if (log.getStatus() == 2) {
                statusText = "已拒绝";
            } else {
                statusText = "未接通";
            }
            item.put("typeName", typeName);
            item.put("statusText", statusText);
            
            result.add(item);
        }
        
        return Result.success(result);
    }
}