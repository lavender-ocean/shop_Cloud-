package com.shop.user.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.shop.user.dto.UserProfileDTO;
import com.shop.user.entity.UserProfile;
import com.shop.user.mapper.UserProfileMapper;
import context.UserContext;
import exception.BizAssert;
import org.springframework.stereotype.Service;
import result.ResultCode;

@Service
public class UserService {

    private final UserProfileMapper userProfileMapper;

    public UserService(UserProfileMapper userProfileMapper) {
        this.userProfileMapper = userProfileMapper;
    }

    /** 获取资料；不存在则懒初始化（注册流程未做，这里兜底） */
    public UserProfile getOrCreate(Long userId) {
        UserProfile profile = userProfileMapper.selectByUserId(userId);
        if (profile == null) {
            profile = new UserProfile();
            profile.setId(IdWorker.getId());
            profile.setUserId(userId);
            profile.setNickname("用户" + userId);
            profile.setGender(0);
            profile.setStatus(1);
            userProfileMapper.insert(profile);
            profile = userProfileMapper.selectByUserId(userId);
        }
        return profile;
    }

    public void update(UserProfileDTO dto) {
        Long userId = UserContext.getUserId();
        BizAssert.notNull(userId, ResultCode.UNAUTHORIZED);
        getOrCreate(userId);   // 确保存在

        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setNickname(dto.getNickname());
        profile.setAvatar(dto.getAvatar());
        profile.setGender(dto.getGender());
        profile.setBirthday(dto.getBirthday());
        profile.setRealName(dto.getRealName());
        userProfileMapper.update(profile);
    }
}