package com.example.tradeLedger.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared JSON plumbing for the control-plane services.
 *
 * The strategy module stores four jsonb columns as text on the entity side
 * (rule_tree, param_schema, validation, signal_params / exec_params) and hands
 * them to callers as maps. Doing that conversion in one place keeps the services
 * free of repeated try/catch blocks and keeps the failure behaviour consistent.
 */
@Component
public class JsonSupport {

    private final ObjectMapper objectMapper;

    public JsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectMapper mapper() {
        return objectMapper;
    }

    /** Unparseable or absent JSON reads as an empty map rather than failing a GET. */
    public Map<String, Object> toMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON payload", e);
        }
    }

    public JsonNode toNode(Object value) {
        return objectMapper.valueToTree(value);
    }

    /** Parses stored JSON; returns null when the text is absent or malformed. */
    public JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
