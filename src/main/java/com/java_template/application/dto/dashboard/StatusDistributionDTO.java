package com.java_template.application.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for loan status distribution data.
 * 
 * <p>Contains parallel arrays of status labels and their corresponding counts.
 * Used for rendering status distribution charts in the dashboard UI.</p>
 * 
 * <p>Example JSON structure:</p>
 * <pre>
 * {
 *   "labels": ["active", "funded", "matured", "settled"],
 *   "values": [45, 12, 8, 5]
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusDistributionDTO {
    
    /**
     * Array of workflow state labels (e.g., "active", "funded", "matured").
     */
    private List<String> labels;
    
    /**
     * Array of counts corresponding to each label.
     * The count at index i corresponds to the label at index i.
     */
    private List<Integer> values;
}

