package com.chat.older.xiechen.vo;

import com.chat.older.xiechen.model.*;
import lombok.Data;
import java.util.List;

@Data
public class ProfileSettingsVO {
    // 用户基本信息
    private User user;
    // 个人档案信息
    private UserProfile profile;
    // 当前居住社区
    private Community currentCommunity;
    // 社区负责人紧急联系人
    private EmergencyContact staffContact;
    // 自定义紧急联系人
    private List<EmergencyContact> customContacts;
    // 好友列表（用于选择紧急联系人）
    private List<User> friends;
    // 所有社区列表
    private List<Community> communities;
    // 所有小区列表
    private List<Neighborhood> neighborhoods;
    // 病史字典
    private List<MedicalHistoryDict> diseaseDict;
    // 用户已选疾病ID列表
    private List<Integer> selectedDiseases;
}
