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
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfessionalService {
    @Autowired
    private ProfessionalRepository professionalRepository;

    public Professional save(Professional professional){
        return professionalRepository.save(professional);
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

    public Professional findById(Long professionalId) {
        return professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Professional not found: " + professionalId));
    }

    public Optional<Professional> findByUserId(Long userId) {
        return professionalRepository.findByUserId(userId);
    }

    public Optional<Professional> findByGithubId(String githubId) {
        return professionalRepository.findByGithubId(githubId);
    }
}
