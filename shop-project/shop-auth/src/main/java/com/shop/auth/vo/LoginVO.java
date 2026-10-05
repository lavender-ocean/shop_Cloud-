package com.shop.auth.vo;

public record LoginVO(String accessToken, String refreshToken,
                      Long userId, String username, Integer userType) {
}