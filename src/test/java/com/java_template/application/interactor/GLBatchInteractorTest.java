package com.java_template.application.interactor;

import com.java_template.application.entity.gl_batch.version_1.GLBatch;
import com.java_template.common.dto.EntityWithMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ABOUTME: Unit tests for GLBatchInteractor covering CRUD operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GLBatchInteractor Tests")
class GLBatchInteractorTest extends BaseInteractorTest<GLBatch> {

    private GLBatchInteractor glBatchInteractor;

    @BeforeEach
    void setUp() {
        glBatchInteractor = new GLBatchInteractor(entityService);
    }

    @Override
    protected String getEntityName() {
        return GLBatch.ENTITY_NAME;
    }

    @Override
    protected Integer getEntityVersion() {
        return GLBatch.ENTITY_VERSION;
    }

    @Override
    protected String getBusinessIdField() {
        return "glBatchId";
    }

    @Override
    protected GLBatch createValidEntity(String businessId) {
        GLBatch glBatch = new GLBatch();
        glBatch.setGlBatchId(businessId);
        glBatch.setPeriodStart(LocalDate.now().withDayOfMonth(1));
        glBatch.setPeriodEnd(LocalDate.now());
        glBatch.setPeriodCode("2024-01");
        return glBatch;
    }

    @Override
    protected String getBusinessId(GLBatch entity) {
        return entity.getGlBatchId();
    }

    @Override
    protected void setBusinessId(GLBatch entity, String businessId) {
        entity.setGlBatchId(businessId);
    }

    @Override
    protected void assertEntityEquals(GLBatch expected, GLBatch actual) {
        assertEquals(expected.getGlBatchId(), actual.getGlBatchId());
        assertEquals(expected.getPeriodStart(), actual.getPeriodStart());
        assertEquals(expected.getPeriodEnd(), actual.getPeriodEnd());
        assertEquals(expected.getPeriodCode(), actual.getPeriodCode());
    }

    @Nested
    @DisplayName("Create GLBatch Tests")
    class CreateGLBatchTests {

        @Test
        @DisplayName("Should create GL batch successfully with valid data")
        void shouldCreateGLBatchSuccessfully() {
            GLBatch glBatch = createValidEntity("BATCH-001");
            EntityWithMetadata<GLBatch> expected = createEntityWithMetadata(glBatch, testEntityId);

            mockFindByBusinessIdOrNullNotFound("BATCH-001");
            mockCreate(glBatch, expected);

            EntityWithMetadata<GLBatch> result = glBatchInteractor.createGLBatch(glBatch);

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(glBatch.getCreatedAt());
            assertNotNull(glBatch.getUpdatedAt());
            assertEntityServiceFindByBusinessIdOrNullCalled("BATCH-001", 1);
            assertEntityServiceCreateCalled(1);
        }

        @Test
        @DisplayName("Should throw DuplicateEntityException when GL batch with same glBatchId exists")
        void shouldThrowExceptionWhenDuplicateGLBatchId() {
            testCreateDuplicate(
                    glBatchInteractor::createGLBatch,
                    GLBatchInteractor.DuplicateEntityException.class
            );
        }

        @Test
        @DisplayName("Should throw exception when glBatchId is null")
        void shouldThrowExceptionWhenGLBatchIdIsNull() {
            GLBatch glBatch = createValidEntity("BATCH-001");

            // Lombok's @NonNull throws NullPointerException when setting null
            NullPointerException exception = assertThrows(
                    NullPointerException.class,
                    () -> glBatch.setGlBatchId(null)
            );

            assertTrue(exception.getMessage().contains("glBatchId"));
        }

        @Test
        @DisplayName("Should throw exception when glBatchId is empty")
        void shouldThrowExceptionWhenGLBatchIdIsEmpty() {
            GLBatch glBatch = createValidEntity("BATCH-001");
            glBatch.setGlBatchId("  ");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> glBatchInteractor.createGLBatch(glBatch)
            );

            assertEquals("glBatchId cannot be empty", exception.getMessage());
            assertEntityServiceCreateCalled(0);
        }
    }

    @Nested
    @DisplayName("Get GLBatch Tests")
    class GetGLBatchTests {

        @Test
        @DisplayName("Should get GL batch by technical ID successfully")
        void shouldGetGLBatchByIdSuccessfully() {
            GLBatch glBatch = createValidEntity("BATCH-001");
            EntityWithMetadata<GLBatch> expected = createEntityWithMetadata(glBatch, testEntityId);
            
            mockGetById(testEntityId, expected);

            EntityWithMetadata<GLBatch> result = glBatchInteractor.getGLBatchById(testEntityId);

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertEntityEquals(glBatch, result.entity());
            assertEntityServiceGetByIdCalled(testEntityId, 1);
        }

        @Test
        @DisplayName("Should get GL batch by business ID successfully")
        void shouldGetGLBatchByBusinessIdSuccessfully() {
            testGetByBusinessIdSuccess(glBatchInteractor::getGLBatchByBusinessId);
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when GL batch not found by business ID")
        void shouldThrowExceptionWhenGLBatchNotFoundByBusinessId() {
            testGetByBusinessIdNotFound(
                    glBatchInteractor::getGLBatchByBusinessId,
                    GLBatchInteractor.EntityNotFoundException.class
            );
        }
    }

    @Nested
    @DisplayName("Update GLBatch Tests")
    class UpdateGLBatchTests {

        @Test
        @DisplayName("Should update GL batch by technical ID successfully")
        void shouldUpdateGLBatchByIdSuccessfully() {
            GLBatch glBatch = createValidEntity("BATCH-001");
            glBatch.setExportFileName("export_2024_01.csv");
            EntityWithMetadata<GLBatch> expected = createEntityWithMetadata(glBatch, testEntityId);

            mockUpdate(testEntityId, expected);

            EntityWithMetadata<GLBatch> result = glBatchInteractor.updateGLBatchById(testEntityId, glBatch, "UPDATE");

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(glBatch.getUpdatedAt());
            assertEntityServiceUpdateCalled(testEntityId, 1);
        }

        @Test
        @DisplayName("Should update GL batch by business ID successfully")
        void shouldUpdateGLBatchByBusinessIdSuccessfully() {
            GLBatch glBatch = createValidEntity("BATCH-001");
            glBatch.setExportFileName("export_2024_01.csv");
            EntityWithMetadata<GLBatch> expected = createEntityWithMetadata(glBatch, testEntityId);

            mockUpdateByBusinessId(expected);

            EntityWithMetadata<GLBatch> result = glBatchInteractor.updateGLBatchByBusinessId("BATCH-001", glBatch, "UPDATE");

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(glBatch.getUpdatedAt());
            assertEntityServiceUpdateByBusinessIdCalled(1);
        }
    }

    @Nested
    @DisplayName("Get All GLBatches Tests")
    class GetAllGLBatchesTests {

        @Test
        @DisplayName("Should get all GL batches successfully")
        void shouldGetAllGLBatchesSuccessfully() {
            GLBatch glBatch1 = createValidEntity("BATCH-001");
            GLBatch glBatch2 = createValidEntity("BATCH-002");
            List<EntityWithMetadata<GLBatch>> expected = List.of(
                    createEntityWithMetadata(glBatch1, testEntityId),
                    createEntityWithMetadata(glBatch2, testEntityId2)
            );
            
            mockFindAll(expected);

            List<EntityWithMetadata<GLBatch>> result = glBatchInteractor.getAllGLBatches();

            assertNotNull(result);
            assertEquals(2, result.size());
            assertEntityServiceFindAllCalled(1);
        }

        @Test
        @DisplayName("Should return empty list when no GL batches exist")
        void shouldReturnEmptyListWhenNoGLBatches() {
            mockFindAll(List.of());

            List<EntityWithMetadata<GLBatch>> result = glBatchInteractor.getAllGLBatches();

            assertNotNull(result);
            assertTrue(result.isEmpty());
            assertEntityServiceFindAllCalled(1);
        }
    }
}

