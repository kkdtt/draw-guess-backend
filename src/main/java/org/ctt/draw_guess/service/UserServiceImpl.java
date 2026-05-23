package org.ctt.draw_guess.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.ctt.draw_guess.entity.SysUser;
import org.ctt.draw_guess.mapper.SysUserMapper;
import org.ctt.draw_guess.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder; // 注入我们刚刚配置的加密器

    @Override
    public SysUser login(String username, String password) {
        // 1. 根据用户名查询数据库
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        SysUser userInDb = userMapper.selectOne(queryWrapper);

        // 2. 判断用户是否存在
        if (userInDb == null) {
            // 为了安全，不应该明确告诉前端是“用户不存在”还是“密码错误”，可以统一模糊提示
            throw new RuntimeException("用户名或密码错误");
        }

        // 3. 验证密码
        // passwordEncoder.matches(前端传来的明文密码, 数据库查出的加密密码)
        if (!passwordEncoder.matches(password, userInDb.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
//        if (!password.equals(userInDb.getPassword())) {
//            throw new RuntimeException("用户名或密码错误");
//        }

        // 4. 登录成功, 返回用户信息。为了安全，清空密码字段再返回给前端
        userInDb.setPassword(null);
        return userInDb;
    }



    // 在 UserServiceImpl.java 中



    public SysUser register(String username, String password, String nickname, String phone) {
        // ==========================================================
        //  第一步：检查用户名是否已被占用
        // ==========================================================
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);

        // selectCount 比 selectOne 更高效，因为它只查询数量，不返回完整对象
        Long count = userMapper.selectCount(queryWrapper);

        if (count > 0) {
            // 如果 count 大于 0，说明用户名已存在，直接抛出异常
            // 这个异常会被我们后面的“全局异常处理器”捕获，并返回一个友好的提示给前端
            throw new RuntimeException("用户名 '" + username + "' 已被占用！");
        }

        // ==========================================================
        //  第二步：如果用户名可用，再创建新用户并插入数据库
        // ==========================================================
        SysUser newUser = new SysUser();
        newUser.setUsername(username);
        newUser.setPassword(passwordEncoder.encode(password));
        newUser.setNickname(nickname);

        // 把手机号设置进去！
        newUser.setPhone(phone);

        int insertedRows = userMapper.insert(newUser);
        if (insertedRows > 0) {
            return newUser;
        } else {
            // 正常情况下不会发生，但为了健壮性可以加上
            throw new RuntimeException("注册失败，未知错误！");
        }
    }
}