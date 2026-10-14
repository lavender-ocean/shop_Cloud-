package com.shop.user.mapper;

import com.shop.user.entity.UserAddress;
import org.apache.ibatis.annotations.Param;

import java.util.List;


//用户地址
public interface UserAddressMapper {
    List<UserAddress> selectByUserId(@Param("userId") Long userId);
    UserAddress selectById(@Param("id") Long id);
    int insert(UserAddress address);
    int update(UserAddress address);
    int clearDefault(@Param("userId") Long userId);
    int setDefault(@Param("id") Long id, @Param("userId") Long userId);
    int deleteById(@Param("id") Long id, @Param("userId") Long userId);
}