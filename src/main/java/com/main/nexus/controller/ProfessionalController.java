package com.main.nexus.controller;

import com.main.nexus.dto.ContactInfoDTO;
import com.main.nexus.dto.MatchResponseDTO;
import com.main.nexus.dto.PreviousProjectDTO;
import com.main.nexus.dto.ProfessionalProfileDTO;
import com.main.nexus.dto.ProfessionalSummaryDTO;
import com.main.nexus.dto.ProjectResponseDTO;
import com.main.nexus.dto.PublicCompanyDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Match;
import com.main.nexus.model.PreviousProject;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.ReputationMetrics;
import com.main.nexus.model.Skill;
import com.main.nexus.model.enums.NotificationType;
import com.main.nexus.repository.PreviousProjectRepository;
import com.main.nexus.repository.ReputationMetricsRepository;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.EmailService;
import com.main.nexus.service.GeolocationService;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.NotificationService;
import com.main.nexus.service.PreviousProjectService;
import com.main.nexus.service.PdfService;
import com.main.nexus.service.ProfessionalService;
import com.main.nexus.service.ProfileCompletionService;
import com.main.nexus.service.ProjectResponseAssembler;
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
    private CompanyService companyService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private SkillService skillService;
    
    @Autowired
    private PreviousProjectService previousProjectService;
    
    @Autowired
    private GeolocationService geolocationService;
    
    @Autowired
    private SupabaseStorageService supabaseStorageService;

    @Autowired
    private PdfService pdfService;

    @Autowired
    private ReputationMetricsRepository reputationMetricsRepository;

    @Autowired
    private PreviousProjectRepository previousProjectRepository;
    
    @Autowired
    private ProfileCompletionService profileCompletionService;
    
    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ProjectResponseAssembler projectResponseAssembler;

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
            @RequestBody PreviousProjectDTO request) {
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

    @PutMapping("/projects/{projectId}")
    public ResponseEntity<String> updatePreviousProject(
            @PathVariable Long projectId,
            @RequestBody PreviousProjectDTO request) {
        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Profile not found"));
        previousProjectService.update(projectId, professional.getId(), request);
        return ResponseEntity.ok("Previous project updated.");
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
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Profile not found"));

        boolean wasIncomplete = !profileCompletionService.isProfileComplete(existing);

        existing.setName(request.name());
        existing.setPhone(request.phone());
        existing.setAvailable(request.available());
        existing.setPreferredTypes(request.preferredTypes());
        existing.setExperienceLevel(request.experienceLevel());
        existing.setPreferredOpportunityTypes(request.preferredOpportunityTypes());
        existing.setExpectedSalaryCLT(request.expectedSalaryCLT());
        existing.setExpectedSalaryPJ(request.expectedSalaryPJ());
        existing.setFreelanceMinExpectation(request.freelanceMinExpectation());
        existing.setFreelanceMaxExpectation(request.freelanceMaxExpectation());
        existing.setLinkedinUrl(request.linkedinUrl());

        if (request.cep() != null && !request.cep().isBlank()) {
            GeolocationService.AddressData address = geolocationService.resolveFromCep(request.cep());
            existing.setLatitude(address.latitude());
            existing.setLongitude(address.longitude());
            existing.setCity(address.city());
            existing.setUf(address.state());
        }

        professionalService.update(existing);

        // Se o perfil estava incompleto e agora está completo, notifica
        boolean nowComplete = profileCompletionService.isProfileComplete(existing);
        if (wasIncomplete && nowComplete) {
            notificationService.notify(
                existing.getUser(),
                    NotificationType.COMPLETE_YOUR_PROFILE,
                "Perfil completo!",
                "Parabéns! Seu perfil está completo e você agora aparece nos rankings de oportunidades compatíveis.",
                "/pro/profile"
            );
        }

        // Se ainda incompleto lembra o que fatla
        if (!nowComplete) {
            List<String> missing = profileCompletionService.getMissingFields(existing);
            notificationService.notifyIncompleteProfile(existing.getUser(), missing);
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
    public ResponseEntity<List<MatchResponseDTO>> getMatches() {
        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        return ResponseEntity.ok(matchService.getMatchesByProfessional(professional.getId())
                .stream().map(this::toMatchResponseDTO).toList());
    }

    @GetMapping("/matches/invites")
    public ResponseEntity<List<MatchResponseDTO>> getPendingInvites() {
        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        return ResponseEntity.ok(
                matchService.getPendingInvitesForProfessional(professional.getId())
                        .stream().map(this::toMatchResponseDTO).toList());
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

    private MatchResponseDTO toMatchResponseDTO(Match m) {
        Professional professional = m.getProfessional();
        return new MatchResponseDTO(
                m.getId(),
                m.getMatchScore(),
                m.getCompanyStatus(),
                m.getProfessionalStatus(),
                m.getStatus(),
                m.getCreatedAt(),
                toProjectResponseDTO(m.getProject()),
                new ProfessionalSummaryDTO(
                        professional.getId(),
                        professional.getName(),
                        professional.getPhone(),
                        professional.getReputation(),
                        professional.getProfilePhotoUrl()
                ),
                matchService.getScoreBreakdown(professional, m.getProject(), m),
                m.getActive()
        );
    }

    private ProjectResponseDTO toProjectResponseDTO(Project p) {
        Company c = p.getCompany();
        PublicCompanyDTO companyDTO = new PublicCompanyDTO(
                c.getId(),
                c.getCompanyName(),
                c.getDescription(),
                c.getCity(),
                c.getUf(),
                c.getReputation(),
                c.getProfilePhotoUrl(),
                null,
                c.getTaxId(),
                c.getStatus().name(),
                List.of(),
                c.getUser() != null ? c.getUser().getEmail() : null
        );
        return projectResponseAssembler.toDTO(p, ProjectResponseAssembler.Viewer.professional(), companyDTO);
    }

    private ProfessionalProfileDTO toProfileDTO(Professional p) {
        List<String> missing = profileCompletionService.getMissingFields(p);
        return new ProfessionalProfileDTO(
                p.getId(),
                p.getName(),
                p.getUser().getEmail(),
                p.getPhone(),
                p.getCity(),
                p.getUf(),
                null,
                p.getAvailable(),
                p.getReputation(),
                p.getLatitude(),
                p.getLongitude(),
                p.getSkills().stream().map(Skill::getName).toList(),
                p.getPreferredTypes(),
                p.getExperienceLevel(),
                p.getProfilePhotoUrl(),
                p.getPreferredOpportunityTypes(),
                p.getExpectedSalaryCLT(),
                p.getExpectedSalaryPJ(),
                p.getFreelanceMinExpectation(),
                p.getFreelanceMaxExpectation(),
                missing.isEmpty(),
                missing,
                p.getLinkedinUrl(),
                p.getResume()
        );
    }

    // Lista oportunidades compatíveis , projetos vistos por mim
    @GetMapping("/opportunities")
    public ResponseEntity<List<MatchResponseDTO>> getOpportunities() {
        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Profile not found"));

        return ResponseEntity.ok(matchService.getOpportunitiesForProfessional(professional.getId())
                .stream().map(this::toMatchResponseDTO).toList());
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

        // Remove o curriculo anterior do bucket se existir
        supabaseStorageService.deleteResume(professional.getResume());

        String resumeUrl = supabaseStorageService.uploadResume(file, professional.getId());
        professional.setResume(resumeUrl);
        professionalService.update(professional);

        return ResponseEntity.ok("Resume uploaded successfully.");
    }

    // download do curriculo - acessivel pelo proprio profissional, ou por empresa
    // com match confirmado com ele (mesma regra do getContact, logo abaixo)
    @GetMapping("/{professionalId}/resume")
    public ResponseEntity<byte[]> downloadResume(@PathVariable Long professionalId) {
        Professional professional = professionalService.findById(professionalId);

        UserDTO logged = getLoggedUser();
        boolean isOwner = professional.getUser().getId().equals(logged.id());

        if (!isOwner) {
            Company company = companyService.findByUserId(logged.id())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatusCode.valueOf(403),
                            "Resume is only available to the professional or a company with a confirmed match."));

            if (!matchService.hasConfirmedMatchBetween(company.getId(), professionalId)) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                        "Resume is only available after a confirmed match.");
            }
        }

        if (professional.getResume() == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404),
                    "This professional has no resume.");
        }

        byte[] content = supabaseStorageService.downloadResume(professional.getResume());

        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition",
                        "inline; filename=\"curriculo_" + professional.getName().replace(" ", "_") + ".pdf\"")
                .body(content);
    }
    
    // Contato so liberado para empresa com match confirmado
    @GetMapping("/{professionalId}/contact")
    public ResponseEntity<ContactInfoDTO> getContact(@PathVariable Long professionalId) {
        UserDTO logged = getLoggedUser();
        Company company = companyService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Company profile not found"));

        Professional professional = professionalService.findById(professionalId);

        if (!matchService.hasConfirmedMatchBetween(company.getId(), professionalId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "Contact info is only available after a confirmed match.");
        }

        return ResponseEntity.ok(new ContactInfoDTO(
                professional.getPhone(), professional.getUser().getEmail()));
    }

    @PostMapping("/profile/photo")
    public ResponseEntity<String> uploadProfilePhoto(
            @RequestParam("file") MultipartFile file) {

        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Profile not found"));

        // remove a foto antiga do bucket se existir
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