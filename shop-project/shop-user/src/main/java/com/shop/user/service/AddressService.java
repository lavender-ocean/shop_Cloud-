package com.shop.user.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.shop.user.dto.AddressDTO;
import com.shop.user.entity.UserAddress;
import com.shop.user.mapper.UserAddressMapper;
import context.UserContext;
import exception.BizAssert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import result.ResultCode;

import java.util.List;

@Service
public class AddressService {

    private final UserAddressMapper userAddressMapper;

    public AddressService(UserAddressMapper userAddressMapper) {
        this.userAddressMapper = userAddressMapper;
    }

    public List<UserAddress> list() {
        return userAddressMapper.selectByUserId(requireUserId());
    }

    public UserAddress getOwned(Long id) {
        Long userId = requireUserId();
        UserAddress address = userAddressMapper.selectById(id);
        // 越权防护：只能访问自己的地址
        BizAssert.isTrue(address != null && userId.equals(address.getUserId()),
                ResultCode.ADDRESS_NOT_FOUND);
        return address;
    }

    /** 供内部服务调用（order 取地址）；只按 id 查，调用方自行校验归属 */
    public UserAddress getById(Long id) {
        return userAddressMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(AddressDTO dto) {
        Long userId = requireUserId();
        boolean asDefault = dto.getIsDefault() != null && dto.getIsDefault() == 1;
        if (asDefault) {
            userAddressMapper.clearDefault(userId);
        }

        UserAddress address = new UserAddress();
        address.setId(IdWorker.getId());
        address.setUserId(userId);
        address.setReceiverName(dto.getReceiverName());
        address.setReceiverPhone(dto.getReceiverPhone());
        address.setProvince(dto.getProvince());
        address.setCity(dto.getCity());
        address.setDistrict(dto.getDistrict());
        address.setDetailAddress(dto.getDetailAddress());
        address.setTag(dto.getTag());
        address.setIsDefault(asDefault ? 1 : 0);
        userAddressMapper.insert(address);
        return address.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(AddressDTO dto) {
        BizAssert.notNull(dto.getId(), ResultCode.PARAM_ERROR);
        getOwned(dto.getId());

        if (dto.getIsDefault() != null && dto.getIsDefault() == 1) {
            userAddressMapper.clearDefault(requireUserId());
        }

        UserAddress address = new UserAddress();
        address.setId(dto.getId());
        address.setUserId(requireUserId());
        address.setReceiverName(dto.getReceiverName());
        address.setReceiverPhone(dto.getReceiverPhone());
        address.setProvince(dto.getProvince());
        address.setCity(dto.getCity());
        address.setDistrict(dto.getDistrict());
        address.setDetailAddress(dto.getDetailAddress());
        address.setTag(dto.getTag());
        address.setIsDefault(dto.getIsDefault());
        userAddressMapper.update(address);
    }

    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        Long userId = requireUserId();
        getOwned(id);
        userAddressMapper.clearDefault(userId);
        userAddressMapper.setDefault(id, userId);
    }

    public void delete(Long id) {
        getOwned(id);
        userAddressMapper.deleteById(id, requireUserId());
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        BizAssert.notNull(userId, ResultCode.UNAUTHORIZED);
        return userId;
    }
}