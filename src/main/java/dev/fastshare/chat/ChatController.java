package dev.fastshare.chat;

import com.fasterxml.jackson.databind.JsonNode;
import dev.fastshare.room.RoomIdentity;
import dev.fastshare.room.RoomStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Controller
public class ChatController {
    private final RoomStore rooms;
    private final SimpMessagingTemplate broker;

    public ChatController(RoomStore rooms, SimpMessagingTemplate broker) {
        this.rooms = rooms;
        this.broker = broker;
    }

    public record ChatInput(@NotBlank String clientId, @NotBlank @Size(max = 2000) String text) {}
    public record ShareInput(@NotNull Boolean sharing) {}
    public record SignalInput(@NotBlank String target, @NotBlank String callId,
                              @Pattern(regexp = "watch|offer|answer|ice|stop") @NotNull String kind,
                              JsonNode data) {}

    private Map<String, Object> payload(RoomIdentity identity) {
        Map<String, Object> result = new HashMap<>();
        result.put("token", identity.token());
        result.put("connectionId", identity.connectionId());
        return result;
    }

    @MessageMapping("/chat")
    public void chat(@Valid ChatInput input, Principal principal) {
        RoomIdentity identity = (RoomIdentity) principal;
        RoomStore.requireUuid(input.clientId());
        Map<String, Object> data = payload(identity);
        data.put("clientId", input.clientId());
        data.put("text", input.text().strip());
        JsonNode saved = rooms.execute(identity.roomId(), "message", data);
        broker.convertAndSend(identity.chat(), saved);
    }

    @MessageMapping("/heartbeat")
    public void heartbeat(Principal principal) {
        RoomIdentity identity = (RoomIdentity) principal;
        rooms.execute(identity.roomId(), "heartbeat", payload(identity));
    }

    @MessageMapping("/share")
    public void share(@Valid ShareInput input, Principal principal) {
        RoomIdentity identity = (RoomIdentity) principal;
        Map<String, Object> data = payload(identity);
        data.put("sharing", input.sharing());
        rooms.execute(identity.roomId(), "share", data);
        broker.convertAndSend(identity.chat(), Map.of(
                "type", "member",
                "member", Map.of("id", identity.participantId(), "name", identity.name(),
                        "connectionId", identity.connectionId(), "sharing", input.sharing())
        ));
    }

    @MessageMapping("/signal")
    public void signal(@Valid SignalInput input, Principal principal) {
        RoomIdentity identity = (RoomIdentity) principal;
        RoomStore.requireUuid(input.target());
        RoomStore.requireUuid(input.callId());
        Map<String, Object> data = payload(identity);
        data.put("target", input.target());
        data.put("kind", input.kind());
        rooms.execute(identity.roomId(), "signal", data);
        Map<String, Object> event = new HashMap<>();
        event.put("type", "signal");
        event.put("from", identity.connectionId());
        event.put("callId", input.callId());
        event.put("kind", input.kind());
        event.put("data", input.data());
        broker.convertAndSend("/topic/room." + identity.roomId() + ".signal." + input.target(), event);
    }

    @MessageExceptionHandler
    public void error(Exception exception, Principal principal) {
        if (principal instanceof RoomIdentity identity) {
            String message = exception instanceof ResponseStatusException status ? status.getReason()
                    : "Não foi possível concluir a operação. Verifique os dados e tente novamente.";
            broker.convertAndSend(identity.inbox(), Map.of("type", "error", "message", message));
        }
    }
}
