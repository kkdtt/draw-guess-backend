package org.ctt.draw_guess.controller;

import org.ctt.draw_guess.common.Result;
import org.ctt.draw_guess.dto.LoginVo;
import org.ctt.draw_guess.dto.RegisterDto;
import org.ctt.draw_guess.entity.SysUser;
import org.ctt.draw_guess.service.SmsService;
import org.ctt.draw_guess.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ctt.draw_guess.util.JwtUtil;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private SmsService smsService;

    @Autowired
    private JwtUtil jwtUtil;

    // 我们只需要一个登录接口
    @PostMapping("/login")
    public Result<?> login(@RequestBody SysUser user) {
        // @RequestBody 会把前端传来的JSON自动映射到SysUser对象上
        try {
            SysUser loggedInUser = userService.login(user.getUsername(), user.getPassword());
            if (loggedInUser != null) {
                // 【核心改造】登录成功，生成JWT
                final String token = jwtUtil.generateToken(loggedInUser);

                // 返回用户信息和Token
                return Result.success(new LoginVo(loggedInUser, token));
            }
            return Result.error("用户名或密码错误");
        } catch (Exception e) {
            return Result.error(e.getMessage()); // 抛出异常时，返回错误信息
        }
    }

    // TODO: 以后在这里补充 /register 接口

    @PostMapping("/register")
    public Result<SysUser> login(@RequestBody RegisterDto registerDto){

        if(smsService.validateCode(registerDto.getPhone(),registerDto.getCode()))
        {
            SysUser newUser = userService.register(registerDto.getUsername(), registerDto.getPassword(), registerDto.getNickname(), registerDto.getPhone());



            return Result.success(new SysUser());

        }
        return Result.error("验证码错误或过期!请重试");

    }

}