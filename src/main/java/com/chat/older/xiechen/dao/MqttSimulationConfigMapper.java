package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.MqttSimulationConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MqttSimulationConfigMapper {
    int insert(MqttSimulationConfig config);

    int update(MqttSimulationConfig config);

    int delete(@Param("id") Long id);

    MqttSimulationConfig selectById(@Param("id") Long id);

    List<MqttSimulationConfig> selectByUserId(@Param("userId") Long userId);

    int updateAutoSend(@Param("id") Long id, @Param("autoSend") Integer autoSend);

    int updateEnabled(@Param("id") Long id, @Param("isEnabled") Integer isEnabled);
}
