package com.shop.auth.mapper;


import com.shop.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserMapper {

    SysUser selectByUsername(@Param("username") String username);

    int insert(SysUser user);

    int updateLastLoginTime(@Param("id") Long id);


}
