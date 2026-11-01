package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.AdminMapper;
import com.chat.older.xiechen.dao.CommunityMapper;
import com.chat.older.xiechen.dao.CommunityNewsMapper;
import com.chat.older.xiechen.dao.MedicalHistoryDictMapper;
import com.chat.older.xiechen.dao.NeighborhoodMapper;
import com.chat.older.xiechen.dao.MessageArchiveMapper;
import com.chat.older.xiechen.dao.UserMapper;
import com.chat.older.xiechen.dao.UserProfileMapper;
import com.chat.older.xiechen.model.Community;
import com.chat.older.xiechen.model.CommunityNews;
import com.chat.older.xiechen.model.MedicalHistoryDict;
import com.chat.older.xiechen.model.Neighborhood;
import com.chat.older.xiechen.model.User;
import com.chat.older.xiechen.model.UserProfile;
import com.chat.older.xiechen.service.CommunityNewsService;
import com.chat.older.xiechen.service.SystemConfigService;
import com.chat.older.xiechen.service.SystemOperationLogService;
import com.chat.older.xiechen.util.MD5Util;
import com.chat.older.xiechen.vo.EmergencyContactVO;
import com.chat.older.xiechen.vo.HealthStatusVO;
import com.chat.older.xiechen.vo.MovedOutResidentVO;
import com.chat.older.xiechen.vo.ResidentVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminMapper adminMapper;
    private final CommunityMapper communityMapper;
    private final NeighborhoodMapper neighborhoodMapper;
    private final MedicalHistoryDictMapper medicalHistoryDictMapper;
    private final MessageArchiveMapper messageArchiveMapper;
    private final CommunityNewsMapper communityNewsMapper;
    private final CommunityNewsService communityNewsService;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final SystemConfigService systemConfigService;
    private final SystemOperationLogService systemOperationLogService;

    public AdminController(AdminMapper adminMapper, CommunityMapper communityMapper,
                          NeighborhoodMapper neighborhoodMapper, MedicalHistoryDictMapper medicalHistoryDictMapper,
                          MessageArchiveMapper messageArchiveMapper, CommunityNewsMapper communityNewsMapper,
                          CommunityNewsService communityNewsService, UserMapper userMapper,
                          UserProfileMapper userProfileMapper, SystemConfigService systemConfigService,
                          SystemOperationLogService systemOperationLogService) {
        this.adminMapper = adminMapper;
        this.communityMapper = communityMapper;
        this.neighborhoodMapper = neighborhoodMapper;
        this.medicalHistoryDictMapper = medicalHistoryDictMapper;
        this.messageArchiveMapper = messageArchiveMapper;
        this.communityNewsMapper = communityNewsMapper;
        this.communityNewsService = communityNewsService;
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.systemConfigService = systemConfigService;
        this.systemOperationLogService = systemOperationLogService;
    }

    @GetMapping("/community/info")
    public Result<Map<String, Object>> getCommunityInfo(HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }
        
        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }
        
        Community community = communityMapper.selectById(communityId);
        List<Neighborhood> neighborhoods = neighborhoodMapper.selectByCommunityId(communityId);
        
        Map<String, Object> data = new HashMap<>();
        data.put("communityId", communityId);
        data.put("communityName", community != null ? community.getCommunityName() : "");
        data.put("neighborhoods", neighborhoods.stream()
            .map(n -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", n.getId());
                map.put("name", n.getNeighborhoodName());
                return map;
            })
            .collect(Collectors.toList()));
        
        return Result.success(data);
    }

    @GetMapping("/resident/list")
    public Result<Map<String, Object>> getResidentList(
            @RequestParam(required = false) Integer neighborhoodId,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String historyIds,
            @RequestParam(required = false, defaultValue = "moveInDesc") String sortType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpSession session) {
        
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }
        
        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }
        
        List<ResidentVO> residents = adminMapper.selectResidentsByCommunityId(communityId);
        
        List<Integer> historyIdList = null;
        if (historyIds != null && !historyIds.isEmpty()) {
            historyIdList = Arrays.stream(historyIds.split(","))
                .map(Integer::parseInt)
                .collect(Collectors.toList());
        }
        
        final List<Integer> finalHistoryIdList = historyIdList;
        List<ResidentVO> filtered = residents.stream()
            .filter(r -> neighborhoodId == null || neighborhoodId.equals(r.getNeighborhoodId()))
            .filter(r -> gender == null || gender.isEmpty() || gender.equals(r.getGender()))
            .filter(r -> keyword == null || keyword.isEmpty() || 
                        (r.getRealName() != null && r.getRealName().contains(keyword)))
            .filter(r -> {
                if (finalHistoryIdList == null || finalHistoryIdList.isEmpty()) return true;
                List<String> diseases = adminMapper.selectDiseaseNamesByUserId(r.getUserId());
                r.setHistoryList(diseases);
                return diseases.stream().anyMatch(d -> finalHistoryIdList.contains(getDiseaseIdByName(d)));
            })
            .collect(Collectors.toList());
        
        for (ResidentVO r : filtered) {
            if (r.getHistoryList() == null) {
                r.setHistoryList(adminMapper.selectDiseaseNamesByUserId(r.getUserId()));
            }
            HealthStatusVO healthStatus = adminMapper.selectTodayHealthStatus(r.getUserId());
            if (healthStatus != null && healthStatus.getLevel() != null) {
                String tooltipText = buildHealthTooltip(healthStatus);
                healthStatus.setTooltip(tooltipText);
            } else {
                healthStatus = new HealthStatusVO();
                healthStatus.setLevel(0);
                healthStatus.setTooltip("今日无预警");
            }
            r.setHealthStatus(healthStatus);
        }
        
        sortResidents(filtered, sortType);
        
        int total = filtered.size();
        int totalPages = (int) Math.ceil((double) total / pageSize);
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, total);
        List<ResidentVO> pagedList = start < total ? filtered.subList(start, end) : new ArrayList<>();
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", pagedList);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("pages", totalPages);
        
        return Result.success(result);
    }

    @GetMapping("/resident/emergency-contacts")
    public Result<List<EmergencyContactVO>> getEmergencyContacts(@RequestParam Long userId, HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }
        
        List<EmergencyContactVO> contacts = adminMapper.selectEmergencyContactsByUserId(userId);
        return Result.success(contacts);
    }

    @GetMapping("/resident/moved-out")
    public Result<Map<String, Object>> getMovedOutResidents(
            @RequestParam(required = false) String keyword,
            HttpSession session) {
        
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }
        
        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }
        
        List<MovedOutResidentVO> movedOut = adminMapper.selectMovedOutResidents(communityId);
        
        Map<Long, MovedOutResidentVO> latestMap = new LinkedHashMap<>();
        for (MovedOutResidentVO r : movedOut) {
            if (!latestMap.containsKey(r.getUserId())) {
                latestMap.put(r.getUserId(), r);
            }
        }
        
        List<MovedOutResidentVO> uniqueList = new ArrayList<>(latestMap.values());
        
        if (keyword != null && !keyword.isEmpty()) {
            uniqueList = uniqueList.stream()
                .filter(r -> r.getRealName() != null && r.getRealName().contains(keyword))
                .collect(Collectors.toList());
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", uniqueList);
        result.put("total", uniqueList.size());
        
        return Result.success(result);
    }

    @GetMapping("/medical-dict")
    public Result<List<Map<String, Object>>> getMedicalDict() {
        List<MedicalHistoryDict> dict = medicalHistoryDictMapper.selectAll();
        List<Map<String, Object>> result = dict.stream()
            .map(d -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", d.getId());
                map.put("name", d.getDiseaseName());
                return map;
            })
            .collect(Collectors.toList());
        
        Map<String, Object> other = new HashMap<>();
        other.put("id", -1);
        other.put("name", "其它");
        result.add(other);
        
        return Result.success(result);
    }

    @GetMapping("/archive/info")
    public Result<Map<String, Object>> getArchiveInfo(@RequestParam Long recordId, HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }
        
        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }
        
        MovedOutResidentVO resident = adminMapper.selectMovedOutResidentByRecordId(recordId);
        if (resident == null) {
            return Result.error("未找到该搬离记录");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("userId", resident.getUserId());
        result.put("realName", resident.getRealName());
        result.put("gender", resident.getGender());
        result.put("age", resident.getAge());
        result.put("moveInTime", resident.getMoveInTime());
        result.put("moveOutTime", resident.getMoveOutTime());
        
        return Result.success(result);
    }

    @GetMapping("/archive/messages")
    public Result<List<Map<String, Object>>> getArchiveMessages(@RequestParam Long userId, HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }
        
        List<Map<String, Object>> messages = messageArchiveMapper.getArchiveMessagesByUserIdAndOtherId(userId, currUser.getId());
        
        return Result.success(messages);
    }

    private Integer getDiseaseIdByName(String name) {
        List<MedicalHistoryDict> dict = medicalHistoryDictMapper.selectAll();
        for (MedicalHistoryDict d : dict) {
            if (d.getDiseaseName().equals(name)) {
                return d.getId();
            }
        }
        return -1;
    }

    private String buildHealthTooltip(HealthStatusVO status) {
        if (status.getLevel() == null || status.getLevel() == 0) {
            return "今日无预警";
        }
        String levelText;
        switch (status.getLevel()) {
            case 1: levelText = "一级预警"; break;
            case 2: levelText = "二级预警"; break;
            case 3: levelText = "三级预警(严重)"; break;
            default: levelText = "正常"; break;
        }
        String types = status.getAlertTypes() != null ? status.getAlertTypes() : "";
        String typeText = types.replace("HR", "心率")
            .replace("BP", "血压")
            .replace("BO", "血氧")
            .replace("BG", "血糖");
        return levelText + " - " + typeText;
    }

    private void sortResidents(List<ResidentVO> list, String sortType) {
        switch (sortType) {
            case "nameAsc":
                list.sort((a, b) -> {
                    String nameA = a.getRealName() != null ? a.getRealName() : "";
                    String nameB = b.getRealName() != null ? b.getRealName() : "";
                    return nameA.compareTo(nameB);
                });
                break;
            case "nameDesc":
                list.sort((a, b) -> {
                    String nameA = a.getRealName() != null ? a.getRealName() : "";
                    String nameB = b.getRealName() != null ? b.getRealName() : "";
                    return nameB.compareTo(nameA);
                });
                break;
            case "ageAsc":
                list.sort((a, b) -> {
                    int ageA = a.getAge() != null ? a.getAge() : 0;
                    int ageB = b.getAge() != null ? b.getAge() : 0;
                    return Integer.compare(ageA, ageB);
                });
                break;
            case "ageDesc":
                list.sort((a, b) -> {
                    int ageA = a.getAge() != null ? a.getAge() : 0;
                    int ageB = b.getAge() != null ? b.getAge() : 0;
                    return Integer.compare(ageB, ageA);
                });
                break;
            case "moveInAsc":
                list.sort((a, b) -> {
                    String timeA = a.getMoveInTime() != null ? a.getMoveInTime() : "";
                    String timeB = b.getMoveInTime() != null ? b.getMoveInTime() : "";
                    return timeA.compareTo(timeB);
                });
                break;
            case "moveInDesc":
                list.sort((a, b) -> {
                    String timeA = a.getMoveInTime() != null ? a.getMoveInTime() : "";
                    String timeB = b.getMoveInTime() != null ? b.getMoveInTime() : "";
                    return timeB.compareTo(timeA);
                });
                break;
            case "healthDesc":
                list.sort((a, b) -> {
                    int levelA = a.getHealthStatus() != null && a.getHealthStatus().getLevel() != null 
                        ? a.getHealthStatus().getLevel() : 0;
                    int levelB = b.getHealthStatus() != null && b.getHealthStatus().getLevel() != null 
                        ? b.getHealthStatus().getLevel() : 0;
                    return Integer.compare(levelB, levelA);
                });
                break;
            default:
                list.sort((a, b) -> {
                    String timeA = a.getMoveInTime() != null ? a.getMoveInTime() : "";
                    String timeB = b.getMoveInTime() != null ? b.getMoveInTime() : "";
                    return timeB.compareTo(timeA);
                });
                break;
        }
    }


    @GetMapping("/news/list")
    public Result<Map<String, Object>> getNewsList(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpSession session) {
        
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }
        
        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }
        
        int offset = (page - 1) * pageSize;
        List<CommunityNews> list = communityNewsMapper.getAdminListByCommunityId(
                communityId, status, category, keyword, pageSize, offset);
        int total = communityNewsMapper.countAdminListByCommunityId(
                communityId, status, category, keyword);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("pages", (int) Math.ceil((double) total / pageSize));
        
        return Result.success(result);
    }

    @GetMapping("/news/detail/{id}")
    public Result<CommunityNews> getNewsDetail(@PathVariable Long id, HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }
        
        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }
        
        CommunityNews news = communityNewsMapper.getByIdForAdmin(id);
        if (news == null) {
            return Result.error("新闻不存在");
        }
        
        if (!news.getCommunityId().equals(communityId)) {
            return Result.error("无权访问该新闻");
        }
        
        return Result.success(news);
    }

    @PostMapping("/news/create")
    public Result<Map<String, Object>> createNews(@RequestBody Map<String, Object> params, HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }
        
        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }
        
        params.put("communityId", communityId);
        params.put("publisherId", currUser.getId());
        
        Map<String, Object> result = communityNewsService.createNews(params, session);
        
        if ((Integer) result.get("code") == 200) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            return Result.success(data);
        } else {
            return Result.error((String) result.get("msg"));
        }
    }

    @PostMapping("/news/update/{id}")
    public Result<Map<String, Object>> updateNews(
            @PathVariable Long id, 
            @RequestBody Map<String, Object> params, 
            HttpSession session,
            HttpServletRequest request) {
        
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }
        
        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }
        
        CommunityNews existing = communityNewsMapper.getByIdForAdmin(id);
        if (existing == null) {
            return Result.error("新闻不存在");
        }
        
        if (!existing.getCommunityId().equals(communityId)) {
            return Result.error("无权修改该新闻");
        }
        
        Map<String, Object> result = communityNewsService.updateNewsWithStatusChange(id, params, session, request);
        
        if ((Integer) result.get("code") == 200) {
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            return Result.success(data);
        } else {
            return Result.error((String) result.get("msg"));
        }
    }

    @PostMapping("/news/publish/{id}")
    public Result<String> publishNews(@PathVariable Long id, HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }
        
        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }
        
        CommunityNews existing = communityNewsMapper.getByIdForAdmin(id);
        if (existing == null) {
            return Result.error("新闻不存在");
        }
        
        if (!existing.getCommunityId().equals(communityId)) {
            return Result.error("无权操作该新闻");
        }
        
        communityNewsMapper.publishNow(id);
        
        return Result.success("发布成功");
    }

    @PostMapping("/news/delete/{id}")
    public Result<String> deleteNews(@PathVariable Long id, HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }
        
        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }
        
        CommunityNews existing = communityNewsMapper.getByIdForAdmin(id);
        if (existing == null) {
            return Result.error("新闻不存在");
        }
        
        if (!existing.getCommunityId().equals(communityId)) {
            return Result.error("无权删除该新闻");
        }
        
        Map<String, Object> result = communityNewsService.deleteNews(id, session);
        
        if ((Integer) result.get("code") == 200) {
            return Result.success((String) result.get("msg"));
        } else {
            return Result.error((String) result.get("msg"));
        }
    }

    @GetMapping("/news/stats")
    public Result<Map<String, Object>> getNewsStats(HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }
        
        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }
        
        int total = communityNewsMapper.countAdminListByCommunityId(communityId, null, null, null);
        int published = communityNewsMapper.countAdminListByCommunityId(communityId, 1, null, null);
        int draft = communityNewsMapper.countAdminListByCommunityId(communityId, 0, null, null);
        int scheduled = communityNewsMapper.countAdminListByCommunityId(communityId, 2, null, null);
        
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("published", published);
        result.put("draft", draft);
        result.put("scheduled", scheduled);
        
        return Result.success(result);
    }


    @GetMapping("/profile")
    public Result<Map<String, Object>> getAdminProfile(HttpSession session) {
        try {
            User currUser = (User) session.getAttribute("currUser");
            if (currUser == null) {
                return Result.error("未登录");
            }
            
            Long userId = currUser.getId();
            if (userId == null) {
                return Result.error("用户ID为空");
            }
            
            User user = userMapper.getById(userId);
            if (user == null) {
                return Result.error("用户不存在");
            }
            
            UserProfile profile = userProfileMapper.selectById(userId);
            
            Integer communityId = adminMapper.selectCommunityIdByAdminUserId(userId);
            Community community = null;
            if (communityId != null) {
                community = communityMapper.selectById(communityId);
            }
            
            Map<String, Object> result = new HashMap<>();
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("nickname", user.getNickname() != null ? user.getNickname() : "");
            userInfo.put("realName", profile != null && profile.getRealName() != null ? profile.getRealName() : "");
            userInfo.put("phone", profile != null && profile.getUserPhone() != null ? profile.getUserPhone() : "");
            userInfo.put("avatar", user.getAvatar() != null ? user.getAvatar() : "");
            result.put("user", userInfo);
            
            Map<String, Object> communityInfo = new HashMap<>();
            if (community != null) {
                communityInfo.put("name", community.getCommunityName() != null ? community.getCommunityName() : "");
                communityInfo.put("phone", community.getAdminPhone() != null ? community.getAdminPhone() : "");
            }
            result.put("community", communityInfo);
            
            return Result.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("获取信息失败: " + e.getMessage());
        }
    }

    @PostMapping("/profile/update")
    public Result<String> updateAdminProfile(@RequestBody Map<String, Object> params, HttpSession session) {
        try {
            User currUser = (User) session.getAttribute("currUser");
            if (currUser == null) {
                return Result.error("未登录");
            }
            
            Long userId = currUser.getId();
            if (userId == null) {
                return Result.error("用户ID为空");
            }
            
            String nickname = (String) params.get("nickname");
            String realName = (String) params.get("realName");
            String phone = (String) params.get("phone");
            String oldPassword = (String) params.get("oldPassword");
            String newPassword = (String) params.get("newPassword");
            
            if (nickname == null || nickname.trim().isEmpty()) {
                return Result.error("昵称不能为空");
            }
            if (realName == null || realName.trim().isEmpty()) {
                return Result.error("真实姓名不能为空");
            }
            
            User user = userMapper.getById(userId);
            if (user == null) {
                return Result.error("用户不存在");
            }
            
            user.setNickname(nickname.trim());
            userMapper.update(user);
            
            UserProfile profile = userProfileMapper.selectById(userId);
            boolean profileExists = profile != null;
            if (!profileExists) {
                profile = new UserProfile();
                profile.setUserId(userId);
                profile.setGender("男");
                profile.setAge(0);
            }
            profile.setRealName(realName.trim());
            if (phone != null) {
                profile.setUserPhone(phone.trim());
            }
            
            if (profileExists) {
                userProfileMapper.update(profile);
            } else {
                userProfileMapper.insert(profile);
            }
            
            if (oldPassword != null && !oldPassword.isEmpty() && 
                newPassword != null && !newPassword.isEmpty()) {
                
                String currentPwd = user.getPassword();
                String encryptedOldPassword = MD5Util.encrypt(oldPassword);
                if (currentPwd == null || !currentPwd.equals(encryptedOldPassword)) {
                    return Result.error("当前密码错误");
                }
                if (newPassword.length() < 6 || newPassword.length() > 20) {
                    return Result.error("新密码长度需为6-20位");
                }
                
                user.setPassword(MD5Util.encrypt(newPassword));
                userMapper.updatePassword(user);
            }
            
            return Result.success("保存成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("保存失败: " + e.getMessage());
        }
    }


    @GetMapping("/resume-config")
    public Result<Map<String, Object>> getResumeConfig() {
        Map<String, String> config = systemConfigService.getResumeConfig();
        Map<String, Object> result = new HashMap<>();
        result.put("nickname", config.getOrDefault("nickname", "望山遥"));
        result.put("email", config.getOrDefault("email", "yeechen177@163.com"));
        result.put("avatarUrl", config.getOrDefault("avatarUrl", ""));
        
        return Result.success(result);
    }

    @PostMapping("/resume-config")
    public Result<String> saveResumeConfig(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String nickname = (String) params.get("nickname");
        String avatarUrl = (String) params.get("avatarUrl");
        
        if (nickname == null || nickname.trim().isEmpty()) {
            return Result.error("昵称不能为空");
        }
        
        if (nickname.length() > 20) {
            return Result.error("昵称长度不能超过20个字符");
        }
        
        Map<String, String> config = new HashMap<>();
        config.put("nickname", nickname.trim());
        config.put("avatarUrl", avatarUrl != null ? avatarUrl.trim() : "");
        
        systemConfigService.saveResumeConfig(config);
        
        String operatorIp = request.getRemoteAddr();
        systemOperationLogService.addLog("简历配置", "更新简历配置", "昵称: " + nickname, null, "管理员", operatorIp);
        
        return Result.success("保存成功");
    }


    @GetMapping("/logs")
    public Result<Map<String, Object>> getOperationLogs(
            @RequestParam(required = false) String logType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        
        LocalDate startLocalDate = null;
        LocalDate endLocalDate = null;
        
        if (startDate != null && !startDate.isEmpty()) {
            try {
                startLocalDate = LocalDate.parse(startDate);
            } catch (Exception e) {}
        }
        if (endDate != null && !endDate.isEmpty()) {
            try {
                endLocalDate = LocalDate.parse(endDate);
            } catch (Exception e) {}
        }
        
        Map<String, Object> result = systemOperationLogService.getLogsByPage(
            logType, startLocalDate, endLocalDate, keyword, page, pageSize);
        
        return Result.success(result);
    }

    @GetMapping("/logs/types")
    public Result<List<String>> getLogTypes() {
        List<String> types = systemOperationLogService.getAllLogTypes();
        return Result.success(types);
    }

    @PostMapping("/logs/clear")
    public Result<String> clearLogs() {
        systemOperationLogService.clearAllLogs();
        return Result.success("日志已清空");
    }

    @PostMapping("/logs/add")
    public Result<String> addLog(@RequestBody Map<String, String> params, HttpServletRequest request) {
        String logType = params.get("logType");
        String operation = params.get("operation");
        String detail = params.get("detail");
        
        if (logType == null || logType.isEmpty() || operation == null || operation.isEmpty()) {
            return Result.error("日志类型和操作不能为空");
        }
        
        String operatorIp = request.getRemoteAddr();
        
        systemOperationLogService.addLog(logType, operation, detail, null, "管理员", operatorIp);
        
        return Result.success("日志记录成功");
    }
}
