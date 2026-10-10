package dev.fastshare.room;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class TurnCredentials {
    public record IceServer(List<String> urls, String username, String credential) {}

    private final List<String> urls;
    private final String secret;
    private final long ttlSeconds;

    public TurnCredentials(@Value("${fastshare.turn.urls:}") String turnUrls,
                           @Value("${fastshare.turn.shared-secret:}") String secret,
                           @Value("${fastshare.turn.credential-ttl-seconds:3600}") long ttlSeconds) {
        this.urls = List.of(turnUrls.split(",")).stream().map(String::strip).filter(url -> !url.isEmpty()).toList();
        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
        if (urls.isEmpty() != secret.isBlank() || ttlSeconds < 1) {
            throw new IllegalStateException("A configuração TURN é inválida.");
        }
    }

    public List<IceServer> forParticipant(String participantId) {
        if (urls.isEmpty()) return List.of();
        String username = (Instant.now().getEpochSecond() + ttlSeconds) + ":" + participantId;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            String credential = Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
            return List.of(new IceServer(urls, username, credential));
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível gerar a credencial TURN.", exception);
        }
    }
}
