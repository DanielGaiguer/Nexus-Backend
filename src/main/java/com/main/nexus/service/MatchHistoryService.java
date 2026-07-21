package com.main.nexus.service;

import com.main.nexus.model.Match;
import com.main.nexus.model.MatchHistory;
import com.main.nexus.repository.MatchHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MatchHistoryService {

    @Autowired
    private MatchHistoryRepository matchHistoryRepository;

    public void record(Match match, String fromStatus, String toStatus, String changedBy) {
        MatchHistory history = new MatchHistory();
        history.setMatch(match);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setChangedBy(changedBy);
        matchHistoryRepository.save(history);
    }
}
