package com.main.nexus.config;

import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.User;
import com.main.nexus.repository.UserRepository;
import com.main.nexus.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null) {
                header = accessor.getFirstNativeHeader("token");
            }

            if (header == null) {
                throw new MessageDeliveryException("Unauthorized");
            }

            String token = header.startsWith("Bearer ") ? header.substring(7) : header;

            if (!tokenService.validToken(token)) {
                throw new MessageDeliveryException("Unauthorized: invalid token");
            }

            UserDTO userDTO = tokenService.extractClaims(token);

            User user = userRepository.findByEmail(userDTO.email())
                    .orElseThrow(() -> new MessageDeliveryException("Unauthorized: user not found"));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getType())));

            accessor.setUser(authentication);
        }

        return message;
    }
}
