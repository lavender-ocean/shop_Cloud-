package com.shop.user.mapper;

import com.shop.user.entity.UserProfile;
import org.apache.ibatis.annotations.Param;


//用户资料
public interface UserProfileMapper {
    UserProfile selectByUserId(@Param("userId") Long userId);
    int insert(UserProfile profile);
    int update(UserProfile profile);
}