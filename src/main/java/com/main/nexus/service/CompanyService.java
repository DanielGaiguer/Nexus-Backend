package com.main.nexus.service;

import com.main.nexus.model.Company;
import com.main.nexus.model.User;
import com.main.nexus.repository.CompanyRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CompanyService {

    @Autowired
    private CompanyRepository companyRepository;

    public Company save(Company company) {
        return companyRepository.save(company);
    }

    public Company findById(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found: " + id));
    }

    public Company findByUser(User user) {
        return companyRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Company profile not found"));
    }

    public List<Company> findAll() {
        return companyRepository.findAll();
    }

    public Company update(Company company) {
        return companyRepository.save(company);
    }

    public void updateReputation(Long companyId, Double newAverage) {
        Company company = findById(companyId);
        company.setReputation(newAverage);
        companyRepository.save(company);
    }
}