package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.MedicineInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MedicineInfoMapper {
    MedicineInfo selectByBarcode(@Param("barcode") String barcode);

    MedicineInfo selectById(@Param("id") Integer id);

    MedicineInfo selectByName(@Param("name") String name);

    MedicineInfo selectByNameLike(@Param("name") String name);
}
