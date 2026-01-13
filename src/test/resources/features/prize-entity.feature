@e2e
Feature: Nobel Prize Entity Management
  As a user of the Cyoda platform
  I want to manage Nobel Prize entities through E2E tests
  So that I can verify the complete workflow integration

  # Note: These tests require a running Cyoda environment
  # They will be skipped if CYODA_API_URL is not configured

  @requires-cyoda
  Scenario: Create and retrieve a single prize
    Given I have a prize:
      | year | category | comment           |
      | 1901 | Physics  | First Nobel Prize |
    When I create a single prize
    And I get the prize by its ID
    Then the prize's year should be "1901"

  @requires-cyoda
  Scenario: Create multiple prizes in bulk
    Given I have a list of prizes:
      | year | category  | comment   |
      | 1905 | Physics   | Comment 1 |
      | 1906 | Chemistry | Comment 2 |
      | 1907 | Medicine  | Comment 3 |
    When I create the prizes in bulk
    Then 3 prizes should be created successfully

  @requires-cyoda
  Scenario: Get all prizes for a model
    Given I have a list of prizes:
      | year | category | comment   |
      | 1908 | Physics  | Comment 1 |
      | 1909 | Physics  | Comment 2 |
    When I create the prizes in bulk
    And I get all of model "nobel-prize" version 1
    Then returned list of 2 prizes

  @requires-cyoda
  Scenario: Delete all prizes for a model
    Given I have a list of prizes:
      | year | category | comment   |
      | 1910 | Physics  | Comment 1 |
      | 1911 | Physics  | Comment 2 |
    When I create the prizes in bulk
    And I delete all of model "nobel-prize" version 1
    Then 2 prizes were deleted

