package com.chat.older.xiechen.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chat.older.xiechen.dao.MedicineInfoMapper;
import com.chat.older.xiechen.model.MedicineInfo;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MedicineVoiceExtractor {

    @Autowired
    private MedicineAiService medicineAiService;
    
    @Autowired
    private MedicineInfoMapper medicineInfoMapper;

    public Map<String, Object> extractMedicineInfo(String voiceText) {
        Map<String, Object> result = new HashMap<>();
        
        if (voiceText == null || voiceText.trim().isEmpty()) {
            return result;
        }
        
        try {
            String correctedText = correctDosageText(voiceText);
            String aiResponse = medicineAiService.extractMedicineInfo(correctedText);
            
            if (aiResponse != null && aiResponse.contains("{")) {
                int start = aiResponse.indexOf("{");
                int end = aiResponse.lastIndexOf("}") + 1;
                String jsonStr = aiResponse.substring(start, end);
                
                JSONObject json = JSON.parseObject(jsonStr);
                
                if (json.containsKey("name")) {
                    String rawName = json.getString("name");
                    result.put("name", rawName);
                    
                    MedicineInfo matchedInfo = tryMatchMedicine(rawName);
                    if (matchedInfo != null) {
                        fillFromMedicineInfo(result, matchedInfo, json);
                    }
                }
                if (!result.containsKey("dosagePerTime") && json.containsKey("dosagePerTime")) {
                    result.put("dosagePerTime", json.getInteger("dosagePerTime"));
                }
                if (!result.containsKey("frequency") && json.containsKey("frequency")) {
                    result.put("frequency", json.getInteger("frequency"));
                }
                if (!result.containsKey("unit") && json.containsKey("unit")) {
                    result.put("unit", json.getString("unit"));
                }
                if (json.containsKey("packages")) {
                    result.put("packages", json.getInteger("packages"));
                }
                if (!result.containsKey("perPackage") && json.containsKey("perPackage")) {
                    result.put("perPackage", json.getInteger("perPackage"));
                }
                if (!result.containsKey("type") && json.containsKey("type")) {
                    result.put("type", json.getString("type"));
                }
                if (json.containsKey("reminderTimes")) {
                    result.put("reminderTimes", json.getJSONArray("reminderTimes").toJavaList(String.class));
                }
                if (json.containsKey("corrections")) {
                    result.put("corrections", json.getJSONArray("corrections").toJavaList(String.class));
                }
                if (!result.containsKey("spec") && json.containsKey("spec")) {
                    result.put("spec", json.getString("spec"));
                }
            }
        } catch (Exception e) {
            System.err.println("AI extraction failed, using regex fallback: " + e.getMessage());
            result = extractByRegex(voiceText);
        }
        
        if (result.isEmpty()) {
            result = extractByRegex(voiceText);
        }
        
        if (result.containsKey("name") && !result.containsKey("type")) {
            String name = (String) result.get("name");
            result.put("type", guessMedicineType(name));
        }
        
        if (result.containsKey("name")) {
            String correctedName = correctMedicineName((String) result.get("name"));
            if (!correctedName.equals(result.get("name"))) {
                if (!result.containsKey("corrections")) {
                    result.put("corrections", new java.util.ArrayList<String>());
                }
                ((java.util.List<String>) result.get("corrections")).add(result.get("name") + "纠正为" + correctedName);
                result.put("name", correctedName);
            }
        }
        
        return result;
    }
    
    private String correctMedicineName(String name) {
        if (name == null) return name;
        
        java.util.Map<String, String> corrections = new java.util.HashMap<>();
        corrections.put("阿伐他丁", "阿托伐他汀");
        corrections.put("阿法他丁", "阿托伐他汀");
        corrections.put("氨绿地平", "氨氯地平");
        corrections.put("硝本地平", "硝苯地平");
        corrections.put("二甲双瓜", "二甲双胍");
        corrections.put("罗红每素", "罗红霉素");
        corrections.put("罗红枚素", "罗红霉素");
        corrections.put("阿莫西灵", "阿莫西林");
        corrections.put("头饱", "头孢");
        
        for (java.util.Map.Entry<String, String> entry : corrections.entrySet()) {
            if (name.contains(entry.getKey())) {
                return name.replace(entry.getKey(), entry.getValue());
            }
        }
        return name;
    }

    private String correctDosageText(String text) {
        if (text == null) return text;
        
        java.util.Map<String, String> dosageCorrections = new java.util.HashMap<>();
        dosageCorrections.put("实力", "十粒");
        dosageCorrections.put("十力", "十粒");
        dosageCorrections.put("事实", "十粒");
        dosageCorrections.put("两百两百", "两盒");
        dosageCorrections.put("两白", "两盒");
        dosageCorrections.put("一共实力", "一共十粒");
        dosageCorrections.put("共实力", "共十粒");
        dosageCorrections.put("一粒一粒", "一次一粒");
        dosageCorrections.put("两粒两粒", "两次两粒");
        
        String result = text;
        for (java.util.Map.Entry<String, String> entry : dosageCorrections.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
    
    private MedicineInfo tryMatchMedicine(String inputName) {
        if (inputName == null || inputName.trim().isEmpty()) {
            return null;
        }
        
        MedicineInfo exactMatch = medicineInfoMapper.selectByName(inputName);
        if (exactMatch != null) {
            return exactMatch;
        }
        
        String simplified = inputName.replaceAll("[（(].*?[）)]", "").trim();
        exactMatch = medicineInfoMapper.selectByName(simplified);
        if (exactMatch != null) {
            return exactMatch;
        }
        
        String coreName = simplified.replaceAll("(片|胶囊|颗粒|口服液|糖浆|滴丸|缓释片|控释片|分散片|肠溶片|泡腾片|合剂|丸|膏|贴|喷雾|注射液|注射剂|栓剂|软膏|眼药水|滴眼液)$", "");
        if (!coreName.equals(simplified)) {
            exactMatch = medicineInfoMapper.selectByName(coreName);
            if (exactMatch != null) {
                return exactMatch;
            }
        }
        
        return null;
    }
    
    private void fillFromMedicineInfo(Map<String, Object> result, MedicineInfo info, JSONObject json) {
        if (info.getSpec() != null && !result.containsKey("spec")) {
            result.put("spec", info.getSpec());
            parseSpecInfo(result, info.getSpec());
        }
        if (info.getAgentiaUnit() != null && !result.containsKey("unit")) {
            result.put("unit", info.getAgentiaUnit());
        }
        if (info.getType() != null && !result.containsKey("type")) {
            result.put("type", info.getType());
        }
    }
    
    private void parseSpecInfo(Map<String, Object> result, String spec) {
        if (spec == null || spec.isEmpty()) return;
        
        Pattern unitPattern = Pattern.compile("(\\d+)\\s*(粒|片|袋|毫升|mg|g|ml)");
        Matcher unitMatcher = unitPattern.matcher(spec);
        if (unitMatcher.find() && !result.containsKey("unit")) {
            result.put("unit", unitMatcher.group(2));
        }
        
        Pattern countPattern = Pattern.compile("[*×x](\\d+)\\s*(粒|片|袋|毫升)?");
        Matcher countMatcher = countPattern.matcher(spec);
        if (countMatcher.find() && !result.containsKey("perPackage")) {
            result.put("perPackage", Integer.parseInt(countMatcher.group(1)));
        }
        
        Pattern simpleCountPattern = Pattern.compile("(\\d+)\\s*(粒|片|袋)");
        Matcher simpleMatcher = simpleCountPattern.matcher(spec);
        if (simpleMatcher.find() && !result.containsKey("perPackage")) {
            result.put("perPackage", Integer.parseInt(simpleMatcher.group(1)));
            if (!result.containsKey("unit")) {
                result.put("unit", simpleMatcher.group(2));
            }
        }
    }
    
    private String guessMedicineType(String name) {
        if (name == null) return "其他";
        String lowerName = name.toLowerCase();
        
        if (lowerName.contains("降脂") || lowerName.contains("他汀") || lowerName.contains("阿托伐他汀")
            || lowerName.contains("瑞舒伐他汀") || lowerName.contains("辛伐他汀") || lowerName.contains("洛伐他汀")
            || lowerName.contains("匹伐他汀") || lowerName.contains("血脂康")) {
            return "降脂药";
        }
        if (lowerName.contains("降压") || lowerName.contains("氨氯地平") || lowerName.contains("硝苯地平") 
            || lowerName.contains("卡托普利") || lowerName.contains("缬沙坦") || lowerName.contains("氯沙坦")) {
            return "降压药";
        }
        if (lowerName.contains("降糖") || lowerName.contains("二甲双胍") || lowerName.contains("格列")
            || lowerName.contains("胰岛素") || lowerName.contains("阿卡波糖")) {
            return "降糖药";
        }
        if (lowerName.contains("心") && (lowerName.contains("血管") || lowerName.contains("心痛")))
            return "心血管药";
        if (lowerName.contains("感冒") || lowerName.contains("退烧") || lowerName.contains("布洛芬")
            || lowerName.contains("对乙酰氨基酚") || lowerName.contains("扑热息痛")) {
            return "感冒药";
        }
        if (lowerName.contains("消炎") || lowerName.contains("抗生素") || lowerName.contains("阿莫西林")
            || lowerName.contains("头孢") || lowerName.contains("红霉素") || lowerName.contains("罗红霉素")
            || lowerName.contains("阿奇霉素") || lowerName.contains("青霉素")) {
            return "抗生素";
        }
        if (lowerName.contains("胃") || lowerName.contains("奥美拉唑") || lowerName.contains("雷贝拉唑")
            || lowerName.contains("多潘立酮")) {
            return "胃药";
        }
        if (lowerName.contains("钙") || lowerName.contains("维生素d") || lowerName.contains("维d")) {
            return "钙片";
        }
        return "其他";
    }

    private Map<String, Object> extractByRegex(String text) {
        Map<String, Object> result = new HashMap<>();
        
        Pattern namePattern = Pattern.compile("药[物品]?名[称叫：:]*\\s*([^，,。.\\s]+)");
        Matcher nameMatcher = namePattern.matcher(text);
        if (nameMatcher.find()) {
            result.put("name", nameMatcher.group(1));
        }
        
        Pattern namePattern2 = Pattern.compile("(?:叫|是|吃|服用)\\s*([^，,。.\\s]{2,10})(?:片|胶囊|颗粒|药)");
        Matcher nameMatcher2 = namePattern2.matcher(text);
        if (nameMatcher2.find() && !result.containsKey("name")) {
            result.put("name", nameMatcher2.group(1));
        }
        
        Pattern dosagePattern = Pattern.compile("(?:一次|每次|单次)[^\\d]*(\\d+)[^\\d]*(?:粒|片|袋|毫升|mg|g)");
        Matcher dosageMatcher = dosagePattern.matcher(text);
        if (dosageMatcher.find()) {
            result.put("dosagePerTime", Integer.parseInt(dosageMatcher.group(1)));
        }
        
        Pattern freqPattern = Pattern.compile("(?:一天|每日|每天)[^\\d]*(\\d+)[^\\d]*次");
        Matcher freqMatcher = freqPattern.matcher(text);
        if (freqMatcher.find()) {
            result.put("frequency", Integer.parseInt(freqMatcher.group(1)));
        }
        
        Pattern unitPattern = Pattern.compile("(\\d+)\\s*(粒|片|袋|毫升|mg|g)");
        Matcher unitMatcher = unitPattern.matcher(text);
        if (unitMatcher.find()) {
            result.put("unit", unitMatcher.group(2));
        }
        
        Pattern packagePattern = Pattern.compile("(\\d+)\\s*[盒瓶]");
        Matcher packageMatcher = packagePattern.matcher(text);
        if (packageMatcher.find()) {
            result.put("packages", Integer.parseInt(packageMatcher.group(1)));
        }
        
        Pattern perPackagePattern = Pattern.compile("每[盒瓶][^\\d]*(\\d+)");
        Matcher perPackageMatcher = perPackagePattern.matcher(text);
        if (perPackageMatcher.find()) {
            result.put("perPackage", Integer.parseInt(perPackageMatcher.group(1)));
        }
        
        Pattern perPackagePattern2 = Pattern.compile("[盒瓶][^\\d]*(\\d+)[^\\d]*(粒|片|袋)");
        Matcher perPackageMatcher2 = perPackagePattern2.matcher(text);
        if (perPackageMatcher2.find() && !result.containsKey("perPackage")) {
            result.put("perPackage", Integer.parseInt(perPackageMatcher2.group(1)));
        }
        
        if (result.containsKey("name")) {
            result.put("type", guessMedicineType((String) result.get("name")));
        }
        
        return result;
    }
}
