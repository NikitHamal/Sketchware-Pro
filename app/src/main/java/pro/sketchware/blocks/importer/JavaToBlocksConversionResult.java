package pro.sketchware.blocks.importer;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JavaToBlocksConversionResult {

    private final ArrayList<BlockBean> blocks;
    private final ArrayList<JavaToBlocksDiagnostic> diagnostics;
    private final int supportedStatements;
    private final int opaqueStatements;
    private final int supportedExpressions;
    private final int opaqueExpressions;
    private final String normalizedSource;
    private final String sourceShape;

    public JavaToBlocksConversionResult(
            ArrayList<BlockBean> blocks,
            ArrayList<JavaToBlocksDiagnostic> diagnostics,
            int supportedStatements,
            int opaqueStatements,
            int supportedExpressions,
            int opaqueExpressions,
            String normalizedSource,
            String sourceShape
    ) {
        this.blocks = blocks;
        this.diagnostics = diagnostics;
        this.supportedStatements = supportedStatements;
        this.opaqueStatements = opaqueStatements;
        this.supportedExpressions = supportedExpressions;
        this.opaqueExpressions = opaqueExpressions;
        this.normalizedSource = normalizedSource;
        this.sourceShape = sourceShape;
    }

    public ArrayList<BlockBean> getBlocks() {
        return blocks;
    }

    public List<JavaToBlocksDiagnostic> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    public int getSupportedStatements() {
        return supportedStatements;
    }

    public int getOpaqueStatements() {
        return opaqueStatements;
    }

    public int getSupportedExpressions() {
        return supportedExpressions;
    }

    public int getOpaqueExpressions() {
        return opaqueExpressions;
    }

    public String getNormalizedSource() {
        return normalizedSource;
    }

    public String getSourceShape() {
        return sourceShape;
    }

    public boolean hasOpaqueNodes() {
        return opaqueStatements > 0 || opaqueExpressions > 0;
    }

    public boolean hasErrors() {
        for (JavaToBlocksDiagnostic diagnostic : diagnostics) {
            if (diagnostic.getSeverity() == JavaToBlocksDiagnostic.Severity.ERROR) {
                return true;
            }
        }
        return false;
    }

    public String buildSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Source shape: ").append(sourceShape).append('\n');
        summary.append("Blocks created: ").append(blocks.size()).append('\n');
        summary.append("Supported statements: ").append(supportedStatements).append('\n');
        summary.append("Opaque statements: ").append(opaqueStatements).append('\n');
        summary.append("Supported expressions: ").append(supportedExpressions).append('\n');
        summary.append("Opaque expressions: ").append(opaqueExpressions);
        if (!diagnostics.isEmpty()) {
            summary.append("\n\nDiagnostics:\n");
            for (JavaToBlocksDiagnostic diagnostic : diagnostics) {
                summary.append("• ").append(diagnostic.formatForDisplay()).append('\n');
            }
        }
        return summary.toString().trim();
    }
}
