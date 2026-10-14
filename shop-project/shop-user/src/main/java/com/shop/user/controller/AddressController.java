package com.shop.user.controller;

import com.shop.user.dto.AddressDTO;
import com.shop.user.entity.UserAddress;
import com.shop.user.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import result.Result;

import java.util.List;

@RestController
@RequestMapping("/api/user/address")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping("/list")
    public Result<List<UserAddress>> list() {
        return Result.success(addressService.list());
    }

    @PostMapping
    public Result<Long> add(@Valid @RequestBody AddressDTO dto) {
        return Result.success(addressService.add(dto));
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody AddressDTO dto) {
        addressService.update(dto);
        return Result.success();
    }

    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        addressService.setDefault(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addressService.delete(id);
        return Result.success();
    }
}