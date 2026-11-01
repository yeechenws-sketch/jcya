package com.chat.older.xiechen.service.impl;

import com.chat.older.xiechen.service.HealthAIService;
import com.chat.older.xiechen.service.HealthMonitoringService;
import com.chat.older.xiechen.vo.HealthCardVO;
import com.chat.older.xiechen.vo.DailyTrendVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

@Service
public class HealthAIServiceImpl implements HealthAIService {

    @Autowired
    private HealthMonitoringService healthMonitoringService;

    private static final Random random = new Random();

    @Override
    public String generateHealthSummary(Long userId, List<HealthCardVO> healthCards) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("您的各项身体指标总体情况如下：");
        
        for (HealthCardVO card : healthCards) {
            String label = card.getLabel();
            String value = card.getValue();
            String status = card.getStatus();
            
            if (status.equals("正常") || status.equals("达标") || status.equals("优")) {
                summary.append(String.format("%s为%s，状态%s，非常好！", label, value, status));
            } else if (status.equals("稍高") || status.equals("偏低")) {
                summary.append(String.format("%s为%s，状态%s，需要注意。", label, value, status));
            } else if (status.equals("警告")) {
                summary.append(String.format("%s为%s，状态%s，建议及时就医！", label, value, status));
            }
        }
        
        summary.append("请继续保持健康的生活方式，定期监测身体状况。");
        
        return summary.toString();
    }

    @Override
    public String analyzeHealthTrend(Long userId, int days) {
        DailyTrendVO trend = healthMonitoringService.getDailyTrend(userId, days);
        
        StringBuilder analysis = new StringBuilder();
        analysis.append(String.format("最近%d天的健康趋势分析：", days));
        
        // 分析血压趋势
        List<Float> bpData = trend.getBloodPressure();
        if (!bpData.isEmpty()) {
            float avgBP = (float) bpData.stream().mapToDouble(Float::doubleValue).average().orElse(0);
            float maxBP = (float) bpData.stream().mapToDouble(Float::doubleValue).max().orElse(0);
            float minBP = (float) bpData.stream().mapToDouble(Float::doubleValue).min().orElse(0);
            
            analysis.append(String.format("血压平均值为%.1f，最高值为%.1f，最低值为%.1f。", avgBP, maxBP, minBP));
            
            if (avgBP < 120) {
                analysis.append("血压偏低，建议适当增加盐分摄入。");
            } else if (avgBP < 140) {
                analysis.append("血压正常，继续保持。");
            } else {
                analysis.append("血压偏高，建议减少盐分摄入，增加运动。");
            }
        }
        
        // 分析心率趋势
        List<Float> hrData = trend.getHeartRate();
        if (!hrData.isEmpty()) {
            float avgHR = (float) hrData.stream().mapToDouble(Float::doubleValue).average().orElse(0);
            
            analysis.append(String.format("心率平均值为%.1f次/分。", avgHR));
            
            if (avgHR < 60) {
                analysis.append("心率偏低，建议咨询医生。");
            } else if (avgHR < 100) {
                analysis.append("心率正常，继续保持。");
            } else {
                analysis.append("心率偏高，建议减少咖啡因摄入，适当休息。");
            }
        }
        
        // 分析步数趋势
        List<Integer> stepsData = trend.getSteps();
        if (!stepsData.isEmpty()) {
            int avgSteps = (int) stepsData.stream().mapToInt(Integer::intValue).average().orElse(0);
            
            analysis.append(String.format("平均每日步数为%d步。", avgSteps));
            
            if (avgSteps < 5000) {
                analysis.append("步数较少，建议增加日常活动量。");
            } else if (avgSteps < 10000) {
                analysis.append("步数适中，继续保持。");
            } else {
                analysis.append("步数充足，非常好！");
            }
        }
        
        return analysis.toString();
    }

    @Override
    public String generateHealthAdvice(Long userId, String type, float value) {
        StringBuilder advice = new StringBuilder();
        
        switch (type) {
            case "BP":
                if (value > 140) {
                    advice.append("您的血压偏高，建议：1. 减少盐分摄入，每天不超过5克；2. 增加有氧运动，如散步、太极拳等；3. 保持规律作息，避免熬夜；4. 定期监测血压变化。");
                } else if (value < 90) {
                    advice.append("您的血压偏低，建议：1. 适当增加盐分摄入；2. 避免长时间站立；3. 保证充足的水分摄入；4. 定期监测血压变化。");
                } else {
                    advice.append("您的血压正常，建议继续保持健康的生活方式，定期监测血压变化。");
                }
                break;
            case "HR":
                if (value > 100) {
                    advice.append("您的心率偏快，建议：1. 减少咖啡因摄入；2. 适当休息，避免过度劳累；3. 学习放松技巧，如深呼吸、冥想等；4. 定期监测心率变化。");
                } else if (value < 60) {
                    advice.append("您的心率偏慢，建议：1. 咨询医生，排除心脏疾病；2. 避免剧烈运动；3. 定期监测心率变化。");
                } else {
                    advice.append("您的心率正常，建议继续保持健康的生活方式，定期监测心率变化。");
                }
                break;
            case "BO":
                if (value < 95) {
                    advice.append("您的血氧饱和度偏低，建议：1. 保持室内通风良好；2. 适当进行有氧运动，如散步、太极拳等；3. 避免长时间处于密闭空间；4. 定期监测血氧变化。");
                } else {
                    advice.append("您的血氧饱和度正常，建议继续保持健康的生活方式，定期监测血氧变化。");
                }
                break;
            case "BG":
                if (value > 6.1) {
                    advice.append("您的血糖偏高，建议：1. 控制碳水化合物摄入，减少主食量；2. 增加膳食纤维摄入，多吃蔬菜、水果；3. 适当进行有氧运动；4. 定期监测血糖变化。");
                } else if (value < 3.9) {
                    advice.append("您的血糖偏低，建议：1. 按时进餐，避免长时间空腹；2. 随身携带糖果或饼干，以备不时之需；3. 定期监测血糖变化。");
                } else {
                    advice.append("您的血糖正常，建议继续保持健康的生活方式，定期监测血糖变化。");
                }
                break;
            default:
                advice.append("请提供有效的健康指标类型。");
        }
        
        return advice.toString();
    }

    @Override
    public String compareWithPrevious(Long userId, String type, float currentValue) {
        StringBuilder comparison = new StringBuilder();
        
        // 获取上一次的测量值
        var previousData = healthMonitoringService.getLatestVitalSign(userId, type);
        
        if (previousData != null) {
            float previousValue = previousData.getValue1();
            float difference = currentValue - previousValue;
            
            if (Math.abs(difference) < 0.1) {
                comparison.append(String.format("您的%s与上次测量值基本一致，保持稳定。", getTypeName(type)));
            } else if (difference > 0) {
                comparison.append(String.format("您的%s比上次测量值上升了%.1f，", getTypeName(type), difference));
                if (isValueHigh(type, currentValue)) {
                    comparison.append("需要注意。");
                } else {
                    comparison.append("但仍在正常范围内。");
                }
            } else {
                comparison.append(String.format("您的%s比上次测量值下降了%.1f，", getTypeName(type), Math.abs(difference)));
                if (isValueLow(type, currentValue)) {
                    comparison.append("需要注意。");
                } else {
                    comparison.append("继续保持。");
                }
            }
        } else {
            comparison.append(String.format("这是您第一次测量%s，建议定期监测。", getTypeName(type)));
        }
        
        return comparison.toString();
    }

    private String getTypeName(String type) {
        switch (type) {
            case "BP":
                return "血压";
            case "HR":
                return "心率";
            case "BO":
                return "血氧饱和度";
            case "BG":
                return "血糖";
            default:
                return type;
        }
    }

    private boolean isValueHigh(String type, float value) {
        switch (type) {
            case "BP":
                return value > 140;
            case "HR":
                return value > 100;
            case "BO":
                return value > 100;
            case "BG":
                return value > 6.1;
            default:
                return false;
        }
    }

    private boolean isValueLow(String type, float value) {
        switch (type) {
            case "BP":
                return value < 90;
            case "HR":
                return value < 60;
            case "BO":
                return value < 95;
            case "BG":
                return value < 3.9;
            default:
                return false;
        }
    }

    @Override
    public List<String> generateDailyHealthAdvices(Long userId, List<HealthCardVO> healthCards) {
        List<String> advices = new ArrayList<>();
        
        if (healthCards == null || healthCards.isEmpty()) {
            advices.add("暂无今日健康数据，请先进行健康测量");
            return advices;
        }

        for (HealthCardVO card : healthCards) {
            String label = card.getLabel();
            String value = card.getValue();
            String status = card.getStatus();
            
            if (label == null || value == null) continue;

            switch (label) {
                case "血压":
                    advices.addAll(generateBloodPressureAdvice(value, status));
                    break;
                case "心率":
                    advices.addAll(generateHeartRateAdvice(value, status));
                    break;
                case "血糖":
                    advices.addAll(generateBloodSugarAdvice(value, status));
                    break;
                case "血氧":
                    advices.addAll(generateBloodOxygenAdvice(value, status));
                    break;
                case "步数":
                    advices.addAll(generateStepsAdvice(value, status));
                    break;
                case "睡眠":
                    advices.addAll(generateSleepAdvice(value, status));
                    break;
            }
        }

        if (advices.isEmpty()) {
            advices.add("各项指标正常，继续保持健康的生活方式");
        }

        return advices;
    }

    private List<String> generateBloodPressureAdvice(String value, String status) {
        List<String> advices = new ArrayList<>();
        
        if ("正常".equals(status)) {
            String[] normalTips = {
                "血压保持在理想范围，继续保持低盐饮食",
                "血压正常，建议每天测量一次保持监测",
                "血压稳定，可适当进行有氧运动如散步"
            };
            advices.add(normalTips[random.nextInt(normalTips.length)]);
        } else if ("稍高".equals(status) || "偏高".equals(status)) {
            String[] highTips = {
                "血压偏高，建议晚餐清淡，减少盐分摄入",
                "血压略高，避免情绪激动，保持心情舒畅",
                "血压偏高，建议减少油腻食物，多吃蔬菜水果"
            };
            advices.add(highTips[random.nextInt(highTips.length)]);
        } else if ("偏低".equals(status)) {
            String[] lowTips = {
                "血压偏低，起床时动作要缓慢，避免头晕",
                "血压偏低，可适当增加盐分和水分摄入",
                "血压偏低，避免长时间站立，注意休息"
            };
            advices.add(lowTips[random.nextInt(lowTips.length)]);
        } else if ("警告".equals(status)) {
            advices.add("血压异常，建议及时就医检查");
        }
        
        return advices;
    }

    private List<String> generateHeartRateAdvice(String value, String status) {
        List<String> advices = new ArrayList<>();
        
        if ("正常".equals(status)) {
            String[] normalTips = {
                "心率正常，继续保持规律作息",
                "心率平稳，可进行适量运动锻炼",
                "心率正常，建议保持良好心态"
            };
            advices.add(normalTips[random.nextInt(normalTips.length)]);
        } else if ("稍高".equals(status) || "偏快".equals(status)) {
            String[] highTips = {
                "心率偏快，建议减少咖啡和浓茶摄入",
                "心率略高，注意休息，避免过度劳累",
                "心率偏快，可尝试深呼吸放松心情"
            };
            advices.add(highTips[random.nextInt(highTips.length)]);
        } else if ("偏低".equals(status) || "偏慢".equals(status)) {
            String[] lowTips = {
                "心率偏慢，如无不适可继续观察",
                "心率较低，建议咨询医生排除心脏问题"
            };
            advices.add(lowTips[random.nextInt(lowTips.length)]);
        } else if ("警告".equals(status)) {
            advices.add("心率异常，建议及时就医检查");
        }
        
        return advices;
    }

    private List<String> generateBloodSugarAdvice(String value, String status) {
        List<String> advices = new ArrayList<>();
        
        if ("正常".equals(status)) {
            String[] normalTips = {
                "血糖正常，继续保持健康饮食习惯",
                "血糖稳定，可适量食用低糖水果",
                "血糖正常，建议定期监测保持关注"
            };
            advices.add(normalTips[random.nextInt(normalTips.length)]);
        } else if ("稍高".equals(status) || "偏高".equals(status)) {
            String[] highTips = {
                "血糖稍高，建议晚餐减少主食摄入",
                "血糖偏高，少吃甜食和含糖饮料",
                "血糖略高，饭后可散步30分钟帮助降糖"
            };
            advices.add(highTips[random.nextInt(highTips.length)]);
        } else if ("偏低".equals(status)) {
            String[] lowTips = {
                "血糖偏低，按时进餐，避免空腹",
                "血糖偏低，随身携带糖果以备不时之需",
                "血糖偏低，可适当增加餐次，少食多餐"
            };
            advices.add(lowTips[random.nextInt(lowTips.length)]);
        } else if ("警告".equals(status)) {
            advices.add("血糖异常，建议及时就医检查");
        }
        
        return advices;
    }

    private List<String> generateBloodOxygenAdvice(String value, String status) {
        List<String> advices = new ArrayList<>();
        
        if ("正常".equals(status) || "优".equals(status)) {
            String[] normalTips = {
                "血氧饱和度正常，肺部功能良好",
                "血氧正常，继续保持室内通风",
                "血氧稳定，可进行适度有氧运动"
            };
            advices.add(normalTips[random.nextInt(normalTips.length)]);
        } else if ("稍低".equals(status) || "偏低".equals(status)) {
            String[] lowTips = {
                "血氧偏低，建议开窗通风，保持空气流通",
                "血氧略低，可进行深呼吸练习",
                "血氧偏低，避免去人多密闭的场所"
            };
            advices.add(lowTips[random.nextInt(lowTips.length)]);
        } else if ("警告".equals(status)) {
            advices.add("血氧异常，建议及时就医检查");
        }
        
        return advices;
    }

    private List<String> generateStepsAdvice(String value, String status) {
        List<String> advices = new ArrayList<>();
        
        try {
            int steps = Integer.parseInt(value.replaceAll("[^0-9]", ""));
            
            if (steps >= 10000) {
                String[] goodTips = {
                    "今日步数达标，运动量充足，继续保持",
                    "步数优秀，注意适当休息，避免过度疲劳",
                    "运动量很好，建议睡前泡脚放松肌肉"
                };
                advices.add(goodTips[random.nextInt(goodTips.length)]);
            } else if (steps >= 6000) {
                String[] normalTips = {
                    "步数接近目标，下午可再散步一会儿",
                    "运动量适中，建议傍晚再走一走",
                    "步数良好，继续保持日常活动"
                };
                advices.add(normalTips[random.nextInt(normalTips.length)]);
            } else if (steps >= 3000) {
                String[] lowTips = {
                    "步数偏少，建议下午3-4点外出散步",
                    "运动量不足，可在室内做些简单活动",
                    "步数较少，饭后散步有助于消化"
                };
                advices.add(lowTips[random.nextInt(lowTips.length)]);
            } else {
                String[] veryLowTips = {
                    "今日运动较少，建议适当增加活动量",
                    "步数过少，久坐不利健康，请起来走动",
                    "运动量不足，可从简单的室内活动开始"
                };
                advices.add(veryLowTips[random.nextInt(veryLowTips.length)]);
            }
        } catch (NumberFormatException e) {
            if ("达标".equals(status) || "正常".equals(status)) {
                advices.add("步数达标，继续保持");
            } else {
                advices.add("建议增加日常活动量");
            }
        }
        
        return advices;
    }

    private List<String> generateSleepAdvice(String value, String status) {
        List<String> advices = new ArrayList<>();
        
        try {
            double hours = Double.parseDouble(value.replaceAll("[^0-9.]", ""));
            
            if (hours >= 7 && hours <= 9) {
                String[] goodTips = {
                    "睡眠时间充足，继续保持规律作息",
                    "睡眠质量良好，早睡早起身体好",
                    "睡眠正常，保持当前作息习惯"
                };
                advices.add(goodTips[random.nextInt(goodTips.length)]);
            } else if (hours >= 6) {
                String[] normalTips = {
                    "睡眠时间稍短，建议今晚早点休息",
                    "睡眠略少，午间可小憩补充精力",
                    "睡眠基本充足，尽量保持规律作息"
                };
                advices.add(normalTips[random.nextInt(normalTips.length)]);
            } else if (hours < 6) {
                String[] lowTips = {
                    "睡眠不足，建议今晚提前半小时上床",
                    "睡眠时间过少，长期熬夜伤身体",
                    "睡眠不够，白天可适当休息补充"
                };
                advices.add(lowTips[random.nextInt(lowTips.length)]);
            } else if (hours > 9) {
                String[] highTips = {
                    "睡眠时间较长，注意是否白天过于疲劳",
                    "睡眠过多也可能影响精神，建议适度运动",
                    "睡眠充足但不宜过长，保持规律作息"
                };
                advices.add(highTips[random.nextInt(highTips.length)]);
            }
        } catch (NumberFormatException e) {
            if ("正常".equals(status)) {
                advices.add("睡眠正常，继续保持");
            } else {
                advices.add("建议保持规律作息，早睡早起");
            }
        }
        
        return advices;
    }
}
