package com.chat.older.xiechen.service.impl;

import com.chat.older.xiechen.dao.*;
import com.chat.older.xiechen.model.*;
import com.chat.older.xiechen.model.dto.ProfileSettingsDTO;
import com.chat.older.xiechen.service.ProfileSettingsService;
import com.chat.older.xiechen.service.CommunityChangeService;
import com.chat.older.xiechen.util.MD5Util;
import com.chat.older.xiechen.vo.ProfileSettingsVO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ProfileSettingsServiceImpl implements ProfileSettingsService {

    private static final Logger logger = LoggerFactory.getLogger(ProfileSettingsServiceImpl.class);

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final UserCommunityMapper userCommunityMapper;
    private final CommunityMapper communityMapper;
    private final NeighborhoodMapper neighborhoodMapper;
    private final EmergencyContactMapper emergencyContactMapper;
    private final MedicalHistoryDictMapper medicalHistoryDictMapper;
    private final UserMedicalMappingMapper userMedicalMappingMapper;
    private final CommunityChangeService communityChangeService;

    @Override
    public ProfileSettingsVO getSettings(Long userId) {
        ProfileSettingsVO vo = new ProfileSettingsVO();

        User user = userMapper.getById(userId);
        vo.setUser(user);

        UserProfile profile = userProfileMapper.selectById(userId);
        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(userId);
        }
        vo.setProfile(profile);
        System.out.println("用户档案信息: " + profile);

        UserCommunity userCommunity = userCommunityMapper.findCurrentByUserId(userId);
        System.out.println("当前居住社区记录: " + userCommunity);
        if (userCommunity != null) {
            Community community = communityMapper.selectById(userCommunity.getCommunityId());
            vo.setCurrentCommunity(community);
            System.out.println("当前社区信息: " + community);
            // 如果个人档案中的社区ID与当前居住社区不一致，更新个人档案
            if (!userCommunity.getCommunityId().equals(profile.getCommunityId())) {
                profile.setCommunityId(userCommunity.getCommunityId());
                userProfileMapper.update(profile);
                System.out.println("更新个人档案社区ID为: " + userCommunity.getCommunityId());
            }
        }

        List<EmergencyContact> allContacts = emergencyContactMapper.selectByUserId(userId);
        System.out.println("紧急联系人数量: " + allContacts.size());
        EmergencyContact staffContact = null;
        List<EmergencyContact> customContacts = new ArrayList<>();

        for (EmergencyContact contact : allContacts) {
            if (contact.getIsStaff() == 1) {
                staffContact = contact;
                System.out.println("社区负责人紧急联系人: " + contact.getContactName());
            } else {
                customContacts.add(contact);
            }
        }
        vo.setStaffContact(staffContact);
        vo.setCustomContacts(customContacts);

        List<User> friends = userMapper.getMyFriends(userId);
        vo.setFriends(friends);

        List<Community> communities = communityMapper.selectAll();
        vo.setCommunities(communities);
        System.out.println("社区列表数量: " + communities.size());
        List<Neighborhood> neighborhoods = neighborhoodMapper.selectAll();
        vo.setNeighborhoods(neighborhoods);
        System.out.println("小区列表数量: " + neighborhoods.size());

        vo.setDiseaseDict(medicalHistoryDictMapper.selectAll());
        List<UserMedicalMapping> mappings = userMedicalMappingMapper.selectByUserId(userId);
        List<Integer> selectedDiseases = mappings.stream()
                .map(UserMedicalMapping::getHistoryId)
                .collect(Collectors.toList());
        vo.setSelectedDiseases(selectedDiseases);

        return vo;
    }

    @Override
    public void saveProfile(Long userId, ProfileSettingsDTO dto) {
        logger.info("开始保存个人信息，用户ID: {}", userId);
        logger.info("请求参数: {}", dto);
        
        try {
            logger.info("1. 更新用户基本信息");
            if (dto.getUser() != null) {
                User user = userMapper.getById(userId);
                user.setNickname(dto.getUser().getNickname());
                if (dto.getUser().getAvatar() != null) {
                    user.setAvatar(dto.getUser().getAvatar());
                }
                userMapper.update(user);
                logger.info("用户基本信息更新完成");
            }

            logger.info("2. 修改密码");
            if (dto.getPassword() != null && 
                dto.getPassword().getOldPassword() != null && !dto.getPassword().getOldPassword().trim().isEmpty() &&
                dto.getPassword().getNewPassword() != null && !dto.getPassword().getNewPassword().trim().isEmpty() &&
                dto.getPassword().getConfirmPassword() != null && !dto.getPassword().getConfirmPassword().trim().isEmpty()) {
                changePassword(userId, dto.getPassword());
                logger.info("密码修改完成");
            } else if (dto.getPassword() != null && 
                      (dto.getPassword().getOldPassword() != null || dto.getPassword().getNewPassword() != null || dto.getPassword().getConfirmPassword() != null)) {
                logger.warn("密码字段不完整，跳过密码修改");
            }

            logger.info("3. 更新个人档案");
            updateUserProfile(userId, dto.getProfile());
            logger.info("个人档案更新完成");

            logger.info("4. 处理社区变更");
            if (dto.getProfile() != null) {
                handleCommunityChange(userId, dto.getProfile().getCommunityId(), dto.getProfile().getNeighborhoodId());
                logger.info("社区变更处理完成");
            }

            logger.info("5. 更新病史关联");
            if (dto.getDiseaseIds() != null) {
                updateUserDiseases(userId, dto.getDiseaseIds());
                logger.info("病史关联更新完成");
            }

            logger.info("6. 更新自定义紧急联系人");
            if (dto.getCustomContacts() != null) {
                updateCustomContacts(userId, dto.getCustomContacts());
                logger.info("自定义紧急联系人更新完成");
            }
            
            logger.info("个人信息保存完成");
        } catch (Exception e) {
            logger.error("保存个人信息失败", e);
            throw e;
        }
    }

    private void changePassword(Long userId, ProfileSettingsDTO.PasswordDTO passwordDto) {
        if (passwordDto.getOldPassword() == null || passwordDto.getOldPassword().trim().isEmpty()) {
            throw new RuntimeException("请输入原密码");
        }
        if (passwordDto.getNewPassword() == null || passwordDto.getNewPassword().trim().isEmpty()) {
            throw new RuntimeException("请输入新密码");
        }
        if (passwordDto.getConfirmPassword() == null || passwordDto.getConfirmPassword().trim().isEmpty()) {
            throw new RuntimeException("请确认新密码");
        }
        
        User user = userMapper.getById(userId);
        String encryptedOldPassword = MD5Util.encrypt(passwordDto.getOldPassword());
        if (!user.getPassword().equals(encryptedOldPassword)) {
            throw new RuntimeException("原密码错误");
        }
        if (!passwordDto.getNewPassword().equals(passwordDto.getConfirmPassword())) {
            throw new RuntimeException("两次新密码不一致");
        }
        if (passwordDto.getNewPassword().length() < 6 || passwordDto.getNewPassword().length() > 20) {
            throw new RuntimeException("新密码长度需为6-20位");
        }
        user.setPassword(MD5Util.encrypt(passwordDto.getNewPassword()));
        userMapper.updatePassword(user);
    }

    private void updateUserProfile(Long userId, ProfileSettingsDTO.ProfileDTO profileDto) {
        if (profileDto == null) return;

        UserProfile profile = userProfileMapper.selectById(userId);
        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(userId);
            BeanUtils.copyProperties(profileDto, profile);
            userProfileMapper.insert(profile);
        } else {
            BeanUtils.copyProperties(profileDto, profile);
            userProfileMapper.update(profile);
        }
    }

    private void handleCommunityChange(Long userId, Integer newCommunityId, Integer newNeighborhoodId) {
        logger.info("开始处理社区变更，用户ID: {}, 新社区ID: {}, 新小区ID: {}", userId, newCommunityId, newNeighborhoodId);
        
        try {
            if (newCommunityId == null || newCommunityId <= 0) {
                logger.info("未设置社区ID，跳过社区变更处理（子女端无需设置社区）");
                return;
            }
            
            UserCommunity current = userCommunityMapper.findCurrentByUserId(userId);
            Integer oldCommunityId = (current != null) ? current.getCommunityId() : null;
            logger.info("当前居住社区记录: {}, 旧社区ID: {}", current, oldCommunityId);

            if (oldCommunityId != null && oldCommunityId.equals(newCommunityId)) {
                logger.info("社区未变化，直接返回");
                return;
            }

            if (current != null) {
                current.setStatus(0);
                current.setMoveOutTime(LocalDateTime.now());
                int updateResult = userCommunityMapper.update(current);
                logger.info("更新旧社区状态结果: {}", updateResult);

                if (oldCommunityId != null) {
                    Community oldCommunity = communityMapper.selectById(oldCommunityId);
                    logger.info("旧社区信息: {}", oldCommunity);
                    if (oldCommunity != null) {
                        Long adminUserId = oldCommunity.getAdminUserId();
                        if (adminUserId != null) {
                            try {
                                int deleteResult = emergencyContactMapper.deleteSystemStaffContact(userId, adminUserId);
                                logger.info("删除旧社区紧急联系人结果: {}", deleteResult);
                            } catch (Exception e) {
                                logger.warn("删除旧社区紧急联系人失败，将继续执行后续操作", e);
                            }
                        } else {
                            logger.warn("旧社区负责人ID为null，跳过删除紧急联系人操作");
                        }
                    } else {
                        logger.warn("未找到旧社区信息，跳过删除紧急联系人操作");
                    }
                }
            }

            communityChangeService.handleCommunityChange(userId, oldCommunityId, newCommunityId);

            UserCommunity newRecord = new UserCommunity();
            newRecord.setUserId(userId);
            newRecord.setCommunityId(newCommunityId);
            newRecord.setStatus(1);
            newRecord.setMoveInTime(LocalDateTime.now());
            int insertResult = userCommunityMapper.insert(newRecord);
            logger.info("插入新社区记录结果: {}", insertResult);

            Community newCommunity = communityMapper.selectById(newCommunityId);
            logger.info("新社区信息: {}", newCommunity);
            if (newCommunity != null) {
                Long adminUserId = newCommunity.getAdminUserId();
                String adminPhone = newCommunity.getAdminPhone();
                String communityName = newCommunity.getCommunityName();
                
                logger.info("新社区名称: {}, 负责人ID: {}, 负责人电话: {}", communityName, adminUserId, adminPhone);
                
                if (adminUserId != null) {
                    try {
                        EmergencyContact existingContact = emergencyContactMapper.selectByUserIdAndContactUserId(userId, adminUserId);
                        if (existingContact != null) {
                            existingContact.setContactName(communityName + " 负责人");
                            existingContact.setContactPhone(adminPhone);
                            existingContact.setIsStaff(1);
                            existingContact.setIsSystemAdd(1);
                            existingContact.setPriority(1);
                            existingContact.setUpdateTime(LocalDateTime.now());
                            int updateContactResult = emergencyContactMapper.update(existingContact);
                            logger.info("更新紧急联系人结果: {}", updateContactResult);
                        } else {
                            EmergencyContact ec = new EmergencyContact();
                            ec.setUserId(userId);
                            ec.setContactUserId(adminUserId);
                            ec.setContactName(communityName + " 负责人");
                            ec.setContactPhone(adminPhone);
                            ec.setIsStaff(1);
                            ec.setIsSystemAdd(1);
                            ec.setPriority(1);
                            ec.setUpdateTime(LocalDateTime.now());
                            int insertContactResult = emergencyContactMapper.insert(ec);
                            logger.info("插入紧急联系人结果: {}", insertContactResult);
                        }
                    } catch (Exception e) {
                        logger.error("添加新社区紧急联系人失败", e);
                        throw new RuntimeException("添加新社区紧急联系人失败: " + e.getMessage());
                    }
                } else {
                    logger.warn("新社区负责人ID为null，跳过添加紧急联系人操作");
                }
            } else {
                logger.error("未找到新社区信息，社区ID: {}", newCommunityId);
                throw new RuntimeException("未找到新社区信息");
            }
            
            logger.info("社区变更处理完成");
        } catch (IllegalArgumentException e) {
            logger.error("社区变更参数错误: {}", e.getMessage(), e);
            throw e;
        } catch (RuntimeException e) {
            logger.error("社区变更业务错误: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("社区变更系统错误: {}", e.getMessage(), e);
            throw new RuntimeException("社区变更失败: " + e.getMessage());
        }
    }


    private void updateUserDiseases(Long userId, List<Integer> diseaseIds) {
        userMedicalMappingMapper.deleteByUserId(userId);
        if (diseaseIds != null && !diseaseIds.isEmpty()) {
            for (Integer diseaseId : diseaseIds) {
                UserMedicalMapping mapping = new UserMedicalMapping();
                mapping.setUserId(userId);
                mapping.setHistoryId(diseaseId);
                userMedicalMappingMapper.insert(mapping);
            }
        }
    }

    private void updateCustomContacts(Long userId, List<ProfileSettingsDTO.CustomContactDTO> customContacts) {
        logger.info("开始更新自定义紧急联系人，用户ID: {}, 联系人数量: {}", userId, customContacts != null ? customContacts.size() : 0);
        
        try {
            emergencyContactMapper.deleteCustomContacts(userId);
            logger.info("删除所有自定义联系人完成");

            if (customContacts != null && !customContacts.isEmpty()) {
                for (ProfileSettingsDTO.CustomContactDTO dto : customContacts) {
                    logger.info("处理联系人: {}", dto);

                    if (dto.getContactUserId() == null) {
                        logger.warn("联系人用户ID为null，跳过处理");
                        continue;
                    }
                    
                    User friend = userMapper.getById(dto.getContactUserId());
                    logger.info("好友信息: {}", friend);
                    
                    if (friend != null) {
                        EmergencyContact existingContact = emergencyContactMapper.selectByUserIdAndContactUserId(userId, dto.getContactUserId());
                        logger.info("现有联系人信息: {}", existingContact);
                        
                        if (existingContact != null) {
                            existingContact.setContactName(friend.getNickname());
                            existingContact.setContactPhone(dto.getContactPhone());
                            existingContact.setUpdateTime(LocalDateTime.now());
                            int updateResult = emergencyContactMapper.update(existingContact);
                            logger.info("更新紧急联系人结果: {}", updateResult);
                        } else {
                            EmergencyContact ec = new EmergencyContact();
                            ec.setUserId(userId);
                            ec.setContactUserId(dto.getContactUserId());
                            ec.setContactName(friend.getNickname());
                            ec.setContactPhone(dto.getContactPhone());
                            ec.setIsStaff(0);
                            ec.setIsSystemAdd(0);
                            ec.setPriority(0);
                            ec.setUpdateTime(LocalDateTime.now());
                            int insertResult = emergencyContactMapper.insert(ec);
                            logger.info("插入紧急联系人结果: {}", insertResult);
                        }
                    } else {
                        logger.warn("未找到好友信息，用户ID: {}", dto.getContactUserId());
                    }
                }
            }
            
            logger.info("更新自定义紧急联系人完成");
        } catch (Exception e) {
            logger.error("更新自定义紧急联系人失败", e);
            throw new RuntimeException("更新自定义紧急联系人失败: " + e.getMessage());
        }
    }
}
