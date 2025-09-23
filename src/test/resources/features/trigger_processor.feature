Feature: Client's Processor Triggering
  This feature covers trigger of async client calculation node.

  Scenario: Trigger client's processor
    Given I have a prize:
      | year | category          | comment        |
      | 2025 | Unified Step Test | This is a test |

    When I import workflow from file "workflow_with_processor.json" for model "nobel-prize" version 1
    Then Workflow imported successfully

    When I create a single prize
    Then 1 prizes should be created successfully

    When I update the prize with transition "TRIGGER_PROCESSOR"
    Then Awaits processor is triggered