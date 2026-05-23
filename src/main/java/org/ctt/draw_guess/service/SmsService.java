package org.ctt.draw_guess.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);
    private static final String REDIS_KEY_PREFIX = "sms:code:";
    private static final long CODE_EXPIRATION_MINUTES = 5; // 验证码5分钟有效

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 生成并发送验证码 (模拟)
     * @param phoneNumber 手机号
     * @return 生成的验证码
     */
    public String sendVerificationCode(String phoneNumber) {
        // 1. 生成6位随机数字验证码
        String code = String.format("%06d", new Random().nextInt(999999));

        // 2. 将验证码存入 Redis，并设置5分钟过期时间
        String redisKey = REDIS_KEY_PREFIX + phoneNumber;
        redisTemplate.opsForValue().set(redisKey, code, CODE_EXPIRATION_MINUTES, TimeUnit.MINUTES);

        // 3. 模拟发送短信：在控制台打印
        log.info("成功向手机号 {} 发送验证码: {}", phoneNumber, code);

        return code; // 返回验证码，方便前端调试
    }

    /**
     * 校验验证码是否正确
     * @param phoneNumber 手机号
     * @param code 用户输入的验证码
     * @return 是否正确
     */
    public boolean validateCode(String phoneNumber, String code) {
        String redisKey = REDIS_KEY_PREFIX + phoneNumber;
        String storedCode = redisTemplate.opsForValue().get(redisKey);

        if (code.equals(storedCode)) {
            // 验证成功后，立即删除Redis中的验证码，防止重复使用
            redisTemplate.delete(redisKey);
            return true;
        }
        return false;
    }
}