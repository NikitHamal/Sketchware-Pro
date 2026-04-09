package pro.sketchware.compiler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.utility.FileUtil;

/**
 * Produces Java-7-safe temporary source trees from user-provided/imported sources.
 */
public final class LegacyJavaSourceNormalizer {

    private LegacyJavaSourceNormalizer() {
    }

    public static String normalizeDirectoryToTemp(String sourceDirPath, String tempRootPath) {
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists()) {
            return sourceDirPath;
        }
        File tempDir = new File(tempRootPath);
        if (tempDir.exists()) {
            FileUtil.deleteFile(tempDir.getAbsolutePath());
        }
        copyRecursive(sourceDir, tempDir);
        normalizeTree(tempDir);
        return tempDir.getAbsolutePath();
    }

    private static void normalizeTree(File root) {
        List<File> files = new ArrayList<>();
        collectJavaFiles(root, files);
        for (File file : files) {
            String code = FileUtil.readFileIfExist(file.getAbsolutePath());
            String normalized = normalizeJavaFile(code);
            if (!code.equals(normalized)) {
                FileUtil.writeFile(file.getAbsolutePath(), normalized);
            }
        }
    }

    public static String normalizeJavaFile(String code) {
        return normalizeJava(code);
    }

    public static String normalizeJava(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }

        code = normalizeArrowSwitchReturnExpression(code);
        code = normalizeArrowSwitchStatement(code);
        return code;
    }

    private static String normalizeArrowSwitchReturnExpression(String code) {
        Pattern pattern = Pattern.compile("return\\s+switch\\s*\\(([^)]*)\\)\\s*\\{([\\s\\S]*?)\\};", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(code);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String expr = matcher.group(1).trim();
            String body = matcher.group(2);
            String replacement = "switch (" + expr + ") {\n" + convertArrowCases(body, true) + "}\n";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String normalizeArrowSwitchStatement(String code) {
        Pattern pattern = Pattern.compile("switch\\s*\\(([^)]*)\\)\\s*\\{([\\s\\S]*?)\\}", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(code);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String full = matcher.group(0);
            String body = matcher.group(2);
            if (!body.contains("->")) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(full));
                continue;
            }
            String expr = matcher.group(1).trim();
            String replacement = "switch (" + expr + ") {\n" + convertArrowCases(body, false) + "}";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String convertArrowCases(String body, boolean returnStatements) {
        StringBuilder sb = new StringBuilder();
        Pattern casePattern = Pattern.compile("(case\\s+[^\\n:]+|default)\\s*->\\s*([^;{}]+);", Pattern.MULTILINE);
        Matcher matcher = casePattern.matcher(body);
        while (matcher.find()) {
            sb.append(matcher.group(1)).append(":\n    ");
            if (returnStatements) {
                sb.append("return ").append(matcher.group(2).trim()).append(";\n");
            } else {
                sb.append(matcher.group(2).trim()).append(";\n    break;\n");
            }
        }
        return sb.toString();
    }

    private static void collectJavaFiles(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectJavaFiles(child, out);
            } else if (child.getName().endsWith(".java")) {
                out.add(child);
            }
        }
    }

    private static void copyRecursive(File source, File target) {
        if (source.isDirectory()) {
            FileUtil.makeDir(target.getAbsolutePath());
            File[] children = source.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                copyRecursive(child, new File(target, child.getName()));
            }
        } else {
            FileUtil.copyFile(source.getAbsolutePath(), target.getAbsolutePath());
        }
    }
}
