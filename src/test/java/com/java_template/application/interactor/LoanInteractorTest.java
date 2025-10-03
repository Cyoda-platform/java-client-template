package com.java_template.application.interactor;

import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.common.dto.EntityWithMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ABOUTME: Unit tests for LoanInteractor covering CRUD operations, search functionality,
 * and workflow transitions (approve, fund).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoanInteractor Tests")
class LoanInteractorTest extends BaseInteractorTest<Loan> {

    private LoanInteractor loanInteractor;

    @BeforeEach
    void setUp() {
        loanInteractor = new LoanInteractor(entityService, objectMapper);
    }

    @Override
    protected String getEntityName() {
        return Loan.ENTITY_NAME;
    }

    @Override
    protected Integer getEntityVersion() {
        return Loan.ENTITY_VERSION;
    }

    @Override
    protected String getBusinessIdField() {
        return "loanId";
    }

    @Override
    protected Loan createValidEntity(String businessId) {
        Loan loan = new Loan();
        loan.setLoanId(businessId);
        loan.setPartyId("PARTY-001");
        loan.setPrincipalAmount(new BigDecimal("100000.00"));
        loan.setApr(new BigDecimal("5.5"));
        loan.setTermMonths(36);
        loan.setDayCountBasis("ACT/365F");
        loan.setRepaymentDay(15);
        loan.setOutstandingPrincipal(BigDecimal.ZERO);
        loan.setAccruedInterest(BigDecimal.ZERO);
        return loan;
    }

    @Override
    protected String getBusinessId(Loan entity) {
        return entity.getLoanId();
    }

    @Override
    protected void setBusinessId(Loan entity, String businessId) {
        entity.setLoanId(businessId);
    }

    @Override
    protected void assertEntityEquals(Loan expected, Loan actual) {
        assertEquals(expected.getLoanId(), actual.getLoanId());
        assertEquals(expected.getPartyId(), actual.getPartyId());
        assertEquals(expected.getPrincipalAmount(), actual.getPrincipalAmount());
        assertEquals(expected.getApr(), actual.getApr());
        assertEquals(expected.getTermMonths(), actual.getTermMonths());
    }

    @Nested
    @DisplayName("Create Loan Tests")
    class CreateLoanTests {

        @Test
        @DisplayName("Should create loan successfully with valid data")
        void shouldCreateLoanSuccessfully() {
            Loan loan = createValidEntity("LOAN-001");
            EntityWithMetadata<Loan> expected = createEntityWithMetadata(loan, testEntityId);

            mockFindByBusinessIdOrNullNotFound("LOAN-001");
            mockCreate(loan, expected);

            EntityWithMetadata<Loan> result = loanInteractor.createLoan(loan);

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(loan.getCreatedAt());
            assertNotNull(loan.getUpdatedAt());
            assertEntityServiceFindByBusinessIdOrNullCalled("LOAN-001", 1);
            assertEntityServiceCreateCalled(1);
        }

        @Test
        @DisplayName("Should throw DuplicateEntityException when loan with same loanId exists")
        void shouldThrowExceptionWhenDuplicateLoanId() {
            testCreateDuplicate(
                    loanInteractor::createLoan,
                    LoanInteractor.DuplicateEntityException.class
            );
        }

        @Test
        @DisplayName("Should throw exception when loanId is null")
        void shouldThrowExceptionWhenLoanIdIsNull() {
            Loan loan = createValidEntity("LOAN-001");

            // Lombok's @NonNull throws NullPointerException when setting null
            NullPointerException exception = assertThrows(
                    NullPointerException.class,
                    () -> loan.setLoanId(null)
            );

            assertTrue(exception.getMessage().contains("loanId"));
        }

        @Test
        @DisplayName("Should throw exception when loanId is empty")
        void shouldThrowExceptionWhenLoanIdIsEmpty() {
            Loan loan = createValidEntity("LOAN-001");
            loan.setLoanId("  ");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> loanInteractor.createLoan(loan)
            );

            assertEquals("loanId cannot be empty", exception.getMessage());
            assertEntityServiceCreateCalled(0);
        }
    }

    @Nested
    @DisplayName("Get Loan Tests")
    class GetLoanTests {

        @Test
        @DisplayName("Should get loan by technical ID successfully")
        void shouldGetLoanByIdSuccessfully() {
            Loan loan = createValidEntity("LOAN-001");
            EntityWithMetadata<Loan> expected = createEntityWithMetadata(loan, testEntityId);

            mockGetById(testEntityId, expected);

            EntityWithMetadata<Loan> result = loanInteractor.getLoanById(testEntityId);

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertEntityEquals(loan, result.entity());
            assertEntityServiceGetByIdCalled(testEntityId, 1);
        }

        @Test
        @DisplayName("Should get loan by business ID successfully")
        void shouldGetLoanByBusinessIdSuccessfully() {
            testGetByBusinessIdSuccess(loanInteractor::getLoanByBusinessId);
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when loan not found by business ID")
        void shouldThrowExceptionWhenLoanNotFoundByBusinessId() {
            testGetByBusinessIdNotFound(
                    loanInteractor::getLoanByBusinessId,
                    LoanInteractor.EntityNotFoundException.class
            );
        }
    }

    @Nested
    @DisplayName("Update Loan Tests")
    class UpdateLoanTests {

        @Test
        @DisplayName("Should update loan by technical ID successfully")
        void shouldUpdateLoanByIdSuccessfully() {
            Loan loan = createValidEntity("LOAN-001");
            loan.setPrincipalAmount(new BigDecimal("150000.00"));
            EntityWithMetadata<Loan> expected = createEntityWithMetadata(loan, testEntityId);

            mockUpdate(testEntityId, expected);

            EntityWithMetadata<Loan> result = loanInteractor.updateLoanById(testEntityId, loan, "UPDATE");

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(loan.getUpdatedAt());
            assertEntityServiceUpdateCalled(testEntityId, 1);
        }

        @Test
        @DisplayName("Should update loan by business ID successfully")
        void shouldUpdateLoanByBusinessIdSuccessfully() {
            Loan loan = createValidEntity("LOAN-001");
            loan.setPrincipalAmount(new BigDecimal("150000.00"));
            EntityWithMetadata<Loan> expected = createEntityWithMetadata(loan, testEntityId);

            mockUpdateByBusinessId(expected);

            EntityWithMetadata<Loan> result = loanInteractor.updateLoanByBusinessId("LOAN-001", loan, "UPDATE");

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(loan.getUpdatedAt());
            assertEntityServiceUpdateByBusinessIdCalled(1);
        }
    }

    @Nested
    @DisplayName("Delete Loan Tests")
    class DeleteLoanTests {

        @Test
        @DisplayName("Should delete loan successfully")
        void shouldDeleteLoanSuccessfully() {
            mockDeleteById(testEntityId);

            assertDoesNotThrow(() -> loanInteractor.deleteLoan(testEntityId));

            assertEntityServiceDeleteByIdCalled(testEntityId, 1);
        }
    }

    @Nested
    @DisplayName("Get All Loans Tests")
    class GetAllLoansTests {

        @Test
        @DisplayName("Should get all loans successfully")
        void shouldGetAllLoansSuccessfully() {
            Loan loan1 = createValidEntity("LOAN-001");
            Loan loan2 = createValidEntity("LOAN-002");
            List<EntityWithMetadata<Loan>> expected = List.of(
                    createEntityWithMetadata(loan1, testEntityId),
                    createEntityWithMetadata(loan2, testEntityId2)
            );

            mockFindAll(expected);

            List<EntityWithMetadata<Loan>> result = loanInteractor.getAllLoans();

            assertNotNull(result);
            assertEquals(2, result.size());
            assertEntityServiceFindAllCalled(1);
        }

        @Test
        @DisplayName("Should return empty list when no loans exist")
        void shouldReturnEmptyListWhenNoLoans() {
            mockFindAll(List.of());

            List<EntityWithMetadata<Loan>> result = loanInteractor.getAllLoans();

            assertNotNull(result);
            assertTrue(result.isEmpty());
            assertEntityServiceFindAllCalled(1);
        }
    }

    @Nested
    @DisplayName("Get Loans By Party Tests")
    class GetLoansByPartyTests {

        @Test
        @DisplayName("Should get loans by party ID successfully")
        void shouldGetLoansByPartySuccessfully() {
            Loan loan1 = createValidEntity("LOAN-001");
            loan1.setPartyId("PARTY-001");
            Loan loan2 = createValidEntity("LOAN-002");
            loan2.setPartyId("PARTY-001");

            List<EntityWithMetadata<Loan>> expected = List.of(
                    createEntityWithMetadata(loan1, testEntityId),
                    createEntityWithMetadata(loan2, testEntityId2)
            );

            mockSearch(expected);

            List<EntityWithMetadata<Loan>> result = loanInteractor.getLoansByParty("PARTY-001");

            assertNotNull(result);
            assertEquals(2, result.size());
            assertEntityServiceSearchCalled(1);
        }

        @Test
        @DisplayName("Should return empty list when no loans for party")
        void shouldReturnEmptyListWhenNoLoansForParty() {
            mockSearch(List.of());

            List<EntityWithMetadata<Loan>> result = loanInteractor.getLoansByParty("PARTY-999");

            assertNotNull(result);
            assertTrue(result.isEmpty());
            assertEntityServiceSearchCalled(1);
        }
    }

    @Nested
    @DisplayName("Advanced Search Tests")
    class AdvancedSearchTests {

        @Test
        @DisplayName("Should search loans with all criteria")
        void shouldSearchLoansWithAllCriteria() {
            LoanInteractor.LoanSearchCriteria criteria = new LoanInteractor.LoanSearchCriteria();
            criteria.setPartyId("PARTY-001");
            criteria.setMinPrincipal(new BigDecimal("50000"));
            criteria.setMaxPrincipal(new BigDecimal("200000"));
            criteria.setTermMonths(36);

            Loan loan = createValidEntity("LOAN-001");
            List<EntityWithMetadata<Loan>> expected = List.of(createEntityWithMetadata(loan, testEntityId));

            mockSearch(expected);

            List<EntityWithMetadata<Loan>> result = loanInteractor.advancedSearch(criteria);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEntityServiceSearchCalled(1);
        }

        @Test
        @DisplayName("Should search loans with partial criteria")
        void shouldSearchLoansWithPartialCriteria() {
            LoanInteractor.LoanSearchCriteria criteria = new LoanInteractor.LoanSearchCriteria();
            criteria.setMinPrincipal(new BigDecimal("100000"));

            mockSearch(List.of());

            List<EntityWithMetadata<Loan>> result = loanInteractor.advancedSearch(criteria);

            assertNotNull(result);
            assertTrue(result.isEmpty());
            assertEntityServiceSearchCalled(1);
        }

        @Test
        @DisplayName("Should search loans with empty criteria")
        void shouldSearchLoansWithEmptyCriteria() {
            LoanInteractor.LoanSearchCriteria criteria = new LoanInteractor.LoanSearchCriteria();

            mockSearch(List.of());

            List<EntityWithMetadata<Loan>> result = loanInteractor.advancedSearch(criteria);

            assertNotNull(result);
            assertEntityServiceSearchCalled(1);
        }
    }

    @Nested
    @DisplayName("Approve Loan Tests")
    class ApproveLoanTests {

        @Test
        @DisplayName("Should approve loan successfully")
        void shouldApproveLoanSuccessfully() {
            Loan loan = createValidEntity("LOAN-001");
            EntityWithMetadata<Loan> existing = createEntityWithMetadata(loan, testEntityId);
            EntityWithMetadata<Loan> updated = createEntityWithMetadata(loan, testEntityId, "APPROVED");

            mockGetById(testEntityId, existing);
            mockUpdate(testEntityId, updated);

            EntityWithMetadata<Loan> result = loanInteractor.approveLoan(testEntityId);

            assertNotNull(result);
            assertMetadata(result, testEntityId, "APPROVED");
            assertNotNull(loan.getUpdatedAt());
            assertEntityServiceGetByIdCalled(testEntityId, 1);
            assertEntityServiceUpdateCalled(testEntityId, 1);
        }
    }

    @Nested
    @DisplayName("Fund Loan Tests")
    class FundLoanTests {

        @Test
        @DisplayName("Should fund loan successfully")
        void shouldFundLoanSuccessfully() {
            Loan loan = createValidEntity("LOAN-001");
            EntityWithMetadata<Loan> existing = createEntityWithMetadata(loan, testEntityId, "APPROVED");
            EntityWithMetadata<Loan> updated = createEntityWithMetadata(loan, testEntityId, "FUNDED");

            mockGetById(testEntityId, existing);
            mockUpdate(testEntityId, updated);

            EntityWithMetadata<Loan> result = loanInteractor.fundLoan(testEntityId);

            assertNotNull(result);
            assertMetadata(result, testEntityId, "FUNDED");
            assertNotNull(loan.getUpdatedAt());
            assertEntityServiceGetByIdCalled(testEntityId, 1);
            assertEntityServiceUpdateCalled(testEntityId, 1);
        }
    }
}

