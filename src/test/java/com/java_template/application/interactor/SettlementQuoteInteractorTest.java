package com.java_template.application.interactor;

import com.java_template.application.entity.settlement_quote.version_1.SettlementQuote;
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
 * ABOUTME: Unit tests for SettlementQuoteInteractor covering CRUD operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementQuoteInteractor Tests")
class SettlementQuoteInteractorTest extends BaseInteractorTest<SettlementQuote> {

    private SettlementQuoteInteractor settlementQuoteInteractor;

    @BeforeEach
    void setUp() {
        settlementQuoteInteractor = new SettlementQuoteInteractor(entityService);
    }

    @Override
    protected String getEntityName() {
        return SettlementQuote.ENTITY_NAME;
    }

    @Override
    protected Integer getEntityVersion() {
        return SettlementQuote.ENTITY_VERSION;
    }

    @Override
    protected String getBusinessIdField() {
        return "settlementQuoteId";
    }

    @Override
    protected SettlementQuote createValidEntity(String businessId) {
        SettlementQuote quote = new SettlementQuote();
        quote.setSettlementQuoteId(businessId);
        quote.setLoanId("LOAN-001");
        quote.setAsOfDate(LocalDate.now());
        quote.setExpiryDate(LocalDate.now().plusDays(7));
        quote.setTotalPayoffAmount(new BigDecimal("95000.00"));
        quote.setOutstandingPrincipal(new BigDecimal("90000.00"));
        quote.setAccruedInterest(new BigDecimal("5000.00"));
        return quote;
    }

    @Override
    protected String getBusinessId(SettlementQuote entity) {
        return entity.getSettlementQuoteId();
    }

    @Override
    protected void setBusinessId(SettlementQuote entity, String businessId) {
        entity.setSettlementQuoteId(businessId);
    }

    @Override
    protected void assertEntityEquals(SettlementQuote expected, SettlementQuote actual) {
        assertEquals(expected.getSettlementQuoteId(), actual.getSettlementQuoteId());
        assertEquals(expected.getLoanId(), actual.getLoanId());
        assertEquals(expected.getAsOfDate(), actual.getAsOfDate());
        assertEquals(expected.getTotalPayoffAmount(), actual.getTotalPayoffAmount());
    }

    @Nested
    @DisplayName("Create SettlementQuote Tests")
    class CreateSettlementQuoteTests {

        @Test
        @DisplayName("Should create settlement quote successfully with valid data")
        void shouldCreateSettlementQuoteSuccessfully() {
            SettlementQuote quote = createValidEntity("QUOTE-001");
            EntityWithMetadata<SettlementQuote> expected = createEntityWithMetadata(quote, testEntityId);

            mockFindByBusinessIdOrNullNotFound("QUOTE-001");
            mockCreate(quote, expected);

            EntityWithMetadata<SettlementQuote> result = settlementQuoteInteractor.createSettlementQuote(quote);

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(quote.getQuotedDate());
            assertEquals(quote.getAsOfDate(), quote.getQuotedDate());
            assertNotNull(quote.getCreatedAt());
            assertNotNull(quote.getUpdatedAt());
            assertEntityServiceFindByBusinessIdOrNullCalled("QUOTE-001", 1);
            assertEntityServiceCreateCalled(1);
        }

        @Test
        @DisplayName("Should throw DuplicateEntityException when settlement quote with same settlementQuoteId exists")
        void shouldThrowExceptionWhenDuplicateSettlementQuoteId() {
            testCreateDuplicate(
                    settlementQuoteInteractor::createSettlementQuote,
                    SettlementQuoteInteractor.DuplicateEntityException.class
            );
        }

        @Test
        @DisplayName("Should throw exception when settlementQuoteId is null")
        void shouldThrowExceptionWhenSettlementQuoteIdIsNull() {
            SettlementQuote quote = createValidEntity("QUOTE-001");

            // Lombok's @NonNull throws NullPointerException when setting null
            NullPointerException exception = assertThrows(
                    NullPointerException.class,
                    () -> quote.setSettlementQuoteId(null)
            );

            assertTrue(exception.getMessage().contains("settlementQuoteId"));
        }

        @Test
        @DisplayName("Should throw exception when settlementQuoteId is empty")
        void shouldThrowExceptionWhenSettlementQuoteIdIsEmpty() {
            SettlementQuote quote = createValidEntity("QUOTE-001");
            quote.setSettlementQuoteId("  ");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> settlementQuoteInteractor.createSettlementQuote(quote)
            );

            assertEquals("settlementQuoteId cannot be empty", exception.getMessage());
            assertEntityServiceCreateCalled(0);
        }
    }

    @Nested
    @DisplayName("Get SettlementQuote Tests")
    class GetSettlementQuoteTests {

        @Test
        @DisplayName("Should get settlement quote by technical ID successfully")
        void shouldGetSettlementQuoteByIdSuccessfully() {
            SettlementQuote quote = createValidEntity("QUOTE-001");
            EntityWithMetadata<SettlementQuote> expected = createEntityWithMetadata(quote, testEntityId);
            
            mockGetById(testEntityId, expected);

            EntityWithMetadata<SettlementQuote> result = settlementQuoteInteractor.getSettlementQuoteById(testEntityId);

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertEntityEquals(quote, result.entity());
            assertEntityServiceGetByIdCalled(testEntityId, 1);
        }

        @Test
        @DisplayName("Should get settlement quote by business ID successfully")
        void shouldGetSettlementQuoteByBusinessIdSuccessfully() {
            testGetByBusinessIdSuccess(settlementQuoteInteractor::getSettlementQuoteByBusinessId);
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when settlement quote not found by business ID")
        void shouldThrowExceptionWhenSettlementQuoteNotFoundByBusinessId() {
            testGetByBusinessIdNotFound(
                    settlementQuoteInteractor::getSettlementQuoteByBusinessId,
                    SettlementQuoteInteractor.EntityNotFoundException.class
            );
        }
    }

    @Nested
    @DisplayName("Update SettlementQuote Tests")
    class UpdateSettlementQuoteTests {

        @Test
        @DisplayName("Should update settlement quote by technical ID successfully")
        void shouldUpdateSettlementQuoteByIdSuccessfully() {
            SettlementQuote quote = createValidEntity("QUOTE-001");
            quote.setTotalPayoffAmount(new BigDecimal("96000.00"));
            EntityWithMetadata<SettlementQuote> expected = createEntityWithMetadata(quote, testEntityId);

            mockUpdate(testEntityId, expected);

            EntityWithMetadata<SettlementQuote> result = settlementQuoteInteractor.updateSettlementQuoteById(testEntityId, quote, "UPDATE");

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(quote.getUpdatedAt());
            assertEntityServiceUpdateCalled(testEntityId, 1);
        }

        @Test
        @DisplayName("Should update settlement quote by business ID successfully")
        void shouldUpdateSettlementQuoteByBusinessIdSuccessfully() {
            SettlementQuote quote = createValidEntity("QUOTE-001");
            quote.setTotalPayoffAmount(new BigDecimal("96000.00"));
            EntityWithMetadata<SettlementQuote> expected = createEntityWithMetadata(quote, testEntityId);

            mockUpdateByBusinessId(expected);

            EntityWithMetadata<SettlementQuote> result = settlementQuoteInteractor.updateSettlementQuoteByBusinessId("QUOTE-001", quote, "UPDATE");

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(quote.getUpdatedAt());
            assertEntityServiceUpdateByBusinessIdCalled(1);
        }
    }

    @Nested
    @DisplayName("Get All SettlementQuotes Tests")
    class GetAllSettlementQuotesTests {

        @Test
        @DisplayName("Should get all settlement quotes successfully")
        void shouldGetAllSettlementQuotesSuccessfully() {
            SettlementQuote quote1 = createValidEntity("QUOTE-001");
            SettlementQuote quote2 = createValidEntity("QUOTE-002");
            List<EntityWithMetadata<SettlementQuote>> expected = List.of(
                    createEntityWithMetadata(quote1, testEntityId),
                    createEntityWithMetadata(quote2, testEntityId2)
            );
            
            mockFindAll(expected);

            List<EntityWithMetadata<SettlementQuote>> result = settlementQuoteInteractor.getAllSettlementQuotes();

            assertNotNull(result);
            assertEquals(2, result.size());
            assertEntityServiceFindAllCalled(1);
        }

        @Test
        @DisplayName("Should return empty list when no settlement quotes exist")
        void shouldReturnEmptyListWhenNoSettlementQuotes() {
            mockFindAll(List.of());

            List<EntityWithMetadata<SettlementQuote>> result = settlementQuoteInteractor.getAllSettlementQuotes();

            assertNotNull(result);
            assertTrue(result.isEmpty());
            assertEntityServiceFindAllCalled(1);
        }
    }
}

