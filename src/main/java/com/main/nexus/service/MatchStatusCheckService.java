package com.main.nexus.service;

import com.main.nexus.model.Match;
import com.main.nexus.model.MatchStatusCheck;
import com.main.nexus.model.PreviousProject;
import com.main.nexus.model.Project;
import com.main.nexus.model.Skill;
import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.MatchOutcome;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.MatchStatusCheckRepository;
import com.main.nexus.repository.PreviousProjectRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MatchStatusCheckService {

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchStatusCheckRepository matchStatusCheckRepository;

    @Autowired
    private PreviousProjectRepository previousProjectRepository;

    @Transactional
    public MatchStatusCheck answerStatusCheck(Long matchId, Long companyId, MatchOutcome outcome) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Match not found: " + matchId));

        if (!match.getProject().getCompany().getId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "This match does not belong to your company.");
        }

        if (match.getStatus() != StatusMatch.MATCHED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "This match is not a confirmed match.");
        }

        if (matchStatusCheckRepository.existsByMatchId(matchId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "This match has already been answered.");
        }

        MatchStatusCheck statusCheck = new MatchStatusCheck();
        statusCheck.setMatch(match);
        statusCheck.setAnsweredBy(AuthorType.COMPANY);
        statusCheck.setOutcome(outcome);
        MatchStatusCheck saved = matchStatusCheckRepository.save(statusCheck);

        if (outcome == MatchOutcome.WORKING_TOGETHER) {
            addProjectToProfessionalPortfolio(match);
        }

        return saved;
    }

    private void addProjectToProfessionalPortfolio(Match match) {
        Project project = match.getProject();

        PreviousProject previousProject = new PreviousProject();
        previousProject.setProfessional(match.getProfessional());
        previousProject.setTitle(project.getTitle());
        previousProject.setDescription(project.getDescription());
        previousProject.setTechnologies(project.getRequiredSkills().stream()
                .map(Skill::getName)
                .toList());
        previousProject.setYearOfCompletion(LocalDate.now().getYear());

        previousProjectRepository.save(previousProject);
    }
}
