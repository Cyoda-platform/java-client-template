package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class CurrencyRate implements CyodaEntity {
    public static final String ENTITY_NAME = "CurrencyRate";

    private String currencyFrom; // currency code, e.g. USD
    private String currencyTo; // currency code, e.g. EUR
    private Float rate; // exchange rate value
    private String timestamp; // ISO timestamp when rate was valid
    private String jobId; // reference to CurrencyRateJob that triggered this rate creation

    public CurrencyRate() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return currencyFrom != null && !currencyFrom.isBlank()
            && currencyTo != null && !currencyTo.isBlank()
            && rate != null && rate > 0;
    }
}
