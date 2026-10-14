package security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public class JwtUtils {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtUtils(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, String username, Integer userType, List<String> permissions) {
        return build(userId
                , username
                , userType
                , permissions
                , props.getAccessTokenTtl()
                , "access");
    }

    /** refresh token 不携带权限（体积小；刷新时重新查最新权限） */
    public String createRefreshToken(Long userId, String username, Integer userType) {
        return build(userId
                , username
                , userType
                , null
                , props.getRefreshTokenTtl()
                , "refresh");
    }

    private String build(Long userId, String username, Integer userType,
                         List<String> permissions, Duration ttl, String tokenType) {
        Date now = new Date();
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("userType", userType);
        claims.put("tokenType", tokenType);
        if (permissions != null) {
            claims.put("perms", permissions);      // ★ 权限内嵌（jjwt 会序列化为 JSON 数组）
        }
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl.toMillis()))
                .signWith(key)
                .compact();
    }

















//    //签发accessToken令牌
//    public String createAccessToken(Long userId, String username, Integer userType) {
//        return build(userId, username, userType, props.getAccessTokenTtl(), "access");
//    }
//
//    //签发refreshToken令牌
//    public String createRefreshToken(Long userId, String username, Integer userType) {
//        return build(userId, username, userType, props.getRefreshTokenTtl(), "refresh");
//    }

//    private String build(Long userId, String username, Integer userType, Duration ttl, String tokenType) {
//        Date now = new Date();
//        Map<String, Object> claims = new HashMap<>();
//        claims.put("userId", userId);
//        claims.put("username", username);
//        claims.put("userType", userType);
//        claims.put("tokenType", tokenType);

//        return Jwts.builder()
//                .claims(claims)
//                .subject(String.valueOf(userId))
//                .issuedAt(now)
//                .expiration(new Date(now.getTime() + ttl.toMillis()))
//                .signWith(key)
//                .compact();

//        return Jwts.builder()
//                .id(java.util.UUID.randomUUID().toString())   // jti：黑名单/失效用
//                .claims(claims)
//                .subject(String.valueOf(userId))
//                .issuedAt(now)
//                .expiration(new Date(now.getTime() + ttl.toMillis()))
//                .signWith(key)
//                .compact();
//    }

    //解析jwt令牌
    /** 解析并验签；过期抛 ExpiredJwtException，非法抛 JwtException */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}