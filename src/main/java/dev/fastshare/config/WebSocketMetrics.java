package dev.fastshare.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketMetrics {
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();
    private final Counter connections;
    private final Counter normalDisconnects;
    private final Counter abnormalDisconnects;

    public WebSocketMetrics(MeterRegistry registry) {
        Gauge.builder("fastshare.websocket.connections.active", sessions, Set::size).register(registry);
        connections = Counter.builder("fastshare.websocket.connections").register(registry);
        normalDisconnects = Counter.builder("fastshare.websocket.disconnects")
                .tag("result", "normal").register(registry);
        abnormalDisconnects = Counter.builder("fastshare.websocket.disconnects")
                .tag("result", "abnormal").register(registry);
    }

    @EventListener
    public void connected(SessionConnectedEvent event) {
        connected(SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders()));
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        disconnected(event.getSessionId(), event.getCloseStatus());
    }

    void connected(String sessionId) {
        if (sessionId != null && sessions.add(sessionId)) connections.increment();
    }

    void disconnected(String sessionId, CloseStatus status) {
        if (sessionId == null || !sessions.remove(sessionId)) return;
        if (CloseStatus.NORMAL.equals(status)) normalDisconnects.increment();
        else abnormalDisconnects.increment();
    }
}
