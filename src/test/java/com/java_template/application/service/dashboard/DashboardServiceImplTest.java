package com.java_template.application.service.dashboard;

import com.java_template.application.dto.dashboard.*;
import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DashboardServiceImpl.
 * 
 * <p>Tests all calculation methods, caching behavior, and error handling.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardServiceImpl Tests")
class DashboardServiceImplTest {
    
    @Mock
    private EntityService entityService;
    
    private DashboardServiceImpl dashboardService;
    
    @BeforeEach
    void setUp() {
        dashboardService = new DashboardServiceImpl(entityService);
    }
    
    // ==================== getDashboardSummary Tests ====================
    
    @Test
    @DisplayName("getDashboardSummary should return aggregated data successfully")
    void getDashboardSummary_withValidData_returnsAggregatedData() {
        // Arrange
        List<EntityWithMetadata<Loan>> loans = createTestLoans();
        List<EntityWithMetadata<Payment>> payments = createTestPayments();
        
        when(entityService.findAll(any(ModelSpec.class), eq(Loan.class), isNull()))
            .thenReturn(loans);
        when(entityService.findAll(any(ModelSpec.class), eq(Payment.class), isNull()))
            .thenReturn(payments);
        
        // Act
        DashboardSummaryDTO result = dashboardService.getDashboardSummary();
        
        // Assert
        assertNotNull(result);
        assertNotNull(result.getTotalPortfolioValue());
        assertNotNull(result.getActiveLoansCount());
        assertNotNull(result.getOutstandingPrincipal());
        assertNotNull(result.getActiveBorrowersCount());
        assertNotNull(result.getStatusDistribution());
        assertNotNull(result.getPortfolioTrend());
        assertNotNull(result.getAprDistribution());
        assertNotNull(result.getMonthlyPayments());
        
        verify(entityService, times(1)).findAll(any(ModelSpec.class), eq(Loan.class), isNull());
        verify(entityService, times(1)).findAll(any(ModelSpec.class), eq(Payment.class), isNull());
    }
    
    @Test
    @DisplayName("getDashboardSummary should use cached data on second call")
    void getDashboardSummary_secondCall_usesCachedData() {
        // Arrange
        List<EntityWithMetadata<Loan>> loans = createTestLoans();
        List<EntityWithMetadata<Payment>> payments = createTestPayments();
        
        when(entityService.findAll(any(ModelSpec.class), eq(Loan.class), isNull()))
            .thenReturn(loans);
        when(entityService.findAll(any(ModelSpec.class), eq(Payment.class), isNull()))
            .thenReturn(payments);
        
        // Act
        DashboardSummaryDTO result1 = dashboardService.getDashboardSummary();
        DashboardSummaryDTO result2 = dashboardService.getDashboardSummary();
        
        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        assertSame(result1, result2, "Second call should return cached instance");
        
        // EntityService should only be called once (first call)
        verify(entityService, times(1)).findAll(any(ModelSpec.class), eq(Loan.class), isNull());
        verify(entityService, times(1)).findAll(any(ModelSpec.class), eq(Payment.class), isNull());
    }
    
    @Test
    @DisplayName("getDashboardSummary should handle EntityService exceptions gracefully")
    void getDashboardSummary_whenEntityServiceFails_handlesGracefully() {
        // Arrange
        when(entityService.findAll(any(ModelSpec.class), eq(Loan.class), isNull()))
            .thenThrow(new RuntimeException("Database connection failed"));
        when(entityService.findAll(any(ModelSpec.class), eq(Payment.class), isNull()))
            .thenReturn(Collections.emptyList());

        // Act
        DashboardSummaryDTO result = dashboardService.getDashboardSummary();

        // Assert - should return data with empty/zero values due to graceful degradation
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getTotalPortfolioValue());
        assertEquals(0, result.getActiveLoansCount());
        assertEquals(BigDecimal.ZERO, result.getOutstandingPrincipal());
        assertEquals(0, result.getActiveBorrowersCount());
    }
    
    @Test
    @DisplayName("invalidateCache should clear cached data")
    void invalidateCache_shouldClearCache() {
        // Arrange
        List<EntityWithMetadata<Loan>> loans = createTestLoans();
        List<EntityWithMetadata<Payment>> payments = createTestPayments();
        
        when(entityService.findAll(any(ModelSpec.class), eq(Loan.class), isNull()))
            .thenReturn(loans);
        when(entityService.findAll(any(ModelSpec.class), eq(Payment.class), isNull()))
            .thenReturn(payments);
        
        // Act
        dashboardService.getDashboardSummary(); // First call - caches data
        dashboardService.invalidateCache();     // Invalidate cache
        dashboardService.getDashboardSummary(); // Second call - should fetch fresh data
        
        // Assert
        // EntityService should be called twice (once before invalidation, once after)
        verify(entityService, times(2)).findAll(any(ModelSpec.class), eq(Loan.class), isNull());
        verify(entityService, times(2)).findAll(any(ModelSpec.class), eq(Payment.class), isNull());
    }
    
    // ==================== Calculation Method Tests ====================
    
    @Test
    @DisplayName("calculateTotalPortfolioValue should sum all principal amounts")
    void calculateTotalPortfolioValue_withValidLoans_returnsCorrectSum() {
        // Arrange
        List<EntityWithMetadata<Loan>> loans = Arrays.asList(
            createLoanWithMetadata("loan1", "active", new BigDecimal("100000"), null, null, null, null),
            createLoanWithMetadata("loan2", "funded", new BigDecimal("200000"), null, null, null, null),
            createLoanWithMetadata("loan3", "matured", new BigDecimal("300000"), null, null, null, null)
        );
        
        when(entityService.findAll(any(ModelSpec.class), eq(Loan.class), isNull()))
            .thenReturn(loans);
        when(entityService.findAll(any(ModelSpec.class), eq(Payment.class), isNull()))
            .thenReturn(Collections.emptyList());
        
        // Act
        DashboardSummaryDTO result = dashboardService.getDashboardSummary();
        
        // Assert
        assertEquals(new BigDecimal("600000"), result.getTotalPortfolioValue());
    }
    
    @Test
    @DisplayName("calculateTotalPortfolioValue should handle null principal amounts")
    void calculateTotalPortfolioValue_withNullPrincipals_ignoresNulls() {
        // Arrange
        List<EntityWithMetadata<Loan>> loans = Arrays.asList(
            createLoanWithMetadata("loan1", "active", new BigDecimal("100000"), null, null, null, null),
            createLoanWithMetadata("loan2", "funded", null, null, null, null, null),
            createLoanWithMetadata("loan3", "matured", new BigDecimal("200000"), null, null, null, null)
        );
        
        when(entityService.findAll(any(ModelSpec.class), eq(Loan.class), isNull()))
            .thenReturn(loans);
        when(entityService.findAll(any(ModelSpec.class), eq(Payment.class), isNull()))
            .thenReturn(Collections.emptyList());
        
        // Act
        DashboardSummaryDTO result = dashboardService.getDashboardSummary();
        
        // Assert
        assertEquals(new BigDecimal("300000"), result.getTotalPortfolioValue());
    }
    
    @Test
    @DisplayName("calculateActiveLoansCount should count only active and funded loans")
    void calculateActiveLoansCount_withMixedStates_countsOnlyActiveAndFunded() {
        // Arrange
        List<EntityWithMetadata<Loan>> loans = Arrays.asList(
            createLoanWithMetadata("loan1", "active", null, null, null, null, null),
            createLoanWithMetadata("loan2", "funded", null, null, null, null, null),
            createLoanWithMetadata("loan3", "matured", null, null, null, null, null),
            createLoanWithMetadata("loan4", "active", null, null, null, null, null),
            createLoanWithMetadata("loan5", "rejected", null, null, null, null, null)
        );
        
        when(entityService.findAll(any(ModelSpec.class), eq(Loan.class), isNull()))
            .thenReturn(loans);
        when(entityService.findAll(any(ModelSpec.class), eq(Payment.class), isNull()))
            .thenReturn(Collections.emptyList());
        
        // Act
        DashboardSummaryDTO result = dashboardService.getDashboardSummary();
        
        // Assert
        assertEquals(3, result.getActiveLoansCount());
    }
    
    @Test
    @DisplayName("calculateOutstandingPrincipal should sum only active/funded loans")
    void calculateOutstandingPrincipal_withMixedStates_sumsOnlyActiveAndFunded() {
        // Arrange
        List<EntityWithMetadata<Loan>> loans = Arrays.asList(
            createLoanWithMetadata("loan1", "active", null, new BigDecimal("50000"), null, null, null),
            createLoanWithMetadata("loan2", "funded", null, new BigDecimal("75000"), null, null, null),
            createLoanWithMetadata("loan3", "matured", null, new BigDecimal("100000"), null, null, null)
        );
        
        when(entityService.findAll(any(ModelSpec.class), eq(Loan.class), isNull()))
            .thenReturn(loans);
        when(entityService.findAll(any(ModelSpec.class), eq(Payment.class), isNull()))
            .thenReturn(Collections.emptyList());
        
        // Act
        DashboardSummaryDTO result = dashboardService.getDashboardSummary();
        
        // Assert
        assertEquals(new BigDecimal("125000"), result.getOutstandingPrincipal());
    }
    
    @Test
    @DisplayName("calculateActiveBorrowersCount should count distinct borrowers")
    void calculateActiveBorrowersCount_withDuplicateBorrowers_countsDistinct() {
        // Arrange
        List<EntityWithMetadata<Loan>> loans = Arrays.asList(
            createLoanWithMetadata("loan1", "active", null, null, "borrower1", null, null),
            createLoanWithMetadata("loan2", "active", null, null, "borrower1", null, null),
            createLoanWithMetadata("loan3", "funded", null, null, "borrower2", null, null),
            createLoanWithMetadata("loan4", "matured", null, null, "borrower3", null, null)
        );
        
        when(entityService.findAll(any(ModelSpec.class), eq(Loan.class), isNull()))
            .thenReturn(loans);
        when(entityService.findAll(any(ModelSpec.class), eq(Payment.class), isNull()))
            .thenReturn(Collections.emptyList());
        
        // Act
        DashboardSummaryDTO result = dashboardService.getDashboardSummary();
        
        // Assert
        assertEquals(2, result.getActiveBorrowersCount(), "Should count 2 distinct active borrowers");
    }
    
    @Test
    @DisplayName("calculateStatusDistribution should group loans by state")
    void calculateStatusDistribution_withVariousStates_groupsCorrectly() {
        // Arrange
        List<EntityWithMetadata<Loan>> loans = Arrays.asList(
            createLoanWithMetadata("loan1", "active", null, null, null, null, null),
            createLoanWithMetadata("loan2", "active", null, null, null, null, null),
            createLoanWithMetadata("loan3", "funded", null, null, null, null, null),
            createLoanWithMetadata("loan4", "matured", null, null, null, null, null),
            createLoanWithMetadata("loan5", "active", null, null, null, null, null)
        );
        
        when(entityService.findAll(any(ModelSpec.class), eq(Loan.class), isNull()))
            .thenReturn(loans);
        when(entityService.findAll(any(ModelSpec.class), eq(Payment.class), isNull()))
            .thenReturn(Collections.emptyList());
        
        // Act
        DashboardSummaryDTO result = dashboardService.getDashboardSummary();
        StatusDistributionDTO distribution = result.getStatusDistribution();
        
        // Assert
        assertNotNull(distribution);
        assertNotNull(distribution.getLabels());
        assertNotNull(distribution.getValues());
        assertEquals(distribution.getLabels().size(), distribution.getValues().size());
        
        // Should be sorted by count descending, so "active" (3) should be first
        assertEquals("active", distribution.getLabels().get(0));
        assertEquals(3, distribution.getValues().get(0));
    }
    
    @Test
    @DisplayName("calculatePortfolioTrend should include last 12 months")
    void calculatePortfolioTrend_shouldIncludeLast12Months() {
        // Arrange
        LocalDate today = LocalDate.now();
        List<EntityWithMetadata<Loan>> loans = Arrays.asList(
            createLoanWithMetadata("loan1", "active", new BigDecimal("100000"), null, null, today.minusMonths(1), null),
            createLoanWithMetadata("loan2", "funded", new BigDecimal("200000"), null, null, today.minusMonths(2), null)
        );
        
        when(entityService.findAll(any(ModelSpec.class), eq(Loan.class), isNull()))
            .thenReturn(loans);
        when(entityService.findAll(any(ModelSpec.class), eq(Payment.class), isNull()))
            .thenReturn(Collections.emptyList());
        
        // Act
        DashboardSummaryDTO result = dashboardService.getDashboardSummary();
        PortfolioTrendDTO trend = result.getPortfolioTrend();
        
        // Assert
        assertNotNull(trend);
        assertEquals(12, trend.getMonths().size(), "Should have 12 months");
        assertEquals(12, trend.getValues().size(), "Should have 12 values");
        
        // Verify month format (yyyy-MM)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
        for (String month : trend.getMonths()) {
            assertDoesNotThrow(() -> YearMonth.parse(month, formatter));
        }
    }
    
    @Test
    @DisplayName("calculateMonthlyPayments should include last 6 months")
    void calculateMonthlyPayments_shouldIncludeLast6Months() {
        // Arrange
        LocalDate today = LocalDate.now();
        List<EntityWithMetadata<Payment>> payments = Arrays.asList(
            createPaymentWithMetadata("payment1", new BigDecimal("10000"), today.minusMonths(1)),
            createPaymentWithMetadata("payment2", new BigDecimal("15000"), today.minusMonths(2))
        );
        
        when(entityService.findAll(any(ModelSpec.class), eq(Loan.class), isNull()))
            .thenReturn(Collections.emptyList());
        when(entityService.findAll(any(ModelSpec.class), eq(Payment.class), isNull()))
            .thenReturn(payments);
        
        // Act
        DashboardSummaryDTO result = dashboardService.getDashboardSummary();
        MonthlyPaymentsDTO monthlyPayments = result.getMonthlyPayments();
        
        // Assert
        assertNotNull(monthlyPayments);
        assertEquals(6, monthlyPayments.getMonths().size(), "Should have 6 months");
        assertEquals(6, monthlyPayments.getAmounts().size(), "Should have 6 amounts");
    }
    
    // ==================== Helper Methods ====================
    
    private List<EntityWithMetadata<Loan>> createTestLoans() {
        return Arrays.asList(
            createLoanWithMetadata("loan1", "active", new BigDecimal("100000"), new BigDecimal("90000"), "borrower1", LocalDate.now().minusMonths(1), new BigDecimal("5.5")),
            createLoanWithMetadata("loan2", "funded", new BigDecimal("200000"), new BigDecimal("200000"), "borrower2", LocalDate.now().minusMonths(2), new BigDecimal("6.0")),
            createLoanWithMetadata("loan3", "matured", new BigDecimal("150000"), new BigDecimal("0"), "borrower3", LocalDate.now().minusMonths(6), new BigDecimal("5.75"))
        );
    }
    
    private List<EntityWithMetadata<Payment>> createTestPayments() {
        return Arrays.asList(
            createPaymentWithMetadata("payment1", new BigDecimal("5000"), LocalDate.now().minusMonths(1)),
            createPaymentWithMetadata("payment2", new BigDecimal("7500"), LocalDate.now().minusMonths(2))
        );
    }
    
    private EntityWithMetadata<Loan> createLoanWithMetadata(String id, String state, BigDecimal principal, 
                                                             BigDecimal outstanding, String partyId, 
                                                             LocalDate fundingDate, BigDecimal apr) {
        Loan loan = new Loan();
        loan.setLoanId(id);
        loan.setPrincipalAmount(principal);
        loan.setOutstandingPrincipal(outstanding);
        loan.setPartyId(partyId);
        loan.setFundingDate(fundingDate);
        loan.setApr(apr);
        
        EntityMetadata metadata = new EntityMetadata();
        metadata.setState(state);
        
        return new EntityWithMetadata<>(loan, metadata);
    }
    
    private EntityWithMetadata<Payment> createPaymentWithMetadata(String id, BigDecimal amount, LocalDate valueDate) {
        Payment payment = new Payment();
        payment.setPaymentId(id);
        payment.setPaymentAmount(amount);
        payment.setValueDate(valueDate);
        
        EntityMetadata metadata = new EntityMetadata();
        
        return new EntityWithMetadata<>(payment, metadata);
    }
}

