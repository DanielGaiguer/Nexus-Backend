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
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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

    @Autowired
    private ProfileCompletionService profileCompletionService;
    
    @Autowired
    private NotificationService notificationService;

    public void registerProfessional(RegisterProfessionalRequestDTO request) {
        // ── 1. Validações de campos obrigatórios ───────────────────────────────
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Email is required.");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password is required.");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Name is required.");
        }

        // ── 2. Validação de unicidade ──────────────────────────────────────────
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already registered in the system.");
        }

        // ── 3. Resolução do CEP ANTES de qualquer persistência ────────────────
        GeolocationService.AddressData addressData = null;
        if (request.cep() != null && !request.cep().isBlank()) {
            try {
                addressData = geolocationService.resolveFromCep(request.cep());
            } catch (ResponseStatusException e) {
                if (e.getStatusCode().value() == 404) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "CEP not found. Please check the ZIP code and try again.");
                }
                if (e.getStatusCode().value() == 400) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "CEP has an invalid format. Expected format: 00000-000 or 00000000.");
                }
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Could not validate the ZIP code at this moment. Please try again in a few seconds.");
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
        professional.setPreferredOpportunityTypes(request.preferredOpportunityTypes());
        professional.setExpectedSalaryCLT(request.expectedSalaryCLT());
        professional.setExpectedSalaryPJ(request.expectedSalaryPJ());
        professional.setFreelanceMinExpectation(request.freelanceMinExpectation());
        professional.setFreelanceMaxExpectation(request.freelanceMaxExpectation());

        if (request.cep() != null && !request.cep().isBlank()) {
            professional.setLatitude(addressData.latitude());
            professional.setLongitude(addressData.longitude());
            professional.setCity(addressData.city());
            professional.setUf(addressData.state());
        }
        
        professionalService.save(professional);
        
        List<String> missing = profileCompletionService.getMissingFields(professional);
        boolean incomplete = !missing.isEmpty();
        
        String emailBody;
         if (incomplete) {
             String fieldList = String.join(", ", missing);
             emailBody = "Olá " + request.name() + ",\n\nSua conta foi criada com sucesso!\n\n" +
                     "Para começar a receber oportunidades compatíveis, complete seu perfil preenchendo: " +
                     fieldList + ".\n\n" +
                     "Acesse o Nexus e finalize seu cadastro.\n\nEquipe Nexus";
         } else {
             emailBody = "Olá " + request.name() + ",\n\nSua conta foi criada com sucesso e seu perfil está completo! " +
                     "Você já pode começar a receber oportunidades compatíveis com seu perfil.\n\nEquipe Nexus";
         }

         emailService.send(savedUser.getEmail(), "Bem-vindo ao Nexus!", emailBody);

         // Notificação in-app de perfil incompleto
         if (incomplete) {
             notificationService.notifyIncompleteProfile(savedUser, missing);
         }
    }
    
    @Transactional
    public void registerCompany(RegisterCompanyRequestDTO request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Email is required.");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password is required.");
        }
        if (request.companyName() == null || request.companyName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Company name is required.");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already registered in the system.");
        }

        if (request.taxId() != null && !request.taxId().isBlank()) {
            validateTaxId(request.taxId());
            if (companyRepository.existsByTaxId(request.taxId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "CNPJ already registered in the system.");
            }
        }

        GeolocationService.AddressData addressData = null;
        if (request.cep() != null && !request.cep().isBlank()) {
            try {
                addressData = geolocationService.resolveFromCep(request.cep());
            } catch (ResponseStatusException e) {
                if (e.getStatusCode().value() == 404) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "CEP not found. Please check the ZIP code and try again.");
                }
                if (e.getStatusCode().value() == 400) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "CEP has an invalid format. Expected format: 00000-000 or 00000000.");
                }
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Could not validate the ZIP code at this moment. Please try again in a few seconds.");
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
            company.setLatitude(addressData.latitude());
            company.setLongitude(addressData.longitude());
            company.setCity(addressData.city());
            company.setUf(addressData.state());
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Email and password are required.");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid email or password.");
        }

        if (!user.getActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Account is inactive.");
        }

        if (user.getType() == UserType.COMPANY) {
            Company company = companyService.findByUserId(user.getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Company profile missing for this user."));

            if (company.getStatus() == CompanyStatus.PENDING) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Company registration is pending admin approval.");
            }
            if (company.getStatus() == CompanyStatus.REJECTED) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Company registration was rejected.");
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "CNPJ has an invalid format. Expected 14 digits, e.g. 12.345.678/0001-99 or 12345678000199.");
        }

        if (digits.chars().distinct().count() == 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "CNPJ is invalid. Sequences of identical digits are not accepted.");
        }

        if (!isCnpjValid(digits)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "CNPJ is invalid. Please check the number and try again.");
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