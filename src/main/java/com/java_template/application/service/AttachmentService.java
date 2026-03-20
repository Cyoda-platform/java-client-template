package com.java_template.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.dto.AttachmentDTO;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.SearchAndRetrievalParams;
import com.java_template.common.service.EdgeMessageService;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Attachment operations.
 * File content is stored in Cyoda EdgeMessage; metadata is stored in the repository.
 */
@Service
public class AttachmentService {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentService.class);
    private static final String EDGE_MESSAGE_SUBJECT = "attachment";
    private static final ModelSpec MODEL_SPEC =
            new ModelSpec().withName(AttachmentDTO.ENTITY_NAME).withVersion(AttachmentDTO.ENTITY_VERSION);

    private final EntityService entityService;
    private final EdgeMessageService edgeMessageService;
    private final ObjectMapper objectMapper;

    public AttachmentService(EntityService entityService,
                             EdgeMessageService edgeMessageService,
                             ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.edgeMessageService = edgeMessageService;
        this.objectMapper = objectMapper;
    }

    private AttachmentDTO withId(EntityWithMetadata<AttachmentDTO> result) {
        AttachmentDTO entity = result.entity();
        entity.setId(result.getId());
        return entity;
    }

    private PageResult<AttachmentDTO> toPage(PageResult<EntityWithMetadata<AttachmentDTO>> result) {
        return PageResult.of(result.searchId(),
                result.data().stream().map(this::withId).toList(),
                result.pageNumber(), result.pageSize(), result.totalElements());
    }

    private GroupCondition conditionByField(String fieldName, Object value) {
        SimpleCondition condition = new SimpleCondition()
                .withJsonPath("$." + fieldName)
                .withOperation(Operation.EQUALS)
                .withValue(objectMapper.valueToTree(value));
        return new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(List.of(condition));
    }

    /**
     * Uploads a file to Cyoda EdgeMessage and saves attachment metadata to the repository.
     */
    public AttachmentDTO uploadAttachment(UUID projectId, MultipartFile file) throws IOException {
        String encodedContent = Base64.getEncoder().encodeToString(file.getBytes());

        ObjectNode content = objectMapper.createObjectNode();
        content.put("fileName", file.getOriginalFilename());
        content.put("fileType", file.getContentType());
        content.put("fileSize", file.getSize());
        content.put("data", encodedContent);

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("projectId", projectId.toString());
        metadata.put("contentType", file.getContentType());

        UUID messageId = edgeMessageService.createMessage(EDGE_MESSAGE_SUBJECT, content, metadata);
        logger.info("Uploaded file '{}' to EdgeMessage: {}", file.getOriginalFilename(), messageId);

        AttachmentDTO attachment = new AttachmentDTO();
        attachment.setProjectId(projectId);
        attachment.setFileName(file.getOriginalFilename());
        attachment.setFileType(file.getContentType());
        attachment.setFileSize(file.getSize());
        attachment.setMessageId(messageId);

        return withId(entityService.create(attachment));
    }

    /**
     * Creates attachment metadata only (without file content).
     */
    public AttachmentDTO uploadAttachment(AttachmentDTO attachment) {
        return withId(entityService.create(attachment));
    }

    /**
     * Retrieves attachment metadata by ID.
     */
    public Optional<AttachmentDTO> getAttachmentById(UUID id) {
        try {
            return Optional.of(withId(entityService.getById(id, MODEL_SPEC, AttachmentDTO.class)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Retrieves the raw file content (base64-decoded) from Cyoda EdgeMessage.
     */
    public Optional<byte[]> getAttachmentContent(UUID id) throws Exception {
        return getAttachmentById(id)
                .filter(a -> a.getMessageId() != null)
                .map(a -> {
                    try {
                        var content = edgeMessageService.getMessageContent(a.getMessageId());
                        if (content == null || !content.has("data")) return null;
                        return Base64.getDecoder().decode(content.get("data").asText());
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to retrieve file content: " + e.getMessage(), e);
                    }
                });
    }

    /**
     * Retrieves all attachments for a specific project.
     */
    public PageResult<AttachmentDTO> getAttachmentsByProjectId(UUID projectId, int page, int size) {
        SearchAndRetrievalParams params = SearchAndRetrievalParams.builder()
                .pageNumber(page).pageSize(size).build();
        return toPage(entityService.search(MODEL_SPEC, conditionByField("projectId", projectId.toString()),
                AttachmentDTO.class, params));
    }

    /**
     * Deletes attachment metadata and the corresponding EdgeMessage.
     */
    public boolean deleteAttachment(UUID id) {
        return getAttachmentById(id).map(attachment -> {
            if (attachment.getMessageId() != null) {
                edgeMessageService.deleteMessage(attachment.getMessageId());
                logger.info("Deleted EdgeMessage: {}", attachment.getMessageId());
            }
            entityService.deleteById(id);
            return true;
        }).orElse(false);
    }
}

