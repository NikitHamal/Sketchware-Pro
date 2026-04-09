package pro.sketchware.blocks.importer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class JavaToBlocksScopeTest {

    @Test
    public void eventArgumentLookupSupportsUnderscoreAliases() {
        JavaToBlocksScope scope = new JavaToBlocksScope();
        scope.registerEventArgument("text", "s", "", JavaToBlocksScope.TypeHint.STRING);
        scope.registerEventArgument("_text", "s", "", JavaToBlocksScope.TypeHint.STRING);

        JavaToBlocksScope.Symbol plain = scope.find("text");
        JavaToBlocksScope.Symbol underscored = scope.find("_text");

        assertNotNull(plain);
        assertNotNull(underscored);
        assertEquals(JavaToBlocksScope.TypeHint.STRING, plain.getTypeHint());
        assertEquals(JavaToBlocksScope.TypeHint.STRING, underscored.getTypeHint());
    }

    @Test
    public void typeHintInferenceCoversProjectRelevantJavaTypes() {
        assertEquals(JavaToBlocksScope.TypeHint.BOOLEAN, JavaToBlocksScope.typeHintForJavaType("boolean"));
        assertEquals(JavaToBlocksScope.TypeHint.NUMBER, JavaToBlocksScope.typeHintForJavaType("Integer"));
        assertEquals(JavaToBlocksScope.TypeHint.STRING, JavaToBlocksScope.typeHintForJavaType("CharSequence"));
        assertEquals(JavaToBlocksScope.TypeHint.MAP, JavaToBlocksScope.typeHintForJavaType("HashMap<String, Object>"));
        assertEquals(JavaToBlocksScope.TypeHint.LIST, JavaToBlocksScope.typeHintForJavaType("ArrayList<String>"));
        assertEquals(JavaToBlocksScope.TypeHint.VIEW, JavaToBlocksScope.typeHintForJavaType("LinearLayout"));
        assertEquals(JavaToBlocksScope.TypeHint.COMPONENT, JavaToBlocksScope.typeHintForJavaType("Intent"));
    }
}
