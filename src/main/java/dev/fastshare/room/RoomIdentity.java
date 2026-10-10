package dev.fastshare.room;

import java.security.Principal;

public record RoomIdentity(String roomId, String token, String participantId, String name,
                           String connectionId) implements Principal {
    @Override
    public String getName() {
        return participantId;
    }

    public String inbox() {
        return "/topic/room." + roomId + ".signal." + connectionId;
    }

    public String chat() {
        return "/topic/room." + roomId + ".chat";
    }

    @Override
    public String toString() {
        return "RoomIdentity[participantId=" + participantId + ", connectionId=" + connectionId + "]";
    }
}
