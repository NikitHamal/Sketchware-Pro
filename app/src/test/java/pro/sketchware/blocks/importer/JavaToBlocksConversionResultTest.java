package pro.sketchware.blocks.importer;

import com.besome.sketch.beans.BlockBean;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JavaToBlocksConversionResultTest {

    @Test
    public void buildSummaryIncludesKeyCountsAndDiagnostics() {
        ArrayList<BlockBean> blocks = new ArrayList<>();
        blocks.add(new BlockBean("1", "if %b", " ", "if"));

        ArrayList<JavaToBlocksDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.add(JavaToBlocksDiagnostic.warning("Fallback block created"));

        JavaToBlocksConversionResult result = new JavaToBlocksConversionResult(
                blocks,
                diagnostics,
                3,
                1,
                4,
                2,
                "if (x) { showMessage(\"ok\"); }",
                "statement_block"
        );

        String summary = result.buildSummary();

        assertTrue(summary.contains("Source shape: statement_block"));
        assertTrue(summary.contains("Blocks created: 1"));
        assertTrue(summary.contains("Opaque expressions: 2"));
        assertTrue(summary.contains("Fallback block created"));
        assertFalse(result.hasErrors());
        assertTrue(result.hasOpaqueNodes());
    }
}
