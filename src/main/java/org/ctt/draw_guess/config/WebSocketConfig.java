package org.ctt.draw_guess.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;
import org.ctt.draw_guess.manager.RoomManager;
import org.ctt.draw_guess.websocket.GameRoomServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

/**
 * WebSocket 配置类
 * 相当于告诉 Spring Boot：“老哥，我要开启全双工长连接功能了，帮我把引擎点着！”
 */
@Configuration
public class WebSocketConfig {


    @Autowired
    private RoomManager roomManager;

    @Autowired
    private ObjectMapper objectMapper; // 【新增】注入 ObjectMapper
    @PostConstruct
    public void init() {
        GameRoomServer.setRoomManager(roomManager);
        GameRoomServer.setObjectMapper(objectMapper); // 【新增】
    }
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        // 这个 Bean 是必须的，它会自动注册所有带有 @ServerEndpoint 注解的 WebSocket 服务
        return new ServerEndpointExporter();
    }
}