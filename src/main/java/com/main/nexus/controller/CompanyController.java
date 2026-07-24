package com.main.nexus.controller;

import com.main.nexus.dto.CompanyDashboardDTO;
import com.main.nexus.dto.CompanyProfileDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.GeolocationService;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.ProjectService;
import com.main.nexus.service.SupabaseStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
    private MatchService matchService;

    @Autowired
    private GeolocationService geolocationService;
    
    @Autowired
    private SupabaseStorageService supabaseStorageService;

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