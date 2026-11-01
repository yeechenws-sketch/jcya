package com.chat.older.xiechen.service;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class MedicineService {

    private final ChatLanguageModel visionModel;
    private final MedicineOcrService ocrService;

    public MedicineService(@Qualifier("visionModel") ChatLanguageModel visionModel, 
                          MedicineOcrService ocrService) {
        this.visionModel = visionModel;
        this.ocrService = ocrService;
    }

    public String analyzeImage(String imageUrl) {
        ImageContent imageContent = ImageContent.from(imageUrl);

        String promptText = "请以长辈贴心助手『小智』的身份，观察图片并直接回答。\n" +
                "**要求：**\n" +
                "1. 输出纯文本，不要用任何标记符号（如###、**、标题等），也不要用列表符号或编号。\n" +
                "2. 语气亲切温柔，像子女对父母说话，多用『您』、『记得』、『注意哦』等词语。\n" +
                "3. 内容分三部分自然叙述：\n" +
                "   - **识别物体**：简单说明这是什么（药、蔬菜、水果或植物）。\n" +
                "   - **功能讲解**：用最通俗的话说用途，例如『这是降火的』、『能补钙』或『煮汤很鲜』。\n" +
                "   - **叮嘱细节**：如果是药，强调怎么吃（饭前/饭后）、吃多少，并提醒『一定遵医嘱』；如果是菜或植物，说怎么挑、怎么吃，注意安全。\n" +
                "4. 控制150字左右，不用专业术语。\n" +
                "5. 如果图片模糊看不清，请委婉说：『小智没看清楚，您再拍一张清楚的好吗？』";

        TextContent textContent = TextContent.from(promptText);

        try {
            UserMessage message = UserMessage.from(imageContent, textContent);
            String response = visionModel.chat(message).aiMessage().text();

            if (response == null || response.isEmpty()) {
                return "哎呀，小智刚才走神了，没看清这张图。您能再拍一张清楚点的发给我吗？";
            }

            response = response.replaceAll("#+\\s*", "").replaceAll("\\*+", "").trim();
            return response;

        } catch (Exception e) {
            return "对不起呀，小智现在眼睛有点累，暂时没认出来。如果是急用的药，您先问问家里人或者医生哦！";
        }
    }
    
    public String analyzeImageWithOcr(String imageUrl) {
        String ocrText = ocrService.extractText(imageUrl);
        String visualAnalysis = analyzeImage(imageUrl);
        
        StringBuilder result = new StringBuilder();
        result.append(visualAnalysis);
        
        if (ocrText != null && !ocrText.isEmpty()) {
            result.append("\n\n【图片文字识别结果】\n");
            result.append(ocrText);
        }
        
        return result.toString();
    }
    
    public MedicineOcrService.MedicineOcrResult extractMedicineInfo(String imageUrl) {
        return ocrService.extractMedicineInfo(imageUrl);
    }
    
    public String extractMedicineName(String imageUrl) {
        return ocrService.extractMedicineName(imageUrl);
    }
    
    public String extractTextFromImage(String imageUrl) {
        return ocrService.extractText(imageUrl);
    }
}
