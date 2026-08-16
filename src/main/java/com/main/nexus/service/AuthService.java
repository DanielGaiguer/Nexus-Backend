package com.main.nexus.service;

import com.main.nexus.dto.GitHubUserDTO;
import com.main.nexus.dto.LoginRequestDTO;
import com.main.nexus.dto.LoginResponseDTO;
import com.main.nexus.dto.RegisterCompanyLinkedInRequestDTO;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Autowired
    private LinkedInService linkedInService;

    @Autowired
    private GitHubService gitHubService;

    @Value("${nexus.frontend.base-url}")
    private String frontendBaseUrl;

    public void registerProfessional(RegisterProfessionalRequestDTO request) {
        // Validações de campos obrigatórios
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

        // Validação de unicidad
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already registered in the system.");
        }

        // Resolução do CEP ANTES de qualquer persistência 
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

         // Notificação de perfil incompleto
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
        Company savedCompany = companyService.save(company);

        emailService.send(
            savedUser.getEmail(),
            "Cadastro recebido — Nexus",
            "Olá " + request.companyName() + ",\n\nSeu cadastro foi recebido e está em análise pelo administrador. " +
            "Você receberá um e-mail assim que sua conta for aprovada.\n\nEquipe Nexus"
        );

        notifyAdminsOfNewCompany(savedCompany);

        List<String> missing = profileCompletionService.getMissingFields(savedCompany);
        if (!missing.isEmpty()) {
            notificationService.notifyIncompleteCompanyProfile(savedUser, missing);
        }
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

        String name = resolveName(user);

        touchLastLogin(user);

        UserDTO userDTO = new UserDTO(user.getId(), user.getEmail(), user.getType().name());
        String token = tokenService.generateToken(userDTO);

        return new LoginResponseDTO(user.getId(), user.getEmail(), name, user.getType().name(), token);
    }

    // Usado pelo job de inatividade (ProfessionalInactivityService) pra saber há quanto
    // tempo o profissional não acessa a plataforma — chamado em todo fluxo que gera um
    // token novo (login por senha, LinkedIn e GitHub, tanto login quanto registro).
    private void touchLastLogin(User user) {
        user.setLastLoginAt(java.time.LocalDateTime.now());
        userRepository.save(user);
    }

    private String resolveName(User user) {
        return switch (user.getType()) {
            case PROFESSIONAL -> professionalService
                    .findByUserId(user.getId())
                    .map(Professional::getName) // No Type, seria feito assim: professional ? professional.getName() : user.getEmail()
                    .orElse(user.getEmail());
            case COMPANY -> companyService
                    .findByUserId(user.getId())
                    .map(Company::getCompanyName)
                    .orElse(user.getEmail());
            case ADMIN -> "Admin";
        };
    }

    public String getLinkedInLoginUrl(String redirect) {
        return linkedInService.buildAuthorizationUrl(withRedirect("login", redirect));
    }

    // Embute o destino pós-login no "state" do OAuth — ele viaja intacto até o
    // LinkedIn e volta no callback, então não precisa de escaping extra aqui:
    // o "state" inteiro já é URL-encoded/decoded uma única vez no transporte.
    private String withRedirect(String mode, String redirect) {
        if (redirect == null || redirect.isBlank()) {
            return mode;
        }
        return mode + ";redirect=" + redirect;
    }

    private String extractRedirect(String state) {
        int idx = state.indexOf(";redirect=");
        return idx < 0 ? null : state.substring(idx + ";redirect=".length());
    }

    private String stripRedirect(String state) {
        int idx = state.indexOf(";redirect=");
        return idx < 0 ? state : state.substring(0, idx);
    }

    public String getLinkedInRegisterUrl(String role) {
        String normalizedRole = "COMPANY".equalsIgnoreCase(role) ? "COMPANY" : "PROFESSIONAL";
        return linkedInService.buildAuthorizationUrl("register:" + normalizedRole);
    }

    public String getLinkedInLinkUrl(String currentUserToken) {
        if (!tokenService.validToken(currentUserToken)) {
            return frontendBaseUrl + "/auth/login?linkedinError=session_expired";
        }
        return linkedInService.buildAuthorizationUrl("link:" + currentUserToken);
    }

    public String handleLinkedInCallback(String code, String state, String error) {
        if (error != null || code == null || state == null) {
            return frontendBaseUrl + "/auth/login?linkedinError=denied";
        }

        String redirect = extractRedirect(state);
        String mode = stripRedirect(state);

        try {
            if (mode.startsWith("link:")) {
                return handleLinkedInLink(code, mode.substring("link:".length()));
            }
            if (mode.startsWith("register:")) {
                return handleLinkedInRegister(code, mode.substring("register:".length()));
            }
            return handleLinkedInLogin(code, redirect);
        } catch (ResponseStatusException e) {
            return frontendBaseUrl + "/auth/login?linkedinError=failed";
        }
    }

    private String handleLinkedInLogin(String code, String redirect) {
        LinkedInService.LinkedInUserInfo info = linkedInService.exchangeCodeForUserInfo(code);

        User user = findUserByLinkedInInfo(info).orElse(null);
        if (user == null) {
            return frontendBaseUrl + "/auth/login?linkedinError=no_account";
        }

        return loginExistingLinkedInUser(user, info, redirect);
    }

    private Optional<User> findUserByLinkedInInfo(LinkedInService.LinkedInUserInfo info) {
        return userRepository.findByLinkedinId(info.sub())
                .or(() -> info.email() != null
                        ? userRepository.findByEmail(info.email())
                        : Optional.empty());
    }

    private String loginExistingLinkedInUser(User user, LinkedInService.LinkedInUserInfo info, String redirect) {
        if (!user.getActive()) {
            return frontendBaseUrl + "/auth/login?linkedinError=inactive";
        }

        if (user.getType() == UserType.COMPANY) {
            Company company = companyService.findByUserId(user.getId()).orElse(null);
            if (company != null && company.getStatus() == CompanyStatus.PENDING) {
                return frontendBaseUrl + "/auth/login?linkedinError=pending_approval";
            }
            if (company != null && company.getStatus() == CompanyStatus.REJECTED) {
                return frontendBaseUrl + "/auth/login?linkedinError=rejected";
            }
        }

        // vincula na primeira vez que o e-mail bater 
        if (user.getLinkedinId() == null) {
            user.setLinkedinId(info.sub());
            userRepository.save(user);
        }

        backfillProfilePhoto(user, info.picture());

        String name = resolveName(user);
        touchLastLogin(user);
        UserDTO userDTO = new UserDTO(user.getId(), user.getEmail(), user.getType().name());
        String jwt = tokenService.generateToken(userDTO);

        String url = frontendBaseUrl + "/auth/linkedin/complete"
                + "?token=" + urlEncode(jwt)
                + "&userId=" + user.getId()
                + "&email=" + urlEncode(user.getEmail())
                + "&name=" + urlEncode(name)
                + "&role=" + user.getType().name();
        if (redirect != null && !redirect.isBlank()) {
            url += "&redirect=" + urlEncode(redirect);
        }
        return url;
    }

    // usa a foto do LinkedIn como foto de perfil apenas se o usuário ainda
    // não tiver uma nunca sobrescreve uma foto que a pessoa já escolheu.
    private void backfillProfilePhoto(User user, String pictureUrl) {
        if (pictureUrl == null || pictureUrl.isBlank()) {
            return;
        }
        if (user.getType() == UserType.PROFESSIONAL) {
            professionalService.findByUserId(user.getId()).ifPresent(p -> {
                if (p.getProfilePhotoUrl() == null || p.getProfilePhotoUrl().isBlank()) {
                    p.setProfilePhotoUrl(pictureUrl);
                    professionalService.update(p);
                }
            });
        } else if (user.getType() == UserType.COMPANY) {
            companyService.findByUserId(user.getId()).ifPresent(c -> {
                if (c.getProfilePhotoUrl() == null || c.getProfilePhotoUrl().isBlank()) {
                    c.setProfilePhotoUrl(pictureUrl);
                    companyService.update(c);
                }
            });
        }
    }

    // Cadastro via LinkedIn 
    // Profissional: o LinkedIn fornece nome + e-mail, os dois únicos campos
    // obrigatórios — a conta é criada de imediato (senha aleatória; o login
    // seguinte sempre será via LinkedIn). Empresa: o LinkedIn não fornece
    // razão social, então em vez de criar a conta aqui, emitimos um ticket
    // assinado e mandamos o navegador para um formulário curto de finalização.

    private String handleLinkedInRegister(String code, String role) {
        LinkedInService.LinkedInUserInfo info = linkedInService.exchangeCodeForUserInfo(code);

        if (info.email() == null || info.email().isBlank()) {
            return frontendBaseUrl + "/auth/login?linkedinError=no_email";
        }

        // Já existe conta com esse LinkedIn ou e-mail?
        User existing = findUserByLinkedInInfo(info).orElse(null);
        if (existing != null) {
            return loginExistingLinkedInUser(existing, info, null);
        }

        if ("COMPANY".equals(role)) {
            String ticket = tokenService.generateLinkedInTicket( // Gera um ticket para completar as informacoes da empresa, pois o linkedin nao da todas as necessarias
                    info.sub(), info.email(), info.name(), info.picture());
            return frontendBaseUrl + "/auth/register/company/linkedin"
                    + "?ticket=" + urlEncode(ticket)
                    + "&email=" + urlEncode(info.email())
                    + "&name=" + urlEncode(info.name() != null ? info.name() : "");
        }

        return registerProfessionalViaLinkedIn(info);
    }

    private String registerProfessionalViaLinkedIn(LinkedInService.LinkedInUserInfo info) {
        String name = (info.name() != null && !info.name().isBlank()) ? info.name() : info.email();

        User user = new User();
        user.setEmail(info.email());
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setType(UserType.PROFESSIONAL);
        user.setLinkedinId(info.sub());
        User savedUser = userRepository.save(user);

        Professional professional = new Professional();
        professional.setUser(savedUser);
        professional.setName(name);
        if (info.picture() != null && !info.picture().isBlank()) {
            professional.setProfilePhotoUrl(info.picture());
        }
        professionalService.save(professional);

        List<String> missing = profileCompletionService.getMissingFields(professional);
        String emailBody = "Olá " + name + ",\n\nSua conta foi criada com sucesso via LinkedIn!\n\n"
                + "Para começar a receber oportunidades compatíveis, complete seu perfil preenchendo: "
                + String.join(", ", missing) + ".\n\nAcesse o Nexus e finalize seu cadastro.\n\nEquipe Nexus";
        emailService.send(savedUser.getEmail(), "Bem-vindo ao Nexus!", emailBody);
        if (!missing.isEmpty()) {
            notificationService.notifyIncompleteProfile(savedUser, missing);
        }

        UserDTO userDTO = new UserDTO(savedUser.getId(), savedUser.getEmail(), UserType.PROFESSIONAL.name());
        String jwt = tokenService.generateToken(userDTO);

        return frontendBaseUrl + "/auth/linkedin/complete"
                + "?token=" + urlEncode(jwt)
                + "&userId=" + savedUser.getId()
                + "&email=" + urlEncode(savedUser.getEmail())
                + "&name=" + urlEncode(name)
                + "&role=" + UserType.PROFESSIONAL.name();
    }

    @Transactional
    public void registerCompanyViaLinkedIn(RegisterCompanyLinkedInRequestDTO request) {
        TokenService.LinkedInTicket ticket;
        try {
            ticket = tokenService.extractLinkedInTicket(request.ticket());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "LinkedIn session expired. Please connect with LinkedIn again.");
        }

        if (request.companyName() == null || request.companyName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Company name is required.");
        }

        if (userRepository.existsByEmail(ticket.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already registered in the system.");
        }
        if (userRepository.findByLinkedinId(ticket.sub()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This LinkedIn account is already linked to another user.");
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
        user.setEmail(ticket.email());
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setType(UserType.COMPANY);
        user.setLinkedinId(ticket.sub());
        User savedUser = userRepository.save(user);

        Company company = new Company();
        company.setUser(savedUser);
        company.setCompanyName(request.companyName());
        company.setTaxId(request.taxId());
        company.setPhone(request.phone());
        company.setCep(request.cep());

        if (addressData != null) {
            company.setLatitude(addressData.latitude());
            company.setLongitude(addressData.longitude());
            company.setCity(addressData.city());
            company.setUf(addressData.state());
        }

        company.setDescription(request.description());
        company.setStatus(CompanyStatus.PENDING);
        if (ticket.picture() != null && !ticket.picture().isBlank()) {
            company.setProfilePhotoUrl(ticket.picture());
        }
        Company savedCompany = companyService.save(company);

        emailService.send(
            savedUser.getEmail(),
            "Cadastro recebido — Nexus",
            "Olá " + request.companyName() + ",\n\nSeu cadastro foi recebido e está em análise pelo administrador. "
            + "Você receberá um e-mail assim que sua conta for aprovada.\n\nEquipe Nexus"
        );

        notifyAdminsOfNewCompany(savedCompany);

        List<String> missing = profileCompletionService.getMissingFields(savedCompany);
        if (!missing.isEmpty()) {
            notificationService.notifyIncompleteCompanyProfile(savedUser, missing);
        }
    }

    // ── notifica administradores sobre novo cadastro de empresa pendente 
    private void notifyAdminsOfNewCompany(Company company) {
        List<User> admins = userRepository.findByType(UserType.ADMIN);

        for (User admin : admins) {
            notificationService.notifyNewCompanyRegistration(
                admin, company.getCompanyName(), company.getId());

            emailService.send(
                admin.getEmail(),
                "Nova empresa aguardando aprovação — Nexus",
                "Olá,\n\nA empresa \"" + company.getCompanyName() + "\" acabou de se cadastrar no Nexus e está " +
                "aguardando aprovação.\n\nAcesse o painel administrativo para analisar o cadastro.\n\nEquipe Nexus"
            );
        }
    }

    private String handleLinkedInLink(String code, String existingJwt) {
        if (!tokenService.validToken(existingJwt)) {
            return frontendBaseUrl + "/auth/login?linkedinError=session_expired";
        }

        UserDTO claims = tokenService.extractClaims(existingJwt);
        User user = userRepository.findById(claims.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found."));

        LinkedInService.LinkedInUserInfo info = linkedInService.exchangeCodeForUserInfo(code);
        String profilePath = user.getType() == UserType.COMPANY ? "/company/profile" : "/pro/profile";

        Optional<User> owner = userRepository.findByLinkedinId(info.sub());
        if (owner.isPresent() && !owner.get().getId().equals(user.getId())) {
            return frontendBaseUrl + profilePath + "?linkedinError=already_linked";
        }

        user.setLinkedinId(info.sub());
        userRepository.save(user);
        backfillProfilePhoto(user, info.picture());

        return frontendBaseUrl + profilePath + "?linkedinLinked=true";
    }

    // ── Sign In with GitHub (OAuth Apps) ──────────────────────
    // Mesmo desenho do LinkedIn: o "state" carrega o modo (login/link) através de todo o
    // ciclo OAuth. Diferente do LinkedIn, o GitHub é exclusivo de profissionais e a própria
    // API do GitHub já fornece a URL pública do perfil (html_url) — não precisa de ticket
    // de finalização nem de um segundo cadastro tipo empresa.

    public String getGitHubLoginUrl(String redirect) {
        return gitHubService.buildAuthorizationUrl(withRedirect("login", redirect));
    }

    // Sem parâmetro de role — diferente do LinkedIn (PROFESSIONAL ou COMPANY), o GitHub só
    // registra profissional. Sem parâmetro de redirect também: mesmo desenho do LinkedIn,
    // onde /linkedin/register não aceita "redirect" (só faz sentido voltar para uma página
    // específica depois de um login, não de um primeiro cadastro).
    public String getGitHubRegisterUrl() {
        return gitHubService.buildAuthorizationUrl("register");
    }

    public String getGitHubLinkUrl(String currentUserToken) {
        if (!tokenService.validToken(currentUserToken)) {
            return frontendBaseUrl + "/auth/login?githubError=session_expired";
        }
        return gitHubService.buildAuthorizationUrl("link:" + currentUserToken);
    }

    public String handleGitHubCallback(String code, String state, String error) {
        if (error != null || code == null || state == null) {
            return frontendBaseUrl + "/auth/login?githubError=denied";
        }

        String redirect = extractRedirect(state);
        String mode = stripRedirect(state);

        try {
            if (mode.startsWith("link:")) {
                return handleGitHubLink(code, mode.substring("link:".length()));
            }
            if (mode.equals("register")) {
                return handleGitHubRegister(code);
            }
            return handleGitHubLogin(code, redirect);
        } catch (ResponseStatusException e) {
            return frontendBaseUrl + "/auth/login?githubError=failed";
        }
    }

    // Sinaliza "e-mail já pertence a uma conta que não é PROFESSIONAL" sem passar pelo catch
    // genérico de ResponseStatusException em handleGitHubCallback (que mapearia para o
    // githubError=failed genérico e perderia essa mensagem específica).
    private static class GitHubNotProfessionalException extends RuntimeException {}

    // Só loga quem já tem conta , mesmo padrão do Linkedin.
    private String handleGitHubLogin(String code, String redirect) {
        GitHubUserDTO gitHubUser = gitHubService.exchangeCodeForUser(code);

        Professional professional;
        try {
            professional = findGitHubProfessional(gitHubUser);
        } catch (GitHubNotProfessionalException e) {
            return frontendBaseUrl + "/auth/login?githubError=not_professional";
        }
        if (professional == null) {
            return frontendBaseUrl + "/auth/login?githubError=no_account";
        }
        return loginExistingGitHubProfessional(professional, gitHubUser, redirect);
    }

    // Cria conta se ninguém tiver essa identidade ainda mas, se já existir (por githubId ou
    // por e-mail), apenas loga, exatamente como handleLinkedInRegister faz para quem repete o
    // clique em "Cadastre-se" já tendo conta.
    private String handleGitHubRegister(String code) {
        GitHubUserDTO gitHubUser = gitHubService.exchangeCodeForUser(code);

        Professional professional;
        try {
            professional = findGitHubProfessional(gitHubUser);
        } catch (GitHubNotProfessionalException e) {
            return frontendBaseUrl + "/auth/login?githubError=not_professional";
        }
        if (professional != null) {
            return loginExistingGitHubProfessional(professional, gitHubUser, null);
        }
        return registerProfessionalViaGitHub(gitHubUser, null);
    }

    // Resolve a identidade do GitHub para um Professional já existente, por githubId ou,
    // na primeira vez que o e-mail bater, por e-mail (vínculo automático, mesmo padrão RN31
    // do LinkedIn em User) — usado tanto por login quanto por register. Retorna null quando
    // nenhuma conta existe ainda com essa identidade (aí cada chamador decide o que fazer:
    // login erra com "no_account", register cria a conta).
    private Professional findGitHubProfessional(GitHubUserDTO gitHubUser) {
        Professional byGithubId = professionalService.findByGithubId(gitHubUser.id()).orElse(null);
        if (byGithubId != null) {
            return byGithubId;
        }

        if (gitHubUser.email() == null || gitHubUser.email().isBlank()) {
            return null;
        }

        User existingUser = userRepository.findByEmail(gitHubUser.email()).orElse(null);
        if (existingUser == null) {
            return null;
        }
        if (existingUser.getType() != UserType.PROFESSIONAL) {
            throw new GitHubNotProfessionalException();
        }

        Professional matchedByEmail = professionalService.findByUserId(existingUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Professional profile missing for this user."));
        matchedByEmail.setGithubId(gitHubUser.id());
        matchedByEmail.setGithubUrl(gitHubUser.htmlUrl());
        professionalService.update(matchedByEmail);
        return matchedByEmail;
    }

    private String loginExistingGitHubProfessional(Professional professional, GitHubUserDTO gitHubUser, String redirect) {
        User user = professional.getUser();
        if (!user.getActive()) {
            return frontendBaseUrl + "/auth/login?githubError=inactive";
        }

        if (backfillProfilePhotoFromGithub(professional, gitHubUser.avatarUrl())) {
            professionalService.update(professional);
        }

        touchLastLogin(user);
        UserDTO userDTO = new UserDTO(user.getId(), user.getEmail(), UserType.PROFESSIONAL.name());
        String jwt = tokenService.generateToken(userDTO);

        String url = frontendBaseUrl + "/auth/github/complete"
                + "?token=" + urlEncode(jwt)
                + "&userId=" + user.getId()
                + "&email=" + urlEncode(user.getEmail())
                + "&name=" + urlEncode(professional.getName())
                + "&role=" + UserType.PROFESSIONAL.name();
        if (redirect != null && !redirect.isBlank()) {
            url += "&redirect=" + urlEncode(redirect);
        }
        return url;
    }

    // Cadastro via GitHub o GitHub fornece nome/login + e-mail (a menos que o e-mail
    // esteja privado), suficiente para criar a conta de imediato, sem ticket intermediário.
    private String registerProfessionalViaGitHub(GitHubUserDTO gitHubUser, String redirect) {
        if (gitHubUser.email() == null || gitHubUser.email().isBlank()) {
            return frontendBaseUrl + "/auth/login?githubError=no_email";
        }
        if (userRepository.existsByEmail(gitHubUser.email())) {
            return frontendBaseUrl + "/auth/login?githubError=email_in_use";
        }

        String name = (gitHubUser.name() != null && !gitHubUser.name().isBlank())
                ? gitHubUser.name() : gitHubUser.login();

        User user = new User();
        user.setEmail(gitHubUser.email());
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setType(UserType.PROFESSIONAL);
        User savedUser = userRepository.save(user);

        Professional professional = new Professional();
        professional.setUser(savedUser);
        professional.setName(name);
        professional.setGithubId(gitHubUser.id());
        professional.setGithubUrl(gitHubUser.htmlUrl());
        if (gitHubUser.avatarUrl() != null && !gitHubUser.avatarUrl().isBlank()) {
            professional.setProfilePhotoUrl(gitHubUser.avatarUrl());
        }
        professionalService.save(professional);

        List<String> missing = profileCompletionService.getMissingFields(professional);
        String emailBody = "Olá " + name + ",\n\nSua conta foi criada com sucesso via GitHub!\n\n"
                + "Para começar a receber oportunidades compatíveis, complete seu perfil preenchendo: "
                + String.join(", ", missing) + ".\n\nAcesse o Nexus e finalize seu cadastro.\n\nEquipe Nexus";
        emailService.send(savedUser.getEmail(), "Bem-vindo ao Nexus!", emailBody);
        if (!missing.isEmpty()) {
            notificationService.notifyIncompleteProfile(savedUser, missing);
        }

        UserDTO userDTO = new UserDTO(savedUser.getId(), savedUser.getEmail(), UserType.PROFESSIONAL.name());
        String jwt = tokenService.generateToken(userDTO);

        String url = frontendBaseUrl + "/auth/github/complete"
                + "?token=" + urlEncode(jwt)
                + "&userId=" + savedUser.getId()
                + "&email=" + urlEncode(savedUser.getEmail())
                + "&name=" + urlEncode(name)
                + "&role=" + UserType.PROFESSIONAL.name();
        if (redirect != null && !redirect.isBlank()) {
            url += "&redirect=" + urlEncode(redirect);
        }
        return url;
    }

    private String handleGitHubLink(String code, String existingJwt) {
        if (!tokenService.validToken(existingJwt)) {
            return frontendBaseUrl + "/auth/login?githubError=session_expired";
        }

        UserDTO claims = tokenService.extractClaims(existingJwt);
        User user = userRepository.findById(claims.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found."));

        if (user.getType() != UserType.PROFESSIONAL) {
            return frontendBaseUrl + "/auth/login?githubError=not_professional";
        }

        Professional professional = professionalService.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Professional profile not found."));

        GitHubUserDTO gitHubUser = gitHubService.exchangeCodeForUser(code);

        Optional<Professional> owner = professionalService.findByGithubId(gitHubUser.id());
        if (owner.isPresent() && !owner.get().getId().equals(professional.getId())) {
            return frontendBaseUrl + "/pro/profile?githubError=already_linked";
        }

        professional.setGithubId(gitHubUser.id());
        professional.setGithubUrl(gitHubUser.htmlUrl());
        backfillProfilePhotoFromGithub(professional, gitHubUser.avatarUrl());
        professionalService.update(professional);

        return frontendBaseUrl + "/pro/profile?githubLinked=true";
    }

    // Remove o vínculo com o GitHub do profissional logado (chamado por um endpoint
    // autenticado comum, sem envolver o ciclo OAuth não há "code" para trocar aqui).
    public void unlinkGitHub(Long userId) {
        Professional professional = professionalService.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Professional profile not found."));
        professional.setGithubId(null);
        professional.setGithubUrl(null);
        professionalService.update(professional);
    }

    // usa o avatar do GitHub como foto de perfil apenas se o profissional ainda não tiver
    // uma e  nunca sobrescreve uma foto que a pessoa já escolheu
    private boolean backfillProfilePhotoFromGithub(Professional professional, String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return false;
        }
        if (professional.getProfilePhotoUrl() != null && !professional.getProfilePhotoUrl().isBlank()) {
            return false;
        }
        professional.setProfilePhotoUrl(avatarUrl);
        return true;
    }

    private String urlEncode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
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