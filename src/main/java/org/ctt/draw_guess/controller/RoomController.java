package org.ctt.draw_guess.controller;

import lombok.extern.slf4j.Slf4j;
import org.ctt.draw_guess.common.Result;
import org.ctt.draw_guess.dto.GameRoom;
import org.ctt.draw_guess.dto.PlayerInfo;
import org.ctt.draw_guess.entity.SysUser;
import org.ctt.draw_guess.manager.RoomManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/room")
public class RoomController {

    @Autowired
    RoomManager roomManager;



    // controller/RoomController.java

    @PostMapping("/create")
// 看！直接拿 SysUser，不需要拆包！
    public Result<GameRoom> createRoom(@RequestBody Map<String, String> payload, @AuthenticationPrincipal SysUser user) {

        String roomName = payload.get("roomName");
        if (roomName == null || roomName.trim().isEmpty()) {
            roomName = user.getNickname() + "的房间";
        }

        // 直接传进去，顺畅无比！
        GameRoom newRoom = roomManager.createRoom(roomName, user);
        log.info("房间 [{}] 由 [{}] 创建成功。", newRoom.getRoomNo(), user.getNickname());
        return Result.success(newRoom);
    }


    // 👇👇 新增的 GET 请求：获取房间列表 👇👇
    @GetMapping("/list")
    public Result<List<GameRoom>> getRoomList(@RequestParam(required = false) String keyword) {
        // 调用刚才写好的搜索方法，如果前端不传 keyword，就会返回所有备战房间
        List<GameRoom> rooms = roomManager.searchWaitingRooms(keyword);
        return Result.success(rooms);
    }
    // 👇👇 新增的 POST 请求：加入房间 👇👇
    @PostMapping("/join")
    public Result<GameRoom> joinRoom(@RequestBody Map<String, String> payload, @AuthenticationPrincipal SysUser user) {
        String roomNo = payload.get("roomNo");


        try {
            // 调用加入逻辑
            GameRoom updatedRoom = roomManager.joinRoom(roomNo, user);
            // 加入成功后，把最新的房间完整信息返回给安卓端
            return Result.success(updatedRoom);
        } catch (RuntimeException e) {
            // 如果抛出了“房间满”之类的异常，通过 Result 优雅地告诉前端
            return Result.error(e.getMessage());
        }
    }
    // RoomController.java
    //    @PostMapping("/join")
    //    public Result<?> joinRoom(@RequestBody JoinRoomRequest request, @AuthenticationPrincipal SysUser user) {
    //        // 【核心】让“裁判”先处理业务逻辑
    //        boolean success = roomManager.joinRoom(request.getRoomNo(), user);
    //        if (success) {
    //            return Result.success("加入成功，请连接WebSocket");
    //        } else {
    //            return Result.error("加入失败，房间已满或不存在");
    //        }
    //
    //    }
}
