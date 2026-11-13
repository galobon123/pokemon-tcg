package com.utn.pokemontcg.realtime.presentation;

import com.utn.pokemontcg.realtime.dto.ChatMessage;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat/{gameId}")
    public void handleChat(@DestinationVariable("gameId") String gameId, ChatMessage message) {
        messagingTemplate.convertAndSend("/topic/games/" + gameId + "/chat", message);
    }
}
