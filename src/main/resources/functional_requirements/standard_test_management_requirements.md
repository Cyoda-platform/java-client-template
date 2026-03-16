# Standard Test Management - Functional Requirements

Module 1: Workspace, Project Management & Access Control

FR 1.1 Authentication: The system shall support basic user registration and login using an Email and Password combination.
FR 1.2 Workspace Initialization: The system shall automatically assign the Admin role to the first user who registers and creates a new workspace/account.
FR 1.3 Team Management (Admin Panel): The system shall provide a team management panel allowing Admin users to invite new members via email, assign them either an Admin or Tester role, and remove them from the workspace.
FR 1.4 Project Creation: The system shall allow users with the Admin role to create, read, update, and delete isolated Projects.
FR 1.5 Data Isolation: The system shall restrict all queries for suites, test cases, and test runs to the context of a single Project to ensure no cross-project data leakage.
FR 1.6 Role Permissions:
Admins: full CRUD operations across all entities within their assigned project.
Testers: read-only access for the Test Repository and execute-only access for Test Runs.

Module 2: Test Repository

FR 2.1 Suite Management: The system shall allow Admins to create single-level folders (Suites) to group test cases. Nested folders are strictly prohibited to maintain simplicity.
FR 2.2 Test Case Creation: The system shall allow Admins to create Test Cases containing a mandatory Title, a Description, a Pre-conditions text field (for setup instructions), and a Priority level (e.g., High, Medium, Low).
FR 2.3 Step Management: The system shall allow Admins to add sequential steps to a Test Case. Each step must enforce two mandatory text fields: "Action" and "Expected Result".
FR 2.4 Multi-format Attachments: The system shall allow users to upload files of various formats (images, configuration files such as JSON/XML/YAML, and documents) to a Test Case. The backend shall store these files in an S3-compatible storage and save the resulting URL and original filename in the database.
FR 2.5 Test Case Export: The system shall allow both Admins and Testers to export selected Test Cases or entire Suites into standard CSV or XML formats. The exported file must include all case metadata and sequential step data.
FR 2.6 Test Case Import: The system shall allow Admins to bulk-import Test Cases by uploading a CSV or XML file. The system must parse the uploaded file and automatically generate new Test Cases with their corresponding steps within the active Project.
FR 2.7 Quick Search: The system shall provide a search bar allowing users to instantly filter and locate specific test cases by matching keywords against their titles.

Module 3: Test Execution

FR 3.1 Run Initialization: The system shall allow Admins and Testers to create a new Test Run by providing a mandatory Run Title (e.g., "Sprint 42 Regression"), an optional Environment (e.g., "Staging", "Production"), and selecting entire Suites (folders), individual Test Cases, or any combination of both.
FR 3.2 Step-Level Execution: The system shall allow Testers to mark individual steps within a running Test Case as Passed, Failed, or Skipped.
FR 3.3 Atomic Failure Logic: The system shall automatically change the overall status of a Test Case to Failed if any single step within it is marked as Failed.
FR 3.4 Execution Artifacts: The system shall allow Testers to upload evidence files (e.g., error screenshots, logs, network traces) directly linkable to a specific failed step.
FR 3.5 Defect Linking: The system shall provide a text input field labeled "Bug URL" on the Test Case execution view to allow Testers to save a direct link to an external bug tracker ticket.
FR 3.6 Run Completion (Locking): The system shall allow users (Admin, Tester) to manually mark an active Test Run as "Completed". Upon completion, the system shall lock the entire Test Run into a read-only state, preventing any further modifications to preserve historical integrity.

Module 4: Reporting

FR 4.1 Real-time Metrics: The system shall calculate the aggregate status of all test cases within a Test Run and display the total count of: Passed, Failed, Untested, and overall Total.
FR 4.2 Test Run Export: The system shall allow users (Admin, Tester) to export the detailed results of a completed or active Test Run into a standard format (PDF or CSV). The export must include the overall metrics (Total, Passed, Failed, Untested) and a list of failed cases with their attached Bug URLs.

Glossary:
Workspace: Organizational container for Projects and users.
Project: Isolated scope within a Workspace containing Suites, Test Cases, and Test Runs.
Suite: Single-level folder to group Test Cases.
Test Case: A set of ordered steps with pre-conditions and attachments.
Test Run: An execution instance consisting of a selection of Suites and/or Test Cases.

Suggested Data Model (core entities):
Workspace { id, name, created_at }
User { id, email, password_hash, role, workspace_id }
Project { id, workspace_id, name, description }
Suite { id, project_id, name }
TestCase { id, suite_id, title, description, pre_conditions, priority }
TestStep { id, test_case_id, sequence_index, action, expected_result }
Attachment { id, owner_type, owner_id, filename, url, content_type }
TestRun { id, project_id, title, environment, status, created_by }
TestRunExecution { id, test_run_id, test_case_id, status }
StepExecution { id, test_run_execution_id, test_step_id, status, evidence_attachments, bug_url }