package com.main.nexus.service;

import com.main.nexus.model.Company;
import com.main.nexus.model.Professional;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.CompanyStatus;
import com.main.nexus.model.enums.UserType;
import com.main.nexus.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfessionalService professionalService;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public User registerProfessional(User user, Professional professional) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already in use");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setType(UserType.PROFESSIONAL);
        User savedUser = userRepository.save(user);

        professional.setUser(savedUser);
        professionalService.save(professional);

        return savedUser;
    }

    public User registerCompany(User user, Company company) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already in use");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setType(UserType.COMPANY);
        User savedUser = userRepository.save(user);

        company.setUser(savedUser);
        company.setStatus(CompanyStatus.PENDING); 
        companyService.save(company);

        return savedUser;
    }
}