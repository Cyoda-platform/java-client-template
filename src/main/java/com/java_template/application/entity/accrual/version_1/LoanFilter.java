package com.java_template.application.entity.accrual.version_1;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Filter criteria for selecting loans to include in an EOD accrual batch.
 * 
 * <p>Allows filtering by specific loan IDs or product codes. If both are null or empty,
 * all eligible loans will be included in the batch.</p>
 */
@Data
public class LoanFilter {
    /**
     * Optional list of specific loan IDs to include in the batch.
     * If null or empty, no loan ID filtering is applied.
     */
    @JsonProperty("loanIds")
    private List<UUID> loanIds;
    
    /**
     * Optional list of product codes to include in the batch.
     * If null or empty, no product code filtering is applied.
     */
    @JsonProperty("productCodes")
    private List<String> productCodes;
}

