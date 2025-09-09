package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order_entity.version_1.OrderEntity;
import com.java_template.application.entity.pet_entity.version_1.PetEntity;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * OrderImportProcessor - Import order data from Pet Store API
 * Transition: import_order (none → imported)
 */
@Component
public class OrderImportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderImportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final Random random = new Random();

    public OrderImportProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing OrderImport for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(OrderEntity.class)
                .validate(this::isValidEntityWithMetadata, "Invalid entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<OrderEntity> entityWithMetadata) {
        return entityWithMetadata != null && entityWithMetadata.metadata() != null && 
               entityWithMetadata.metadata().getId() != null;
    }

    private EntityWithMetadata<OrderEntity> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<OrderEntity> context) {

        EntityWithMetadata<OrderEntity> entityWithMetadata = context.entityResponse();
        OrderEntity entity = entityWithMetadata.entity();

        logger.debug("Importing order data from Pet Store API simulation");

        // Import order data from Pet Store API simulation
        importOrderDataFromAPI(entity);

        logger.info("Order import completed for entity: {}", entity.getOrderId());

        return entityWithMetadata;
    }

    /**
     * Simulate importing order data from Pet Store API
     * In real implementation, this would make HTTP calls to Pet Store API
     */
    private void importOrderDataFromAPI(OrderEntity entity) {
        // Simulate API data - in real implementation, this would be actual API calls
        Long orderId = random.nextLong(10000) + 1000;
        entity.setOrderId(orderId);

        // Get a random pet to associate with this order
        Long petId = getRandomPetId();
        entity.setPetId(petId);

        // Generate order details
        int quantity = random.nextInt(5) + 1;
        entity.setQuantity(quantity);

        // Get pet price for unit price calculation
        Double unitPrice = getPetPrice(petId);
        entity.setUnitPrice(unitPrice);
        entity.setTotalAmount(quantity * unitPrice);

        // Set dates
        LocalDateTime orderDate = LocalDateTime.now().minusDays(random.nextInt(30));
        entity.setOrderDate(orderDate);
        entity.setShipDate(orderDate.plusDays(random.nextInt(7) + 1));

        // Generate mock customer data
        entity.setCustomerInfo(generateMockCustomerData());

        // Set completion status
        entity.setComplete(random.nextBoolean());

        logger.debug("Order data imported: orderId={}, petId={}, quantity={}, total={}", 
                orderId, petId, quantity, entity.getTotalAmount());
    }

    /**
     * Get a random pet ID from existing pets
     */
    private Long getRandomPetId() {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(PetEntity.ENTITY_NAME)
                    .withVersion(PetEntity.ENTITY_VERSION);

            List<EntityWithMetadata<PetEntity>> pets = entityService.search(modelSpec, null, PetEntity.class);
            
            if (!pets.isEmpty()) {
                int randomIndex = random.nextInt(pets.size());
                return pets.get(randomIndex).entity().getPetId();
            }
        } catch (Exception e) {
            logger.warn("Failed to retrieve pets for order association: {}", e.getMessage());
        }
        
        // Fallback to random pet ID if no pets found
        return random.nextLong(100) + 1L;
    }

    /**
     * Get pet price for unit price calculation
     */
    private Double getPetPrice(Long petId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(PetEntity.ENTITY_NAME)
                    .withVersion(PetEntity.ENTITY_VERSION);

            ObjectMapper objectMapper = new ObjectMapper();
            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.petId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(petId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<PetEntity>> pets = entityService.search(modelSpec, condition, PetEntity.class);
            
            if (!pets.isEmpty()) {
                PetEntity pet = pets.get(0).entity();
                return pet.getPrice() != null ? pet.getPrice() : 100.0;
            }
        } catch (Exception e) {
            logger.warn("Failed to retrieve pet price for petId {}: {}", petId, e.getMessage());
        }
        
        // Fallback price
        return 100.0 + random.nextDouble() * 200;
    }

    /**
     * Generate mock customer data
     */
    private OrderEntity.CustomerInfo generateMockCustomerData() {
        OrderEntity.CustomerInfo customerInfo = new OrderEntity.CustomerInfo();
        
        String[] names = {"John Doe", "Jane Smith", "Bob Johnson", "Alice Brown", "Charlie Wilson"};
        String[] domains = {"example.com", "test.com", "demo.com"};
        
        String name = names[random.nextInt(names.length)];
        customerInfo.setName(name);
        
        String email = name.toLowerCase().replace(" ", ".") + "@" + domains[random.nextInt(domains.length)];
        customerInfo.setEmail(email);
        
        customerInfo.setPhone("+1-555-" + String.format("%04d", random.nextInt(10000)));
        
        return customerInfo;
    }
}
