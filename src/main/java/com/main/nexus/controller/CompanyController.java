package com.main.nexus.controller;

import com.main.nexus.dto.CompanyDashboardDTO;
import com.main.nexus.dto.CompanyProfileDTO;
import com.main.nexus.dto.ContactInfoDTO;
import com.main.nexus.dto.ProjectResponseDTO;
import com.main.nexus.dto.PublicCompanyDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Professional;
import com.main.nexus.model.enums.NotificationType;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.GeolocationService;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.NotificationService;
import com.main.nexus.service.ProfessionalService;
import com.main.nexus.service.ProfileCompletionService;
import com.main.nexus.service.ProjectResponseAssembler;
import com.main.nexus.service.ProjectService;
import com.main.nexus.service.SupabaseStorageService;
import java.util.List;
import java.util.Optional;
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
@RequestMapping("/api/company")
public class CompanyController {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProfessionalService professionalService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private GeolocationService geolocationService;

    @Autowired
    private SupabaseStorageService supabaseStorageService;

    @Autowired
    private ProfileCompletionService profileCompletionService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ProjectResponseAssembler projectResponseAssembler;

    @GetMapping("/profile")
    public ResponseEntity<CompanyProfileDTO> getProfile() {
        UserDTO logged = getLoggedUser();
        Company company = companyService.findByUserId(logged.id())
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        return ResponseEntity.ok(toProfileDTO(company));
    }

    @PutMapping("/profile")
    public ResponseEntity<CompanyProfileDTO> updateProfile(
            @RequestBody CompanyProfileDTO request) {
        UserDTO logged = getLoggedUser();
        Company existing = companyService.findByUserId(logged.id())
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        if (!existing.getTaxId().equals(request.taxId()) && companyService.existsByTaxId(request.taxId())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409), "Tax ID already in use.");
        }

        boolean wasIncomplete = !profileCompletionService.isProfileComplete(existing);

        existing.setCompanyName(request.companyName());
        existing.setPhone(request.phone());
        existing.setCep(request.cep());
        existing.setDescription(request.description());
        existing.setLinkedinUrl(request.linkedinUrl());

        if (request.cep() != null && !request.cep().isBlank()) {
            GeolocationService.AddressData address = geolocationService.resolveFromCep(request.cep());
            existing.setLatitude(address.latitude());
            existing.setLongitude(address.longitude());
            existing.setCity(address.city());
            existing.setUf(address.state());
        }

        companyService.update(existing);

        // Se o perfil estava incompleto e agora está completo notifica
        boolean nowComplete = profileCompletionService.isProfileComplete(existing);
        if (wasIncomplete && nowComplete) {
            notificationService.notify(
                existing.getUser(),
                NotificationType.COMPLETE_YOUR_PROFILE,
                "Perfil completo!",
                "Parabéns! O perfil da sua empresa está completo, o que melhora a qualidade dos matches com profissionais.",
                "/company/profile"
            );
        }

        // Se ainda incompleto lembra o que falta
        if (!nowComplete) {
            List<String> missing = profileCompletionService.getMissingFields(existing);
            notificationService.notifyIncompleteCompanyProfile(existing.getUser(), missing);
        }

        return ResponseEntity.ok(toProfileDTO(existing));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<CompanyDashboardDTO> dashboard() {
        UserDTO logged = getLoggedUser();
        Company company = companyService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Company not found"));

        return ResponseEntity.ok(new CompanyDashboardDTO(
                toProfileDTO(company),
                projectService.findByCompany(company).size(),
                matchService.countConfirmedMatchesByCompany(company.getId())
        ));
    }

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    // Vitrine de todas as oportunidades da plataforma, para a company navegar o mercado.
    // Oportunidades marcadas como não visíveis para outras empresas ficam de fora, exceto
    // as da própria empresa logada.
    @GetMapping("/opportunities")
    public ResponseEntity<List<ProjectResponseDTO>> listAllOpportunities() {
        Company company = getLoggedCompany();
        ProjectResponseAssembler.Viewer viewer = ProjectResponseAssembler.Viewer.company(company.getId());

        List<ProjectResponseDTO> projects = projectService.findAllOpen()
                .stream()
                .map(p -> projectResponseAssembler.toVisibleDTO(p, viewer, toPublicCompanyDTO(p.getCompany())))
                .flatMap(Optional::stream)
                .toList();
        return ResponseEntity.ok(projects);
    }

    private Company getLoggedCompany() {
        UserDTO logged = getLoggedUser();
        return companyService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Company not found"));
    }

    private PublicCompanyDTO toPublicCompanyDTO(Company c) {
        return new PublicCompanyDTO(
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
    }

    // Contato só liberado para profissional com match confirmado
    @GetMapping("/{companyId}/contact")
    public ResponseEntity<ContactInfoDTO> getContact(@PathVariable Long companyId) {
        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Professional profile not found"));

        Company company = companyService.findById(companyId);

        if (!matchService.hasConfirmedMatchBetween(companyId, professional.getId())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "Contact info is only available after a confirmed match.");
        }

        return ResponseEntity.ok(new ContactInfoDTO(
                company.getPhone(), company.getUser().getEmail()));
    }

    @PostMapping("/profile/photo")
    public ResponseEntity<String> uploadProfilePhoto(
            @RequestParam("file") MultipartFile file) {

        UserDTO logged = getLoggedUser();
        Company company = companyService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Company not found"));

        supabaseStorageService.deleteProfilePhoto(company.getProfilePhotoUrl());

        String photoUrl = supabaseStorageService.uploadProfilePhoto(
                file, "companies", company.getId());

        company.setProfilePhotoUrl(photoUrl);
        companyService.update(company);

        return ResponseEntity.ok(photoUrl);
    }

    @DeleteMapping("/profile/photo")
    public ResponseEntity<String> deleteProfilePhoto() {
        UserDTO logged = getLoggedUser();
        Company company = companyService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Company not found"));

        supabaseStorageService.deleteProfilePhoto(company.getProfilePhotoUrl());
        company.setProfilePhotoUrl(null);
        companyService.update(company);

        return ResponseEntity.ok("Profile photo removed.");
    }

    private CompanyProfileDTO toProfileDTO(Company c) {
        return new CompanyProfileDTO(
                c.getId(),
                c.getCompanyName(),
                c.getUser().getEmail(),
                c.getTaxId(),
                c.getPhone(),
                c.getCity(),
                c.getUf(),
                c.getCep(),
                c.getDescription(),
                c.getReputation(),
                c.getLatitude(),
                c.getLongitude(),
                c.getStatus().name(),
                c.getProfilePhotoUrl(),
                c.getLinkedinUrl()
        );
    }
}