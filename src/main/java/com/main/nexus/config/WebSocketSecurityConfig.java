package com.main.nexus.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

//Esse arquivo desativa os sistemas de seguranca padrao do WebSocket, que conflitariam caso nao fossem sobreescritos
@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

    // A autenticação já é feita no WebSocketAuthInterceptor durante o CONNECT
    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        messages.anyMessage().permitAll();
        return messages.build();
    }

    // @EnableWebSocketSecurity registra por padrão um XorCsrfChannelInterceptor
    // que exige token CSRF em todo CONNECT. CSRF já está desabilitado
    // globalmente (JWT stateless, ver SecurityConfig), então sobrescrevemos
    // esse bean com um no-op — sem isso o Spring bloqueia o CONNECT STOMP com
    // MissingCsrfTokenException mesmo com csrf().disable() no SecurityConfig HTTP.
    @Bean
    ChannelInterceptor csrfChannelInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                return message;
            }
        };
    }
}
