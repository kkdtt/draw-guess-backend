package org.ctt.draw_guess.controller;

import org.ctt.draw_guess.common.Result;
import org.ctt.draw_guess.service.SmsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sms")
public class SmsController {

    @Autowired
    private SmsService smsService;

    @PostMapping("/send")
    public Result<String> sendCode(@RequestParam String phone) {
        // 这里可以加一些手机号格式校验的逻辑
        String code = smsService.sendVerificationCode(phone);
        // 为了方便调试，我们把验证码也返回给前端
        return Result.success(code);
    }
}