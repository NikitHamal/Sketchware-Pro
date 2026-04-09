package pro.sketchware.blocks.importer;

import com.besome.sketch.beans.BlockBean;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.EmptyStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.body.VariableDeclarator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import a.a.a.kq;
import mod.hey.studios.editor.manage.block.ExtraBlockInfo;
import mod.hey.studios.editor.manage.block.v2.BlockLoader;
import pro.sketchware.compiler.LegacyJavaSourceNormalizer;

public final class JavaToBlocksConverter {

    private JavaToBlocksConverter() {
    }

    public static JavaToBlocksConversionResult convert(String source, JavaToBlocksScope scope) {
        String normalizedSource = LegacyJavaSourceNormalizer.normalizeJavaFile(source == null ? "" : source);
        ParsedInput input = parseStatements(normalizedSource);

        ConversionContext context = new ConversionContext(scope == null ? new JavaToBlocksScope() : scope,
                normalizedSource, input.sourceShape);
        context.diagnostics.addAll(input.diagnostics);
        context.appendChain(context.convertStatements(input.statements));
        return context.buildResult();
    }

    private static ParsedInput parseStatements(String source) {
        ArrayList<JavaToBlocksDiagnostic> diagnostics = new ArrayList<>();

        try {
            BlockStmt blockStmt = StaticJavaParser.parseBlock("{" + source + "}");
            return new ParsedInput(blockStmt.getStatements(), "statement_block", diagnostics);
        } catch (ParseProblemException ignored) {
        }

        try {
            BodyDeclaration<?> bodyDeclaration = StaticJavaParser.parseBodyDeclaration(source);
            if (bodyDeclaration instanceof MethodDeclaration methodDeclaration && methodDeclaration.getBody().isPresent()) {
                return new ParsedInput(methodDeclaration.getBody().get().getStatements(), "method", diagnostics);
            }
            if (bodyDeclaration instanceof ConstructorDeclaration constructorDeclaration) {
                return new ParsedInput(constructorDeclaration.getBody().getStatements(), "constructor", diagnostics);
            }
            if (bodyDeclaration instanceof InitializerDeclaration initializerDeclaration) {
                return new ParsedInput(initializerDeclaration.getBody().getStatements(), "initializer", diagnostics);
            }
        } catch (ParseProblemException ignored) {
        }

        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(source);
            List<BlockStmt> candidates = new ArrayList<>();
            for (MethodDeclaration method : compilationUnit.findAll(MethodDeclaration.class)) {
                method.getBody().ifPresent(candidates::add);
            }
            for (ConstructorDeclaration constructor : compilationUnit.findAll(ConstructorDeclaration.class)) {
                candidates.add(constructor.getBody());
            }
            for (InitializerDeclaration initializer : compilationUnit.findAll(InitializerDeclaration.class)) {
                candidates.add(initializer.getBody());
            }
            if (!candidates.isEmpty()) {
                if (candidates.size() > 1) {
                    diagnostics.add(JavaToBlocksDiagnostic.warning(
                            "Multiple method bodies found. Imported the first body only."));
                }
                return new ParsedInput(candidates.get(0).getStatements(), "compilation_unit", diagnostics);
            }
        } catch (ParseProblemException ignored) {
        }

        diagnostics.add(JavaToBlocksDiagnostic.error("Could not parse the provided Java source."));
        return new ParsedInput(new NodeList<>(), "unknown", diagnostics);
    }

    private static final class ParsedInput {
        final NodeList<Statement> statements;
        final String sourceShape;
        final ArrayList<JavaToBlocksDiagnostic> diagnostics;

        ParsedInput(NodeList<Statement> statements, String sourceShape, ArrayList<JavaToBlocksDiagnostic> diagnostics) {
            this.statements = statements;
            this.sourceShape = sourceShape;
            this.diagnostics = diagnostics;
        }
    }

    private static final class ConversionContext {
        private final JavaToBlocksScope scope;
        private final ArrayList<BlockBean> blocks = new ArrayList<>();
        private final ArrayList<JavaToBlocksDiagnostic> diagnostics = new ArrayList<>();
        private final String normalizedSource;
        private final String sourceShape;

        private int nextId = 1;
        private int rootHeadId = -1;
        private int supportedStatements;
        private int opaqueStatements;
        private int supportedExpressions;
        private int opaqueExpressions;

        ConversionContext(JavaToBlocksScope scope, String normalizedSource, String sourceShape) {
            this.scope = scope;
            this.normalizedSource = normalizedSource;
            this.sourceShape = sourceShape;
        }

        JavaToBlocksConversionResult buildResult() {
            ArrayList<BlockBean> orderedBlocks = new ArrayList<>(blocks);
            if (rootHeadId >= 0) {
                String rootId = String.valueOf(rootHeadId);
                for (int i = 0; i < orderedBlocks.size(); i++) {
                    if (rootId.equals(orderedBlocks.get(i).id)) {
                        BlockBean root = orderedBlocks.remove(i);
                        orderedBlocks.add(0, root);
                        break;
                    }
                }
            }
            return new JavaToBlocksConversionResult(
                    orderedBlocks,
                    diagnostics,
                    supportedStatements,
                    opaqueStatements,
                    supportedExpressions,
                    opaqueExpressions,
                    normalizedSource,
                    sourceShape
            );
        }

        private void appendChain(Chain chain) {
            if (chain == null || chain.headId < 0) {
                return;
            }
            if (rootHeadId < 0) {
                rootHeadId = chain.headId;
            }
        }

        private Chain convertStatements(NodeList<Statement> statements) {
            Chain chain = new Chain();
            for (Statement statement : statements) {
                Chain part = convertStatement(statement);
                chain.append(part);
            }
            return chain;
        }

        private Chain convertStatement(Statement statement) {
            if (statement == null || statement instanceof EmptyStmt) {
                return new Chain();
            }
            if (statement instanceof BlockStmt blockStmt) {
                return convertStatements(blockStmt.getStatements());
            }
            if (statement instanceof ExpressionStmt expressionStmt) {
                return convertExpressionStatement(expressionStmt.getExpression(), expressionStmt);
            }
            if (statement instanceof IfStmt ifStmt) {
                return convertIfStatement(ifStmt);
            }
            if (statement instanceof ForStmt forStmt) {
                return convertForStatement(forStmt);
            }
            if (statement instanceof WhileStmt whileStmt) {
                return convertWhileStatement(whileStmt);
            }
            if (statement instanceof DoStmt doStmt) {
                return convertDoStatement(doStmt);
            }
            if (statement instanceof BreakStmt breakStmt) {
                BlockBean block = createBuiltInBlock("break");
                supportedStatements++;
                return chainOf(block);
            }
            if (statement instanceof ReturnStmt returnStmt) {
                return fallbackStatement(returnStmt, "Return statements are preserved as direct source.");
            }
            return fallbackStatement(statement, "Unsupported statement was preserved as direct source.");
        }

        private Chain convertExpressionStatement(Expression expression, Statement originalStatement) {
            if (expression instanceof VariableDeclarationExpr variableDeclarationExpr) {
                return convertVariableDeclaration(variableDeclarationExpr);
            }
            if (expression instanceof AssignExpr assignExpr) {
                Chain chain = convertAssignment(assignExpr);
                if (!chain.isEmpty()) {
                    supportedStatements++;
                    return chain;
                }
                return fallbackStatement(originalStatement, "Assignment could not be mapped safely and was preserved as direct source.");
            }
            if (expression instanceof UnaryExpr unaryExpr) {
                Chain chain = convertUnaryStatement(unaryExpr);
                if (!chain.isEmpty()) {
                    supportedStatements++;
                    return chain;
                }
            }
            if (expression instanceof MethodCallExpr methodCallExpr) {
                Chain chain = convertMethodCallStatement(methodCallExpr);
                if (!chain.isEmpty()) {
                    supportedStatements++;
                    return chain;
                }
            }
            return fallbackStatement(originalStatement, "Expression statement was preserved as direct source.");
        }

        private Chain convertVariableDeclaration(VariableDeclarationExpr expr) {
            Chain chain = new Chain();
            Type declaredType = expr.getElementType();
            for (VariableDeclarator declarator : expr.getVariables()) {
                JavaToBlocksScope.Symbol symbol = scope.find(declarator.getNameAsString());
                if (symbol == null || declarator.getInitializer().isEmpty()) {
                    diagnostics.add(JavaToBlocksDiagnostic.warning(expr,
                            "Local declaration for '" + declarator.getNameAsString() + "' was preserved as source because it is not a project-level symbol."));
                    return fallbackStatement(expr.getParentNode().orElse(expr),
                            "Local variable declaration was preserved as direct source.");
                }
                AssignExpr assignExpr = new AssignExpr(
                        new NameExpr(declarator.getNameAsString()),
                        declarator.getInitializer().get(),
                        AssignExpr.Operator.ASSIGN
                );
                Chain converted = convertAssignment(assignExpr);
                if (converted.isEmpty()) {
                    return fallbackStatement(expr.getParentNode().orElse(expr),
                            "Variable declaration initializer was preserved as direct source.");
                }
                chain.append(converted);
                if (symbol.getTypeHint() == JavaToBlocksScope.TypeHint.UNKNOWN) {
                    JavaToBlocksScope.TypeHint inferred = JavaToBlocksScope.typeHintForJavaType(declaredType.asString());
                    scope.registerSymbol(symbol.getName(), symbol.getBlockType(), symbol.getTypeName(), inferred, symbol.isEventArgument());
                }
            }
            supportedStatements++;
            return chain;
        }

        private Chain convertIfStatement(IfStmt ifStmt) {
            ValueRef condition = convertExpression(ifStmt.getCondition(), JavaToBlocksScope.TypeHint.BOOLEAN, true);
            if (condition == null) {
                return fallbackStatement(ifStmt, "If condition could not be mapped safely and was preserved as direct source.");
            }

            Chain thenChain = convertNestedBody(ifStmt.getThenStmt());
            Chain elseChain = ifStmt.getElseStmt().map(this::convertNestedBody).orElse(new Chain());

            BlockBean block = createBuiltInBlock(ifStmt.getElseStmt().isPresent() ? "ifElse" : "if");
            block.parameters.add(condition.asParameter());
            block.subStack1 = thenChain.headId;
            if (ifStmt.getElseStmt().isPresent()) {
                block.subStack2 = elseChain.headId;
            }
            supportedStatements++;
            return chainOf(block);
        }

        private Chain convertForStatement(ForStmt forStmt) {
            Expression repeatCount = extractRepeatCount(forStmt);
            if (repeatCount == null) {
                return fallbackStatement(forStmt, "Only repeat-style for loops are converted natively. This loop was preserved as direct source.");
            }
            ValueRef countRef = convertExpression(repeatCount, JavaToBlocksScope.TypeHint.NUMBER, true);
            if (countRef == null) {
                return fallbackStatement(forStmt, "Repeat count expression was preserved as direct source.");
            }
            Chain body = convertNestedBody(forStmt.getBody());
            BlockBean block = createBuiltInBlock("repeat");
            block.parameters.add(countRef.asParameter());
            block.subStack1 = body.headId;
            supportedStatements++;
            return chainOf(block);
        }

        private Expression extractRepeatCount(ForStmt forStmt) {
            if (forStmt.getInitialization().size() != 1 || forStmt.getCompare().isEmpty() || forStmt.getUpdate().size() != 1) {
                return null;
            }
            Expression initialization = forStmt.getInitialization().get(0);
            Expression update = forStmt.getUpdate().get(0);
            if (!(initialization instanceof VariableDeclarationExpr declarationExpr)) {
                return null;
            }
            if (declarationExpr.getVariables().size() != 1) {
                return null;
            }
            VariableDeclarator variable = declarationExpr.getVariables().get(0);
            if (variable.getInitializer().isEmpty()) {
                return null;
            }
            if (!(variable.getInitializer().get() instanceof IntegerLiteralExpr integerLiteralExpr) || !"0".equals(integerLiteralExpr.getValue())) {
                return null;
            }
            String loopName = variable.getNameAsString();
            if (!(update instanceof UnaryExpr unaryExpr)) {
                return null;
            }
            if (!(unaryExpr.getExpression() instanceof NameExpr updateName) || !loopName.equals(updateName.getNameAsString())) {
                return null;
            }
            if (unaryExpr.getOperator() != UnaryExpr.Operator.POSTFIX_INCREMENT
                    && unaryExpr.getOperator() != UnaryExpr.Operator.PREFIX_INCREMENT) {
                return null;
            }
            Expression compare = forStmt.getCompare().get();
            if (compare instanceof BinaryExpr binaryExpr
                    && binaryExpr.getOperator() == BinaryExpr.Operator.LESS
                    && binaryExpr.getLeft() instanceof NameExpr leftName
                    && loopName.equals(leftName.getNameAsString())) {
                return binaryExpr.getRight();
            }
            return null;
        }

        private Chain convertWhileStatement(WhileStmt whileStmt) {
            ValueRef condition = convertExpression(whileStmt.getCondition(), JavaToBlocksScope.TypeHint.BOOLEAN, true);
            if (condition == null) {
                return fallbackStatement(whileStmt, "While condition was preserved as direct source.");
            }

            BlockBean foreverBlock = createBuiltInBlock("forever");
            Chain body = new Chain();
            if (!isBooleanLiteralTrue(condition)) {
                BlockBean notBlock = createBuiltInBlock("not");
                notBlock.parameters.add(condition.asParameter());
                BlockBean breakBlock = createBuiltInBlock("break");
                Chain guard = chainOf(breakBlock);
                BlockBean ifBlock = createBuiltInBlock("if");
                ifBlock.parameters.add(referenceOf(notBlock));
                ifBlock.subStack1 = guard.headId;
                Chain guardChain = chainOf(ifBlock);
                body.append(guardChain);
            }
            body.append(convertNestedBody(whileStmt.getBody()));
            foreverBlock.subStack1 = body.headId;
            supportedStatements++;
            return chainOf(foreverBlock);
        }

        private Chain convertDoStatement(DoStmt doStmt) {
            ValueRef condition = convertExpression(doStmt.getCondition(), JavaToBlocksScope.TypeHint.BOOLEAN, true);
            if (condition == null) {
                return fallbackStatement(doStmt, "Do/while condition was preserved as direct source.");
            }

            BlockBean foreverBlock = createBuiltInBlock("forever");
            Chain body = convertNestedBody(doStmt.getBody());
            if (!isBooleanLiteralTrue(condition)) {
                BlockBean notBlock = createBuiltInBlock("not");
                notBlock.parameters.add(condition.asParameter());
                BlockBean breakBlock = createBuiltInBlock("break");
                Chain breakChain = chainOf(breakBlock);
                BlockBean ifBlock = createBuiltInBlock("if");
                ifBlock.parameters.add(referenceOf(notBlock));
                ifBlock.subStack1 = breakChain.headId;
                body.append(chainOf(ifBlock));
            }
            foreverBlock.subStack1 = body.headId;
            supportedStatements++;
            return chainOf(foreverBlock);
        }

        private Chain convertUnaryStatement(UnaryExpr unaryExpr) {
            if (!(unaryExpr.getExpression() instanceof NameExpr nameExpr)) {
                return new Chain();
            }
            JavaToBlocksScope.Symbol symbol = scope.find(nameExpr.getNameAsString());
            if (symbol == null || symbol.getTypeHint() != JavaToBlocksScope.TypeHint.NUMBER) {
                return new Chain();
            }
            if (unaryExpr.getOperator() == UnaryExpr.Operator.POSTFIX_INCREMENT
                    || unaryExpr.getOperator() == UnaryExpr.Operator.PREFIX_INCREMENT) {
                BlockBean block = createBuiltInBlock("increaseInt");
                block.parameters.add(symbol.getName());
                return chainOf(block);
            }
            if (unaryExpr.getOperator() == UnaryExpr.Operator.POSTFIX_DECREMENT
                    || unaryExpr.getOperator() == UnaryExpr.Operator.PREFIX_DECREMENT) {
                BlockBean block = createBuiltInBlock("decreaseInt");
                block.parameters.add(symbol.getName());
                return chainOf(block);
            }
            return new Chain();
        }

        private Chain convertAssignment(AssignExpr assignExpr) {
            NameInfo target = extractName(assignExpr.getTarget());
            if (target == null) {
                return new Chain();
            }
            JavaToBlocksScope.Symbol symbol = scope.find(target.normalizedLookupName);
            if (symbol == null) {
                return new Chain();
            }

            AssignExpr.Operator operator = assignExpr.getOperator();
            if (symbol.getTypeHint() == JavaToBlocksScope.TypeHint.MAP && operator == AssignExpr.Operator.ASSIGN
                    && assignExpr.getValue() instanceof ObjectCreationExpr objectCreationExpr
                    && objectCreationExpr.getType().asString().toLowerCase(Locale.US).contains("hashmap")) {
                BlockBean block = createBuiltInBlock("mapCreateNew");
                block.parameters.add(symbol.getName());
                return chainOf(block);
            }

            Expression effectiveValue = assignExpr.getValue();
            if (operator != AssignExpr.Operator.ASSIGN) {
                effectiveValue = rewriteCompoundAssignment(symbol, operator, assignExpr.getValue());
                if (effectiveValue == null) {
                    return new Chain();
                }
            }

            String opcode = switch (symbol.getTypeHint()) {
                case BOOLEAN -> "setVarBoolean";
                case NUMBER -> "setVarInt";
                case STRING -> "setVarString";
                default -> null;
            };
            if (opcode == null) {
                return new Chain();
            }

            ValueRef valueRef = convertExpression(effectiveValue, symbol.getTypeHint(), true);
            if (valueRef == null) {
                return new Chain();
            }

            BlockBean block = createBuiltInBlock(opcode);
            block.parameters.add(symbol.getName());
            block.parameters.add(valueRef.asParameter());
            return chainOf(block);
        }

        private Expression rewriteCompoundAssignment(JavaToBlocksScope.Symbol symbol, AssignExpr.Operator operator, Expression value) {
            NameExpr targetExpr = new NameExpr(symbol.getName());
            return switch (operator) {
                case PLUS -> new BinaryExpr(targetExpr, value,
                        symbol.getTypeHint() == JavaToBlocksScope.TypeHint.STRING
                                ? BinaryExpr.Operator.PLUS
                                : BinaryExpr.Operator.PLUS);
                case MINUS -> new BinaryExpr(targetExpr, value, BinaryExpr.Operator.MINUS);
                case MULTIPLY -> new BinaryExpr(targetExpr, value, BinaryExpr.Operator.MULTIPLY);
                case DIVIDE -> new BinaryExpr(targetExpr, value, BinaryExpr.Operator.DIVIDE);
                case REMAINDER -> new BinaryExpr(targetExpr, value, BinaryExpr.Operator.REMAINDER);
                default -> null;
            };
        }

        private Chain convertMethodCallStatement(MethodCallExpr call) {
            // List and map statement APIs.
            if (call.getScope().isPresent()) {
                NameInfo scopeName = extractName(call.getScope().get());
                JavaToBlocksScope.Symbol scopedSymbol = scopeName == null ? null : scope.find(scopeName.normalizedLookupName);
                if (scopedSymbol != null) {
                    Chain collectionChain = convertCollectionMutation(call, scopedSymbol);
                    if (!collectionChain.isEmpty()) {
                        return collectionChain;
                    }
                    Chain uiChain = convertScopedUiMutation(call, scopedSymbol);
                    if (!uiChain.isEmpty()) {
                        return uiChain;
                    }
                    Chain componentChain = convertComponentMutation(call, scopedSymbol);
                    if (!componentChain.isEmpty()) {
                        return componentChain;
                    }
                }
            }

            if ("finish".equals(call.getNameAsString()) && call.getArguments().isEmpty()
                    && (!call.getScope().isPresent() || isCurrentInstanceScope(call.getScope().get()))) {
                return chainOf(createBuiltInBlock("finishActivity"));
            }
            if ("setTitle".equals(call.getNameAsString()) && call.getArguments().size() == 1
                    && (!call.getScope().isPresent() || isCurrentInstanceScope(call.getScope().get()))) {
                ValueRef valueRef = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.STRING, true);
                if (valueRef != null) {
                    BlockBean block = createBuiltInBlock("setTitle");
                    block.parameters.add(valueRef.asParameter());
                    return chainOf(block);
                }
            }
            if ("showMessage".equals(call.getNameAsString()) && call.getArguments().size() == 1
                    && (!call.getScope().isPresent() || isCurrentInstanceScope(call.getScope().get()))) {
                ValueRef valueRef = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.STRING, true);
                if (valueRef != null) {
                    BlockBean block = createBuiltInBlock("doToast");
                    block.parameters.add(valueRef.asParameter());
                    return chainOf(block);
                }
            }
            return new Chain();
        }

        private Chain convertCollectionMutation(MethodCallExpr call, JavaToBlocksScope.Symbol symbol) {
            String method = call.getNameAsString();
            if (symbol.getTypeHint() == JavaToBlocksScope.TypeHint.MAP) {
                if ("put".equals(method) && call.getArguments().size() == 2) {
                    ValueRef key = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.STRING, true);
                    ValueRef value = convertExpression(call.getArgument(1), JavaToBlocksScope.TypeHint.OBJECT, true);
                    if (key != null && value != null) {
                        BlockBean block = createBuiltInBlock("mapPut");
                        block.parameters.add(symbol.getName());
                        block.parameters.add(key.asParameter());
                        block.parameters.add(value.asParameter());
                        return chainOf(block);
                    }
                }
                if ("remove".equals(method) && call.getArguments().size() == 1) {
                    ValueRef key = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.STRING, true);
                    if (key != null) {
                        BlockBean block = createBuiltInBlock("mapRemoveKey");
                        block.parameters.add(symbol.getName());
                        block.parameters.add(key.asParameter());
                        return chainOf(block);
                    }
                }
                if ("clear".equals(method) && call.getArguments().isEmpty()) {
                    BlockBean block = createBuiltInBlock("mapClear");
                    block.parameters.add(symbol.getName());
                    return chainOf(block);
                }
            }
            if (symbol.getTypeHint() == JavaToBlocksScope.TypeHint.LIST) {
                if ("clear".equals(method) && call.getArguments().isEmpty()) {
                    BlockBean block = createBuiltInBlock("clearList");
                    block.parameters.add(symbol.getName());
                    return chainOf(block);
                }
                if ("remove".equals(method) && call.getArguments().size() == 1) {
                    ValueRef index = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.NUMBER, true);
                    if (index != null) {
                        BlockBean block = createBuiltInBlock("deleteList");
                        block.parameters.add(index.asParameter());
                        block.parameters.add(symbol.getName());
                        return chainOf(block);
                    }
                }
                if ("add".equals(method) && call.getArguments().size() == 1) {
                    JavaToBlocksScope.TypeHint itemHint = inferListItemHint(symbol.getTypeName());
                    ValueRef item = convertExpression(call.getArgument(0), itemHint, true);
                    if (item != null) {
                        String opcode = switch (symbol.getTypeName()) {
                            case "List Number" -> "addListInt";
                            case "List String" -> "addListStr";
                            case "List Map" -> "addMapToList";
                            default -> null;
                        };
                        if (opcode != null) {
                            BlockBean block = createBuiltInBlock(opcode);
                            block.parameters.add(item.asParameter());
                            block.parameters.add(symbol.getName());
                            return chainOf(block);
                        }
                    }
                }
                if ("add".equals(method) && call.getArguments().size() == 2) {
                    JavaToBlocksScope.TypeHint itemHint = inferListItemHint(symbol.getTypeName());
                    ValueRef index = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.NUMBER, true);
                    ValueRef item = convertExpression(call.getArgument(1), itemHint, true);
                    String opcode = switch (symbol.getTypeName()) {
                        case "List Number" -> "insertListInt";
                        case "List String" -> "insertListStr";
                        case "List Map" -> "insertMapToList";
                        default -> null;
                    };
                    if (opcode != null && index != null && item != null) {
                        BlockBean block = createBuiltInBlock(opcode);
                        block.parameters.add(item.asParameter());
                        block.parameters.add(index.asParameter());
                        block.parameters.add(symbol.getName());
                        return chainOf(block);
                    }
                }
            }
            return new Chain();
        }

        private JavaToBlocksScope.TypeHint inferListItemHint(String typeName) {
            return switch (typeName) {
                case "List Number" -> JavaToBlocksScope.TypeHint.NUMBER;
                case "List String" -> JavaToBlocksScope.TypeHint.STRING;
                case "List Map" -> JavaToBlocksScope.TypeHint.MAP;
                default -> JavaToBlocksScope.TypeHint.OBJECT;
            };
        }

        private Chain convertScopedUiMutation(MethodCallExpr call, JavaToBlocksScope.Symbol symbol) {
            if (symbol.getTypeHint() != JavaToBlocksScope.TypeHint.VIEW) {
                return new Chain();
            }
            String method = call.getNameAsString();
            switch (method) {
                case "setText": {
                    if (call.getArguments().size() == 1) {
                        ValueRef valueRef = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.STRING, true);
                        if (valueRef != null) {
                            BlockBean block = createBuiltInBlock("setText");
                            block.parameters.add(symbol.getName());
                            block.parameters.add(valueRef.asParameter());
                            return chainOf(block);
                        }
                    }
                    break;
                }
                case "setEnabled": {
                    if (call.getArguments().size() == 1) {
                        ValueRef valueRef = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.BOOLEAN, true);
                        if (valueRef != null) {
                            BlockBean block = createBuiltInBlock("setEnable");
                            block.parameters.add(symbol.getName());
                            block.parameters.add(valueRef.asParameter());
                            return chainOf(block);
                        }
                    }
                    break;
                }
                case "setClickable": {
                    if (call.getArguments().size() == 1) {
                        ValueRef valueRef = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.BOOLEAN, true);
                        if (valueRef != null) {
                            BlockBean block = createBuiltInBlock("setClickable");
                            block.parameters.add(symbol.getName());
                            block.parameters.add(valueRef.asParameter());
                            return chainOf(block);
                        }
                    }
                    break;
                }
                case "requestFocus": {
                    if (call.getArguments().isEmpty()) {
                        BlockBean block = createBuiltInBlock("requestFocus");
                        block.parameters.add(symbol.getName());
                        return chainOf(block);
                    }
                    break;
                }
                case "setHint": {
                    if (call.getArguments().size() == 1) {
                        ValueRef valueRef = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.STRING, true);
                        if (valueRef != null) {
                            BlockBean block = createBuiltInBlock("setHint");
                            block.parameters.add(symbol.getName());
                            block.parameters.add(valueRef.asParameter());
                            return chainOf(block);
                        }
                    }
                    break;
                }
                case "setTextColor": {
                    if (call.getArguments().size() == 1) {
                        ValueRef valueRef = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.NUMBER, true);
                        if (valueRef != null) {
                            BlockBean block = createBuiltInBlock("setTextColor");
                            block.parameters.add(symbol.getName());
                            block.parameters.add(valueRef.asParameter());
                            return chainOf(block);
                        }
                    }
                    break;
                }
                case "setBackgroundColor": {
                    if (call.getArguments().size() == 1) {
                        ValueRef valueRef = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.NUMBER, true);
                        if (valueRef != null) {
                            BlockBean block = createBuiltInBlock("setBgColor");
                            block.parameters.add(symbol.getName());
                            block.parameters.add(valueRef.asParameter());
                            return chainOf(block);
                        }
                    }
                    break;
                }
                default:
                    break;
            }
            return new Chain();
        }

        private Chain convertComponentMutation(MethodCallExpr call, JavaToBlocksScope.Symbol symbol) {
            if (symbol.getTypeHint() != JavaToBlocksScope.TypeHint.COMPONENT) {
                return new Chain();
            }
            String method = call.getNameAsString();
            switch (symbol.getTypeName()) {
                case "Intent":
                    return convertIntentMutation(call, symbol, method);
                case "File":
                    return convertSharedPreferencesMutation(call, symbol, method);
                default:
                    return new Chain();
            }
        }

        private Chain convertIntentMutation(MethodCallExpr call, JavaToBlocksScope.Symbol symbol, String method) {
            switch (method) {
                case "setAction":
                    if (call.getArguments().size() == 1) {
                        ValueRef action = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.STRING, true);
                        if (action != null) {
                            BlockBean block = createBuiltInBlock("intentSetAction");
                            block.parameters.add(symbol.getName());
                            block.parameters.add(action.asParameter());
                            return chainOf(block);
                        }
                    }
                    break;
                case "setData":
                    if (call.getArguments().size() == 1) {
                        ValueRef data = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.STRING, true);
                        if (data != null) {
                            BlockBean block = createBuiltInBlock("intentSetData");
                            block.parameters.add(symbol.getName());
                            block.parameters.add(data.asParameter());
                            return chainOf(block);
                        }
                    }
                    break;
                case "putExtra":
                    if (call.getArguments().size() == 2) {
                        ValueRef key = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.STRING, true);
                        ValueRef value = convertExpression(call.getArgument(1), JavaToBlocksScope.TypeHint.STRING, true);
                        if (key != null && value != null) {
                            BlockBean block = createBuiltInBlock("intentPutExtra");
                            block.parameters.add(symbol.getName());
                            block.parameters.add(key.asParameter());
                            block.parameters.add(value.asParameter());
                            return chainOf(block);
                        }
                    }
                    break;
                default:
                    break;
            }
            return new Chain();
        }

        private Chain convertSharedPreferencesMutation(MethodCallExpr call, JavaToBlocksScope.Symbol symbol, String method) {
            if (!"edit".equals(method) || call.getArguments().size() != 0 || call.getParentNode().isEmpty()) {
                return new Chain();
            }
            if (!(call.getParentNode().get() instanceof MethodCallExpr parentCall)) {
                return new Chain();
            }
            if (parentCall.getParentNode().isEmpty() || !(parentCall.getParentNode().get() instanceof MethodCallExpr commitCall)) {
                return new Chain();
            }
            if (!"commit".equals(commitCall.getNameAsString())) {
                return new Chain();
            }
            switch (parentCall.getNameAsString()) {
                case "putString":
                    if (parentCall.getArguments().size() == 2) {
                        ValueRef key = convertExpression(parentCall.getArgument(0), JavaToBlocksScope.TypeHint.STRING, true);
                        ValueRef value = convertExpression(parentCall.getArgument(1), JavaToBlocksScope.TypeHint.STRING, true);
                        if (key != null && value != null) {
                            BlockBean block = createBuiltInBlock("fileSetData");
                            block.parameters.add(symbol.getName());
                            block.parameters.add(key.asParameter());
                            block.parameters.add(value.asParameter());
                            return chainOf(block);
                        }
                    }
                    break;
                case "remove":
                    if (parentCall.getArguments().size() == 1) {
                        ValueRef key = convertExpression(parentCall.getArgument(0), JavaToBlocksScope.TypeHint.STRING, true);
                        if (key != null) {
                            BlockBean block = createBuiltInBlock("fileRemoveData");
                            block.parameters.add(symbol.getName());
                            block.parameters.add(key.asParameter());
                            return chainOf(block);
                        }
                    }
                    break;
                default:
                    break;
            }
            return new Chain();
        }

        private Chain convertNestedBody(Statement statement) {
            if (statement instanceof BlockStmt blockStmt) {
                return convertStatements(blockStmt.getStatements());
            }
            return convertStatement(statement);
        }

        private boolean isCurrentInstanceScope(Expression expression) {
            if (expression == null) {
                return false;
            }
            if (expression.isThisExpr()) {
                return true;
            }
            String text = expression.toString();
            return text.endsWith(".this");
        }

        private Chain fallbackStatement(Object node, String message) {
            if (node instanceof Statement statement) {
                diagnostics.add(JavaToBlocksDiagnostic.warning(statement, message));
                String source = statement.toString().trim();
                BlockBean block = createBuiltInBlock("addSourceDirectly");
                block.parameters.add(source);
                opaqueStatements++;
                return chainOf(block);
            }
            diagnostics.add(JavaToBlocksDiagnostic.warning(message));
            BlockBean block = createBuiltInBlock("addSourceDirectly");
            block.parameters.add(String.valueOf(node));
            opaqueStatements++;
            return chainOf(block);
        }

        private ValueRef convertExpression(Expression expression, JavaToBlocksScope.TypeHint expectedType, boolean allowOpaque) {
            if (expression == null) {
                return null;
            }
            if (expression instanceof EnclosedExpr enclosedExpr) {
                return convertExpression(enclosedExpr.getInner(), expectedType, allowOpaque);
            }
            if (expression instanceof BooleanLiteralExpr booleanLiteralExpr) {
                supportedExpressions++;
                return ValueRef.inline(Boolean.toString(booleanLiteralExpr.getValue()), JavaToBlocksScope.TypeHint.BOOLEAN);
            }
            if (expression instanceof StringLiteralExpr stringLiteralExpr) {
                supportedExpressions++;
                return ValueRef.inline(stringLiteralExpr.asString(), JavaToBlocksScope.TypeHint.STRING);
            }
            if (expression instanceof CharLiteralExpr charLiteralExpr) {
                supportedExpressions++;
                return ValueRef.inline(charLiteralExpr.asChar() + "", JavaToBlocksScope.TypeHint.STRING);
            }
            if (expression instanceof IntegerLiteralExpr integerLiteralExpr) {
                supportedExpressions++;
                return ValueRef.inline(integerLiteralExpr.asNumber().toString(), JavaToBlocksScope.TypeHint.NUMBER);
            }
            if (expression instanceof LongLiteralExpr longLiteralExpr) {
                supportedExpressions++;
                return ValueRef.inline(longLiteralExpr.asLong() + "", JavaToBlocksScope.TypeHint.NUMBER);
            }
            if (expression instanceof DoubleLiteralExpr doubleLiteralExpr) {
                supportedExpressions++;
                return ValueRef.inline(doubleLiteralExpr.asDouble() + "", JavaToBlocksScope.TypeHint.NUMBER);
            }
            if (expression instanceof NullLiteralExpr) {
                if (allowOpaque) {
                    return fallbackExpression(expression, expectedType);
                }
                return null;
            }
            if (expression instanceof NameExpr nameExpr) {
                return convertNameReference(nameExpr.getNameAsString(), expectedType, allowOpaque, expression);
            }
            if (expression instanceof FieldAccessExpr fieldAccessExpr) {
                return convertNameReference(fieldAccessExpr.toString(), expectedType, allowOpaque, expression);
            }
            if (expression instanceof UnaryExpr unaryExpr) {
                return convertUnaryExpression(unaryExpr, expectedType, allowOpaque);
            }
            if (expression instanceof BinaryExpr binaryExpr) {
                return convertBinaryExpression(binaryExpr, expectedType, allowOpaque);
            }
            if (expression instanceof MethodCallExpr methodCallExpr) {
                return convertMethodCallExpression(methodCallExpr, expectedType, allowOpaque);
            }
            if (expression instanceof ObjectCreationExpr objectCreationExpr) {
                return convertObjectCreationExpression(objectCreationExpr, expectedType, allowOpaque);
            }
            if (expression instanceof AssignExpr) {
                return allowOpaque ? fallbackExpression(expression, expectedType) : null;
            }
            return allowOpaque ? fallbackExpression(expression, expectedType) : null;
        }

        private ValueRef convertNameReference(String name, JavaToBlocksScope.TypeHint expectedType, boolean allowOpaque, Expression sourceNode) {
            JavaToBlocksScope.Symbol symbol = scope.find(name);
            if (symbol != null) {
                BlockBean block = symbol.isEventArgument() ? createEventArgumentBlock(symbol) : createVariableReferenceBlock(symbol);
                supportedExpressions++;
                return ValueRef.block(block, symbol.getTypeHint());
            }
            if (allowOpaque) {
                return fallbackExpression(sourceNode, expectedType);
            }
            return null;
        }

        private ValueRef convertUnaryExpression(UnaryExpr unaryExpr, JavaToBlocksScope.TypeHint expectedType, boolean allowOpaque) {
            if (unaryExpr.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {
                ValueRef inner = convertExpression(unaryExpr.getExpression(), JavaToBlocksScope.TypeHint.BOOLEAN, allowOpaque);
                if (inner != null) {
                    BlockBean block = createBuiltInBlock("not");
                    block.parameters.add(inner.asParameter());
                    supportedExpressions++;
                    return ValueRef.block(block, JavaToBlocksScope.TypeHint.BOOLEAN);
                }
            }
            if ((unaryExpr.getOperator() == UnaryExpr.Operator.MINUS || unaryExpr.getOperator() == UnaryExpr.Operator.PLUS)
                    && (unaryExpr.getExpression() instanceof IntegerLiteralExpr
                    || unaryExpr.getExpression() instanceof LongLiteralExpr
                    || unaryExpr.getExpression() instanceof DoubleLiteralExpr)) {
                supportedExpressions++;
                return ValueRef.inline(unaryExpr.toString(), JavaToBlocksScope.TypeHint.NUMBER);
            }
            return allowOpaque ? fallbackExpression(unaryExpr, expectedType) : null;
        }

        private ValueRef convertBinaryExpression(BinaryExpr binaryExpr, JavaToBlocksScope.TypeHint expectedType, boolean allowOpaque) {
            BinaryExpr.Operator operator = binaryExpr.getOperator();
            JavaToBlocksScope.TypeHint inferred = inferBinaryType(binaryExpr, expectedType);
            if (operator == BinaryExpr.Operator.NOT_EQUALS) {
                ValueRef equalsRef = convertBinaryExpression(new BinaryExpr(binaryExpr.getLeft(), binaryExpr.getRight(), BinaryExpr.Operator.EQUALS),
                        JavaToBlocksScope.TypeHint.BOOLEAN, allowOpaque);
                if (equalsRef != null) {
                    BlockBean notBlock = createBuiltInBlock("not");
                    notBlock.parameters.add(equalsRef.asParameter());
                    supportedExpressions++;
                    return ValueRef.block(notBlock, JavaToBlocksScope.TypeHint.BOOLEAN);
                }
                return allowOpaque ? fallbackExpression(binaryExpr, expectedType) : null;
            }
            if (operator == BinaryExpr.Operator.GREATER_EQUALS || operator == BinaryExpr.Operator.LESS_EQUALS) {
                BinaryExpr.Operator reducedOperator = operator == BinaryExpr.Operator.GREATER_EQUALS
                        ? BinaryExpr.Operator.LESS
                        : BinaryExpr.Operator.GREATER;
                ValueRef reduced = convertBinaryExpression(new BinaryExpr(binaryExpr.getLeft(), binaryExpr.getRight(), reducedOperator),
                        JavaToBlocksScope.TypeHint.BOOLEAN, allowOpaque);
                if (reduced != null) {
                    BlockBean notBlock = createBuiltInBlock("not");
                    notBlock.parameters.add(reduced.asParameter());
                    supportedExpressions++;
                    return ValueRef.block(notBlock, JavaToBlocksScope.TypeHint.BOOLEAN);
                }
                return allowOpaque ? fallbackExpression(binaryExpr, expectedType) : null;
            }
            ValueRef left = convertExpression(binaryExpr.getLeft(), inferOperandHint(operator, inferred), allowOpaque);
            ValueRef right = convertExpression(binaryExpr.getRight(), inferOperandHint(operator, inferred), allowOpaque);
            if (left == null || right == null) {
                return allowOpaque ? fallbackExpression(binaryExpr, expectedType) : null;
            }

            String opcode = switch (operator) {
                case PLUS -> inferred == JavaToBlocksScope.TypeHint.STRING ? "stringJoin" : "+";
                case MINUS -> "-";
                case MULTIPLY -> "*";
                case DIVIDE -> "/";
                case REMAINDER -> "%";
                case GREATER -> ">";
                case LESS -> "<";
                case EQUALS -> inferred == JavaToBlocksScope.TypeHint.STRING ? "stringEquals" : "=";
                case AND -> "&&";
                case OR -> "||";
                default -> null;
            };
            if (opcode == null) {
                return allowOpaque ? fallbackExpression(binaryExpr, expectedType) : null;
            }
            BlockBean block = createBuiltInBlock(opcode);
            if ("stringJoin".equals(opcode)) {
                block.parameters.add(left.asParameter());
                block.parameters.add(right.asParameter());
            } else if ("stringEquals".equals(opcode)) {
                block.parameters.add(left.asParameter());
                block.parameters.add(right.asParameter());
            } else {
                block.parameters.add(left.asParameter());
                block.parameters.add(right.asParameter());
            }
            supportedExpressions++;
            return ValueRef.block(block, inferred);
        }

        private JavaToBlocksScope.TypeHint inferBinaryType(BinaryExpr binaryExpr, JavaToBlocksScope.TypeHint expectedType) {
            return switch (binaryExpr.getOperator()) {
                case AND, OR, EQUALS, NOT_EQUALS, GREATER, GREATER_EQUALS, LESS, LESS_EQUALS -> JavaToBlocksScope.TypeHint.BOOLEAN;
                case PLUS -> {
                    JavaToBlocksScope.TypeHint leftHint = guessExpressionType(binaryExpr.getLeft());
                    JavaToBlocksScope.TypeHint rightHint = guessExpressionType(binaryExpr.getRight());
                    if (leftHint == JavaToBlocksScope.TypeHint.STRING || rightHint == JavaToBlocksScope.TypeHint.STRING
                            || expectedType == JavaToBlocksScope.TypeHint.STRING) {
                        yield JavaToBlocksScope.TypeHint.STRING;
                    }
                    yield JavaToBlocksScope.TypeHint.NUMBER;
                }
                default -> JavaToBlocksScope.TypeHint.NUMBER;
            };
        }

        private JavaToBlocksScope.TypeHint inferOperandHint(BinaryExpr.Operator operator, JavaToBlocksScope.TypeHint expressionType) {
            return switch (operator) {
                case AND, OR -> JavaToBlocksScope.TypeHint.BOOLEAN;
                case GREATER, GREATER_EQUALS, LESS, LESS_EQUALS, MINUS, MULTIPLY, DIVIDE, REMAINDER -> JavaToBlocksScope.TypeHint.NUMBER;
                case EQUALS, NOT_EQUALS -> expressionType == JavaToBlocksScope.TypeHint.STRING
                        ? JavaToBlocksScope.TypeHint.STRING
                        : JavaToBlocksScope.TypeHint.OBJECT;
                case PLUS -> expressionType;
                default -> JavaToBlocksScope.TypeHint.OBJECT;
            };
        }

        private ValueRef convertMethodCallExpression(MethodCallExpr call, JavaToBlocksScope.TypeHint expectedType, boolean allowOpaque) {
            if (call.getScope().isPresent()) {
                NameInfo scopeName = extractName(call.getScope().get());
                JavaToBlocksScope.Symbol scopedSymbol = scopeName == null ? null : scope.find(scopeName.normalizedLookupName);
                if (scopedSymbol != null) {
                    ValueRef collectionRef = convertCollectionExpression(call, scopedSymbol, allowOpaque);
                    if (collectionRef != null) {
                        return collectionRef;
                    }
                    ValueRef viewRef = convertViewExpression(call, scopedSymbol, allowOpaque);
                    if (viewRef != null) {
                        return viewRef;
                    }
                    ValueRef componentRef = convertComponentExpression(call, scopedSymbol, allowOpaque);
                    if (componentRef != null) {
                        return componentRef;
                    }
                }
                ValueRef stringRef = convertStringMethodExpression(call, allowOpaque);
                if (stringRef != null) {
                    return stringRef;
                }
            }
            return allowOpaque ? fallbackExpression(call, expectedType) : null;
        }

        private ValueRef convertCollectionExpression(MethodCallExpr call, JavaToBlocksScope.Symbol symbol, boolean allowOpaque) {
            String method = call.getNameAsString();
            if (symbol.getTypeHint() == JavaToBlocksScope.TypeHint.MAP) {
                switch (method) {
                    case "get":
                        if (call.getArguments().size() == 1) {
                            ValueRef key = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.STRING, allowOpaque);
                            if (key != null) {
                                BlockBean block = createBuiltInBlock("mapGet");
                                block.parameters.add(symbol.getName());
                                block.parameters.add(key.asParameter());
                                supportedExpressions++;
                                return ValueRef.block(block, JavaToBlocksScope.TypeHint.STRING);
                            }
                        }
                        break;
                    case "containsKey":
                        if (call.getArguments().size() == 1) {
                            ValueRef key = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.STRING, allowOpaque);
                            if (key != null) {
                                BlockBean block = createBuiltInBlock("mapContainKey");
                                block.parameters.add(symbol.getName());
                                block.parameters.add(key.asParameter());
                                supportedExpressions++;
                                return ValueRef.block(block, JavaToBlocksScope.TypeHint.BOOLEAN);
                            }
                        }
                        break;
                    case "size":
                        if (call.getArguments().isEmpty()) {
                            BlockBean block = createBuiltInBlock("mapSize");
                            block.parameters.add(symbol.getName());
                            supportedExpressions++;
                            return ValueRef.block(block, JavaToBlocksScope.TypeHint.NUMBER);
                        }
                        break;
                    case "isEmpty":
                        if (call.getArguments().isEmpty()) {
                            BlockBean block = createBuiltInBlock("mapIsEmpty");
                            block.parameters.add(symbol.getName());
                            supportedExpressions++;
                            return ValueRef.block(block, JavaToBlocksScope.TypeHint.BOOLEAN);
                        }
                        break;
                    default:
                        break;
                }
            }
            if (symbol.getTypeHint() == JavaToBlocksScope.TypeHint.LIST) {
                switch (method) {
                    case "get":
                        if (call.getArguments().size() == 1) {
                            ValueRef index = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.NUMBER, allowOpaque);
                            if (index != null) {
                                String opcode = switch (symbol.getTypeName()) {
                                    case "List Number" -> "getAtListInt";
                                    case "List String" -> "getAtListStr";
                                    default -> null;
                                };
                                if (opcode != null) {
                                    BlockBean block = createBuiltInBlock(opcode);
                                    block.parameters.add(index.asParameter());
                                    block.parameters.add(symbol.getName());
                                    supportedExpressions++;
                                    return ValueRef.block(block, inferListItemHint(symbol.getTypeName()));
                                }
                            }
                        }
                        break;
                    case "contains":
                        if (call.getArguments().size() == 1) {
                            ValueRef item = convertExpression(call.getArgument(0), inferListItemHint(symbol.getTypeName()), allowOpaque);
                            if (item != null) {
                                String opcode = switch (symbol.getTypeName()) {
                                    case "List Number" -> "containListInt";
                                    case "List String" -> "containListStr";
                                    default -> null;
                                };
                                if (opcode != null) {
                                    BlockBean block = createBuiltInBlock(opcode);
                                    block.parameters.add(symbol.getName());
                                    block.parameters.add(item.asParameter());
                                    supportedExpressions++;
                                    return ValueRef.block(block, JavaToBlocksScope.TypeHint.BOOLEAN);
                                }
                            }
                        }
                        break;
                    case "indexOf":
                        if (call.getArguments().size() == 1) {
                            ValueRef item = convertExpression(call.getArgument(0), inferListItemHint(symbol.getTypeName()), allowOpaque);
                            if (item != null) {
                                String opcode = switch (symbol.getTypeName()) {
                                    case "List Number" -> "indexListInt";
                                    case "List String" -> "indexListStr";
                                    default -> null;
                                };
                                if (opcode != null) {
                                    BlockBean block = createBuiltInBlock(opcode);
                                    block.parameters.add(item.asParameter());
                                    block.parameters.add(symbol.getName());
                                    supportedExpressions++;
                                    return ValueRef.block(block, JavaToBlocksScope.TypeHint.NUMBER);
                                }
                            }
                        }
                        break;
                    case "size":
                        if (call.getArguments().isEmpty()) {
                            BlockBean block = createBuiltInBlock("lengthList");
                            block.parameters.add(symbol.getName());
                            supportedExpressions++;
                            return ValueRef.block(block, JavaToBlocksScope.TypeHint.NUMBER);
                        }
                        break;
                    default:
                        break;
                }
            }
            return null;
        }

        private ValueRef convertViewExpression(MethodCallExpr call, JavaToBlocksScope.Symbol symbol, boolean allowOpaque) {
            if (symbol.getTypeHint() != JavaToBlocksScope.TypeHint.VIEW) {
                return null;
            }
            String method = call.getNameAsString();
            switch (method) {
                case "getText":
                    if (call.getArguments().isEmpty()) {
                        BlockBean block = createBuiltInBlock("getText");
                        block.parameters.add(symbol.getName());
                        supportedExpressions++;
                        return ValueRef.block(block, JavaToBlocksScope.TypeHint.STRING);
                    }
                    break;
                case "isEnabled":
                    if (call.getArguments().isEmpty()) {
                        BlockBean block = createBuiltInBlock("getEnable");
                        block.parameters.add(symbol.getName());
                        supportedExpressions++;
                        return ValueRef.block(block, JavaToBlocksScope.TypeHint.BOOLEAN);
                    }
                    break;
                case "isChecked":
                    if (call.getArguments().isEmpty()) {
                        BlockBean block = createBuiltInBlock("getChecked");
                        block.parameters.add(symbol.getName());
                        supportedExpressions++;
                        return ValueRef.block(block, JavaToBlocksScope.TypeHint.BOOLEAN);
                    }
                    break;
                case "getProgress":
                    if (call.getArguments().isEmpty()) {
                        BlockBean block = createBuiltInBlock("seekBarGetProgress");
                        block.parameters.add(symbol.getName());
                        supportedExpressions++;
                        return ValueRef.block(block, JavaToBlocksScope.TypeHint.NUMBER);
                    }
                    break;
                default:
                    break;
            }
            return allowOpaque ? fallbackExpression(call, JavaToBlocksScope.TypeHint.OBJECT) : null;
        }

        private ValueRef convertComponentExpression(MethodCallExpr call, JavaToBlocksScope.Symbol symbol, boolean allowOpaque) {
            if (symbol.getTypeHint() != JavaToBlocksScope.TypeHint.COMPONENT) {
                return null;
            }
            if ("File".equals(symbol.getTypeName())) {
                if ("getString".equals(call.getNameAsString()) && call.getArguments().size() >= 1) {
                    ValueRef key = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.STRING, allowOpaque);
                    if (key != null) {
                        BlockBean block = createBuiltInBlock("fileGetData");
                        block.parameters.add(symbol.getName());
                        block.parameters.add(key.asParameter());
                        supportedExpressions++;
                        return ValueRef.block(block, JavaToBlocksScope.TypeHint.STRING);
                    }
                }
            }
            return allowOpaque ? fallbackExpression(call, JavaToBlocksScope.TypeHint.OBJECT) : null;
        }

        private ValueRef convertStringMethodExpression(MethodCallExpr call, boolean allowOpaque) {
            String method = call.getNameAsString();
            switch (method) {
                case "valueOf":
                    if (call.getArguments().size() == 1 && call.getScope().isPresent() && "String".equals(call.getScope().get().toString())) {
                        ValueRef value = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.OBJECT, allowOpaque);
                        if (value != null) {
                            BlockBean block = createBuiltInBlock("toString");
                            block.parameters.add(value.asParameter());
                            supportedExpressions++;
                            return ValueRef.block(block, JavaToBlocksScope.TypeHint.STRING);
                        }
                    }
                    break;
                case "toString":
                    if (call.getArguments().isEmpty() && call.getScope().isPresent()) {
                        ValueRef value = convertExpression(call.getScope().get(), JavaToBlocksScope.TypeHint.OBJECT, allowOpaque);
                        if (value != null) {
                            BlockBean block = createBuiltInBlock("toString");
                            block.parameters.add(value.asParameter());
                            supportedExpressions++;
                            return ValueRef.block(block, JavaToBlocksScope.TypeHint.STRING);
                        }
                    }
                    break;
                case "length":
                    if (call.getArguments().isEmpty() && call.getScope().isPresent()) {
                        ValueRef value = convertExpression(call.getScope().get(), JavaToBlocksScope.TypeHint.STRING, allowOpaque);
                        if (value != null) {
                            BlockBean block = createBuiltInBlock("stringLength");
                            block.parameters.add(value.asParameter());
                            supportedExpressions++;
                            return ValueRef.block(block, JavaToBlocksScope.TypeHint.NUMBER);
                        }
                    }
                    break;
                case "substring":
                    if (call.getArguments().size() == 2 && call.getScope().isPresent()) {
                        ValueRef value = convertExpression(call.getScope().get(), JavaToBlocksScope.TypeHint.STRING, allowOpaque);
                        ValueRef start = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.NUMBER, allowOpaque);
                        ValueRef end = convertExpression(call.getArgument(1), JavaToBlocksScope.TypeHint.NUMBER, allowOpaque);
                        if (value != null && start != null && end != null) {
                            BlockBean block = createBuiltInBlock("stringSub");
                            block.parameters.add(value.asParameter());
                            block.parameters.add(start.asParameter());
                            block.parameters.add(end.asParameter());
                            supportedExpressions++;
                            return ValueRef.block(block, JavaToBlocksScope.TypeHint.STRING);
                        }
                    }
                    break;
                case "equals":
                    if (call.getArguments().size() == 1 && call.getScope().isPresent()) {
                        ValueRef left = convertExpression(call.getScope().get(), JavaToBlocksScope.TypeHint.STRING, allowOpaque);
                        ValueRef right = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.STRING, allowOpaque);
                        if (left != null && right != null) {
                            BlockBean block = createBuiltInBlock("stringEquals");
                            block.parameters.add(left.asParameter());
                            block.parameters.add(right.asParameter());
                            supportedExpressions++;
                            return ValueRef.block(block, JavaToBlocksScope.TypeHint.BOOLEAN);
                        }
                    }
                    break;
                case "contains":
                    if (call.getArguments().size() == 1 && call.getScope().isPresent()) {
                        ValueRef left = convertExpression(call.getScope().get(), JavaToBlocksScope.TypeHint.STRING, allowOpaque);
                        ValueRef right = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.STRING, allowOpaque);
                        if (left != null && right != null) {
                            BlockBean block = createBuiltInBlock("stringContains");
                            block.parameters.add(left.asParameter());
                            block.parameters.add(right.asParameter());
                            supportedExpressions++;
                            return ValueRef.block(block, JavaToBlocksScope.TypeHint.BOOLEAN);
                        }
                    }
                    break;
                case "replace":
                    if (call.getArguments().size() == 2 && call.getScope().isPresent()) {
                        ValueRef base = convertExpression(call.getScope().get(), JavaToBlocksScope.TypeHint.STRING, allowOpaque);
                        ValueRef target = convertExpression(call.getArgument(0), JavaToBlocksScope.TypeHint.STRING, allowOpaque);
                        ValueRef replacement = convertExpression(call.getArgument(1), JavaToBlocksScope.TypeHint.STRING, allowOpaque);
                        if (base != null && target != null && replacement != null) {
                            BlockBean block = createBuiltInBlock("stringReplace");
                            block.parameters.add(base.asParameter());
                            block.parameters.add(target.asParameter());
                            block.parameters.add(replacement.asParameter());
                            supportedExpressions++;
                            return ValueRef.block(block, JavaToBlocksScope.TypeHint.STRING);
                        }
                    }
                    break;
                default:
                    break;
            }
            return null;
        }

        private ValueRef convertObjectCreationExpression(ObjectCreationExpr expression, JavaToBlocksScope.TypeHint expectedType, boolean allowOpaque) {
            if (expression.getType().asString().toLowerCase(Locale.US).contains("hashmap")) {
                return allowOpaque ? fallbackExpression(expression, JavaToBlocksScope.TypeHint.MAP) : null;
            }
            return allowOpaque ? fallbackExpression(expression, expectedType) : null;
        }

        private ValueRef fallbackExpression(Expression expression, JavaToBlocksScope.TypeHint expectedType) {
            JavaToBlocksScope.TypeHint hint = expectedType == null || expectedType == JavaToBlocksScope.TypeHint.UNKNOWN
                    ? guessExpressionType(expression)
                    : expectedType;
            String opcode = switch (hint) {
                case BOOLEAN -> "asdBoolean";
                case NUMBER -> "asdNumber";
                default -> "asdString";
            };
            BlockBean block = createBuiltInBlock(opcode);
            block.parameters.add(expression.toString().trim());
            opaqueExpressions++;
            diagnostics.add(JavaToBlocksDiagnostic.warning(expression,
                    "Unsupported expression was preserved as a typed source-backed block."));
            return ValueRef.block(block, hint);
        }

        private JavaToBlocksScope.TypeHint guessExpressionType(Expression expression) {
            if (expression instanceof BooleanLiteralExpr) {
                return JavaToBlocksScope.TypeHint.BOOLEAN;
            }
            if (expression instanceof StringLiteralExpr || expression instanceof CharLiteralExpr) {
                return JavaToBlocksScope.TypeHint.STRING;
            }
            if (expression instanceof IntegerLiteralExpr || expression instanceof LongLiteralExpr || expression instanceof DoubleLiteralExpr) {
                return JavaToBlocksScope.TypeHint.NUMBER;
            }
            if (expression instanceof NameExpr nameExpr) {
                JavaToBlocksScope.Symbol symbol = scope.find(nameExpr.getNameAsString());
                return symbol != null ? symbol.getTypeHint() : JavaToBlocksScope.TypeHint.UNKNOWN;
            }
            if (expression instanceof FieldAccessExpr fieldAccessExpr) {
                JavaToBlocksScope.Symbol symbol = scope.find(fieldAccessExpr.toString());
                return symbol != null ? symbol.getTypeHint() : JavaToBlocksScope.TypeHint.UNKNOWN;
            }
            if (expression instanceof BinaryExpr binaryExpr) {
                return inferBinaryType(binaryExpr, JavaToBlocksScope.TypeHint.UNKNOWN);
            }
            if (expression instanceof MethodCallExpr methodCallExpr) {
                String name = methodCallExpr.getNameAsString();
                if (name.startsWith("is") || name.startsWith("has") || name.startsWith("can")) {
                    return JavaToBlocksScope.TypeHint.BOOLEAN;
                }
                if (name.contains("size") || name.contains("length") || name.contains("count") || name.contains("progress")) {
                    return JavaToBlocksScope.TypeHint.NUMBER;
                }
                if (name.contains("text") || name.contains("string") || name.contains("format")) {
                    return JavaToBlocksScope.TypeHint.STRING;
                }
            }
            return JavaToBlocksScope.TypeHint.UNKNOWN;
        }

        private boolean isBooleanLiteralTrue(ValueRef ref) {
            return ref != null && ref.blockId == null && "true".equals(ref.inlineValue);
        }

        private BlockBean createBuiltInBlock(String opcode) {
            ExtraBlockInfo info = BlockLoader.getBlockInfo(opcode);
            if (info.isMissing) {
                info = BlockLoader.getBlockFromProject("", opcode);
            }
            String type = info.getType() == null || info.getType().isEmpty() ? " " : info.getType();
            String typeName = info.getTypeName() == null ? "" : info.getTypeName();
            String spec = info.getSpec() == null ? "" : info.getSpec();
            BlockBean block = new BlockBean(String.valueOf(nextId++), spec, type, typeName, opcode);
            block.color = info.getColor() != 0 ? info.getColor() : kq.a(opcode, type);
            blocks.add(block);
            return block;
        }

        private BlockBean createVariableReferenceBlock(JavaToBlocksScope.Symbol symbol) {
            BlockBean block = new BlockBean(String.valueOf(nextId++), symbol.getName(),
                    symbol.getBlockType(), symbol.getTypeName(), "getVar");
            block.color = kq.a("getVar", symbol.getBlockType());
            blocks.add(block);
            return block;
        }

        private BlockBean createEventArgumentBlock(JavaToBlocksScope.Symbol symbol) {
            String spec = symbol.getName().startsWith("_") ? symbol.getName().substring(1) : symbol.getName();
            BlockBean block = new BlockBean(String.valueOf(nextId++), spec,
                    symbol.getBlockType(), symbol.getTypeName(), "getArg");
            block.color = kq.a("getVar", symbol.getBlockType());
            blocks.add(block);
            return block;
        }

        private String referenceOf(BlockBean block) {
            return "@" + block.id;
        }

        private NameInfo extractName(Expression expression) {
            if (expression instanceof NameExpr nameExpr) {
                return new NameInfo(nameExpr.getNameAsString(), nameExpr.getNameAsString());
            }
            if (expression instanceof FieldAccessExpr fieldAccessExpr) {
                return new NameInfo(fieldAccessExpr.toString(), fieldAccessExpr.toString());
            }
            return null;
        }

        private Chain chainOf(BlockBean block) {
            Chain chain = new Chain();
            chain.headId = Integer.parseInt(block.id);
            chain.tailId = chain.headId;
            return chain;
        }

        private final class Chain {
            int headId = -1;
            int tailId = -1;

            boolean isEmpty() {
                return headId < 0;
            }

            void append(Chain other) {
                if (other == null || other.isEmpty()) {
                    return;
                }
                if (isEmpty()) {
                    headId = other.headId;
                    tailId = other.tailId;
                    return;
                }
                BlockBean tail = findBlock(tailId);
                if (tail != null) {
                    tail.nextBlock = other.headId;
                }
                tailId = other.tailId;
            }

            private BlockBean findBlock(int id) {
                String expected = String.valueOf(id);
                for (int i = blocks.size() - 1; i >= 0; i--) {
                    BlockBean candidate = blocks.get(i);
                    if (expected.equals(candidate.id)) {
                        return candidate;
                    }
                }
                return null;
            }
        }
    }

    private static final class NameInfo {
        final String displayName;
        final String normalizedLookupName;

        NameInfo(String displayName, String normalizedLookupName) {
            this.displayName = displayName;
            this.normalizedLookupName = normalizedLookupName;
        }
    }

    private static final class ValueRef {
        final String inlineValue;
        final Integer blockId;
        final JavaToBlocksScope.TypeHint typeHint;

        private ValueRef(String inlineValue, Integer blockId, JavaToBlocksScope.TypeHint typeHint) {
            this.inlineValue = inlineValue;
            this.blockId = blockId;
            this.typeHint = typeHint;
        }

        static ValueRef inline(String inlineValue, JavaToBlocksScope.TypeHint typeHint) {
            return new ValueRef(inlineValue, null, typeHint);
        }

        static ValueRef block(BlockBean block, JavaToBlocksScope.TypeHint typeHint) {
            return new ValueRef(null, Integer.parseInt(block.id), typeHint);
        }

        String asParameter() {
            return blockId != null ? "@" + blockId : inlineValue;
        }
    }

}