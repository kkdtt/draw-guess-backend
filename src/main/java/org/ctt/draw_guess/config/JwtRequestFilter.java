package org.ctt.draw_guess.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.ctt.draw_guess.entity.SysUser;
import org.ctt.draw_guess.service.UserService;
import org.ctt.draw_guess.util.JwtUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

// config/JwtRequestFilter.java
// 替换 JwtRequestFilter.java 的核心逻辑

@Component
@RequiredArgsConstructor
public class JwtRequestFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    // 【剪辫子】删掉 private final UserService userService; 不需要它了！

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        final String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String jwt = authorizationHeader.substring(7);
            try {
                // 1. 直接解析 Token 拿到所有的 Claims
                Claims claims = jwtUtil.extractAllClaims(jwt);
                String username = claims.getSubject();

                // 2. 检查 Token 是否过期，并且 SecurityContext 目前是空的
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null && !jwtUtil.validateToken(jwt, username).equals(false)) {

                    // 3. 【核心，彻底剪断辫子！】
                    // 直接从 Claims 里把数据掏出来，现场组装一个 SysUser！
                    // 全程没有查数据库，也没有创建 LoginUser 包装类！
                    SysUser sysUser = new SysUser();
                    // 注意：JWT里取出来的数字默认可能是 Integer，强转一下 Long
                    sysUser.setId(claims.get("id", Number.class).longValue());
                    sysUser.setUsername(username);
                    sysUser.setNickname(claims.get("nickname", String.class));
                    sysUser.setAvatar(claims.get("avatar", String.class));

                    // 4. Spring Security 其实并不强制要求传 UserDetails
                    // 我们直接把裸的 sysUser 对象当做 Principal 塞给它！
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                            sysUser, null, Collections.emptyList()); // 权限传空列表

                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
            } catch (Exception e) {
                logger.warn("JWT 验证失败: " + e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }
}