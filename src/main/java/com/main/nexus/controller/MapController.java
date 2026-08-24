package com.main.nexus.controller;

import com.main.nexus.dto.MapCompanyDTO;
import com.main.nexus.dto.MapOpportunityDTO;
import com.main.nexus.dto.MapProfessionalDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.Skill;
import com.main.nexus.model.enums.CompanyStatus;
import com.main.nexus.model.enums.ProjectStatus;
import com.main.nexus.model.enums.UserType;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.service.CompanyService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/map")
public class MapController {

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private CompanyService companyService;

    @GetMapping("/professionals")
    public ResponseEntity<List<MapProfessionalDTO>> getProfessionals(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String uf) {

        List<Professional> all = professionalRepository.findAll();

        List<MapProfessionalDTO> result = all.stream()
                .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                .filter(p -> {
                    if (lat == null || lng == null || radiusKm == null) return true;
                    return haversineDistance(lat, lng, p.getLatitude(), p.getLongitude()) <= radiusKm;
                })
                .filter(p -> {
                    if (city != null && !city.isBlank())
                        return city.equalsIgnoreCase(p.getCity());
                    return true;
                })
                .filter(p -> {
                    if (uf != null && !uf.isBlank())
                        return uf.equalsIgnoreCase(p.getUf());
                    return true;
                })
                .map(p -> new MapProfessionalDTO(
                        p.getId(),
                        p.getName(),
                        p.getCity(),
                        p.getUf(),
                        p.getLatitude(),
                        p.getLongitude(),
                        p.getReputation(),
                        p.getExperienceLevel() != null ? p.getExperienceLevel().name() : null,
                        p.getSkills().stream().map(Skill::getName).toList()
                ))
                .toList();

        return ResponseEntity.ok(result);
    }


    @GetMapping("/companies")
    public ResponseEntity<List<MapCompanyDTO>> getCompanies(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String uf) {

        List<Company> all = companyRepository.findAll();
        boolean isAdmin = isViewerAdmin();

        // Uma única query agregada (GROUP BY company_id) em vez de uma consulta por empresa (N+1)
        Map<Long, Integer> openProjectsByCompanyId = new HashMap<>();
        for (Object[] row : projectRepository.countByStatusGroupedByCompany(ProjectStatus.OPEN)) {
            openProjectsByCompanyId.put((Long) row[0], ((Long) row[1]).intValue());
        }

        List<MapCompanyDTO> result = all.stream()
                // mesma regra do PublicProfessionalController: só empresas aprovadas ficam visíveis,
                // exceto para o admin, que precisa ver pendentes/rejeitadas para moderação
                .filter(c -> isAdmin || c.getStatus() == CompanyStatus.APPROVED)
                .filter(c -> c.getLatitude() != null && c.getLongitude() != null)
                .filter(c -> {
                    if (lat == null || lng == null || radiusKm == null) return true;
                    return haversineDistance(lat, lng, c.getLatitude(), c.getLongitude()) <= radiusKm;
                })
                .filter(c -> {
                    if (city != null && !city.isBlank())
                        return city.equalsIgnoreCase(c.getCity());
                    return true;
                })
                .filter(c -> {
                    if (uf != null && !uf.isBlank())
                        return uf.equalsIgnoreCase(c.getUf());
                    return true;
                })
                .map(c -> new MapCompanyDTO(
                        c.getId(),
                        c.getCompanyName(),
                        c.getCity(),
                        c.getUf(),
                        c.getLatitude(),
                        c.getLongitude(),
                        c.getReputation(),
                        openProjectsByCompanyId.getOrDefault(c.getId(), 0),
                        c.getType().name()
                ))
                .toList();

        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/opportunities")
    public ResponseEntity<List<MapOpportunityDTO>> getOpportunities(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) String type) {

        List<Project> all = projectRepository.findByStatus(ProjectStatus.OPEN);
        Long viewerCompanyId = resolveViewerCompanyId();

        List<MapOpportunityDTO> result = all.stream()
                // some do mapa para outras empresas quando marcada como não visível para elas
                // (não afeta profissionais nem o admin)
                .filter(p -> !Boolean.FALSE.equals(p.getVisibleToCompanies())
                        || viewerCompanyId == null
                        || viewerCompanyId.equals(p.getCompany().getId()))
                .filter(p -> type == null
                        || p.getOpportunityType().name().equalsIgnoreCase(type))
                .filter(p -> p.getEffectiveLatitude() != null && p.getEffectiveLongitude() != null)
                .filter(p -> {
                    if (lat == null || lng == null || radiusKm == null) return true;
                    return haversineDistance(lat, lng,
                            p.getEffectiveLatitude(),
                            p.getEffectiveLongitude()) <= radiusKm;
                })
                .map(p -> new MapOpportunityDTO(
                        p.getId(),
                        p.getOpportunityType().name(),
                        p.getTitle(),
                        p.getCompany().getCompanyName(),
                        p.getEffectiveCity() != null ? p.getEffectiveCity() : "",
                        p.getEffectiveUf() != null ? p.getEffectiveUf() : "",
                        p.getEffectiveLatitude(),
                        p.getEffectiveLongitude(),
                        p.getWorkMode() != null ? p.getWorkMode().name() : null,
                        p.getRequiredSkills().stream().map(Skill::getName).toList(),
                        p.getExperienceLevel() != null ? p.getExperienceLevel().name() : null,
                        p.getType() != null ? p.getType().name() : null,
                        p.getContractType() != null ? p.getContractType().name() : null,
                        p.getMonthlySalaryMin(),
                        p.getMonthlySalaryMax(),
                        p.getMinimumBudget(),
                        p.getMaximumBudget(),
                        p.getCreatedAt()
                ))
                .toList();

        return ResponseEntity.ok(result);
    }


    // Retorna o ID da empresa logada, ou null se quem está olhando não é uma empresa
    // (profissional ou admin) — usado só para a regra de "oculto para outras empresas".
    private Long resolveViewerCompanyId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDTO logged)) {
            return null;
        }
        if (!UserType.COMPANY.name().equals(logged.role())) {
            return null;
        }
        return companyService.findByUserId(logged.id()).map(Company::getId).orElse(null);
    }

    // Indica se quem está olhando o mapa é o admin — usado para liberar empresas
    // PENDING/REJECTED, que ficam ocultas dos demais papéis.
    private boolean isViewerAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDTO logged)) {
            return false;
        }
        return UserType.ADMIN.name().equals(logged.role());
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS_KM = 6371.0;

        double latRad1 = Math.toRadians(lat1);
        double latRad2 = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                 + Math.cos(latRad1) * Math.cos(latRad2)
                 * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }
}