package com.shop.product.controller;

import com.shop.product.entity.Brand;
import com.shop.product.mapper.BrandMapper;
import org.springframework.web.bind.annotation.*;
import result.Result;
import security.RequirePermission;

import java.util.List;

@RestController
@RequestMapping("/admin/product/brand")
public class AdminBrandController {

    private final BrandMapper brandMapper;

    public AdminBrandController(BrandMapper brandMapper) {
        this.brandMapper = brandMapper;
    }

    @GetMapping("/list")
    @RequirePermission("product:manage")
    public Result<List<Brand>> list() {
        return Result.success(brandMapper.selectAll());
    }
}