package com.chat.older.xiechen.service;

import com.chat.older.xiechen.handler.ChatHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationService {

    @Autowired
    private ChatHandler chatHandler;

    public void sendSms(String phone, String message) {
        System.out.println("========== 发送短信通知 ==========");
        System.out.println("接收号码: " + phone);
        System.out.println("内容: " + message);
        System.out.println("=================================");
    }
    
    public void sendAppNotification(Long userId, String title, String content) {
        System.out.println("========== 发送APP通知 ==========");
        System.out.println("用户ID: " + userId);
        System.out.println("标题: " + title);
        System.out.println("内容: " + content);
        System.out.println("=================================");

        if (chatHandler != null) {
            Map<String, Object> notice = new HashMap<>();
            notice.put("msgType", "SYSTEM_NOTIFICATION");
            notice.put("title", title);
            notice.put("content", content);
            notice.put("timestamp", System.currentTimeMillis());
            chatHandler.sendToUser(userId, notice);
        }
    }
    
    public void sendMedicineNotification(String phone, Map<String, Object> medicineInfo, String senderName) {
        StringBuilder message = new StringBuilder();
        message.append("【用药信息通知】\n");
        message.append("您的家人 ").append(senderName).append(" 添加了新的用药信息：\n\n");
        
        if (medicineInfo.get("name") != null) {
            message.append("药品名称：").append(medicineInfo.get("name")).append("\n");
        }
        if (medicineInfo.get("spec") != null) {
            message.append("药品规格：").append(medicineInfo.get("spec")).append("\n");
        }
        if (medicineInfo.get("dosageMg") != null) {
            message.append("每次剂量：").append(medicineInfo.get("dosageMg")).append("\n");
        }
        if (medicineInfo.get("dosagePerTime") != null && medicineInfo.get("unit") != null) {
            message.append("每次服用：").append(medicineInfo.get("dosagePerTime"))
                   .append(" ").append(medicineInfo.get("unit")).append("\n");
        }
        if (medicineInfo.get("frequencyDetail") != null) {
            message.append("服用频次：").append(medicineInfo.get("frequencyDetail")).append("\n");
        } else if (medicineInfo.get("frequency") != null) {
            message.append("每日次数：").append(medicineInfo.get("frequency")).append(" 次\n");
        }
        if (medicineInfo.get("precautions") != null) {
            String precautions = (String) medicineInfo.get("precautions");
            if (precautions.length() > 100) {
                precautions = precautions.substring(0, 100) + "...";
            }
            message.append("注意事项：").append(precautions).append("\n");
        }
        
        message.append("\n请关注家人的用药情况，确保按时服药。");
        
        sendSms(phone, message.toString());
    }
    
    public void sendMedicineNotificationWithImages(String phone, Map<String, Object> medicineInfo, 
                                                    String senderName, String nameImageUrl, String instructionImageUrl) {
        StringBuilder message = new StringBuilder();
        message.append("【用药信息通知】\n");
        message.append("您的家人 ").append(senderName).append(" 添加了新的用药信息：\n\n");
        
        if (medicineInfo.get("name") != null) {
            message.append("药品名称：").append(medicineInfo.get("name")).append("\n");
        }
        if (medicineInfo.get("spec") != null) {
            message.append("药品规格：").append(medicineInfo.get("spec")).append("\n");
        }
        if (medicineInfo.get("dosageMg") != null) {
            message.append("每次剂量：").append(medicineInfo.get("dosageMg")).append("\n");
        }
        if (medicineInfo.get("dosagePerTime") != null && medicineInfo.get("unit") != null) {
            message.append("每次服用：").append(medicineInfo.get("dosagePerTime"))
                   .append(" ").append(medicineInfo.get("unit")).append("\n");
        }
        if (medicineInfo.get("frequencyDetail") != null) {
            message.append("服用频次：").append(medicineInfo.get("frequencyDetail")).append("\n");
        }
        
        message.append("\n药品图片已上传至系统，请登录查看详情。\n");
        message.append("请关注家人的用药情况。");
        
        sendSms(phone, message.toString());
        
        if (nameImageUrl != null || instructionImageUrl != null) {
            System.out.println("图片链接:");
            if (nameImageUrl != null) System.out.println("  药品名称图片: " + nameImageUrl);
            if (instructionImageUrl != null) System.out.println("  用药说明图片: " + instructionImageUrl);
        }
    }
}
