package pro.sketchware.ai.engine;

import pro.sketchware.ai.prompts.SystemPrompts;

/**
 * PromptBuilder — builds per-tool, context-aware prompts for the AI engine.
 *
 * <p>Delegates to {@link SystemPrompts} for all prompt text. This class
 * retains its own API for backward compatibility but all prompt content
 * is now centralized in SystemPrompts.
 */
public final class PromptBuilder {

    private PromptBuilder() {}

    // ── Shared guardrails (delegated to SystemPrompts) ─────────────────────────

    private static final String GUARDRAILS = SystemPrompts.GUARDRAILS;

    // ── Tool: GENERATE_UI ──────────────────────────────────────────────────────

    public static String buildGenerateUiPrompt(
            String userRequest, String activityName, String projectPkg) {
        return SystemPrompts.buildGenerateUiPrompt(userRequest, activityName, projectPkg);
    }

    // ── Tool: MODIFY_UI ────────────────────────────────────────────────────────

    public static String buildModifyUiPrompt(
            String userRequest, String existingXml, String activityName) {
        return SystemPrompts.buildModifyUiPrompt(userRequest, existingXml, activityName);
    }

    // ── Tool: FIX_CODE ─────────────────────────────────────────────────────────

    public static String buildFixPrompt(String brokenXml, String errorReport) {
        return SystemPrompts.buildFixPrompt(brokenXml, errorReport);
    }

    // ── Tool: OPTIMIZE ────────────────────────────────────────────────────────

    public static String buildOptimizePrompt(String xml, String activityName) {
        return SystemPrompts.buildOptimizePrompt(xml, activityName);
    }

    // ── Tool: RTL_PROMPT (for AI-assisted RTL advice only) ────────────────────

    public static String buildRtlReviewPrompt(String xml) {
        return SystemPrompts.buildRtlReviewPrompt(xml);
    }

    // ── Tool: EXPLAIN ──────────────────────────────────────────────────────────

    public static String buildExplainPrompt(String xml, String language) {
        return SystemPrompts.buildExplainPrompt(xml, language);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Null-safe string: replaces null with empty string. */
    private static String safe(String s) {
        return s != null ? s : "";
    }

    /**
     * Extracts the XML block from an AI response.
     * The model should always output {@code ```xml … ```} but this handles edge cases:
     * raw XML, missing fences, extra text around the block.
     *
     * @param aiResponse the raw AI text response
     * @return extracted XML string, or the raw response if no fence found
     */
    public static String extractXmlFromResponse(String aiResponse) {
        if (aiResponse == null || aiResponse.isEmpty()) return "";

        // Primary: ```xml … ``` fence
        int start = aiResponse.indexOf("```xml");
        if (start >= 0) {
            start += 6;
            // skip optional newline right after ```xml
            if (start < aiResponse.length() && aiResponse.charAt(start) == '\n') start++;
            int end = aiResponse.indexOf("```", start);
            if (end > start) return aiResponse.substring(start, end).trim();
        }

        // Secondary: ``` … ``` fence (no language marker)
        start = aiResponse.indexOf("```");
        if (start >= 0) {
            start += 3;
            if (start < aiResponse.length() && aiResponse.charAt(start) == '\n') start++;
            int end = aiResponse.indexOf("```", start);
            if (end > start) return aiResponse.substring(start, end).trim();
        }

        // Tertiary: response starts with a valid XML tag
        String trimmed = aiResponse.trim();
        if (trimmed.startsWith("<?xml") || trimmed.startsWith("<LinearLayout")
                || trimmed.startsWith("<ConstraintLayout")
                || trimmed.startsWith("<RelativeLayout")
                || trimmed.startsWith("<FrameLayout")
                || trimmed.startsWith("<ScrollView")
                || trimmed.startsWith("<androidx.")) {
            return trimmed;
        }

        // Fallback: return as-is and let XMLValidator decide
        return aiResponse.trim();
    }

}
