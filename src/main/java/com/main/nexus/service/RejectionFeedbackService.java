package com.main.nexus.service;

import com.main.nexus.model.RejectionFeedback;
import com.main.nexus.repository.RejectionFeedbackRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RejectionFeedbackService {

    @Autowired
    private RejectionFeedbackRepository rejectionFeedbackRepository;

    public RejectionFeedback save(RejectionFeedback feedback) {
        return rejectionFeedbackRepository.save(feedback);
    }

    // Retorna contagem de rejeições por motivo para um profissional
    // Usado pelo algoritmo de aprendizado contínuo
    public List<Object[]> getRejectionPatterns(Long professionalId) {
        return rejectionFeedbackRepository.countByReasonForProfessional(professionalId);
    }
}