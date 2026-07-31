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

//Com todos os sistemas de seguranca sobreescritos, e necessario fazer a seguranca na mao, para isso que esse arquivo serve
// Esse arquivo e analogo ao JwtFilter faz para requisicoes HTTP, esse faz para STOMP
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor { 

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) { //Esse metodo do ChannelInterceptor e chamado para toda mensagem que entra pelo canal de entrada do cliente
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class); // Pega os dados STOMP da mensagem

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) { // So acontece quando o comando e de CONNECT, o primeiro hadshake, ou seja, a validacao so acontece uma unica vez, nao em cada mensagem
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
                    // Aqui e aonde a idantidade autenticada e associada a sessao STOMP, e isso que permite depois a usar Principal/@AuthenticationPrincipal. Para saber a quem entregar as mensagens pessoais
                    new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getType())));

            accessor.setUser(authentication);
        }

        return message;
    }
}
