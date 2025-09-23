Feature: Single Entity CRUD
  This feature covers the lifecycle of a single Nobel Prize entity,
  including creation, reading, updating, and deletion.

  Scenario: Create, read, update, and delete a single prize
    Given I have a prize:
      | year | category          | comment        |
      | 2025 | Unified Step Test | This is a test |

    When I create a single prize
    Then 1 prizes should be created successfully

    When I get the prize by its ID
    Then the prize's year should be "2025"

    When I update the prize with the year "2026" and transition "UPDATE"
    Then the update should be successful

    When I get the prize by its ID again
    Then the prize's year should be "2026"

    When I delete the prize by its ID
    Then the deletion should be successful

    When I try to get the prize by its ID
    Then the prize is not found