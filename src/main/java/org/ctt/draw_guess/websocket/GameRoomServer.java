package org.ctt.draw_guess.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.ctt.draw_guess.dto.*;

import org.ctt.draw_guess.entity.GameWord;
import org.ctt.draw_guess.manager.RoomManager;
import org.ctt.draw_guess.mapper.GameWordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;

/**
 * 游戏房间的 WebSocket 服务器
 * 这里的 url 路径 /ws/game/{roomNo}/{userId} 极其重要：
 * 当手机连接时，必须带上他要进哪个房间(roomNo)，以及他是谁(userId)
 */
@Slf4j
@ServerEndpoint("/ws/game/{roomNo}/{userId}")
@Component
public class GameRoomServer {
    // ⚠️ 极其硬核的架构设计：并发安全的全局游戏大厅！
    // 外层 Map 的 Key 是房间号 (roomNo)
    // 内层 Set 里面装着这个房间里所有的玩家的连接通道 (Session)
    // 之前我们说 Spring 的 Controller 是无状态单例不能存数据，
    // 但是 WebSocket 类是【多实例】的（每个连进来的手机都会创建一个新对象）
    // 所以这里必须加 `static` 关键字，让所有对象共享这个大厅数据！
    private static final ConcurrentHashMap<String, CopyOnWriteArraySet<Session>> ROOMS = new ConcurrentHashMap<>();
    // 当前玩家专属的管道（类似于刚才说的“电话线”）
    private Session session;
    // 当前玩家所在的房间号
    private String roomNo;
    // 当前玩家的 ID
    private String userId;

    // 【关键改造1】: 引入 RoomManager，让通信官能找到裁判
    private static RoomManager roomManager;
    private static ObjectMapper objectMapper; // 【新增】JSON转换工具




    private static GameWordMapper gameWordMapper;
    @Autowired
    public void setGameWordMapper(GameWordMapper gameWordMapper) {
        GameRoomServer.gameWordMapper = gameWordMapper;
    }

    // 【关键改造2】: 提供一个静态方法，让 Spring 容器能把 roomManager 实例注入进来
    public static void setRoomManager(RoomManager manager) {
        GameRoomServer.roomManager = manager;
    }
    // 【新增】注入 ObjectMapper
    public static void setObjectMapper(ObjectMapper mapper) {
        GameRoomServer.objectMapper = mapper;
    }



    // 游戏核心定时器引擎 (支持多线程并发处理多个房间)
    private static final ScheduledExecutorService gameScheduler = Executors.newScheduledThreadPool(10);

    // 记录每个房间当前的定时任务，方便“所有人提前猜对”时取消定时
    private static final ConcurrentHashMap<String, ScheduledFuture<?>> roomTasks = new ConcurrentHashMap<>();

    /**
     * 第一步：当有手机连接成功（TCP 三次握手完成）时触发
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("roomNo") String roomNo, @PathParam("userId") String userId) {
        this.session = session;
        this.roomNo = roomNo;
        this.userId = userId;

        ROOMS.putIfAbsent(roomNo, new CopyOnWriteArraySet<>());
        ROOMS.get(roomNo).add(session);
        log.info("玩家 [{}] 的WebSocket连接已建立，房间 [{}]", userId, roomNo);

        // 拿到最新的权威房间数据（此时通过 HTTP join，该玩家已经在里面了）
        GameRoom currentRoomState = roomManager.getRoom(roomNo);
        if (currentRoomState == null) {
            try { session.close(); } catch (IOException e) {}
            return;
        }

        try {
            // ==========================================
            // 动作 1：广播最新的房间状态 (ROOM_STATE_UPDATE)
            // 作用：让所有人(包括刚进来的自己)刷新左上角的人数和头像列表
            // ==========================================
            WebSocketMessage<GameRoom> stateMsg = new WebSocketMessage<>("ROOM_STATE_UPDATE", currentRoomState);
            broadcastToRoom(roomNo, objectMapper.writeValueAsString(stateMsg));

            // ==========================================
            // 动作 2：广播一条“系统公告” (CHAT_MESSAGE)
            // 作用：在聊天室里提示“某某加入了房间”
            // ==========================================
            // 小技巧：从 currentRoomState 中找出当前连进来的玩家的真实昵称
            String nickname = "神秘玩家";
            for (PlayerInfo p : currentRoomState.getPlayers()) {
                if (p.getUserId().toString().equals(userId)) {
                    nickname = p.getNickname();
                    break;
                }
            }

            // 架构师规范：用 senderId = 0 表示这是“系统发出的消息”，前端可以用不同的颜色(比如灰色)显示
            ChatMessage systemChat = new ChatMessage(
                    0L,
                    "系统公告",
                    "欢迎玩家 [" + nickname + "] 加入了画室！",
                    System.currentTimeMillis()
            );
            WebSocketMessage<ChatMessage> chatWsMsg = new WebSocketMessage<>("CHAT_MESSAGE", systemChat);
            broadcastToRoom(roomNo, objectMapper.writeValueAsString(chatWsMsg));

        } catch (Exception e) {
            log.error("处理玩家加入逻辑时发生异常", e);
        }
    }
    /**
     * 第二步：最核心的方法！当收到某个手机发来的画画坐标时触发
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String typeStr = root.get("type").asText(); // 从网络收到的字符串
            // 👇 核心魔法：把字符串转成枚举
            MessageType type;
            try {
                type = MessageType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                // 如果客户端发了一个不存在的 type，在这里直接拦截！
                log.error("非法消息类型: {}", typeStr);
                sendError(session, "消息类型错误");
                return;
            }
            // 现在你可以用枚举的 switch 了，IDE 会帮你自动补全，如果你漏了分支还会警告你！
            switch (type) {
                case ROOM_STATE_UPDATE:
                    // 这个不应该由客户端发送，只由服务器主动广播
                    break;
                case CHAT_MESSAGE:
                    handleChatMessage(root.get("data"), session,userId);
                    break;
                case DRAW_ACTION:
                    handleDrawAction(root.get("data"), session);
                    break;
                case GAME_CONTROL:
                    handleGameControl(root.get("data"), session);
                    break;
                case LIKE_ACTION:
                    handleLikeAction();
                    break;
                case ERROR:
                    // 客户端不应该发 ERROR，这是服务器专用的
                    break;
                // 不需要 default，因为枚举的所有值都已经在上面了！
            }
        } catch (Exception e) {
            log.error("解析消息失败", e);
        }
    }

    private void sendError(Session session, String errReason) {

        broadcastToRoom(roomNo,errReason);

    }

    private void handleGameControl(JsonNode data, Session senderSession) {
        try {

            // 假设你的 GameControl 类有 command 和 data 两个字段
            String command =data.get("command").asText();
            String commandData =data.has("data") ?data.get("data").asText() : "";

            // 拿到裁判手里的权威房间数据
            GameRoom room = roomManager.getRoom(roomNo);
            if (room == null) return;

            if ("TOGGLE_READY".equals(command)) {
                // ==========================================
                // 分支 1：玩家切换准备状态
                // ==========================================
                boolean targetState = Boolean.parseBoolean(commandData);

                // 找到发指令的这个人，修改他的状态
                for (PlayerInfo p : room.getPlayers()) {
                    if (p.getUserId().toString().equals(userId)) {
                       if(targetState){
                           p.setStatus(PlayerInfo.PLAYER_STATUS_READY);
                       }else{
                           p.setStatus(PlayerInfo.PLAYER_STATUS_UNREADY);
                       }
                    }
                }

                // 🌟 核心：状态更新完了，给所有人下发最新房间数据！
                WebSocketMessage<GameRoom> stateMsg = new WebSocketMessage<>("ROOM_STATE_UPDATE", room);
                broadcastToRoom(roomNo, objectMapper.writeValueAsString(stateMsg));

            }
            else if ("START_GAME".equals(command)) {
                // ==========================================
                // 分支 2：房主点击了开始游戏
                // ==========================================
                // 安全校验：确认发指令的人是不是房主
                if (!room.getOwnerId().toString().equals(userId)) {
                    return; // 伪造指令，直接无视
                }


                // 更改房间状态为游戏进行中
                roomManager.setRoomPlaying(room);

                // TODO: 未来这里还要做：
                // 1. 从词库抽一个词设置给 room.setCurrentWord()
                // 2. 选第一个人为画手 room.setDrawerId()
                // 3. 设置倒计时结束时间
                startNewRound(room);


            }else if("BACK_ROOM".equals(command))
            {

                //分支3


                room.setStatus("WAITING");
                for (PlayerInfo p : room.getPlayers()) {
                    if (p.getUserId().toString().equals(userId)) {
                      p.setStatus(PlayerInfo.PLAYER_STATUS_UNREADY);
                      p.setScore((float) 0);
                      p.setHasGuessed(false);
                        break; // 👈 找到了就退出循环，省点 CPU
                    }
                }

                // 🌟 核心：状态更新完了，给回到房间玩家房间数据
                WebSocketMessage<GameRoom> stateMsg = new WebSocketMessage<>("ROOM_STATE_UPDATE", room);
                        if (session.isOpen()) { // 确保连接是打开的
                            try {
                                session.getBasicRemote().sendText(objectMapper.writeValueAsString(stateMsg));
                            } catch (IOException e) {
                                // 记录日志，而不是直接打印堆栈
                                log.error("消息给房间 {} 的玩家 {} 失败: {}", roomNo, getUserIdFromSession(session), e.getMessage());
                            }
                        }




            }

        } catch (Exception e) {
            log.error("处理 GAME_CONTROL 消息失败", e);
        }
    }

    private void handleDrawAction(JsonNode data, Session session) {
        // 1. ⚠️ 必须用 asText() 剥离双引号，获取纯净的逗号分隔字符串
        String actionStr = data.asText();

        CopyOnWriteArraySet<Session> roomPlayers = ROOMS.get(roomNo);
        if (roomPlayers != null) {
            for (Session playerSession : roomPlayers) {
                // 只发给别人，不发给自己
                if (playerSession != session && playerSession.isOpen()) {
                    try {
                        // 2. ⚠️ 修正类型名，确保和前端保持一致
                        WebSocketMessage<String> msg = new WebSocketMessage<>("DRAW_ACTION", actionStr);
                        playerSession.getBasicRemote().sendText(objectMapper.writeValueAsString(msg));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void handleChatMessage(JsonNode data, Session senderSession, String userId) throws Exception {
        String content = data.get("content").asText();

        GameRoom room = roomManager.getRoom(roomNo);
        if (room != null && "PLAYING".equals(room.getStatus())) {

            PlayerInfo sender = findUserInRoom(room, userId);
// 老哥你要求的非空防御，直接安排上！
            if (sender == null) {
                log.warn("收到幽灵消息：玩家 {} 已不在房间 {} 中", userId, roomNo);
                return;
            }

// 接下来再安全地使用 sender...


            // 如果发消息的是画手，或者已经猜对的人，可以禁止他们透题
            if (sender.getUserId().equals(room.getCurrentDrawerId()) || sender.getHasGuessed()) {
                if (content.contains(room.getCurrentWord())) {
                    // 画手或已猜对的人企图发答案，直接把答案替换成 ***
                    String regex = room.getCurrentWord();
                    String stars = "*".repeat(room.getCurrentWordLength());
                    content = content.replace(regex, stars);

                    // 重新构建要广播的 JSON
                    // (此处为了演示逻辑，实际你需要用 ObjectMapper 重构 JSON)
                }
            }
            // 正常猜词玩家
            else {
                // 模块 7 & 8：精确命中了答案！
                if (content.equals(room.getCurrentWord())) {
                    // 标记猜对
                    sender.setHasGuessed(true);

                    // 根据当前时间计算得分 (越早猜对分越高，比如满分10分)
                    long timeLeft = room.getRoundEndTime() - System.currentTimeMillis();
                    float scoreAdd = 10f * (timeLeft / 120000f);
                    sender.setScore(sender.getScore() + Math.max(1f, scoreAdd)); // 最少加 1 分

                    // 广播系统公告：XXX 猜对了！
                    ChatMessage sysMsg = new ChatMessage(0L, "系统公告", sender.getNickname() + " 猜对了答案！", System.currentTimeMillis());
                    broadcastToRoom(roomNo, objectMapper.writeValueAsString(new WebSocketMessage<>("CHAT_MESSAGE", sysMsg)));

                    // 广播状态更新 (刷新大家的分数和绿勾标)
                    broadcastToRoom(roomNo, objectMapper.writeValueAsString(new WebSocketMessage<>("ROOM_STATE_UPDATE", room)));

                    // 【进阶检查】：是不是除了画手之外，所有人都猜对了？
                    if (checkAllGuessed(room)) {
                        // 所有人提前猜对！取消 120 秒的定时器，立刻进入展览阶段！
                        ScheduledFuture<?> task = roomTasks.get(roomNo);
                        if (task != null) task.cancel(false);
                        enterExhibitionPhase(room);
                    }

                    return; // ⚠️拦截原消息，猜对的词绝对不能广播出去！
                }
            }
        }

        // 如果没拦截，正常广播聊天
        broadcastToRoom(roomNo,objectMapper.writeValueAsString(new WebSocketMessage<>("CHAT_MESSAGE", data)));
    }

    // ==========================================
    // 处理点赞请求
    // ==========================================
    private void handleLikeAction() {
        try {


            GameRoom room = roomManager.getRoom(roomNo);

            // 极限防御：必须在展览阶段才能点赞，防止有人抓包作弊
            if (!"EXHIBITION".equals(room.getStatus())) {
                return;
            }

            // 找到当前的画师
            Long drawerId = room.getCurrentDrawerId();
            PlayerInfo drawer = findUserInRoom(room, String.valueOf(drawerId));

            if (drawer != null) {
                // 给画师加 0.5 分
                drawer.setScore(drawer.getScore() + 0.5f);

                log.info("玩家点赞！画师 [{}] 加 0.5 分，当前总分: {}", drawer.getNickname(), drawer.getScore());

                // 核心：加完分后，立刻把最新的房间状态广播给所有人！
                // 这样所有人的屏幕上，画师的得分都会瞬间发生改变，成就感拉满！
                WebSocketMessage<String> likeEvent = new WebSocketMessage<>("LIKE_EVENT", drawer.getUserId().toString());
                broadcastToRoom(roomNo, objectMapper.writeValueAsString(likeEvent));
            }
        } catch (Exception e) {
            log.error("处理点赞异常", e);
        }
    }

    // ==========================================
    // 检查是否所有【猜词玩家】都猜对了答案
    // ==========================================
    private boolean checkAllGuessed(GameRoom room) {
        // 如果房间里只有画手1个人，直接返回 true（防止死循环）
        if (room.getPlayers().size() <= 1) {
            return true;
        }

        for (PlayerInfo playerInfo : room.getPlayers()) {
            // ⚠️ 核心修复：跳过当前的画手！只检查猜词的人！
            if (playerInfo.getUserId().equals(room.getCurrentDrawerId())) {
                continue;
            }

            // 只要有一个猜词人还没猜对，就说明没全猜对
            if (!playerInfo.getHasGuessed()) {
                return false;
            }
        }

        // 除了画手之外，其他人都猜对了！
        return true;
    }

    // ==========================================
    // 在房间内安全查找用户
    // ==========================================
    private PlayerInfo findUserInRoom(GameRoom room, String userId) {
        if (room == null || userId == null) return null; // 极限防御

        for (PlayerInfo playerInfo : room.getPlayers()) {
            if (playerInfo.getUserId().toString().equals(userId)) {
                return playerInfo;
            }
        }
        return null;
    }

    private String getUserIdFromSession(Session session) {
        try {
            return session.getPathParameters().get("userId");
        } catch (Exception e) {
            return "未知用户";
        }
    }


    /**
     * 第三步：当手机断开连接（退出游戏、杀后台、断网）时触发
     */
    @OnClose
    public void onClose() {
        if (roomNo == null || userId == null) return; // 防御式编程

        try {
            // 1. 获取这个人的昵称 (为了发公告，必须在人被踢掉之前获取)
            String nickname = "神秘玩家";
            GameRoom roomBeforeLeave = roomManager.getRoom(roomNo);
            if (roomBeforeLeave != null) {
                for (PlayerInfo p : roomBeforeLeave.getPlayers()) {
                    if (p.getUserId().toString().equals(userId)) {
                        nickname = p.getNickname();
                        break;
                    }
                }
            }

            // 2. 拔掉网络连接 (这步在 Tomcat 底层已经做了，我们主要是清理数据结构)
            CopyOnWriteArraySet<Session> roomSessions = ROOMS.get(roomNo);
            if (roomSessions != null) {
                roomSessions.remove(session);
            }

            // 3. 核心：报告给裁判，让他处理业务逻辑（包括权力交接）
            GameRoom updatedRoom = roomManager.leaveRoom(roomNo, userId);

            // 4. 根据裁判的处理结果，执行广播
            if (updatedRoom == null) {
                // 房间被销毁了，清理网络层的壳子
                ROOMS.remove(roomNo);
                log.info("WebSocket: 房间 [{}] 已空，管道清理完毕。", roomNo);
            } else {
                // 房间还在，广播双连击！
                // 广播1: 最新的房间状态（前端收到后，会看到有人离开，且房主图标可能换人了）
                broadcastToRoom(roomNo, objectMapper.writeValueAsString(new WebSocketMessage<>("ROOM_STATE_UPDATE", updatedRoom)));

                // 广播2: 系统公告
                String noticeText = "玩家 [" + nickname + "] 挥一挥衣袖，离开了画室。";
                // 检查离开的是否是旧房主
                if(roomBeforeLeave != null && roomBeforeLeave.getOwnerId().toString().equals(userId)) {
                    // 找到新房主的名字
                    String newOwnerNickname = "新房主";
                    for(PlayerInfo p : updatedRoom.getPlayers()){
                        if(p.getUserId().equals(updatedRoom.getOwnerId())){
                            newOwnerNickname = p.getNickname();
                            break;
                        }
                    }
                    noticeText += " 房主已自动移交给 [" + newOwnerNickname + "]。";
                }

                ChatMessage systemChat = new ChatMessage(0L, "系统公告", noticeText, System.currentTimeMillis());
                broadcastToRoom(roomNo, objectMapper.writeValueAsString(new WebSocketMessage<>("CHAT_MESSAGE", systemChat)));
            }

        } catch (Exception e) {
            log.error("处理玩家 [{}] 离线时发生异常", userId, e);
        }
    }
    /**
     * 第四步：网络波动报错时触发
     */
    @OnError
    public void onError(Session session, Throwable error) {
        System.out.println("❌玩家 [" + userId + "] 连接发生异常！");
        error.printStackTrace();
    }


    // ... 在 GameRoomServer.java 的末尾 ...

    /**
     * 广播消息给指定房间的所有人
     * @param roomNo  房间号
     * @param message 要发送的消息
     */
    private void broadcastToRoom(String roomNo, String message) {
        CopyOnWriteArraySet<Session> roomPlayers = ROOMS.get(roomNo);
        if (roomPlayers != null) {
            for (Session playerSession : roomPlayers) {
                if (playerSession.isOpen()) { // 确保连接是打开的
                    try {
                        playerSession.getBasicRemote().sendText(message);
                    } catch (IOException e) {
                        // 记录日志，而不是直接打印堆栈
                        log.error("广播消息给房间 {} 的玩家 {} 失败: {}", roomNo, getUserIdFromSession(playerSession), e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 广播消息给指定房间的【其他人】（不包括自己）
     * @param roomNo  房间号
     * @param message 要发送的消息
     * @param self    自己的 Session，用来排除
     */
    private void broadcastToOthers(String roomNo, String message, Session self) {
        CopyOnWriteArraySet<Session> roomPlayers = ROOMS.get(roomNo);
        if (roomPlayers != null) {
            for (Session playerSession : roomPlayers) {
                // 排除自己，并且确保连接是打开的
                if (!playerSession.equals(self) && playerSession.isOpen()) {
                    try {
                        playerSession.getBasicRemote().sendText(message);
                    } catch (IOException e) {
                        log.error("广播消息给房间 {} 的其他玩家失败: {}", roomNo, e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * [可选的辅助方法] 从 Session 中获取 userId
     * 因为 Session 对象里没有直接存 userId，我们可以通过 PathParameters 来获取
     * 这样日志会更清晰
     */




    // ==========================================
    // 开启游戏 / 开启新回合 (模块 1,2,3,4,10)
    // ==========================================
    private void startNewRound(GameRoom room) throws Exception {
        // 如果是第 0 回合，初始化词库 (模块 1, 2)
        if (room.getCurrentTurnIndex() == 0) {
            List<GameWord> words = gameWordMapper.getRandomWords(10);
            room.setWordPool(words);
        }

        // 检查是否所有人都画过了 (模块 10 的终止条件)
        if (room.getCurrentTurnIndex() >= room.getPlayers().size()) {
            room.setStatus("GAME_OVER");
            broadcastToRoom(room.getRoomNo(), objectMapper.writeValueAsString(new WebSocketMessage<>("ROOM_STATE_UPDATE", room)));
            return;
        }

        // 模块 3：挑选当前回合的词
        GameWord currentWord = room.getWordPool().get(room.getCurrentTurnIndex());
        room.setCurrentWord(currentWord.getWordText());
        room.setCurrentWordLength(currentWord.getWordText().length());
        room.setWordHint(currentWord.getCategory());

        // 模块 4：挑选画手 (按列表顺序)
        PlayerInfo drawer = room.getPlayers().get(room.getCurrentTurnIndex());
        room.setCurrentDrawerId(drawer.getUserId());

        // 重置所有人的猜词状态
        for (PlayerInfo p : room.getPlayers()) {
            p.setHasGuessed(false);
        }

        // 设置房间状态和 120秒倒计时
        room.setStatus("PLAYING");
        long endTime = System.currentTimeMillis() + 120 * 1000;
        room.setRoundEndTime(endTime);

        // 广播新回合数据 (模块 4 完毕)
        broadcastToRoom(room.getRoomNo(), objectMapper.writeValueAsString(new WebSocketMessage<>("ROOM_STATE_UPDATE", room)));

        // 模块 6：设置 120 秒后自动结算的定时任务！
        ScheduledFuture<?> futureTask = gameScheduler.schedule(() -> {
            enterExhibitionPhase(room);
        }, 120, TimeUnit.SECONDS);

        // 把任务存起来，万一大家都提前猜对了，我们要取消它
        roomTasks.put(room.getRoomNo(), futureTask);
    }


    // ==========================================
    // 回合结束：展览画作 (模块 9)
    // ==========================================
    private void enterExhibitionPhase(GameRoom room) {
        try {
            // 切入展览状态
            room.setStatus("EXHIBITION");
            // 展览 10 秒
            room.setRoundEndTime(System.currentTimeMillis() + 10 * 1000);

            // 告诉所有人，答案到底是什么 (这时可以下发了)
            // 你可以通过一个特殊的系统消息，或者在 ROOM_STATE_UPDATE 里加上答案
            ChatMessage sysMsg = new ChatMessage(0L, "系统公告", "时间到！本轮答案是：【" + room.getCurrentWord() + "】", System.currentTimeMillis());
            broadcastToRoom(room.getRoomNo(), objectMapper.writeValueAsString(new WebSocketMessage<>("CHAT_MESSAGE", sysMsg)));

            // 广播状态，前端收到 EXHIBITION，显示点赞按钮，锁定画板
            broadcastToRoom(room.getRoomNo(), objectMapper.writeValueAsString(new WebSocketMessage<>("ROOM_STATE_UPDATE", room)));

            // 设置 10 秒后，进入下一回合的定时器 (模块 10)
            // 设置 10 秒后，进入下一回合的定时器
            ScheduledFuture<?> futureTask = gameScheduler.schedule(() -> {
                try {
                    // 判断游戏是否结束：回合游标即将超过总人数
                    if (room.getCurrentTurnIndex() + 1 >= room.getPlayers().size()) {
                        // 触发游戏结束
                        enterGameOverPhase(room);
                    } else {
                        // 游戏未结束，正常进入下一轮
                        room.setCurrentTurnIndex(room.getCurrentTurnIndex() + 1);
                        startNewRound(room);
                    }
                } catch (Exception e) {
                    log.error("切换回合/结束游戏时发生异常", e);
                }
            }, 10, TimeUnit.SECONDS);

            roomTasks.put(room.getRoomNo(), futureTask);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // ==========================================
    // 游戏结束：进入最终结算
    // ==========================================
    private void enterGameOverPhase(GameRoom room) throws JsonProcessingException {
        // 清理掉之前的定时任务
        ScheduledFuture<?> task = roomTasks.remove(room.getRoomNo());
        if (task != null) {
            task.cancel(false);
        }

        // 1. 设置最终状态
        room.setStatus("GAME_OVER");

        // 2. 广播最终结算状态，前端收到后将弹出结算面板
        broadcastToRoom(room.getRoomNo(), objectMapper.writeValueAsString(new WebSocketMessage<>("ROOM_STATE_UPDATE", room)));
        log.info("房间 [{}] 游戏结束，广播 GAME_OVER 状态。", room.getRoomNo());

        // 3. ⚠️ 核心架构：广播完结算面板后，服务器默默地把房间重置回“等待状态”
        // 这样玩家在结算界面点击“回到房间”时，看到的就是一个可以重新准备的新房间
        room.setStatus("WAITING");
        room.setCurrentTurnIndex(0);
        room.setCurrentWord(null);
        room.setRoundEndTime(null);
        room.getWordPool().clear();
        room.setWordHint("等待游戏开始...");
        room.setCurrentDrawerId((long)0);
        for (PlayerInfo player : room.getPlayers()) {
            player.setHasGuessed(false);
            player.setStatus(PlayerInfo.PLAYER_STATUS_VIEWING_RESULT);
            // ⚠️ 分数不清零！要让玩家在结算界面能看到自己的最终分数
        }
    }

}