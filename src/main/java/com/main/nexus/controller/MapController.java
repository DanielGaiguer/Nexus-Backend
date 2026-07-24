package com.main.nexus.controller;

import com.main.nexus.dto.MapCompanyDTO;
import com.main.nexus.dto.MapProfessionalDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.Skill;
import com.main.nexus.model.enums.ProjectStatus;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.repository.ProjectRepository;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

        List<MapCompanyDTO> result = all.stream()
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
                        (int) projectRepository.findByCompanyId(c.getId())
                                .stream()
                                .filter(p -> p.getStatus() == ProjectStatus.OPEN)
                                .count()
                ))
                .toList();

        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/opportunities")
    public ResponseEntity<?> getOpportunities(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) String type) {

        List<Project> all = projectRepository.findByStatus(ProjectStatus.OPEN);

        return ResponseEntity.ok(
            all.stream()
                .filter(p -> type == null
                        || p.getOpportunityType().name().equalsIgnoreCase(type))
                .filter(p -> p.getEffectiveLatitude() != null && p.getEffectiveLongitude() != null)
                .filter(p -> {
                    if (lat == null || lng == null || radiusKm == null) return true;
                    return haversineDistance(lat, lng,
                            p.getEffectiveLatitude(),
                            p.getEffectiveLongitude()) <= radiusKm;
                })
                .map(p -> Map.of(
                        "id", p.getId(),
                        "opportunityType", p.getOpportunityType().name(),
                        "title", p.getTitle(),
                        "companyName", p.getCompany().getCompanyName(),
                        "city", p.getEffectiveCity() != null ? p.getEffectiveCity() : "",
                        "uf", p.getEffectiveUf() != null ? p.getEffectiveUf() : "",
                        "latitude", p.getEffectiveLatitude(),
                        "longitude", p.getEffectiveLongitude(),
                        "workMode", p.getWorkMode() != null ? p.getWorkMode().name() : null,
                        "requiredSkills", p.getRequiredSkills().stream().map(Skill::getName).toList()
                ))
                .toList()
        );
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