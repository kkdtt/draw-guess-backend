package org.ctt.draw_guess.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage<T> {

    /**
     * 消息类型，比如：
     * ROOM_STATE_UPDATE (房间状态更新)
     * CHAT_MESSAGE (聊天消息)
     * GAME_START (游戏开始)
     * DRAW_DATA (绘画数据)
     * ...
     */
    private String type;

    /**
     * 消息体，存放具体的数据
     * 如果是 ROOM_STATE_UPDATE，T 就是 GameRoom 对象
     * 如果是 CHAT_MESSAGE，T 可以是一个包含发送者和内容的 ChatMessage 对象
     */
    private T data;
}