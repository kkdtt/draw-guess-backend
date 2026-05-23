package org.ctt.draw_guess.dto;

public enum MessageType {
    // 房间状态更新（玩家进出、房主变更、游戏状态变化）
    ROOM_STATE_UPDATE,
    // 聊天消息（备战和游戏中通用）
    CHAT_MESSAGE,
    // 画板数据（坐标、颜色、清屏等）
    DRAW_ACTION,
    // 游戏控制（开始游戏、回合倒计时、猜对提示等）
    GAME_CONTROL,

    LIKE_ACTION,
    // 错误消息（用于后端主动推异常）
    ERROR
}