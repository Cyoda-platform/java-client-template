package com.java_template.application.service;

import com.java_template.application.dto.BugLinkDTO;
import com.java_template.application.repository.BugLinkRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Bug Link operations
 */
@Service
public class BugLinkService {
    private final BugLinkRepository bugLinkRepository;

    public BugLinkService(BugLinkRepository bugLinkRepository) {
        this.bugLinkRepository = bugLinkRepository;
    }

    /**
     * Links a bug to a test run step
     */
    public BugLinkDTO linkBug(BugLinkDTO bugLink) {
        return bugLinkRepository.create(bugLink);
    }

    /**
     * Retrieves a bug link by ID
     */
    public Optional<BugLinkDTO> getBugLinkById(UUID id) {
        return bugLinkRepository.findById(id);
    }

    /**
     * Retrieves all bug links for a specific test run step
     */
    public List<BugLinkDTO> getBugLinksByTestRunStep(UUID testRunStepId) {
        return bugLinkRepository.findByTestRunStepId(testRunStepId);
    }

    /**
     * Unlinks a bug from a test run step by deleting the bug link
     */
    public boolean unlinkBug(UUID bugLinkId) {
        return bugLinkRepository.delete(bugLinkId);
    }
}

