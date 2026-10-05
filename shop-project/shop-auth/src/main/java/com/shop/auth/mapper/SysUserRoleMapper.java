package com.shop.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


@Mapper
public interface SysUserRoleMapper {
    int insertIgnore(@Param("id") Long id, @Param("userId") Long userId, @Param("roleId") Long roleId);
}