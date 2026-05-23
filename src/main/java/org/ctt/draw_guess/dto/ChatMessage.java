package org.ctt.draw_guess.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private Long senderId;
    private String senderNickname;
    private String content;
    private Long timestamp;
}