package pro.sketchware.blocks.importer;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

public final class JavaToBlocksMetadataStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DIRECTORY = "java_to_blocks";

    private JavaToBlocksMetadataStore() {
    }

    public static void persist(Context context, String scId, String javaName, String eventKey,
                               String originalSource, JavaToBlocksConversionResult result) {
        File outputFile = resolvePath(scId, javaName, eventKey);
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            FileUtil.makeDir(parent.getAbsolutePath());
        }

        Record record = new Record();
        record.schemaVersion = 1;
        record.javaName = javaName;
        record.eventKey = eventKey;
        record.originalSource = originalSource;
        record.normalizedSource = result.getNormalizedSource();
        record.sourceShape = result.getSourceShape();
        record.supportedStatements = result.getSupportedStatements();
        record.opaqueStatements = result.getOpaqueStatements();
        record.supportedExpressions = result.getSupportedExpressions();
        record.opaqueExpressions = result.getOpaqueExpressions();
        record.blockCount = result.getBlocks().size();
        record.diagnostics = new ArrayList<>();
        for (JavaToBlocksDiagnostic diagnostic : result.getDiagnostics()) {
            DiagnosticRecord dr = new DiagnosticRecord();
            dr.severity = diagnostic.getSeverity().name();
            dr.message = diagnostic.getMessage();
            dr.line = diagnostic.getLine();
            dr.column = diagnostic.getColumn();
            record.diagnostics.add(dr);
        }

        FileUtil.writeFile(outputFile.getAbsolutePath(), GSON.toJson(record));
    }

    public static File resolvePath(String scId, String javaName, String eventKey) {
        String safeJava = sanitize(javaName);
        String safeEvent = sanitize(eventKey);
        return new File(new File(new File(new FilePathUtil().getPathImport(scId)), DIRECTORY),
                safeJava + File.separator + safeEvent + ".json");
    }

    private static String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return "unknown";
        }
        return input.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static final class Record {
        int schemaVersion;
        String javaName;
        String eventKey;
        String originalSource;
        String normalizedSource;
        String sourceShape;
        int supportedStatements;
        int opaqueStatements;
        int supportedExpressions;
        int opaqueExpressions;
        int blockCount;
        List<DiagnosticRecord> diagnostics;
    }

    private static final class DiagnosticRecord {
        String severity;
        String message;
        Integer line;
        Integer column;
    }
}
