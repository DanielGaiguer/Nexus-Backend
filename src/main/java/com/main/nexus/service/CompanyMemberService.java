package com.main.nexus.service;

import com.main.nexus.dto.AcceptCompanyInvitationDTO;
import com.main.nexus.dto.CompanyInvitationDTO;
import com.main.nexus.dto.CompanyMemberDTO;
import com.main.nexus.dto.CompanyMembersResponseDTO;
import com.main.nexus.dto.LoginResponseDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.CompanyInvitation;
import com.main.nexus.model.CompanyMember;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.CompanyInvitationStatus;
import com.main.nexus.model.enums.CompanyMemberRole;
import com.main.nexus.model.enums.CompanyMemberStatus;
import com.main.nexus.model.enums.UserType;
import com.main.nexus.repository.CompanyInvitationRepository;
import com.main.nexus.repository.CompanyMemberRepository;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Fluxo real de membros de uma conta empresarial: convite por e-mail (token de
// capability, ver TokenService#generateCompanyInviteToken), aceite público,
// listagem, remoção e transferência de titularidade.
//
// Invariante: exatamente 1 OWNER ACTIVE por empresa. removeMember barra a saída
// do único OWNER; transferOwnership troca os papéis atomicamente.
//
// O gate OWNER-only fica no controller (CompanyMemberController via
// CompanyAccessService.requireOwner); aqui os métodos já recebem a Company
// resolvida. acceptInvitation é o único público -- não tem contexto de empresa.
@Service
public class CompanyMemberService {

    @Autowired
    private CompanyMemberRepository companyMemberRepository;

    @Autowired
    private CompanyInvitationRepository companyInvitationRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserConsentService userConsentService;

    @Autowired
    private EmailService emailService;

    @Value("${nexus.frontend.base-url}")
    private String frontendBaseUrl;

    // ── Leitura ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CompanyMembersResponseDTO list(Company company) {
        List<CompanyMemberDTO> members = companyMemberRepository
                .findByCompanyIdAndStatus(company.getId(), CompanyMemberStatus.ACTIVE)
                .stream()
                .sorted(Comparator.comparing(CompanyMember::getCreatedAt))
                .map(m -> new CompanyMemberDTO(
                        m.getId(), m.getUser().getId(), m.getUser().getEmail(),
                        m.getRole().name(), m.getCreatedAt()))
                .toList();

        List<CompanyInvitationDTO> pending = companyInvitationRepository
                .findByCompanyIdAndStatus(company.getId(), CompanyInvitationStatus.PENDING)
                .stream()
                .sorted(Comparator.comparing(CompanyInvitation::getCreatedAt))
                .map(this::toInvitationDTO)
                .toList();

        return new CompanyMembersResponseDTO(members, pending);
    }

    // ── Convite ─────────────────────────────────────────────────────

    @Transactional
    public CompanyInvitationDTO invite(Company company, Long actorUserId, String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim();
        if (email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An email is required.");
        }
        if (!email.contains("@") || email.contains(" ")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide a valid email address.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This email already belongs to a Nexus account. Only people without an account can be invited.");
        }
        if (companyInvitationRepository.existsByCompanyIdAndEmailAndStatus(
                company.getId(), email, CompanyInvitationStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "There is already a pending invitation for this email.");
        }

        User invitedBy = userRepository.findById(actorUserId).orElse(null);

        LocalDateTime now = LocalDateTime.now();
        CompanyInvitation invitation = new CompanyInvitation();
        invitation.setCompany(company);
        invitation.setEmail(email);
        invitation.setRole(CompanyMemberRole.MEMBER);
        invitation.setStatus(CompanyInvitationStatus.PENDING);
        invitation.setInvitedBy(invitedBy);
        invitation.setCreatedAt(now);
        invitation.setExpiresAt(now.plusDays(tokenService.getCompanyInviteTtlDays()));
        CompanyInvitation saved = companyInvitationRepository.save(invitation);

        String token = tokenService.generateCompanyInviteToken(saved.getId(), email);
        String link = frontendBaseUrl + "/join?token=" + token;

        emailService.send(
                email,
                "Você foi convidado para o Nexus",
                "Olá,\n\n" + company.getCompanyName() + " convidou você para participar da conta "
                + "empresarial dela no Nexus.\n\n"
                + "Para aceitar e criar a sua conta, acesse o link abaixo (válido por "
                + tokenService.getCompanyInviteTtlDays() + " dias):\n" + link + "\n\n"
                + "Se você não esperava este convite, pode ignorar este e-mail.\n\nEquipe Nexus");

        return toInvitationDTO(saved);
    }

    @Transactional
    public void revokeInvitation(Company company, Long invitationId) {
        CompanyInvitation invitation = companyInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found."));
        if (!invitation.getCompany().getId().equals(company.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This invitation does not belong to your company.");
        }
        if (invitation.getStatus() != CompanyInvitationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a pending invitation can be revoked.");
        }
        invitation.setStatus(CompanyInvitationStatus.REVOKED);
        companyInvitationRepository.save(invitation);
    }

    // ── Aceite (público) ────────────────────────────────────────────

    @Transactional
    public LoginResponseDTO acceptInvitation(AcceptCompanyInvitationDTO dto) {
        if (dto == null || dto.token() == null || dto.token().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation token is required.");
        }
        if (dto.password() == null || dto.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A password is required.");
        }
        // Termos ANTES de qualquer persistência (mesmo espírito de
        // AuthService.requireTermsAccepted no cadastro normal).
        if (!Boolean.TRUE.equals(dto.acceptedTermsOfUse())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You must accept the Terms of Use to continue.");
        }

        TokenService.CompanyInviteToken claims;
        try {
            claims = tokenService.extractCompanyInviteToken(dto.token());
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "This invitation link is invalid or has expired. Ask the account owner for a new one.");
        }

        CompanyInvitation invitation = companyInvitationRepository.findById(claims.invitationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found."));

        if (invitation.getStatus() != CompanyInvitationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This invitation is no longer valid.");
        }
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus(CompanyInvitationStatus.EXPIRED);
            companyInvitationRepository.save(invitation);
            throw new ResponseStatusException(HttpStatus.GONE,
                    "This invitation has expired. Ask the account owner for a new one.");
        }

        String email = invitation.getEmail();
        // O e-mail pode ter virado uma conta entre o convite e o aceite.
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An account with this email already exists. Ask the account owner to invite a different address.");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setType(UserType.COMPANY);
        User savedUser = userRepository.save(user);

        CompanyMember membership = new CompanyMember();
        membership.setCompany(invitation.getCompany());
        membership.setUser(savedUser);
        membership.setRole(CompanyMemberRole.MEMBER);
        membership.setStatus(CompanyMemberStatus.ACTIVE);
        membership.setInvitedBy(invitation.getInvitedBy());
        companyMemberRepository.save(membership);

        // Mesma trilha de consentimento do cadastro normal (gera UserConsent).
        userConsentService.recordRegistrationConsents(
                savedUser,
                dto.acceptedTermsOfUse(),
                dto.acceptedMarketingCommunications(),
                dto.acceptedAlgorithmImprovement());

        invitation.setStatus(CompanyInvitationStatus.ACCEPTED);
        companyInvitationRepository.save(invitation);

        String companyName = invitation.getCompany().getCompanyName();
        String jwt = tokenService.generateToken(
                new UserDTO(savedUser.getId(), savedUser.getEmail(), UserType.COMPANY.name()));
        return new LoginResponseDTO(
                savedUser.getId(), savedUser.getEmail(), companyName, UserType.COMPANY.name(), jwt);
    }

    // ── Remoção / transferência ─────────────────────────────────────

    @Transactional
    public void removeMember(Company company, Long memberId) {
        CompanyMember member = companyMemberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found."));
        if (!member.getCompany().getId().equals(company.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This member does not belong to your company.");
        }
        if (member.getRole() == CompanyMemberRole.OWNER
                && companyMemberRepository.countByCompanyIdAndRole(
                        company.getId(), CompanyMemberRole.OWNER) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You cannot remove the only owner. Transfer ownership to another member first.");
        }

        User user = member.getUser();
        companyMemberRepository.delete(member);

        // A conta do ex-membro deixa de ter empresa -- desativa para não cair num
        // login degenerado (User COMPANY sem vínculo). A linha em tb_user
        // permanece (é FK de mensagem, notificação, resposta de confirmação, ...).
        user.setActive(false);
        userRepository.save(user);

        assertSingleActiveOwner(company.getId());
    }

    @Transactional
    public void transferOwnership(Company company, Long actorUserId, Long targetMemberId) {
        CompanyMember current = companyMemberRepository
                .findByUserIdAndStatus(actorUserId, CompanyMemberStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You are not an active member of this company."));
        if (!current.getCompany().getId().equals(company.getId())
                || current.getRole() != CompanyMemberRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the current owner can transfer ownership.");
        }

        CompanyMember target = companyMemberRepository.findById(targetMemberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found."));
        if (!target.getCompany().getId().equals(company.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This member does not belong to your company.");
        }
        if (target.getId().equals(current.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already the owner.");
        }
        if (target.getStatus() != CompanyMemberStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ownership can only be transferred to an active member.");
        }

        current.setRole(CompanyMemberRole.MEMBER);
        target.setRole(CompanyMemberRole.OWNER);
        companyMemberRepository.save(current);
        companyMemberRepository.save(target);

        // Mantém o @OneToOne legado Company.user apontando para o OWNER (conta de
        // faturamento / contato público) -- ~60 call sites ainda usam company.getUser().
        company.setUser(target.getUser());
        companyRepository.save(company);

        assertSingleActiveOwner(company.getId());
    }

    // ── Interno ─────────────────────────────────────────────────────

    // Barreira otimista da invariante "exatamente 1 OWNER ACTIVE por empresa".
    // removeMember e transferOwnership disparados em paralelo (o próprio OWNER em
    // duas abas, mirando o mesmo membro) poderiam, em certas intercalações de
    // commit, deixar a empresa sem dono -- estado corrompido, recuperável só no
    // banco. Reavaliar a contagem ao fim da transação transforma isso num 409
    // alto e faz rollback. Não é lock: reduz a janela, não a fecha em toda
    // intercalação -- proporcional ao risco (exige auto-sabotagem do único OWNER).
    private void assertSingleActiveOwner(Long companyId) {
        long owners = companyMemberRepository.countByCompanyIdAndStatusAndRole(
                companyId, CompanyMemberStatus.ACTIVE, CompanyMemberRole.OWNER);
        if (owners != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This account must have exactly one owner. Refresh and try again.");
        }
    }

    private CompanyInvitationDTO toInvitationDTO(CompanyInvitation i) {
        return new CompanyInvitationDTO(
                i.getId(), i.getEmail(), i.getRole().name(), i.getStatus().name(),
                i.getCreatedAt(), i.getExpiresAt());
    }
}
