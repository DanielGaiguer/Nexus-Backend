package com.main.nexus.model;

import com.main.nexus.model.enums.ContractType;
import com.main.nexus.model.enums.ExperienceLevel;
import com.main.nexus.model.enums.Modality;
import com.main.nexus.model.enums.OpportunityType;
import com.main.nexus.model.enums.ProjectStatus;
import com.main.nexus.model.enums.ProjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tb_project")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Double minimumBudget;
    private Double maximumBudget;

    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Modality modality;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.OPEN;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectType type;

    @ManyToMany
    @JoinTable(
        name = "tb_job_skill",
        joinColumns = @JoinColumn(name = "job_id"),
        inverseJoinColumns = @JoinColumn(name = "skill_id")
    )
    private List<Skill> requiredSkills = new ArrayList<>();
    
    @Column(nullable = false)
    private Integer maxPositions = 1;

    @Column(nullable = false)
    private Integer filledPositions = 0;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ExperienceLevel experienceLevel; 
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OpportunityType opportunityType = OpportunityType.PROJECT;

    // Campos exclusivos de VAGA
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ContractType contractType;

    @Column(columnDefinition = "TEXT")
    private String benefits;

    @Column
    private LocalDate startDate;

    @Column
    private Integer workloadHoursPerWeek;

    @Column
    private Double monthlySalaryMin;

    @Column
    private Double monthlySalaryMax;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getMinimumBudget() {
        return minimumBudget;
    }

    public void setMinimumBudget(Double minimumBudget) {
        this.minimumBudget = minimumBudget;
    }

    public Double getMaximumBudget() {
        return maximumBudget;
    }

    public void setMaximumBudget(Double maximumBudget) {
        this.maximumBudget = maximumBudget;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    public Modality getWorkMode() {
        return modality;
    }

    public void setWorkMode(Modality modality) {
        this.modality = modality;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<Skill> getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(List<Skill> requiredSkills) {
        this.requiredSkills = requiredSkills;
    }

    public Modality getModality() {
        return modality;
    }

    public void setModality(Modality modality) {
        this.modality = modality;
    }

    public ProjectType getType() {
        return type;
    }

    public void setType(ProjectType type) {
        this.type = type;
    }

    public Integer getMaxPositions() {
        return maxPositions;
    }

    public void setMaxPositions(Integer maxPositions) {
        this.maxPositions = maxPositions;
    }

    public Integer getFilledPositions() {
        return filledPositions;
    }

    public void setFilledPositions(Integer filledPositions) {
        this.filledPositions = filledPositions;
    }
    
    public boolean hasOpenPositions() {
        return filledPositions < maxPositions;
    }

    public ExperienceLevel getExperienceLevel() {
        return experienceLevel;
    }

    public void setExperienceLevel(ExperienceLevel experienceLevel) {
        this.experienceLevel = experienceLevel;
    }

    public OpportunityType getOpportunityType() {
        return opportunityType;
    }

    public void setOpportunityType(OpportunityType opportunityType) {
        this.opportunityType = opportunityType;
    }

    public ContractType getContractType() {
        return contractType;
    }

    public void setContractType(ContractType contractType) {
        this.contractType = contractType;
    }

    public String getBenefits() {
        return benefits;
    }

    public void setBenefits(String benefits) {
        this.benefits = benefits;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public Integer getWorkloadHoursPerWeek() {
        return workloadHoursPerWeek;
    }

    public void setWorkloadHoursPerWeek(Integer workloadHoursPerWeek) {
        this.workloadHoursPerWeek = workloadHoursPerWeek;
    }

    public Double getMonthlySalaryMin() {
        return monthlySalaryMin;
    }

    public void setMonthlySalaryMin(Double monthlySalaryMin) {
        this.monthlySalaryMin = monthlySalaryMin;
    }

    public Double getMonthlySalaryMax() {
        return monthlySalaryMax;
    }

    public void setMonthlySalaryMax(Double monthlySalaryMax) {
        this.monthlySalaryMax = monthlySalaryMax;
    }
    
    

}