package com.main.nexus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.main.nexus.dto.AcceptCompanyInvitationDTO;
import com.main.nexus.dto.CompanyInvitationDTO;
import com.main.nexus.dto.LoginResponseDTO;
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
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanyMemberServiceTest {

    @Mock private CompanyMemberRepository companyMemberRepository;
    @Mock private CompanyInvitationRepository companyInvitationRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenService tokenService;
    @Mock private UserConsentService userConsentService;
    @Mock private EmailService emailService;

    @InjectMocks private CompanyMemberService service;

    private Company company;
    private User ownerUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://front");

        ownerUser = new User();
        ownerUser.setId(1L);
        ownerUser.setEmail("owner@acme.com");
        ownerUser.setType(UserType.COMPANY);

        company = new Company();
        company.setId(10L);
        company.setCompanyName("Acme");
        company.setUser(ownerUser);

        when(tokenService.getCompanyInviteTtlDays()).thenReturn(7L);
        when(tokenService.generateCompanyInviteToken(anyLong(), anyString())).thenReturn("invite-tok");
        when(tokenService.generateToken(any())).thenReturn("jwt");
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(companyInvitationRepository.save(any(CompanyInvitation.class)))
                .thenAnswer(inv -> {
                    CompanyInvitation i = inv.getArgument(0);
                    if (i.getId() == null) {
                        i.setId(50L);
                    }
                    return i;
                });
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(99L);
            }
            return u;
        });
        when(companyMemberRepository.save(any(CompanyMember.class))).thenAnswer(inv -> inv.getArgument(0));
        // Barreira otimista: por padrão a invariante "1 OWNER ACTIVE" vale ao fim
        // de removeMember/transferOwnership; os testes de corrida sobrescrevem com 0.
        when(companyMemberRepository.countByCompanyIdAndStatusAndRole(
                anyLong(), any(), any())).thenReturn(1L);
    }

    private CompanyMember member(Long id, User user, CompanyMemberRole role) {
        CompanyMember m = new CompanyMember();
        m.setId(id);
        m.setCompany(company);
        m.setUser(user);
        m.setRole(role);
        m.setStatus(CompanyMemberStatus.ACTIVE);
        m.setCreatedAt(LocalDateTime.now());
        return m;
    }

    private CompanyInvitation pendingInvitation(String email) {
        CompanyInvitation i = new CompanyInvitation();
        i.setId(50L);
        i.setCompany(company);
        i.setEmail(email);
        i.setRole(CompanyMemberRole.MEMBER);
        i.setStatus(CompanyInvitationStatus.PENDING);
        i.setInvitedBy(ownerUser);
        i.setCreatedAt(LocalDateTime.now());
        i.setExpiresAt(LocalDateTime.now().plusDays(7));
        return i;
    }

    // ── invite ──────────────────────────────────────────────────────

    @Test
    void invite_emailAlreadyRegistered_isRejected() {
        when(userRepository.existsByEmail("taken@x.com")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.invite(company, 1L, "taken@x.com"));

        assertEquals(409, ex.getStatusCode().value());
        verify(companyInvitationRepository, never()).save(any());
        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void invite_pendingInvitationExists_isRejected() {
        when(userRepository.existsByEmail("dup@x.com")).thenReturn(false);
        when(companyInvitationRepository.existsByCompanyIdAndEmailAndStatus(
                10L, "dup@x.com", CompanyInvitationStatus.PENDING)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.invite(company, 1L, "dup@x.com"));

        assertEquals(409, ex.getStatusCode().value());
        verify(companyInvitationRepository, never()).save(any());
    }

    @Test
    void invite_newEmail_persistsPendingInvitationAndSendsLink() {
        when(userRepository.existsByEmail("new@x.com")).thenReturn(false);
        when(companyInvitationRepository.existsByCompanyIdAndEmailAndStatus(
                anyLong(), anyString(), any())).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(ownerUser));

        CompanyInvitationDTO dto = service.invite(company, 1L, "  new@x.com  ");

        assertEquals("new@x.com", dto.email());
        assertEquals("PENDING", dto.status());
        assertEquals("MEMBER", dto.role());

        ArgumentCaptor<CompanyInvitation> captor = ArgumentCaptor.forClass(CompanyInvitation.class);
        verify(companyInvitationRepository).save(captor.capture());
        assertEquals(CompanyMemberRole.MEMBER, captor.getValue().getRole());
        assertEquals(CompanyInvitationStatus.PENDING, captor.getValue().getStatus());
        assertEquals(ownerUser, captor.getValue().getInvitedBy());

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("new@x.com"), anyString(), body.capture());
        org.junit.jupiter.api.Assertions.assertTrue(
                body.getValue().contains("http://front/join?token=invite-tok"));
    }

    // ── acceptInvitation ────────────────────────────────────────────

    private AcceptCompanyInvitationDTO acceptDto(boolean acceptTerms) {
        return new AcceptCompanyInvitationDTO("valid-token", "S3nha!", acceptTerms, false, false);
    }

    @Test
    void acceptInvitation_validToken_createsActiveMemberAndRecordsConsent() {
        CompanyInvitation invitation = pendingInvitation("invitee@x.com");
        when(tokenService.extractCompanyInviteToken("valid-token"))
                .thenReturn(new TokenService.CompanyInviteToken(50L, "invitee@x.com"));
        when(companyInvitationRepository.findById(50L)).thenReturn(Optional.of(invitation));
        when(userRepository.existsByEmail("invitee@x.com")).thenReturn(false);

        LoginResponseDTO res = service.acceptInvitation(acceptDto(true));

        assertNotNull(res.token());
        assertEquals("Acme", res.name());
        assertEquals("COMPANY", res.role());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(UserType.COMPANY, userCaptor.getValue().getType());
        assertEquals("invitee@x.com", userCaptor.getValue().getEmail());

        ArgumentCaptor<CompanyMember> memberCaptor = ArgumentCaptor.forClass(CompanyMember.class);
        verify(companyMemberRepository).save(memberCaptor.capture());
        assertEquals(CompanyMemberRole.MEMBER, memberCaptor.getValue().getRole());
        assertEquals(CompanyMemberStatus.ACTIVE, memberCaptor.getValue().getStatus());
        assertEquals(company, memberCaptor.getValue().getCompany());

        verify(userConsentService).recordRegistrationConsents(any(User.class), eq(true), eq(false), eq(false));
        assertEquals(CompanyInvitationStatus.ACCEPTED, invitation.getStatus());
    }

    @Test
    void acceptInvitation_emailRegisteredBetweenInviteAndAccept_isRejected() {
        CompanyInvitation invitation = pendingInvitation("later@x.com");
        when(tokenService.extractCompanyInviteToken(anyString()))
                .thenReturn(new TokenService.CompanyInviteToken(50L, "later@x.com"));
        when(companyInvitationRepository.findById(50L)).thenReturn(Optional.of(invitation));
        when(userRepository.existsByEmail("later@x.com")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.acceptInvitation(acceptDto(true)));

        assertEquals(409, ex.getStatusCode().value());
        verify(userRepository, never()).save(any());
        verify(companyMemberRepository, never()).save(any());
    }

    @Test
    void acceptInvitation_termsNotAccepted_isRejectedBeforeAnyPersistence() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.acceptInvitation(acceptDto(false)));

        assertEquals(400, ex.getStatusCode().value());
        verify(companyInvitationRepository, never()).findById(anyLong());
        verify(userRepository, never()).save(any());
    }

    @Test
    void acceptInvitation_expiredInvitation_isRejectedAndMarkedExpired() {
        CompanyInvitation invitation = pendingInvitation("late@x.com");
        invitation.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(tokenService.extractCompanyInviteToken(anyString()))
                .thenReturn(new TokenService.CompanyInviteToken(50L, "late@x.com"));
        when(companyInvitationRepository.findById(50L)).thenReturn(Optional.of(invitation));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.acceptInvitation(acceptDto(true)));

        assertEquals(410, ex.getStatusCode().value());
        assertEquals(CompanyInvitationStatus.EXPIRED, invitation.getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    void acceptInvitation_alreadyAcceptedInvitation_isRejected() {
        CompanyInvitation invitation = pendingInvitation("x@x.com");
        invitation.setStatus(CompanyInvitationStatus.ACCEPTED);
        when(tokenService.extractCompanyInviteToken(anyString()))
                .thenReturn(new TokenService.CompanyInviteToken(50L, "x@x.com"));
        when(companyInvitationRepository.findById(50L)).thenReturn(Optional.of(invitation));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.acceptInvitation(acceptDto(true)));

        assertEquals(409, ex.getStatusCode().value());
        verify(userRepository, never()).save(any());
    }

    // ── removeMember ────────────────────────────────────────────────

    @Test
    void removeMember_onlyOwner_isBlocked() {
        CompanyMember owner = member(2L, ownerUser, CompanyMemberRole.OWNER);
        when(companyMemberRepository.findById(2L)).thenReturn(Optional.of(owner));
        when(companyMemberRepository.countByCompanyIdAndRole(10L, CompanyMemberRole.OWNER)).thenReturn(1L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.removeMember(company, 2L));

        assertEquals(409, ex.getStatusCode().value());
        verify(companyMemberRepository, never()).delete(any());
    }

    @Test
    void removeMember_regularMember_deletesAndDeactivatesUser() {
        User memberUser = new User();
        memberUser.setId(3L);
        memberUser.setEmail("member@x.com");
        memberUser.setType(UserType.COMPANY);
        memberUser.setActive(true);
        CompanyMember m = member(4L, memberUser, CompanyMemberRole.MEMBER);
        when(companyMemberRepository.findById(4L)).thenReturn(Optional.of(m));

        service.removeMember(company, 4L);

        verify(companyMemberRepository).delete(m);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        org.junit.jupiter.api.Assertions.assertFalse(userCaptor.getValue().getActive());
    }

    @Test
    void removeMember_invariantViolatedConcurrently_rollsBackWith409() {
        // Corrida: uma transferOwnership concorrente deixou a empresa sem OWNER
        // ACTIVE (0). A barreira otimista ao fim de removeMember tem que abortar.
        User memberUser = new User();
        memberUser.setId(3L);
        memberUser.setType(UserType.COMPANY);
        memberUser.setActive(true);
        CompanyMember m = member(4L, memberUser, CompanyMemberRole.MEMBER);
        when(companyMemberRepository.findById(4L)).thenReturn(Optional.of(m));
        when(companyMemberRepository.countByCompanyIdAndStatusAndRole(
                10L, CompanyMemberStatus.ACTIVE, CompanyMemberRole.OWNER)).thenReturn(0L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.removeMember(company, 4L));

        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void removeMember_fromAnotherCompany_isForbidden() {
        Company other = new Company();
        other.setId(999L);
        CompanyMember m = new CompanyMember();
        m.setId(7L);
        m.setCompany(other);
        m.setRole(CompanyMemberRole.MEMBER);
        when(companyMemberRepository.findById(7L)).thenReturn(Optional.of(m));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.removeMember(company, 7L));

        assertEquals(403, ex.getStatusCode().value());
    }

    // ── transferOwnership ───────────────────────────────────────────

    @Test
    void transferOwnership_swapsRolesAndUpdatesCompanyUser() {
        User targetUser = new User();
        targetUser.setId(3L);
        targetUser.setEmail("member@x.com");
        CompanyMember ownerM = member(2L, ownerUser, CompanyMemberRole.OWNER);
        CompanyMember targetM = member(5L, targetUser, CompanyMemberRole.MEMBER);
        when(companyMemberRepository.findByUserIdAndStatus(1L, CompanyMemberStatus.ACTIVE))
                .thenReturn(Optional.of(ownerM));
        when(companyMemberRepository.findById(5L)).thenReturn(Optional.of(targetM));

        service.transferOwnership(company, 1L, 5L);

        assertEquals(CompanyMemberRole.MEMBER, ownerM.getRole());
        assertEquals(CompanyMemberRole.OWNER, targetM.getRole());
        assertEquals(targetUser, company.getUser());
        verify(companyRepository).save(company);
    }

    @Test
    void transferOwnership_invariantViolatedConcurrently_rollsBackWith409() {
        // Corrida: um removeMember concorrente apagou o alvo entre o read e o
        // save. A contagem ao fim não bate (0 OWNER ACTIVE) -> 409 + rollback.
        User targetUser = new User();
        targetUser.setId(3L);
        CompanyMember ownerM = member(2L, ownerUser, CompanyMemberRole.OWNER);
        CompanyMember targetM = member(5L, targetUser, CompanyMemberRole.MEMBER);
        when(companyMemberRepository.findByUserIdAndStatus(1L, CompanyMemberStatus.ACTIVE))
                .thenReturn(Optional.of(ownerM));
        when(companyMemberRepository.findById(5L)).thenReturn(Optional.of(targetM));
        when(companyMemberRepository.countByCompanyIdAndStatusAndRole(
                10L, CompanyMemberStatus.ACTIVE, CompanyMemberRole.OWNER)).thenReturn(0L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.transferOwnership(company, 1L, 5L));

        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void transferOwnership_callerIsNotOwner_isForbidden() {
        CompanyMember callerM = member(2L, ownerUser, CompanyMemberRole.MEMBER);
        when(companyMemberRepository.findByUserIdAndStatus(1L, CompanyMemberStatus.ACTIVE))
                .thenReturn(Optional.of(callerM));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.transferOwnership(company, 1L, 5L));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void transferOwnership_targetInAnotherCompany_isForbidden() {
        CompanyMember ownerM = member(2L, ownerUser, CompanyMemberRole.OWNER);
        Company other = new Company();
        other.setId(999L);
        CompanyMember targetM = new CompanyMember();
        targetM.setId(5L);
        targetM.setCompany(other);
        targetM.setRole(CompanyMemberRole.MEMBER);
        targetM.setStatus(CompanyMemberStatus.ACTIVE);
        when(companyMemberRepository.findByUserIdAndStatus(1L, CompanyMemberStatus.ACTIVE))
                .thenReturn(Optional.of(ownerM));
        when(companyMemberRepository.findById(5L)).thenReturn(Optional.of(targetM));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.transferOwnership(company, 1L, 5L));

        assertEquals(403, ex.getStatusCode().value());
    }

    // ── revokeInvitation ────────────────────────────────────────────

    @Test
    void revokeInvitation_pending_marksRevoked() {
        CompanyInvitation invitation = pendingInvitation("z@x.com");
        when(companyInvitationRepository.findById(50L)).thenReturn(Optional.of(invitation));

        service.revokeInvitation(company, 50L);

        assertEquals(CompanyInvitationStatus.REVOKED, invitation.getStatus());
        verify(companyInvitationRepository).save(invitation);
    }

    @Test
    void revokeInvitation_notPending_isRejected() {
        CompanyInvitation invitation = pendingInvitation("z@x.com");
        invitation.setStatus(CompanyInvitationStatus.ACCEPTED);
        when(companyInvitationRepository.findById(50L)).thenReturn(Optional.of(invitation));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.revokeInvitation(company, 50L));

        assertEquals(409, ex.getStatusCode().value());
    }
}
