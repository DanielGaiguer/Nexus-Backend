package com.main.nexus.service;

import com.main.nexus.dto.CompanyFiscalProfileDTO;
import com.main.nexus.dto.FiscalConfigDTO;
import com.main.nexus.dto.NfseInvoiceDTO;
import com.main.nexus.dto.NfseModeDTO;
import com.main.nexus.dto.UpdateCompanyFiscalProfileDTO;
import com.main.nexus.model.CommissionCharge;
import com.main.nexus.model.Company;
import com.main.nexus.model.CompanyFiscalProfile;
import com.main.nexus.model.FiscalConfig;
import com.main.nexus.model.NfseInvoice;
import com.main.nexus.model.PortalSubscriptionCharge;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.CommissionChargeStatus;
import com.main.nexus.model.enums.CompanyType;
import com.main.nexus.model.enums.NfseInvoiceKind;
import com.main.nexus.model.enums.NfseInvoiceStatus;
import com.main.nexus.model.enums.PortalSubscriptionChargeStatus;
import com.main.nexus.repository.CommissionChargeRepository;
import com.main.nexus.repository.CompanyFiscalProfileRepository;
import com.main.nexus.repository.FiscalConfigRepository;
import com.main.nexus.repository.NfseInvoiceRepository;
import com.main.nexus.repository.PortalSubscriptionChargeRepository;
import com.main.nexus.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Camada financeira -- Prompt 6: emissão automática de NFS-e por comissão paga,
// via eNotas.
//
// Fluxo: cobrança vira PAID (Prompt 5) -> evento após o commit -> issueFor() cria
// o NfseInvoice e, se os dados fiscais do contratante estão completos, manda pro
// eNotas (ou "simula"). O webhook do eNotas (ou o job de varredura) finaliza como
// ISSUED (guarda numero/linkPdf/linkXml) ou FAILED (motivo -> fila /admin/invoices
// + aviso ao contratante). Nada disso trava a cobrança nem o fechamento de contratações.
@Service
public class NfseService {

    private static final Logger log = LoggerFactory.getLogger(NfseService.class);

    private static final String DEFAULT_DESCRIPTION =
            "Comissão pela intermediação de contratação — Nexus";

    private static final String PORTAL_DESCRIPTION =
            "Assinatura mensal de plataforma personalizada — Nexus";

    @Value("${nexus.nfse.simulate}")
    private boolean simulate;

    @Autowired
    private EnotasClient enotas;

    @Autowired
    private FiscalConfigRepository fiscalConfigRepository;

    @Autowired
    private CompanyFiscalProfileRepository fiscalProfileRepository;

    @Autowired
    private NfseInvoiceRepository invoiceRepository;

    @Autowired
    private CommissionChargeRepository chargeRepository;

    @Autowired
    private PortalSubscriptionChargeRepository portalChargeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    // ─── Modo ────────────────────────────────────────────────────

    public boolean isLive() {
        String empresaId = getConfig().getEnotasEmpresaId();
        return enotas.hasCredentials() && empresaId != null && !empresaId.isBlank();
    }

    public boolean isSimulated() {
        return simulate && !isLive();
    }

    public boolean isNfseEnabled() {
        return isLive() || isSimulated();
    }

    public NfseModeDTO mode() {
        return new NfseModeDTO(isLive(), isSimulated());
    }

    // ─── FiscalConfig (Admin) ────────────────────────────────────

    @Transactional
    public FiscalConfig getConfig() {
        return fiscalConfigRepository.findById(FiscalConfig.SINGLETON_ID)
                .orElseGet(() -> {
                    FiscalConfig fresh = new FiscalConfig();
                    fresh.setId(FiscalConfig.SINGLETON_ID);
                    fresh.setDefaultServiceDescription(DEFAULT_DESCRIPTION);
                    fresh.setUpdatedAt(LocalDateTime.now());
                    return fiscalConfigRepository.save(fresh);
                });
    }

    @Transactional
    public FiscalConfigDTO getConfigDTO() {
        return FiscalConfigDTO.from(getConfig(), isNfseEnabled(), isSimulated());
    }

    @Transactional
    public FiscalConfigDTO updateConfig(String enotasEmpresaId, String description, Long adminUserId) {
        FiscalConfig c = getConfig();
        c.setEnotasEmpresaId(clean(enotasEmpresaId));
        c.setDefaultServiceDescription(clean(description));
        c.setUpdatedAt(LocalDateTime.now());
        if (adminUserId != null) {
            userRepository.findById(adminUserId).ifPresent(c::setUpdatedByAdmin);
        }
        fiscalConfigRepository.save(c);
        return FiscalConfigDTO.from(c, isNfseEnabled(), isSimulated());
    }

    // ─── Dados fiscais do contratante ───────────────────────────

    @Transactional
    public CompanyFiscalProfile profileFor(Company company) {
        return fiscalProfileRepository.findByCompanyId(company.getId())
                .orElseGet(() -> {
                    CompanyFiscalProfile p = new CompanyFiscalProfile();
                    p.setCompany(company);
                    return fiscalProfileRepository.save(p);
                });
    }

    @Transactional
    public CompanyFiscalProfileDTO getFiscalProfileDTO(Company company) {
        return toProfileDTO(company, profileFor(company));
    }

    @Transactional
    public CompanyFiscalProfileDTO saveFiscalProfile(Company company, UpdateCompanyFiscalProfileDTO body) {
        CompanyFiscalProfile p = profileFor(company);
        p.setLegalName(clean(body.legalName()));
        p.setFiscalEmail(clean(body.fiscalEmail()));
        p.setStreet(clean(body.street()));
        p.setNumber(clean(body.number()));
        p.setComplement(clean(body.complement()));
        p.setDistrict(clean(body.district()));
        p.setCityIbgeCode(clean(body.cityIbgeCode()));
        p.setUpdatedAt(LocalDateTime.now());
        fiscalProfileRepository.save(p);

        // Se agora está completo, re-tenta as notas que falharam por falta de dado.
        if (fiscalDataComplete(company, p)) {
            for (NfseInvoice inv : invoiceRepository
                    .findByCompanyIdOrderByCreatedAtDesc(company.getId())) {
                if (inv.getStatus() == NfseInvoiceStatus.FAILED) {
                    inv.setStatus(NfseInvoiceStatus.PENDING);
                    inv.setFailureReason(null);
                    inv.setUpdatedAt(LocalDateTime.now());
                    invoiceRepository.saveAndFlush(inv);
                    issueInvoice(inv);
                }
            }
        }
        return toProfileDTO(company, p);
    }

    // ─── Emissão ────────────────────────────────────────────────

    // Chamado pelo NfseEventListener quando uma comissão vira PAID (Prompt 6).
    @Transactional
    public void issueFor(Long chargeId) {
        CommissionCharge charge = chargeRepository.findById(chargeId).orElse(null);
        if (charge == null || charge.getStatus() != CommissionChargeStatus.PAID) {
            return;
        }
        NfseInvoice inv = invoiceRepository.findByCommissionChargeId(chargeId)
                .orElseGet(() -> {
                    NfseInvoice fresh = new NfseInvoice();
                    fresh.setCommissionCharge(charge);
                    fresh.setCompany(charge.getCompany());
                    fresh.setStatus(NfseInvoiceStatus.PENDING);
                    return invoiceRepository.save(fresh);
                });
        issueInvoice(inv);
    }

    // Chamado pelo NfseEventListener quando uma mensalidade de plataforma vira PAID.
    @Transactional
    public void issueForPortalCharge(Long portalChargeId) {
        PortalSubscriptionCharge charge = portalChargeRepository.findById(portalChargeId).orElse(null);
        if (charge == null || charge.getStatus() != PortalSubscriptionChargeStatus.PAID) {
            return;
        }
        NfseInvoice inv = invoiceRepository.findByPortalSubscriptionChargeId(portalChargeId)
                .orElseGet(() -> {
                    NfseInvoice fresh = new NfseInvoice();
                    fresh.setPortalSubscriptionCharge(charge);
                    fresh.setCompany(charge.getCompany());
                    fresh.setStatus(NfseInvoiceStatus.PENDING);
                    return invoiceRepository.save(fresh);
                });
        issueInvoice(inv);
    }

    // Emissão de uma nota já criada -- comum aos dois tipos de cobrança.
    private void issueInvoice(NfseInvoice inv) {
        if (inv.getStatus() == NfseInvoiceStatus.ISSUED
                || inv.getStatus() == NfseInvoiceStatus.PROCESSING
                || inv.getStatus() == NfseInvoiceStatus.CANCELED) {
            return;
        }
        if (!isNfseEnabled()) {
            log.info("NFS-e desligada -- nota {} fica PENDING.", inv.getId());
            return;
        }

        Company company = inv.getCompany();
        CompanyFiscalProfile profile = profileFor(company);

        if (!fiscalDataComplete(company, profile)) {
            failInvoice(inv,
                    "Dados fiscais do contratante incompletos (razão social, endereço ou e-mail fiscal).");
            return;
        }

        inv.setStatus(NfseInvoiceStatus.PROCESSING);
        inv.setAttempts(inv.getAttempts() + 1);
        inv.setUpdatedAt(LocalDateTime.now());

        if (isSimulated()) {
            inv.setEnotasId("SIM-" + inv.getId());
            invoiceRepository.save(inv);
            return; // o Admin decide o resultado em /admin/invoices
        }

        invoiceRepository.saveAndFlush(inv);
        try {
            String enotasId = enotas.createNfse(
                    getConfig().getEnotasEmpresaId(), buildRequest(inv, company, profile));
            inv.setEnotasId(enotasId);
            inv.setUpdatedAt(LocalDateTime.now());
            invoiceRepository.save(inv);
            // status final vem pelo webhook / job de varredura
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 422) {
                failInvoice(inv, "O eNotas recusou a emissão. Confira os dados fiscais.");
            } else {
                inv.setStatus(NfseInvoiceStatus.PENDING);
                inv.setFailureReason("Erro temporário ao contatar o eNotas. Nova tentativa em breve.");
                inv.setUpdatedAt(LocalDateTime.now());
                invoiceRepository.save(inv);
                log.warn("NFS-e {} falhou de forma transitória: {}", inv.getId(), e.getReason());
            }
        }
    }

    // ─── Webhook do eNotas ─────────────────────────────────────

    @Transactional
    public void handleWebhook(String nfeId) {
        if (nfeId == null || nfeId.isBlank() || !isLive()) return;
        NfseInvoice inv = invoiceRepository.findByEnotasId(nfeId).orElse(null);
        if (inv == null) {
            log.info("Webhook eNotas para nfe {} sem NfseInvoice correspondente.", nfeId);
            return;
        }
        if (inv.getStatus() == NfseInvoiceStatus.ISSUED
                || inv.getStatus() == NfseInvoiceStatus.CANCELED) {
            return;
        }
        EnotasClient.EnotasNfe nfe = enotas.getNfse(getConfig().getEnotasEmpresaId(), nfeId);
        reconcile(inv, nfe);
    }

    // ─── Simulação / retry (Admin) ─────────────────────────────

    @Transactional
    public NfseInvoiceDTO simulateOutcome(Long invoiceId, String outcome) {
        if (!isSimulated()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A simulação de NFS-e só está disponível no modo simulate.");
        }
        NfseInvoice inv = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));
        if (inv.getStatus() != NfseInvoiceStatus.PROCESSING
                && inv.getStatus() != NfseInvoiceStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta nota já foi finalizada.");
        }
        boolean authorized = "authorized".equalsIgnoreCase(outcome);
        reconcile(inv, new EnotasClient.EnotasNfe(
                inv.getEnotasId() != null ? inv.getEnotasId() : "SIM-" + inv.getId(),
                authorized ? "Autorizada" : "Negada",
                authorized ? "SIM-" + inv.getId() : null,
                authorized ? "1" : null,
                authorized ? "https://simulado.nexus/nfse/" + inv.getId() + ".pdf" : null,
                authorized ? "https://simulado.nexus/nfse/" + inv.getId() + ".xml" : null,
                authorized ? "SIMULADO" : null,
                authorized ? null : "Emissão negada (simulado)."));
        return NfseInvoiceDTO.from(inv);
    }

    @Transactional
    public NfseInvoiceDTO retry(Long invoiceId) {
        NfseInvoice inv = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));
        if (inv.getStatus() == NfseInvoiceStatus.ISSUED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta nota já foi emitida.");
        }
        inv.setStatus(NfseInvoiceStatus.PENDING);
        inv.setFailureReason(null);
        inv.setUpdatedAt(LocalDateTime.now());
        invoiceRepository.saveAndFlush(inv);
        issueInvoice(inv);
        return NfseInvoiceDTO.from(invoiceRepository.findById(invoiceId).orElse(inv));
    }

    // ─── Job de varredura (NexusScheduler) ─────────────────────

    @Transactional
    public void processStuckInvoices() {
        if (!isLive()) return;

        for (NfseInvoice inv : invoiceRepository
                .findByStatusOrderByCreatedAtAsc(NfseInvoiceStatus.PENDING)) {
            issueInvoice(inv);
        }
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);
        for (NfseInvoice inv : invoiceRepository.findStaleProcessing(threshold)) {
            try {
                handleWebhook(inv.getEnotasId());
            } catch (Exception e) {
                log.warn("Re-consulta da NFS-e {} falhou: {}", inv.getId(), e.getMessage());
            }
        }
    }

    // ─── Listagens ────────────────────────────────────────────

    public List<NfseInvoiceDTO> invoicesFor(Company company) {
        return invoiceRepository.findByCompanyIdOrderByCreatedAtDesc(company.getId()).stream()
                .map(NfseInvoiceDTO::from).toList();
    }

    public List<NfseInvoiceDTO> listForAdmin(String status) {
        List<NfseInvoice> rows;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            rows = invoiceRepository.findAllByOrderByCreatedAtDesc();
        } else {
            rows = invoiceRepository.findByStatusOrderByCreatedAtDesc(parseStatus(status));
        }
        return rows.stream().map(NfseInvoiceDTO::from).toList();
    }

    public NfseInvoice findByChargeId(Long chargeId) {
        return invoiceRepository.findByCommissionChargeId(chargeId).orElse(null);
    }

    // ─── Internos ────────────────────────────────────────────

    private void reconcile(NfseInvoice inv, EnotasClient.EnotasNfe nfe) {
        String s = nfe.status() == null ? "" : nfe.status().toLowerCase();
        inv.setUpdatedAt(LocalDateTime.now());
        User companyUser = inv.getCompany().getUser();
        String projectTitle = notifyLabel(inv);

        if (s.contains("autorizada")) {
            inv.setStatus(NfseInvoiceStatus.ISSUED);
            inv.setNumero(nfe.numero());
            inv.setSerie(nfe.serie());
            inv.setLinkPdf(nfe.linkPdf());
            inv.setLinkXml(nfe.linkXml());
            inv.setCodigoVerificacao(nfe.codigoVerificacao());
            inv.setFailureReason(null);
            inv.setIssuedAt(LocalDateTime.now());
            invoiceRepository.save(inv);
            if (companyUser != null) {
                if (inv.getKind() == NfseInvoiceKind.PORTAL_SUBSCRIPTION) {
                    notificationService.notifyNfsePortalIssued(companyUser,
                            inv.getPortalSubscriptionCharge().getCustomPortal().getSubdomain(),
                            inv.getNumero());
                } else {
                    notificationService.notifyNfseIssued(companyUser, projectTitle, inv.getNumero());
                }
            }
        } else if (s.contains("negada") || s.contains("cancelada") || s.contains("erro")) {
            failInvoice(inv, nfe.motivo() != null ? nfe.motivo() : "A prefeitura recusou a nota.");
        } else {
            inv.setStatus(NfseInvoiceStatus.PROCESSING);
            invoiceRepository.save(inv);
        }
    }

    private void failInvoice(NfseInvoice inv, String reason) {
        inv.setStatus(NfseInvoiceStatus.FAILED);
        inv.setFailureReason(reason);
        inv.setUpdatedAt(LocalDateTime.now());
        invoiceRepository.save(inv);
        User u = inv.getCompany().getUser();
        if (u != null) {
            notificationService.notifyNfseFailed(u, reason);
        }
    }

    // Endereço mínimo + e-mail. Para PF o eNotas costuma dispensar o endereço.
    private boolean fiscalDataComplete(Company company, CompanyFiscalProfile p) {
        boolean hasTaxId = company.getTaxId() != null && !company.getTaxId().isBlank();
        boolean hasEmail = p.hasContact()
                || (company.getUser() != null && company.getUser().getEmail() != null);
        if (!hasTaxId || !hasEmail) return false;
        if (company.getType() == CompanyType.INDIVIDUAL) {
            return true;
        }
        // PJ: exige endereço + cidade/UF/CEP no Company.
        return p.hasAddress()
                && notBlank(company.getCity()) && notBlank(company.getUf()) && notBlank(company.getCep());
    }

    // Rótulo curto da cobrança para as notificações (título do projeto / plataforma).
    private String notifyLabel(NfseInvoice inv) {
        if (inv.getCommissionCharge() != null) {
            return inv.getCommissionCharge().getMatchConfirmation()
                    .getMatch().getProject().getTitle();
        }
        return "plataforma " + inv.getPortalSubscriptionCharge().getCustomPortal().getSubdomain();
    }

    private java.math.BigDecimal amountFor(NfseInvoice inv) {
        return inv.getCommissionCharge() != null
                ? inv.getCommissionCharge().getAmount()
                : inv.getPortalSubscriptionCharge().getAmount();
    }

    private String idExternoFor(NfseInvoice inv) {
        return inv.getCommissionCharge() != null
                ? "commission-charge-" + inv.getCommissionCharge().getId()
                : "portal-sub-charge-" + inv.getPortalSubscriptionCharge().getId();
    }

    private String descriptionFor(NfseInvoice inv) {
        if (inv.getCommissionCharge() != null) {
            String desc = getConfig().getDefaultServiceDescription();
            return notBlank(desc) ? desc : DEFAULT_DESCRIPTION;
        }
        return PORTAL_DESCRIPTION + " (\""
                + inv.getPortalSubscriptionCharge().getCustomPortal().getSubdomain() + "\")";
    }

    private Map<String, Object> buildRequest(NfseInvoice inv, Company company, CompanyFiscalProfile p) {
        String email = p.hasContact() ? p.getFiscalEmail()
                : (company.getUser() != null ? company.getUser().getEmail() : null);

        Map<String, Object> endereco = new LinkedHashMap<>();
        endereco.put("pais", "BR");
        endereco.put("uf", company.getUf());
        endereco.put("cidade", company.getCity());
        endereco.put("logradouro", p.getStreet());
        endereco.put("numero", p.getNumber());
        if (notBlank(p.getComplement())) endereco.put("complemento", p.getComplement());
        endereco.put("bairro", p.getDistrict());
        endereco.put("cep", digits(company.getCep()));
        if (notBlank(p.getCityIbgeCode())) endereco.put("codigoIbgeCidade", p.getCityIbgeCode());

        Map<String, Object> cliente = new LinkedHashMap<>();
        cliente.put("nome", notBlank(p.getLegalName()) ? p.getLegalName() : company.getCompanyName());
        cliente.put("email", email);
        cliente.put("cpfCnpj", digits(company.getTaxId()));
        cliente.put("tipoPessoa", company.getType() == CompanyType.INDIVIDUAL ? "F" : "J");
        cliente.put("endereco", endereco);

        Map<String, Object> servico = new LinkedHashMap<>();
        servico.put("descricao", descriptionFor(inv));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tipo", "NF");
        body.put("idExterno", idExternoFor(inv));
        body.put("cliente", cliente);
        body.put("servico", servico);
        body.put("valorTotal", amountFor(inv));
        return body;
    }

    private CompanyFiscalProfileDTO toProfileDTO(Company c, CompanyFiscalProfile p) {
        return new CompanyFiscalProfileDTO(
                c.getTaxId(),
                c.getType() != null ? c.getType().name() : null,
                c.getCompanyName(),
                c.getCity(),
                c.getUf(),
                c.getCep(),
                p.getLegalName(),
                p.getFiscalEmail(),
                p.getStreet(),
                p.getNumber(),
                p.getComplement(),
                p.getDistrict(),
                p.getCityIbgeCode(),
                fiscalDataComplete(c, p));
    }

    private NfseInvoiceStatus parseStatus(String s) {
        try {
            return NfseInvoiceStatus.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown invoice status: " + s);
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String digits(String s) {
        return s == null ? null : s.replaceAll("\\D", "");
    }

    private static String clean(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }
}
