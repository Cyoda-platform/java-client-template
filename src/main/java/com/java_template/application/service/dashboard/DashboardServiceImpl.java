package com.java_template.application.service.dashboard;

import com.java_template.application.dto.dashboard.*;
import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Implementation of DashboardService with caching.
 * 
 * <p>Aggregates data from Loan and Payment entities to provide dashboard metrics.
 * Results are cached for 5 minutes to reduce database load.</p>
 */
@Service
public class DashboardServiceImpl implements DashboardService {
    
    private static final Logger logger = LoggerFactory.getLogger(DashboardServiceImpl.class);
    
    /**
     * Cache TTL: 5 minutes in milliseconds
     */
    private static final long CACHE_TTL_MS = 300_000L; // 5 minutes
    
    /**
     * Cache key for dashboard summary data
     */
    private static final String CACHE_KEY = "dashboard_summary";
    
    /**
     * Thread-safe cache for dashboard data
     */
    private final ConcurrentMap<String, CachedDashboardSummary> cache = new ConcurrentHashMap<>();
    
    private final EntityService entityService;
    
    /**
     * Constructor with dependency injection.
     * 
     * @param entityService Service for accessing entity data
     */
    public DashboardServiceImpl(EntityService entityService) {
        this.entityService = entityService;
    }
    
    @Override
    public DashboardSummaryDTO getDashboardSummary() {
        logger.debug("Retrieving dashboard summary");
        
        // Use atomic compute operation for thread-safe caching
        CachedDashboardSummary cached = cache.compute(CACHE_KEY, (key, existing) -> {
            if (existing != null && existing.isValid()) {
                logger.debug("Returning cached dashboard data (age: {} ms)", 
                    System.currentTimeMillis() - existing.timestamp());
                return existing;
            }
            
            logger.info("Cache miss or expired - fetching fresh dashboard data");
            try {
                DashboardSummaryDTO freshData = aggregateDashboardData();
                return new CachedDashboardSummary(freshData, System.currentTimeMillis());
            } catch (Exception e) {
                logger.error("Failed to aggregate dashboard data", e);
                throw new RuntimeException("Failed to retrieve dashboard data", e);
            }
        });
        
        return cached.data();
    }
    
    @Override
    public void invalidateCache() {
        cache.remove(CACHE_KEY);
        logger.info("Dashboard cache manually invalidated");
    }
    
    /**
     * Aggregates all dashboard data from entity sources.
     * 
     * @return DashboardSummaryDTO with all calculated metrics
     */
    private DashboardSummaryDTO aggregateDashboardData() {
        logger.debug("Starting dashboard data aggregation");
        
        // Retrieve all loans
        List<EntityWithMetadata<Loan>> allLoans = retrieveAllLoans();
        logger.info("Retrieved {} loans for dashboard aggregation", allLoans.size());
        
        // Retrieve all payments
        List<EntityWithMetadata<Payment>> allPayments = retrieveAllPayments();
        logger.info("Retrieved {} payments for dashboard aggregation", allPayments.size());
        
        // Calculate all metrics
        BigDecimal totalPortfolioValue = calculateTotalPortfolioValue(allLoans);
        Integer activeLoansCount = calculateActiveLoansCount(allLoans);
        BigDecimal outstandingPrincipal = calculateOutstandingPrincipal(allLoans);
        Integer activeBorrowersCount = calculateActiveBorrowersCount(allLoans);
        StatusDistributionDTO statusDistribution = calculateStatusDistribution(allLoans);
        PortfolioTrendDTO portfolioTrend = calculatePortfolioTrend(allLoans);
        List<BigDecimal> aprDistribution = calculateAprDistribution(allLoans);
        MonthlyPaymentsDTO monthlyPayments = calculateMonthlyPayments(allPayments);
        
        logger.info("Dashboard aggregation complete - Portfolio: {}, Active Loans: {}, Active Borrowers: {}", 
            totalPortfolioValue, activeLoansCount, activeBorrowersCount);
        
        return new DashboardSummaryDTO(
            totalPortfolioValue,
            activeLoansCount,
            outstandingPrincipal,
            activeBorrowersCount,
            statusDistribution,
            portfolioTrend,
            aprDistribution,
            monthlyPayments
        );
    }
    
    /**
     * Retrieves all loans from the database.
     * 
     * @return List of loans with metadata
     */
    private List<EntityWithMetadata<Loan>> retrieveAllLoans() {
        try {
            ModelSpec modelSpec = new ModelSpec()
                .withName(Loan.ENTITY_NAME)
                .withVersion(Loan.ENTITY_VERSION);
            return entityService.findAll(modelSpec, Loan.class, null);
        } catch (Exception e) {
            logger.error("Failed to retrieve loans", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Retrieves all payments from the database.
     * 
     * @return List of payments with metadata
     */
    private List<EntityWithMetadata<Payment>> retrieveAllPayments() {
        try {
            ModelSpec modelSpec = new ModelSpec()
                .withName(Payment.ENTITY_NAME)
                .withVersion(Payment.ENTITY_VERSION);
            return entityService.findAll(modelSpec, Payment.class, null);
        } catch (Exception e) {
            logger.error("Failed to retrieve payments", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Calculates total portfolio value (sum of all loan principal amounts).
     * 
     * @param loans List of all loans
     * @return Total portfolio value
     */
    private BigDecimal calculateTotalPortfolioValue(List<EntityWithMetadata<Loan>> loans) {
        return loans.stream()
            .map(EntityWithMetadata::entity)
            .map(Loan::getPrincipalAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Calculates count of active loans (loans in "active" or "funded" states).
     * 
     * @param loans List of all loans
     * @return Count of active loans
     */
    private Integer calculateActiveLoansCount(List<EntityWithMetadata<Loan>> loans) {
        return (int) loans.stream()
            .filter(this::isActiveLoan)
            .count();
    }
    
    /**
     * Calculates total outstanding principal for active/funded loans.
     * 
     * @param loans List of all loans
     * @return Total outstanding principal
     */
    private BigDecimal calculateOutstandingPrincipal(List<EntityWithMetadata<Loan>> loans) {
        return loans.stream()
            .filter(this::isActiveLoan)
            .map(EntityWithMetadata::entity)
            .map(Loan::getOutstandingPrincipal)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Calculates count of distinct borrowers with active/funded loans.
     * 
     * @param loans List of all loans
     * @return Count of distinct active borrowers
     */
    private Integer calculateActiveBorrowersCount(List<EntityWithMetadata<Loan>> loans) {
        return (int) loans.stream()
            .filter(this::isActiveLoan)
            .map(EntityWithMetadata::entity)
            .map(Loan::getPartyId)
            .filter(Objects::nonNull)
            .distinct()
            .count();
    }
    
    /**
     * Calculates distribution of loans by workflow state.
     * 
     * @param loans List of all loans
     * @return StatusDistributionDTO with labels and counts
     */
    private StatusDistributionDTO calculateStatusDistribution(List<EntityWithMetadata<Loan>> loans) {
        Map<String, Long> stateCounts = loans.stream()
            .collect(Collectors.groupingBy(
                loan -> loan.getState() != null ? loan.getState() : "unknown",
                Collectors.counting()
            ));
        
        // Sort by count descending for better visualization
        List<Map.Entry<String, Long>> sortedEntries = stateCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .toList();
        
        List<String> labels = sortedEntries.stream()
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        List<Integer> values = sortedEntries.stream()
            .map(e -> e.getValue().intValue())
            .collect(Collectors.toList());
        
        return new StatusDistributionDTO(labels, values);
    }
    
    /**
     * Calculates portfolio trend over the last 12 months.
     * 
     * @param loans List of all loans
     * @return PortfolioTrendDTO with months and values
     */
    private PortfolioTrendDTO calculatePortfolioTrend(List<EntityWithMetadata<Loan>> loans) {
        // Calculate last 12 months
        YearMonth currentMonth = YearMonth.now();
        List<YearMonth> last12Months = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            last12Months.add(currentMonth.minusMonths(i));
        }
        
        // Group loans by funding month and sum principal amounts
        Map<YearMonth, BigDecimal> monthlyValues = new HashMap<>();
        for (YearMonth month : last12Months) {
            monthlyValues.put(month, BigDecimal.ZERO);
        }
        
        for (EntityWithMetadata<Loan> loanWithMeta : loans) {
            Loan loan = loanWithMeta.entity();
            if (loan.getFundingDate() != null && loan.getPrincipalAmount() != null) {
                YearMonth fundingMonth = YearMonth.from(loan.getFundingDate());
                if (monthlyValues.containsKey(fundingMonth)) {
                    monthlyValues.put(fundingMonth, 
                        monthlyValues.get(fundingMonth).add(loan.getPrincipalAmount()));
                }
            }
        }
        
        // Format as parallel arrays
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
        List<String> months = last12Months.stream()
            .map(formatter::format)
            .collect(Collectors.toList());
        
        List<BigDecimal> values = last12Months.stream()
            .map(monthlyValues::get)
            .collect(Collectors.toList());
        
        return new PortfolioTrendDTO(months, values);
    }
    
    /**
     * Calculates APR distribution (array of all APR values).
     * 
     * @param loans List of all loans
     * @return List of APR values
     */
    private List<BigDecimal> calculateAprDistribution(List<EntityWithMetadata<Loan>> loans) {
        return loans.stream()
            .map(EntityWithMetadata::entity)
            .map(Loan::getApr)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Calculates monthly payment amounts over the last 6 months.
     * 
     * @param payments List of all payments
     * @return MonthlyPaymentsDTO with months and amounts
     */
    private MonthlyPaymentsDTO calculateMonthlyPayments(List<EntityWithMetadata<Payment>> payments) {
        // Calculate last 6 months
        YearMonth currentMonth = YearMonth.now();
        List<YearMonth> last6Months = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            last6Months.add(currentMonth.minusMonths(i));
        }
        
        // Group payments by month and sum amounts
        Map<YearMonth, BigDecimal> monthlyAmounts = new HashMap<>();
        for (YearMonth month : last6Months) {
            monthlyAmounts.put(month, BigDecimal.ZERO);
        }
        
        for (EntityWithMetadata<Payment> paymentWithMeta : payments) {
            Payment payment = paymentWithMeta.entity();
            if (payment.getValueDate() != null && payment.getPaymentAmount() != null) {
                YearMonth paymentMonth = YearMonth.from(payment.getValueDate());
                if (monthlyAmounts.containsKey(paymentMonth)) {
                    monthlyAmounts.put(paymentMonth, 
                        monthlyAmounts.get(paymentMonth).add(payment.getPaymentAmount()));
                }
            }
        }
        
        // Format as parallel arrays
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
        List<String> months = last6Months.stream()
            .map(formatter::format)
            .collect(Collectors.toList());
        
        List<BigDecimal> amounts = last6Months.stream()
            .map(monthlyAmounts::get)
            .collect(Collectors.toList());
        
        return new MonthlyPaymentsDTO(months, amounts);
    }
    
    /**
     * Checks if a loan is considered active (in "active" or "funded" state).
     * 
     * @param loan Loan with metadata
     * @return true if loan is active or funded
     */
    private boolean isActiveLoan(EntityWithMetadata<Loan> loan) {
        String state = loan.getState();
        return "active".equals(state) || "funded".equals(state);
    }
    
    /**
     * Record for cached dashboard summary with timestamp.
     * 
     * @param data The cached dashboard data
     * @param timestamp Timestamp when data was cached (milliseconds since epoch)
     */
    private record CachedDashboardSummary(DashboardSummaryDTO data, long timestamp) {
        
        /**
         * Checks if the cached data is still valid based on TTL.
         * 
         * @return true if cache is still valid, false if expired
         */
        public boolean isValid() {
            return System.currentTimeMillis() - timestamp < CACHE_TTL_MS;
        }
    }
}

