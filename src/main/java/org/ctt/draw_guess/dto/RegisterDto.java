package org.ctt.draw_guess.dto;
import lombok.Data;

@Data
public class RegisterDto {
    private String username;
    private String password;
    private String nickname;
    private String phone;
    private String code;
}