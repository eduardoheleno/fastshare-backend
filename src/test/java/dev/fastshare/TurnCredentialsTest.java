package dev.fastshare;

import dev.fastshare.room.TurnCredentials;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnCredentialsTest {
    @Test
    void createsExpiringCredentialsForConfiguredTurnUrls() throws Exception {
        var service = new TurnCredentials("turn:turn.example.com:3478?transport=udp,turn:turn.example.com:3478?transport=tcp", "secret", 600);
        var server = service.forParticipant("participant").getFirst();

        assertEquals(List.of("turn:turn.example.com:3478?transport=udp", "turn:turn.example.com:3478?transport=tcp"), server.urls());
        assertEquals("participant", server.username().split(":", 2)[1]);
        assertTrue(Long.parseLong(server.username().split(":", 2)[0]) > System.currentTimeMillis() / 1000);
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        assertEquals(Base64.getEncoder().encodeToString(mac.doFinal(server.username().getBytes(StandardCharsets.UTF_8))), server.credential());
    }

    @Test
    void returnsNoTurnServerWhenItIsNotConfigured() {
        assertFalse(new TurnCredentials("", "", 600).forParticipant("participant").iterator().hasNext());
    }
}
