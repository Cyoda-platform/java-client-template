package com.java_template.application.interactor;

import com.java_template.application.entity.party.version_1.Party;
import com.java_template.common.dto.EntityWithMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ABOUTME: Unit tests for PartyInteractor covering CRUD operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PartyInteractor Tests")
class PartyInteractorTest extends BaseInteractorTest<Party> {

    private PartyInteractor partyInteractor;

    @BeforeEach
    void setUp() {
        partyInteractor = new PartyInteractor(entityService, objectMapper);
    }

    @Override
    protected String getEntityName() {
        return Party.ENTITY_NAME;
    }

    @Override
    protected Integer getEntityVersion() {
        return Party.ENTITY_VERSION;
    }

    @Override
    protected String getBusinessIdField() {
        return "partyId";
    }

    @Override
    protected Party createValidEntity(String businessId) {
        Party party = new Party();
        party.setPartyId(businessId);
        party.setName("Test Party");
        party.setType("INDIVIDUAL");
        return party;
    }

    @Override
    protected String getBusinessId(Party entity) {
        return entity.getPartyId();
    }

    @Override
    protected void setBusinessId(Party entity, String businessId) {
        entity.setPartyId(businessId);
    }

    @Override
    protected void assertEntityEquals(Party expected, Party actual) {
        assertEquals(expected.getPartyId(), actual.getPartyId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getType(), actual.getType());
    }

    @Nested
    @DisplayName("Create Party Tests")
    class CreatePartyTests {

        @Test
        @DisplayName("Should create party successfully with valid data")
        void shouldCreatePartySuccessfully() {
            Party party = createValidEntity("PARTY-001");
            EntityWithMetadata<Party> expected = createEntityWithMetadata(party, testEntityId);
            
            mockFindByBusinessIdNotFound("PARTY-001");
            mockCreate(party, expected);

            EntityWithMetadata<Party> result = partyInteractor.createParty(party);

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(party.getCreatedAt());
            assertNotNull(party.getUpdatedAt());
            assertEquals("ACTIVE", party.getStatus());
            assertEntityServiceFindByBusinessIdCalled("PARTY-001", 1);
            assertEntityServiceCreateCalled(1);
        }

        @Test
        @DisplayName("Should throw DuplicateEntityException when party with same partyId exists")
        void shouldThrowExceptionWhenDuplicatePartyId() {
            testCreateDuplicate(
                    partyInteractor::createParty,
                    PartyInteractor.DuplicateEntityException.class
            );
        }

        @Test
        @DisplayName("Should throw exception when partyId is null")
        void shouldThrowExceptionWhenPartyIdIsNull() {
            Party party = createValidEntity("PARTY-001");

            // Lombok's @NonNull throws NullPointerException when setting null
            NullPointerException exception = assertThrows(
                    NullPointerException.class,
                    () -> party.setPartyId(null)
            );

            assertTrue(exception.getMessage().contains("partyId"));
        }

        @Test
        @DisplayName("Should throw exception when partyId is empty")
        void shouldThrowExceptionWhenPartyIdIsEmpty() {
            Party party = createValidEntity("PARTY-001");
            party.setPartyId("  ");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> partyInteractor.createParty(party)
            );

            assertEquals("partyId is mandatory and cannot be null or empty", exception.getMessage());
            assertEntityServiceCreateCalled(0);
        }
    }

    @Nested
    @DisplayName("Get Party Tests")
    class GetPartyTests {

        @Test
        @DisplayName("Should get party by technical ID successfully")
        void shouldGetPartyByIdSuccessfully() {
            Party party = createValidEntity("PARTY-001");
            EntityWithMetadata<Party> expected = createEntityWithMetadata(party, testEntityId);
            
            mockGetById(testEntityId, expected);

            EntityWithMetadata<Party> result = partyInteractor.getPartyById(testEntityId);

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertEntityEquals(party, result.entity());
            assertEntityServiceGetByIdCalled(testEntityId, 1);
        }

        @Test
        @DisplayName("Should get party by business ID successfully")
        void shouldGetPartyByBusinessIdSuccessfully() {
            testGetByBusinessIdSuccess(partyInteractor::getPartyByBusinessId);
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when party not found by business ID")
        void shouldThrowExceptionWhenPartyNotFoundByBusinessId() {
            testGetByBusinessIdNotFound(
                    partyInteractor::getPartyByBusinessId,
                    PartyInteractor.EntityNotFoundException.class
            );
        }
    }

    @Nested
    @DisplayName("Update Party Tests")
    class UpdatePartyTests {

        @Test
        @DisplayName("Should update party by technical ID successfully")
        void shouldUpdatePartyByIdSuccessfully() {
            Party party = createValidEntity("PARTY-001");
            party.setName("Updated Party Name");
            EntityWithMetadata<Party> expected = createEntityWithMetadata(party, testEntityId);
            
            mockUpdate(testEntityId, expected);

            EntityWithMetadata<Party> result = partyInteractor.updatePartyById(testEntityId, party, "UPDATE");

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(party.getUpdatedAt());
            assertEntityServiceUpdateCalled(testEntityId, 1);
        }

        @Test
        @DisplayName("Should update party by business ID successfully")
        void shouldUpdatePartyByBusinessIdSuccessfully() {
            Party party = createValidEntity("PARTY-001");
            party.setName("Updated Party Name");
            EntityWithMetadata<Party> expected = createEntityWithMetadata(party, testEntityId);
            
            mockUpdateByBusinessId(expected);

            EntityWithMetadata<Party> result = partyInteractor.updatePartyByBusinessId("PARTY-001", party, "UPDATE");

            assertNotNull(result);
            assertMetadata(result, testEntityId);
            assertNotNull(party.getUpdatedAt());
            assertEntityServiceUpdateByBusinessIdCalled(1);
        }
    }

    @Nested
    @DisplayName("Delete Party Tests")
    class DeletePartyTests {

        @Test
        @DisplayName("Should delete party successfully")
        void shouldDeletePartySuccessfully() {
            mockDeleteById(testEntityId);

            assertDoesNotThrow(() -> partyInteractor.deleteParty(testEntityId));

            assertEntityServiceDeleteByIdCalled(testEntityId, 1);
        }
    }

    @Nested
    @DisplayName("Get All Parties Tests")
    class GetAllPartiesTests {

        @Test
        @DisplayName("Should get all parties successfully")
        void shouldGetAllPartiesSuccessfully() {
            Party party1 = createValidEntity("PARTY-001");
            Party party2 = createValidEntity("PARTY-002");
            List<EntityWithMetadata<Party>> expected = List.of(
                    createEntityWithMetadata(party1, testEntityId),
                    createEntityWithMetadata(party2, testEntityId2)
            );
            
            mockFindAll(expected);

            List<EntityWithMetadata<Party>> result = partyInteractor.getAllParties();

            assertNotNull(result);
            assertEquals(2, result.size());
            assertEntityServiceFindAllCalled(1);
        }

        @Test
        @DisplayName("Should return empty list when no parties exist")
        void shouldReturnEmptyListWhenNoParties() {
            mockFindAll(List.of());

            List<EntityWithMetadata<Party>> result = partyInteractor.getAllParties();

            assertNotNull(result);
            assertTrue(result.isEmpty());
            assertEntityServiceFindAllCalled(1);
        }
    }
}

