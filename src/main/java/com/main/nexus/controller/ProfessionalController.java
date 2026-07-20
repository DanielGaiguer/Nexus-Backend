package com.main.nexus.controller;

import com.main.nexus.dto.PreviousProjectRequestDTO;
import com.main.nexus.dto.ProfessionalProfileDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.PreviousProject;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Skill;
import com.main.nexus.service.FileStorageService;
import com.main.nexus.service.GeolocationService;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.PreviousProjectService;
import com.main.nexus.service.ProfessionalService;
import com.main.nexus.service.SkillService;
import com.main.nexus.service.SupabaseStorageService;
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
import org.springframework.web.multipart.MultipartFile;
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
    
    @Autowired
    private GeolocationService geolocationService;
    
    @Autowired
    private FileStorageService fileStorageService;
    
    @Autowired
    private SupabaseStorageService supabaseStorageService;

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
        existing.setCep(request.cep());
        existing.setMinimumSalaryExpectation(request.minimumSalary());
        existing.setMaximumSalaryExpectation(request.maximumSalary());
        existing.setAvailable(request.available());
        existing.setLatitude(request.latitude());
        existing.setLongitude(request.longitude());
        existing.setPreferredTypes(request.preferredTypes());
        existing.setExperienceLevel(request.experienceLevel());
        
        if (request.cep() != null && !request.cep().isBlank()) {
            GeolocationService.AddressData coords = geolocationService.resolveFromCep(request.cep());
            existing.setLatitude(coords.latitude());
            existing.setLongitude(coords.longitude());
            existing.setCity(coords.city());
            existing.setUf(coords.state());
        }

        professionalService.update(existing);
        return ResponseEntity.ok(toProfileDTO(existing));
    }

    @GetMapping("/skills")
    public ResponseEntity<?> listSkills() {
        return ResponseEntity.ok(skillService.findAll());
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
                p.getUf(),
                p.getCep(),
                p.getMinimumSalaryExpectation(),
                p.getMaximumSalaryExpectation(),
                p.getAvailable(),
                p.getReputation(),
                p.getLatitude(),
                p.getLongitude(),
                p.getSkills().stream().map(Skill::getName).toList(),
                p.getPreferredTypes(),
                p.getExperienceLevel(),
                p.getProfilePhotoUrl()
        );
    }
    
    // Lista oportunidades compatíveis — "projetos vistos por mim"
    @GetMapping("/opportunities")
    public ResponseEntity<?> getOpportunities() {
        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Profile not found"));

        return ResponseEntity.ok(matchService.getOpportunitiesForProfessional(professional.getId()));
    }

    // Profissional demonstra interesse em um projeto
    @PostMapping("/opportunities/{projectId}/interest")
    public ResponseEntity<String> showInterestInProject(@PathVariable Long projectId) {
        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Profile not found"));

        matchService.professionalShowsInterest(professional.getId(), projectId);
        return ResponseEntity.ok("Interest sent to company.");
    }

    // Upload de curriculo
    @PostMapping("/resume")
    public ResponseEntity<String> uploadResume(@RequestParam("file") MultipartFile file) {
        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Profile not found"));

        // Remove o currículo anterior se existir
        if (professional.getResume() != null) {
            fileStorageService.deleteResume(professional.getResume());
        }

        String fileName = fileStorageService.storeResume(file, professional.getId());
        professional.setResume(fileName);
        professionalService.update(professional);

        return ResponseEntity.ok("Resume uploaded successfully.");
    }

    // Download do currículo — acessível por COMPANY e PROFESSIONAL autenticados
    @GetMapping("/{professionalId}/resume")
    public ResponseEntity<byte[]> downloadResume(@PathVariable Long professionalId) {
        Professional professional = professionalService.findById(professionalId);

        if (professional.getResume() == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404),
                    "This professional has no resume.");
        }

        byte[] content = fileStorageService.loadResume(professional.getResume());

        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition",
                        "inline; filename=\"curriculo_" + professional.getName().replace(" ", "_") + ".pdf\"")
                .body(content);
    }
    
    @PostMapping("/profile/photo")
    public ResponseEntity<String> uploadProfilePhoto(
            @RequestParam("file") MultipartFile file) {

        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Profile not found"));

        // Remove a foto antiga do bucket se existir
        supabaseStorageService.deleteProfilePhoto(professional.getProfilePhotoUrl());

        String photoUrl = supabaseStorageService.uploadProfilePhoto(
                file, "professionals", professional.getId());

        professional.setProfilePhotoUrl(photoUrl);
        professionalService.update(professional);

        return ResponseEntity.ok(photoUrl);
    }

    @DeleteMapping("/profile/photo")
    public ResponseEntity<String> deleteProfilePhoto() {
        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Profile not found"));

        supabaseStorageService.deleteProfilePhoto(professional.getProfilePhotoUrl());
        professional.setProfilePhotoUrl(null);
        professionalService.update(professional);

        return ResponseEntity.ok("Profile photo removed.");
    }
}