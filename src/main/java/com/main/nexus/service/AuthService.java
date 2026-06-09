package com.main.nexus.service;

import com.main.nexus.dto.LoginRequestDTO;
import com.main.nexus.dto.LoginResponseDTO;
import com.main.nexus.dto.RegisterCompanyRequestDTO;
import com.main.nexus.dto.RegisterProfessionalRequestDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Professional;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.CompanyStatus;
import com.main.nexus.model.enums.UserType;
import com.main.nexus.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    @Autowired
    private TokenService tokenService;

    public void registerProfessional(RegisterProfessionalRequestDTO request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409), "Email already in use.");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setType(UserType.PROFESSIONAL);
        User savedUser = userRepository.save(user);

        Professional professional = new Professional();
        professional.setUser(savedUser);
        professional.setName(request.name());
        professional.setPhone(request.phone());
        professional.setCity(request.city());
        professional.setMinimumSalaryExpectation(request.minimumSalary());
        professional.setMaximumSalaryExpectation(request.maximumSalary());
        professionalService.save(professional);
    }

    public void registerCompany(RegisterCompanyRequestDTO request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409), "Email already in use.");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setType(UserType.COMPANY);
        User savedUser = userRepository.save(user);

        Company company = new Company();
        company.setUser(savedUser);
        company.setCompanyName(request.companyName());
        company.setTaxId(request.taxId());
        company.setPhone(request.phone());
        company.setCity(request.city());
        company.setDescription(request.description());
        company.setStatus(CompanyStatus.PENDING);
        companyService.save(company);
    }

    public LoginResponseDTO login(LoginRequestDTO request) {
        if (request.email().isBlank() || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400), "Email and password are required.");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(401), "Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(401), "Invalid email or password.");
        }

        if (!user.getActive()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403), "Account is disabled.");
        }

        if (user.getType() == UserType.COMPANY) {
            companyService.findByUserId(user.getId()).ifPresent(company -> {
                if (company.getStatus() != CompanyStatus.APPROVED) {
                    throw new ResponseStatusException(
                            HttpStatusCode.valueOf(403), "Company account is pending admin approval.");
                }
            });
        }

        UserDTO userDTO = new UserDTO(user.getId(), user.getEmail(), user.getType().name());
        String token = tokenService.generateToken(userDTO);

        return new LoginResponseDTO(user.getId(), user.getEmail(), user.getType().name(), token);
    }
}