package com.main.nexus.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker //Habilita o protocolo STOMP
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue"); //Habilita o broker do proprio Spring, ta dizendo para o broker cuidar de todos os destinos que comecam com /topic e /queue
        // Topic - É um broadcast, um canal para duas ou mais pessoas
        // Queue - E mensagens direcionadas para um usuario especifico
        config.setApplicationDestinationPrefixes("/app"); // COnfigura o prefixo app, para receber mensagens de todo client, encaminha todas as mensagens para metodos com @messageMapping
        config.setUserDestinationPrefix("/user"); // Hablitia destinos pessoais por usuario
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws") // Define o endpoint como handshake inicial, para a conexao Websocket
                .setAllowedOriginPatterns("*") // Libera o CORS para qualquer origem, deve ser aceito apenas em desenvolvimento
                .withSockJS(); //Adicione um fallback de compatibilidade, caso o navegador nao tenha suporte ao WebSocket
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) { // Registra como interceptador de toda mensagem que entra no servidro, vindo do cliente
        registration.interceptors(webSocketAuthInterceptor); 
    }
}
