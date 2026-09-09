package com.main.nexus.repository;

import com.main.nexus.model.AssessmentTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssessmentTemplateRepository extends JpaRepository<AssessmentTemplate, Long> {

    // Biblioteca da empresa. Inclui os desativados -- a tela lista os dois, marcando quais nao
    // podem mais ser aplicados; esconder um molde aposentado faria a empresa achar que ele
    // sumiu.
    List<AssessmentTemplate> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    // So os aplicaveis -- usado onde a escolha e "qual teste aplicar nesta vaga".
    List<AssessmentTemplate> findByCompanyIdAndActiveTrueOrderByTitleAsc(Long companyId);
}
