package com.shop.auth.mapper;



import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;


@Mapper
public interface SysRoleMapper {


    @SuppressWarnings("MybatisPlusMapperMethodInspection")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);


}