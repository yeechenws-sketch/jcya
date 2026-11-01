package com.chat.older.xiechen.service;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class MedicineOcrService {

    private final ChatLanguageModel visionModel;

    public MedicineOcrService(@Qualifier("visionModel") ChatLanguageModel visionModel) {
        this.visionModel = visionModel;
    }

    public String extractText(String imageUrl) {
        String promptText = "请识别图片中的所有文字内容，按原始排版格式输出。" +
            "要求：" +
            "1. 只输出识别到的文字，不要添加任何解释或说明" +
            "2. 保持文字的原始排版格式（换行、空格等）" +
            "3. 如果图片中没有文字，返回空字符串" +
            "4. 不要输出任何标记符号如###、**等";
        
        try {
            ImageContent imageContent = ImageContent.from(imageUrl);
            TextContent textContent = TextContent.from(promptText);
            UserMessage message = UserMessage.from(imageContent, textContent);
            
            String response = visionModel.chat(message).aiMessage().text();
            
            if (response != null) {
                return response.replaceAll("#+\\s*", "").replaceAll("\\*+", "").trim();
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    public String extractMedicineName(String imageUrl) {
        String promptText = "请从图片中识别药物名称。" +
            "要求：" +
            "1. 只输出药物名称，不要其他任何内容" +
            "2. 如果有多个药物名称，只输出最主要的那个" +
            "3. 如果无法识别，返回空字符串";
        
        try {
            ImageContent imageContent = ImageContent.from(imageUrl);
            TextContent textContent = TextContent.from(promptText);
            UserMessage message = UserMessage.from(imageContent, textContent);
            
            String response = visionModel.chat(message).aiMessage().text();
            
            if (response != null) {
                return response.replaceAll("[#*\\[\\]]", "").trim();
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    public MedicineOcrResult extractMedicineInfo(String imageUrl) {
        MedicineOcrResult result = new MedicineOcrResult();
        
        String promptText = "你是一位资深药师，专门负责解读药品说明书。请仔细阅读图片中的药品说明书，提取用药信息。" +
            "【核心概念区分】" +
            "1. 规格：药品的剂量规格，例如'20mg/粒'表示每粒药含有20mg有效成分" +
            "2. 剂量：每次服用的有效成分量，单位通常是mg或g" +
            "3. 用法用量：每次实际服用的药片/胶囊数量" +
            "【关键计算规则】" +
            "如果说明书说'规格20mg，一次20mg'，则每次服用1粒（因为每粒就是20mg）" +
            "如果说明书说'规格10mg，一次20mg'，则每次服用2粒（需要两粒10mg的药才能达到20mg）" +
            "如果说明书说'规格20mg，一次10mg'，则每次服用0.5粒（半粒）" +
            "【绝对禁止的错误】" +
            "绝不能把'一次20mg'误解为'一次20粒'！这是致命错误！" +
            "mg是重量单位（毫克），粒是数量单位，两者完全不同！" +
            "请返回JSON：{\"name\":\"药品名称\",\"spec\":\"规格如20mg/粒\",\"dosagePerTime\":每次服用的粒数(必须是合理的数字1-10),\"unit\":\"粒或片\",\"dosageMg\":\"每次剂量如20mg\",\"frequency\":每日次数(1-6),\"precautions\":\"注意事项\"}" +
            "只返回JSON，不要解释。";
        
        try {
            ImageContent imageContent = ImageContent.from(imageUrl);
            TextContent textContent = TextContent.from(promptText);
            UserMessage message = UserMessage.from(imageContent, textContent);
            
            String response = visionModel.chat(message).aiMessage().text();
            
            if (response != null && !response.isEmpty()) {
                response = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                
                try {
                    com.alibaba.fastjson2.JSONObject json = com.alibaba.fastjson2.JSON.parseObject(response);
                    result.setName(json.getString("name"));
                    result.setSpec(json.getString("spec"));
                    
                    Integer dosagePerTime = json.getInteger("dosagePerTime");
                    if (dosagePerTime != null && dosagePerTime > 10) {
                        System.err.println("警告：AI返回异常用量 " + dosagePerTime + "，已自动修正为1");
                        dosagePerTime = 1;
                    }
                    result.setDosagePerTime(dosagePerTime);
                    
                    result.setUnit(json.getString("unit"));
                    result.setDosageMg(json.getString("dosageMg"));
                    result.setFrequency(json.getInteger("frequency"));
                    result.setPrecautions(json.getString("precautions"));
                } catch (Exception e) {
                    result.setRawText(response);
                }
            }
        } catch (Exception e) {
            result.setError(e.getMessage());
        }
        
        return result;
    }

    public static class MedicineOcrResult {
        private String name;
        private String spec;
        private Integer dosagePerTime;
        private String unit;
        private String dosageMg;
        private Integer frequency;
        private String precautions;
        private String manufacturer;
        private String approvalNumber;
        private String rawText;
        private String error;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSpec() { return spec; }
        public void setSpec(String spec) { this.spec = spec; }
        public Integer getDosagePerTime() { return dosagePerTime; }
        public void setDosagePerTime(Integer dosagePerTime) { this.dosagePerTime = dosagePerTime; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public String getDosageMg() { return dosageMg; }
        public void setDosageMg(String dosageMg) { this.dosageMg = dosageMg; }
        public Integer getFrequency() { return frequency; }
        public void setFrequency(Integer frequency) { this.frequency = frequency; }
        public String getPrecautions() { return precautions; }
        public void setPrecautions(String precautions) { this.precautions = precautions; }
        public String getManufacturer() { return manufacturer; }
        public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
        public String getApprovalNumber() { return approvalNumber; }
        public void setApprovalNumber(String approvalNumber) { this.approvalNumber = approvalNumber; }
        public String getRawText() { return rawText; }
        public void setRawText(String rawText) { this.rawText = rawText; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}
