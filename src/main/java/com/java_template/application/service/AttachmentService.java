package com.java_template.application.service;

import com.java_template.application.dto.AttachmentDTO;
import com.java_template.application.repository.AttachmentRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Attachment operations
 */
@Service
public class AttachmentService {
    private final AttachmentRepository attachmentRepository;

    public AttachmentService(AttachmentRepository attachmentRepository) {
        this.attachmentRepository = attachmentRepository;
    }

    /**
     * Uploads a new attachment
     */
    public AttachmentDTO uploadAttachment(AttachmentDTO attachment) {
        return attachmentRepository.create(attachment);
    }

    /**
     * Retrieves an attachment by ID
     */
    public Optional<AttachmentDTO> getAttachmentById(UUID id) {
        return attachmentRepository.findById(id);
    }

    /**
     * Retrieves all attachments for a specific project
     */
    public List<AttachmentDTO> getAttachmentsByProjectId(UUID projectId) {
        return attachmentRepository.findByProjectId(projectId);
    }

    /**
     * Deletes an attachment by ID
     */
    public boolean deleteAttachment(UUID id) {
        return attachmentRepository.delete(id);
    }
}

