# ASCII UI Wireframes (v0.1)
*Status:* Draft — Representative layouts to guide design. Not pixel-perfect.

## 1) Submission Wizard

```
+----------------------------------------------------------------------------------+
|  Submission Wizard                                 Step 3 of 5  [Protocol & Docs]|
+----------------------------------------------------------------------------------+
| Study Type: [ Clinical Trial v ]     Protocol ID: [   PROT-2025-013   ]          |
| Title:      [ Phase II Study of XYZ in ABC ]                                     |
| Sponsor:    [ Contoso Pharma ]        PI: [ Dr. Jane Roe           ]             |
| Sites: [+ Add Site]                                                              |
|                                                                                  |
| Required Documents                                                               |
| [*] Protocol (PDF)     [ Upload ]  v1.0  [Remove]                                |
| [ ] Investigator Brochure [ Upload ]                                             |
| [ ] Sample ICF           [ Upload ]                                              |
|                                                                                  |
| < Back                         Save as Draft                         Submit >    |
+----------------------------------------------------------------------------------+
```

## 2) Submission Detail & Timeline

```
+----------------------------------------------------------------------------------+
| Submission: PROT-2025-013 - Phase II Study of XYZ in ABC                         |
+----------------------------------------------------------------------------------+
| State: REVIEW   | SLA DUE: 2025-10-15 | Owner: Scientific Committee              |
|----------------------------------------------------------------------------------|
| Timeline                                                                             
| [✓] 2025-09-01 Submitted by jroe                                                    
| [✓] 2025-09-02 Intake completed by ksmith                                          
| [→] 2025-09-05 Sent to Scientific Review (parallel: Ethics, Legal)                 
| [ ] ...                                                                             |
|----------------------------------------------------------------------------------|
| Outstanding RFIs (2) | Documents (8) | Approvals (2/3) | Activity | Messages     |
+----------------------------------------------------------------------------------+
```

## 3) Document Library

```
+----------------------------------------------------------------------------------+
| Documents (Submission PROT-2025-013)                                             |
+----------------------------------------------------------------------------------+
| Type                Name                   Version   Status     Uploaded         |
| Protocol            XYZ_Protocol.pdf       v1.1      final      2025-09-04 by JR |
| Investigator Broch. XYZ_IB.pdf             v1.0      final      2025-09-01 by JR |
| ICF                 XYZ_ICF_v0.9.docx      v0.9      draft      2025-09-02 by JR |
| ...                                                                              |
+----------------------------------------------------------------------------------+
| [Upload] [Request New Version] [Export Archive]                                  |
+----------------------------------------------------------------------------------+
```

## 4) Workflow Board (Kanban)

```
+------------------+-------------------+-------------------+-------------------+
|   INTAKE         |   SCIENTIFIC      |    ETHICS         |   DECISION        |
+------------------+-------------------+-------------------+-------------------+
| #123 PROT-013    | #123 PROT-013     | #123 PROT-013     |                   |
| Due: Sep 07      | Due: Sep 12       | Due: Sep 12       |                   |
| Docs: 8/8        | RFIs: 1 open      | Approvals: 1/2    |                   |
+------------------+-------------------+-------------------+-------------------+
```

## 5) Study Dashboard

```
+----------------------------------------------------------------------------------+
| Study: XYZ — Sites: 4 — Enrolled: 38/60 — Visit Compliance: 92% — AEs: 14 (2 SAE)|
+----------------------------------------------------------------------------------+
| [Subjects] [Visits] [Inventory] [AEs] [Reports] [Settings]                       |
+----------------------------------------------------------------------------------+
| KPI Cards: Enrollment Trend | Overdue Visits | Open Queries | Inventory Alerts   |
+----------------------------------------------------------------------------------+
```

## 6) Subject Profile

```
+----------------------------------------------------------------------------------+
| Subject: S-00023 (Enrolled)     Study: XYZ                                       |
+----------------------------------------------------------------------------------+
| Demographics: Age 57 | Sex at birth: Female | Consent: Consented (2025-09-03)    |
|----------------------------------------------------------------------------------|
| Visit Schedule                                                                      
| V1  Planned: 2025-09-10  Actual: 2025-09-11  [Completed]                         
| V2  Planned: 2025-10-10  Actual: —            [Record Visit]                     
| ...                                                                                 |
+----------------------------------------------------------------------------------+
| [Create AE] [Open Queries] [Dispense IP]                                         |
+----------------------------------------------------------------------------------+
```

## 7) AE Entry

```
+----------------------------------------------------------------------------------+
| New Adverse Event — Subject S-00023                                              |
+----------------------------------------------------------------------------------+
| Onset Date: [ 2025-09-11 ]   Serious: ( ) No  (x) Yes                            |
| Severity: [ Mild v ]   Relatedness: [ Possible v ]                               |
| Outcome: [ Recovering v ]                                                        |
| Narrative: [..................................................................]  |
| Attachments: [ Upload ]                                                          |
| [Save Draft] [Mark as SAE & Alert Safety]                                        |
+----------------------------------------------------------------------------------+
```

## 8) Medication Inventory

```
+----------------------------------------------------------------------------------+
| Inventory — XYZ IP                                                               |
+----------------------------------------------------------------------------------+
| Lot       Expiry      On Hand   Reserved   Temp Range   Notes                    |
| LOT-01    2026-03-31  120       12         2–8°C        —                        |
| LOT-02    2026-06-30  90        10         2–8°C        —                        |
+----------------------------------------------------------------------------------+
| [Dispense] [Record Return] [Adjust]                                              |
+----------------------------------------------------------------------------------+
```

## 9) RFI Thread

```
+----------------------------------------------------------------------------------+
| RFI: Clarify Dose Titration Schedule — Due 2025-09-18                            |
+----------------------------------------------------------------------------------+
| @jroe Please provide updated schedule aligned with v1.1 of protocol.             |
| [Attach] [Reply]                                                                 |
| Thread:                                                                          |
| - 2025-09-05 @ksmith: Request created                                            |
| - 2025-09-06 @jroe: Uploaded 'Dose_Schedule_v1.1.xlsx'                           |
+----------------------------------------------------------------------------------+
```

## 10) Report Builder

```
+----------------------------------------------------------------------------------+
| Ad-hoc Report Builder                                                            |
+----------------------------------------------------------------------------------+
| Dataset: [ Submissions v ]  Metrics: [ + Add Metric ]  Dimensions: [ + Add ]     |
| Filters: [ status = 'review' ] [ created_at >= 2025-09-01 ]                      |
| Visualization: (x) Table  ( ) Chart                                              |
| [Run] [Save] [Export CSV]                                                        |
+----------------------------------------------------------------------------------+
```

## 11) Real-Time Board

```
+----------------------------------------------------------------------------------+
| Live Board — Review Workload (Auto-refresh)                                      |
+----------------------------------------------------------------------------------+
| Queue    Open  Due Today  Overdue   SLA Breach %                                 |
| Intake    7       2         1          4%                                        |
| SciRev    12      4         3          10%                                       |
| Ethics    9       3         2          6%                                        |
+----------------------------------------------------------------------------------+
```

## 12) Admin — Workflow Designer

```
+----------------------------------------------------------------------------------+
| Workflow Template: Clinical Trial Review v3                                      |
+----------------------------------------------------------------------------------+
| States: [Intake] → [Scientific] → [Consolidation] → [Decision]                   |
| Parallel Branch: [Ethics] + [Legal] (join at Consolidation)                      |
| Transitions: + Add                                                               |
| SLA: Scientific (120h), Ethics (120h), Legal (120h)                              |
| [Publish New Version]                                                            |
+----------------------------------------------------------------------------------+
```
