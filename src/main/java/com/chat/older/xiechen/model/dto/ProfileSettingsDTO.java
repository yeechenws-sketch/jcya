package com.chat.older.xiechen.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class ProfileSettingsDTO {
    @Data
    public static class UserDTO {
        private String avatar;
        private String nickname;
    }
    private UserDTO user;

    @Data
    public static class PasswordDTO {
        private String oldPassword;
        private String newPassword;
        private String confirmPassword;
    }
    private PasswordDTO password;

    @Data
    public static class ProfileDTO {
        private String realName;
        private String gender;
        private Integer age;
        private Integer communityId;
        private Integer neighborhoodId;
        private String addressDetail;
        private String customHistory;
        private String userPhone;
    }
    private ProfileDTO profile;

    private List<Integer> diseaseIds;

    @Data
    public static class CustomContactDTO {
        private Long contactUserId;
        private String contactPhone;
    }
    private List<CustomContactDTO> customContacts;
}
