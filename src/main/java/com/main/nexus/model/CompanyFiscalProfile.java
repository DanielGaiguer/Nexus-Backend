package com.main.nexus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

// Dados fiscais do contratante (tomador do servico) que a NFS-e exige e que o
// cadastro basico de Company nao tem: razao social, e-mail fiscal e endereco
// completo. O contratante preenche em /company/billing. CPF/CNPJ, cidade, UF e
// CEP continuam vindo do proprio Company. (Prompt 6)
@Entity
@Table(name = "tb_company_fiscal_profile")
public class CompanyFiscalProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    // Razao social -- pode diferir do companyName (nome fantasia).
    @Column(length = 200)
    private String legalName;

    @Column(length = 150)
    private String fiscalEmail;

    @Column(length = 200)
    private String street;

    @Column(length = 20)
    private String number;

    @Column(length = 100)
    private String complement;

    @Column(length = 120)
    private String district;

    // Codigo IBGE da cidade (7 digitos). Opcional -- o eNotas resolve por
    // cidade + UF quando ausente.
    @Column(length = 7)
    private String cityIbgeCode;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // "Completo o suficiente para emitir" -- endereco minimo + e-mail.
    // Para PF (CompanyType.INDIVIDUAL) o eNotas costuma dispensar o endereco,
    // entao a checagem de PJ e mais rigida (ver NfseService).
    public boolean hasAddress() {
        return notBlank(street) && notBlank(number) && notBlank(district);
    }

    public boolean hasContact() {
        return notBlank(fiscalEmail);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

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

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getFiscalEmail() {
        return fiscalEmail;
    }

    public void setFiscalEmail(String fiscalEmail) {
        this.fiscalEmail = fiscalEmail;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getComplement() {
        return complement;
    }

    public void setComplement(String complement) {
        this.complement = complement;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getCityIbgeCode() {
        return cityIbgeCode;
    }

    public void setCityIbgeCode(String cityIbgeCode) {
        this.cityIbgeCode = cityIbgeCode;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
