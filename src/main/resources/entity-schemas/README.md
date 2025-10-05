# Entity JSON Schemas

This directory contains JSON Schema files for all business entities defined in the application. These schemas can be used with jsonschema2pojo to generate Java POJOs that match the existing entity structures.

## Schema Files

The following entity schemas are available:

1. **Party.json** - Legal entities (borrowers, lenders, agents) that participate in loan agreements
2. **Loan.json** - Funded commercial loans under servicing (aggregate root for financial activities)
3. **Accrual.json** - Daily interest calculation records for active loans
4. **Payment.json** - Payment records showing allocation to interest, fees, and principal
5. **SettlementQuote.json** - Early loan settlement quotes with expiration dates
6. **GLBatch.json** - Batches of summarized accounting entries for General Ledger posting
7. **GLLine.json** - Individual debit/credit lines within GL batches

## Schema Structure

Each schema file follows JSON Schema Draft 2020-12 specification and includes:

- **$schema**: JSON Schema version identifier
- **$id**: Unique identifier for the schema
- **title**: Entity name
- **description**: Entity purpose and business context
- **javaType**: Target Java class for POJO generation
- **properties**: Entity fields with types and descriptions
- **required**: List of mandatory fields
- **definitions**: Nested class definitions for complex types

## Java Type Mappings

The schemas use the following Java type mappings:

| JSON Schema Type | Java Type |
|-----------------|-----------|
| `string` | `java.lang.String` |
| `string` (format: date) | `java.time.LocalDate` |
| `string` (format: date-time) | `java.time.LocalDateTime` |
| `number` | `java.math.BigDecimal` |
| `integer` | `java.lang.Integer` |
| `boolean` | `java.lang.Boolean` |
| `array` | `java.util.List<T>` |
| `object` | Nested class |

## Generating POJOs

To generate Java POJOs from these schemas, you can use the jsonschema2pojo Gradle plugin:

```bash
./gradlew generateJsonSchema2Pojo
```

The generated classes will be placed in `build/generated-sources/js2p/` directory.

## Validation

All schema files have been validated for correct JSON syntax. You can validate them manually using:

```bash
python3 -m json.tool <schema-file.json>
```

## Nested Classes

Many entities contain nested classes for complex data structures:

### Party
- `PartyContact` - Contact information
- `PartyAddress` - Address information

### Loan (most complex)
- `LoanParty` - Parties involved in the loan
- `LoanFacility` - Loan facilities
- `LoanAvailability` - Facility availability
- `LoanTranche` - Loan tranches
- `LoanInterest` - Interest configuration
- `LoanRateReset` - Rate reset configuration
- `LoanFee` - Fee structures
- `LoanAmortization` - Amortization schedules
- `LoanCovenant` - Covenant definitions
- `LoanCureRights` - Cure rights for covenants
- `LoanCollateral` - Collateral information
- `LoanDrawdown` - Drawdown records
- `LoanFx` - Foreign exchange information
- `LoanRepayment` - Repayment records
- `LoanAllocation` - Payment allocation
- `LoanPrepayment` - Prepayment terms
- `LoanVoluntary` - Voluntary prepayment
- `LoanMandatory` - Mandatory prepayment

### Accrual
- `AccrualCalculation` - Calculation details
- `AccrualSubLedgerEntry` - Sub-ledger entries
- `AccrualAudit` - Audit information

### Payment
- `PaymentAllocation` - Payment allocation details
- `PaymentSubLedgerEntry` - Sub-ledger entries
- `PaymentAudit` - Audit information

### SettlementQuote
- `SettlementCalculation` - Settlement calculation breakdown
- `SettlementAudit` - Audit information

### GLBatch
- `GLControlTotals` - Control totals for validation
- `GLLine` - Individual GL lines (nested in batch)
- `GLExport` - Export information
- `GLBatchAudit` - Audit information
- `GLApproval` - Approval tracking

### GLLine
- `GLLineSource` - Source tracking
- `GLLineAudit` - Audit information

## Notes

- All schemas use `javaType` annotations to ensure generated classes match the existing entity package structure
- Required fields are explicitly marked in the `required` array
- Descriptions are preserved from the original Java entity comments
- All BigDecimal fields use `"type": "number"` with explicit `javaType` annotation
- All date fields use `"format": "date"` or `"format": "date-time"` with explicit `javaType` annotation
- Boolean and Integer wrapper types are explicitly specified using `javaType` annotations

