package dev.fastshare.room;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomStore rooms;
    private final TurnCredentials turn;

    public RoomController(RoomStore rooms, TurnCredentials turn) {
        this.rooms = rooms;
        this.turn = turn;
    }

    public record JoinRequest(@NotBlank @Size(max = 40) String name) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> create() {
        String id = UUID.randomUUID().toString();
        rooms.execute(id, "create", Map.of());
        return Map.of("id", id);
    }

    @PostMapping("/{roomId}/join")
    public JsonNode join(@PathVariable String roomId, @Valid @RequestBody JoinRequest request) {
        return rooms.execute(roomId, "join", Map.of("name", request.name().strip(),
                "token", UUID.randomUUID().toString(), "id", UUID.randomUUID().toString()));
    }

    @GetMapping("/{roomId}")
    public JsonNode state(@PathVariable String roomId, @RequestHeader("Authorization") String authorization,
                          @RequestParam(defaultValue = "0") long after) {
        if (after < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requisição inválida.");
        }
        return rooms.execute(roomId, "state", Map.of("token", token(authorization), "after", after));
    }

    @GetMapping("/{roomId}/ice")
    public Map<String, ?> ice(@PathVariable String roomId, @RequestHeader("Authorization") String authorization) {
        JsonNode user = rooms.execute(roomId, "auth", Map.of("token", token(authorization)));
        return Map.of("iceServers", turn.forParticipant(user.get("id").asText()));
    }

    private String token(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requisição inválida.");
        }
        String token = authorization.substring(7);
        RoomStore.requireUuid(token);
        return token;
    }
}
