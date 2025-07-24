package pro.sketchware.activities.ai.errors;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompileErrorParser {
    private static final String TAG = "CompileErrorParser";
    
    // Pattern to match file paths in error logs
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
        "(/[^\\s:]+\\.(java|xml|kt|gradle|properties|json|txt|png|jpg|jpeg))" +
        "(?::(\\d+))?(?::(\\d+))?\\s*:\\s*(.+)"
    );
    
    // Alternative pattern for different error formats
    private static final Pattern ALTERNATIVE_PATH_PATTERN = Pattern.compile(
        "([A-Za-z]:[^\\s:]+\\.(java|xml|kt|gradle|properties|json|txt|png|jpg|jpeg))" +
        "(?::(\\d+))?(?::(\\d+))?\\s*:\\s*(.+)"
    );
    
    // Pattern for numbered error format: "1. ERROR in /path/file.java (at line 32)"
    private static final Pattern NUMBERED_ERROR_PATTERN = Pattern.compile(
        "^\\d+\\.\\s+ERROR\\s+in\\s+([^\\s]+)\\s+\\(at\\s+line\\s+(\\d+)\\)"
    );
    
    // Pattern for error with caret markers
    private static final Pattern CARET_ERROR_PATTERN = Pattern.compile(
        "\\s*(\\^+)\\s*"
    );
    
    // Pattern for general error lines without file paths
    private static final Pattern GENERAL_ERROR_PATTERN = Pattern.compile(
        "(?i)(error|exception|failed|cannot\\s+resolve|cannot\\s+find|duplicate|missing)"
    );
    
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
            if (lower.contains("cannot be resolved to a type")) return "type_not_found";
            if (lower.contains("cannot be resolved")) return "symbol_not_found";
            if (lower.contains("cannot find symbol")) return "symbol_not_found";
            if (lower.contains("package does not exist")) return "package_missing";
            if (lower.contains("no element found")) return "xml_syntax_error";
            if (lower.contains("duplicate")) return "duplicate_resource";
            if (lower.contains("permission")) return "permission_error";
            if (lower.contains("error:")) return "compile_error";
            if (lower.contains("warning:")) return "warning";
            if (lower.contains("mainbinding")) return "binding_class_missing";
            if (lower.contains("binding")) return "binding_issue";
            
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
    
    public static ParseResult parseCompileLog(String compileLog, String projectId) {
        ParseResult result = new ParseResult();
        
        if (compileLog == null || compileLog.trim().isEmpty()) {
            result.summary = "No compile log available";
            return result;
        }
        
        String[] lines = compileLog.split("\n");
        
        // Try to extract project path
        result.projectPath = extractProjectPath(compileLog, projectId);
        
        // Parse with context awareness for multi-line errors
        parseWithContext(lines, result);
        
        // Generate summary
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
            
            // Check for numbered error format first
            Matcher numberedMatcher = NUMBERED_ERROR_PATTERN.matcher(line);
            if (numberedMatcher.find()) {
                // Finalize previous error
                if (currentError != null) {
                    currentError.errorMessage = errorDetails.toString().trim();
                    result.addError(currentError);
                }
                
                // Start new error
                String filePath = numberedMatcher.group(1);
                int lineNumber = Integer.parseInt(numberedMatcher.group(2));
                currentError = new ParsedError(filePath, lineNumber, -1, "", line);
                errorDetails = new StringBuilder();
                
                // Look ahead for error details
                for (int j = i + 1; j < Math.min(i + 5, lines.length); j++) {
                    String nextLine = lines[j].trim();
                    if (nextLine.isEmpty()) continue;
                    
                    // Check if it's a caret line (^^^)
                    if (CARET_ERROR_PATTERN.matcher(nextLine).matches()) {
                        continue; // Skip caret lines
                    }
                    
                    // Check if it's another numbered error
                    if (NUMBERED_ERROR_PATTERN.matcher(nextLine).find()) {
                        break; // Stop at next error
                    }
                    
                    // Add error details
                    if (errorDetails.length() > 0) errorDetails.append(" ");
                    errorDetails.append(nextLine);
                }
                continue;
            }
            
            // Try standard parsing for other formats
            ParsedError error = parseLine(line);
            if (error != null) {
                // If we have an ongoing numbered error, finalize it first
                if (currentError != null) {
                    currentError.errorMessage = errorDetails.toString().trim();
                    result.addError(currentError);
                    currentError = null;
                    errorDetails = new StringBuilder();
                }
                result.addError(error);
            }
        }
        
        // Finalize last error if exists
        if (currentError != null) {
            currentError.errorMessage = errorDetails.toString().trim();
            result.addError(currentError);
        }
    }
    
    private static ParsedError parseLine(String line) {
        // Try primary pattern
        Matcher matcher = FILE_PATH_PATTERN.matcher(line);
        if (matcher.find()) {
            String filePath = matcher.group(1);
            String lineNumStr = matcher.group(3);
            String colNumStr = matcher.group(4);
            String errorMessage = matcher.group(5);
            
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
        
        // Try alternative pattern
        matcher = ALTERNATIVE_PATH_PATTERN.matcher(line);
        if (matcher.find()) {
            String filePath = matcher.group(1);
            String lineNumStr = matcher.group(3);
            String colNumStr = matcher.group(4);
            String errorMessage = matcher.group(5);
            
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
        
        // Check if line contains error indicators without file path
        Matcher generalMatcher = GENERAL_ERROR_PATTERN.matcher(line);
        if (generalMatcher.find()) {
            return new ParsedError(null, line, line);
        }
        
        return null;
    }
    
    private static String extractProjectPath(String compileLog, String projectId) {
        // Try to extract project path from the log
        String[] lines = compileLog.split("\n");
        for (String line : lines) {
            if (line.contains("/data/" + projectId + "/")) {
                int index = line.indexOf("/data/" + projectId + "/");
                if (index != -1) {
                    return "/storage/emulated/0/.sketchware/data/" + projectId;
                }
            }
        }
        
        // Fallback to standard path
        return "/storage/emulated/0/.sketchware/data/" + projectId;
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
        // Check if errors are types that AI can potentially fix
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