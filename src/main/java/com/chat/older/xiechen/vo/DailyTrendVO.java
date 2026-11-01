package com.chat.older.xiechen.vo;

import lombok.Data;
import java.util.List;

@Data
public class DailyTrendVO {
    private List<String> dates;
    private List<Float> bloodPressure;
    private List<Float> heartRate;
    private List<Float> bloodOxygen;
    private List<Float> bloodGlucose;
    private List<Integer> steps;
    private List<Float> sleepHours;
}
