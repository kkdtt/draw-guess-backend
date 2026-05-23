package org.ctt.draw_guess.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerInfo {

    // 👇 把魔法字符串定义成公开的静态常量
    public static final String PLAYER_STATUS_UNREADY = "UNREADY";
    public static final String PLAYER_STATUS_READY = "READY";
    public static final String PLAYER_STATUS_PLAYING = "PLAYING";
    public static final String PLAYER_STATUS_VIEWING_RESULT = "VIEWING_RESULT";
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;

    // 玩家状态
    private String status = PLAYER_STATUS_UNREADY;

    // 👇👇👇 新增的游戏进行时状态数据 👇👇👇

    // 玩家当前总分 (使用 Float 是为了支持你说的点赞加 0.5 分)
    private Float score = 0f;

    // 这回合他猜对了吗？(用来防止猜对的人继续刷屏，以及判断是否全员提前猜对)
    private Boolean hasGuessed = false;
}