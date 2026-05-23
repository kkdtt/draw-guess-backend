package org.ctt.draw_guess.util;

// util/JwtUtil.java


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.ctt.draw_guess.entity.SysUser;
import org.springframework.stereotype.Component;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;

@Component
public class JwtUtil {

    // 1. 定义我们原始的字符串秘钥 (要求长度必须大于等于 256 bit，也就是 32 个英文字符，你的长度足够了)
    private final String SECRET_STRING = "YourSuperSecretKeyForDrawAndGuessGame-ChangeThis!";
    // 2. 【核心修复】使用 Keys 工具类，将普通字符串转换成符合 HMAC-SHA 算法规范的 Key 对象
    private final Key SECRET_KEY = Keys.hmacShaKeyFor(SECRET_STRING.getBytes(StandardCharsets.UTF_8));
    private final long EXPIRATION_TIME = 1000 * 60 * 60 * 24 * 7;



    // 从token中提取用户名
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // 从token中提取过期时间
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // 核心提取逻辑
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }


    // 检查token是否过期
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // 替换 JwtUtil.java 中的 generateToken 方法

    // 接收整个 SysUser 对象
    public String generateToken(SysUser user) {
        Map<String, Object> claims = new HashMap<>();
        // 【核心】把基础信息放进 JWT 的 Payload 里！
        claims.put("id", user.getId());
        claims.put("nickname", user.getNickname());
        claims.put("avatar", user.getAvatar());

        return createToken(claims, user.getUsername());
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                // 【核心修复】这里直接传入 Key 对象
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                // 【核心修复】注意参数顺序！第一个参数传 Key 对象，第二个参数传算法类型
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    // 验证token是否有效
    public Boolean validateToken(String token, String username) {
        final String extractedUsername = extractUsername(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }
}