package com.shop.auth.mapper;



import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;


@Mapper
public interface SysRoleMapper {


    @SuppressWarnings("MybatisPlusMapperMethodInspection")
    //ReleCode  就是：
    //ROLE_OPERATOR
    //ROLE_ADMIN
//  查询用户对应的角色编码集合，用于权限控制。
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);


}