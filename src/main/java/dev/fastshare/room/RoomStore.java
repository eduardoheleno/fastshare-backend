package dev.fastshare.room;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RoomStore {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final DefaultRedisScript<String> script;
    private final long idleMs;
    private final long leaseMs;
    private final int maxParticipants;
    private final int maxMessages;

    public RoomStore(StringRedisTemplate redis, ObjectMapper mapper,
                     @Value("${fastshare.idle-ms}") long idleMs,
                     @Value("${fastshare.lease-ms}") long leaseMs,
                     @Value("${fastshare.max-participants}") int maxParticipants,
                     @Value("${fastshare.max-messages}") int maxMessages) {
        this.redis = redis;
        this.mapper = mapper;
        this.idleMs = idleMs;
        this.leaseMs = leaseMs;
        this.maxParticipants = maxParticipants;
        this.maxMessages = maxMessages;
        this.script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("room.lua"));
        script.setResultType(String.class);
    }

    public JsonNode execute(String roomId, String action, Map<String, ?> payload) {
        requireUuid(roomId);
        String prefix = "room:{" + roomId + "}:";
        List<String> keys = List.of("meta", "auth", "presence", "members", "messages", "dedup")
                .stream().map(prefix::concat).toList();
        try {
            String result = redis.execute(script, keys, action, mapper.writeValueAsString(payload),
                    Long.toString(idleMs), Long.toString(leaseMs),
                    Integer.toString(maxParticipants), Integer.toString(maxMessages));
            JsonNode value = mapper.readTree(result);
            if (value.has("error")) {
                String error = value.get("error").asText();
                HttpStatus status = switch (error) {
                    case "Sala expirada ou inexistente." -> HttpStatus.NOT_FOUND;
                    case "Acesso inválido." -> HttpStatus.UNAUTHORIZED;
                    case "Sala lotada.", "Limite de mensagens atingido.", "Limite de entradas atingido." -> HttpStatus.CONFLICT;
                    default -> HttpStatus.BAD_REQUEST;
                };
                throw new ResponseStatusException(status, error);
            }
            if (action.equals("state")) {
                for (String field : List.of("members", "messages")) {
                    if (!value.get(field).isArray()) ((ObjectNode) value).set(field, mapper.createArrayNode());
                }
            }
            return value;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível processar os dados da sala.", exception);
        }
    }

    public RoomIdentity authenticate(String roomId, String token, String connectionId) {
        requireUuid(token);
        JsonNode user = execute(roomId, "auth", Map.of("token", token));
        return new RoomIdentity(roomId, token, user.get("id").asText(), user.get("name").asText(), connectionId);
    }

    public static void requireUuid(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador inválido.");
        }
        UUID.fromString(value);
    }
}
