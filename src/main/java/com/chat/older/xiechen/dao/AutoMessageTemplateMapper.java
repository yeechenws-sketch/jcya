package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.AutoMessageTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AutoMessageTemplateMapper {
    @Select("SELECT * FROM auto_message_template WHERE scene = #{scene} AND is_enabled = 1")
    AutoMessageTemplate findByScene(String scene);

    @Select("SELECT * FROM auto_message_template WHERE is_enabled = 1")
    List<AutoMessageTemplate> findAllEnabled();
}
