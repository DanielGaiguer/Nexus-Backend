package com.main.nexus.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.main.nexus.model.CommissionCharge;
import com.main.nexus.model.CommissionPolicy;
import com.main.nexus.model.Company;
import com.main.nexus.model.Match;
import com.main.nexus.model.MatchConfirmation;
import com.main.nexus.model.Project;
import com.main.nexus.repository.CommissionPolicyRepository;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommissionServiceOnHireConfirmedTest {

    @Mock private CommissionPolicyRepository commissionPolicyRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private UserRepository userRepository;
    @Mock private BillingService billingService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private CommissionService service;

    private Company company;
    private MatchConfirmation confirmation;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);

        Project project = new Project();
        project.setCompany(company);
        Match match = new Match();
        match.setProject(project);
        confirmation = new MatchConfirmation();
        confirmation.setMatch(match);
        confirmation.setConfirmedAmount(new BigDecimal("2000.00"));

        CommissionPolicy policy = new CommissionPolicy();
        policy.setId(CommissionPolicy.SINGLETON_ID);
        policy.setPercentage(new BigDecimal("10.00"));
        when(commissionPolicyRepository.findById(CommissionPolicy.SINGLETON_ID))
                .thenReturn(Optional.of(policy));
        when(billingService.isBillingEnabled()).thenReturn(true);
    }

    @Test
    void thirdHire_incrementsCounterButDoesNotCharge() {
        company.setSuccessfulHiresCount(2); // esta será a 3ª

        service.onHireConfirmed(confirmation);

        assertEquals(3, company.getSuccessfulHiresCount());
        verify(billingService, never()).createCharge(any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void fourthHire_createsChargeAndPublishesEvent() {
        company.setSuccessfulHiresCount(3); // esta será a 4ª -> cobra
        CommissionCharge charge = new CommissionCharge();
        charge.setId(500L);
        when(billingService.createCharge(any(), any(), any(), any())).thenReturn(charge);

        service.onHireConfirmed(confirmation);

        assertEquals(4, company.getSuccessfulHiresCount());
        verify(billingService).createCharge(
                confirmation,
                new BigDecimal("2000.00"),
                new BigDecimal("10.00"),
                new BigDecimal("200.00"));
        verify(eventPublisher).publishEvent(new CommissionChargeCreatedEvent(500L));
    }

    @Test
    void fourthHire_butBillingDisabled_onlyIncrements() {
        company.setSuccessfulHiresCount(3);
        when(billingService.isBillingEnabled()).thenReturn(false);

        service.onHireConfirmed(confirmation);

        assertEquals(4, company.getSuccessfulHiresCount());
        verify(billingService, never()).createCharge(any(), any(), any(), any());
    }
}
