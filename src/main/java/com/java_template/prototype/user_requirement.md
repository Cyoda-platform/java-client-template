Goal: Build a customer onboarding portal for regulated businesses. Capture rich customer data, run validations and checks, and drive stateful workflows from submission to approval.

Primary entity: CustomerProfile
	•	Identity: legalName, tradingName, registrationId, taxId, countryOfIncorporation.
	•	OwnershipGraph: nested structure of beneficial owners (persons or entities), each with percentOwnership, controlFlags, residency, and optional children to represent multi-level ownership. Support cycles prevention and aggregate ownership by ultimate beneficiaries.
	•	Contacts: emails[], phones[] with labels and verificationStatus.
	•	Addresses: array with type, lines[], locality, region, postalCode, country, geoCode.
	•	KYCArtifacts: documents[] with type, fileRef, hash, issuer, issueDate, expiryDate, verificationResult, and revision history.
	•	Risk: riskScore, riskFactors[], pepFlags[], sanctionsHits[] with dispositions.
	•	State: {Draft, Submitted, InReview, PendingDocs, Approved, Rejected, Archived} with timestamps and actor.

Workflow:
	•	Draft → Submitted when mandatory fields and minimum documents are present.
	•	Submitted → InReview auto-triggers checks (KYC, PEP/sanctions via external APIs, address verification, duplicate detection).
	•	InReview → PendingDocs on missing or expired documents; request specific artifacts.
	•	InReview → Approved requires: verified identity, ownership aggregation ≥ 75% coverage of ultimate owners, riskScore ≤ threshold, all sanctions hits dispositioned.
	•	Any → Rejected with reason codes; auto-notify and allow re-submission.
	•	SLA timers: escalate if InReview > 48h or PendingDocs > 7d.

Validations & checks:
	•	Strong ID rules per country; VAT/tax number formats; address postal code by country.
	•	OwnershipGraph consistency: no circular references; total declared ownership ≥ 100% ± tolerance; UBO roll-up.
	•	Document integrity via file hash; expiry alerts; revision audit.
	•	Duplicate detection by fuzzy match on legalName + registrationId + country.

APIs & reports:
	•	Create/Update CustomerProfile; upload documents; request decision.
	•	Webhook events on state changes.
	•	Reports: onboarding throughput, time-to-approve by risk band, outstanding PendingDocs, UBO coverage, audit of decisions.