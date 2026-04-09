package pro.sketchware.blocks.importer;

import com.github.javaparser.Position;
import com.github.javaparser.ast.Node;

import java.util.Locale;

public class JavaToBlocksDiagnostic {

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }

    private final Severity severity;
    private final String message;
    private final Integer line;
    private final Integer column;

    public JavaToBlocksDiagnostic(Severity severity, String message, Integer line, Integer column) {
        this.severity = severity;
        this.message = message;
        this.line = line;
        this.column = column;
    }

    public static JavaToBlocksDiagnostic info(String message) {
        return new JavaToBlocksDiagnostic(Severity.INFO, message, null, null);
    }

    public static JavaToBlocksDiagnostic warning(String message) {
        return new JavaToBlocksDiagnostic(Severity.WARNING, message, null, null);
    }

    public static JavaToBlocksDiagnostic warning(Node node, String message) {
        return fromNode(Severity.WARNING, node, message);
    }

    public static JavaToBlocksDiagnostic error(String message) {
        return new JavaToBlocksDiagnostic(Severity.ERROR, message, null, null);
    }

    public static JavaToBlocksDiagnostic error(Node node, String message) {
        return fromNode(Severity.ERROR, node, message);
    }

    private static JavaToBlocksDiagnostic fromNode(Severity severity, Node node, String message) {
        Integer line = null;
        Integer column = null;
        if (node != null && node.getRange().isPresent()) {
            Position begin = node.getRange().get().begin;
            line = begin.line;
            column = begin.column;
        }
        return new JavaToBlocksDiagnostic(severity, message, line, column);
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public Integer getLine() {
        return line;
    }

    public Integer getColumn() {
        return column;
    }

    public String formatForDisplay() {
        if (line == null || column == null) {
            return severity.name() + ": " + message;
        }
        return String.format(Locale.US, "%s (line %d, col %d): %s", severity.name(), line, column, message);
    }
}
