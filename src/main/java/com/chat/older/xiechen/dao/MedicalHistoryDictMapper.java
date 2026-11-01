package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.MedicalHistoryDict;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MedicalHistoryDictMapper {
    @Select("SELECT * FROM medical_history_dict")
    List<MedicalHistoryDict> selectAll();

    @Select("SELECT * FROM medical_history_dict WHERE id = #{id}")
    MedicalHistoryDict selectById(Integer id);
}
