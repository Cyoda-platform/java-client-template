// Chunk 1: Package, imports, class declaration, constants
package com.java_template.common.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class WorkflowConverter {
    public static final String TREE_NODE_ENTITY =
            "com.cyoda.tdb.model.treenode.TreeNodeEntity";

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private static final Map<String, Map<String, String>> OPERATION_MAPPING = new HashMap<>();

    static {
        OPERATION_MAPPING.put("equals (disregard case)", Map.of(
                "operation", "IEQUALS",
                "@bean", "com.cyoda.core.conditions.nonqueryable.IEquals"
        ));
        OPERATION_MAPPING.put("not equal (disregard case)", Map.of(
                "operation", "INOT_EQUAL",
                "@bean", "com.cyoda.core.conditions.nonqueryable.INotEquals"
        ));
        OPERATION_MAPPING.put("between (inclusive)", Map.of(
                "operation", "BETWEEN",
                "@bean", "com.cyoda.core.conditions.queryable.Between"
        ));
        OPERATION_MAPPING.put("contains", Map.of(
                "operation", "CONTAINS",
                "@bean", "com.cyoda.core.conditions.nonqueryable.IContains"
        ));
        OPERATION_MAPPING.put("starts with", Map.of(
                "operation", "ISTARTS_WITH",
                "@bean", "com.cyoda.core.conditions.nonqueryable.IStartsWith"
        ));
        OPERATION_MAPPING.put("ends with", Map.of(
                "operation", "IENDS_WITH",
                "@bean", "com.cyoda.core.conditions.nonqueryable.IEndsWith"
        ));
        OPERATION_MAPPING.put("does not contain", Map.of(
                "operation", "INOT_CONTAINS",
                "@bean", "com.cyoda.core.conditions.nonqueryable.INotContains"
        ));
        OPERATION_MAPPING.put("does not start with", Map.of(
                "operation", "INOT_STARTS_WITH",
                "@bean", "com.cyoda.core.conditions.nonqueryable.INotStartsWith"
        ));
        OPERATION_MAPPING.put("does not end with", Map.of(
                "operation", "NOT_ENDS_WITH",
                "@bean", "com.cyoda.core.conditions.nonqueryable.NotEndsWith"
        ));
        OPERATION_MAPPING.put("matches other field (case insensitive)", Map.of(
                "operation", "INOT_ENDS_WITH",
                "@bean", "com.cyoda.core.conditions.nonqueryable.INotEndsWith"
        ));
        OPERATION_MAPPING.put("equals", Map.of(
                "operation", "EQUALS",
                "@bean", "com.cyoda.core.conditions.queryable.Equals"
        ));
        OPERATION_MAPPING.put("not equal", Map.of(
                "operation", "NOT_EQUAL",
                "@bean", "com.cyoda.core.conditions.nonqueryable.NotEquals"
        ));
        OPERATION_MAPPING.put("less than", Map.of(
                "operation", "LESS_THAN",
                "@bean", "com.cyoda.core.conditions.queryable.LessThan"
        ));
        OPERATION_MAPPING.put("greater than", Map.of(
                "operation", "GREATER_THAN",
                "@bean", "com.cyoda.core.conditions.queryable.GreaterThan"
        ));
        OPERATION_MAPPING.put("less than or equal to", Map.of(
                "operation", "LESS_OR_EQUAL",
                "@bean", "com.cyoda.core.conditions.queryable.LessThanEquals"
        ));
        OPERATION_MAPPING.put("greater than or equal to", Map.of(
                "operation", "GREATER_OR_EQUAL",
                "@bean", "com.cyoda.core.conditions.queryable.GreaterThanEquals"
        ));
        OPERATION_MAPPING.put("between (inclusive, match case)", Map.of(
                "operation", "BETWEEN_INCLUSIVE",
                "@bean", "com.cyoda.core.conditions.queryable.BetweenInclusive"
        ));
        OPERATION_MAPPING.put("is null", Map.of(
                "operation", "IS_NULL",
                "@bean", "com.cyoda.core.conditions.nonqueryable.IsNull"
        ));
        OPERATION_MAPPING.put("is not null", Map.of(
                "operation", "NOT_NULL",
                "@bean", "com.cyoda.core.conditions.nonqueryable.NotNull"
        ));
    }

    public static String parseAiWorkflowToDtoJson(String inputJson) throws JsonProcessingException, JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();

        // 1. Deserialize input JSON into a Map
        Map<String, Object> inputMap = mapper.readValue(
                inputJson,
                new TypeReference<Map<String, Object>>() {}
        );

        // 2. Invoke the existing parser (which returns Map<String,Object>)
        Map<String, Object> dtoMap = parseAiWorkflowToDto(inputMap);

        // 3. Serialize the result back to JSON
        return mapper.writeValueAsString(dtoMap);
    }

    // Chunk 2: ID & timestamp utilities
    private static String generateId() {
        return UUID.randomUUID().toString();
    }

    private static String currentTimestamp() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(TS_FORMAT);
    }

    // Chunk 3: Value parsing & condition transformation
    @SuppressWarnings("unchecked")
    private static Object parseValue(Object v) {
        if (v instanceof Boolean || v instanceof Number) {
            return v;
        }
        try {
            return Double.valueOf(v.toString());
        } catch (Exception e) {
            return v;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> transformCondition(Map<String, Object> cond) {
        // Handle grouped conditions recursively
        if (cond.containsKey("conditions")) {
            List<Map<String, Object>> subs = new ArrayList<>();
            for (Map<String, Object> sub : (List<Map<String, Object>>) cond.get("conditions")) {
                subs.add(transformCondition(sub));
            }
            return Map.of(
                    "@bean", "com.cyoda.core.conditions.GroupCondition",
                    "operator", cond.getOrDefault("group_condition_operator", "AND"),
                    "conditions", subs
            );
        }

        // Lookup the mapping for this operation label
        String opLabel = (String) cond.get("operation");
        Map<String, String> mapping = OPERATION_MAPPING.get(opLabel);
        if (mapping == null) {
            throw new IllegalArgumentException("Unsupported operation: " + opLabel);
        }

        boolean isRange = mapping.get("operation").contains("BETWEEN");
        boolean isMeta = Boolean.TRUE.equals(cond.get("is_meta_field"));

        // Determine the appropriate fieldName
        String fieldName;
        if (!isMeta) {
            String prefix = "members.[*]@com#cyoda#tdb#model#treenode#NodeInfo.value"
                    + "@com#cyoda#tdb#model#treenode#PersistedValueMaps.";
            fieldName = prefix
                    + cond.get("value_type")
                    + ".[$." + cond.get("field_name") + "]";
        } else {
            fieldName = (String) cond.get("field_name");
        }

        // Build the base condition map
        Map<String, Object> base = new HashMap<>();
        base.put("@bean", mapping.get("@bean"));
        base.put("fieldName", fieldName);
        base.put("operation", mapping.get("operation"));
        base.put("rangeField", Boolean.toString(isRange));

        // Add value if required
        Object rawVal = cond.get("value");
        if (rawVal != null
                && !Set.of("IS_NULL", "NOT_NULL").contains(mapping.get("operation"))) {
            base.put("value", parseValue(rawVal));
        }

        // Special handling for "does not start with"
        if ("does not start with".equalsIgnoreCase(opLabel)) {
            Map<String, Object> alt = new HashMap<>();
            alt.put("@bean", "com.cyoda.core.conditions.nonqueryable.IStartsWith");
            alt.put("fieldName", cond.get("field_name"));
            alt.put("operation", "ISTARTS_WITH");
            alt.put("rangeField", "false");
            alt.put("value", parseValue(rawVal));
            base.put("iStartsWith", alt);
        }

        return base;
    }

    // Chunk 4: Bulk condition & state lookup helpers
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> transformConditions(List<Map<String, Object>> input) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> c : input) {
            out.add(transformCondition(c));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static String getExistingStateId(String name, Map<String, Object> dto) {
        for (Map<String, Object> st : (List<Map<String, Object>>) dto.get("states")) {
            if (name.equals(st.get("name"))) {
                return (String) st.get("id");
            }
        }
        return null;
    }

    // Chunk 5 (corrected): Externalized criteria builders
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> generateExtCriteriaParams(Map<String, Object> crit) {
        List<Map<String, Object>> params = new ArrayList<>();
        params.add(Map.of(
                "persisted", true,
                "owner", crit.get("owner"),
                "id", generateId(),
                "name", "Tags for filtering calculation nodes (separated by ',' or ';')",
                "creationDate", currentTimestamp(),
                "valueType", "STRING",
                "value", Map.of("@type", "String", "value", crit.get("calculation_nodes_tags"))
        ));
        params.add(Map.of(
                "persisted", true,
                "owner", crit.get("owner"),
                "id", generateId(),
                "name", "Attach entity",
                "creationDate", currentTimestamp(),
                "valueType", "STRING",
                "value", Map.of("@type", "String",
                        "value", crit.get("attach_entity").toString().toLowerCase())
        ));
        params.add(Map.of(
                "persisted", true,
                "owner", crit.get("owner"),
                "id", generateId(),
                "name", "Calculation response timeout (ms)",
                "creationDate", currentTimestamp(),
                "valueType", "INTEGER",
                "value", Map.of("@type", "String",
                        "value", crit.get("calculation_response_timeout_ms"))
        ));
        params.add(Map.of(
                "persisted", true,
                "owner", crit.get("owner"),
                "id", generateId(),
                "name", "Retry policy",
                "creationDate", currentTimestamp(),
                "valueType", "STRING",
                "value", Map.of("@type", "String", "value", crit.get("retry_policy"))
        ));
        return params;
    }

    private static Map<String, Object> generateExtCriteria(
            Map<String, Object> crit,
            String critId,
            List<Map<String, Object>> params,
            String className
    ) {
        Map<String, Object> criteriaDto = new HashMap<>();
        criteriaDto.put("persisted", true);
        criteriaDto.put("owner", crit.get("owner"));
        criteriaDto.put("id", critId);
        criteriaDto.put("name", crit.get("name"));
        criteriaDto.put("entityClassName", className);
        criteriaDto.put("creationDate", currentTimestamp());
        criteriaDto.put("description", crit.get("description"));

        Map<String, Object> condition = new HashMap<>();
        condition.put("@bean", "com.cyoda.core.conditions.GroupCondition");
        condition.put("operator", "AND");
        condition.put("conditions", new ArrayList<Map<String, Object>>());
        criteriaDto.put("condition", condition);

        criteriaDto.put("aliasDefs", new ArrayList<>());
        criteriaDto.put("parameters", params);
        criteriaDto.put("criteriaChecker", "ExternalizedCriteriaChecker");
        criteriaDto.put("user", "CYODA");

        return criteriaDto;
    }


    // Chunk 6: addNoneStateIfNotExists helper
    @SuppressWarnings("unchecked")
    private static void addNoneStateIfNotExists(
            Map<String, Object> dto,
            String className
    ) {
        List<Map<String, Object>> states = (List<Map<String, Object>>) dto.get("states");
        // Check if a state named "None" already exists
        boolean hasNone = false;
        for (Map<String, Object> st : states) {
            if ("none".equalsIgnoreCase((String) st.get("name"))) {
                hasNone = true;
                break;
            }
        }

        if (!hasNone) {
            // Add the mandatory "None" state
            Map<String, Object> noneState = new HashMap<>();
            noneState.put("persisted", true);
            noneState.put("owner", "CYODA");
            noneState.put("id", "noneState");
            noneState.put("name", "None");
            noneState.put("entityClassName", className);
            noneState.put("creationDate", currentTimestamp());
            noneState.put("description", "Initial state of the workflow.");
            states.add(noneState);

            // Optionally, add an initial transition from "noneState" to the first real state
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> transitions = (List<Map<String, Object>>) dto.get("transitions");
            // Collect all endStateIds
            Set<String> endStateIds = new HashSet<>();
            for (Map<String, Object> t : transitions) {
                endStateIds.add((String) t.get("endStateId"));
            }
            // Find a startStateId that's not any endStateId
            String firstStateId = null;
            for (Map<String, Object> t : transitions) {
                String startId = (String) t.get("startStateId");
                if (!endStateIds.contains(startId)) {
                    firstStateId = startId;
                    break;
                }
            }
            if (firstStateId != null) {
                String initTransId = generateId();
                Map<String, Object> initTrans = new HashMap<>();
                initTrans.put("persisted", true);
                initTrans.put("owner", "CYODA");
                initTrans.put("id", initTransId);
                initTrans.put("name", "initial_transition");
                initTrans.put("entityClassName", className);
                initTrans.put("creationDate", currentTimestamp());
                initTrans.put("description", "Initial transition from None state.");
                initTrans.put("startStateId", "noneState");
                initTrans.put("endStateId", firstStateId);
                initTrans.put("workflowId", ((Map<String, Object>) ((List<?>) dto.get("workflow")).get(0)).get("id"));
                initTrans.put("criteriaIds", new ArrayList<>());
                initTrans.put("endProcessesIds", new ArrayList<>());
                initTrans.put("active", true);
                initTrans.put("automated", true);
                initTrans.put("logActivity", false);

                transitions.add(initTrans);
                // Also register this transition in the workflow's transitionIds
                @SuppressWarnings("unchecked")
                List<String> wfTransIds = (List<String>) ((Map<String, Object>) ((List<?>) dto.get("workflow")).get(0)).get("transitionIds");
                wfTransIds.add(initTransId);
            }
        }
    }

    // Chunk 7: parseAiWorkflowToDto (intro & workflow criteria)
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseAiWorkflowToDto(
            Map<String, Object> inputWorkflow,
            String className
    ) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("@bean", "com.cyoda.core.model.stateMachine.dto.FullWorkflowContainerDto");
        dto.put("workflow", new ArrayList<>());
        dto.put("transitions", new ArrayList<>());
        dto.put("criterias", new ArrayList<>());
        dto.put("processes", new ArrayList<>());
        dto.put("states", new ArrayList<>());
        dto.put("processParams", new ArrayList<>());

        // Build the main workflow object
        String workflowId = generateId();
        Map<String, Object> wf = new HashMap<>();
        wf.put("persisted", true);
        wf.put("owner", "CYODA");
        wf.put("id", workflowId);
        wf.put("name", inputWorkflow.get("name"));
        wf.put("entityClassName", className);
        wf.put("creationDate", currentTimestamp());
        wf.put("description", inputWorkflow.getOrDefault("description", ""));
        wf.put("entityShortClassName", "TreeNodeEntity");
        wf.put("transitionIds", new ArrayList<String>());
        wf.put("criteriaIds", new ArrayList<String>());
        wf.put("stateIds", new ArrayList<>(List.of("noneState")));
        wf.put("active", true);
        wf.put("useDecisionTree", false);
        wf.put("decisionTrees", new ArrayList<>());
        wf.put("metaData", Map.of("documentLink", ""));

        ((List<Object>) dto.get("workflow")).add(wf);

        // Workflow externalized criteria
        List<String> wfCritIds = new ArrayList<>();
        Map<String, Object> wc = (Map<String, Object>) inputWorkflow.get("workflow_criteria");
        for (Map<String, Object> crit : (List<Map<String, Object>>) wc.get("externalized_criteria")) {
            String cid = generateId();
            wfCritIds.add(cid);
            List<Map<String, Object>> params = generateExtCriteriaParams(crit);
            ((List<Map<String, Object>>) dto.get("processParams")).addAll(params);
            ((List<Map<String, Object>>) dto.get("criterias"))
                    .add(generateExtCriteria(crit, cid, params, className));
        }

        // Workflow condition criteria
        for (Map<String, Object> crit : (List<Map<String, Object>>) wc.get("condition_criteria")) {
            String cid = generateId();
            wfCritIds.add(cid);
            Map<String, Object> cd = new HashMap<>();
            cd.put("persisted", true);
            cd.put("owner", "CYODA");
            cd.put("id", cid);
            cd.put("name", crit.get("name"));
            cd.put("entityClassName", className);
            cd.put("creationDate", currentTimestamp());
            cd.put("description", crit.get("description"));

            Map<String, Object> condBlock = (Map<String, Object>) crit.get("condition");
            cd.put("condition", Map.of(
                    "@bean", "com.cyoda.core.conditions.GroupCondition",
                    "operator", condBlock.get("group_condition_operator"),
                    "conditions", transformConditions(
                            (List<Map<String, Object>>) condBlock.get("conditions")
                    )
            ));

            cd.put("aliasDefs", new ArrayList<>());
            cd.put("parameters", new ArrayList<>());
            cd.put("criteriaChecker", "ConditionCriteriaChecker");
            cd.put("user", "CYODA");

            ((List<Map<String, Object>>) dto.get("criterias")).add(cd);
        }

        ((List<String>) wf.get("criteriaIds")).addAll(wfCritIds);

        // Chunk 9: parseAiWorkflowToDto (finalize & return)
        addNoneStateIfNotExists(dto, className);
        return dto;
    }

    // Overload using default entity class
    public static Map<String, Object> parseAiWorkflowToDto(Map<String, Object> inputWorkflow) {
        return parseAiWorkflowToDto(inputWorkflow, TREE_NODE_ENTITY);
    }

}