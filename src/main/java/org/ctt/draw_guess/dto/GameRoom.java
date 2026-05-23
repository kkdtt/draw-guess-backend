package org.ctt.draw_guess.dto;

import com.fasterxml.jackson.annotation.JsonIgnore; // ⚠️必须用这个注解防作弊
import lombok.Data;
import org.ctt.draw_guess.entity.GameWord;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
public class GameRoom {
    private String roomNo;          // 房间号 (6位数字)
    private String roomName;        // 房间名
    private Long ownerId;           // ⚠️统一改成 Long：房主的 userId

    private String status;          // ⚠️统一用字符串："WAITING" (备战) 或 "PLAYING" (游戏中)

    // ================= 游戏进行时的核心状态 =================
    private Long currentDrawerId;       // 当前是谁在画？
    private Integer currentTurnIndex;   // 当前是第几个回合？
    private Long roundEndTime;          // 这一回合结束的时间戳 (毫秒)

    private String currentWord;         // 当前的答案是什么？
    private Integer currentWordLength;  // 答案有几个字？
    private String wordHint;            // 答案是什么类别？(比如"食物")

    // 预加载的词库池 (存完整的对象，因为要用到它的类别)
    @JsonIgnore // ⚠️绝对不能把这个发给前端，防止玩家抓包作弊！
    private List<GameWord> wordPool = new ArrayList<>();

    // =======================================================

    // 【核心】使用线程安全的List来存储玩家
    private List<PlayerInfo> players = new CopyOnWriteArrayList<>();

    public GameRoom(String roomNo, String roomName, PlayerInfo ownerInfo) {
        this.roomNo = roomNo;
        this.roomName = roomName;
        this.ownerId = ownerInfo.getUserId();
        this.status = "WAITING"; // 初始状态都是等待中
        this.currentTurnIndex = 0; // 默认第0回合
        this.players.add(ownerInfo);
    }
}