package com.java_template.common.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedGenerator;

public class WorkflowConverter {
    public static final String TREE_NODE_ENTITY =
            "com.cyoda.tdb.model.treenode.TreeNodeEntity";

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private static final TimeBasedGenerator timeBasedGenerator = Generators.timeBasedGenerator();

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

    public static String parseAiWorkflowToDtoJson(String inputJson) throws JsonProcessingException {
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

    private static String generateId() {
        return timeBasedGenerator.generate().toString();
    }

    private static String currentTimestamp() {
        return ZonedDateTime.now().format(TS_FORMAT);
    }

    private static Object parseValue(Object v) {
        if (v instanceof Boolean || v instanceof Number) return v;
        try {
            return Double.valueOf(v.toString());
        } catch (Exception e) {
            return v;
        }
    }

    private static Map<String, Object> transformCondition(Map<String, Object> cond) {
        if (cond.containsKey("conditions")) {
            List<Map<String, Object>> subs = new ArrayList<>();
            for (Map<String, Object> sub : (List<Map<String, Object>>) cond.get("conditions")) {
                subs.add(transformCondition(sub));
            }
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("@bean", "com.cyoda.core.conditions.GroupCondition");
            group.put("operator", cond.getOrDefault("group_condition_operator", "AND"));
            group.put("conditions", subs);
            return group;
        }

        // Lookup the mapping for this operation label
        String opLabel = (String) cond.get("operation");
        Map<String, String> mapping = OPERATION_MAPPING.get(opLabel);
        if (mapping == null) throw new IllegalArgumentException("Unsupported operation: " + opLabel);

        boolean isRange = mapping.get("operation").contains("BETWEEN");
        boolean isMeta = Boolean.TRUE.equals(cond.get("is_meta_field"));

        // Determine the appropriate fieldName
        String fieldName = isMeta ?
                (String) cond.get("field_name") :
                "members.[*]@com#cyoda#tdb#model#treenode#NodeInfo.value" +
                        "@com#cyoda#tdb#model#treenode#PersistedValueMaps." + cond.get("value_type") +
                        ".[$." + cond.get("field_name") + "]";

        // Build the base condition map
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("@bean", mapping.get("@bean"));
        base.put("fieldName", fieldName);
        base.put("operation", mapping.get("operation"));
        base.put("rangeField", Boolean.toString(isRange));

        // Add value if required
        Object rawVal = cond.get("value");
        if (rawVal != null && !Set.of("IS_NULL", "NOT_NULL").contains(mapping.get("operation"))) {
            base.put("value", parseValue(rawVal));
        }

        // Special handling for "does not start with"
        if ("does not start with".equalsIgnoreCase(opLabel)) {
            Map<String, Object> alt = new LinkedHashMap<>();
            alt.put("@bean", "com.cyoda.core.conditions.nonqueryable.IStartsWith");
            alt.put("fieldName", cond.get("field_name"));
            alt.put("operation", "ISTARTS_WITH");
            alt.put("rangeField", "false");
            alt.put("value", parseValue(rawVal));
            base.put("iStartsWith", alt);
        }

        return base;
    }

    private static List<Map<String, Object>> transformConditions(List<Map<String, Object>> input) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> c : input) {
            out.add(transformCondition(c));
        }
        return out;
    }

    private static String getExistingStateId(String name, Map<String, Object> dto) {
        for (Map<String, Object> st : (List<Map<String, Object>>) dto.get("states")) {
            if (name.equals(st.get("name"))) return (String) st.get("id");
        }
        return null;
    }

    public static Map<String, Object> parseAiWorkflowToDto(Map<String, Object> inputWorkflow) {
        return parseAiWorkflowToDto(inputWorkflow, TREE_NODE_ENTITY);
    }

    public static Map<String, Object> parseAiWorkflowToDto(Map<String, Object> inputWorkflow, String className) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("@bean", "com.cyoda.core.model.stateMachine.dto.FullWorkflowContainerDto");
        dto.put("workflow", new ArrayList<>());
        dto.put("transitions", new ArrayList<>());
        dto.put("criterias", new ArrayList<>());
        dto.put("processes", new ArrayList<>());
        dto.put("states", new ArrayList<>());
        dto.put("processParams", new ArrayList<>());

        // Build the main workflow object
        String workflowId = generateId();
        Map<String, Object> wf = new LinkedHashMap<>();
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

        // Workflow criteria
        Map<String, Object> wc = (Map<String, Object>) inputWorkflow.get("workflow_criteria");

        // Workflow externalized criteria
        for (Map<String, Object> extCrit : (List<Map<String, Object>>) wc.get("externalized_criteria")) {
            String ecid = generateId();
            List<Map<String, Object>> params = generateParams(extCrit);
            ((List<Map<String, Object>>) dto.get("processParams")).addAll(params);
            ((List<Map<String, Object>>) dto.get("criterias"))
                    .add(generateExtCriteria(extCrit, ecid, params, className));
            ((List<String>) wf.get("criteriaIds")).add(ecid);
        }

        // Workflow condition criteria
        for (Map<String, Object> condCrit : (List<Map<String, Object>>) wc.get("condition_criteria")) {
            String ccid = generateId();
            Map<String, Object> cd = new LinkedHashMap<>();
            cd.put("persisted", true);
            cd.put("owner", "CYODA");
            cd.put("id", ccid);
            cd.put("name", condCrit.get("name"));
            cd.put("entityClassName", className);
            cd.put("creationDate", currentTimestamp());
            cd.put("description", condCrit.get("description"));

            Map<String, Object> condBlock = (Map<String, Object>) condCrit.get("condition");
            LinkedHashMap<String, Object> condition = new LinkedHashMap<>();
            condition.put("@bean", "com.cyoda.core.conditions.GroupCondition");
            condition.put("operator", condBlock.get("group_condition_operator"));
            condition.put("conditions", transformConditions(
                    (List<Map<String, Object>>) condBlock.get("conditions")
            ));
            cd.put("condition", condition);
            cd.put("aliasDefs", new ArrayList<>());
            cd.put("parameters", new ArrayList<>());
            cd.put("criteriaChecker", "ConditionCriteriaChecker");
            cd.put("user", "CYODA");
            ((List<Map<String, Object>>) dto.get("criterias")).add(cd);
            ((List<String>) wf.get("criteriaIds")).add(ccid);
        }

        // Process transitions
        List<Map<String, Object>> transitions = (List<Map<String, Object>>) inputWorkflow.get("transitions");
        for (Map<String, Object> t : transitions) {
            String tid = generateId();

            // Transition DTO
            Map<String, Object> transDto = new LinkedHashMap<>();
            transDto.put("persisted", true);
            transDto.put("owner", "CYODA");
            transDto.put("id", tid);
            transDto.put("name", t.get("name"));
            transDto.put("description", t.get("description"));
            transDto.put("startStateId", "");
            transDto.put("endStateId", "");
            transDto.put("workflowId", workflowId);
            transDto.put("creationDate", currentTimestamp());
            transDto.put("entityClassName", className);
            transDto.put("criteriaIds", new ArrayList<>());
            transDto.put("endProcessesIds", new ArrayList<>());
            transDto.put("active", true);
            transDto.put("automated", Boolean.TRUE.equals(t.get("automated")));
            transDto.put("logActivity", false);

            ((List<Map<String, Object>>) dto.get("transitions")).add(transDto);
            ((List<String>) wf.get("transitionIds")).add(tid);

            // Process states
            String startId = "noneState";
            String startName = (String) t.get("start_state");
            String endName = (String) t.get("end_state");

            if (!startName.equalsIgnoreCase("none")) {
                startId = getExistingStateId(startName, dto);
                if (startId == null) {
                    startId = generateId();
                    Map<String, Object> stateDto = new LinkedHashMap<>();
                    stateDto.put("persisted", true);
                    stateDto.put("owner", "CYODA");
                    stateDto.put("id", startId);
                    stateDto.put("name", startName);
                    stateDto.put("entityClassName", className);
                    stateDto.put("creationDate", currentTimestamp());
                    stateDto.put("description", t.getOrDefault("start_state_description", ""));
                    ((List<Map<String, Object>>) dto.get("states")).add(stateDto);
                }
            } else {
                Map<String, Object> stateDto = new LinkedHashMap<>();
                stateDto.put("persisted", true);
                stateDto.put("owner", "CYODA");
                stateDto.put("id", startId);
                stateDto.put("name", startName);
                stateDto.put("entityClassName", className);
                stateDto.put("creationDate", currentTimestamp());
                stateDto.put("description", t.getOrDefault("start_state_description", ""));
                ((List<Map<String, Object>>) dto.get("states")).add(stateDto);
            }
            transDto.put("startStateId", startId);

            String endId = getExistingStateId(endName, dto);
            if (endId == null) {
                endId = generateId();
                Map<String, Object> stateDto = new LinkedHashMap<>();
                stateDto.put("persisted", true);
                stateDto.put("owner", "CYODA");
                stateDto.put("id", endId);
                stateDto.put("name", endName);
                stateDto.put("entityClassName", className);
                stateDto.put("creationDate", currentTimestamp());
                stateDto.put("description", t.getOrDefault("end_state_description", ""));
                ((List<Map<String, Object>>) dto.get("states")).add(stateDto);
            }
            transDto.put("endStateId", endId);

            //Process transition's processors
            Map<String, Object> processors = (Map<String, Object>) t.get("processes");

            //Process transition's externalized_processors
            List<Map<String, Object>> extProcessors = (List<Map<String, Object>>) processors.get("externalized_processors");
            for (Map<String, Object> process : extProcessors) {
                String processId = generateId();
                Map<String, Object> endProcessId = new LinkedHashMap<>();
                endProcessId.put("persisted", true);
                endProcessId.put("persistedId", processId);
                endProcessId.put("runtimeId", 0);
                ((List<Map<String, Object>>) transDto.get("endProcessesIds")).add(endProcessId);

                List<Map<String, Object>> params = generateParams(process);
                ((List<Map<String, Object>>) dto.get("processParams")).addAll(params);

                // Externalized_processor DTO
                Map<String, Object> processDto = new LinkedHashMap<>();
                processDto.put("persisted", true);
                processDto.put("owner", "CYODA");

                Map<String, Object> processIdDto = new LinkedHashMap<>();
                processIdDto.put("@bean", "com.cyoda.core.model.stateMachine.dto.ProcessIdDto");
                processIdDto.put("persisted", true);
                processIdDto.put("persistedId", processId);
                processIdDto.put("runtimeId", 0);

                processDto.put("id", processIdDto);
                processDto.put("name", process.get("name"));
                processDto.put("entityClassName", className);
                processDto.put("creationDate", currentTimestamp());
                processDto.put("description", process.getOrDefault("description", ""));
                processDto.put("processorClassName", "net.cyoda.saas.externalize.processor.ExternalizedProcessor");
                processDto.put("parameters", params);
                processDto.put("fields", new ArrayList<>());
                processDto.put("syncProcess", process.get("sync_process"));
                processDto.put("newTransactionForAsync", process.get("new_transaction_for_async"));
                processDto.put("noneTransactionalForAsync", process.get("none_transactional_for_async"));
                processDto.put("isTemplate", false);
                processDto.put("criteriaIds", new ArrayList<>());
                processDto.put("user", "CYODA");
                ((List<Map<String, Object>>) dto.get("processes")).add(processDto);
            }
        }
        addNoneStateIfNotExists(dto, className);
        return dto;
    }

    private static List<Map<String, Object>> generateParams(Map<String, Object> criteriaOrProcessor) {
        List<Map<String, Object>> processedParams = new ArrayList<>();

        Map<String, Object> param1 = new LinkedHashMap<>();
        param1.put("persisted", true);
        param1.put("owner", "CYODA");
        param1.put("id", generateId());
        param1.put("name", "Tags for filtering calculation nodes (separated by ',' or ';')");
        param1.put("creationDate", currentTimestamp());
        param1.put("valueType", "STRING");

        Map<String, Object> value1 = new LinkedHashMap<>();
        value1.put("@type", "String");
        value1.put("value", criteriaOrProcessor.get("calculation_nodes_tags"));
        param1.put("value", value1);

        processedParams.add(param1);

// -------- Second parameter --------
        Map<String, Object> param2 = new LinkedHashMap<>();
        param2.put("persisted", true);
        param2.put("owner", "CYODA");
        param2.put("id", generateId());
        param2.put("name", "Attach entity");
        param2.put("creationDate", currentTimestamp());
        param2.put("valueType", "STRING");

        Map<String, Object> value2 = new LinkedHashMap<>();
        value2.put("@type", "String");
        value2.put("value", String.valueOf(criteriaOrProcessor.get("attach_entity")).toLowerCase());
        param2.put("value", value2);

        processedParams.add(param2);

// -------- Third parameter --------
        Map<String, Object> param3 = new LinkedHashMap<>();
        param3.put("persisted", true);
        param3.put("owner", "CYODA");
        param3.put("id", generateId());
        param3.put("name", "Calculation response timeout (ms)");
        param3.put("creationDate", currentTimestamp());
        param3.put("valueType", "INTEGER");

        Map<String, Object> value3 = new LinkedHashMap<>();
        value3.put("@type", "String");
        value3.put("value", String.valueOf(criteriaOrProcessor.get("calculation_response_timeout_ms")));
        param3.put("value", value3);

        processedParams.add(param3);

// -------- Fourth parameter --------
        Map<String, Object> param4 = new LinkedHashMap<>();
        param4.put("persisted", true);
        param4.put("owner", "CYODA");
        param4.put("id", generateId());
        param4.put("name", "Retry policy");
        param4.put("creationDate", currentTimestamp());
        param4.put("valueType", "STRING");

        Map<String, Object> value4 = new LinkedHashMap<>();
        value4.put("@type", "String");
        value4.put("value", criteriaOrProcessor.get("retry_policy"));
        param4.put("value", value4);

        processedParams.add(param4);
        return processedParams;
    }

    private static Map<String, Object> generateExtCriteria(
            Map<String, Object> crit,
            String critId,
            List<Map<String, Object>> params,
            String className
    ) {
        Map<String, Object> criteriaDto = new LinkedHashMap<>();
        criteriaDto.put("persisted", true);
        criteriaDto.put("owner", crit.get("owner"));
        criteriaDto.put("id", critId);
        criteriaDto.put("name", crit.get("name"));
        criteriaDto.put("entityClassName", className);
        criteriaDto.put("creationDate", currentTimestamp());
        criteriaDto.put("description", crit.get("description"));

        Map<String, Object> condition = new LinkedHashMap<>();
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

    private static void addNoneStateIfNotExists(Map<String, Object> dto, String className) {
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
            Map<String, Object> noneState = new LinkedHashMap<>();
            noneState.put("persisted", true);
            noneState.put("owner", "CYODA");
            noneState.put("id", "noneState");
            noneState.put("name", "None");
            noneState.put("entityClassName", className);
            noneState.put("creationDate", currentTimestamp());
            noneState.put("description", "Initial state of the workflow.");
            states.add(noneState);

            // Optionally, add an initial transition from "noneState" to the first real state
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
                Map<String, Object> initTrans = new LinkedHashMap<>();
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
                List<String> wfTransIds = (List<String>) ((Map<String, Object>) ((List<?>) dto.get("workflow")).get(0)).get("transitionIds");
                wfTransIds.add(initTransId);
            }
        }
    }
}