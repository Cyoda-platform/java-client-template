Feature: Fetching by Condition
  This feature covers fetching of entities by condition.

  Scenario: Fetch by Condition
    Given I have a list of prizes
      | year | category    | comment                         |
      | 2027 | Bulk Test 1 | First prize in the bulk set     |
      | 2027 | Bulk Test 2 | Second prize in the bulk set    |
      | 2028 | Bulk Test 3 | Third prize in the bulk set     |
      | 2029 | Bulk Test 4 | Fourth prize in the bulk set    |
      | 2030 | Bulk Test 5 | Fifth prize in the bulk set     |

    When I create the prizes in bulk
    Then 5 prizes should be created successfully

    When I fetching of models "nobel-prize" version 1 by condition:
      """json
  {
    "type": "group",
    "operator": "AND",
    "conditions": [
      {
        "type": "simple",
        "field": "year",
        "operation": "EQUALS",
        "value": "2027",
        "jsonPath": "$.year"
      }
    ]
  }
  """
    Then returned list of 2 prizes