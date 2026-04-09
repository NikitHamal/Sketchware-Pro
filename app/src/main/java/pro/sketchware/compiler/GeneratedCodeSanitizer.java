package pro.sketchware.compiler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes generated Sketchware Java so legacy ECJ/Java 7 builds do not fail on
 * common empty-hole patterns and anonymous-listener context pitfalls.
 */
public final class GeneratedCodeSanitizer {

    private GeneratedCodeSanitizer() {
    }

    public static String sanitize(String code, String outerClassName, boolean isFragment) {
        if (code == null || code.isEmpty()) {
            return code;
        }

        String outerRef = isFragment ? "getActivity()" : outerClassName + ".this";
        String applicationContextRef = isFragment
                ? "getContext().getApplicationContext()"
                : outerRef + ".getApplicationContext()";
        String baseContextRef = isFragment
                ? "getActivity().getBaseContext()"
                : outerRef + ".getBaseContext()";
        String systemServiceRef = isFragment
                ? "getContext().getSystemService("
                : outerRef + ".getSystemService(";
        String findViewByIdRef = isFragment
                ? "getActivity().findViewById("
                : outerRef + ".findViewById(";
        String runOnUiThreadRef = isFragment
                ? "getActivity().runOnUiThread("
                : outerRef + ".runOnUiThread(";
        String startActivityRef = isFragment
                ? "getActivity().startActivity("
                : outerRef + ".startActivity(";
        String finishRef = isFragment
                ? "getActivity().finish()"
                : outerRef + ".finish()";

        code = code.replaceAll("if\\s*\\(\\s*\\)\\s*\\{", "if (true) {");
        code = code.replaceAll("else\\s+if\\s*\\(\\s*\\)\\s*\\{", "else if (true) {");
        code = code.replace("String.valueOf()", "String.valueOf(\"\")");
        code = code.replaceAll("(\\.setChecked)\\s*\\(\\s*\\)\\s*;", "$1(false);");
        code = code.replaceAll("(\\.setProgress)\\s*([0-9]+)\\s*;", "$1($2);");
        code = code.replaceAll("([A-Za-z_][A-Za-z0-9_]*Boolean)\\s*=\\s*;", "$1 = false;");

        code = code.replace("getApplicationContext()", applicationContextRef);
        code = code.replace("getBaseContext()", baseContextRef);
        code = code.replace("getSystemService(", systemServiceRef);
        code = code.replace("findViewById(", findViewByIdRef);
        code = code.replace("runOnUiThread(", runOnUiThreadRef);
        code = code.replace("startActivity(", startActivityRef);
        code = code.replace("finish();", finishRef + ";");

        code = replaceBareThisArgument(code, outerRef);
        code = code.replace("new Intent(this,", "new Intent(" + outerRef + ",");

        return code;
    }

    private static String replaceBareThisArgument(String code, String replacement) {
        Pattern pattern = Pattern.compile("([,(]\\s*)this(\\s*[,\\)])");
        Matcher matcher = pattern.matcher(code);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer,
                    Matcher.quoteReplacement(matcher.group(1) + replacement + matcher.group(2)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
