package org.ctt.draw_guess.manager;


import org.ctt.draw_guess.dto.RoomStatus;
import org.ctt.draw_guess.entity.SysUser;
import org.ctt.draw_guess.dto.GameRoom;
import org.ctt.draw_guess.dto.PlayerInfo;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RoomManager {


    public static final float INIT_SCORE=0;
    public static final Boolean INIT_GUESSED=false;

    // 【核心】使用 ConcurrentHashMap 存储所有活跃的房间，Key 是房间号
    private final Map<String, GameRoom> activeRooms = new ConcurrentHashMap<>();
    private final Random random = new Random();
    // ... 其他代码 ...
    // 👇 定义后端的最大玩家数常量 👇
    public static final int MAX_PLAYER_COUNT = 8;

    /**
     * 创建一个新房间
     * @param roomName 房间名称
     * @param creator 创建者
     * @return 创建好的房间对象
     */
    public GameRoom createRoom(String roomName, SysUser creator) {
        String roomNo;

        // 生成一个唯一的6位房间号
        do {
            roomNo = String.format("%06d", random.nextInt(1000000));
        } while (activeRooms.containsKey(roomNo));

        PlayerInfo ownerInfo = new PlayerInfo(creator.getId(),creator.getUsername(), creator.getNickname(), creator.getAvatar(),PlayerInfo.PLAYER_STATUS_UNREADY,INIT_SCORE,INIT_GUESSED);
        GameRoom newRoom = new GameRoom(roomNo, roomName, ownerInfo);

        activeRooms.put(roomNo, newRoom);
        return newRoom;
    }

    public GameRoom getRoom(String roomId)
    {
        return  activeRooms.get(roomId);
    }



    /**
     * 获取“备战大厅”房间列表，支持模糊搜索
     * @param keyword 搜索关键字（房间号 或 房间名）
     */
    public List<GameRoom> searchWaitingRooms(String keyword) {
        // 1. 获取当前所有活跃的房间
        return activeRooms.values().stream()
                // 2. 核心规则：过滤掉已经开局的游戏，只能加入“备战中”的房间！
                .filter(room -> room.getStatus().equals( RoomStatus.WAITING.toString()))
                // 3. 关键字过滤（如果是空搜，就返回所有）
                .filter(room -> {
                    if (keyword == null || keyword.trim().isEmpty()) {
                        return true;
                    }
                    // 支持按房号或房名搜索
                    return room.getRoomNo().contains(keyword) || room.getRoomName().contains(keyword);
                })
                // 4. 打包成列表返回
                .collect(Collectors.toList());
    }
    /**
     * 玩家加入房间（注意并发安全）
     * @param roomNo 房号
     * @param user 玩家
     */
    public synchronized GameRoom joinRoom(String roomNo, SysUser user) {
        // 把当前发起请求的用户，包装成游戏中的“玩家角色”
        PlayerInfo playerInfo = new PlayerInfo();
        playerInfo.setUserId(user.getId());
        playerInfo.setNickname(user.getNickname());
        playerInfo.setAvatar(user.getAvatar());
        playerInfo.setStatus(PlayerInfo.PLAYER_STATUS_UNREADY);
        playerInfo.setScore(INIT_SCORE);
        playerInfo.setHasGuessed(INIT_GUESSED);
        GameRoom room = activeRooms.get(roomNo);

        // 各种严谨的业务校验防线
        if (room == null) {
            throw new RuntimeException("房间不存在或已被解散");
        }
        if (!room.getStatus().equals( RoomStatus.WAITING.toString())) {
            throw new RuntimeException("该房间已经在游戏中，无法加入");
        }
        if (room.getPlayers().size() >= RoomManager.MAX_PLAYER_COUNT) { // 假设最大8人
            throw new RuntimeException("房间已满员");
        }
        // 防止同一个玩家在两台手机上重复加入同一个房间
        boolean exists = room.getPlayers().stream()
                .anyMatch(p -> p.getUserId().equals(playerInfo.getUserId()));

        if (!exists) {
            // 如果不在里面，就加进去！
            room.getPlayers().add(playerInfo);
        }
        return room;
    }



    // 在 RoomManager.java 中新增这个方法

    /**
     * 玩家离开房间（处理业务数据）
     * @return 返回更新后的房间对象。如果房间空了被销毁，则返回 null。
     */


    public GameRoom leaveRoom(String roomNo, String userId) {
        GameRoom room = activeRooms.get(roomNo);
        if (room == null) {
            return null;
        }


        // 1. 从玩家列表里移除这个人
        PlayerInfo playerToRemove = null;
        for (PlayerInfo p : room.getPlayers()) {
            if (p.getUserId().toString().equals(userId)) {
                playerToRemove = p;
                break;
            }
        }
        if (playerToRemove != null) {
            room.getPlayers().remove(playerToRemove);
        }

        // 2. 判断房间是否空了
        if (room.getPlayers().isEmpty()) {
            activeRooms.remove(roomNo);
            log.info("房间 [{}] 因最后一名玩家离开而销毁。", roomNo);
            return null; // 返回 null，通知上层房间已销毁
        }

        // 3. 🌟 核心：权力交接模块！🌟
        // 判断离开的人是不是房主
        if (room.getOwnerId().toString().equals(userId)) {
            // 房主跑路了！
            // 选出新的房主：直接取当前玩家列表里的第一个人
            PlayerInfo newOwner = room.getPlayers().get(0);
            room.setOwnerId(newOwner.getUserId());
            log.info("房主 [{}] 离开，已自动将房主权限移交给玩家 [{}]", userId, newOwner.getUserId());

            // ⚠️【可选】顺便把他设为“未准备”，因为新房主可能需要重新点“开始游戏”
            // 这个细节看你的产品设计，这里先加上
//            newOwner.setIsReady(false);
        }

        // 4. 返回更新后的房间状态
        return room;
    }

    // 在 RoomManager.java 中新增
    public void setRoomPlaying(GameRoom room) {
        if (room == null) return;

        // 1. 设置房间整体状态为 PLAYING
        // (建议也用常量，比如 GameRoom.ROOM_STATUS_PLAYING)
        room.setStatus("PLAYING");

        // 2. 遍历玩家，修改状态并清空上一局的分数
        for (PlayerInfo player : room.getPlayers()) {
            // 只有点了“已准备”的人，才能进入游戏状态（中途加入的 UNREADY 玩家保持原样充当观众）
            if (PlayerInfo.PLAYER_STATUS_READY.equals(player.getStatus())) {
                player.setStatus(PlayerInfo.PLAYER_STATUS_PLAYING);
                player.setScore(0f); // 🌟 新开一局，分数清零！
            }
        }
        log.info("房间 [{}] 状态切为 PLAYING，已准备玩家进入游戏。", room.getRoomNo());
    }

    // 后续我们还会在这里添加 joinRoom, leaveRoom, getRoom 等方法
}