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

    public AttachmentDTO uploadAttachment(AttachmentDTO attachment) {
        return attachmentRepository.create(attachment);
    }

    public Optional<AttachmentDTO> getAttachmentById(UUID id) {
        return attachmentRepository.findById(id);
    }

    public List<AttachmentDTO> getAttachmentsByProjectId(UUID projectId) {
        return attachmentRepository.findByProjectId(projectId);
    }

    public List<AttachmentDTO> getAllAttachments() {
        return attachmentRepository.findAll();
    }

    public boolean deleteAttachment(UUID id) {
        return attachmentRepository.delete(id);
    }

    public boolean attachmentExists(UUID id) {
        return attachmentRepository.exists(id);
    }
}

