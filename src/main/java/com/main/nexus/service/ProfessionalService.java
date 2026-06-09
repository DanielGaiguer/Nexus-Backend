/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.main.nexus.service;

import com.main.nexus.model.Professional;
import com.main.nexus.repository.ProfessionalRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProfessionalService {
    @Autowired
    private ProfessionalRepository professionalRepository;
    
    public Professional save(Professional professional){
        return professionalRepository.save(professional);
    }
    
    public Professional findById(Long id){
        return professionalRepository.findByUserId(id).orElseThrow(() -> new RuntimeException("Professional profile not found."));
    }
    
    public List<Professional> findAll() {
        return professionalRepository.findAll();
    }

    public Professional update(Professional professional) {
        return professionalRepository.save(professional);
    }

    public void delete(Long id) {
        professionalRepository.deleteById(id);
    }

    // Atualiza reputação após nova avaliação
    public void updateReputation(Long professionalId, Double newAverage) {
        Professional professional = findById(professionalId);
        professional.setReputation(newAverage);
        professionalRepository.save(professional);
    }
    
    public Optional<Professional> findByUserId(Long userId) {
        return professionalRepository.findByUserId(userId);
    }
}
