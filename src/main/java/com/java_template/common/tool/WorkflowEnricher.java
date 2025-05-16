package com.java_template.common.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

public class WorkflowEnricher {

    private static final ObjectMapper mapper = new ObjectMapper();

    // Default templates
    private static final ObjectNode DEFAULT_WORKFLOW_CRITERIA;
    private static final ObjectNode DEFAULT_TRANSITION_CRITERIA;
    private static final ObjectNode DEFAULT_PROCESSOR_CRITERIA;
    private static final ObjectNode DEFAULT_EXTERNALIZED_PROCESSOR_DEFAULTS;

    static {
        // Build default_workflow_criteria
        DEFAULT_WORKFLOW_CRITERIA = mapper.createObjectNode();
        DEFAULT_WORKFLOW_CRITERIA.set("externalized_criteria", mapper.createArrayNode());

        ArrayNode wfCondCriteria = mapper.createArrayNode();
        ObjectNode wfCondEntry = mapper.createObjectNode();
        wfCondEntry.put("name", "ENTITY_MODEL_VAR:ENTITY_VERSION_VAR:CHAT_ID_VAR");
        wfCondEntry.put("description", "Workflow criteria");

        ObjectNode condition = mapper.createObjectNode();
        condition.put("group_condition_operator", "AND");
        ArrayNode conditions = mapper.createArrayNode();

        ObjectNode c1 = mapper.createObjectNode();
        c1.put("field_name", "entityModelName");
        c1.put("is_meta_field", true);
        c1.put("operation", "equals");
        c1.put("value", "ENTITY_MODEL_VAR");
        c1.put("value_type", "strings");

        ObjectNode c2 = mapper.createObjectNode();
        c2.put("field_name", "entityModelVersion");
        c2.put("is_meta_field", true);
        c2.put("operation", "equals");
        c2.put("value", "ENTITY_VERSION_VAR");
        c2.put("value_type", "strings");

        conditions.add(c1);
        conditions.add(c2);
        condition.set("conditions", conditions);
        wfCondEntry.set("condition", condition);
        wfCondCriteria.add(wfCondEntry);

        DEFAULT_WORKFLOW_CRITERIA.set("condition_criteria", wfCondCriteria);

        // Build default_transition_criteria and default_processor_criteria
        DEFAULT_TRANSITION_CRITERIA = mapper.createObjectNode();
        DEFAULT_TRANSITION_CRITERIA.set("externalized_criteria", mapper.createArrayNode());
        DEFAULT_TRANSITION_CRITERIA.set("condition_criteria", mapper.createArrayNode());

        DEFAULT_PROCESSOR_CRITERIA = DEFAULT_TRANSITION_CRITERIA.deepCopy();

        // Build default_externalized_processor_defaults
        DEFAULT_EXTERNALIZED_PROCESSOR_DEFAULTS = mapper.createObjectNode();
        DEFAULT_EXTERNALIZED_PROCESSOR_DEFAULTS.put("calculation_nodes_tags", "CHAT_ID_VAR");
        DEFAULT_EXTERNALIZED_PROCESSOR_DEFAULTS.put("attach_entity", true);
        DEFAULT_EXTERNALIZED_PROCESSOR_DEFAULTS.put("calculation_response_timeout_ms", "120000");
        DEFAULT_EXTERNALIZED_PROCESSOR_DEFAULTS.put("retry_policy", "NONE");
        DEFAULT_EXTERNALIZED_PROCESSOR_DEFAULTS.put("sync_process", false);
        DEFAULT_EXTERNALIZED_PROCESSOR_DEFAULTS.put("new_transaction_for_async", true);
        DEFAULT_EXTERNALIZED_PROCESSOR_DEFAULTS.put("none_transactional_for_async", false);
        DEFAULT_EXTERNALIZED_PROCESSOR_DEFAULTS.set("processor_criteria", DEFAULT_PROCESSOR_CRITERIA.deepCopy());
    }

    public static String enrichWorkflow(String wf) throws JsonProcessingException {
        // 1) Ensure workflow_criteria
        ObjectNode workflow = (ObjectNode) mapper.readTree(wf);
        if (!workflow.has("workflow_criteria")) {
            workflow.set("workflow_criteria", DEFAULT_WORKFLOW_CRITERIA.deepCopy());
        }

        // 2) Process transitions
        ArrayNode transitions = (ArrayNode) workflow.get("transitions");
        for (JsonNode tNode : transitions) {
            if (!(tNode instanceof ObjectNode)) continue;
            ObjectNode transition = (ObjectNode) tNode;

            // 2a) transition_criteria
            if (!transition.has("transition_criteria")) {
                transition.set("transition_criteria", DEFAULT_TRANSITION_CRITERIA.deepCopy());
            }

            // 2b) processes
            ObjectNode processes = transition.has("processes")
                    ? (ObjectNode) transition.get("processes")
                    : mapper.createObjectNode();

            // ensure schedule_transition_processors exists
            if (!processes.has("schedule_transition_processors")) {
                processes.set("schedule_transition_processors", mapper.createArrayNode());
            }

            // 2c) externalized_processors
            if (processes.has("externalized_processors")) {
                ArrayNode extProcs = (ArrayNode) processes.get("externalized_processors");
                for (JsonNode pNode : extProcs) {
                    if (!(pNode instanceof ObjectNode)) continue;
                    ObjectNode proc = (ObjectNode) pNode;

                    // name & description defaults
                    if (!proc.has("name")) {
                        proc.put("name", "");
                    }
                    if (!proc.has("description")) {
                        proc.put("description", "External processor to create a job");
                    }

                    // apply each default if missing
                    DEFAULT_EXTERNALIZED_PROCESSOR_DEFAULTS.fieldNames().forEachRemaining(key -> {
                        if (!proc.has(key)) {
                            proc.set(key, DEFAULT_EXTERNALIZED_PROCESSOR_DEFAULTS.get(key).deepCopy());
                        } else if ("processor_criteria".equals(key)) {
                            ObjectNode pc = (ObjectNode) proc.get("processor_criteria");
                            if (!pc.has("externalized_criteria")) {
                                pc.set("externalized_criteria", mapper.createArrayNode());
                            }
                            if (!pc.has("condition_criteria")) {
                                pc.set("condition_criteria", mapper.createArrayNode());
                            }
                        }
                    });
                }
            }

            // write back processes
            transition.set("processes", processes);
        }

        // 3) Append suffix to workflow name
        String originalName = workflow.path("name").asText("");
        workflow.put("name", originalName + ":ENTITY_MODEL_VAR:ENTITY_VERSION_VAR:CHAT_ID_VAR");

        return mapper.writeValueAsString(workflow);
    }
}
