package com.main.nexus.service;

import com.main.nexus.model.Company;
import com.main.nexus.model.CompanyBillingProfile;
import com.main.nexus.model.CompanyFiscalProfile;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Project;
import com.main.nexus.model.ProposalAttachment;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.CompanyType;
import com.main.nexus.model.enums.ProjectStatus;
import com.main.nexus.model.enums.UserType;
import com.main.nexus.repository.CommissionChargeRepository;
import com.main.nexus.repository.CompanyBillingProfileRepository;
import com.main.nexus.repository.CompanyFiscalProfileRepository;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.NfseInvoiceRepository;
import com.main.nexus.repository.NotificationRepository;
import com.main.nexus.repository.PreviousProjectRepository;
import com.main.nexus.repository.ProfessionalCredentialRepository;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.repository.ProjectRepository;
import com.main.nexus.repository.ProposalAttachmentRepository;
import com.main.nexus.repository.SectionViewRepository;
import com.main.nexus.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Direito de eliminação (LGPD, art. 18, VI). O titular pede a exclusão pela rota
 * autenticada {@code DELETE /api/users/me}; NÃO anonimiza na hora -- manda um
 * e-mail com link de confirmação (48h) para o endereço ORIGINAL. Só a
 * confirmação ({@code POST /api/users/me/deletion/confirm}) dispara a
 * anonimização, evitando exclusão por acesso indevido à conta.
 *
 * A anonimização (ver {@link #anonymize}) foi desenhada a partir da classificação
 * entidade a entidade do Passo 0 do Prompt 2:
 *  (a) dado do próprio titular sem função de integridade p/ terceiros -> anonimiza
 *      ou apaga (nome, e-mail, telefone, foto, currículo, localização,
 *      portfólio, credenciais, notificações, "visto" de sidebar);
 *  (b) registro fiscal/financeiro -> NUNCA tocado (CommissionCharge, NfseInvoice,
 *      PortalSubscriptionCharge; CompanyFiscalProfile só se houver fato gerador);
 *  (c) integridade de dado de OUTRA pessoa -> intacto (matches, reviews,
 *      propostas, respostas de triagem, chat, suporte) -- a anonimização da
 *      identidade em User/Professional/Company já resolve a exposição, e a tela
 *      passa a exibir "Usuário removido" no lugar do nome.
 *
 * Invalidação de sessão (Rule 4): {@code active=false} + e-mail/senha embaralhados
 * (login novo impossível). O JWT é stateless (8h) -- há uma janela residual de
 * até 8h em que um token já emitido continua valendo; decisão consciente de
 * manter simples (sem blocklist/checagem por request).
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    @Autowired private UserRepository userRepository;
    @Autowired private ProfessionalRepository professionalRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private PreviousProjectRepository previousProjectRepository;
    @Autowired private ProfessionalCredentialRepository professionalCredentialRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private SectionViewRepository sectionViewRepository;
    @Autowired private ProposalAttachmentRepository proposalAttachmentRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private CompanyBillingProfileRepository billingProfileRepository;
    @Autowired private CompanyFiscalProfileRepository fiscalProfileRepository;
    @Autowired private CommissionChargeRepository commissionChargeRepository;
    @Autowired private NfseInvoiceRepository nfseInvoiceRepository;
    @Autowired private SupabaseStorageService storageService;
    @Autowired private MercadoPagoClient mercadoPago;
    @Autowired private TokenService tokenService;
    @Autowired private EmailService emailService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${nexus.frontend.base-url}")
    private String frontendBaseUrl;

    public static final String ANONYMOUS_NAME = "Usuário removido";

    // ── 1. pedido (rota autenticada) ───────────────────────────────────

    @Transactional
    public void requestDeletion(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        if (user.getType() == UserType.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Administrators cannot self-delete through this route.");
        }
        if (user.getAnonymizedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This account has already been deleted.");
        }

        LocalDateTime requestedAt = LocalDateTime.now();
        user.setDeletionRequestedAt(requestedAt);
        userRepository.save(user);

        // Epoch-SEGUNDOS (não millis): robusto a datetime vs datetime(6) no
        // MySQL. Dois pedidos no mesmo segundo não é cenário real deste fluxo.
        long requestedAtSec = requestedAt.atZone(ZoneId.systemDefault()).toEpochSecond();
        String token = tokenService.generateAccountDeletionToken(user.getId(), requestedAtSec);
        String link = frontendBaseUrl + "/account/delete?token=" + token;

        emailService.send(
                user.getEmail(),
                "Confirme a exclusão da sua conta — Nexus",
                "Olá,\n\nRecebemos um pedido para excluir permanentemente a sua conta no Nexus e "
                + "anonimizar seus dados pessoais.\n\n"
                + "Para confirmar, acesse o link abaixo (válido por 48 horas):\n" + link + "\n\n"
                + "Se você NÃO solicitou isto, ignore este e-mail e troque a sua senha por precaução — "
                + "sem a confirmação, nada é alterado.\n\n"
                + "Observação: registros fiscais e financeiros (cobranças e notas fiscais já emitidas) "
                + "são mantidos pelo prazo exigido pela legislação, mesmo após a exclusão.\n\nEquipe Nexus");
    }

    // ── 2. confirmação (rota pública, o token é a credencial) ──────────

    @Transactional
    public void confirmDeletion(String rawToken) {
        TokenService.AccountDeletionToken claims;
        try {
            claims = tokenService.extractAccountDeletionToken(rawToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Confirmation link is invalid or has expired. Please request the deletion again.");
        }

        User user = userRepository.findById(claims.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        if (user.getAnonymizedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This account has already been deleted.");
        }
        if (user.getDeletionRequestedAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No pending deletion request for this account.");
        }
        long currentRequestSec = user.getDeletionRequestedAt()
                .atZone(ZoneId.systemDefault()).toEpochSecond();
        if (currentRequestSec != claims.requestedAt()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "This confirmation link is no longer valid (a newer request was made).");
        }

        anonymize(user);
    }

    // ── 3. anonimização ───────────────────────────────────────────────

    private void anonymize(User user) {
        // E-mail de conclusão para o endereço ORIGINAL, ANTES de embaralhar.
        String originalEmail = user.getEmail();
        emailService.send(
                originalEmail,
                "Sua conta foi excluída — Nexus",
                "Olá,\n\nSua conta no Nexus foi excluída e seus dados pessoais foram anonimizados, "
                + "conforme solicitado.\n\n"
                + "Matches, avaliações e conversas anteriores passam a exibir \"" + ANONYMOUS_NAME
                + "\" no seu lugar. Registros fiscais e financeiros (cobranças e notas fiscais já "
                + "emitidas) são mantidos pelo prazo exigido pela legislação.\n\n"
                + "Este é o último e-mail que enviaremos para este endereço.\n\nEquipe Nexus");

        switch (user.getType()) {
            case PROFESSIONAL -> anonymizeProfessional(user);
            case COMPANY -> anonymizeCompany(user);
            default -> { /* ADMIN nunca chega aqui (barrado em requestDeletion) */ }
        }

        // Dado do próprio usuário, sem função de integridade p/ terceiros.
        notificationRepository.deleteByUserId(user.getId());
        sectionViewRepository.deleteByUserId(user.getId());

        // Identidade compartilhada (User): anonimiza em vez de apagar a linha
        // (é FK de match, review, mensagem, notificação, suporte...).
        user.setEmail("deleted-" + user.getId() + "@removido.nexus");
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setLinkedinId(null);
        user.setActive(false);
        user.setAnonymizedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("LGPD: conta {} (tipo {}) anonimizada.", user.getId(), user.getType());
    }

    private void anonymizeProfessional(User user) {
        Professional p = professionalRepository.findByUserId(user.getId()).orElse(null);
        if (p == null) {
            return;
        }

        // (a) apagar arquivos físicos + portfólio + credenciais
        deleteFileQuietly(p.getResume(), storageService::deleteResume);
        deleteFileQuietly(p.getProfilePhotoUrl(), storageService::deleteProfilePhoto);
        deleteProposalAttachmentFiles(p.getId());
        previousProjectRepository.deleteByProfessionalId(p.getId());
        professionalCredentialRepository.deleteByProfessionalId(p.getId());

        // (a) anonimizar campos de identificação / preferências
        p.setName(ANONYMOUS_NAME);
        p.setPhone(null);
        p.setCity(null);
        p.setUf(null);
        p.setCep(null);
        p.setLatitude(null);
        p.setLongitude(null);
        p.setResume(null);
        p.setProfilePhotoUrl(null);
        p.setExperienceLevel(null);
        p.setExpectedSalaryCLT(null);
        p.setExpectedSalaryPJ(null);
        p.setFreelanceMinExpectation(null);
        p.setFreelanceMaxExpectation(null);
        p.setLinkedinUrl(null);
        p.setGithubUrl(null);
        p.setGithubId(null);
        p.setAvailable(false); // sai do matchmaking
        p.getSkills().clear();
        p.getPreferredTypes().clear();
        p.getPreferredOpportunityTypes().clear();
        professionalRepository.save(p);
    }

    private void anonymizeCompany(User user) {
        Company c = companyRepository.findByUserId(user.getId()).orElse(null);
        if (c == null) {
            return;
        }
        boolean individual = c.getType() == CompanyType.INDIVIDUAL;

        deleteFileQuietly(c.getProfilePhotoUrl(), storageService::deleteProfilePhoto);

        // (a) campos sempre anonimizados
        c.setPhone(null);
        c.setCity(null);
        c.setUf(null);
        c.setCep(null);
        c.setLatitude(null);
        c.setLongitude(null);
        c.setDescription(null);
        c.setProfilePhotoUrl(null);
        c.setLinkedinUrl(null);

        // (d.2) PF: nome + CPF são dado pessoal -> anonimiza.
        //       PJ: razão social + CNPJ NÃO são dado pessoal de pessoa física e
        //       são referência fiscal de notas já emitidas -> mantidos.
        if (individual) {
            c.setCompanyName(ANONYMOUS_NAME);
            c.setTaxId(null);
        }
        companyRepository.save(c);

        // (d.7) cartão salvo: desvincula no Mercado Pago (o dado sensível mora lá)
        // e limpa a referência local. Mantém histórico de bloqueio (auditoria).
        billingProfileRepository.findByCompanyId(c.getId()).ifPresent(this::scrubBillingCard);

        // (d.8) perfil fiscal: só há obrigação de retenção se houve fato gerador
        // (cobrança/nota referenciando a empresa). Sem isso, apaga.
        fiscalProfileRepository.findByCompanyId(c.getId()).ifPresent(fp -> {
            boolean hasFiscalRecord = commissionChargeRepository.existsByCompanyId(c.getId())
                    || nfseInvoiceRepository.existsByCompanyId(c.getId());
            if (!hasFiscalRecord) {
                fiscalProfileRepository.delete(fp);
            }
            // se houver, mantém intacto (dado já impresso em documento fiscal).
        });

        // (c) fecha oportunidades abertas/pausadas -> evita "vaga fantasma"
        // recebendo candidatura para uma empresa que não existe mais.
        List<com.main.nexus.model.Project> projects = projectRepository.findByCompanyId(c.getId());
        for (com.main.nexus.model.Project project : projects) {
            if (project.getStatus() == ProjectStatus.OPEN || project.getStatus() == ProjectStatus.PAUSED) {
                project.setStatus(ProjectStatus.CLOSED);
                projectRepository.save(project);
            }
        }
    }

    private void scrubBillingCard(CompanyBillingProfile bp) {
        try {
            if (bp.getMpCustomerId() != null && bp.getMpCardId() != null) {
                mercadoPago.deleteCard(bp.getMpCustomerId(), bp.getMpCardId());
            }
        } catch (RuntimeException e) {
            log.warn("LGPD: falha ao desvincular cartão no Mercado Pago (billing profile {}): {}",
                    bp.getId(), e.getMessage());
        }
        bp.setMpCustomerId(null);
        bp.setMpCardId(null);
        bp.setCardBrand(null);
        bp.setCardLast4(null);
        bp.setCardExpMonth(null);
        bp.setCardExpYear(null);
        bp.setCardholderName(null);
        bp.setUpdatedAt(LocalDateTime.now());
        billingProfileRepository.save(bp);
    }

    // (d.4) texto da proposta = registro contratual (mantido); arquivo do anexo
    // é apagado, e a linha inteira também -- fileName pode conter o nome da
    // pessoa (ex.: "Curriculo_Joao_Silva.pdf").
    private void deleteProposalAttachmentFiles(Long professionalId) {
        List<ProposalAttachment> attachments =
                proposalAttachmentRepository.findByProposalProfessionalId(professionalId);
        for (ProposalAttachment att : attachments) {
            deleteFileQuietly(att.getFileUrl(), storageService::deleteProposalAttachment);
        }
        if (!attachments.isEmpty()) {
            proposalAttachmentRepository.deleteAll(attachments);
        }
    }

    private void deleteFileQuietly(String url, java.util.function.Consumer<String> deleter) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            deleter.accept(url);
        } catch (RuntimeException e) {
            // Anonimização do banco é a parte legalmente crítica; um arquivo
            // órfão no storage é problema menor -- só registra.
            log.warn("LGPD: falha ao apagar arquivo do storage ({}): {}", url, e.getMessage());
        }
    }
}
