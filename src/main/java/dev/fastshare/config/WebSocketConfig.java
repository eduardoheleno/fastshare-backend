package dev.fastshare.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final SocketAccess access;
    private final String[] origins;
    private final String host;
    private final int port;
    private final String username;
    private final String password;

    public WebSocketConfig(SocketAccess access,
                           @Value("${fastshare.allowed-origins}") String[] origins,
                           @Value("${fastshare.broker.host}") String host,
                           @Value("${fastshare.broker.port}") int port,
                           @Value("${fastshare.broker.username}") String username,
                           @Value("${fastshare.broker.password}") String password) {
        this.access = access;
        this.origins = origins;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(origins);
    }

    @Bean
    TaskScheduler brokerRelayTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("stomp-relay-");
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setPreservePublishOrder(true);
        registry.enableStompBrokerRelay("/topic")
                .setRelayHost(host).setRelayPort(port)
                .setClientLogin(username).setClientPasscode(password)
                .setSystemLogin(username).setSystemPasscode(password)
                .setTaskScheduler(brokerRelayTaskScheduler());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(access);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(64 * 1024)
                .setSendBufferSizeLimit(256 * 1024).setSendTimeLimit(10_000)
                .setTimeToFirstMessage(10_000);
    }
}
