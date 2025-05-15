{
  "name": "purrfect_pets_workflow",
  "description": "Workflow for processing pet entities in Purrfect Pets API",
  "transitions": [
    {
      "name": "validate_description",
      "description": "Validate and set default description if missing",
      "start_state": "None",
      "start_state_description": "Initial state",
      "end_state": "Description_validated",
      "end_state_description": "Pet description has been validated",
      "automated": true,
      "processes": {
        "schedule_transition_processors": [],
        "externalized_processors": [
          {
            "name": "processValidateDescription",
            "description": ""
          }
        ]
      }
    },
    {
      "name": "trigger_workflow",
      "description": "Trigger asynchronous pet workflow",
      "start_state": "Description_validated",
      "start_state_description": "Pet description has been validated",
      "end_state": "Workflow_triggered",
      "end_state_description": "Pet workflow has been triggered",
      "automated": true,
      "processes": {
        "schedule_transition_processors": [],
        "externalized_processors": [
          {
            "name": "processTriggerWorkflow",
            "description": ""
          }
        ]
      }
    }
  ]
}