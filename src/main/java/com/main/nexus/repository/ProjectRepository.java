
package com.main.nexus.repository;

import com.main.nexus.model.Project;
import com.main.nexus.model.enums.ProjectStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByCompanyId(Long companyId);
    List<Project> findByStatus(ProjectStatus status);
}