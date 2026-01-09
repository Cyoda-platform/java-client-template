# Processor Implementation Guide

## Overview

This guide provides templates and patterns for implementing the 21 processors referenced in the four workflows.

## Processor Template

```java
@Component
public class ProcessorName extends CyodaProcessor {
    
    @Autowired
    private EntityService entityService;
    
    @Override
    public CyodaEntity process(CyodaEntity entity, ProcessingContext context) 
            throws ProcessingException {
        try {
            // 1. Extract data from entity
            // 2. Perform business logic
            // 3. Update entity state/metadata
            // 4. Return modified entity
            
            return entity;
        } catch (Exception e) {
            throw new ProcessingException("Error in " + this.getClass().getSimpleName(), e);
        }
    }
}
```

## Implementation Priority

### Phase 1: Core Processors (High Priority)
1. **validatePayload** - Input validation
2. **normalizeTransaction** - Data standardization
3. **computeRiskScore** - Risk calculation
4. **createAlert** - Alert generation

### Phase 2: Integration Processors (Medium Priority)
5. **callKYCProvider** - External API integration
6. **evaluateRulesEngine** - Rule evaluation
7. **fuzzyWatchlistMatch** - Watchlist matching
8. **storeDocuments** - Document persistence

### Phase 3: Case Management (Medium Priority)
9. **assignAnalyst** - Workload distribution
10. **createCase** - Case creation
11. **enrichWithCustomerHistory** - Data enrichment
12. **attachEvidence** - Evidence linking

### Phase 4: Reporting & Compliance (Lower Priority)
13. **collectReportData** - Data aggregation
14. **renderTemplate** - Report generation
15. **signAndHashExport** - Digital signing
16. **submitToRegulator** - Regulatory submission

### Phase 5: Utility Processors (Lower Priority)
17. **createCaseOnHighRisk** - Conditional case creation
18. **holdFunds** - Transaction hold
19. **updateAlertStatus** - Status updates
20. **generateSARReport** - SAR generation
21. **resumeReview** - Workflow resumption

## Key Implementation Patterns

### Pattern 1: Validation Processor
```java
@Component
public class ValidatePayload extends CyodaProcessor {
    @Override
    public CyodaEntity process(CyodaEntity entity, ProcessingContext context) {
        if (entity == null || !entity.isValid()) {
            throw new ProcessingException("Invalid entity");
        }
        return entity;
    }
}
```

### Pattern 2: External API Integration
```java
@Component
public class CallKYCProvider extends CyodaProcessor {
    @Autowired
    private RestTemplate restTemplate;
    
    @Override
    public CyodaEntity process(CyodaEntity entity, ProcessingContext context) {
        // Call external API with timeout/retry logic
        // Update entity with response
        return entity;
    }
}
```

### Pattern 3: Scoring Processor
```java
@Component
public class ComputeRiskScore extends CyodaProcessor {
    @Override
    public CyodaEntity process(CyodaEntity entity, ProcessingContext context) {
        double score = calculateScore(entity);
        entity.getMetadata().put("riskScore", score);
        return entity;
    }
}
```

### Pattern 4: Entity Creation
```java
@Component
public class CreateAlert extends CyodaProcessor {
    @Autowired
    private EntityService entityService;
    
    @Override
    public CyodaEntity process(CyodaEntity entity, ProcessingContext context) {
        Alert alert = new Alert();
        // Populate alert from entity
        entityService.create(alert);
        return entity;
    }
}
```

## Testing Strategy

### Unit Tests
- Test processor logic in isolation
- Mock external dependencies
- Verify entity state changes

### Integration Tests
- Test processor within workflow context
- Verify state transitions
- Test condition evaluation

### End-to-End Tests
- Test complete workflow execution
- Verify audit logging
- Test error handling

## Error Handling

### Processor Exceptions
```java
throw new ProcessingException("Descriptive error message", cause);
```

### Retry Logic
- Implement for external API calls
- Use exponential backoff
- Max 3 retries recommended

### Fallback Behavior
- Define fallback transitions
- Log failures for audit
- Notify operators for critical failures

## Performance Optimization

### Caching
- Cache watchlist data (1-hour TTL)
- Cache KYC provider responses (24-hour TTL)
- Cache rule definitions (on-demand reload)

### Async Processing
- Use @Async for long-running operations
- Implement callbacks for completion
- Monitor async task queue

### Batch Processing
- Process transactions in batches (100-500)
- Implement batch timeout (5 minutes)
- Aggregate results before state transition

## Monitoring & Observability

### Metrics to Track
- Processor execution time
- Success/failure rates
- Entity state distribution
- Workflow completion time

### Logging
- Log processor entry/exit
- Log entity state changes
- Log external API calls
- Log errors with full context

### Alerting
- Alert on processor failures
- Alert on SLA violations
- Alert on unusual patterns

## Deployment Checklist

- [ ] All 21 processors implemented
- [ ] Unit tests pass (>80% coverage)
- [ ] Integration tests pass
- [ ] Performance tests pass (10k tx/day)
- [ ] Security review completed
- [ ] Audit logging verified
- [ ] Documentation updated
- [ ] Staging deployment successful
- [ ] UAT sign-off obtained
- [ ] Production deployment scheduled

---

**Next Steps:** Start with Phase 1 processors for MVP validation

