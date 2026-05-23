package org.ctt.draw_guess.service;

import org.ctt.draw_guess.entity.SysUser;

public interface UserService {
    SysUser login(String username, String password);
    SysUser register(String username, String password,String nickname, String phone);
}