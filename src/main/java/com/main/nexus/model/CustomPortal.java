package com.main.nexus.model;

import com.main.nexus.model.enums.CustomPortalPaymentStatus;
import com.main.nexus.model.enums.CustomPortalStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

// A plataforma personalizada em si — 1:1 com o contratante (Company). Vitrine com
// identidade visual propria sobre os MESMOS projetos/vagas que a empresa ja publica
// no Nexus; a customizacao visual e o roteamento por subdominio vem nos proximos
// prompts. Aqui: dono, ciclo de vida, subdominio reservado e dados da assinatura
// (controlados a mao pelo Admin, sem gateway).
@Entity
@Table(name = "tb_custom_portal")
public class CustomPortal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    // Solicitacao que originou este portal — nulo quando o Admin cria direto
    // (contato comercial feito por fora da plataforma).
    @OneToOne
    @JoinColumn(name = "origin_request_id", unique = true)
    private CustomPortalRequest originRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomPortalStatus status = CustomPortalStatus.ACTIVE;

    // Subdominio reservado (ex.: "acme" -> acme.nexus.com.br). Unico. Nesta etapa
    // e so o campo/valor; o roteamento real vem no Prompt 3.
    @Column(nullable = false, unique = true, length = 63)
    private String subdomain;

    // ── Assinatura ────────────────────────────────────────────────────
    @Column(nullable = false, length = 120)
    private String planName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal planPrice;

    @Column(nullable = false)
    private LocalDate subscriptionStartDate;

    @Column(nullable = false)
    private LocalDate nextDueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomPortalPaymentStatus paymentStatus = CustomPortalPaymentStatus.UP_TO_DATE;

    // ── Customizacao visual (Prompt 2) ───────────────────────────────
    // Tudo opcional — portais criados no Prompt 1 seguem validos sem nada disso.
    // Vale so dentro da pagina publica da plataforma (Prompt 3) / preview; nao
    // afeta o tema do Nexus.

    // Nome de exibicao da plataforma (cai pro Company.companyName quando vazio).
    @Column(length = 120)
    private String displayName;

    // Cor primaria em hex #rrggbb — aplicada a botoes/destaques da pagina publica.
    @Column(length = 9)
    private String primaryColor;

    @Column(length = 500)
    private String logoUrl;

    @Column(length = 500)
    private String bannerUrl;

    @Column(length = 500)
    private String faviconUrl;

    // Texto "Sobre" — texto longo simples (o projeto nao tem editor rich text).
    @Column(columnDefinition = "TEXT")
    private String aboutText;

    // Secoes institucionais extras, reordenaveis — a ordem e a posicao no List.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "tb_custom_portal_section",
        joinColumns = @JoinColumn(name = "custom_portal_id"))
    @OrderColumn(name = "position")
    @BatchSize(size = 50)
    private List<CustomPortalSection> sections = new ArrayList<>();

    // Links de redes sociais no rodape da pagina publica (colunas social_*).
    @Embedded
    private CustomPortalSocialLinks socialLinks = new CustomPortalSocialLinks();

    // ── Auditoria ─────────────────────────────────────────────────────
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "created_by_admin")
    private User createdByAdmin;

    // Ultima data de vencimento para a qual o lembrete "assinatura perto do
    // vencimento" ja foi enviado — trava do job diario para nao repetir no
    // mesmo ciclo (mesmo espirito do existsByUserIdAndTypeAndActionUrl em
    // MatchExpirationService).
    @Column
    private LocalDate lastRenewalReminderFor;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public CustomPortalRequest getOriginRequest() {
        return originRequest;
    }

    public void setOriginRequest(CustomPortalRequest originRequest) {
        this.originRequest = originRequest;
    }

    public CustomPortalStatus getStatus() {
        return status;
    }

    public void setStatus(CustomPortalStatus status) {
        this.status = status;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public void setSubdomain(String subdomain) {
        this.subdomain = subdomain;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public BigDecimal getPlanPrice() {
        return planPrice;
    }

    public void setPlanPrice(BigDecimal planPrice) {
        this.planPrice = planPrice;
    }

    public LocalDate getSubscriptionStartDate() {
        return subscriptionStartDate;
    }

    public void setSubscriptionStartDate(LocalDate subscriptionStartDate) {
        this.subscriptionStartDate = subscriptionStartDate;
    }

    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public void setNextDueDate(LocalDate nextDueDate) {
        this.nextDueDate = nextDueDate;
    }

    public CustomPortalPaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(CustomPortalPaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public User getCreatedByAdmin() {
        return createdByAdmin;
    }

    public void setCreatedByAdmin(User createdByAdmin) {
        this.createdByAdmin = createdByAdmin;
    }

    public LocalDate getLastRenewalReminderFor() {
        return lastRenewalReminderFor;
    }

    public void setLastRenewalReminderFor(LocalDate lastRenewalReminderFor) {
        this.lastRenewalReminderFor = lastRenewalReminderFor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getBannerUrl() {
        return bannerUrl;
    }

    public void setBannerUrl(String bannerUrl) {
        this.bannerUrl = bannerUrl;
    }

    public String getFaviconUrl() {
        return faviconUrl;
    }

    public void setFaviconUrl(String faviconUrl) {
        this.faviconUrl = faviconUrl;
    }

    public String getAboutText() {
        return aboutText;
    }

    public void setAboutText(String aboutText) {
        this.aboutText = aboutText;
    }

    public List<CustomPortalSection> getSections() {
        return sections;
    }

    public void setSections(List<CustomPortalSection> sections) {
        this.sections = sections;
    }

    public CustomPortalSocialLinks getSocialLinks() {
        return socialLinks;
    }

    public void setSocialLinks(CustomPortalSocialLinks socialLinks) {
        this.socialLinks = socialLinks;
    }
}
