package com.main.nexus.service;

import com.main.nexus.model.Match;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.MessageRepository;
import java.util.List;
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

    @Autowired
    private CompanyAccessService companyAccessService;

    // Lado da empresa no chat = qualquer membro ACTIVE da empresa dona do match
    // (antes: só o companyUserId de company.getUser()).
    private boolean isParticipant(Match match, Long userId) {
        return userId.equals(match.getProfessional().getUser().getId())
                || companyAccessService.isActiveMember(
                        match.getProject().getCompany().getId(), userId);
    }

    // Faz todas as validacoes se a pessoa tem acesso aquela chat
    // Usado no envio de mensagem e consulta de contagem por match
    public Match validateChatAccess(Long matchId, Long userId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404),
                        "Match not found."));

        if (!isParticipant(match, userId)) {
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

        if (!isParticipant(match, userId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "You are not a participant of this match.");
        }

        return match;
    }

    // Destinatários do "ping" leve de mensagem nova (contador de não lidas) via
    // WebSocket, dado quem enviou. Se o remetente é o profissional, o outro lado
    // é a EMPRESA — e o ping vai para TODOS os membros ACTIVE (mesmo fan-out das
    // notificações operacionais), não só o OWNER primário. Se o remetente é um
    // membro da empresa, o outro lado é só o profissional (sempre 1 pessoa).
    public List<User> chatNotificationRecipients(Match match, Long senderUserId) {
        if (senderUserId.equals(match.getProfessional().getUser().getId())) {
            return companyAccessService.operationalRecipients(match.getProject().getCompany());
        }

        if (companyAccessService.isActiveMember(
                match.getProject().getCompany().getId(), senderUserId)) {
            return List.of(match.getProfessional().getUser());
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
