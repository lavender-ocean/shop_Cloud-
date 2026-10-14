package com.shop.user.controller;


import com.shop.api.dto.UserAddressDTO;
import com.shop.api.dto.UserProfileDTO;
import com.shop.user.entity.UserAddress;
import com.shop.user.entity.UserProfile;
import com.shop.user.service.AddressService;
import com.shop.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import result.Result;

@RestController
@Slf4j
@RequestMapping("inner/user")
@RequiredArgsConstructor
public class InnerUserController {

    private final UserService userService;
    private final AddressService addressService;

    @GetMapping("/address/{id}")
    public Result<UserAddressDTO> getAddress(@PathVariable("id") Long id){
        UserAddress userAddress = addressService.getById(id);
        if(userAddress == null){
            return Result.success(null);
        }
        UserAddressDTO userAddressDTO = new UserAddressDTO();
        BeanUtils.copyProperties(userAddress, userAddressDTO);
        return Result.success(userAddressDTO);

    }

    @GetMapping("/profile/{userId}")
    public Result<UserProfileDTO> profile(@PathVariable Long userId) {
        UserProfile profile = userService.getOrCreate(userId);   // 不存在则懒初始化
        UserProfileDTO dto = new UserProfileDTO();
        BeanUtils.copyProperties(profile, dto);
        return Result.success(dto);
    }

}
