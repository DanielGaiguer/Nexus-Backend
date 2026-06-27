package com.main.nexus.model;

import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.CompanyRejectionReason;
import com.main.nexus.model.enums.ProfessionalRejectionReason;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "tb_rejection_feedback")
public class RejectionFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "professional_id", nullable = false)
    private Professional professional;

    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private AuthorType rejectedBy;

    // Preenchido apenas quando rejectedBy == PROFESSIONAL
    @ElementCollection(targetClass = ProfessionalRejectionReason.class)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
        name = "tb_professional_rejection_reasons",
        joinColumns = @JoinColumn(name = "rejection_id")
    )
    @Column(name = "reason", nullable = false, length = 40)
    private List<ProfessionalRejectionReason> professionalReasons;

    // Preenchido apenas quando rejectedBy == COMPANY
    @ElementCollection(targetClass = CompanyRejectionReason.class)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
        name = "tb_company_rejection_reasons",
        joinColumns = @JoinColumn(name = "rejection_id")
    )
    @Column(name = "reason", nullable = false, length = 40)
    private List<CompanyRejectionReason> companyReasons;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { 
        return id; 
    }
    
    public void setId(Long id) { 
        this.id = id; 
    }
    public Professional getProfessional() { 
        return professional; 
    }
    public void setProfessional(Professional professional) { 
        this.professional = professional; 
    }
    public Project getProject() { 
        return project; 
    }
    public void setProject(Project project) { 
        this.project = project; 
    }
    public AuthorType getRejectedBy() { 
        return rejectedBy; 
    }
    public void setRejectedBy(AuthorType rejectedBy) { 
        this.rejectedBy = rejectedBy; 
    }
    public List<ProfessionalRejectionReason> getProfessionalReasons() { 
        return professionalReasons; 
    }
    public void setProfessionalReasons(List<ProfessionalRejectionReason> professionalReasons) { 
        this.professionalReasons = professionalReasons; 
    }
    public List<CompanyRejectionReason> getCompanyReasons() { 
        return companyReasons; 
    }
    public void setCompanyReasons(List<CompanyRejectionReason> companyReasons) { 
        this.companyReasons = companyReasons; 
    }
    public LocalDateTime getCreatedAt() { 
        return createdAt; 
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt; 
    }
}