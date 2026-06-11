package com.main.nexus.controller;

import com.main.nexus.dto.PreviousProjectRequestDTO;
import com.main.nexus.dto.ProfessionalProfileDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.PreviousProject;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Skill;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.PreviousProjectService;
import com.main.nexus.service.ProfessionalService;
import com.main.nexus.service.SkillService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/professional")
public class ProfessionalController {

    @Autowired
    private ProfessionalService professionalService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private SkillService skillService;
    
    @Autowired
    private PreviousProjectService previousProjectService;

    @GetMapping("/projects")
    public ResponseEntity<?> listPreviousProjects() {
        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Profile not found"));
        return ResponseEntity.ok(previousProjectService.findByProfessional(professional.getId()));
    }

    @PostMapping("/projects")
    public ResponseEntity<String> addPreviousProject(
            @RequestBody PreviousProjectRequestDTO request) {
        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Profile not found"));

        PreviousProject project = new PreviousProject();
        project.setProfessional(professional);
        project.setTitle(request.title());
        project.setDescription(request.description());
        project.setTechnologies(request.technologies());
        project.setYearOfCompletion(request.yearOfCompletion());
        previousProjectService.save(project);

        return ResponseEntity.ok("Previous project added.");
    }

    @DeleteMapping("/projects/{projectId}")
    public ResponseEntity<String> deletePreviousProject(@PathVariable Long projectId) {
        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Profile not found"));
        previousProjectService.delete(projectId, professional.getId());
        return ResponseEntity.ok("Previous project deleted.");
    }

    @GetMapping("/profile")
    public ResponseEntity<ProfessionalProfileDTO> getProfile() {
        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        return ResponseEntity.ok(toProfileDTO(professional));
    }

    @PutMapping("/profile")
    public ResponseEntity<ProfessionalProfileDTO> updateProfile(
            @RequestBody ProfessionalProfileDTO request) {
        UserDTO logged = getLoggedUser();
        Professional existing = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        existing.setName(request.name());
        existing.setPhone(request.phone());
        existing.setCity(request.city());
        existing.setMinimumSalaryExpectation(request.minimumSalary());
        existing.setMaximumSalaryExpectation(request.maximumSalary());
        existing.setAvailable(request.available());
        existing.setLatitude(request.latitude());
        existing.setLongitude(request.longitude());

        professionalService.update(existing);
        return ResponseEntity.ok(toProfileDTO(existing));
    }

    @PutMapping("/skills")
    public ResponseEntity<String> updateSkills(@RequestBody List<Long> skillIds) {
        UserDTO logged = getLoggedUser();
        Professional existing = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        List<Skill> skills = skillService.findAllById(skillIds);
        existing.setSkills(skills);
        professionalService.update(existing);

        return ResponseEntity.ok("Skills updated successfully.");
    }

    @GetMapping("/matches")
    public ResponseEntity<?> getMatches() {
        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        return ResponseEntity.ok(matchService.getMatchesByProfessional(professional.getId()));
    }

    @GetMapping("/matches/invites")
    public ResponseEntity<?> getPendingInvites() {
        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        return ResponseEntity.ok(
                matchService.getPendingInvitesForProfessional(professional.getId()));
    }

    @PostMapping("/matches/{matchId}/accept")
    public ResponseEntity<String> acceptMatch(@PathVariable Long matchId) {
        matchService.professionalAccepts(matchId);
        return ResponseEntity.ok("Match confirmed! Contact details are now available.");
    }

    @PostMapping("/matches/{matchId}/reject")
    public ResponseEntity<String> rejectMatch(
            @PathVariable Long matchId,
            @RequestParam String reason) {
        matchService.professionalRejectsWithFeedback(matchId, reason);
        return ResponseEntity.ok("Invite rejected.");
    }

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    private ProfessionalProfileDTO toProfileDTO(Professional p) {
        return new ProfessionalProfileDTO(
                p.getId(),
                p.getName(),
                p.getUser().getEmail(),
                p.getPhone(),
                p.getCity(),
                p.getMinimumSalaryExpectation(),
                p.getMaximumSalaryExpectation(),
                p.getAvailable(),
                p.getReputation(),
                p.getLatitude(),
                p.getLongitude(),
                p.getSkills().stream().map(Skill::getName).toList()
        );
    }
}