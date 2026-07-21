package com.main.nexus.controller;

import com.main.nexus.dto.PreviousProjectRequestDTO;
import com.main.nexus.dto.ProfessionalProfileDTO;
import com.main.nexus.dto.ProfessionalStatsDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.PreviousProject;
import com.main.nexus.model.Professional;
import com.main.nexus.model.ReputationMetrics;
import com.main.nexus.model.Skill;
import com.main.nexus.repository.PreviousProjectRepository;
import com.main.nexus.repository.ReputationMetricsRepository;
import com.main.nexus.service.EmailService;
import com.main.nexus.service.FileStorageService;
import com.main.nexus.service.GeolocationService;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.PreviousProjectService;
import com.main.nexus.service.PdfService;
import com.main.nexus.service.ProfessionalService;
import com.main.nexus.service.SkillService;
import com.main.nexus.service.SupabaseStorageService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
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

    @Autowired
    private EmailService emailService;

    @Autowired
    private PdfService pdfService;

    @Autowired
    private ReputationMetricsRepository reputationMetricsRepository;

    @Autowired
    private PreviousProjectRepository previousProjectRepository;

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

        boolean hasPhoto = existing.getProfilePhotoUrl() != null && !existing.getProfilePhotoUrl().isBlank();
        boolean hasSkills = existing.getSkills() != null && existing.getSkills().size() >= 3;
        boolean hasCep = existing.getCep() != null && !existing.getCep().isBlank();
        boolean hasExp = existing.getExperienceLevel() != null;
        boolean hasSalary = existing.getMinimumSalaryExpectation() != null && existing.getMinimumSalaryExpectation() > 0;
        boolean hasPrevPr = existing.getProjects() != null && !existing.getProjects().isEmpty();
        boolean complete = hasPhoto && hasSkills && hasCep && hasExp && hasSalary && hasPrevPr;

        if (complete && !Boolean.TRUE.equals(existing.getProfileCompletionEmailSent())) {
            emailService.send(
                    existing.getUser().getEmail(),
                    "Seu perfil está completo! — Nexus",
                    "Olá " + existing.getName() + ",\n\n" +
                    "Parabéns! Seu perfil está completo e você agora aparece com destaque no " +
                    "ranking das vagas compatíveis com seu perfil.\n\n" +
                    "Acesse o Nexus para ver suas oportunidades: http://localhost:8080/pro/opportunities\n\n" +
                    "Equipe Nexus"
            );
            existing.setProfileCompletionEmailSent(true);
            professionalService.update(existing);
        }

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

    @GetMapping("/stats")
    public ResponseEntity<ProfessionalStatsDTO> getStats(
            @RequestParam(required = false) Long professionalId) {
        Professional professional;
        if (professionalId != null) {
            professional = professionalService.findById(professionalId);
        } else {
            UserDTO logged = getLoggedUser();
            professional = professionalService.findByUserId(logged.id())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatusCode.valueOf(404), "Profile not found"));
        }
        return ResponseEntity.ok(professionalService.getStats(professional.getId()));
    }

    @GetMapping("/profile/export")
    @ResponseBody
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) Long professionalId) {
        Professional professional;
        if (professionalId != null) {
            professional = professionalService.findById(professionalId);
        } else {
            UserDTO logged = getLoggedUser();
            professional = professionalService.findByUserId(logged.id())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatusCode.valueOf(404), "Profile not found"));
        }

        List<PreviousProject> projects = previousProjectRepository
                .findByProfessionalId(professional.getId());
        ReputationMetrics reputation = reputationMetricsRepository
                .findByProfessionalId(professional.getId()).orElse(null);

        byte[] pdf = pdfService.generateProfessionalProfile(professional, projects, reputation);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"perfil-nexus.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
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