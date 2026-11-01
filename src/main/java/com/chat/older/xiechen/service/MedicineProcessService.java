package com.chat.older.xiechen.service;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MedicineProcessService {

    @Autowired
    @Qualifier("visionModel")
    private ChatLanguageModel visionModel;
    
    @Autowired
    @Qualifier("textChatModel")
    private ChatLanguageModel textModel;

    public static class ProcessResult {
        private String rawOcrText;
        private String cleanedText;
        private String coverText;
        private String medicineName;
        private String spec;
        private Integer dosagePerTime;
        private String unit;
        private String dosageMg;
        private Integer frequency;
        private String frequencyDetail;
        private String precautions;
        private String manufacturer;
        private String approvalNumber;
        private String storageCondition;
        private String validityPeriod;
        private Integer pillsPerBox;
        private Integer bottlesPerBox;
        private Integer bagsPerBox;
        private Integer platesPerBox;
        private String totalQuantity;
        private String packageSpec;
        private String medicineType;
        private String error;
        private Double confidence;

        public String getRawOcrText() { return rawOcrText; }
        public void setRawOcrText(String rawOcrText) { this.rawOcrText = rawOcrText; }
        public String getCleanedText() { return cleanedText; }
        public void setCleanedText(String cleanedText) { this.cleanedText = cleanedText; }
        public String getCoverText() { return coverText; }
        public void setCoverText(String coverText) { this.coverText = coverText; }
        public String getMedicineName() { return medicineName; }
        public void setMedicineName(String medicineName) { this.medicineName = medicineName; }
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
        public String getFrequencyDetail() { return frequencyDetail; }
        public void setFrequencyDetail(String frequencyDetail) { this.frequencyDetail = frequencyDetail; }
        public String getPrecautions() { return precautions; }
        public void setPrecautions(String precautions) { this.precautions = precautions; }
        public String getManufacturer() { return manufacturer; }
        public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
        public String getApprovalNumber() { return approvalNumber; }
        public void setApprovalNumber(String approvalNumber) { this.approvalNumber = approvalNumber; }
        public String getStorageCondition() { return storageCondition; }
        public void setStorageCondition(String storageCondition) { this.storageCondition = storageCondition; }
        public String getValidityPeriod() { return validityPeriod; }
        public void setValidityPeriod(String validityPeriod) { this.validityPeriod = validityPeriod; }
        public Integer getPillsPerBox() { return pillsPerBox; }
        public void setPillsPerBox(Integer pillsPerBox) { this.pillsPerBox = pillsPerBox; }
        public Integer getBottlesPerBox() { return bottlesPerBox; }
        public void setBottlesPerBox(Integer bottlesPerBox) { this.bottlesPerBox = bottlesPerBox; }
        public Integer getBagsPerBox() { return bagsPerBox; }
        public void setBagsPerBox(Integer bagsPerBox) { this.bagsPerBox = bagsPerBox; }
        public Integer getPlatesPerBox() { return platesPerBox; }
        public void setPlatesPerBox(Integer platesPerBox) { this.platesPerBox = platesPerBox; }
        public String getTotalQuantity() { return totalQuantity; }
        public void setTotalQuantity(String totalQuantity) { this.totalQuantity = totalQuantity; }
        public String getPackageSpec() { return packageSpec; }
        public void setPackageSpec(String packageSpec) { this.packageSpec = packageSpec; }
        public String getMedicineType() { return medicineType; }
        public void setMedicineType(String medicineType) { this.medicineType = medicineType; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }
    }

    public ProcessResult processCoverImage(String coverImageUrl) {
        ProcessResult result = new ProcessResult();
        
        try {
            System.out.println("=== 步骤一：药物封面处理 ===");
            System.out.println("封面图片URL: " + coverImageUrl);
            
            String coverText = extractCoverText(coverImageUrl);
            result.setCoverText(coverText);
            System.out.println("封面文字提取完成，内容: " + coverText);
            
        } catch (Exception e) {
            result.setError("封面处理失败: " + e.getMessage());
            System.err.println("封面处理异常: " + e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }

    public ProcessResult processInstructionImage(String instructionImageUrl, String coverText) {
        ProcessResult result = new ProcessResult();
        
        try {
            System.out.println("=== 步骤二：药物说明书处理 ===");
            System.out.println("说明书图片URL: " + instructionImageUrl);
            
            String rawOcrText = extractRawText(instructionImageUrl);
            result.setRawOcrText(rawOcrText);
            System.out.println("OCR原始文本: " + rawOcrText);
            
            System.out.println("=== 步骤三：信息整合与提取 ===");
            String combinedText = combineTexts(coverText, rawOcrText);
            System.out.println("整合文本长度: " + combinedText.length());
            
            extractAndCleanParameters(combinedText, result);
            result.setCleanedText(result.getRawOcrText());
            System.out.println("参数提取完成");
            
            validateAndCorrect(result);
            System.out.println("数据校验修正完成");
            
            System.out.println("=== 药物信息处理完成 ===");
            System.out.println("药品名称: " + result.getMedicineName());
            System.out.println("规格: " + result.getSpec());
            System.out.println("每盒粒数: " + result.getPillsPerBox());
            System.out.println("每次服用: " + result.getDosagePerTime() + " " + result.getUnit());
            System.out.println("每次剂量: " + result.getDosageMg());
            System.out.println("每日次数: " + result.getFrequency());
            
        } catch (Exception e) {
            result.setError("处理失败: " + e.getMessage());
            System.err.println("药物信息处理异常: " + e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }

    public ProcessResult processMedicineImage(String imageUrl) {
        return processInstructionImage(imageUrl, null);
    }

    private String extractCoverText(String imageUrl) {
        String prompt = "你是一个专业的药品包装识别系统。请仔细观察图片，完整提取药盒封面上的所有文字信息。" +
            "" +
            "【必须提取的信息】" +
            "1. 药品名称：包括商品名和通用名（如：硝苯地平缓释片(II)）" +
            "2. 包装规格：如'30粒/盒'、'12片/盒'、'20mg×12粒/盒'等" +
            "3. 药品规格：如'20mg/片'、'0.4g/粒'、'10mg/粒'等" +
            "4. 生产厂家：生产企业全称" +
            "5. 批准文号：国药准字开头的编号" +
            "" +
            "【输出格式要求】" +
            "严格按照以下格式输出，每项一行：" +
            "【药品名称】提取的药品名称" +
            "【包装规格】提取的包装规格" +
            "【药品规格】提取的药品规格" +
            "【生产厂家】提取的生产厂家" +
            "【批准文号】提取的批准文号" +
            "" +
            "【注意事项】" +
            "- 如果某项信息在图片中看不到，标注'未显示'" +
            "- 不要输出任何Markdown格式符号（**、##等）" +
            "- 不要输出英文翻译或拼音" +
            "- 只输出中文文字，保持原文格式" +
            "- 药品名称中的罗马数字要保留（如I、II、III）";
        
        try {
            ImageContent imageContent = ImageContent.from(imageUrl);
            TextContent textContent = TextContent.from(prompt);
            UserMessage message = UserMessage.from(imageContent, textContent);
            
            String response = visionModel.chat(message).aiMessage().text();
            if (response != null) {
                response = cleanResponse(response);
            }
            return response != null ? response : "";
        } catch (Exception e) {
            System.err.println("封面OCR提取失败: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    private String extractRawText(String imageUrl) {
        String prompt = "你是一个专业的药品说明书OCR识别系统。请仔细观察图片，完整提取所有可见文字。" +
            "" +
            "【识别要求】" +
            "1. 完整提取所有文字，不要遗漏任何内容" +
            "2. 重点提取以下信息：" +
            "   - 药品名称（商品名和通用名）" +
            "   - 成分" +
            "   - 适应症/功能主治" +
            "   - 规格" +
            "   - 用法用量（非常重要，必须完整提取）" +
            "   - 不良反应" +
            "   - 禁忌" +
            "   - 注意事项" +
            "   - 贮藏条件" +
            "   - 包装" +
            "   - 有效期" +
            "   - 批准文号" +
            "   - 生产企业" +
            "3. 保持原始排版格式，用换行分隔不同段落" +
            "4. 如果文字模糊不清，用[?]标注" +
            "" +
            "【输出要求】" +
            "- 只输出识别到的文字，不要添加任何解释" +
            "- 不要输出Markdown格式符号" +
            "- 不要输出英文翻译" +
            "- 用法用量部分必须完整，不要省略";
        
        try {
            ImageContent imageContent = ImageContent.from(imageUrl);
            TextContent textContent = TextContent.from(prompt);
            UserMessage message = UserMessage.from(imageContent, textContent);
            
            String response = visionModel.chat(message).aiMessage().text();
            if (response != null) {
                response = cleanResponse(response);
            }
            return response != null ? response : "";
        } catch (Exception e) {
            System.err.println("OCR提取失败: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    private String cleanResponse(String response) {
        if (response == null) return "";
        
        response = response.replaceAll("\\*+", "");
        response = response.replaceAll("#{1,6}\\s*", "");
        response = response.replaceAll("`+", "");
        response = response.replaceAll("\\\\n", "\n");
        response = response.replaceAll("\\\\t", " ");
        response = response.replaceAll("\\s{3,}", "  ");
        response = response.trim();
        
        return response;
    }

    private String cleanAndStandardize(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }
        
        String prompt = "你是一位专业的医药文本处理专家。请对以下OCR识别的药品说明书文本进行清洗和标准化处理。" +
            "" +
            "【处理要求】" +
            "1. 去除识别噪声：删除乱码、重复字符、无意义的符号" +
            "2. 修正识别错误：纠正明显的OCR识别错误" +
            "3. 标准化表述（保持原意不变）：" +
            "   - '一日三次'、'每天3次'、'tid' 统一为 '一日3次'" +
            "   - '每次一片'、'一次1片'、'1片/次' 统一为 '一次1片'" +
            "   - '20毫克'、'20mg'、'20MG' 统一为 '20mg'" +
            "4. 结构化整理，按以下顺序输出：" +
            "   【药品名称】..." +
            "   【成分】..." +
            "   【适应症】..." +
            "   【规格】..." +
            "   【用法用量】..." +
            "   【不良反应】..." +
            "   【禁忌】..." +
            "   【注意事项】..." +
            "   【贮藏】..." +
            "   【包装】..." +
            "   【有效期】..." +
            "   【批准文号】..." +
            "   【生产企业】..." +
            "5. 保留所有有效信息，不要遗漏" +
            "6. 用法用量部分必须完整保留" +
            "" +
            "【原始文本】" +
            rawText +
            "" +
            "请输出清洗和标准化后的文本：";
        
        try {
            UserMessage message = UserMessage.from(prompt);
            String response = textModel.chat(message).aiMessage().text();
            if (response != null) {
                response = cleanResponse(response);
            }
            return response != null ? response : rawText;
        } catch (Exception e) {
            System.err.println("文本清洗失败: " + e.getMessage());
            e.printStackTrace();
            return rawText;
        }
    }

    private String combineTexts(String coverText, String instructionText) {
        StringBuilder combined = new StringBuilder();
        
        if (coverText != null && !coverText.isEmpty()) {
            combined.append("【药盒封面信息】\n").append(coverText).append("\n\n");
        }
        
        if (instructionText != null && !instructionText.isEmpty()) {
            combined.append("【药品说明书信息】\n").append(instructionText);
        }
        
        return combined.toString();
    }

    private void extractAndCleanParameters(String combinedText, ProcessResult result) {
        String prompt = "你是一位资深药师，请从以下药品OCR原始文本中直接提取关键信息。文本可能有OCR识别噪声，请自动忽略并提取有效信息。" +
            "" +
            "【提取规则】" +
            "1. 药品名称：提取完整的药品通用名称，保留罗马数字（如硝苯地平缓释片II）" +
            "2. 规格：提取剂量规格，格式如'20mg/片'或'0.5g/粒'" +
            "3. 每次剂量：提取每次服用的有效成分量，单位为mg或g" +
            "4. 每次服用粒数：" +
            "   - 如果用法用量明确写了'一次X片/粒'，直接使用该数字" +
            "   - 如果是范围剂量（如'一次3-4片'），取较高值" +
            "   - 如果需要计算：服用粒数 = 每次剂量 ÷ 每粒含量" +
            "5. 每日次数：从用法用量中提取（如'一日3次' -> 3）" +
            "6. 每盒粒数：从包装信息中提取粒数/片数/袋数" +
            "7. 药品类型：根据药品名称判断（降压药/降糖药/降脂药/感冒药/抗生素/胃药/钙片/其他）" +
            "" +
            "【重要警告】" +
            "- 绝对不能把'每次20mg'误解为'每次20粒'！" +
            "- mg是重量单位，粒是数量单位" +
            "- 必须仔细阅读用法用量原文" +
            "" +
            "【OCR原始文本】" +
            combinedText +
            "" +
            "请以JSON格式返回（只返回JSON，不要其他内容）：" +
            "{\"name\":\"药品名称\",\"spec\":\"规格\",\"dosageMg\":\"每次剂量\",\"dosagePerTime\":每次服用粒数,\"unit\":\"粒或片\",\"frequency\":每日次数,\"frequencyDetail\":\"详细频次\",\"pillsPerBox\":每盒粒数,\"packageSpec\":\"包装规格\",\"medicineType\":\"药品类型\",\"precautions\":\"注意事项\",\"manufacturer\":\"生产企业\",\"approvalNumber\":\"批准文号\"}";
        
        try {
            UserMessage message = UserMessage.from(prompt);
            String response = textModel.chat(message).aiMessage().text();
            
            System.out.println("AI返回的JSON: " + response);
            
            if (response != null && !response.isEmpty()) {
                response = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                
                com.alibaba.fastjson2.JSONObject json = com.alibaba.fastjson2.JSON.parseObject(response);
                
                result.setMedicineName(json.getString("name"));
                result.setSpec(json.getString("spec"));
                result.setDosageMg(json.getString("dosageMg"));
                result.setDosagePerTime(json.getInteger("dosagePerTime"));
                result.setUnit(json.getString("unit"));
                result.setFrequency(json.getInteger("frequency"));
                result.setFrequencyDetail(json.getString("frequencyDetail"));
                result.setPillsPerBox(json.getInteger("pillsPerBox"));
                result.setPackageSpec(json.getString("packageSpec"));
                result.setMedicineType(json.getString("medicineType"));
                result.setPrecautions(json.getString("precautions"));
                result.setManufacturer(json.getString("manufacturer"));
                result.setApprovalNumber(json.getString("approvalNumber"));
                result.setConfidence(0.9);
            }
        } catch (Exception e) {
            System.err.println("参数提取失败: " + e.getMessage());
            e.printStackTrace();
            result.setError("参数提取失败: " + e.getMessage());
        }
    }

    private void extractAllParameters(String combinedText, ProcessResult result) {
        String prompt = "你是一位资深药师，请从以下药品信息中精确提取所有关键信息。" +
            "" +
            "【提取规则】" +
            "1. 药品名称：提取完整的药品通用名称，保留罗马数字（如硝苯地平缓释片II）" +
            "2. 规格：提取剂量规格，格式如'20mg/片'或'0.5g/粒'" +
            "3. 每次剂量：提取每次服用的有效成分量，单位为mg或g" +
            "4. 每次服用粒数：" +
            "   - 如果用法用量明确写了'一次X片/粒'，直接使用该数字" +
            "   - 如果是范围剂量（如'一次3-4片'），取较高值" +
            "   - 如果需要计算：服用粒数 = 每次剂量 ÷ 每粒含量" +
            "5. 每日次数：从用法用量中提取（如'一日3次' -> 3）" +
            "6. 每盒粒数：从包装信息中提取粒数/片数/袋数" +
            "7. 药品类型：根据药品名称判断（降压药/降糖药/降脂药/感冒药/抗生素/胃药/钙片/其他）" +
            "" +
            "【重要警告】" +
            "- 绝对不能把'每次20mg'误解为'每次20粒'！" +
            "- mg是重量单位，粒是数量单位" +
            "- 必须仔细阅读用法用量原文" +
            "" +
            "【整合文本】" +
            combinedText +
            "" +
            "请以JSON格式返回（只返回JSON，不要其他内容）：" +
            "{\"name\":\"药品名称\",\"spec\":\"规格\",\"dosageMg\":\"每次剂量\",\"dosagePerTime\":每次服用粒数,\"unit\":\"粒或片\",\"frequency\":每日次数,\"frequencyDetail\":\"详细频次\",\"pillsPerBox\":每盒粒数,\"packageSpec\":\"包装规格\",\"medicineType\":\"药品类型\",\"precautions\":\"注意事项\",\"manufacturer\":\"生产企业\",\"approvalNumber\":\"批准文号\"}";
        
        try {
            UserMessage message = UserMessage.from(prompt);
            String response = textModel.chat(message).aiMessage().text();
            
            System.out.println("AI返回的JSON: " + response);
            
            if (response != null && !response.isEmpty()) {
                response = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                
                com.alibaba.fastjson2.JSONObject json = com.alibaba.fastjson2.JSON.parseObject(response);
                
                result.setMedicineName(json.getString("name"));
                result.setSpec(json.getString("spec"));
                result.setDosageMg(json.getString("dosageMg"));
                result.setDosagePerTime(json.getInteger("dosagePerTime"));
                result.setUnit(json.getString("unit"));
                result.setFrequency(json.getInteger("frequency"));
                result.setFrequencyDetail(json.getString("frequencyDetail"));
                result.setPillsPerBox(json.getInteger("pillsPerBox"));
                result.setPackageSpec(json.getString("packageSpec"));
                result.setMedicineType(json.getString("medicineType"));
                result.setPrecautions(json.getString("precautions"));
                result.setManufacturer(json.getString("manufacturer"));
                result.setApprovalNumber(json.getString("approvalNumber"));
                result.setConfidence(0.9);
            }
        } catch (Exception e) {
            System.err.println("参数提取失败: " + e.getMessage());
            e.printStackTrace();
            result.setError("参数提取失败: " + e.getMessage());
        }
    }

    private void validateAndCorrect(ProcessResult result) {
        if (result.getDosagePerTime() != null && result.getDosagePerTime() > 10) {
            System.err.println("警告：每次服用粒数异常 (" + result.getDosagePerTime() + ")，尝试从规格计算修正");
            
            Integer corrected = calculateDosageFromSpec(result.getSpec(), result.getDosageMg());
            if (corrected != null && corrected <= 10) {
                System.out.println("已修正每次服用粒数: " + result.getDosagePerTime() + " -> " + corrected);
                result.setDosagePerTime(corrected);
            } else {
                result.setDosagePerTime(1);
                System.out.println("无法计算，使用默认值1");
            }
        }
        
        if (result.getFrequency() != null && result.getFrequency() > 6) {
            System.err.println("警告：每日次数异常 (" + result.getFrequency() + ")，修正为3");
            result.setFrequency(3);
        }
        
        if (result.getUnit() == null || result.getUnit().isEmpty()) {
            result.setUnit(detectUnit(result.getSpec()));
        }
        
        if (result.getDosagePerTime() == null || result.getDosagePerTime() < 1) {
            result.setDosagePerTime(1);
        }
        
        if (result.getFrequency() == null || result.getFrequency() < 1) {
            result.setFrequency(1);
        }
        
        if (result.getMedicineType() == null || result.getMedicineType().isEmpty()) {
            result.setMedicineType(guessMedicineType(result.getMedicineName()));
        }
        
        if (result.getPackageSpec() == null || result.getPackageSpec().isEmpty()) {
            if (result.getPillsPerBox() != null && result.getPillsPerBox() > 0) {
                result.setPackageSpec(result.getPillsPerBox() + (result.getUnit() != null ? result.getUnit() : "粒") + "/盒");
            }
        }
    }

    private Integer calculateDosageFromSpec(String spec, String dosageMg) {
        if (spec == null || dosageMg == null) return null;
        
        try {
            Double specMg = extractMgValue(spec);
            Double doseMg = extractMgValue(dosageMg);
            
            if (specMg != null && doseMg != null && specMg > 0) {
                int count = (int) Math.round(doseMg / specMg);
                return Math.max(1, count);
            }
        } catch (Exception e) {
            System.err.println("计算服用粒数失败: " + e.getMessage());
        }
        return null;
    }

    private Double extractMgValue(String text) {
        if (text == null) return null;
        
        Pattern pattern = Pattern.compile("(\\d+\\.?\\d*)\\s*(mg|毫克|g|克)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            double value = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();
            
            if (unit.equals("g") || unit.equals("克")) {
                value *= 1000;
            }
            return value;
        }
        return null;
    }

    private String detectUnit(String spec) {
        if (spec == null) return "粒";
        
        String lower = spec.toLowerCase();
        if (lower.contains("片")) return "片";
        if (lower.contains("袋")) return "袋";
        if (lower.contains("瓶")) return "瓶";
        if (lower.contains("支")) return "支";
        if (lower.contains("丸")) return "丸";
        return "粒";
    }
    
    private String guessMedicineType(String name) {
        if (name == null) return "其他";
        String lowerName = name.toLowerCase();
        
        if (lowerName.contains("降脂") || lowerName.contains("他汀")) return "降脂药";
        if (lowerName.contains("降压") || lowerName.contains("氨氯地平") || lowerName.contains("硝苯地平")) return "降压药";
        if (lowerName.contains("降糖") || lowerName.contains("二甲双胍")) return "降糖药";
        if (lowerName.contains("感冒") || lowerName.contains("布洛芬")) return "感冒药";
        if (lowerName.contains("抗生素") || lowerName.contains("阿莫西林") || lowerName.contains("头孢") || lowerName.contains("红霉素")) return "抗生素";
        if (lowerName.contains("胃") || lowerName.contains("奥美拉唑")) return "胃药";
        if (lowerName.contains("钙")) return "钙片";
        return "其他";
    }
}
