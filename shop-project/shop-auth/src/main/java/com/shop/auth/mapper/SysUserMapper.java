package com.shop.auth.mapper;


import com.shop.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserMapper {

    //根据username来查找user
    SysUser selectByUsername(@Param("username") String username);

    //生成新的user
    int insert(SysUser user);

    //设置最近的登陆时间
    int updateLastLoginTime(@Param("id") Long id);


}
