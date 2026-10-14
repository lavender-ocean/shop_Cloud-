package com.shop.auth.mapper;

import com.shop.auth.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;


@Mapper
public interface SysPermissionMapper {

    //通过userId来查找这个人所具有的所有权限
    List<SysPermission> selectByUserId(@Param("userId") Long userId);
}