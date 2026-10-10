package dev.fastshare.config;

import dev.fastshare.room.RoomIdentity;
import dev.fastshare.room.RoomStore;
import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Set;

@Component
public class SocketAccess implements ChannelInterceptor {
    private static final Logger log = LoggerFactory.getLogger(SocketAccess.class);
    private final RoomStore rooms;
    private final SimpMessagingTemplate broker;

    public SocketAccess(RoomStore rooms, @Lazy SimpMessagingTemplate broker) {
        this.rooms = rooms;
        this.broker = broker;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null || headers.getCommand() == null) return message;
        StompCommand command = headers.getCommand();
        if (command == StompCommand.CONNECT) {
            String room = headers.getFirstNativeHeader("room");
            String token = headers.getFirstNativeHeader("token");
            headers.removeNativeHeader("token");
            String connection = headers.getFirstNativeHeader("connection-id");
            RoomStore.requireUuid(connection);
            RoomIdentity identity = rooms.authenticate(room, token, connection);
            rooms.execute(room, "connect", Map.of("token", token, "connectionId", connection));
            headers.setUser(identity);
            broker.convertAndSend(identity.chat(), Map.of(
                    "type", "member",
                    "member", Map.of("id", identity.participantId(), "name", identity.name(),
                            "connectionId", identity.connectionId(), "sharing", false)
            ));
        } else if (command == StompCommand.SUBSCRIBE || command == StompCommand.SEND) {
            if (!(headers.getUser() instanceof RoomIdentity identity)) throw new IllegalArgumentException("Acesso inválido.");
            if (headers.toNativeHeaderMap().values().stream().anyMatch(values -> values.size() != 1)) {
                throw new IllegalArgumentException("Cabeçalhos duplicados não são permitidos.");
            }
            if (command == StompCommand.SUBSCRIBE) {
                Set<String> allowed = Set.of("destination", "id", "ack", "receipt");
                if (!allowed.containsAll(headers.toNativeHeaderMap().keySet())
                        || (headers.getFirstNativeHeader("ack") != null && !"auto".equals(headers.getFirstNativeHeader("ack")))) {
                    throw new IllegalArgumentException("Assinatura inválida.");
                }
                if (!identity.chat().equals(headers.getDestination()) && !identity.inbox().equals(headers.getDestination())) {
                    throw new IllegalArgumentException("Destino não autorizado.");
                }
                rooms.authenticate(identity.roomId(), identity.token(), identity.connectionId());
            } else if (!Set.of("/app/chat", "/app/heartbeat", "/app/share", "/app/signal").contains(headers.getDestination())) {
                throw new IllegalArgumentException("Destino não autorizado.");
            }
        } else if (!Set.of(StompCommand.DISCONNECT, StompCommand.UNSUBSCRIBE).contains(command)) {
            throw new IllegalArgumentException("Comando não autorizado.");
        }
        return message;
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        if (event.getUser() instanceof RoomIdentity identity) {
            try {
                rooms.execute(identity.roomId(), "disconnect", Map.of("token", identity.token(),
                        "connectionId", identity.connectionId()));
                broker.convertAndSend(identity.chat(), Map.of("type", "leave", "connectionId", identity.connectionId()));
            } catch (RuntimeException exception) {
                log.debug("Presença será removida pelo prazo da conexão: {}", exception.getClass().getSimpleName());
            }
        }
    }
}
