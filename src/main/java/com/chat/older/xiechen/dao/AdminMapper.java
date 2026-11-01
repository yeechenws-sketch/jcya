package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.vo.ResidentVO;
import com.chat.older.xiechen.vo.EmergencyContactVO;
import com.chat.older.xiechen.vo.MovedOutResidentVO;
import com.chat.older.xiechen.vo.HealthStatusVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminMapper {
    List<ResidentVO> selectResidentsByCommunityId(@Param("communityId") Integer communityId);

    List<EmergencyContactVO> selectEmergencyContactsByUserId(@Param("userId") Long userId);

    List<MovedOutResidentVO> selectMovedOutResidents(@Param("communityId") Integer communityId);

    MovedOutResidentVO selectMovedOutResidentByRecordId(@Param("recordId") Long recordId);

    HealthStatusVO selectTodayHealthStatus(@Param("userId") Long userId);

    List<String> selectDiseaseNamesByUserId(@Param("userId") Long userId);

    Integer selectCommunityIdByAdminUserId(@Param("adminUserId") Long adminUserId);
}
