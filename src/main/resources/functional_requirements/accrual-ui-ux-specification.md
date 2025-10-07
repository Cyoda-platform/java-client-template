# Interest Accrual UI/UX Specification

**Version**: 1.0
**Date**: 2025-10-07
**Status**: Draft

---

## Table of Contents

1. [Overview](#1-overview)
2. [User Stories](#2-user-stories)
3. [User Personas](#3-user-personas)
4. [Information Architecture](#4-information-architecture)
5. [Screen Specifications](#5-screen-specifications)
6. [Interaction Patterns](#6-interaction-patterns)
7. [Validation Rules](#7-validation-rules)
8. [Error Handling](#8-error-handling)
9. [Accessibility](#9-accessibility)
10. [Responsive Design](#10-responsive-design)

---

## 1. Overview

This document specifies the user interface and user experience for the Interest Accrual system, which enables users to trigger and monitor daily interest accrual calculations for commercial loans. The system supports three primary modes of operation:

1. **Daily Interest Accrual** - Normal end-of-day processing for the current business date
2. **As-at Interest Accrual** - Corrections within the current accounting period
3. **Backdated Interest Accrual** - Historical corrections for prior accounting periods

### 1.1 Design Principles

- **Clarity**: Clear distinction between different accrual modes and their implications
- **Safety**: Prevent accidental backdated runs through confirmation dialogs and reason codes
- **Transparency**: Real-time visibility into batch progress and results
- **Traceability**: Complete audit trail of all accrual operations
- **Efficiency**: Streamlined workflows for daily operations

### 1.2 Technical Context

The UI interacts with the following backend components:
- **EODAccrualBatch** entity - Orchestrates batch runs
- **Accrual** entity - Individual loan accrual records
- **REST API** - `/eod-batches` and `/accruals` endpoints
- **Workflow Engine** - Cyoda workflow state machine

---

## 2. User Stories

### US-001: Daily Interest Accrual

**As a** Finance Operations User
**I want to** trigger the process to calculate and record interest for every ACTIVE loan for the current business day
**So that** the company's interest receivable balance is always accurate and up-to-date

**Acceptance Criteria**:
- User can initiate a batch run for today's business date with one click
- System validates that today's batch has not already been run
- User can see real-time progress of the batch execution
- User can view summary metrics (eligible loans, processed, failed, totals)
- User can drill down into individual accrual details
- System prevents duplicate runs for the same business date

### US-002: As-at Interest Accrual

**As a** Finance Operations User
**I want to** trigger the process to automatically calculate and record interest for every ACTIVE loan as at a time in the past within the same period
**So that** I can correct the state of accruals for that period that might have been produced due to bugs, incorrect static data or other influences

**Acceptance Criteria**:
- User can select a past date within the current accounting period
- System clearly indicates this is a correction run (not backdated)
- User must provide a reason code for the correction
- System shows which existing accruals will be superseded
- User can simulate the run before executing
- System creates new accruals that supersede the originals

### US-003: Backdated Interest Accrual

**As a** Finance Operations User
**I want to** trigger the process to automatically calculate and record interest for every ACTIVE loan for a given value date in the past
**So that** I can adjust the state of accruals for the current period due to backdated amendments in dependent data (loans, payments, ...)

**Acceptance Criteria**:
- User can select any past business date
- System clearly warns about prior period adjustment implications
- User must provide a detailed reason code (required for audit)
- User must confirm understanding of GL impact
- System shows cascade recalculation scope (affected future dates)
- User can simulate the run before executing
- System marks resulting accruals with priorPeriodFlag=true
- System triggers cascade recalculation for subsequent dates if needed

---

## 3. User Personas

### 3.1 Finance Operations Analyst

**Name**: Sarah Chen
**Role**: Finance Operations Analyst
**Experience**: 3 years in loan operations
**Technical Proficiency**: Medium

**Goals**:
- Run daily accrual batches efficiently
- Monitor batch progress and resolve failures quickly
- Generate accurate GL reports for month-end

**Pain Points**:
- Manual reconciliation is time-consuming
- Difficult to track down accrual discrepancies
- Unclear impact of backdated corrections

**Usage Frequency**: Daily for normal runs, weekly for corrections

### 3.2 Finance Manager

**Name**: Michael Rodriguez
**Role**: Finance Manager
**Experience**: 10 years in finance
**Technical Proficiency**: Low-Medium

**Goals**:
- Ensure accurate and timely accrual processing
- Review and approve backdated corrections
- Monitor system health and performance

**Pain Points**:
- Needs high-level visibility without technical details
- Requires audit trail for compliance
- Concerned about GL period integrity

**Usage Frequency**: Weekly for monitoring, monthly for approvals

### 3.3 System Administrator

**Name**: David Kim
**Role**: System Administrator
**Experience**: 5 years in system administration
**Technical Proficiency**: High

**Goals**:
- Troubleshoot failed batches
- Monitor system performance
- Manage user permissions

**Pain Points**:
- Needs detailed error logs
- Requires ability to retry failed items
- Needs performance metrics

**Usage Frequency**: Daily for monitoring, as-needed for troubleshooting

---

## 4. Information Architecture

### 4.1 Navigation Structure

```
Interest Accrual System
├── Dashboard (Home)
│   ├── Today's Batch Status
│   ├── Recent Batches
│   └── Quick Actions
├── Batch Management
│   ├── Create New Batch
│   ├── Batch List
│   └── Batch Detail
│       ├── Overview
│       ├── Accruals
│       ├── Failures
│       ├── Journals
│       └── Reconciliation
├── Accrual Search
│   ├── Search Filters
│   └── Accrual Detail
│       ├── Summary
│       ├── Journal Entries
│       └── History
├── Reports
│   ├── GL Monthly Report
│   ├── Batch Summary Report
│   └── Audit Trail
└── Administration
    ├── Reason Codes
    ├── Business Calendar
    └── User Permissions
```

### 4.2 Screen Hierarchy

1. **Level 1**: Dashboard (landing page)
2. **Level 2**: Batch List, Accrual Search, Reports
3. **Level 3**: Batch Detail, Accrual Detail, Report Detail
4. **Level 4**: Drill-down views (journal entries, history)

---

## 5. Screen Specifications

### 5.1 Dashboard (Home Screen)

**Purpose**: Provide at-a-glance status of accrual processing and quick access to common actions

**Layout**: 3-column responsive grid

#### Components:

**A. Today's Batch Status Card**
- **Position**: Top left, full width on mobile
- **Content**:
  - Business date (large, prominent)
  - Batch status badge (NOT_STARTED | IN_PROGRESS | COMPLETED | FAILED)
  - Progress bar (if in progress)
  - Key metrics: Eligible loans, Processed, Failed
  - Total DR/CR amounts
  - Last updated timestamp
- **Actions**:
  - "Start Today's Batch" button (primary, disabled if already run)
  - "View Details" link (if batch exists)

**B. Recent Batches List**
- **Position**: Center column
- **Content**:
  - Table with columns: Date, Mode, Status, Loans Processed, Initiated By, Actions
  - Last 10 batches, sorted by date descending
  - Status badges with color coding
  - Mode badges (TODAY | BACKDATED)
- **Actions**:
  - Click row to view batch detail
  - "View All Batches" link at bottom

**C. Quick Actions Panel**
- **Position**: Right column
- **Content**:
  - "Start Daily Batch" button (primary)
  - "Correct Past Date" button (secondary)
  - "Backdated Run" button (warning style)
  - "View GL Report" button (secondary)
  - "Search Accruals" button (secondary)

**D. System Health Indicators**
- **Position**: Bottom, full width
- **Content**:
  - Last successful batch date
  - System status (operational | degraded | down)
  - Pending failures count (if any)
  - Link to admin dashboard

---

### 5.2 Create New Batch Screen

**Purpose**: Initiate a new accrual batch run with appropriate mode and parameters

**Layout**: Centered form, max-width 800px

#### Form Sections:

**A. Batch Mode Selection** (Step 1)
- **Component**: Radio button group with cards
- **Options**:

  1. **Daily Accrual** (TODAY mode)
     - Icon: Calendar with checkmark
     - Label: "Daily Interest Accrual"
     - Description: "Calculate interest for today's business date"
     - Badge: "Recommended for daily operations"
     - Auto-fills asOfDate with current business date

  2. **As-at Correction** (BACKDATED mode, current period)
     - Icon: Calendar with pencil
     - Label: "As-at Interest Accrual"
     - Description: "Correct accruals within the current accounting period"
     - Badge: "Requires reason code"
     - Shows date picker for current period only

  3. **Backdated Correction** (BACKDATED mode, prior period)
     - Icon: Calendar with warning
     - Label: "Backdated Interest Accrual"
     - Description: "Adjust accruals for prior accounting periods"
     - Badge: "Prior Period Adjustment - Requires approval"
     - Shows date picker for any past date
     - Warning banner: "This will create prior period adjustments affecting GL"

**B. Batch Parameters** (Step 2)

*Fields displayed based on mode selection:*

1. **As-of Date** (required)
   - Component: Date picker with business day validation
   - For TODAY mode: Read-only, shows current business date
   - For AS-AT mode: Selectable within current period only
   - For BACKDATED mode: Selectable any past business date
   - Validation: Must be a valid business day
   - Helper text: Shows accounting period for selected date
### 5.4 Accrual Search Screen

**Purpose**: Search and filter accruals across all batches

**Layout**: Full-width with filter sidebar

#### Filter Sidebar (Left, 300px):

**Search Filters**:
- **Accrual ID** (text input with autocomplete)
- **Loan ID** (text input with autocomplete)
- **As-of Date Range** (date range picker)
- **Status** (multi-select: DRAFT | VALIDATED | POSTED | SUPERSEDED)
- **Currency** (multi-select)
- **Batch ID** (dropdown of recent batches)
- **Prior Period Flag** (checkbox)
- **Amount Range** (min/max inputs)

**Actions**:
- "Apply Filters" button (primary)
- "Clear All" link
- "Save Search" button (for frequent searches)

#### Results Area (Right, remaining width):

**Results Table**:
- Columns:
  - Accrual ID (link)
  - Loan ID (link)
  - As-of Date
  - Interest Amount
  - Currency
  - Status (badge)
  - Prior Period Flag (icon)
  - Batch ID (link)
  - Created At
  - Actions (View | Download)
- Sortable columns
- Pagination: 100 per page
- Bulk actions: Export selected, Compare

**Results Summary**:
- Total results count
- Sum of interest amounts
- Currency breakdown

---

### 5.5 Accrual Detail Screen

**Purpose**: View complete details of a single accrual

**Layout**: 2-column layout with sidebar

#### Main Content Area:

**A. Accrual Summary Card**
- Accrual ID (with copy button)
- Status badge (large)
- Key fields:
  - Loan ID (link to loan detail)
  - As-of Date
  - Interest Amount (large, prominent)
  - Currency
  - APR (annual percentage rate)
  - Days in Period
  - Prior Period Flag (badge if true)
  - Batch ID (link to batch detail)

**B. Principal Snapshot**
- Principal Amount
- Effective at Start of Day (boolean)
- Snapshot timestamp

**C. Journal Entries Table**
- Columns:
  - Entry ID
  - Account (INTEREST_RECEIVABLE | INTEREST_INCOME)
  - Direction (DR | CR)
  - Amount
  - Kind (ORIGINAL | REVERSAL | REPLACEMENT)
  - Adjusts Entry ID (if reversal)
  - Memo
- Visual indicators for reversals (link to original)
- Balance verification (DR total = CR total)

**D. Calculation Details** (Expandable)
- Formula used
- Input values (principal, APR, days)
- Calculation breakdown
- Rounding details

#### Sidebar:

**Metadata**:
- Created At
- Created By
- Last Updated
- Posting Timestamp
- Run ID (link to batch)

**Supersedence Chain** (if applicable):
- "Supersedes" link (to previous accrual)
- "Superseded By" link (to newer accrual)
- Visual timeline of versions

**Related Items**:
- Link to Loan detail
- Link to Batch detail
- Link to GL entries

**Actions**:
- Download as JSON
- Download as PDF
- View Audit Trail
- Compare with Previous (if superseded)

---

### 5.6 GL Monthly Report Screen

**Purpose**: View and export monthly GL aggregation reports

**Layout**: Full-width with report viewer

#### Report Parameters:

**Selection Controls**:
- **Month** (month picker, e.g., "August 2025")
- **Currency** (multi-select, default: all)
- **Include Prior Period Adjustments** (checkbox, default: checked)
- "Generate Report" button (primary)

#### Report Display:

**Summary Section**:
- Month
- Batch File ID
- Generated At
- Total Debits
- Total Credits
- Balance Status (✓ Balanced | ✗ Imbalanced)
- Imbalance Amount (if any, highlighted)
- Prior Period Adjustments Count
- Checksum (for integrity verification)

**Aggregated Entries Table**:
- Grouped by: As-of Date, then Account
- Columns:
  - As-of Date
  - Account
  - Direction
  - Currency
  - Amount
  - Entry Count
  - Prior Period Flag
- Expandable rows to show daily breakdown
- Subtotals by date
- Grand totals at bottom

**Prior Period Adjustments Section** (if any):
- Separate table with same structure
- Highlighted with warning color
- Shows impact on GL period

**Export Options**:
- Export to CSV (GL format)
- Export to JSON
- Export to Excel
- Export to PDF
- Schedule monthly export (admin feature)

---

## 6. Interaction Patterns

### 6.1 Batch Creation Flow

**Daily Accrual (Happy Path)**:
1. User clicks "Start Daily Batch" from dashboard
2. System validates current business date
3. System checks for existing batch (prevent duplicates)
4. Confirmation dialog: "Start accrual batch for [date]?"
5. User confirms
6. System creates batch and redirects to Batch Detail screen
7. User sees real-time progress updates

**As-at Correction Flow**:
1. User selects "Correct Past Date" from dashboard
2. System shows Create Batch form with AS-AT mode pre-selected
3. User selects date within current period
4. User selects reason code from dropdown
5. User optionally adds comments
6. User clicks "Simulate" (optional but recommended)
7. System shows simulation results in modal
8. User reviews impact and clicks "Start Batch"
9. Confirmation dialog: "This will supersede existing accruals for [date]. Continue?"
10. User confirms
11. System creates batch and redirects to Batch Detail screen

**Backdated Correction Flow**:
1. User selects "Backdated Run" from dashboard
2. System shows warning banner about prior period implications
3. User selects date from any past period
4. User selects reason code (required)
5. User adds detailed comments (strongly recommended)
6. System shows impact summary:
   - "This will create prior period adjustments"
   - "Affected future dates: [list]"
   - "GL period: [period] will be impacted"
7. User clicks "Simulate" (strongly recommended)
8. System shows simulation results with cascade scope
9. User reviews and clicks "Start Batch"
10. First confirmation: "This will create prior period adjustments. Continue?"
11. User confirms
12. Second confirmation: "You are about to modify closed GL period [period]. This requires approval. Confirm?"
13. User confirms
14. System creates batch and redirects to Batch Detail screen

### 6.2 Real-time Updates

**Batch Progress**:
- WebSocket connection for live updates
- Progress bar updates every 2 seconds
- Metrics update every 5 seconds
- State transitions trigger notifications
- Auto-refresh disabled when user is interacting with page

**Notifications**:
- Toast notifications for state changes
- Success: "Batch completed successfully"
- Warning: "Batch completed with [N] failures"
- Error: "Batch failed - [error message]"
- Info: "Batch started - processing [N] loans"

### 6.3 Error Recovery

**Failed Batch**:
1. User sees FAILED status badge on Batch Detail
2. Error summary shows failure reason
3. User clicks "View Failures" tab
4. User reviews failed loans and error messages
5. User can:
   - Retry all failed loans
   - Retry selected loans
   - Export failures for investigation
   - Cancel batch and start new one

**Individual Accrual Failure**:
1. User sees failure in Failures tab
2. User clicks "View Details" to see error
3. User investigates root cause (loan data, rate, etc.)
4. User fixes underlying issue
5. User clicks "Retry" for that loan
6. System re-processes single loan
7. Success notification or new error message

---

## 7. Validation Rules

### 7.1 Batch Creation Validation

**As-of Date**:
- Must be a valid business day (per calendar)
- For TODAY mode: Must equal current business date
- For AS-AT mode: Must be within current accounting period
- For BACKDATED mode: Must be in the past
- Cannot be a future date
- Error message: "Selected date is not a valid business day"

**Reason Code**:
- Required for AS-AT and BACKDATED modes
- Must be from approved list
- If OTHER selected, comments are required
- Error message: "Reason code is required for correction runs"

**Duplicate Prevention**:
- Check for existing batch with same asOfDate and mode
- For TODAY mode: Only one batch per business date
- For BACKDATED mode: Warn if batch already exists, allow override
- Warning message: "A batch for this date already exists. Continue?"

**Loan Filter**:
- If Loan IDs provided, validate they exist
- If file uploaded, validate format (CSV, max 10,000 rows)
- Warn if filter results in zero eligible loans
- Warning message: "No eligible loans match the filter criteria"

### 7.2 Field Validation

**Date Inputs**:
- Format: YYYY-MM-DD or locale-specific
- Range validation based on mode
- Business day validation
- Real-time validation with inline error messages

**Numeric Inputs**:
- Max Steps: Integer, 1-100
- Amount ranges: Positive numbers, up to 2 decimal places
- Currency-aware formatting

**Text Inputs**:
- Comments: Max 500 characters, character counter
- Reason code details: Max 1000 characters
- Loan IDs: Comma-separated, validated format

---

## 8. Error Handling

### 8.1 Error Categories

**Validation Errors** (Client-side):
- Display inline below field
- Red border on invalid field
- Prevent form submission
- Example: "As-of date must be a business day"

**Business Rule Errors** (Server-side):
- Display in modal dialog
- Explain the rule violation
- Provide guidance on resolution
- Example: "Cannot create batch - accruals already exist for this date"

**System Errors** (Server/Network):
- Display in toast notification
- Log to error tracking system
- Provide error ID for support
- Example: "System error (ID: 12345). Please contact support."

**Workflow Errors** (Batch execution):
- Display in Failures tab
- Group by error type
- Provide retry mechanism
- Example: "Loan [ID] - Missing APR rate"

### 8.2 Error Messages

**Format**: [Context] - [Problem] - [Action]

**Examples**:
- "Batch Creation - Selected date is not a business day - Please select a valid business day from the calendar"
- "Accrual Processing - Loan LOAN-123 has no active APR rate - Update loan rate and retry"
- "GL Export - Report generation failed - Click retry or contact support (Error ID: ABC123)"

### 8.3 Error Recovery Flows

**Network Timeout**:
1. Show "Connection lost" banner
2. Attempt auto-reconnect (3 retries)
3. If failed, show "Offline mode" message
4. Disable actions that require server
5. Show "Retry" button

**Batch Stuck in Progress**:
1. If no updates for 30 minutes, show warning
2. "Batch appears stuck - Last update: [time]"
3. Provide "Check Status" button
4. Provide "Contact Support" link with batch ID

---

## 9. Accessibility

### 9.1 WCAG 2.1 Level AA Compliance

**Keyboard Navigation**:
- All interactive elements accessible via Tab key
- Logical tab order (top to bottom, left to right)
- Skip navigation links for screen readers
- Keyboard shortcuts for common actions:
  - Alt+N: New batch
  - Alt+S: Search accruals
  - Alt+R: Refresh current view

**Screen Reader Support**:
- Semantic HTML (header, nav, main, aside, footer)
- ARIA labels for all interactive elements
- ARIA live regions for dynamic updates
- Alt text for all icons and images
- Table headers properly associated with cells

**Visual Accessibility**:
- Color contrast ratio ≥ 4.5:1 for text
- Color contrast ratio ≥ 3:1 for UI components
- Don't rely on color alone (use icons + text)
- Focus indicators visible and high contrast
- Text resizable up to 200% without loss of functionality

**Form Accessibility**:
- Labels associated with inputs (for/id)
- Required fields marked with aria-required
- Error messages associated with fields (aria-describedby)
- Fieldsets and legends for grouped inputs
- Help text available via aria-describedby

### 9.2 Assistive Technology Testing

**Required Testing**:
- JAWS (Windows screen reader)
- NVDA (Windows screen reader)
- VoiceOver (macOS/iOS screen reader)
- TalkBack (Android screen reader)
- Dragon NaturallySpeaking (voice control)

---

## 10. Responsive Design

### 10.1 Breakpoints

- **Mobile**: 320px - 767px
- **Tablet**: 768px - 1023px
- **Desktop**: 1024px - 1439px
- **Large Desktop**: 1440px+

### 10.2 Mobile Adaptations

**Dashboard**:
- Stack cards vertically
- Collapse Recent Batches to show only 3
- Quick Actions as bottom sheet
- Swipe gestures for navigation

**Create Batch Form**:
- Single column layout
- Collapsible sections
- Sticky action buttons at bottom
- Native date pickers

**Batch Detail**:
- Tabs converted to accordion
- Horizontal scroll for tables
- Simplified metrics (show top 3)
- Pull-to-refresh gesture

**Tables**:
- Horizontal scroll with sticky first column
- Card view option for mobile
- Pagination controls larger for touch
- Swipe actions on rows (View, Delete)

### 10.3 Touch Optimization

**Target Sizes**:
- Minimum 44x44px for all touch targets
- Adequate spacing between interactive elements (8px minimum)
- Larger buttons for primary actions (48px height)

**Gestures**:
- Swipe left/right for tab navigation
- Pull-to-refresh for data updates
- Long-press for context menus
- Pinch-to-zoom for charts (where applicable)

---

## 11. Visual Design Specifications

### 11.1 Color Palette

**Primary Colors**:
- Primary Blue: #0066CC (buttons, links, focus states)
- Primary Dark: #004C99 (hover states)
- Primary Light: #E6F2FF (backgrounds, highlights)

**Status Colors**:
- Success Green: #28A745 (completed, balanced)
- Warning Orange: #FFC107 (in progress, warnings)
- Error Red: #DC3545 (failed, errors, imbalanced)
- Info Blue: #17A2B8 (informational messages)

**Mode Colors**:
- TODAY Mode: #28A745 (green badge)
- AS-AT Mode: #FFC107 (orange badge)
- BACKDATED Mode: #DC3545 (red badge)

**Neutral Colors**:
- Text Primary: #212529
- Text Secondary: #6C757D
- Border: #DEE2E6
- Background: #F8F9FA
- White: #FFFFFF

### 11.2 Typography

**Font Family**:
- Primary: "Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif
- Monospace: "Fira Code", "Courier New", monospace (for IDs, amounts)

**Font Sizes**:
- H1: 32px / 2rem (page titles)
- H2: 24px / 1.5rem (section headers)
- H3: 20px / 1.25rem (card titles)
- Body: 16px / 1rem (default text)
- Small: 14px / 0.875rem (helper text, labels)
- Tiny: 12px / 0.75rem (metadata, timestamps)

**Font Weights**:
- Regular: 400 (body text)
- Medium: 500 (labels, emphasis)
- Semibold: 600 (headings, buttons)
- Bold: 700 (important metrics)

### 11.3 Spacing System

**Base Unit**: 8px

**Scale**:
- xs: 4px (0.5 units)
- sm: 8px (1 unit)
- md: 16px (2 units)
- lg: 24px (3 units)
- xl: 32px (4 units)
- 2xl: 48px (6 units)
- 3xl: 64px (8 units)

### 11.4 Component Specifications

**Buttons**:
- Height: 40px (desktop), 48px (mobile)
- Padding: 12px 24px
- Border radius: 4px
- Font size: 16px
- Font weight: 600
- Transition: all 0.2s ease

**Input Fields**:
- Height: 40px
- Padding: 8px 12px
- Border: 1px solid #DEE2E6
- Border radius: 4px
- Focus: 2px solid Primary Blue, box-shadow

**Cards**:
- Background: White
- Border: 1px solid #DEE2E6
- Border radius: 8px
- Padding: 24px
- Box shadow: 0 1px 3px rgba(0,0,0,0.1)

**Badges**:
- Height: 24px
- Padding: 4px 8px
- Border radius: 12px
- Font size: 12px
- Font weight: 600
- Text transform: uppercase

**Tables**:
- Row height: 48px
- Cell padding: 12px 16px
- Border: 1px solid #DEE2E6
- Hover: Background #F8F9FA
- Striped rows: Alternate #FFFFFF and #F8F9FA

---

## 12. Performance Requirements

### 12.1 Page Load Times

- Dashboard: < 2 seconds
- Batch Detail: < 3 seconds
- Accrual Search: < 2 seconds
- GL Report: < 5 seconds (for large months)

### 12.2 Interaction Response Times

- Button clicks: < 100ms visual feedback
- Form validation: < 200ms
- Search/filter: < 500ms
- Batch status updates: < 5 seconds

### 12.3 Data Loading

- Pagination: Load 50-100 items per page
- Infinite scroll: Load next page when 80% scrolled
- Lazy loading: Load images and charts on demand
- Caching: Cache static data (reason codes, calendars) for 1 hour

---

## 13. Security Considerations

### 13.1 Authentication & Authorization

**Role-Based Access Control**:
- **Finance Analyst**: Can run TODAY batches, view all data
- **Finance Manager**: Can run AS-AT batches, approve BACKDATED
- **System Admin**: Can run BACKDATED batches, access all features
- **Read-Only User**: Can view batches and accruals, no create/edit

**Permission Checks**:
- `accrual.batch.create.today` - Create TODAY batches
- `accrual.batch.create.asat` - Create AS-AT batches
- `accrual.batch.create.backdated` - Create BACKDATED batches
- `accrual.batch.cancel` - Cancel running batches
- `accrual.batch.retry` - Retry failed items
- `accrual.view` - View accrual data
- `accrual.export` - Export reports

### 13.2 Audit Trail

**Logged Actions**:
- Batch creation (with all parameters)
- Batch cancellation
- Batch retry
- Accrual view (for sensitive data)
- Report export
- Configuration changes

**Audit Log Fields**:
- Timestamp
- User ID
- Action type
- Entity ID (batch, accrual)
- Before/after values (for updates)
- IP address
- Session ID

### 13.3 Data Protection

**Sensitive Data**:
- Mask loan IDs in logs (show last 4 digits)
- Mask customer information
- Encrypt data in transit (HTTPS)
- Encrypt data at rest (database encryption)

**Session Management**:
- Session timeout: 30 minutes of inactivity
- Concurrent session limit: 3 per user
- Force logout on password change
- Remember me: Max 30 days

---

## 14. Internationalization (i18n)

### 14.1 Supported Locales

**Phase 1**:
- en-US (English - United States)
- en-GB (English - United Kingdom)

**Phase 2** (Future):
- es-ES (Spanish - Spain)
- fr-FR (French - France)
- de-DE (German - Germany)

### 14.2 Localization Requirements

**Date Formats**:
- US: MM/DD/YYYY
- UK/EU: DD/MM/YYYY
- ISO: YYYY-MM-DD (for API)

**Number Formats**:
- US: 1,234.56
- EU: 1.234,56
- Decimal places: 2 for currency, configurable for rates

**Currency Display**:
- Symbol position: Locale-specific
- Thousand separator: Locale-specific
- Decimal separator: Locale-specific
- Example: $1,234.56 (US) vs 1.234,56 € (EU)

**Time Zones**:
- Display in user's local time zone
- Store in UTC
- Show time zone abbreviation (EST, GMT, etc.)

---

## 15. Analytics & Monitoring

### 15.1 User Analytics

**Track Events**:
- Page views (by screen)
- Batch creation (by mode)
- Batch completion (success/failure rate)
- Search usage (filters used)
- Export usage (format, frequency)
- Error occurrences (by type)

**User Metrics**:
- Daily active users
- Average session duration
- Most used features
- Error rate per user
- Time to complete batch creation

### 15.2 Performance Monitoring

**Client-Side Metrics**:
- Page load time (by screen)
- Time to interactive
- First contentful paint
- Largest contentful paint
- Cumulative layout shift

**Server-Side Metrics**:
- API response times
- Batch processing times
- Database query times
- Error rates
- Throughput (batches per hour)

### 15.3 Business Metrics

**Operational KPIs**:
- Batches run per day
- Average batch size (loans processed)
- Failure rate (by error type)
- Retry success rate
- Time to resolution (for failures)

**Financial KPIs**:
- Total interest accrued (daily, monthly)
- GL balance accuracy
- Prior period adjustment frequency
- Reconciliation discrepancies

---

## 16. Future Enhancements

### 16.1 Phase 2 Features

**Scheduled Batches**:
- Schedule daily batch to run automatically
- Configure run time (e.g., 11:00 PM daily)
- Email notifications on completion
- Automatic retry on failure

**Batch Templates**:
- Save batch configurations as templates
- Quick create from template
- Share templates across team
- Template versioning

**Advanced Filtering**:
- Save custom filters
- Share filters with team
- Filter by calculated fields
- Complex boolean logic

**Bulk Operations**:
- Bulk retry failed accruals
- Bulk export multiple batches
- Bulk comparison across dates
- Bulk approval workflow

### 16.2 Phase 3 Features

**Machine Learning**:
- Predict batch completion time
- Anomaly detection (unusual amounts)
- Failure prediction
- Optimization recommendations

**Mobile App**:
- Native iOS/Android apps
- Push notifications
- Offline mode
- Biometric authentication

**Advanced Reporting**:
- Custom report builder
- Scheduled report delivery
- Interactive dashboards
- Trend analysis

**Workflow Automation**:
- Approval workflows for BACKDATED
- Escalation rules
- SLA monitoring
- Automated reconciliation

---

## 17. Appendix

### 17.1 Glossary

- **Accrual**: Interest calculation for a single loan on a specific date
- **As-of Date**: The business date for which interest is being calculated
- **Batch**: A collection of accruals processed together
- **Backdated**: Processing for a date in a prior accounting period
- **Prior Period Adjustment (PPA)**: Accrual that affects a closed GL period
- **Supersede**: Replace a previous accrual with a corrected version
- **Cascade**: Recalculate subsequent dates affected by a backdated change
- **GL**: General Ledger
- **DR**: Debit
- **CR**: Credit

### 17.2 Acronyms

- **EOD**: End of Day
- **APR**: Annual Percentage Rate
- **GL**: General Ledger
- **PPA**: Prior Period Adjustment
- **UUID**: Universally Unique Identifier
- **CSV**: Comma-Separated Values
- **JSON**: JavaScript Object Notation
- **PDF**: Portable Document Format
- **API**: Application Programming Interface
- **UI**: User Interface
- **UX**: User Experience

### 17.3 References

- Cyoda EOD Accrual Workflows Specification
- REST API Documentation (`/eod-batches`, `/accruals`)
- Entity Model Documentation (EODAccrualBatch, Accrual)
- Workflow State Machine Documentation
- GL Aggregation Specification

---

**Document Version**: 1.0
**Last Updated**: 2025-10-07
**Next Review**: 2025-11-07
**Owner**: Product Team
**Approvers**: Finance Manager, System Architect, UX Lead
