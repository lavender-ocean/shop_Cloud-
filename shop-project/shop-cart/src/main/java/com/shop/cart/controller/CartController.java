package com.shop.cart.controller;

import com.shop.cart.dto.AddCartDTO;
import com.shop.cart.dto.UpdateQuantityDTO;
import com.shop.cart.dto.UpdateSelectedDTO;
import com.shop.cart.service.CartService;
import com.shop.cart.vo.CartVO;
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

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/add")
    public Result<Void> add(@Valid @RequestBody AddCartDTO dto) {
        cartService.add(dto);
        return Result.success();
    }

    @GetMapping("/list")
    public Result<CartVO> list() {
        return Result.success(cartService.list());
    }

    @PutMapping("/quantity")
    public Result<Void> updateQuantity(@Valid @RequestBody UpdateQuantityDTO dto) {
        cartService.updateQuantity(dto);
        return Result.success();
    }

    @PutMapping("/selected")
    public Result<Void> updateSelected(@Valid @RequestBody UpdateSelectedDTO dto) {
        cartService.updateSelected(dto);
        return Result.success();
    }

    @DeleteMapping("/{skuId}")
    public Result<Void> remove(@PathVariable Long skuId) {
        cartService.remove(skuId);
        return Result.success();
    }

    @DeleteMapping("/clear")
    public Result<Void> clear() {
        cartService.clear();
        return Result.success();
    }
}