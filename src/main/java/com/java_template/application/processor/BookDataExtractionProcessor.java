package com.java_template.application.processor;

import com.java_template.application.entity.book.version_1.Book;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class BookDataExtractionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BookDataExtractionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final RestTemplate restTemplate;
    private final EntityService entityService;

    public BookDataExtractionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Book data extraction for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Book.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Book entity) {
        return entity != null;
    }

    private Book processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Book> context) {
        Book book = context.entity();
        
        try {
            if (book.getBookId() == null) {
                // Bulk extraction scenario - get all books from API
                extractAllBooks();
            } else {
                // Single book extraction
                extractSingleBook(book);
            }
        } catch (Exception e) {
            logger.error("Failed to extract book data: {}", e.getMessage(), e);
            throw new RuntimeException("Book data extraction failed: " + e.getMessage(), e);
        }

        return book;
    }

    private void extractAllBooks() {
        try {
            String apiUrl = "https://fakerestapi.azurewebsites.net/api/v1/Books";
            logger.info("Fetching all books from API: {}", apiUrl);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> apiBooks = restTemplate.getForObject(apiUrl, List.class);
            
            if (apiBooks != null) {
                for (Map<String, Object> apiBook : apiBooks) {
                    Book newBook = new Book();
                    populateBookFromApiData(newBook, apiBook);
                    entityService.save(newBook);
                    logger.info("Saved book: {} (ID: {})", newBook.getTitle(), newBook.getBookId());
                }
                logger.info("Successfully extracted {} books from API", apiBooks.size());
            }
        } catch (RestClientException e) {
            logger.error("Failed to fetch books from API: {}", e.getMessage(), e);
            throw new RuntimeException("API call failed: " + e.getMessage(), e);
        }
    }

    private void extractSingleBook(Book book) {
        try {
            String apiUrl = "https://fakerestapi.azurewebsites.net/api/v1/Books/" + book.getBookId();
            logger.info("Fetching book from API: {}", apiUrl);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> apiBook = restTemplate.getForObject(apiUrl, Map.class);
            
            if (apiBook != null) {
                populateBookFromApiData(book, apiBook);
                logger.info("Successfully extracted book data for ID: {}", book.getBookId());
            } else {
                throw new RuntimeException("No data returned from API for book ID: " + book.getBookId());
            }
        } catch (RestClientException e) {
            logger.error("Failed to fetch book {} from API: {}", book.getBookId(), e.getMessage(), e);
            throw new RuntimeException("API call failed for book " + book.getBookId() + ": " + e.getMessage(), e);
        }
    }

    private void populateBookFromApiData(Book book, Map<String, Object> apiBook) {
        book.setBookId(((Number) apiBook.get("id")).longValue());
        book.setTitle((String) apiBook.get("title"));
        book.setDescription((String) apiBook.get("description"));
        book.setPageCount((Integer) apiBook.get("pageCount"));
        book.setExcerpt((String) apiBook.get("excerpt"));
        
        // Parse publish date
        String publishDateStr = (String) apiBook.get("publishDate");
        if (publishDateStr != null) {
            try {
                book.setPublishDate(LocalDateTime.parse(publishDateStr, DateTimeFormatter.ISO_DATE_TIME));
            } catch (Exception e) {
                logger.warn("Failed to parse publish date: {}", publishDateStr);
                book.setPublishDate(LocalDateTime.now().minusYears(1)); // Default fallback
            }
        }
        
        book.setRetrievedAt(LocalDateTime.now());
        book.setAnalysisScore(null); // Will be set during analysis
        book.setReportId(null); // Will be set when included in report
    }
}
