package org.ctt.draw_guess.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.ctt.draw_guess.entity.SysUser;

// dto/LoginVo.java (Vo = View Object)
@Data
@AllArgsConstructor // 方便创建实例
public class LoginVo {
    private SysUser userInfo;
    private String token;
}