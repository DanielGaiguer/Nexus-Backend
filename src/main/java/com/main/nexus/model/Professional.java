package com.main.nexus.model;

import com.main.nexus.model.enums.ExperienceLevel;
import com.main.nexus.model.enums.OpportunityType;
import com.main.nexus.model.enums.ProjectType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tb_professional")
public class Professional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String city;
 
    @Column(length = 3)
    private String uf;
    
    @Column(length = 9)
    private String cep;

    @Column(nullable = false)
    private Boolean available = true;

    // Nula ate a primeira avaliacao recebida — nao confundir "sem nota" com "nota 0"
    @Column
    private Double reputation;

    private Double latitude;
    private Double longitude;

    private String resume; 
    
    @ElementCollection(targetClass = ProjectType.class) 
    @CollectionTable( 
        name = "tb_professional_project_type", 
        joinColumns = @JoinColumn(name = "professional_id") 
    )
    @Enumerated(EnumType.STRING) 
    @Column(name = "project_type", length = 20)
    private List<ProjectType> preferredTypes = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "tb_professional_skill",
        joinColumns = @JoinColumn(name = "professional_id"),
        inverseJoinColumns = @JoinColumn(name = "skill_id")
    )
    private List<Skill> skills = new ArrayList<>();

    @OneToMany(mappedBy = "professional", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PreviousProject> projects = new ArrayList<>();
    
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ExperienceLevel experienceLevel;
    
    @Column(length = 500)
    private String profilePhotoUrl;
    
    @Column
    private Double expectedSalaryCLT;

    @Column
    private Double expectedSalaryPJ;

    @Column
    private Double freelanceMinExpectation;

    @Column
    private Double freelanceMaxExpectation;

    @ElementCollection(targetClass = OpportunityType.class)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
        name = "tb_professional_opportunity_types",
        joinColumns = @JoinColumn(name = "professional_id")
    )
    @Column(name = "opportunity_type", length = 20)
    private List<OpportunityType> preferredOpportunityTypes = new ArrayList<>();

    private Boolean profileCompletionEmailSent = false;

    @Column(length = 255)
    private String linkedinUrl;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getUf() {
        return uf;
    }

    public void setUf(String uf) {
        this.uf = uf;
    }

    public String getCep() {
        return cep;
    }

    public void setCep(String cep) {
        this.cep = cep;
    }

    public Boolean getAvailable() {
        return available;
    }

    public void setAvailable(Boolean available) {
        this.available = available;
    }

    public Double getReputation() {
        return reputation;
    }

    public void setReputation(Double reputation) {
        this.reputation = reputation;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getResume() {
        return resume;
    }

    public void setResume(String resume) {
        this.resume = resume;
    }

    public List<Skill> getSkills() {
        return skills;
    }

    public void setSkills(List<Skill> skills) {
        this.skills = skills;
    }

    public List<PreviousProject> getProjects() {
        return projects;
    }

    public void setProjects(List<PreviousProject> projects) {
        this.projects = projects;
    }

    public List<ProjectType> getPreferredTypes() {
        return preferredTypes;
    }

    public void setPreferredTypes(List<ProjectType> preferredTypes) {
        this.preferredTypes = preferredTypes;
    }

    public ExperienceLevel getExperienceLevel() {
        return experienceLevel;
    }

    public void setExperienceLevel(ExperienceLevel experienceLevel) {
        this.experienceLevel = experienceLevel;
    }

    public String getProfilePhotoUrl() {
        return profilePhotoUrl;
    }

    public void setProfilePhotoUrl(String profilePhotoUrl) {
        this.profilePhotoUrl = profilePhotoUrl;
    }

    public Double getExpectedSalaryCLT() {
        return expectedSalaryCLT;
    }

    public void setExpectedSalaryCLT(Double expectedSalaryCLT) {
        this.expectedSalaryCLT = expectedSalaryCLT;
    }

    public Double getExpectedSalaryPJ() {
        return expectedSalaryPJ;
    }

    public void setExpectedSalaryPJ(Double expectedSalaryPJ) {
        this.expectedSalaryPJ = expectedSalaryPJ;
    }

    public Double getFreelanceMinExpectation() {
        return freelanceMinExpectation;
    }

    public void setFreelanceMinExpectation(Double freelanceMinExpectation) {
        this.freelanceMinExpectation = freelanceMinExpectation;
    }

    public Double getFreelanceMaxExpectation() {
        return freelanceMaxExpectation;
    }

    public void setFreelanceMaxExpectation(Double freelanceMaxExpectation) {
        this.freelanceMaxExpectation = freelanceMaxExpectation;
    }

    public List<OpportunityType> getPreferredOpportunityTypes() {
        return preferredOpportunityTypes;
    }

    public void setPreferredOpportunityTypes(List<OpportunityType> preferredOpportunityTypes) {
        this.preferredOpportunityTypes = preferredOpportunityTypes;
    }
    
    

    public Boolean getProfileCompletionEmailSent() {
        return profileCompletionEmailSent;
    }

    public void setProfileCompletionEmailSent(Boolean profileCompletionEmailSent) {
        this.profileCompletionEmailSent = profileCompletionEmailSent;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public void setLinkedinUrl(String linkedinUrl) {
        this.linkedinUrl = linkedinUrl;
    }
}