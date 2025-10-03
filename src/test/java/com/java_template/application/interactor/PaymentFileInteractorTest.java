package com.java_template.application.interactor;

import com.java_template.application.entity.payment_file.version_1.PaymentFile;
import com.java_template.common.dto.EntityWithMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ABOUTME: Unit tests for PaymentFileInteractor covering CRUD operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentFileInteractor Tests")
class PaymentFileInteractorTest extends BaseInteractorTest<PaymentFile> {

    private PaymentFileInteractor paymentFileInteractor;

    @BeforeEach
    void setUp() {
        paymentFileInteractor = new PaymentFileInteractor(entityService);
    }

    @Override
    protected String getEntityName() {
        return PaymentFile.ENTITY_NAME;
    }

    @Override
    protected Integer getEntityVersion() {
        return PaymentFile.ENTITY_VERSION;
    }

    @Override
    protected String getBusinessIdField() {
        return "paymentFileId";
    }

    @Override
    protected PaymentFile createValidEntity(String businessId) {
        PaymentFile paymentFile = new PaymentFile();
        paymentFile.setPaymentFileId(businessId);
        paymentFile.setFileName("payments_20240101.csv");
        paymentFile.setFileType("CSV");
        paymentFile.setTotalRecords(100);
        return paymentFile;
    }

    @Override
    protected String getBusinessId(PaymentFile entity) {
        return entity.getPaymentFileId();
    }

    @Override
    protected void setBusinessId(PaymentFile entity, String businessId) {
        entity.setPaymentFileId(businessId);
    }

    @Override
    protected void assertEntityEquals(PaymentFile expected, PaymentFile actual) {
        assertEquals(expected.getPaymentFileId(), actual.getPaymentFileId());
        assertEquals(expected.getFileName(), actual.getFileName());
        assertEquals(expected.getFileType(), actual.getFileType());
    }

    @Nested
    @DisplayName("Create PaymentFile Tests")
    class CreatePaymentFileTests {

        @Test
        @DisplayName("Should create payment file successfully with valid data")
        void shouldCreatePaymentFileSuccessfully() {
            PaymentFile paymentFile = createValidEntity("FILE-001");
            EntityWithMetadata<PaymentFile> expected = createEntityWithMetadata(paymentFile, testEntityId);

            mockFindByBusinessIdOrNullNotFound("FILE-001");
            mockCreate(paymentFile, expected);

            EntityWithMetadata<PaymentFile> result = paymentFileInteractor.createPaymentFile(paymentFile);

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(paymentFile.getReceivedAt());
            assertNotNull(paymentFile.getCreatedAt());
            assertNotNull(paymentFile.getUpdatedAt());
            assertEntityServiceFindByBusinessIdOrNullCalled("FILE-001", 1);
            assertEntityServiceCreateCalled(1);
        }

        @Test
        @DisplayName("Should throw DuplicateEntityException when payment file with same paymentFileId exists")
        void shouldThrowExceptionWhenDuplicatePaymentFileId() {
            testCreateDuplicate(
                    paymentFileInteractor::createPaymentFile,
                    PaymentFileInteractor.DuplicateEntityException.class
            );
        }

        @Test
        @DisplayName("Should throw exception when paymentFileId is null")
        void shouldThrowExceptionWhenPaymentFileIdIsNull() {
            PaymentFile paymentFile = createValidEntity("FILE-001");

            // Lombok's @NonNull throws NullPointerException when setting null
            NullPointerException exception = assertThrows(
                    NullPointerException.class,
                    () -> paymentFile.setPaymentFileId(null)
            );

            assertTrue(exception.getMessage().contains("paymentFileId"));
        }

        @Test
        @DisplayName("Should throw exception when paymentFileId is empty")
        void shouldThrowExceptionWhenPaymentFileIdIsEmpty() {
            PaymentFile paymentFile = createValidEntity("FILE-001");
            paymentFile.setPaymentFileId("  ");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> paymentFileInteractor.createPaymentFile(paymentFile)
            );

            assertEquals("paymentFileId cannot be empty", exception.getMessage());
            assertEntityServiceCreateCalled(0);
        }
    }

    @Nested
    @DisplayName("Get PaymentFile Tests")
    class GetPaymentFileTests {

        @Test
        @DisplayName("Should get payment file by technical ID successfully")
        void shouldGetPaymentFileByIdSuccessfully() {
            PaymentFile paymentFile = createValidEntity("FILE-001");
            EntityWithMetadata<PaymentFile> expected = createEntityWithMetadata(paymentFile, testEntityId);

            mockGetById(testEntityId, expected);

            EntityWithMetadata<PaymentFile> result = paymentFileInteractor.getPaymentFileById(testEntityId);

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertEntityEquals(paymentFile, result.entity());
            assertEntityServiceGetByIdCalled(testEntityId, 1);
        }

        @Test
        @DisplayName("Should get payment file by business ID successfully")
        void shouldGetPaymentFileByBusinessIdSuccessfully() {
            testGetByBusinessIdSuccess(paymentFileInteractor::getPaymentFileByBusinessId);
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when payment file not found by business ID")
        void shouldThrowExceptionWhenPaymentFileNotFoundByBusinessId() {
            testGetByBusinessIdNotFound(
                    paymentFileInteractor::getPaymentFileByBusinessId,
                    PaymentFileInteractor.EntityNotFoundException.class
            );
        }
    }

    @Nested
    @DisplayName("Update PaymentFile Tests")
    class UpdatePaymentFileTests {

        @Test
        @DisplayName("Should update payment file by technical ID successfully")
        void shouldUpdatePaymentFileByIdSuccessfully() {
            PaymentFile paymentFile = createValidEntity("FILE-001");
            paymentFile.setTotalRecords(150);
            EntityWithMetadata<PaymentFile> expected = createEntityWithMetadata(paymentFile, testEntityId);

            mockUpdate(testEntityId, expected);

            EntityWithMetadata<PaymentFile> result = paymentFileInteractor.updatePaymentFileById(testEntityId, paymentFile, "UPDATE");

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(paymentFile.getUpdatedAt());
            assertEntityServiceUpdateCalled(testEntityId, 1);
        }

        @Test
        @DisplayName("Should update payment file by business ID successfully")
        void shouldUpdatePaymentFileByBusinessIdSuccessfully() {
            PaymentFile paymentFile = createValidEntity("FILE-001");
            paymentFile.setTotalRecords(150);
            EntityWithMetadata<PaymentFile> expected = createEntityWithMetadata(paymentFile, testEntityId);

            mockUpdateByBusinessId(expected);

            EntityWithMetadata<PaymentFile> result = paymentFileInteractor.updatePaymentFileByBusinessId("FILE-001", paymentFile, "UPDATE");

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(paymentFile.getUpdatedAt());
            assertEntityServiceUpdateByBusinessIdCalled(1);
        }
    }

    @Nested
    @DisplayName("Get All PaymentFiles Tests")
    class GetAllPaymentFilesTests {

        @Test
        @DisplayName("Should get all payment files successfully")
        void shouldGetAllPaymentFilesSuccessfully() {
            PaymentFile paymentFile1 = createValidEntity("FILE-001");
            PaymentFile paymentFile2 = createValidEntity("FILE-002");
            List<EntityWithMetadata<PaymentFile>> expected = List.of(
                    createEntityWithMetadata(paymentFile1, testEntityId),
                    createEntityWithMetadata(paymentFile2, testEntityId2)
            );

            mockFindAll(expected);

            List<EntityWithMetadata<PaymentFile>> result = paymentFileInteractor.getAllPaymentFiles();

            assertNotNull(result);
            assertEquals(2, result.size());
            assertEntityServiceFindAllCalled(1);
        }

        @Test
        @DisplayName("Should return empty list when no payment files exist")
        void shouldReturnEmptyListWhenNoPaymentFiles() {
            mockFindAll(List.of());

            List<EntityWithMetadata<PaymentFile>> result = paymentFileInteractor.getAllPaymentFiles();

            assertNotNull(result);
            assertTrue(result.isEmpty());
            assertEntityServiceFindAllCalled(1);
        }
    }
}

