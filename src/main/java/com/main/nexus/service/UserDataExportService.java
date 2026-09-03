package com.main.nexus.service;

import com.main.nexus.model.Company;
import com.main.nexus.model.CustomPortal;
import com.main.nexus.model.Match;
import com.main.nexus.model.Message;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.Proposal;
import com.main.nexus.model.Review;
import com.main.nexus.model.ScreeningAnswer;
import com.main.nexus.model.ScreeningInvitation;
import com.main.nexus.model.SupportConversation;
import com.main.nexus.model.SupportMessage;
import com.main.nexus.model.User;
import com.main.nexus.model.UserConsent;
import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.UserType;
import com.main.nexus.repository.CommissionChargeRepository;
import com.main.nexus.repository.CompanyBillingProfileRepository;
import com.main.nexus.repository.CompanyFiscalProfileRepository;
import com.main.nexus.repository.CustomPortalRepository;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.MessageRepository;
import com.main.nexus.repository.NfseInvoiceRepository;
import com.main.nexus.repository.NotificationRepository;
import com.main.nexus.repository.PortalSubscriptionChargeRepository;
import com.main.nexus.repository.PreviousProjectRepository;
import com.main.nexus.repository.ProfessionalCredentialRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.ProposalRepository;
import com.main.nexus.repository.ReviewRepository;
import com.main.nexus.repository.ScreeningInvitationRepository;
import com.main.nexus.repository.SupportConversationRepository;
import com.main.nexus.repository.SupportMessageRepository;
import com.main.nexus.repository.UserConsentRepository;
import com.main.nexus.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Direito de portabilidade (LGPD, art. 18, V). {@code GET /api/users/me/export}
 * devolve, em JSON estruturado, todos os dados PESSOAIS do próprio titular.
 *
 * Reaproveita o mapeamento de entidades do Passo 0 do Prompt 2. Minimização de
 * dado de terceiro (Rule 2):
 *  - de cada match/proposta/review/mensagem entra o lado do titular por inteiro;
 *  - da OUTRA parte entra só o nome de exibição + id (informação já visível no
 *    app, e sem a qual o registro do titular fica ininteligível) -- nunca
 *    e-mail, telefone, CPF/CNPJ ou endereço de terceiro;
 *  - de mensagens de chat entra só o que o titular escreveu;
 *  - de triagem entram as respostas do titular + o enunciado das questões;
 *  - do suporte entra a conversa inteira, com o operador identificado só como
 *    "Equipe Nexus".
 *
 * Volume: no porte atual o JSON fica em poucos KB -- geração SÍNCRONA, sem job
 * nem arquivo intermediário (Rule 3: não super-engenheirar). Se crescer, migra
 * para assíncrono aqui.
 */
@Service
public class UserDataExportService {

    private static final String OPERATOR_LABEL = "Equipe Nexus";

    @Autowired private UserRepository userRepository;
    @Autowired private ProfessionalService professionalService;
    @Autowired private CompanyService companyService;
    @Autowired private PreviousProjectRepository previousProjectRepository;
    @Autowired private ProfessionalCredentialRepository credentialRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private ProposalRepository proposalRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private UserConsentRepository consentRepository;
    @Autowired private ScreeningInvitationRepository screeningInvitationRepository;
    @Autowired private SupportConversationRepository supportConversationRepository;
    @Autowired private SupportMessageRepository supportMessageRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private CommissionChargeRepository commissionChargeRepository;
    @Autowired private NfseInvoiceRepository nfseInvoiceRepository;
    @Autowired private PortalSubscriptionChargeRepository portalSubscriptionChargeRepository;
    @Autowired private CompanyBillingProfileRepository billingProfileRepository;
    @Autowired private CompanyFiscalProfileRepository fiscalProfileRepository;
    @Autowired private CustomPortalRepository customPortalRepository;
    @Autowired private EmailService emailService;

    @Transactional(readOnly = true)
    public Map<String, Object> export(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("exportedAt", LocalDateTime.now());
        root.put("format", "Nexus data export (LGPD art. 18, V) - v1");
        root.put("account", accountSection(user));
        root.put("consentHistory", consentsSection(user.getId()));
        root.put("notifications", notificationsSection(user.getId()));

        if (user.getType() == UserType.PROFESSIONAL) {
            professionalService.findByUserId(user.getId()).ifPresent(p -> {
                root.put("professionalProfile", professionalProfile(p));
                root.put("portfolio", portfolio(p.getId()));
                root.put("credentials", credentials(p.getId()));
                root.put("matches", professionalMatches(p.getId()));
                root.put("proposalsSent", professionalProposals(p.getId()));
                root.put("reviews", reviewsForProfessional(p.getId()));
                root.put("screeningInvitations", screeningForProfessional(p.getId()));
            });
        } else if (user.getType() == UserType.COMPANY) {
            companyService.findByUserId(user.getId()).ifPresent(c -> {
                root.put("companyProfile", companyProfile(c));
                root.put("opportunities", companyProjects(c.getId()));
                root.put("matches", companyMatches(c.getId()));
                root.put("proposalsReceived", companyProposals(c.getId()));
                root.put("reviews", reviewsForCompany(c.getId()));
                root.put("screeningProcesses", screeningForCompany(c.getId()));
                root.put("billing", companyBilling(c.getId()));
                root.put("customPortal", companyCustomPortal(c.getId()));
            });
        }

        root.put("chatMessagesSent", messagesSent(user.getId()));
        root.put("support", supportSection(user.getId()));

        emailService.send(
                user.getEmail(),
                "Exportação dos seus dados solicitada — Nexus",
                "Olá,\n\nUma exportação dos seus dados pessoais no Nexus foi solicitada e gerada agora.\n\n"
                + "Se NÃO foi você, troque a sua senha imediatamente e entre em contato com o suporte.\n\n"
                + "Equipe Nexus");

        return root;
    }

    // ── seções comuns ─────────────────────────────────────────────────

    private Map<String, Object> accountSection(User user) {
        return obj(
                "id", user.getId(),
                "email", user.getEmail(),
                "type", user.getType().name(),
                "active", user.getActive(),
                "createdAt", user.getCreatedAt(),
                "lastLoginAt", user.getLastLoginAt(),
                "linkedLoginProviders", user.getLinkedinId() != null ? List.of("LINKEDIN") : List.of(),
                "deletionRequestedAt", user.getDeletionRequestedAt(),
                "anonymizedAt", user.getAnonymizedAt());
    }

    private List<Map<String, Object>> consentsSection(Long userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserConsent c : consentRepository.findByUserIdOrderByCreatedAtAsc(userId)) {
            out.add(obj(
                    "type", c.getType().name(),
                    "granted", c.getGranted(),
                    "documentType", c.getDocumentType() != null ? c.getDocumentType().name() : null,
                    "documentVersion", c.getDocumentVersion(),
                    "source", c.getSource().name(),
                    "at", c.getCreatedAt()));
        }
        return out;
    }

    private List<Map<String, Object>> notificationsSection(Long userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).forEach(n -> out.add(obj(
                "type", n.getType().name(),
                "title", n.getTitle(),
                "message", n.getMessage(),
                "read", n.getRead(),
                "createdAt", n.getCreatedAt())));
        return out;
    }

    // ── profissional ─────────────────────────────────────────────────

    private Map<String, Object> professionalProfile(Professional p) {
        return obj(
                "name", p.getName(),
                "phone", p.getPhone(),
                "city", p.getCity(),
                "uf", p.getUf(),
                "cep", p.getCep(),
                "latitude", p.getLatitude(),
                "longitude", p.getLongitude(),
                "resumeFileUrl", p.getResume(),
                "profilePhotoUrl", p.getProfilePhotoUrl(),
                "experienceLevel", nameOrNull(p.getExperienceLevel()),
                "available", p.getAvailable(),
                "reputation", p.getReputation(),
                "expectedSalaryCLT", p.getExpectedSalaryCLT(),
                "expectedSalaryPJ", p.getExpectedSalaryPJ(),
                "freelanceMinExpectation", p.getFreelanceMinExpectation(),
                "freelanceMaxExpectation", p.getFreelanceMaxExpectation(),
                "linkedinUrl", p.getLinkedinUrl(),
                "githubUrl", p.getGithubUrl(),
                "skills", p.getSkills().stream().map(com.main.nexus.model.Skill::getName).toList(),
                "preferredProjectTypes", p.getPreferredTypes().stream().map(Enum::name).toList(),
                "preferredOpportunityTypes",
                        p.getPreferredOpportunityTypes().stream().map(Enum::name).toList());
    }

    private List<Map<String, Object>> portfolio(Long professionalId) {
        List<Map<String, Object>> out = new ArrayList<>();
        previousProjectRepository.findByProfessionalId(professionalId).forEach(pp -> out.add(obj(
                "title", pp.getTitle(),
                "description", pp.getDescription(),
                "technologies", pp.getTechnologies(),
                "yearOfCompletion", pp.getYearOfCompletion())));
        return out;
    }

    private List<Map<String, Object>> credentials(Long professionalId) {
        List<Map<String, Object>> out = new ArrayList<>();
        credentialRepository.findByProfessionalId(professionalId).forEach(cr -> out.add(obj(
                "type", cr.getType().name(),
                "name", cr.getName(),
                "color", cr.getColor().name())));
        return out;
    }

    private List<Map<String, Object>> professionalMatches(Long professionalId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Match m : matchRepository.findByProfessionalId(professionalId)) {
            out.add(matchRow(m, companyName(projectCompany(m))));
        }
        return out;
    }

    private List<Map<String, Object>> professionalProposals(Long professionalId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Proposal pr : proposalRepository.findByProfessionalId(professionalId)) {
            Map<String, Object> row = proposalRow(pr, true); // anexos são do próprio titular
            row.put("toOpportunity", opportunityContext(pr.getProject()));
            out.add(row);
        }
        return out;
    }

    private Map<String, Object> reviewsForProfessional(Long professionalId) {
        List<Map<String, Object>> received = new ArrayList<>();
        List<Map<String, Object>> written = new ArrayList<>();
        for (Review r : reviewRepository.findByMatchProfessionalId(professionalId)) {
            String other = companyName(r.getMatch().getProject().getCompany());
            if (r.getAuthorType() == AuthorType.COMPANY) {
                received.add(reviewRow(r, "from", other));
            } else {
                written.add(reviewRow(r, "about", other));
            }
        }
        return obj("received", received, "written", written);
    }

    private List<Map<String, Object>> screeningForProfessional(Long professionalId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ScreeningInvitation si : screeningInvitationRepository.findByProfessionalId(professionalId)) {
            Project project = si.getScreeningStage().getScreeningQuestionnaire().getProject();
            out.add(obj(
                    "opportunity", opportunityContext(project),
                    "stageTitle", si.getScreeningStage().getTitle(),
                    "status", si.getStatus().name(),
                    "sentAt", si.getSentAt(),
                    "startedAt", si.getStartedAt(),
                    "submittedAt", si.getSubmittedAt(),
                    "decidedAt", si.getDecidedAt(),
                    "totalTimeSpentSeconds", si.getTotalTimeSpentSeconds(),
                    "tabSwitchCount", si.getTabSwitchCount(),
                    "autoScorePercent", si.getAutoScorePercent(),
                    "companyDecisionComment", si.getCompanyDecisionComment(),
                    "myAnswers", screeningAnswers(si.getAnswers())));
        }
        return out;
    }

    private List<Map<String, Object>> screeningAnswers(List<ScreeningAnswer> answers) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (answers == null) {
            return out;
        }
        for (ScreeningAnswer a : answers) {
            out.add(obj(
                    "question", a.getScreeningQuestion() != null ? a.getScreeningQuestion().getPrompt() : null,
                    "questionType", a.getScreeningQuestion() != null
                            ? a.getScreeningQuestion().getType().name() : null,
                    "selectedOptionIndex", a.getSelectedOptionIndex(),
                    "essayText", a.getEssayText(),
                    "correct", a.getCorrect(),
                    "timeSpentSeconds", a.getTimeSpentSeconds()));
        }
        return out;
    }

    // ── contratante ──────────────────────────────────────────────────

    private Map<String, Object> companyProfile(Company c) {
        return obj(
                "companyName", c.getCompanyName(),
                "type", c.getType().name(),
                "taxId", c.getTaxId(),
                "phone", c.getPhone(),
                "city", c.getCity(),
                "uf", c.getUf(),
                "cep", c.getCep(),
                "latitude", c.getLatitude(),
                "longitude", c.getLongitude(),
                "description", c.getDescription(),
                "profilePhotoUrl", c.getProfilePhotoUrl(),
                "linkedinUrl", c.getLinkedinUrl(),
                "status", c.getStatus().name(),
                "reputation", c.getReputation(),
                "successfulHiresCount", c.getSuccessfulHiresCount());
    }

    private List<Map<String, Object>> companyProjects(Long companyId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Project p : projectRepository.findByCompanyId(companyId)) {
            out.add(obj(
                    "id", p.getId(),
                    "title", p.getTitle(),
                    "description", p.getDescription(),
                    "status", p.getStatus().name(),
                    "opportunityType", nameOrNull(p.getOpportunityType()),
                    "city", p.getCity(),
                    "uf", p.getUf(),
                    "minimumBudget", p.getMinimumBudget(),
                    "maximumBudget", p.getMaximumBudget(),
                    "monthlySalaryMin", p.getMonthlySalaryMin(),
                    "monthlySalaryMax", p.getMonthlySalaryMax(),
                    "createdAt", p.getCreatedAt()));
        }
        return out;
    }

    private List<Map<String, Object>> companyMatches(Long companyId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Match m : matchRepository.findByProjectCompanyId(companyId)) {
            out.add(matchRow(m, professionalName(m.getProfessional())));
        }
        return out;
    }

    private List<Map<String, Object>> companyProposals(Long companyId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Proposal pr : proposalRepository.findByProjectCompanyId(companyId)) {
            // includeAttachments=false: o nome do arquivo do anexo pode conter o
            // nome do profissional (ex.: "Curriculo_Joao_Silva.pdf") -- não é
            // estritamente necessário no export do CONTRATANTE (Rule 2).
            Map<String, Object> row = proposalRow(pr, false);
            row.put("forOpportunity", opportunityContext(pr.getProject()));
            row.put("fromProfessional", professionalName(pr.getProfessional()));
            out.add(row);
        }
        return out;
    }

    private Map<String, Object> reviewsForCompany(Long companyId) {
        List<Map<String, Object>> received = new ArrayList<>();
        List<Map<String, Object>> written = new ArrayList<>();
        for (Review r : reviewRepository.findByMatchProjectCompanyId(companyId)) {
            String other = professionalName(r.getMatch().getProfessional());
            if (r.getAuthorType() == AuthorType.PROFESSIONAL) {
                received.add(reviewRow(r, "from", other));
            } else {
                written.add(reviewRow(r, "about", other));
            }
        }
        return obj("received", received, "written", written);
    }

    private List<Map<String, Object>> screeningForCompany(Long companyId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ScreeningInvitation si
                : screeningInvitationRepository
                        .findByScreeningStageScreeningQuestionnaireProjectCompanyId(companyId)) {
            Project project = si.getScreeningStage().getScreeningQuestionnaire().getProject();
            out.add(obj(
                    "opportunity", opportunityContext(project),
                    "candidate", professionalName(si.getProfessional()),
                    "stageTitle", si.getScreeningStage().getTitle(),
                    "status", si.getStatus().name(),
                    "autoScorePercent", si.getAutoScorePercent(),
                    "companyDecisionComment", si.getCompanyDecisionComment(),
                    "decidedAt", si.getDecidedAt()));
        }
        return out;
    }

    private Map<String, Object> companyBilling(Long companyId) {
        Map<String, Object> out = new LinkedHashMap<>();

        List<Map<String, Object>> commissionCharges = new ArrayList<>();
        commissionChargeRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).forEach(cc ->
                commissionCharges.add(obj(
                        "baseAmount", cc.getBaseAmount(),
                        "percentage", cc.getPercentage(),
                        "amount", cc.getAmount(),
                        "status", cc.getStatus().name(),
                        "createdAt", cc.getCreatedAt(),
                        "paidAt", cc.getPaidAt())));
        out.put("commissionCharges", commissionCharges);

        List<Map<String, Object>> invoices = new ArrayList<>();
        nfseInvoiceRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).forEach(inv ->
                invoices.add(obj(
                        "kind", inv.getKind().name(),
                        "status", inv.getStatus().name(),
                        "numero", inv.getNumero(),
                        "serie", inv.getSerie(),
                        "linkPdf", inv.getLinkPdf(),
                        "linkXml", inv.getLinkXml(),
                        "issuedAt", inv.getIssuedAt(),
                        "createdAt", inv.getCreatedAt())));
        out.put("serviceInvoices", invoices);

        List<Map<String, Object>> subs = new ArrayList<>();
        portalSubscriptionChargeRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).forEach(sc ->
                subs.add(obj(
                        "amount", sc.getAmount(),
                        "dueDate", sc.getDueDate(),
                        "status", sc.getStatus().name(),
                        "createdAt", sc.getCreatedAt(),
                        "paidAt", sc.getPaidAt())));
        out.put("portalSubscriptionCharges", subs);

        billingProfileRepository.findByCompanyId(companyId).ifPresent(bp -> out.put("paymentMethod", obj(
                "cardBrand", bp.getCardBrand(),
                "cardLast4", bp.getCardLast4(),
                "cardExpMonth", bp.getCardExpMonth(),
                "cardExpYear", bp.getCardExpYear(),
                "cardholderName", bp.getCardholderName(),
                "paymentBlocked", bp.getPaymentBlocked())));

        fiscalProfileRepository.findByCompanyId(companyId).ifPresent(fp -> out.put("fiscalProfile", obj(
                "legalName", fp.getLegalName(),
                "fiscalEmail", fp.getFiscalEmail(),
                "street", fp.getStreet(),
                "number", fp.getNumber(),
                "complement", fp.getComplement(),
                "district", fp.getDistrict(),
                "cityIbgeCode", fp.getCityIbgeCode())));

        return out;
    }

    private Map<String, Object> companyCustomPortal(Long companyId) {
        CustomPortal cp = customPortalRepository.findByCompanyId(companyId).orElse(null);
        if (cp == null) {
            return null;
        }
        return obj(
                "subdomain", cp.getSubdomain(),
                "displayName", cp.getDisplayName(),
                "status", nameOrNull(cp.getStatus()),
                "planName", cp.getPlanName(),
                "planPrice", cp.getPlanPrice(),
                "aboutText", cp.getAboutText());
    }

    // ── chat + suporte ───────────────────────────────────────────────

    private List<Map<String, Object>> messagesSent(Long userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Message m : messageRepository.findBySenderIdOrderBySentAtAsc(userId)) {
            Match match = m.getMatch();
            out.add(obj(
                    "matchId", match != null ? match.getId() : null,
                    "withOpportunity", match != null ? opportunityContext(match.getProject()) : null,
                    "content", m.getContent(),
                    "sentAt", m.getSentAt()));
        }
        return out;
    }

    private Map<String, Object> supportSection(Long userId) {
        List<Map<String, Object>> conversations = new ArrayList<>();
        for (SupportConversation conv
                : supportConversationRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            List<Map<String, Object>> msgs = new ArrayList<>();
            for (SupportMessage sm
                    : supportMessageRepository.findByConversationIdOrderBySentAtAsc(conv.getId())) {
                boolean fromMe = sm.getSender() != null && sm.getSender().getId().equals(userId);
                msgs.add(obj(
                        "from", fromMe ? "me" : OPERATOR_LABEL,
                        "content", sm.getContent(),
                        "sentAt", sm.getSentAt()));
            }
            conversations.add(obj(
                    "subject", conv.getSubject(),
                    "status", conv.getStatus().name(),
                    "createdAt", conv.getCreatedAt(),
                    "closedAt", conv.getClosedAt(),
                    "messages", msgs));
        }
        return obj("conversations", conversations);
    }

    // ── helpers ──────────────────────────────────────────────────────

    private Map<String, Object> matchRow(Match m, Object counterparty) {
        return obj(
                "id", m.getId(),
                "counterparty", counterparty,
                "opportunity", opportunityContext(m.getProject()),
                "matchScore", m.getMatchScore(),
                "companyStatus", nameOrNull(m.getCompanyStatus()),
                "professionalStatus", nameOrNull(m.getProfessionalStatus()),
                "status", nameOrNull(m.getStatus()),
                "initiatedBy", nameOrNull(m.getInitiatedBy()),
                "createdAt", m.getCreatedAt());
    }

    private Map<String, Object> proposalRow(Proposal pr, boolean includeAttachmentNames) {
        Map<String, Object> row = obj(
                "id", pr.getId(),
                "proposedValue", pr.getProposedValue(),
                "estimatedDays", pr.getEstimatedDays(),
                "proposedStartDate", pr.getProposedStartDate(),
                "proposedDeliveryDate", pr.getProposedDeliveryDate(),
                "description", pr.getDescription(),
                "relevantExperience", pr.getRelevantExperience(),
                "deliverables", pr.getDeliverables(),
                "executionSteps", pr.getExecutionSteps(),
                "paymentTerms", pr.getPaymentTerms(),
                "questionsForCompany", pr.getQuestionsForCompany(),
                "validityDays", pr.getValidityDays(),
                "status", nameOrNull(pr.getStatus()),
                "matchScoreAtSubmission", pr.getMatchScoreAtSubmission(),
                "createdAt", pr.getCreatedAt());
        if (includeAttachmentNames && pr.getAttachments() != null) {
            List<String> names = new ArrayList<>();
            pr.getAttachments().forEach(a -> names.add(a.getFileName()));
            row.put("attachmentFileNames", names);
        }
        return row;
    }

    private Map<String, Object> reviewRow(Review r, String counterpartyLabel, String counterpartyName) {
        return obj(
                "rating", r.getRating(),
                "comment", r.getComment(),
                "positiveReasons", r.getPositiveReasons() != null
                        ? r.getPositiveReasons().stream().map(Enum::name).toList() : List.of(),
                "negativeReasons", r.getNegativeReasons() != null
                        ? r.getNegativeReasons().stream().map(Enum::name).toList() : List.of(),
                counterpartyLabel, counterpartyName,
                "createdAt", r.getCreatedAt());
    }

    private Map<String, Object> opportunityContext(Project p) {
        if (p == null) {
            return null;
        }
        return obj(
                "id", p.getId(),
                "title", p.getTitle(),
                "company", p.getCompany() != null ? companyName(p.getCompany()) : null);
    }

    private Company projectCompany(Match m) {
        return m.getProject() != null ? m.getProject().getCompany() : null;
    }

    private String companyName(Company c) {
        return c == null ? null : c.getCompanyName();
    }

    private String professionalName(Professional p) {
        return p == null ? null : p.getName();
    }

    private static String nameOrNull(Enum<?> e) {
        return e == null ? null : e.name();
    }

    private static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
