package com.shop.product.controller;

import com.shop.product.dto.CategoryDTO;
import com.shop.product.dto.CategoryQuery;
import com.shop.product.entity.Category;
import com.shop.product.service.CategoryService;
import com.shop.product.vo.CategoryVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import result.PageResult;
import result.Result;
import security.RequirePermission;

import java.util.List;

@RestController
@RequestMapping("/admin/product/category")
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/page")
    @RequirePermission("product:manage")
    public Result<PageResult<Category>> page(CategoryQuery query) {
        return Result.success(categoryService.page(query));
    }

    @GetMapping("/tree")
    @RequirePermission("product:manage")
    public Result<List<CategoryVO>> tree() {
        return Result.success(categoryService.tree());
    }

    @PostMapping
    @RequirePermission("product:manage")
    public Result<Void> add(@Valid @RequestBody CategoryDTO dto) {
        categoryService.add(dto);
        return Result.success();
    }

    @PutMapping
    @RequirePermission("product:manage")
    public Result<Void> update(@Valid @RequestBody CategoryDTO dto) {
        categoryService.update(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("product:manage")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success();
    }
}