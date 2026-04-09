package pro.sketchware.compiler;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.Objects;

/**
 * Normalizes generated Activity/Fragment Java so legacy ECJ builds do not fail on
 * common empty-hole patterns and anonymous-listener context pitfalls.
 */
public final class GeneratedCodeSanitizer {

    private GeneratedCodeSanitizer() {
    }

    public static String sanitize(String code, String outerClassName, boolean isFragment) {
        if (code == null || code.isEmpty()) {
            return code;
        }

        String sanitized = GeneratedCodeSyntaxFixer.fix(code);
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(sanitized);
            LexicalPreservingPrinter.setup(compilationUnit);
            compilationUnit.accept(new SanitizingVisitor(), SanitizerContext.create(outerClassName, isFragment));
            return LexicalPreservingPrinter.print(compilationUnit);
        } catch (ParseProblemException ignored) {
            // Keep the safe syntax-only fixes if parsing fails instead of risking destructive rewrites.
            return sanitized;
        }
    }

    private static final class SanitizingVisitor extends ModifierVisitor<SanitizerContext> {

        @Override
        public Expression visit(MethodCallExpr methodCall, SanitizerContext context) {
            super.visit(methodCall, context);

            if (methodCall.getScope().isEmpty()) {
                Expression scope = context.getScopeFor(methodCall.getNameAsString());
                if (scope != null) {
                    methodCall.setScope(scope);
                }
            }

            if (isUnscopedStringValueOf(methodCall)) {
                methodCall.addArgument(new StringLiteralExpr(""));
            }

            if (methodCall.getNameAsString().equals("setChecked") && methodCall.getArguments().isEmpty()) {
                methodCall.addArgument(new BooleanLiteralExpr(false));
            }

            if (methodCall.getNameAsString().equals("setProgress") && methodCall.getArguments().isEmpty()) {
                methodCall.addArgument(new IntegerLiteralExpr("0"));
            }

            return methodCall;
        }

        @Override
        public Expression visit(ThisExpr thisExpr, SanitizerContext context) {
            super.visit(thisExpr, context);
            if (thisExpr.getTypeName().isPresent() || !isBareThisArgument(thisExpr)) {
                return thisExpr;
            }
            return context.bareThisReplacement().clone();
        }

        private boolean isUnscopedStringValueOf(MethodCallExpr methodCall) {
            if (!methodCall.getNameAsString().equals("valueOf") || !methodCall.getArguments().isEmpty()) {
                return false;
            }

            return methodCall.getScope()
                    .filter(Expression::isNameExpr)
                    .map(Expression::asNameExpr)
                    .map(nameExpr -> nameExpr.getNameAsString().equals("String"))
                    .orElse(false);
        }

        private boolean isBareThisArgument(ThisExpr thisExpr) {
            Node parent = thisExpr.getParentNode().orElse(null);
            if (parent instanceof NodeWithArguments<?> withArguments) {
                return withArguments.getArguments().stream().anyMatch(argument -> argument == thisExpr);
            }
            if (parent instanceof ExplicitConstructorInvocationStmt invocationStmt) {
                return invocationStmt.getArguments().stream().anyMatch(argument -> argument == thisExpr);
            }
            return false;
        }
    }

    private record SanitizerContext(
            Expression bareThisReplacement,
            Expression applicationContextScope,
            Expression baseContextScope,
            Expression systemServiceScope,
            Expression findViewByIdScope,
            Expression runOnUiThreadScope,
            Expression startActivityScope,
            Expression finishScope) {

        static SanitizerContext create(String outerClassName, boolean isFragment) {
            String trimmedOuterClassName = Objects.requireNonNullElse(outerClassName, "").trim();
            String activityScope;
            if (isFragment) {
                activityScope = "getActivity()";
            } else {
                activityScope = trimmedOuterClassName.isEmpty() ? "this" : trimmedOuterClassName + ".this";
            }
            String contextScope = isFragment ? "getContext()" : activityScope;

            return new SanitizerContext(
                    parseExpression(activityScope),
                    parseExpression(contextScope),
                    parseExpression(activityScope),
                    parseExpression(contextScope),
                    parseExpression(activityScope),
                    parseExpression(activityScope),
                    parseExpression(activityScope),
                    parseExpression(activityScope)
            );
        }

        Expression getScopeFor(String methodName) {
            return switch (methodName) {
                case "getApplicationContext" -> applicationContextScope.clone();
                case "getBaseContext" -> baseContextScope.clone();
                case "getSystemService" -> systemServiceScope.clone();
                case "findViewById" -> findViewByIdScope.clone();
                case "runOnUiThread" -> runOnUiThreadScope.clone();
                case "startActivity" -> startActivityScope.clone();
                case "finish" -> finishScope.clone();
                default -> null;
            };
        }

        private static Expression parseExpression(String expression) {
            return StaticJavaParser.parseExpression(expression);
        }
    }
}
