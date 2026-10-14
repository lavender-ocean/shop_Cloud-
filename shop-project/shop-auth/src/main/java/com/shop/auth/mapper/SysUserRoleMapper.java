package com.shop.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


@Mapper
public interface SysUserRoleMapper {

    //给用户分配角色使用
    //建立了  唯一联合索引

    //UNIQUE KEY uk_user_role (user_id,role_id)

    //user_id + role_id 联合唯一，防止同一个用户重复绑定同一个角色。


    int insertIgnore(@Param("id") Long id
            , @Param("userId") Long userId
            , @Param("roleId") Long roleId);
}