package com.main.nexus.service;

import com.main.nexus.dto.ApproveCustomPortalRequestDTO;
import com.main.nexus.dto.CreateCustomPortalDTO;
import com.main.nexus.dto.CustomPortalDTO;
import com.main.nexus.dto.CustomPortalDetailDTO;
import com.main.nexus.dto.CustomPortalOverviewDTO;
import com.main.nexus.dto.CustomPortalRequestDTO;
import com.main.nexus.dto.CustomPortalSectionDTO;
import com.main.nexus.dto.CustomPortalStatusHistoryDTO;
import com.main.nexus.dto.PublicCustomPortalDTO;
import com.main.nexus.dto.UpdateCustomPortalBrandingDTO;
import com.main.nexus.dto.UpdateCustomPortalSubscriptionDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.CustomPortal;
import com.main.nexus.model.CustomPortalRequest;
import com.main.nexus.model.CustomPortalSection;
import com.main.nexus.model.CustomPortalStatusHistory;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.BrandingImageKind;
import com.main.nexus.model.enums.CompanyStatus;
import com.main.nexus.model.enums.CustomPortalPaymentStatus;
import com.main.nexus.model.enums.CustomPortalRequestStatus;
import com.main.nexus.model.enums.CustomPortalStatus;
import com.main.nexus.model.enums.UserType;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.CustomPortalRepository;
import com.main.nexus.repository.CustomPortalRequestRepository;
import com.main.nexus.repository.CustomPortalStatusHistoryRepository;
import com.main.nexus.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Regras da plataforma personalizada (CustomPortal) — fundacao: solicitacao,
 * aprovacao/criacao pelo Admin e controle da assinatura. Mesmo desenho da fila
 * de aprovacao de empresa (fila -> decisao do Admin -> guarda de transicao de
 * status), sem customizacao visual nem roteamento por subdominio (proximos
 * prompts).
 */
@Service
public class CustomPortalService {

    /** Dias de antecedencia do aviso "assinatura perto do vencimento". */
    public static final int RENEWAL_REMINDER_DAYS = 7;

    // Subdominio: 3..63 chars, minusculas/digitos/hifen, sem hifen nas pontas.
    private static final Pattern SUBDOMAIN_PATTERN =
            Pattern.compile("^[a-z0-9]([a-z0-9-]{1,61}[a-z0-9])$");

    private static final Set<String> RESERVED_SUBDOMAINS = Set.of(
            "www", "api", "admin", "app", "mail", "smtp", "ftp", "cdn", "static",
            "assets", "ns1", "ns2", "nexus", "portal", "help", "support", "status",
            "blog", "dev", "staging", "test");

    // Cor primaria de branding — hex #rrggbb (o color picker do front sempre emite 6 digitos).
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private static final int MAX_SECTIONS = 10;
    private static final int MAX_SECTION_TITLE = 150;
    private static final int MAX_DISPLAY_NAME = 120;

    @Autowired
    private CustomPortalRepository customPortalRepository;

    @Autowired
    private CustomPortalRequestRepository requestRepository;

    @Autowired
    private CustomPortalStatusHistoryRepository historyRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private EmailTemplateService emailTemplate;

    @Autowired
    private SupabaseStorageService supabaseStorageService;

    // ═══════════════════════ Lado do contratante ═══════════════════════

    public CustomPortalOverviewDTO getOverviewForUser(Long userId) {
        Company company = companyByUser(userId);

        CustomPortalRequestDTO latestRequest = requestRepository
                .findFirstByCompanyIdOrderByRequestedAtDesc(company.getId())
                .map(CustomPortalRequestDTO::from)
                .orElse(null);

        CustomPortalDTO portal = customPortalRepository
                .findByCompanyId(company.getId())
                .map(CustomPortalDTO::from)
                .orElse(null);

        boolean canRequest = portal == null
                && (latestRequest == null
                    || !CustomPortalRequestStatus.PENDING.name().equals(latestRequest.status()));

        return new CustomPortalOverviewDTO(latestRequest, portal, canRequest);
    }

    @Transactional
    public CustomPortalRequestDTO createRequest(Long userId, String message) {
        Company company = companyByUser(userId);

        if (customPortalRepository.existsByCompanyId(company.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This company already has a custom portal.");
        }
        if (requestRepository.existsByCompanyIdAndStatus(
                company.getId(), CustomPortalRequestStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "There is already a pending custom portal request for this company.");
        }

        CustomPortalRequest request = new CustomPortalRequest();
        request.setCompany(company);
        request.setMessage(message != null && !message.isBlank() ? message.trim() : null);
        request.setStatus(CustomPortalRequestStatus.PENDING);
        CustomPortalRequest saved = requestRepository.save(request);

        // Avisa todos os admins (notificacao + e-mail com marca).
        for (User admin : userRepository.findByType(UserType.ADMIN)) {
            notificationService.notifyCustomPortalRequestReceived(admin, company.getCompanyName());
            emailService.sendHtml(
                admin.getEmail(),
                "Nova solicitação de plataforma personalizada — Nexus",
                emailTemplate.render(
                    "Nova solicitação de plataforma personalizada",
                    List.of(
                        "O contratante \"" + company.getCompanyName() + "\" solicitou uma plataforma personalizada.",
                        "Acesse o painel administrativo para aprovar (definindo plano, valor, vencimento e subdomínio) ou recusar."),
                    new EmailTemplateService.Button("Ver solicitações", "/admin/custom-portals")),
                emailTemplate.renderText(
                    "Nova solicitação de plataforma personalizada",
                    List.of("O contratante \"" + company.getCompanyName() + "\" solicitou uma plataforma personalizada."),
                    new EmailTemplateService.Button("Ver solicitações", "/admin/custom-portals")));
        }

        // Confirmacao para o contratante.
        emailCompany(company,
            "Recebemos sua solicitação — Nexus",
            "Recebemos sua solicitação de plataforma personalizada",
            List.of(
                "Olá " + company.getCompanyName() + ",",
                "Sua solicitação foi registrada e está em análise pela nossa equipe. "
                    + "Você receberá um e-mail assim que houver uma decisão."),
            new EmailTemplateService.Button("Acompanhar solicitação", "/company/custom-portal"));

        return CustomPortalRequestDTO.from(saved);
    }

    // ═══════════════════════ Lado do Admin — solicitações ═══════════════

    public List<CustomPortalRequestDTO> listRequests(CustomPortalRequestStatus statusFilter) {
        List<CustomPortalRequest> rows = statusFilter != null
                ? requestRepository.findByStatusOrderByRequestedAtAsc(statusFilter)
                : requestRepository.findAllByOrderByRequestedAtDesc();
        return rows.stream().map(CustomPortalRequestDTO::from).toList();
    }

    @Transactional
    public CustomPortalDTO approveRequest(Long adminUserId, Long requestId,
                                          ApproveCustomPortalRequestDTO body) {
        CustomPortalRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Custom portal request not found: " + requestId));

        if (request.getStatus() != CustomPortalRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This request is not pending a decision.");
        }

        User admin = adminById(adminUserId);
        Company company = request.getCompany();

        CustomPortal portal = createPortalInternal(
                company, body.subdomain(), body.planName(), body.planPrice(),
                body.subscriptionStartDate(), body.nextDueDate(), body.paymentStatus(),
                admin, request);

        request.setStatus(CustomPortalRequestStatus.APPROVED);
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now());
        requestRepository.save(request);

        notificationService.notifyCustomPortalRequestApproved(
                company.getUser(), portal.getSubdomain());
        emailCompany(company,
            "Sua plataforma personalizada foi aprovada — Nexus",
            "Sua plataforma personalizada foi aprovada!",
            List.of(
                "Olá " + company.getCompanyName() + ",",
                "Reservamos o subdomínio \"" + portal.getSubdomain() + "\" para a sua plataforma.",
                "Plano: " + portal.getPlanName() + " — R$ " + portal.getPlanPrice()
                    + ". Próximo vencimento: " + portal.getNextDueDate() + "."),
            new EmailTemplateService.Button("Ver minha plataforma", "/company/custom-portal"));

        return CustomPortalDTO.from(portal);
    }

    @Transactional
    public CustomPortalRequestDTO rejectRequest(Long adminUserId, Long requestId, String reason) {
        CustomPortalRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Custom portal request not found: " + requestId));

        if (request.getStatus() != CustomPortalRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This request is not pending a decision.");
        }
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A rejection reason must be provided.");
        }

        User admin = adminById(adminUserId);
        request.setStatus(CustomPortalRequestStatus.REJECTED);
        request.setDecisionReason(reason.trim());
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now());
        CustomPortalRequest saved = requestRepository.save(request);

        Company company = request.getCompany();
        notificationService.notifyCustomPortalRequestRejected(company.getUser(), reason.trim());
        emailCompany(company,
            "Sua solicitação de plataforma personalizada — Nexus",
            "Sua solicitação não foi aprovada",
            List.of(
                "Olá " + company.getCompanyName() + ",",
                "Sua solicitação de plataforma personalizada não foi aprovada neste momento.",
                "Motivo: " + reason.trim(),
                "Você pode registrar uma nova solicitação a qualquer momento."),
            new EmailTemplateService.Button("Abrir nova solicitação", "/company/custom-portal"));

        return CustomPortalRequestDTO.from(saved);
    }

    // ═══════════════════════ Lado do Admin — plataformas ════════════════

    public List<CustomPortalDTO> listPortals() {
        return customPortalRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(CustomPortalDTO::from).toList();
    }

    public CustomPortalDetailDTO getPortalDetail(Long portalId) {
        CustomPortal portal = portalById(portalId);
        List<CustomPortalStatusHistoryDTO> history = historyRepository
                .findByCustomPortalIdOrderByChangedAtDesc(portalId)
                .stream().map(CustomPortalStatusHistoryDTO::from).toList();
        CustomPortalRequestDTO origin = portal.getOriginRequest() != null
                ? CustomPortalRequestDTO.from(portal.getOriginRequest())
                : null;
        return new CustomPortalDetailDTO(CustomPortalDTO.from(portal), origin, history);
    }

    @Transactional
    public CustomPortalDTO createPortalDirectly(Long adminUserId, CreateCustomPortalDTO body) {
        if (body.companyId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "companyId is required.");
        }
        Company company = companyRepository.findById(body.companyId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Company not found: " + body.companyId()));

        if (company.getStatus() != CompanyStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The company must be approved before a custom portal can be created.");
        }

        User admin = adminById(adminUserId);
        CustomPortal portal = createPortalInternal(
                company, body.subdomain(), body.planName(), body.planPrice(),
                body.subscriptionStartDate(), body.nextDueDate(), body.paymentStatus(),
                admin, null);

        notificationService.notifyCustomPortalRequestApproved(
                company.getUser(), portal.getSubdomain());
        emailCompany(company,
            "Sua plataforma personalizada foi criada — Nexus",
            "Sua plataforma personalizada está pronta!",
            List.of(
                "Olá " + company.getCompanyName() + ",",
                "Um administrador criou a sua plataforma personalizada com o subdomínio \""
                    + portal.getSubdomain() + "\".",
                "Plano: " + portal.getPlanName() + " — R$ " + portal.getPlanPrice()
                    + ". Próximo vencimento: " + portal.getNextDueDate() + "."),
            new EmailTemplateService.Button("Ver minha plataforma", "/company/custom-portal"));

        return CustomPortalDTO.from(portal);
    }

    @Transactional
    public CustomPortalDTO suspend(Long adminUserId, Long portalId, String note) {
        CustomPortal portal = portalById(portalId);
        if (portal.getStatus() != CustomPortalStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only an active custom portal can be suspended.");
        }
        applyStatusChange(portal, CustomPortalStatus.SUSPENDED, adminById(adminUserId), note);

        Company company = portal.getCompany();
        notificationService.notifyCustomPortalSuspended(company.getUser(), note);
        emailCompany(company,
            "Sua plataforma personalizada foi suspensa — Nexus",
            "Sua plataforma personalizada foi suspensa",
            List.of(
                "Olá " + company.getCompanyName() + ",",
                "Sua plataforma personalizada foi suspensa por um administrador"
                    + (note != null && !note.isBlank() ? " (" + note.trim() + ")" : "") + ".",
                "Seu cadastro normal no Nexus continua ativo e inalterado."),
            new EmailTemplateService.Button("Ver detalhes", "/company/custom-portal"));

        return CustomPortalDTO.from(portal);
    }

    @Transactional
    public CustomPortalDTO reactivate(Long adminUserId, Long portalId, String note) {
        CustomPortal portal = portalById(portalId);
        if (portal.getStatus() != CustomPortalStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a suspended custom portal can be reactivated.");
        }
        applyStatusChange(portal, CustomPortalStatus.ACTIVE, adminById(adminUserId), note);

        Company company = portal.getCompany();
        notificationService.notify(company.getUser(),
                com.main.nexus.model.enums.NotificationType.CUSTOM_PORTAL_REQUEST_APPROVED,
                "Plataforma personalizada reativada",
                "Sua plataforma personalizada voltou a ficar ativa.",
                "/company/custom-portal");
        emailCompany(company,
            "Sua plataforma personalizada foi reativada — Nexus",
            "Sua plataforma personalizada foi reativada",
            List.of(
                "Olá " + company.getCompanyName() + ",",
                "Sua plataforma personalizada voltou a ficar ativa."),
            new EmailTemplateService.Button("Ver minha plataforma", "/company/custom-portal"));

        return CustomPortalDTO.from(portal);
    }

    @Transactional
    public CustomPortalDTO cancel(Long adminUserId, Long portalId, String note) {
        CustomPortal portal = portalById(portalId);
        if (portal.getStatus() == CustomPortalStatus.CANCELED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This custom portal is already canceled.");
        }
        applyStatusChange(portal, CustomPortalStatus.CANCELED, adminById(adminUserId), note);
        portal.setPaymentStatus(CustomPortalPaymentStatus.CANCELED);
        customPortalRepository.save(portal);

        Company company = portal.getCompany();
        notificationService.notify(company.getUser(),
                com.main.nexus.model.enums.NotificationType.CUSTOM_PORTAL_SUSPENDED,
                "Plataforma personalizada cancelada",
                "Sua plataforma personalizada foi cancelada."
                    + (note != null && !note.isBlank() ? " Motivo: " + note.trim() : ""),
                "/company/custom-portal");
        emailCompany(company,
            "Sua plataforma personalizada foi cancelada — Nexus",
            "Sua plataforma personalizada foi cancelada",
            List.of(
                "Olá " + company.getCompanyName() + ",",
                "Sua plataforma personalizada foi cancelada"
                    + (note != null && !note.isBlank() ? " (" + note.trim() + ")" : "") + ".",
                "Seu cadastro normal no Nexus continua ativo e inalterado."),
            null);

        return CustomPortalDTO.from(portal);
    }

    @Transactional
    public CustomPortalDTO updateSubscription(Long portalId, UpdateCustomPortalSubscriptionDTO body) {
        CustomPortal portal = portalById(portalId);
        if (portal.getStatus() == CustomPortalStatus.CANCELED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot change the subscription of a canceled custom portal.");
        }
        requirePlan(body.planName(), body.planPrice());
        if (body.nextDueDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A next due date is required.");
        }

        boolean dueDateChanged = !body.nextDueDate().equals(portal.getNextDueDate());
        portal.setPlanName(body.planName().trim());
        portal.setPlanPrice(body.planPrice());
        portal.setNextDueDate(body.nextDueDate());
        if (body.paymentStatus() != null) {
            portal.setPaymentStatus(body.paymentStatus());
        }
        if (dueDateChanged) {
            // Novo ciclo -> permite o lembrete de vencimento disparar de novo.
            portal.setLastRenewalReminderFor(null);
        }
        portal.setUpdatedAt(LocalDateTime.now());
        return CustomPortalDTO.from(customPortalRepository.save(portal));
    }

    // ═══════════════════════ Customização visual (Prompt 2) ════════════
    // Só o contratante dono (…ForUser) ou o Admin (…AsAdmin) editam. A edição
    // segue liberada mesmo com o portal fora de ACTIVE — a publicação é que
    // depende da assinatura (a UI avisa; a página pública real vem no Prompt 3).

    public CustomPortalDTO getBrandingForUser(Long userId) {
        return CustomPortalDTO.from(portalByUser(userId));
    }

    @Transactional
    public CustomPortalDTO updateBrandingForUser(Long userId, UpdateCustomPortalBrandingDTO dto) {
        return CustomPortalDTO.from(applyBranding(portalByUser(userId), dto));
    }

    @Transactional
    public CustomPortalDTO updateBrandingAsAdmin(Long portalId, UpdateCustomPortalBrandingDTO dto) {
        return CustomPortalDTO.from(applyBranding(portalById(portalId), dto));
    }

    @Transactional
    public CustomPortalDTO setBrandingImageForUser(Long userId, BrandingImageKind kind, MultipartFile file) {
        return CustomPortalDTO.from(applyBrandingImage(portalByUser(userId), kind, file));
    }

    @Transactional
    public CustomPortalDTO setBrandingImageAsAdmin(Long portalId, BrandingImageKind kind, MultipartFile file) {
        return CustomPortalDTO.from(applyBrandingImage(portalById(portalId), kind, file));
    }

    @Transactional
    public CustomPortalDTO clearBrandingImageForUser(Long userId, BrandingImageKind kind) {
        return CustomPortalDTO.from(clearBrandingImage(portalByUser(userId), kind));
    }

    @Transactional
    public CustomPortalDTO clearBrandingImageAsAdmin(Long portalId, BrandingImageKind kind) {
        return CustomPortalDTO.from(clearBrandingImage(portalById(portalId), kind));
    }

    private CustomPortal applyBranding(CustomPortal portal, UpdateCustomPortalBrandingDTO dto) {
        portal.setDisplayName(cleanText(dto.displayName(), MAX_DISPLAY_NAME, "Display name"));
        portal.setPrimaryColor(normalizeColor(dto.primaryColor()));
        portal.setAboutText(blankToNull(dto.aboutText()));

        List<CustomPortalSection> parsed = new ArrayList<>();
        if (dto.sections() != null) {
            for (CustomPortalSectionDTO s : dto.sections()) {
                String title = s.title() == null ? "" : s.title().trim();
                String content = s.content() == null ? "" : s.content().trim();
                if (title.isEmpty() && content.isEmpty()) {
                    continue; // linha em branco — descarta em silêncio
                }
                if (title.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "A section title is required.");
                }
                if (title.length() > MAX_SECTION_TITLE) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "A section title must be at most " + MAX_SECTION_TITLE + " characters.");
                }
                parsed.add(new CustomPortalSection(title, content.isEmpty() ? null : content));
            }
        }
        if (parsed.size() > MAX_SECTIONS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You can add at most " + MAX_SECTIONS + " sections.");
        }
        portal.getSections().clear();
        portal.getSections().addAll(parsed);

        portal.setUpdatedAt(LocalDateTime.now());
        return customPortalRepository.save(portal);
    }

    private CustomPortal applyBrandingImage(CustomPortal portal, BrandingImageKind kind, MultipartFile file) {
        // Remove a anterior antes (best-effort — SupabaseStorageService engole falha).
        supabaseStorageService.deleteCustomPortalImage(currentImageUrl(portal, kind));
        String url = supabaseStorageService.uploadCustomPortalImage(
                file, portal.getId(), kind.name().toLowerCase());
        setImageUrl(portal, kind, url);
        portal.setUpdatedAt(LocalDateTime.now());
        return customPortalRepository.save(portal);
    }

    private CustomPortal clearBrandingImage(CustomPortal portal, BrandingImageKind kind) {
        supabaseStorageService.deleteCustomPortalImage(currentImageUrl(portal, kind));
        setImageUrl(portal, kind, null);
        portal.setUpdatedAt(LocalDateTime.now());
        return customPortalRepository.save(portal);
    }

    private static String currentImageUrl(CustomPortal portal, BrandingImageKind kind) {
        return switch (kind) {
            case LOGO -> portal.getLogoUrl();
            case BANNER -> portal.getBannerUrl();
            case FAVICON -> portal.getFaviconUrl();
        };
    }

    private static void setImageUrl(CustomPortal portal, BrandingImageKind kind, String url) {
        switch (kind) {
            case LOGO -> portal.setLogoUrl(url);
            case BANNER -> portal.setBannerUrl(url);
            case FAVICON -> portal.setFaviconUrl(url);
        }
    }

    private static String normalizeColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase();
        if (!HEX_COLOR_PATTERN.matcher(value).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid color. Use a hex value like #4f46e5.");
        }
        return value;
    }

    private static String cleanText(String raw, int max, String label) {
        String value = blankToNull(raw);
        if (value != null && value.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    label + " must be at most " + max + " characters.");
        }
        return value;
    }

    private static String blankToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private CustomPortal portalByUser(Long userId) {
        Company company = companyByUser(userId);
        return customPortalRepository.findByCompanyId(company.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Custom portal not found for this company."));
    }

    // ═══════════════════════ Página pública (Prompt 3) ════════════════

    /**
     * Resolve o portal por subdomínio para a página pública em
     * empresa.nexus.com.br. 404 se o subdomínio não existir ou a empresa dona
     * não estiver APPROVED. Devolve qualquer status de ciclo de vida —
     * ACTIVE/SUSPENDED/CANCELED — e o frontend decide o que renderizar
     * (página vs "plataforma indisponível").
     */
    public PublicCustomPortalDTO getPublicBySubdomain(String subdomain) {
        String normalized = subdomain == null ? "" : subdomain.trim().toLowerCase();
        CustomPortal portal = customPortalRepository.findBySubdomainIgnoreCase(normalized)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No custom portal for this subdomain."));

        if (portal.getCompany().getStatus() != CompanyStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "No custom portal for this subdomain.");
        }
        return PublicCustomPortalDTO.from(portal);
    }

    // ═══════════════════════ Job diário ════════════════════════════════

    /** Aviso "assinatura perto do vencimento" — chamado pelo NexusScheduler. */
    @Transactional
    public void notifyUpcomingRenewals() {
        LocalDate limit = LocalDate.now().plusDays(RENEWAL_REMINDER_DAYS);
        List<CustomPortal> due = customPortalRepository
                .findByStatusAndNextDueDateLessThanEqual(CustomPortalStatus.ACTIVE, limit);

        for (CustomPortal portal : due) {
            // Uma vez por ciclo de vencimento — se o job repetir no dia seguinte,
            // ou a assinatura estiver atrasada ha dias, nao remanda.
            if (portal.getNextDueDate().equals(portal.getLastRenewalReminderFor())) {
                continue;
            }
            Company company = portal.getCompany();
            notificationService.notifyCustomPortalRenewalDue(
                    company.getUser(), portal.getNextDueDate());
            emailCompany(company,
                "Sua assinatura vence em breve — Nexus",
                "Assinatura da plataforma personalizada perto do vencimento",
                List.of(
                    "Olá " + company.getCompanyName() + ",",
                    "A assinatura da sua plataforma personalizada (\"" + portal.getSubdomain()
                        + "\") vence em " + portal.getNextDueDate() + ".",
                    "Plano: " + portal.getPlanName() + " — R$ " + portal.getPlanPrice() + ".",
                    "Regularize o pagamento para não ter a plataforma suspensa."),
                new EmailTemplateService.Button("Ver assinatura", "/company/custom-portal"));

            portal.setLastRenewalReminderFor(portal.getNextDueDate());
            customPortalRepository.save(portal);
        }
    }

    // ═══════════════════════ Internos ═════════════════════════════════

    private CustomPortal createPortalInternal(
            Company company, String rawSubdomain, String planName, BigDecimal planPrice,
            LocalDate startDate, LocalDate nextDueDate, CustomPortalPaymentStatus paymentStatus,
            User admin, CustomPortalRequest originRequest) {

        if (customPortalRepository.existsByCompanyId(company.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This company already has a custom portal.");
        }

        String subdomain = normalizeAndValidateSubdomain(rawSubdomain);
        if (customPortalRepository.existsBySubdomainIgnoreCase(subdomain)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This subdomain is already in use.");
        }

        requirePlan(planName, planPrice);
        if (startDate == null || nextDueDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Subscription start date and next due date are required.");
        }
        if (nextDueDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The next due date cannot be before the subscription start date.");
        }

        CustomPortal portal = new CustomPortal();
        portal.setCompany(company);
        portal.setOriginRequest(originRequest);
        portal.setStatus(CustomPortalStatus.ACTIVE);
        portal.setSubdomain(subdomain);
        portal.setPlanName(planName.trim());
        portal.setPlanPrice(planPrice);
        portal.setSubscriptionStartDate(startDate);
        portal.setNextDueDate(nextDueDate);
        portal.setPaymentStatus(paymentStatus != null ? paymentStatus : CustomPortalPaymentStatus.UP_TO_DATE);
        portal.setCreatedByAdmin(admin);
        portal.setCreatedAt(LocalDateTime.now());
        portal.setUpdatedAt(LocalDateTime.now());
        CustomPortal saved = customPortalRepository.save(portal);

        CustomPortalStatusHistory row = new CustomPortalStatusHistory();
        row.setCustomPortal(saved);
        row.setPreviousStatus(null);
        row.setNewStatus(CustomPortalStatus.ACTIVE);
        row.setChangedBy(admin);
        row.setNote(originRequest != null
                ? "Criado a partir da solicitação #" + originRequest.getId()
                : "Criado diretamente pelo administrador");
        historyRepository.save(row);

        return saved;
    }

    private void applyStatusChange(CustomPortal portal, CustomPortalStatus newStatus,
                                   User admin, String note) {
        CustomPortalStatus previous = portal.getStatus();
        portal.setStatus(newStatus);
        portal.setUpdatedAt(LocalDateTime.now());
        customPortalRepository.save(portal);

        CustomPortalStatusHistory row = new CustomPortalStatusHistory();
        row.setCustomPortal(portal);
        row.setPreviousStatus(previous);
        row.setNewStatus(newStatus);
        row.setChangedBy(admin);
        row.setNote(note != null && !note.isBlank() ? note.trim() : null);
        historyRepository.save(row);
    }

    private String normalizeAndValidateSubdomain(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A subdomain is required.");
        }
        String value = raw.trim().toLowerCase();
        if (!SUBDOMAIN_PATTERN.matcher(value).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid subdomain. Use 3 to 63 lowercase letters, digits or hyphens, "
                    + "and do not start or end with a hyphen.");
        }
        if (RESERVED_SUBDOMAINS.contains(value)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This subdomain is reserved.");
        }
        return value;
    }

    private void requirePlan(String planName, BigDecimal planPrice) {
        if (planName == null || planName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A plan name is required.");
        }
        if (planPrice == null || planPrice.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A plan price of zero or more is required.");
        }
    }

    private Company companyByUser(Long userId) {
        return companyRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Company profile not found"));
    }

    private CustomPortal portalById(Long portalId) {
        return customPortalRepository.findById(portalId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Custom portal not found: " + portalId));
    }

    // Admin responsavel pela acao — usado so para auditoria (reviewedBy /
    // createdByAdmin / changedBy), nunca bloqueia o fluxo se nao achar.
    private User adminById(Long adminUserId) {
        return adminUserId == null ? null : userRepository.findById(adminUserId).orElse(null);
    }

    private void emailCompany(Company company, String subject, String heading,
                              List<String> paragraphs, EmailTemplateService.Button button) {
        if (company.getUser() == null || company.getUser().getEmail() == null) {
            return;
        }
        emailService.sendHtml(
                company.getUser().getEmail(),
                subject,
                emailTemplate.render(heading, paragraphs, button),
                emailTemplate.renderText(heading, paragraphs, button));
    }
}
