package com.java_template.application.repository;

import com.java_template.application.dto.AttachmentDTO;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory repository for Attachments
 */
@Repository
public class AttachmentRepository {
    private final Map<UUID, AttachmentDTO> attachments = new ConcurrentHashMap<>();

    public AttachmentDTO create(AttachmentDTO attachment) {
        UUID id = UUID.randomUUID();
        attachment.setId(id);
        attachment.setUploadedAt(LocalDateTime.now());
        attachments.put(id, attachment);
        return attachment;
    }

    public Optional<AttachmentDTO> findById(UUID id) {
        return Optional.ofNullable(attachments.get(id));
    }

    public List<AttachmentDTO> findByProjectId(UUID projectId) {
        return attachments.values().stream()
                .filter(a -> a.getProjectId().equals(projectId))
                .collect(Collectors.toList());
    }

    public List<AttachmentDTO> findAll() {
        return new ArrayList<>(attachments.values());
    }

    public boolean delete(UUID id) {
        return attachments.remove(id) != null;
    }

    public boolean exists(UUID id) {
        return attachments.containsKey(id);
    }
}

