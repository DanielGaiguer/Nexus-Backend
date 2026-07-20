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
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private CompanyRepository companyRepository;

    @Autowired
    private TokenService tokenService;
    
    @Autowired
    private EmailService emailService;

    @Autowired
    private GeolocationService geolocationService;

    public void registerProfessional(RegisterProfessionalRequestDTO request) {
        // ── 1. Validações de campos obrigatórios ───────────────────────────────
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Email is required.");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Password is required.");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Name is required.");
        }

        // ── 2. Validação de unicidade ──────────────────────────────────────────
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "Email '" + request.email() + "' is already registered. " +
                    "If you already have an account, please log in.");
        }

        // ── 3. Resolução do CEP ANTES de qualquer persistência ────────────────
        GeolocationService.AddressData addressData = null;
        if (request.cep() != null && !request.cep().isBlank()) {
            try {
                addressData = geolocationService.resolveFromCep(request.cep());
            } catch (ResponseStatusException e) {
                if (e.getStatusCode().value() == 404) {
                    throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                            "CEP '" + request.cep() + "' not found. " +
                            "Please check the ZIP code and try again.");
                }
                if (e.getStatusCode().value() == 400) {
                    throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                            "CEP '" + request.cep() + "' has an invalid format. " +
                            "Expected format: 00000-000 or 00000000.");
                }
                throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                        "Could not validate the ZIP code at this moment. " +
                        "Please try again in a few seconds.");
            }
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
        professional.setCep(request.cep());

        if (request.cep() != null && !request.cep().isBlank()) {
            GeolocationService.AddressData address = geolocationService.resolveFromCep(request.cep());
            professional.setLatitude(address.latitude());
            professional.setLongitude(address.longitude());
            professional.setCity(address.city());
            professional.setUf(address.state());
        }

        professional.setMinimumSalaryExpectation(request.minimumSalary());
        professional.setMaximumSalaryExpectation(request.maximumSalary());
        professionalService.save(professional);
        
        emailService.send(
            savedUser.getEmail(),
            "Bem-vindo ao Nexus!",
            "Olá " + request.name() + ",\n\nSua conta foi criada com sucesso. " +
            "Complete seu perfil e suas skills para começar a receber oportunidades compatíveis.\n\nEquipe Nexus"
        );
    }
    
    @Transactional
    public void registerCompany(RegisterCompanyRequestDTO request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Email is required.");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Password is required.");
        }
        if (request.companyName() == null || request.companyName().isBlank()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Company name is required.");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "Email '" + request.email() + "' is already registered. " +
                    "If you already have an account, please log in.");
        }

        if (request.taxId() != null && !request.taxId().isBlank()) {
            if (companyRepository.existsByTaxId(request.taxId())) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                        "A company with CNPJ '" + request.taxId() + "' is already registered.");
            }
        }
        
        if (request.taxId() != null && !request.taxId().isBlank()) {
            validateTaxId(request.taxId()); // valida formato e dígitos verificadores primeiro
            if (companyRepository.existsByTaxId(request.taxId())) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                        "A company with CNPJ '" + request.taxId() + "' is already registered.");
            }
        }

        GeolocationService.AddressData addressData = null;
        if (request.cep() != null && !request.cep().isBlank()) {
            try {
                addressData = geolocationService.resolveFromCep(request.cep());
            } catch (ResponseStatusException e) {
                if (e.getStatusCode().value() == 404) {
                    throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                            "CEP '" + request.cep() + "' not found. " +
                            "Please check the ZIP code and try again.");
                }
                if (e.getStatusCode().value() == 400) {
                    throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                            "CEP '" + request.cep() + "' has an invalid format. " +
                            "Expected format: 00000-000 or 00000000.");
                }
                throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                        "Could not validate the ZIP code at this moment. " +
                        "Please try again in a few seconds.");
            }
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
        company.setCep(request.cep());

        if (request.cep() != null && !request.cep().isBlank()) {
            GeolocationService.AddressData address = geolocationService.resolveFromCep(request.cep());
            company.setLatitude(address.latitude());
            company.setLongitude(address.longitude());
            company.setCity(address.city());
            company.setUf(address.state());
        }

        company.setDescription(request.description());
        company.setStatus(CompanyStatus.PENDING);
        companyService.save(company);
        
        emailService.send(
            savedUser.getEmail(),
            "Cadastro recebido — Nexus",
            "Olá " + request.companyName() + ",\n\nSeu cadastro foi recebido e está em análise pelo administrador. " +
            "Você receberá um e-mail assim que sua conta for aprovada.\n\nEquipe Nexus"
        );
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
            Company company = companyService.findByUserId(user.getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatusCode.valueOf(500), "Company profile missing for this user."));

            if (company.getStatus() != CompanyStatus.APPROVED) {
                throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                        "Company account is pending admin approval.");
            }
        }

        String name = switch (user.getType()) {
            case PROFESSIONAL -> professionalService
                    .findByUserId(user.getId())
                    .map(Professional::getName)
                    .orElse(user.getEmail());
            case COMPANY -> companyService
                    .findByUserId(user.getId())
                    .map(Company::getCompanyName)
                    .orElse(user.getEmail());
            case ADMIN -> "Admin";
        };

        UserDTO userDTO = new UserDTO(user.getId(), user.getEmail(), user.getType().name());
        String token = tokenService.generateToken(userDTO);

        return new LoginResponseDTO(user.getId(), user.getEmail(), name, user.getType().name(), token);
    }
    
    
    private void validateTaxId(String taxId) {
        if (taxId == null || taxId.isBlank()) return;

        String digits = taxId.replaceAll("[^0-9]", "");

        if (digits.length() != 14) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "CNPJ '" + taxId + "' has an invalid format. " +
                    "Expected 14 digits, e.g. 12.345.678/0001-99 or 12345678000199.");
        }

        if (digits.chars().distinct().count() == 1) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "CNPJ '" + taxId + "' is invalid. Sequences of identical digits are not accepted.");
        }

        if (!isCnpjValid(digits)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "CNPJ '" + taxId + "' is invalid. Please check the number and try again.");
        }
    }

    private boolean isCnpjValid(String digits) {
        int[] weights1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] weights2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += Character.getNumericValue(digits.charAt(i)) * weights1[i];
        }
        int remainder = sum % 11;
        int digit1 = remainder < 2 ? 0 : 11 - remainder;

        if (digit1 != Character.getNumericValue(digits.charAt(12))) return false;

        sum = 0;
        for (int i = 0; i < 13; i++) {
            sum += Character.getNumericValue(digits.charAt(i)) * weights2[i];
        }
        remainder = sum % 11;
        int digit2 = remainder < 2 ? 0 : 11 - remainder;

        return digit2 == Character.getNumericValue(digits.charAt(13));
    }
}