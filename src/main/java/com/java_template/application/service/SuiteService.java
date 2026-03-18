package com.java_template.application.service;

import com.java_template.application.dto.SuiteDTO;
import com.java_template.application.repository.SuiteRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Test Suite operations
 */
@Service
public class SuiteService {
    private final SuiteRepository suiteRepository;

    public SuiteService(SuiteRepository suiteRepository) {
        this.suiteRepository = suiteRepository;
    }

    public SuiteDTO createSuite(SuiteDTO suite) {
        suite.setStatus("ACTIVE");
        return suiteRepository.create(suite);
    }

    public Optional<SuiteDTO> getSuiteById(UUID id) {
        return suiteRepository.findById(id);
    }

    public List<SuiteDTO> getSuitesByProjectId(UUID projectId) {
        return suiteRepository.findByProjectId(projectId);
    }

    public List<SuiteDTO> getAllSuites() {
        return suiteRepository.findAll();
    }

    public SuiteDTO updateSuite(UUID id, SuiteDTO suite) {
        return suiteRepository.update(id, suite);
    }

    public boolean deleteSuite(UUID id) {
        return suiteRepository.delete(id);
    }

    public boolean suiteExists(UUID id) {
        return suiteRepository.exists(id);
    }
}

