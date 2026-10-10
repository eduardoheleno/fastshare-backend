package dev.fastshare;

import dev.fastshare.config.SocketAccess;
import dev.fastshare.room.RoomIdentity;
import dev.fastshare.room.RoomStore;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SocketAccessTest {
    @Test
    void rejectsOtherRoomsDirectBrokerPublishingAndCustomQueueHeaders() {
        RoomStore rooms = mock(RoomStore.class);
        SocketAccess access = new SocketAccess(rooms, mock(SimpMessagingTemplate.class));
        RoomIdentity identity = new RoomIdentity("room-a", "token", "person", "Ana", "connection");
        for (String destination : new String[]{"/topic/room.other.chat", "/topic/room.room-a.signal.someone"}) {
            var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            headers.setUser(identity);
            headers.setDestination(destination);
            assertThrows(IllegalArgumentException.class,
                    () -> access.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null));
        }
        var publish = StompHeaderAccessor.create(StompCommand.SEND);
        publish.setUser(identity);
        publish.setDestination(identity.chat());
        assertThrows(IllegalArgumentException.class,
                () -> access.preSend(MessageBuilder.createMessage(new byte[0], publish.getMessageHeaders()), null));
        var subscription = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscription.setUser(identity);
        subscription.setDestination(identity.chat());
        subscription.setNativeHeader("x-queue-name", "shared");
        assertThrows(IllegalArgumentException.class,
                () -> access.preSend(MessageBuilder.createMessage(new byte[0], subscription.getMessageHeaders()), null));
        verifyNoInteractions(rooms);
    }
}
