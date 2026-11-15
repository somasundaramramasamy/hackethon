package com.cognizant.backend.llm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the JSON object a model was asked to return. Models sometimes wrap it in a code fence or
 * add a sentence around it, so this takes the outermost {...} block.
 */
public final class JsonReplies {

    private JsonReplies() {
    }

    /**
     * @throws LlmException (status 502) if the reply holds no readable JSON object
     */
    public static JsonNode parseObject(JsonMapper json, String reply) {
        if (reply == null) {
            throw new LlmException(502, "The model returned no text");
        }
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new LlmException(502, "The model did not return a JSON object");
        }
        try {
            JsonNode node = json.readTree(reply.substring(start, end + 1));
            if (!node.isObject()) {
                throw new LlmException(502, "The model did not return a JSON object");
            }
            return node;
        } catch (LlmException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LlmException(502, "The model returned malformed JSON", e);
        }
    }

    /** A text field, or null if it is missing, null or blank. */
    public static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asString(null);
        return text == null || text.isBlank() ? null : text.strip();
    }
}
