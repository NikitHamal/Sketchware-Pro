package pro.sketchware.activities.ai.errors;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompileErrorParser {
    private static final String TAG = "CompileErrorParser";

    private static final Pattern ERROR_PATTERN = Pattern.compile(
            // Group 1: File path (optional)
            "((?:[A-Za-z]:)?[^\\s:]+\\.(?:java|xml|kt|gradle|properties|json|txt|png|jpg|jpeg))?" +
            // Group 2: Line number (optional)
            "(?::(\\d+))?" +
            // Group 3: Column number (optional)
            "(?::(\\d+))?" +
            // Group 4: Error message
            "\\s*:\\s*(.+)"
    );

    private static final Pattern NUMBERED_ERROR_PATTERN = Pattern.compile(
            "^\\d+\\.\\s+ERROR\\s+in\\s+([^\\s]+)\\s+\\(at\\s+line\\s+(\\d+)\\)"
    );

    private static final Pattern CARET_ERROR_PATTERN = Pattern.compile(
            "\\s*(\\^+)\\s*"
    );

    private static final Map<String, String> ERROR_TYPE_MAP = new HashMap<>();

    static {
        ERROR_TYPE_MAP.put("cannot be resolved to a type", "type_not_found");
        ERROR_TYPE_MAP.put("cannot be resolved", "symbol_not_found");
        ERROR_TYPE_MAP.put("cannot find symbol", "symbol_not_found");
        ERROR_TYPE_MAP.put("package does not exist", "package_missing");
        ERROR_TYPE_MAP.put("no element found", "xml_syntax_error");
        ERROR_TYPE_MAP.put("duplicate", "duplicate_resource");
        ERROR_TYPE_MAP.put("permission", "permission_error");
        ERROR_TYPE_MAP.put("error:", "compile_error");
        ERROR_TYPE_MAP.put("warning:", "warning");
        ERROR_TYPE_MAP.put("mainbinding", "binding_class_missing");
        ERROR_TYPE_MAP.put("binding", "binding_issue");
    }

    public static class ParsedError {
        public String filePath;
        public int lineNumber = -1;
        public int columnNumber = -1;
        public String errorMessage;
        public String errorType;
        public String fullLine;

        public ParsedError(String filePath, String errorMessage, String fullLine) {
            this.filePath = filePath;
            this.errorMessage = errorMessage;
            this.fullLine = fullLine;
            this.errorType = extractErrorType(errorMessage);
        }

        public ParsedError(String filePath, int lineNumber, int columnNumber, String errorMessage, String fullLine) {
            this(filePath, errorMessage, fullLine);
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
        }

        private String extractErrorType(String errorMessage) {
            if (errorMessage == null) return "unknown";

            String lower = errorMessage.toLowerCase();
            for (Map.Entry<String, String> entry : ERROR_TYPE_MAP.entrySet()) {
                if (lower.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }

            return "general_error";
        }

        public boolean isValidFilePath() {
            return filePath != null && !filePath.isEmpty() && new File(filePath).exists();
        }
    }

    public static class ParseResult {
        public List<ParsedError> errors = new ArrayList<>();
        public Set<String> affectedFiles = new HashSet<>();
        public String summary;
        public String projectPath;
        public boolean hasFileErrors;

        public ParseResult() {}

        public void addError(ParsedError error) {
            errors.add(error);
            if (error.filePath != null && !error.filePath.isEmpty()) {
                affectedFiles.add(error.filePath);
                hasFileErrors = true;
            }
        }

        public String getMainErrorType() {
            if (errors.isEmpty()) return "unknown";

            // Count error types
            String[] types = {"xml_syntax_error", "symbol_not_found", "package_missing", "compile_error"};
            for (String type : types) {
                for (ParsedError error : errors) {
                    if (type.equals(error.errorType)) {
                        return type;
                    }
                }
            }

            return errors.get(0).errorType;
        }
    }

    public static ParseResult parseCompileLog(String compileLog, String projectPath) {
        ParseResult result = new ParseResult();
        result.projectPath = projectPath;

        if (compileLog == null || compileLog.trim().isEmpty()) {
            result.summary = "No compile log available";
            return result;
        }

        String[] lines = compileLog.split("\n");

        parseWithContext(lines, result);

        result.summary = generateSummary(result);

        Log.d(TAG, "Parsed " + result.errors.size() + " errors from compile log");
        Log.d(TAG, "Affected files: " + result.affectedFiles.size());

        return result;
    }

    private static void parseWithContext(String[] lines, ParseResult result) {
        ParsedError currentError = null;
        StringBuilder errorDetails = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            Matcher numberedMatcher = NUMBERED_ERROR_PATTERN.matcher(line);
            if (numberedMatcher.find()) {
                if (currentError != null) {
                    currentError.errorMessage = errorDetails.toString().trim();
                    result.addError(currentError);
                }

                String filePath = numberedMatcher.group(1);
                int lineNumber = Integer.parseInt(numberedMatcher.group(2));
                currentError = new ParsedError(filePath, lineNumber, -1, "", line);
                errorDetails = new StringBuilder();

                for (int j = i + 1; j < Math.min(i + 5, lines.length); j++) {
                    String nextLine = lines[j].trim();
                    if (nextLine.isEmpty()) continue;
                    if (CARET_ERROR_PATTERN.matcher(nextLine).matches()) {
                        continue;
                    }
                    if (NUMBERED_ERROR_PATTERN.matcher(nextLine).find()) {
                        break;
                    }
                    if (errorDetails.length() > 0) errorDetails.append(" ");
                    errorDetails.append(nextLine);
                }
                continue;
            }

            ParsedError error = parseLine(line);
            if (error != null) {
                if (currentError != null) {
                    currentError.errorMessage = errorDetails.toString().trim();
                    result.addError(currentError);
                    currentError = null;
                    errorDetails = new StringBuilder();
                }
                result.addError(error);
            }
        }

        if (currentError != null) {
            currentError.errorMessage = errorDetails.toString().trim();
            result.addError(currentError);
        }
    }

    private static ParsedError parseLine(String line) {
        Matcher matcher = ERROR_PATTERN.matcher(line);
        if (matcher.find()) {
            String filePath = matcher.group(1);
            String lineNumStr = matcher.group(2);
            String colNumStr = matcher.group(3);
            String errorMessage = matcher.group(4);

            int lineNumber = -1;
            int columnNumber = -1;

            try {
                if (lineNumStr != null) lineNumber = Integer.parseInt(lineNumStr);
                if (colNumStr != null) columnNumber = Integer.parseInt(colNumStr);
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }

            return new ParsedError(filePath, lineNumber, columnNumber, errorMessage, line);
        }

        return null;
    }

    private static String generateSummary(ParseResult result) {
        if (result.errors.isEmpty()) {
            return "No errors found in compile log";
        }

        int errorCount = result.errors.size();
        int fileCount = result.affectedFiles.size();
        String mainErrorType = result.getMainErrorType();

        StringBuilder summary = new StringBuilder();
        summary.append("Found ").append(errorCount).append(" error(s)");

        if (fileCount > 0) {
            summary.append(" affecting ").append(fileCount).append(" file(s)");
        }

        summary.append(". Main issue: ").append(mainErrorType.replace("_", " "));

        return summary.toString();
    }

    public static List<String> getUniqueFilePaths(ParseResult result) {
        return new ArrayList<>(result.affectedFiles);
    }

    public static boolean hasFixableErrors(ParseResult result) {
        for (ParsedError error : result.errors) {
            String type = error.errorType;
            if ("xml_syntax_error".equals(type) ||
                    "symbol_not_found".equals(type) ||
                    "type_not_found".equals(type) ||
                    "package_missing".equals(type) ||
                    "duplicate_resource".equals(type) ||
                    "binding_class_missing".equals(type) ||
                    "binding_issue".equals(type)) {
                return true;
            }
        }
        return false;
    }
}