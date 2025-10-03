package com.java_template.application.interactor;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ABOUTME: Unit tests for PaymentInteractor covering CRUD operations and search functionality.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentInteractor Tests")
class PaymentInteractorTest extends BaseInteractorTest<Payment> {

    private PaymentInteractor paymentInteractor;

    @BeforeEach
    void setUp() {
        paymentInteractor = new PaymentInteractor(entityService, objectMapper);
    }

    @Override
    protected String getEntityName() {
        return Payment.ENTITY_NAME;
    }

    @Override
    protected Integer getEntityVersion() {
        return Payment.ENTITY_VERSION;
    }

    @Override
    protected String getBusinessIdField() {
        return "paymentId";
    }

    @Override
    protected Payment createValidEntity(String businessId) {
        Payment payment = new Payment();
        payment.setPaymentId(businessId);
        payment.setLoanId("LOAN-001");
        payment.setAmount(new BigDecimal("1000.00"));
        payment.setValueDate(LocalDate.now());
        payment.setCurrency("USD");
        payment.setPaymentMethod("BANK_TRANSFER");
        return payment;
    }

    @Override
    protected String getBusinessId(Payment entity) {
        return entity.getPaymentId();
    }

    @Override
    protected void setBusinessId(Payment entity, String businessId) {
        entity.setPaymentId(businessId);
    }

    @Override
    protected void assertEntityEquals(Payment expected, Payment actual) {
        assertEquals(expected.getPaymentId(), actual.getPaymentId());
        assertEquals(expected.getLoanId(), actual.getLoanId());
        assertEquals(expected.getAmount(), actual.getAmount());
        assertEquals(expected.getCurrency(), actual.getCurrency());
    }

    @Nested
    @DisplayName("Create Payment Tests")
    class CreatePaymentTests {

        @Test
        @DisplayName("Should create payment successfully with valid data")
        void shouldCreatePaymentSuccessfully() {
            Payment payment = createValidEntity("PAY-001");
            EntityWithMetadata<Payment> expected = createEntityWithMetadata(payment, testEntityId);

            mockFindByBusinessIdOrNullNotFound("PAY-001");
            mockCreate(payment, expected);

            EntityWithMetadata<Payment> result = paymentInteractor.createPayment(payment);

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(payment.getCapturedAt());
            assertNotNull(payment.getCreatedAt());
            assertNotNull(payment.getUpdatedAt());
            assertEquals("MANUAL", payment.getSourceType());
            assertEntityServiceFindByBusinessIdOrNullCalled("PAY-001", 1);
            assertEntityServiceCreateCalled(1);
        }

        @Test
        @DisplayName("Should throw DuplicateEntityException when payment with same paymentId exists")
        void shouldThrowExceptionWhenDuplicatePaymentId() {
            testCreateDuplicate(
                    paymentInteractor::createPayment,
                    PaymentInteractor.DuplicateEntityException.class
            );
        }

        @Test
        @DisplayName("Should throw exception when paymentId is null")
        void shouldThrowExceptionWhenPaymentIdIsNull() {
            Payment payment = createValidEntity("PAY-001");

            // Lombok's @NonNull throws NullPointerException when setting null
            NullPointerException exception = assertThrows(
                    NullPointerException.class,
                    () -> payment.setPaymentId(null)
            );

            assertTrue(exception.getMessage().contains("paymentId"));
        }

        @Test
        @DisplayName("Should throw exception when paymentId is empty")
        void shouldThrowExceptionWhenPaymentIdIsEmpty() {
            Payment payment = createValidEntity("PAY-001");
            payment.setPaymentId("  ");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> paymentInteractor.createPayment(payment)
            );

            assertEquals("paymentId cannot be empty", exception.getMessage());
            assertEntityServiceCreateCalled(0);
        }
    }

    @Nested
    @DisplayName("Get Payment Tests")
    class GetPaymentTests {

        @Test
        @DisplayName("Should get payment by technical ID successfully")
        void shouldGetPaymentByIdSuccessfully() {
            Payment payment = createValidEntity("PAY-001");
            EntityWithMetadata<Payment> expected = createEntityWithMetadata(payment, testEntityId);

            mockGetById(testEntityId, expected);

            EntityWithMetadata<Payment> result = paymentInteractor.getPaymentById(testEntityId);

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertEntityEquals(payment, result.entity());
            assertEntityServiceGetByIdCalled(testEntityId, 1);
        }

        @Test
        @DisplayName("Should get payment by business ID successfully")
        void shouldGetPaymentByBusinessIdSuccessfully() {
            testGetByBusinessIdSuccess(paymentInteractor::getPaymentByBusinessId);
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when payment not found by business ID")
        void shouldThrowExceptionWhenPaymentNotFoundByBusinessId() {
            testGetByBusinessIdNotFound(
                    paymentInteractor::getPaymentByBusinessId,
                    PaymentInteractor.EntityNotFoundException.class
            );
        }
    }

    @Nested
    @DisplayName("Update Payment Tests")
    class UpdatePaymentTests {

        @Test
        @DisplayName("Should update payment by technical ID successfully")
        void shouldUpdatePaymentByIdSuccessfully() {
            Payment payment = createValidEntity("PAY-001");
            payment.setAmount(new BigDecimal("2000.00"));
            EntityWithMetadata<Payment> expected = createEntityWithMetadata(payment, testEntityId);

            mockUpdate(testEntityId, expected);

            EntityWithMetadata<Payment> result = paymentInteractor.updatePaymentById(testEntityId, payment, "UPDATE");

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(payment.getUpdatedAt());
            assertEntityServiceUpdateCalled(testEntityId, 1);
        }

        @Test
        @DisplayName("Should update payment by business ID successfully")
        void shouldUpdatePaymentByBusinessIdSuccessfully() {
            Payment payment = createValidEntity("PAY-001");
            payment.setAmount(new BigDecimal("2000.00"));
            EntityWithMetadata<Payment> expected = createEntityWithMetadata(payment, testEntityId);

            mockUpdateByBusinessId(expected);

            EntityWithMetadata<Payment> result = paymentInteractor.updatePaymentByBusinessId("PAY-001", payment, "UPDATE");

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(payment.getUpdatedAt());
            assertEntityServiceUpdateByBusinessIdCalled(1);
        }
    }

    @Nested
    @DisplayName("Delete Payment Tests")
    class DeletePaymentTests {

        @Test
        @DisplayName("Should delete payment successfully")
        void shouldDeletePaymentSuccessfully() {
            mockDeleteById(testEntityId);

            assertDoesNotThrow(() -> paymentInteractor.deletePayment(testEntityId));

            assertEntityServiceDeleteByIdCalled(testEntityId, 1);
        }
    }

    @Nested
    @DisplayName("Get All Payments Tests")
    class GetAllPaymentsTests {

        @Test
        @DisplayName("Should get all payments successfully")
        void shouldGetAllPaymentsSuccessfully() {
            Payment payment1 = createValidEntity("PAY-001");
            Payment payment2 = createValidEntity("PAY-002");
            List<EntityWithMetadata<Payment>> expected = List.of(
                    createEntityWithMetadata(payment1, testEntityId),
                    createEntityWithMetadata(payment2, testEntityId2)
            );

            mockFindAll(expected);

            List<EntityWithMetadata<Payment>> result = paymentInteractor.getAllPayments();

            assertNotNull(result);
            assertEquals(2, result.size());
            assertEntityServiceFindAllCalled(1);
        }

        @Test
        @DisplayName("Should return empty list when no payments exist")
        void shouldReturnEmptyListWhenNoPayments() {
            mockFindAll(List.of());

            List<EntityWithMetadata<Payment>> result = paymentInteractor.getAllPayments();

            assertNotNull(result);
            assertTrue(result.isEmpty());
            assertEntityServiceFindAllCalled(1);
        }
    }

    @Nested
    @DisplayName("Get Payments By Loan Tests")
    class GetPaymentsByLoanTests {

        @Test
        @DisplayName("Should get payments by loan ID successfully")
        void shouldGetPaymentsByLoanSuccessfully() {
            Payment payment1 = createValidEntity("PAY-001");
            payment1.setLoanId("LOAN-001");
            Payment payment2 = createValidEntity("PAY-002");
            payment2.setLoanId("LOAN-001");

            List<EntityWithMetadata<Payment>> expected = List.of(
                    createEntityWithMetadata(payment1, testEntityId),
                    createEntityWithMetadata(payment2, testEntityId2)
            );

            mockSearch(expected);

            List<EntityWithMetadata<Payment>> result = paymentInteractor.getPaymentsByLoan("LOAN-001");

            assertNotNull(result);
            assertEquals(2, result.size());
            assertEntityServiceSearchCalled(1);
        }

        @Test
        @DisplayName("Should return empty list when no payments for loan")
        void shouldReturnEmptyListWhenNoPaymentsForLoan() {
            mockSearch(List.of());

            List<EntityWithMetadata<Payment>> result = paymentInteractor.getPaymentsByLoan("LOAN-999");

            assertNotNull(result);
            assertTrue(result.isEmpty());
            assertEntityServiceSearchCalled(1);
        }
    }

    @Nested
    @DisplayName("Advanced Search Tests")
    class AdvancedSearchTests {

        @Test
        @DisplayName("Should search payments with all criteria")
        void shouldSearchPaymentsWithAllCriteria() {
            PaymentInteractor.PaymentSearchCriteria criteria = new PaymentInteractor.PaymentSearchCriteria();
            criteria.setLoanId("LOAN-001");
            criteria.setMinAmount(new BigDecimal("500"));
            criteria.setMaxAmount(new BigDecimal("2000"));
            criteria.setValueDateFrom(LocalDate.now().minusDays(30));
            criteria.setValueDateTo(LocalDate.now());

            Payment payment = createValidEntity("PAY-001");
            List<EntityWithMetadata<Payment>> expected = List.of(createEntityWithMetadata(payment, testEntityId));

            mockSearch(expected);

            List<EntityWithMetadata<Payment>> result = paymentInteractor.advancedSearch(criteria);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEntityServiceSearchCalled(1);
        }

        @Test
        @DisplayName("Should search payments with partial criteria")
        void shouldSearchPaymentsWithPartialCriteria() {
            PaymentInteractor.PaymentSearchCriteria criteria = new PaymentInteractor.PaymentSearchCriteria();
            criteria.setMinAmount(new BigDecimal("1000"));

            mockSearch(List.of());

            List<EntityWithMetadata<Payment>> result = paymentInteractor.advancedSearch(criteria);

            assertNotNull(result);
            assertTrue(result.isEmpty());
            assertEntityServiceSearchCalled(1);
        }

        @Test
        @DisplayName("Should search payments with empty criteria")
        void shouldSearchPaymentsWithEmptyCriteria() {
            PaymentInteractor.PaymentSearchCriteria criteria = new PaymentInteractor.PaymentSearchCriteria();

            mockSearch(List.of());

            List<EntityWithMetadata<Payment>> result = paymentInteractor.advancedSearch(criteria);

            assertNotNull(result);
            assertEntityServiceSearchCalled(1);
        }
    }
}

