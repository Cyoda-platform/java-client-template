package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.book.version_1.Book;
import com.java_template.application.entity.fetchjob.version_1.FetchJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class PersistBooksProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistBooksProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PersistBooksProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FetchJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(FetchJob.class)
                .validate(this::isValidEntity, "Invalid FetchJob state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(FetchJob job) {
        return job != null && job.isValid();
    }

    private FetchJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<FetchJob> context) {
        FetchJob fetchJob = context.entity();
        logger.info("PersistBooksProcessor: starting fetch for job={}", fetchJob.getName());

        RestTemplate rest = new RestTemplate();
        String apiUrl = "https://fakerestapi.azurewebsites.net/api/v1/Books";

        try {
            String response = rest.getForObject(apiUrl, String.class);
            if (response == null) {
                logger.warn("PersistBooksProcessor: no response from external API");
                return fetchJob;
            }
            JsonNode root = objectMapper.readTree(response);
            if (!root.isArray()) {
                logger.warn("PersistBooksProcessor: unexpected response format, expected array");
                return fetchJob;
            }

            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (JsonNode node : root) {
                if (node == null || node.isNull()) continue;
                try {
                    Book book = new Book();
                    if (node.hasNonNull("id")) book.setId(node.get("id").asInt());
                    if (node.hasNonNull("title")) book.setTitle(node.get("title").asText());
                    if (node.hasNonNull("description")) book.setDescription(node.get("description").asText());
                    if (node.hasNonNull("pageCount")) book.setPageCount(node.get("pageCount").asInt());
                    if (node.hasNonNull("excerpt")) book.setExcerpt(node.get("excerpt").asText());
                    if (node.hasNonNull("publishDate")) {
                        try {
                            book.setPublishDate(OffsetDateTime.parse(node.get("publishDate").asText()));
                        } catch (Exception ex) {
                            // fallback: ignore parse error
                            logger.debug("PersistBooksProcessor: publishDate parse failed for id={}", book.getId());
                        }
                    }

                    book.setRetrievedAt(OffsetDateTime.now());
                    // preliminary source status
                    if (book.getTitle() == null || book.getPageCount() == null || book.getPublishDate() == null) {
                        book.setSourceStatus("missing");
                    } else {
                        book.setSourceStatus("ok");
                    }

                    // persist via entity service
                    CompletableFuture<?> fut = entityService.addItem(
                            Book.ENTITY_NAME,
                            String.valueOf(Book.ENTITY_VERSION),
                            book
                    );
                    futures.add(fut);
                } catch (Exception ex) {
                    logger.error("PersistBooksProcessor: failed to process book node", ex);
                }
            }

            // wait for all to complete
            for (CompletableFuture<?> f : futures) {
                try { f.join(); } catch (Exception ex) { logger.warn("PersistBooksProcessor: addItem failed", ex); }
            }

            // update fetch job status to indicate persisted
            fetchJob.setStatus("persisted");

        } catch (Exception ex) {
            logger.error("PersistBooksProcessor: error calling external API", ex);
            fetchJob.setStatus("failed");
        }

        return fetchJob;
    }
}
