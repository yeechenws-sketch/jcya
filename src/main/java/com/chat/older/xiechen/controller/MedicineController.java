package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.ChatRecordMapper;
import com.chat.older.xiechen.dao.EmergencyContactMapper;
import com.chat.older.xiechen.dao.MessageMapper;
import com.chat.older.xiechen.dao.UserMapper;
import com.chat.older.xiechen.handler.ChatHandler;
import com.chat.older.xiechen.model.ChatRecord;
import com.chat.older.xiechen.model.EmergencyContact;
import com.chat.older.xiechen.model.MedicineInfo;
import com.chat.older.xiechen.model.Message;
import com.chat.older.xiechen.model.User;
import com.chat.older.xiechen.service.BaiduAsrService;
import com.chat.older.xiechen.service.MedicineAssistantService;
import com.chat.older.xiechen.service.MedicineProcessService;
import com.chat.older.xiechen.service.MedicineService;
import com.chat.older.xiechen.service.NotificationService;
import com.chat.older.xiechen.service.UploadService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/medicine")
public class MedicineController {

    @Autowired
    private UploadService uploadService;

    @Autowired
    private MedicineService medicineService;
    
    @Autowired
    private MedicineAssistantService medicineAssistantService;
    
    @Autowired
    private BaiduAsrService baiduAsrService;
    
    @Autowired
    private ChatRecordMapper chatRecordMapper;
    
    @Autowired
    private MedicineProcessService medicineProcessService;
    
    @Autowired
    private EmergencyContactMapper emergencyContactMapper;
    
    @Autowired
    private MessageMapper messageMapper;
    
    @Autowired
    private ChatHandler chatHandler;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private UserMapper userMapper;

    @PostMapping("/identify")
    public ResponseEntity<String> identifyMedicine(@RequestParam("file") MultipartFile file, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return ResponseEntity.status(401).body("请先登录");

        try {
            String imageUrl = uploadService.upload(file.getInputStream(), file.getOriginalFilename());
            if (imageUrl == null) return ResponseEntity.status(500).body("图片上传失败");

            ChatRecord userRec = new ChatRecord(null, user.getId(), "user", imageUrl, LocalDateTime.now());
            chatRecordMapper.insertRecord(userRec);

            String result = medicineService.analyzeImage(imageUrl);

            ChatRecord aiRec = new ChatRecord(null, user.getId(), "ai", result, LocalDateTime.now());
            chatRecordMapper.insertRecord(aiRec);

            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("处理失败: " + e.getMessage());
        }
    }

    @PostMapping("/identify/name")
    public Result identifyMedicineName(@RequestBody Map<String, String> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        String imageUrl = params.get("imageUrl");
        if (imageUrl == null || imageUrl.isEmpty()) {
            return Result.error("请提供图片链接");
        }
        
        try {
            Map<String, Object> result = medicineAssistantService.identifyMedicineName(imageUrl);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("识别失败: " + e.getMessage());
        }
    }

    @PostMapping("/identify/instruction")
    public Result identifyMedicineInstruction(@RequestBody Map<String, String> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        String imageUrl = params.get("imageUrl");
        if (imageUrl == null || imageUrl.isEmpty()) {
            return Result.error("请提供图片链接");
        }
        
        try {
            Map<String, Object> result = medicineAssistantService.identifyMedicineInstruction(imageUrl);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("识别失败: " + e.getMessage());
        }
    }

    @PostMapping("/process")
    public Result processMedicineImage(@RequestBody Map<String, String> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        String imageUrl = params.get("imageUrl");
        if (imageUrl == null || imageUrl.isEmpty()) {
            return Result.error("请提供图片链接");
        }
        
        try {
            MedicineProcessService.ProcessResult result = medicineProcessService.processMedicineImage(imageUrl);

            if (result.getError() != null) {
                return Result.error(result.getError());
            }

            return buildProcessResult(result);
        } catch (Exception e) {
            return Result.error("处理失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/process/cover")
    public Result processCoverImage(@RequestBody Map<String, String> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        String imageUrl = params.get("imageUrl");
        if (imageUrl == null || imageUrl.isEmpty()) {
            return Result.error("请提供图片链接");
        }
        
        try {
            MedicineProcessService.ProcessResult result = medicineProcessService.processCoverImage(imageUrl);

            if (result.getError() != null) {
                return Result.error(result.getError());
            }

            Map<String, Object> data = new java.util.HashMap<>();
            data.put("coverText", result.getCoverText());

            return Result.success(data);
        } catch (Exception e) {
            return Result.error("处理失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/process/instruction")
    public Result processInstructionImage(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        String imageUrl = (String) params.get("imageUrl");
        String coverText = (String) params.get("coverText");
        
        if (imageUrl == null || imageUrl.isEmpty()) {
            return Result.error("请提供图片链接");
        }
        
        try {
            MedicineProcessService.ProcessResult result = medicineProcessService.processInstructionImage(imageUrl, coverText);

            if (result.getError() != null) {
                return Result.error(result.getError());
            }

            return buildProcessResult(result);
        } catch (Exception e) {
            return Result.error("处理失败: " + e.getMessage());
        }
    }
    
    private Result buildProcessResult(MedicineProcessService.ProcessResult result) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("rawOcrText", result.getRawOcrText());
        data.put("cleanedText", result.getCleanedText());
        data.put("coverText", result.getCoverText());
        data.put("name", result.getMedicineName());
        data.put("spec", result.getSpec());
        data.put("dosagePerTime", result.getDosagePerTime());
        data.put("unit", result.getUnit());
        data.put("dosageMg", result.getDosageMg());
        data.put("frequency", result.getFrequency());
        data.put("frequencyDetail", result.getFrequencyDetail());
        data.put("precautions", result.getPrecautions());
        data.put("manufacturer", result.getManufacturer());
        data.put("approvalNumber", result.getApprovalNumber());
        data.put("pillsPerBox", result.getPillsPerBox());
        data.put("bottlesPerBox", result.getBottlesPerBox());
        data.put("platesPerBox", result.getPlatesPerBox());
        data.put("bagsPerBox", result.getBagsPerBox());
        data.put("totalQuantity", result.getTotalQuantity());
        data.put("packageSpec", result.getPackageSpec());
        data.put("medicineType", result.getMedicineType());
        data.put("confidence", result.getConfidence());

        return Result.success(data);
    }

    @GetMapping("/list")
    public Result getMedicineList(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        List<Map<String, Object>> medicines = medicineAssistantService.getUserMedicines(user.getId().intValue());
        return Result.success(medicines);
    }

    @GetMapping("/detail/{id}")
    public Result getMedicineDetail(@PathVariable Integer id, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        Map<String, Object> detail = medicineAssistantService.getMedicineDetail(id);
        if (detail == null) {
            return Result.error("药品不存在");
        }
        return Result.success(detail);
    }

    @PostMapping("/add")
    public Result addMedicine(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        Result result = medicineAssistantService.addMedicine(user.getId().intValue(), params);
        
        if (result.getCode() == 200) {
            sendMedicineNotificationToContacts(user, params, "手动录入");
        }
        
        return result;
    }
    
    @PostMapping("/addWithNotification")
    public Result addMedicineWithNotification(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        String inputMethod = (String) params.getOrDefault("inputMethod", "手动录入");
        
        Result result = medicineAssistantService.addMedicine(user.getId().intValue(), params);
        
        if (result.getCode() == 200) {
            sendMedicineNotificationToContacts(user, params, inputMethod);
        }
        
        return result;
    }
    
    private void sendMedicineNotificationToContacts(User user, Map<String, Object> medicineInfo, String inputMethod) {
        try {
            List<EmergencyContact> contacts = emergencyContactMapper.selectByUserId(user.getId());
            if (contacts == null || contacts.isEmpty()) {
                System.out.println("用户 " + user.getId() + " 没有设置紧急联系人");
                return;
            }
            
            String senderName = user.getNickname() != null ? user.getNickname() : user.getUsername();
            
            for (EmergencyContact contact : contacts) {
                Long contactUserId = contact.getContactUserId();
                
                if (contactUserId != null) {
                    sendMedicineMessageToUser(user.getId(), contactUserId, medicineInfo, senderName, inputMethod);
                } else {
                    String phone = contact.getContactPhone();
                    if (phone != null && !phone.isEmpty()) {
                        sendMedicineSms(phone, medicineInfo, senderName, inputMethod);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("发送药品通知失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void sendMedicineMessageToUser(Long fromId, Long toId, Map<String, Object> medicineInfo, 
                                           String senderName, String inputMethod) {
        StringBuilder content = new StringBuilder();
        content.append("【药品录入通知】\n");
        content.append(senderName).append(" 通过").append(inputMethod).append("添加了新药品：\n\n");
        
        if (medicineInfo.get("name") != null) {
            content.append("药品名称：").append(medicineInfo.get("name")).append("\n");
        }
        if (medicineInfo.get("spec") != null) {
            content.append("药品规格：").append(medicineInfo.get("spec")).append("\n");
        }
        if (medicineInfo.get("dosagePerTime") != null && medicineInfo.get("unit") != null) {
            content.append("每次服用：").append(medicineInfo.get("dosagePerTime"))
                   .append(" ").append(medicineInfo.get("unit")).append("\n");
        }
        if (medicineInfo.get("frequency") != null) {
            content.append("每日次数：").append(medicineInfo.get("frequency")).append(" 次\n");
        }
        if (medicineInfo.get("perPackage") != null) {
            content.append("每盒数量：").append(medicineInfo.get("perPackage")).append("\n");
        }
        if (medicineInfo.get("packages") != null) {
            content.append("购买盒数：").append(medicineInfo.get("packages")).append("\n");
        }
        if (medicineInfo.get("precautions") != null) {
            String precautions = (String) medicineInfo.get("precautions");
            if (precautions.length() > 100) {
                precautions = precautions.substring(0, 100) + "...";
            }
            content.append("注意事项：").append(precautions).append("\n");
        }
        
        content.append("\n请关注家人的用药情况。");
        
        Message message = new Message();
        message.setFromId(fromId);
        message.setToId(toId);
        message.setContent(content.toString());
        message.setMsgType("text");
        message.setIsRead(0);
        messageMapper.insertMessage(message);
        
        Map<String, Object> notice = new HashMap<>();
        notice.put("fromId", fromId);
        notice.put("toId", toId);
        notice.put("msgType", "TEXT");
        notice.put("content", content.toString());
        chatHandler.sendToUser(toId, notice);
        
        System.out.println("药品录入通知已发送: from=" + fromId + ", to=" + toId);
    }
    
    private void sendMedicineSms(String phone, Map<String, Object> medicineInfo, String senderName, String inputMethod) {
        StringBuilder message = new StringBuilder();
        message.append("【药品录入通知】\n");
        message.append(senderName).append(" 通过").append(inputMethod).append("添加了新药品。\n");
        
        if (medicineInfo.get("name") != null) {
            message.append("药品：").append(medicineInfo.get("name")).append("\n");
        }
        if (medicineInfo.get("dosagePerTime") != null) {
            message.append("每次：").append(medicineInfo.get("dosagePerTime"));
            if (medicineInfo.get("unit") != null) {
                message.append(medicineInfo.get("unit"));
            }
            message.append("\n");
        }
        if (medicineInfo.get("frequency") != null) {
            message.append("每日").append(medicineInfo.get("frequency")).append("次\n");
        }
        message.append("请关注家人用药。");
        
        notificationService.sendSms(phone, message.toString());
    }

    @PostMapping("/update/{id}")
    public Result updateMedicine(@PathVariable Integer id, @RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        return medicineAssistantService.updateMedicine(user.getId().intValue(), id, params);
    }

    @PostMapping("/delete/{id}")
    public Result deleteMedicine(@PathVariable Integer id, @RequestBody(required = false) Map<String, String> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        String deleteReason = params != null ? params.get("reason") : null;
        return medicineAssistantService.deleteMedicine(user.getId().intValue(), id, deleteReason);
    }

    @PostMapping("/take/{id}")
    public Result markAsTaken(@PathVariable Integer id, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        return medicineAssistantService.markAsTaken(user.getId().intValue(), id);
    }

    @PostMapping("/takeAll")
    public Result markAllAsTaken(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        return medicineAssistantService.markAllAsTaken(user.getId().intValue());
    }

    @GetMapping("/makeup-check/{id}")
    public Result checkMakeupDose(@PathVariable Integer id, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        Map<String, Object> result = medicineAssistantService.checkMakeupDoseEligibility(id);
        return Result.success(result);
    }

    @PostMapping("/makeup/{id}")
    public Result makeupDose(@PathVariable Integer id, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        return medicineAssistantService.makeupDose(user.getId().intValue(), id);
    }

    @GetMapping("/todayLogs")
    public Result getTodayLogs(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        List<Map<String, Object>> logs = medicineAssistantService.getTodayLogs(user.getId().intValue());
        return Result.success(logs);
    }

    @GetMapping("/alerts")
    public Result getStockAlerts(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        List<Map<String, Object>> alerts = medicineAssistantService.getStockAlerts(user.getId().intValue());
        return Result.success(alerts);
    }

    @GetMapping("/lowStockCount")
    public Result getLowStockCount(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        int count = medicineAssistantService.getLowStockCount(user.getId().intValue());
        return Result.success(count);
    }

    @GetMapping("/boxStatus")
    public Result getBoxStatus(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        Map<String, Object> status = medicineAssistantService.getBoxStatus(user.getId().intValue());
        return Result.success(status);
    }

    @GetMapping("/barcode/{code}")
    public Result getMedicineByBarcode(@PathVariable String code, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        MedicineInfo info = medicineAssistantService.getMedicineByBarcode(code);
        if (info == null) {
            return Result.error("未找到该药品信息");
        }
        return Result.success(info);
    }
    
    @PostMapping("/barcode/add")
    public Result addMedicineByBarcode(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        String barcode = (String) params.get("barcode");
        if (barcode == null || barcode.isEmpty()) {
            return Result.error("请提供条形码");
        }
        
        MedicineInfo info = medicineAssistantService.getMedicineByBarcode(barcode);
        if (info == null) {
            return Result.error("未找到该药品信息，条形码：" + barcode);
        }
        
        Map<String, Object> medicineParams = new HashMap<>();
        medicineParams.put("name", info.getName());
        medicineParams.put("spec", info.getSpec());
        medicineParams.put("type", info.getType());
        medicineParams.put("dosagePerTime", params.getOrDefault("dosagePerTime", 1));
        medicineParams.put("unit", params.getOrDefault("unit", "片"));
        medicineParams.put("frequency", params.getOrDefault("frequency", 2));
        medicineParams.put("packages", params.getOrDefault("packages", 1));
        medicineParams.put("perPackage", params.getOrDefault("perPackage", 30));
        medicineParams.put("precautions", info.getIndication());
        medicineParams.put("manufacturer", info.getDrugFrom());
        medicineParams.put("approvalNumber", info.getDrugPzwh());
        medicineParams.put("inputMethod", "扫码录入");
        
        Result result = medicineAssistantService.addMedicine(user.getId().intValue(), medicineParams);
        
        if (result.getCode() == 200) {
            sendMedicineNotificationToContacts(user, medicineParams, "扫码录入");
        }
        
        return result;
    }

    @PostMapping("/barcode/image")
    public Result getMedicineByBarcodeImage(@RequestParam("file") MultipartFile file, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        try {
            if (file.isEmpty()) return Result.error("图片文件为空");
            
            String barcode = medicineAssistantService.extractBarcodeFromImage(file.getInputStream());
            if (barcode == null || barcode.isEmpty()) {
                return Result.error("未能识别图片中的条形码");
            }
            
            MedicineInfo info = medicineAssistantService.getMedicineByBarcode(barcode);
            if (info == null) {
                return Result.error("未找到该药品信息，条形码：" + barcode);
            }
            return Result.success(info);
        } catch (Exception e) {
            return Result.error("图片识别失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/barcode/image/add")
    public Result addMedicineByBarcodeImage(@RequestParam("file") MultipartFile file, 
                                            @RequestParam(required = false) Integer dosagePerTime,
                                            @RequestParam(required = false) Integer frequency,
                                            @RequestParam(required = false) Integer packages,
                                            HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        try {
            if (file.isEmpty()) return Result.error("图片文件为空");
            
            String barcode = medicineAssistantService.extractBarcodeFromImage(file.getInputStream());
            if (barcode == null || barcode.isEmpty()) {
                return Result.error("未能识别图片中的条形码");
            }
            
            MedicineInfo info = medicineAssistantService.getMedicineByBarcode(barcode);
            if (info == null) {
                return Result.error("未找到该药品信息，条形码：" + barcode);
            }
            
            Map<String, Object> medicineParams = new HashMap<>();
            medicineParams.put("name", info.getName());
            medicineParams.put("spec", info.getSpec());
            medicineParams.put("type", info.getType());
            medicineParams.put("dosagePerTime", dosagePerTime != null ? dosagePerTime : 1);
            medicineParams.put("unit", "片");
            medicineParams.put("frequency", frequency != null ? frequency : 2);
            medicineParams.put("packages", packages != null ? packages : 1);
            medicineParams.put("perPackage", 30);
            medicineParams.put("precautions", info.getIndication());
            medicineParams.put("manufacturer", info.getDrugFrom());
            medicineParams.put("approvalNumber", info.getDrugPzwh());
            medicineParams.put("inputMethod", "扫码录入");
            
            Result result = medicineAssistantService.addMedicine(user.getId().intValue(), medicineParams);
            
            if (result.getCode() == 200) {
                sendMedicineNotificationToContacts(user, medicineParams, "扫码录入");
            }
            
            return result;
        } catch (Exception e) {
            return Result.error("处理失败: " + e.getMessage());
        }
    }

    @GetMapping("/nextReminder")
    public Result getNextReminder(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        String nextReminder = medicineAssistantService.getNextGlobalReminder(user.getId().intValue());
        return Result.success(nextReminder);
    }

    @PostMapping("/asr/medicine")
    public Result processVoiceMedicine(@RequestParam("file") MultipartFile file, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        try {
            if (file.isEmpty()) return Result.error("音频文件为空");
            
            byte[] bytes = file.getBytes();
            String rawText = baiduAsrService.transcribe(bytes);
            
            if (rawText == null || rawText.isEmpty()) {
                return Result.error("未识别到语音内容");
            }
            
            Result result = medicineAssistantService.processVoiceInput(user.getId().intValue(), rawText);
            if (result.getData() instanceof Map) {
                Map<String, Object> data = (Map<String, Object>) result.getData();
                data.put("rawVoiceText", rawText);
            }
            return result;
        } catch (Exception e) {
            return Result.error("语音识别失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/voice/add")
    public Result addMedicineByVoice(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        String rawVoiceText = (String) params.get("rawVoiceText");
        String cleanedText = (String) params.get("cleanedText");
        
        Map<String, Object> medicineParams = new HashMap<>();
        medicineParams.put("name", params.get("name"));
        medicineParams.put("spec", params.get("spec"));
        medicineParams.put("dosagePerTime", params.getOrDefault("dosagePerTime", 1));
        medicineParams.put("unit", params.getOrDefault("unit", "片"));
        medicineParams.put("frequency", params.getOrDefault("frequency", 2));
        medicineParams.put("packages", params.getOrDefault("packages", 1));
        medicineParams.put("perPackage", params.getOrDefault("perPackage", 30));
        medicineParams.put("precautions", params.get("precautions"));
        medicineParams.put("inputMethod", "语音录入");
        medicineParams.put("rawVoiceText", rawVoiceText);
        medicineParams.put("cleanedText", cleanedText);
        
        Result result = medicineAssistantService.addMedicine(user.getId().intValue(), medicineParams);
        
        if (result.getCode() == 200) {
            sendVoiceMedicineNotificationToContacts(user, medicineParams, rawVoiceText, cleanedText);
        }
        
        return result;
    }
    
    private void sendVoiceMedicineNotificationToContacts(User user, Map<String, Object> medicineInfo, 
                                                          String rawVoiceText, String cleanedText) {
        try {
            List<EmergencyContact> contacts = emergencyContactMapper.selectByUserId(user.getId());
            if (contacts == null || contacts.isEmpty()) {
                System.out.println("用户 " + user.getId() + " 没有设置紧急联系人");
                return;
            }
            
            String senderName = user.getNickname() != null ? user.getNickname() : user.getUsername();
            
            for (EmergencyContact contact : contacts) {
                Long contactUserId = contact.getContactUserId();
                
                if (contactUserId != null) {
                    sendVoiceMedicineMessageToUser(user.getId(), contactUserId, medicineInfo, 
                                                   senderName, rawVoiceText, cleanedText);
                } else {
                    String phone = contact.getContactPhone();
                    if (phone != null && !phone.isEmpty()) {
                        sendMedicineSms(phone, medicineInfo, senderName, "语音录入");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("发送语音药品通知失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void sendVoiceMedicineMessageToUser(Long fromId, Long toId, Map<String, Object> medicineInfo,
                                                 String senderName, String rawVoiceText, String cleanedText) {
        StringBuilder content = new StringBuilder();
        content.append("【语音录入药品通知】\n");
        content.append(senderName).append(" 通过语音录入添加了新药品：\n\n");
        
        if (medicineInfo.get("name") != null) {
            content.append("药品名称：").append(medicineInfo.get("name")).append("\n");
        }
        if (medicineInfo.get("spec") != null) {
            content.append("药品规格：").append(medicineInfo.get("spec")).append("\n");
        }
        if (medicineInfo.get("dosagePerTime") != null && medicineInfo.get("unit") != null) {
            content.append("每次服用：").append(medicineInfo.get("dosagePerTime"))
                   .append(" ").append(medicineInfo.get("unit")).append("\n");
        }
        if (medicineInfo.get("frequency") != null) {
            content.append("每日次数：").append(medicineInfo.get("frequency")).append(" 次\n");
        }
        
        if (rawVoiceText != null && !rawVoiceText.isEmpty()) {
            content.append("\n【原始语音识别】\n");
            if (rawVoiceText.length() > 100) {
                content.append(rawVoiceText.substring(0, 100)).append("...\n");
            } else {
                content.append(rawVoiceText).append("\n");
            }
        }
        
        if (cleanedText != null && !cleanedText.isEmpty()) {
            content.append("\n【优化后文本】\n");
            if (cleanedText.length() > 100) {
                content.append(cleanedText.substring(0, 100)).append("...\n");
            } else {
                content.append(cleanedText).append("\n");
            }
        }
        
        content.append("\n请关注家人的用药情况。");
        
        Message message = new Message();
        message.setFromId(fromId);
        message.setToId(toId);
        message.setContent(content.toString());
        message.setMsgType("text");
        message.setIsRead(0);
        messageMapper.insertMessage(message);
        
        Map<String, Object> notice = new HashMap<>();
        notice.put("fromId", fromId);
        notice.put("toId", toId);
        notice.put("msgType", "TEXT");
        notice.put("content", content.toString());
        chatHandler.sendToUser(toId, notice);
        
        System.out.println("语音药品录入通知已发送: from=" + fromId + ", to=" + toId);
    }

    @PostMapping("/voice/extract")
    public Result extractMedicineFromText(@RequestBody Map<String, String> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        String voiceText = params.get("text");
        if (voiceText == null || voiceText.isEmpty()) {
            return Result.error("请提供语音文本");
        }
        
        return medicineAssistantService.processVoiceInput(user.getId().intValue(), voiceText);
    }

    @GetMapping("/family/{elderId}")
    public Result getFamilyMedicineList(@PathVariable Long elderId, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        List<Map<String, Object>> medicines = medicineAssistantService.getUserMedicines(elderId.intValue());
        return Result.success(medicines);
    }

    @PostMapping("/replenish/{id}")
    public Result replenishMedicine(@PathVariable Integer id, @RequestBody(required = false) Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        Integer packages = params != null && params.get("packages") != null ? 
            ((Number) params.get("packages")).intValue() : 1;
        
        return medicineAssistantService.replenishMedicine(user.getId().intValue(), id, packages);
    }

    @GetMapping("/deleted")
    public Result getDeletedMedicines(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        List<Map<String, Object>> medicines = medicineAssistantService.getDeletedMedicines(user.getId().intValue());
        return Result.success(medicines);
    }

    @GetMapping("/deleted/{elderId}")
    public Result getDeletedMedicinesForFamily(@PathVariable Long elderId, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        List<Map<String, Object>> medicines = medicineAssistantService.getDeletedMedicines(elderId.intValue());
        return Result.success(medicines);
    }

    @PostMapping("/restore/{id}")
    public Result restoreMedicine(@PathVariable Integer id, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        return medicineAssistantService.restoreMedicine(user.getId().intValue(), id);
    }

    @PostMapping("/add-for-family")
    public Result addMedicineForFamily(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        Object familyIdObj = params.get("familyId");
        if (familyIdObj == null) {
            return Result.error("请指定长辈ID");
        }
        
        Integer familyId;
        if (familyIdObj instanceof Number) {
            familyId = ((Number) familyIdObj).intValue();
        } else {
            try {
                familyId = Integer.parseInt(familyIdObj.toString());
            } catch (NumberFormatException e) {
                return Result.error("长辈ID格式错误");
            }
        }
        
        String inputMethod = (String) params.getOrDefault("inputMethod", "子女代录");
        
        Result result = medicineAssistantService.addMedicine(familyId, params);
        
        if (result.getCode() == 200) {
            try {
                String elderName = "长辈";
                User elder = userMapper.getById(familyId.longValue());
                if (elder != null && elder.getNickname() != null) {
                    elderName = elder.getNickname();
                }
                
                String operatorName = user.getNickname() != null ? user.getNickname() : user.getUsername();
                
                StringBuilder content = new StringBuilder();
                content.append("【药品录入通知】\n");
                content.append(operatorName).append(" 为 ").append(elderName).append(" 通过").append(inputMethod).append("添加了新药品：\n\n");
                
                if (params.get("medicineName") != null) {
                    content.append("药品名称：").append(params.get("medicineName")).append("\n");
                } else if (params.get("name") != null) {
                    content.append("药品名称：").append(params.get("name")).append("\n");
                }
                if (params.get("spec") != null) {
                    content.append("药品规格：").append(params.get("spec")).append("\n");
                }
                if (params.get("dosagePerTime") != null && params.get("unit") != null) {
                    content.append("每次服用：").append(params.get("dosagePerTime"))
                           .append(" ").append(params.get("unit")).append("\n");
                }
                if (params.get("frequency") != null) {
                    content.append("每日次数：").append(params.get("frequency")).append(" 次\n");
                }
                
                content.append("\n请关注家人的用药情况。");
                
                Message message = new Message();
                message.setFromId(user.getId());
                message.setToId(familyId.longValue());
                message.setContent(content.toString());
                message.setMsgType("text");
                message.setIsRead(0);
                messageMapper.insertMessage(message);
                
                Map<String, Object> notice = new HashMap<>();
                notice.put("fromId", user.getId());
                notice.put("toId", familyId.longValue());
                notice.put("msgType", "TEXT");
                notice.put("content", content.toString());
                chatHandler.sendToUser(familyId.longValue(), notice);
                
                System.out.println("子女代录药品通知已发送: from=" + user.getId() + ", to=" + familyId);
            } catch (Exception e) {
                System.err.println("发送通知失败: " + e.getMessage());
            }
        }
        
        return result;
    }

    @GetMapping("/anomalies/{familyId}")
    public Result getMedicationAnomalies(@PathVariable Integer familyId, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        return medicineAssistantService.getMedicationAnomalies(familyId);
    }

    @GetMapping("/anomalies/unread-count")
    public Result getUnreadAnomalyCount(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        return medicineAssistantService.getUnreadAnomalyCount(user.getId().intValue());
    }

    @PostMapping("/anomalies/mark-read/{anomalyId}")
    public Result markAnomalyAsRead(@PathVariable Long anomalyId, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        return medicineAssistantService.markAnomalyAsRead(anomalyId);
    }

    @PostMapping("/anomalies/mark-all-read/{familyId}")
    public Result markAllAnomaliesAsRead(@PathVariable Integer familyId, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        return medicineAssistantService.markAllAnomaliesAsRead(familyId);
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MedicineController.class);

    @PostMapping("/fix-reminder-times")
    public Result fixReminderTimes(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        try {
            Integer medicineId = params.get("medicineId") != null ? 
                Integer.parseInt(params.get("medicineId").toString()) : null;
            List<String> reminderTimes = (List<String>) params.get("reminderTimes");
            
            if (medicineId == null) {
                return Result.error("请提供药品ID");
            }
            if (reminderTimes == null || reminderTimes.isEmpty()) {
                return Result.error("请提供提醒时间");
            }
            
            String reminderTimesJson = com.alibaba.fastjson2.JSON.toJSONString(reminderTimes);
            
            jdbcTemplate.update(
                "UPDATE user_medicines SET reminder_times = ?, update_time = NOW() WHERE id = ? AND user_id = ?",
                reminderTimesJson, medicineId, user.getId().intValue()
            );
            
            log.info("[临时修复] 更新药品提醒时间成功, medicineId={}, reminderTimes={}", medicineId, reminderTimes);
            
            return Result.success("更新成功");
        } catch (Exception e) {
            log.error("[临时修复] 更新药品提醒时间失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/sync-all-reminder-times")
    public Result syncAllReminderTimes(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("请先登录");
        
        try {
            int count = 0;
            
            List<Map<String, Object>> medicines = jdbcTemplate.queryForList(
                "SELECT id, medicine_name, custom_name FROM user_medicines WHERE user_id = ? AND is_deleted = 0",
                user.getId().intValue()
            );
            
            for (Map<String, Object> med : medicines) {
                Integer medId = (Integer) med.get("id");
                String medName = (String) (med.get("custom_name") != null ? med.get("custom_name") : med.get("medicine_name"));
                
                List<Map<String, Object>> drafts = jdbcTemplate.queryForList(
                    "SELECT reminder_times FROM medicine_draft WHERE elder_user_id = ? AND (medicine_name = ? OR medicine_name = ?) AND status = 'approved' ORDER BY create_time DESC LIMIT 1",
                    user.getId().intValue(), medName, medName
                );
                
                if (!drafts.isEmpty()) {
                    String reminderTimes = (String) drafts.get(0).get("reminder_times");
                    if (reminderTimes != null && !reminderTimes.isEmpty()) {
                        jdbcTemplate.update(
                            "UPDATE user_medicines SET reminder_times = ?, update_time = NOW() WHERE id = ?",
                            reminderTimes, medId
                        );
                        log.info("[同步提醒时间] 药品 {}({}) 已从草稿同步 reminder_times", medName, medId);
                        count++;
                    }
                }
            }
            
            return Result.success("已同步 " + count + " 个药品的提醒时间");
        } catch (Exception e) {
            log.error("[同步提醒时间] 同步失败", e);
            return Result.error("同步失败: " + e.getMessage());
        }
    }
}
