package com.main.nexus.controller;

import com.main.nexus.dto.UserSummaryDTO;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.UserRepository;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.SkillService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private SkillService skillService;

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        Map<String, Object> data = new HashMap<>();
        data.put("totalUsers",       userRepository.count());
        data.put("totalProjects",    projectRepository.count());
        data.put("totalMatches",     matchService.countConfirmedMatches());
        data.put("pendingCompanies", companyService.findPending().size());
        return ResponseEntity.ok(data);
    }

    @GetMapping("/companies/pending")
    public ResponseEntity<?> pendingCompanies() {
        return ResponseEntity.ok(companyService.findPending());
    }

    @PostMapping("/companies/{id}/approve")
    public ResponseEntity<String> approveCompany(@PathVariable Long id) {
        companyService.approve(id);
        return ResponseEntity.ok("Company approved.");
    }

    @PostMapping("/companies/{id}/reject")
    public ResponseEntity<String> rejectCompany(@PathVariable Long id) {
        companyService.reject(id);
        return ResponseEntity.ok("Company rejected.");
    }

    @GetMapping("/skills")
    public ResponseEntity<?> listSkills() {
        return ResponseEntity.ok(skillService.findAll());
    }

    @PostMapping("/skills")
    public ResponseEntity<String> createSkill(
            @RequestParam String name,
            @RequestParam(required = false) String category) {
        skillService.create(name, category);
        return ResponseEntity.ok("Skill created.");
    }

    @DeleteMapping("/skills/{id}")
    public ResponseEntity<String> deleteSkill(@PathVariable Long id) {
        skillService.delete(id);
        return ResponseEntity.ok("Skill deleted.");
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserSummaryDTO>> listUsers() {
        List<UserSummaryDTO> users = userRepository.findAll()
                .stream()
                .map(u -> new UserSummaryDTO(
                        u.getId(),
                        u.getEmail(),
                        u.getType().name(),
                        u.getActive()))
                .toList();
        return ResponseEntity.ok(users);
    }

    @PostMapping("/users/{id}/toggle")
    public ResponseEntity<String> toggleUser(@PathVariable Long id) {
        userRepository.findById(id).ifPresent(user -> {
            user.setActive(!user.getActive());
            userRepository.save(user);
        });
        return ResponseEntity.ok("User status updated.");
    }
}