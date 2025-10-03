package com.java_template.application.interactor;

import com.java_template.application.entity.accrual.version_1.Accrual;
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
 * ABOUTME: Unit tests for AccrualInteractor covering CRUD operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccrualInteractor Tests")
class AccrualInteractorTest extends BaseInteractorTest<Accrual> {

    private AccrualInteractor accrualInteractor;

    @BeforeEach
    void setUp() {
        accrualInteractor = new AccrualInteractor(entityService);
    }

    @Override
    protected String getEntityName() {
        return Accrual.ENTITY_NAME;
    }

    @Override
    protected Integer getEntityVersion() {
        return Accrual.ENTITY_VERSION;
    }

    @Override
    protected String getBusinessIdField() {
        return "accrualId";
    }

    @Override
    protected Accrual createValidEntity(String businessId) {
        Accrual accrual = new Accrual();
        accrual.setAccrualId(businessId);
        accrual.setLoanId("LOAN-001");
        accrual.setAccrualDate(LocalDate.now());
        accrual.setInterestAmount(new BigDecimal("50.00"));
        accrual.setPrincipalBase(new BigDecimal("100000.00"));
        accrual.setApr(new BigDecimal("5.5"));
        accrual.setDayCountBasis("ACT/365F");
        return accrual;
    }

    @Override
    protected String getBusinessId(Accrual entity) {
        return entity.getAccrualId();
    }

    @Override
    protected void setBusinessId(Accrual entity, String businessId) {
        entity.setAccrualId(businessId);
    }

    @Override
    protected void assertEntityEquals(Accrual expected, Accrual actual) {
        assertEquals(expected.getAccrualId(), actual.getAccrualId());
        assertEquals(expected.getLoanId(), actual.getLoanId());
        assertEquals(expected.getAccrualDate(), actual.getAccrualDate());
        assertEquals(expected.getInterestAmount(), actual.getInterestAmount());
    }

    @Nested
    @DisplayName("Create Accrual Tests")
    class CreateAccrualTests {

        @Test
        @DisplayName("Should create accrual successfully with valid data")
        void shouldCreateAccrualSuccessfully() {
            Accrual accrual = createValidEntity("ACCR-001");
            EntityWithMetadata<Accrual> expected = createEntityWithMetadata(accrual, testEntityId);

            mockFindByBusinessIdOrNullNotFound("ACCR-001");
            mockCreate(accrual, expected);

            EntityWithMetadata<Accrual> result = accrualInteractor.createAccrual(accrual);

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(accrual.getScheduledAt());
            assertNotNull(accrual.getCreatedAt());
            assertNotNull(accrual.getUpdatedAt());
            assertEntityServiceFindByBusinessIdOrNullCalled("ACCR-001", 1);
            assertEntityServiceCreateCalled(1);
        }

        @Test
        @DisplayName("Should throw DuplicateEntityException when accrual with same accrualId exists")
        void shouldThrowExceptionWhenDuplicateAccrualId() {
            testCreateDuplicate(
                    accrualInteractor::createAccrual,
                    AccrualInteractor.DuplicateEntityException.class
            );
        }

        @Test
        @DisplayName("Should throw exception when accrualId is null")
        void shouldThrowExceptionWhenAccrualIdIsNull() {
            Accrual accrual = createValidEntity("ACC-001");

            // Lombok's @NonNull throws NullPointerException when setting null
            NullPointerException exception = assertThrows(
                    NullPointerException.class,
                    () -> accrual.setAccrualId(null)
            );

            assertTrue(exception.getMessage().contains("accrualId"));
        }

        @Test
        @DisplayName("Should throw exception when accrualId is empty")
        void shouldThrowExceptionWhenAccrualIdIsEmpty() {
            Accrual accrual = createValidEntity("ACC-001");
            accrual.setAccrualId("  ");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> accrualInteractor.createAccrual(accrual)
            );

            assertEquals("accrualId cannot be empty", exception.getMessage());
            assertEntityServiceCreateCalled(0);
        }
    }

    @Nested
    @DisplayName("Get Accrual Tests")
    class GetAccrualTests {

        @Test
        @DisplayName("Should get accrual by technical ID successfully")
        void shouldGetAccrualByIdSuccessfully() {
            Accrual accrual = createValidEntity("ACCR-001");
            EntityWithMetadata<Accrual> expected = createEntityWithMetadata(accrual, testEntityId);
            
            mockGetById(testEntityId, expected);

            EntityWithMetadata<Accrual> result = accrualInteractor.getAccrualById(testEntityId);

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertEntityEquals(accrual, result.entity());
            assertEntityServiceGetByIdCalled(testEntityId, 1);
        }

        @Test
        @DisplayName("Should get accrual by business ID successfully")
        void shouldGetAccrualByBusinessIdSuccessfully() {
            testGetByBusinessIdSuccess(accrualInteractor::getAccrualByBusinessId);
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when accrual not found by business ID")
        void shouldThrowExceptionWhenAccrualNotFoundByBusinessId() {
            testGetByBusinessIdNotFound(
                    accrualInteractor::getAccrualByBusinessId,
                    AccrualInteractor.EntityNotFoundException.class
            );
        }
    }

    @Nested
    @DisplayName("Update Accrual Tests")
    class UpdateAccrualTests {

        @Test
        @DisplayName("Should update accrual by technical ID successfully")
        void shouldUpdateAccrualByIdSuccessfully() {
            Accrual accrual = createValidEntity("ACCR-001");
            accrual.setInterestAmount(new BigDecimal("75.00"));
            EntityWithMetadata<Accrual> expected = createEntityWithMetadata(accrual, testEntityId);
            
            mockUpdate(testEntityId, expected);

            EntityWithMetadata<Accrual> result = accrualInteractor.updateAccrualById(testEntityId, accrual, "UPDATE");

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(accrual.getUpdatedAt());
            assertEntityServiceUpdateCalled(testEntityId, 1);
        }

        @Test
        @DisplayName("Should update accrual by business ID successfully")
        void shouldUpdateAccrualByBusinessIdSuccessfully() {
            Accrual accrual = createValidEntity("ACCR-001");
            accrual.setInterestAmount(new BigDecimal("75.00"));
            EntityWithMetadata<Accrual> expected = createEntityWithMetadata(accrual, testEntityId);
            
            mockUpdateByBusinessId(expected);

            EntityWithMetadata<Accrual> result = accrualInteractor.updateAccrualByBusinessId("ACCR-001", accrual, "UPDATE");

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(accrual.getUpdatedAt());
            assertEntityServiceUpdateByBusinessIdCalled(1);
        }
    }

    @Nested
    @DisplayName("Get All Accruals Tests")
    class GetAllAccrualsTests {

        @Test
        @DisplayName("Should get all accruals successfully")
        void shouldGetAllAccrualsSuccessfully() {
            Accrual accrual1 = createValidEntity("ACCR-001");
            Accrual accrual2 = createValidEntity("ACCR-002");
            List<EntityWithMetadata<Accrual>> expected = List.of(
                    createEntityWithMetadata(accrual1, testEntityId),
                    createEntityWithMetadata(accrual2, testEntityId2)
            );
            
            mockFindAll(expected);

            List<EntityWithMetadata<Accrual>> result = accrualInteractor.getAllAccruals();

            assertNotNull(result);
            assertEquals(2, result.size());
            assertEntityServiceFindAllCalled(1);
        }

        @Test
        @DisplayName("Should return empty list when no accruals exist")
        void shouldReturnEmptyListWhenNoAccruals() {
            mockFindAll(List.of());

            List<EntityWithMetadata<Accrual>> result = accrualInteractor.getAllAccruals();

            assertNotNull(result);
            assertTrue(result.isEmpty());
            assertEntityServiceFindAllCalled(1);
        }
    }
}

