Feature: Bulk Prizes CRUD
  This feature covers the lifecycle of Nobel Prize entities,
  including creation, reading, updating, and deletion.

  Scenario: Create, read, and delete multiple prizes in bulk
    Given I have a list of prizes
      | year | category    | comment                         |
      | 2027 | Bulk Test 1 | First prize in the bulk set     |
      | 2028 | Bulk Test 2 | Second prize in the bulk set    |

    When I create the prizes in bulk
    Then 2 prizes should be created successfully

    When I get all of model "nobel-prize" version 1
    Then returned list of 2 prizes

    When I delete all of model "nobel-prize" version 1
    Then 2 prizes were deleted

    When I get all of model "nobel-prize" version 1
    Then returned list of 0 prizes