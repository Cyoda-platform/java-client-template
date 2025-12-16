# Payment Gateway - Functional Requirements

## Overview

Build a payment gateway that supports multi-currency processing, fraud detection, PCI compliance, recurring billing, and real-time transaction monitoring. The gateway will accept card and digital wallet payments, convert currencies, detect fraudulent patterns, store tokens securely, handle subscription billing, and expose observability for transactions and alerts.

## Actors

- Merchant: Integrates the gateway API to accept payments.
- Customer: Makes payments using cards or digital wallets.
- Admin: Monitors transactions, manages fraud rules, and config settings.
- Scheduler: Background process for recurring billing and retries.

## High-Level Requirements

1. Multi-currency Support
   - Accept payments in multiple currencies (USD, EUR, GBP, JPY, AUD).
   - Currency conversion module to settle in merchant's preferred currency.
   - Use exchange rates updated hourly; provide fallbacks.

2. Payment Methods
   - Support card payments (PAN tokenization), digital wallets (Apple Pay, Google Pay), and bank debits where available.
   - Tokenize sensitive card data; never store PAN in plaintext.

3. Fraud Detection
   - Real-time fraud scoring using rule-based and ML-based detectors.
   - Support configurable rules (amount thresholds, velocity checks, BIN checks, geolocation anomalies).
   - Flag and optionally block transactions based on scores.

4. PCI Compliance
   - Only store PCI tokens; sensitive card data should be handled by PCI-compliant processors or vaults.
   - Audit logging for all payment operations with tamper-evident records.
   - Role-based access control for admin interfaces.

5. Recurring Billing
   - Subscription model with plans, trials, and proration for mid-cycle changes.
   - Scheduler to run billing cycles, charge saved payment methods, and handle failed payments with retry policies.

6. Real-time Transaction Monitoring
   - Stream transaction events to monitoring pipelines.
   - Dashboard for transaction volume, failures, fraud rate, and latency.
   - Alerts for spikes, high failure rates, or suspicious activity.

## API Surface

- POST /payments
  - Create a payment intent with amount, currency, payment_method (token), merchant_id, capture(boolean)
- POST /payments/{id}/capture
  - Capture an authorized payment
- POST /subscriptions
  - Create subscription (plan_id, customer_id, payment_method)
- POST /webhooks
  - Handle processor webhooks for settlement and disputes
- GET /transactions
  - List/filter transactions by merchant, status, currency, date range

## Data Models (examples)

- Merchant
  - id, name, default_currency, settlement_currency, payout_schedule
- Customer
  - id, name, email, saved_payment_tokens
- Payment
  - id, amount, currency, status, merchant_id, customer_id, payment_method_token, fraud_score, processor_id, created_at
- Subscription
  - id, plan_id, customer_id, status, next_billing_date, payment_method_token

## Workflows

1. Authorization & Capture
   - Create payment intent -> process through processor -> receive authorization -> optionally capture
2. Recurring Billing
   - Scheduler triggers -> create invoice -> charge payment method -> handle success/failure
3. Fraud Handling
   - Transaction in -> fraud score computed -> if blocked then decline; if review then flag

## Non-Functional Requirements

- Scalability: Handle thousands of TPS; horizontally scalable processors.
- Reliability: Retry policies and durable queues for background jobs.
- Security: End-to-end encryption for sensitive fields, strict RBAC, and secure secrets management.

## Operational Considerations

- Exchange rate updates, processor failover, and dispute handling.
- Integrations with third-party processors and KYC providers.
- Logging, metrics, and retention policies.

## Acceptance Criteria

- End-to-end payment flow for card payments in at least 3 currencies
- Working recurring billing with retries and cancellation
- Fraud rules configurable and firing for test cases
- Monitoring dashboard with transaction metrics and alerts
