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

        sb.append("=== BROKEN LAYOUT ===\n")
          .append("```xml\n").append(safe(brokenXml)).append("\n```\n\n")
          .append("Fix:\n")
          .append("• Close all unclosed tags\n")
          .append("• Fix malformed attribute syntax\n")
          .append("• Add missing required attributes (layout_width, layout_height)\n")
          .append("• Remove unknown/unsupported attributes\n")
          .append("• Keep all original IDs and view structure intact\n")
          .append(GUARDRAILS)
          .append("\nOUTPUT (fixed XML only):");
        return sb.toString();
    }

    // ── Tool: OPTIMIZE ────────────────────────────────────────────────────────

    /**
     * Builds a prompt to optimize a layout for performance and best practices.
     *
     * @param xml          the layout to optimize
     * @param activityName screen name
     * @return full prompt string
     */
    public static String buildOptimizePrompt(String xml, String activityName) {
        return "You are an expert Android performance engineer.\n"
                + "Optimize the following layout for maximum performance and best practices.\n\n"
                + "Activity: " + safe(activityName) + "\n\n"
                + "=== CURRENT LAYOUT ===\n"
                + "```xml\n" + safe(xml) + "\n```\n\n"
                + "Optimizations to apply:\n"
                + "• Flatten view hierarchy (reduce nesting depth)\n"
                + "• Replace nested LinearLayouts with ConstraintLayout where beneficial\n"
                + "• Remove redundant wrapper layouts\n"
                + "• Use merge tag for root if this is an included layout\n"
                + "• Ensure all IDs are unique\n"
                + "• Preserve all functionality — do NOT remove views\n"
                + GUARDRAILS
                + "\nOUTPUT (optimized XML only):";
    }

    // ── Tool: RTL_PROMPT (for AI-assisted RTL advice only) ────────────────────

    /**
     * Builds a prompt for AI-based RTL conversion advice.
     * Note: Actual RTL attribute replacement is done by {@link RTLConverter} (pure logic).
     * This prompt is used only when AI explanation/review of RTL issues is requested.
     *
     * @param xml the layout to review for RTL issues
     * @return full prompt string
     */
    public static String buildRtlReviewPrompt(String xml) {
        return "You are an Android RTL (right-to-left) accessibility expert.\n"
                + "Review the following layout for RTL compatibility issues and list them clearly.\n\n"
                + "```xml\n" + safe(xml) + "\n```\n\n"
                + "Report:\n"
                + "• Attributes using 'left'/'right' that should be 'start'/'end'\n"
                + "• Missing layoutDirection or textDirection attributes\n"
                + "• Gravity values that break RTL\n"
                + "• Do NOT output modified XML — output a numbered list of issues only\n"
                + "\nOUTPUT (numbered issue list only):";
    }

    // ── Tool: EXPLAIN ──────────────────────────────────────────────────────────

    /**
     * Builds a prompt to explain what a layout does in plain English/Arabic.
     *
     * @param xml      the layout XML
     * @param language "English" or "Arabic"
     * @return full prompt string
     */
    public static String buildExplainPrompt(String xml, String language) {
        boolean arabic = "Arabic".equalsIgnoreCase(language);
        return (arabic
                ? "أنت خبير أندرويد. اشرح تخطيط XML التالي بالعربية بشكل واضح وبسيط.\n\n"
                : "You are an Android expert. Explain the following XML layout clearly and concisely.\n\n")
                + "```xml\n" + safe(xml) + "\n```\n\n"
                + (arabic
                   ? "اشرح: ما الشاشة التي يمثلها، العناصر المرئية، وترتيبها. لا تخرج XML."
                   : "Explain: what screen it represents, the visual elements, and their layout structure. Do NOT output XML.")
                + "\nOUTPUT:";
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
