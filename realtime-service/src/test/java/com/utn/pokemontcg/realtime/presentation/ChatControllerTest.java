package com.utn.pokemontcg.realtime.presentation;

import com.utn.pokemontcg.realtime.dto.ChatMessage;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChatControllerTest {

    @Test
    void shouldBroadcastChatMessageToGameTopic() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        ChatController controller = new ChatController(template);
        ChatMessage message = new ChatMessage("player-1", "Ash", "hello", String.valueOf(System.currentTimeMillis()), "msg-1");

        controller.handleChat("game-123", message);

        verify(template).convertAndSend("/topic/games/game-123/chat", message);
    }
}
