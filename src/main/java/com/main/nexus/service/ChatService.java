package com.main.nexus.service;

import com.main.nexus.model.Match;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MessageRepository messageRepository;

    // Faz todas as validacoes se a pessoa tem acesso aquela chat
    // Usado no envio de mensagem e consulta de contagem por match
    public Match validateChatAccess(Long matchId, Long userId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404),
                        "Match not found."));

        Long professionalUserId = match.getProfessional().getUser().getId();
        Long companyUserId = match.getProject().getCompany().getUser().getId();

        if (!userId.equals(professionalUserId) && !userId.equals(companyUserId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "You are not a participant of this match.");
        }

        if (Boolean.FALSE.equals(match.getActive())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(410), // 410 significa "isso existiu e era acessível, mas não está mais disponível permanentemente"
                    "This chat is no longer available. The match has expired.");
        }

        if (match.getStatus() != StatusMatch.MATCHED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Chat is only available for confirmed matches.");
        }

        return match;
    }

    // Valida apenas participação — usado para leitura do histórico.
    // Não valida active nem status: matches encerrados continuam com
    // o histórico acessível em modo leitura (envio bloqueado à parte,
    // via validateChatAccess no WebSocket handler).
    
    // E usado na leitura do historico do chat
    public Match validateReadAccess(Long matchId, Long userId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404),
                        "Match not found"));

        boolean isParticipant = match.getProfessional().getUser().getId().equals(userId)
                || match.getProject().getCompany().getUser().getId().equals(userId);

        if (!isParticipant) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "You are not a participant of this match.");
        }

        return match;
    }

    // Dado um match, verifica quem e o usuario e retorna o user do outro lado do chat, usado para notificat a pessoa certa via WebSocket
    public User getOtherParty(Match match, Long myUserId) {
        Long professionalUserId = match.getProfessional().getUser().getId();
        Long companyUserId = match.getProject().getCompany().getUser().getId();

        if (myUserId.equals(professionalUserId)) {
            return match.getProject().getCompany().getUser();
        }

        if (myUserId.equals(companyUserId)) {
            return match.getProfessional().getUser();
        }

        throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                "You are not a participant of this match.");
    }

    // Total de mensagens não lidas do usuário, somando todos os matches ativos —
    // usado tanto pelo endpoint REST do badge quanto pelo push via WebSocket.
    public long countUnreadTotalForUser(Long userId) {
        return messageRepository.countUnreadTotalForUser(userId);
    }
}
