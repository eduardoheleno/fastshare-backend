package dev.fastshare.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebSocketMetricsTest {
    @Test
    void tracksEachStompSessionOnce() {
        var registry = new SimpleMeterRegistry();
        var metrics = new WebSocketMetrics(registry);

        metrics.connected("first");
        metrics.connected("first");
        metrics.connected("second");
        metrics.disconnected("first", CloseStatus.NORMAL);
        metrics.disconnected("first", CloseStatus.NORMAL);
        metrics.disconnected("second", CloseStatus.SERVER_ERROR);

        assertEquals(2, registry.get("fastshare.websocket.connections").counter().count());
        assertEquals(0, registry.get("fastshare.websocket.connections.active").gauge().value());
        assertEquals(1, registry.get("fastshare.websocket.disconnects").tag("result", "normal").counter().count());
        assertEquals(1, registry.get("fastshare.websocket.disconnects").tag("result", "abnormal").counter().count());
    }
}
