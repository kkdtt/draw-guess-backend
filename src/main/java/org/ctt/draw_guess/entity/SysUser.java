package org.ctt.draw_guess.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data// Lombok的神奇注解：自动帮你生成 get/set 方法，代码瞬间清爽！
@TableName("sys_user") // 告诉程序，这个类对应数据库里的 sys_user 表
@Schema(description = "用户信息实体") // 使用 @Schema 替代 @ApiModel
public class SysUser {

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键ID") // 使用 @Schema 替代 @ApiModelProperty
    private Long id;
    @Schema(description = "登录账号")
    private String username;
    private String password;
    private String nickname;
    @Schema(description = "手机号码")
    private String phone;
    @Schema(description = "性别(0-保密, 1-男, 2-女)")
    private Integer gender;
    private String avatar;
    private Integer totalMatches; // 注意：数据库的 total_matches 会自动变成驼峰命名
    private Integer winMatches;
    private Integer score;
    private Date createTime;
    private Date updateTime;
}