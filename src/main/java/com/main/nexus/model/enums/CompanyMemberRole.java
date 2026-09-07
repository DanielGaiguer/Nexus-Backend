package com.main.nexus.model.enums;

// Papel de um usuário DENTRO de uma conta empresarial (ortogonal ao UserType,
// que continua COMPANY para qualquer membro). OWNER = dono da conta (faturamento,
// dados fiscais, gestão de membros, exclusão); MEMBER = operação de recrutamento.
// v1 tem só estes dois de propósito -- adicionar papéis depois é só um valor novo
// aqui + ajuste nos guards.
public enum CompanyMemberRole {
    OWNER,
    MEMBER
}
