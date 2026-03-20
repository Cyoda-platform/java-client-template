package com.java_template.application.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.dto.AttachmentDTO;
import com.java_template.application.repository.AttachmentRepository;
import com.java_template.common.service.EdgeMessageService;
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

    private final AttachmentRepository attachmentRepository;
    private final EdgeMessageService edgeMessageService;
    private final ObjectMapper objectMapper;

    public AttachmentService(AttachmentRepository attachmentRepository,
                             EdgeMessageService edgeMessageService,
                             ObjectMapper objectMapper) {
        this.attachmentRepository = attachmentRepository;
        this.edgeMessageService = edgeMessageService;
        this.objectMapper = objectMapper;
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

        return attachmentRepository.create(attachment);
    }

    /**
     * Creates attachment metadata only (without file content).
     */
    public AttachmentDTO uploadAttachment(AttachmentDTO attachment) {
        return attachmentRepository.create(attachment);
    }

    /**
     * Retrieves attachment metadata by ID.
     */
    public Optional<AttachmentDTO> getAttachmentById(UUID id) {
        return attachmentRepository.findById(id);
    }

    /**
     * Retrieves the raw file content (base64-decoded) from Cyoda EdgeMessage.
     */
    public Optional<byte[]> getAttachmentContent(UUID id) throws Exception {
        return attachmentRepository.findById(id)
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
    public List<AttachmentDTO> getAttachmentsByProjectId(UUID projectId) {
        return attachmentRepository.findByProjectId(projectId);
    }

    /**
     * Deletes attachment metadata and the corresponding EdgeMessage.
     */
    public boolean deleteAttachment(UUID id) {
        return attachmentRepository.findById(id).map(attachment -> {
            if (attachment.getMessageId() != null) {
                edgeMessageService.deleteMessage(attachment.getMessageId());
                logger.info("Deleted EdgeMessage: {}", attachment.getMessageId());
            }
            return attachmentRepository.delete(id);
        }).orElse(false);
    }
}

