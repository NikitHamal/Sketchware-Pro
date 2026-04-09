package pro.sketchware.blocks.importer;

import android.util.Pair;

import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.eC;
import a.a.a.jC;
import mod.pranav.viewbinding.ViewBindingBuilder;
import pro.sketchware.utility.CustomVariableUtil;

public final class JavaToBlocksProjectScopeFactory {

    private static final Pattern EVENT_ARG_PATTERN = Pattern.compile("%(?:([bds])\\.([A-Za-z0-9_]+)|m\\.[A-Za-z0-9_]+\\.([A-Za-z0-9_]+))");

    private JavaToBlocksProjectScopeFactory() {
    }

    public static JavaToBlocksScope create(String scId, ProjectFileBean projectFile, String eventTitleSpec,
                                           boolean isViewBindingEnabled) {
        JavaToBlocksScope scope = new JavaToBlocksScope();
        if (scId == null || scId.isEmpty() || projectFile == null) {
            return scope;
        }

        String javaName = projectFile.getJavaName();
        eC logic = jC.a(scId);
        registerProjectVariables(scope, logic, javaName);
        registerLists(scope, logic, javaName);
        registerComponents(scope, logic, javaName);
        registerViews(scope, logic, projectFile, isViewBindingEnabled);
        registerEventArguments(scope, eventTitleSpec);
        return scope;
    }

    private static void registerProjectVariables(JavaToBlocksScope scope, eC logic, String javaName) {
        for (String name : logic.e(javaName, 0)) {
            scope.registerSymbol(name, "b", "", JavaToBlocksScope.TypeHint.BOOLEAN);
        }
        for (String name : logic.e(javaName, 1)) {
            scope.registerSymbol(name, "d", "", JavaToBlocksScope.TypeHint.NUMBER);
        }
        for (String name : logic.e(javaName, 2)) {
            scope.registerSymbol(name, "s", "", JavaToBlocksScope.TypeHint.STRING);
        }
        for (String name : logic.e(javaName, 3)) {
            scope.registerSymbol(name, "a", "Map", JavaToBlocksScope.TypeHint.MAP);
        }
        for (String customVariable : logic.e(javaName, 5)) {
            String[] split = customVariable.split(" ", 2);
            if (split.length == 2) {
                scope.registerSymbol(split[1], "v", split[0], JavaToBlocksScope.TypeHint.OBJECT);
            }
        }
        for (String customVariable : logic.e(javaName, 6)) {
            String variableType = CustomVariableUtil.getVariableType(customVariable);
            String variableName = CustomVariableUtil.getVariableName(customVariable);
            if (variableType == null || variableName == null) {
                continue;
            }
            String blockType = switch (variableType) {
                case "boolean", "Boolean" -> "b";
                case "String" -> "s";
                case "double", "Double", "int", "Integer", "float", "Float", "long", "Long",
                        "short", "Short", "byte", "Byte" -> "d";
                default -> "v";
            };
            scope.registerSymbol(variableName, blockType, variableType,
                    JavaToBlocksScope.typeHintForJavaType(variableType));
        }
    }

    private static void registerLists(JavaToBlocksScope scope, eC logic, String javaName) {
        for (Pair<Integer, String> entry : logic.j(javaName)) {
            int type = entry.first;
            String name = entry.second;
            switch (type) {
                case 1 -> scope.registerSymbol(name, "l", "List Number", JavaToBlocksScope.TypeHint.LIST);
                case 2 -> scope.registerSymbol(name, "l", "List String", JavaToBlocksScope.TypeHint.LIST);
                case 3 -> scope.registerSymbol(name, "l", "List Map", JavaToBlocksScope.TypeHint.LIST);
                default -> {
                    String variableName = CustomVariableUtil.getVariableName(name);
                    if (variableName != null) {
                        scope.registerSymbol(variableName, "l", "List", JavaToBlocksScope.TypeHint.LIST);
                    }
                }
            }
        }
    }

    private static void registerComponents(JavaToBlocksScope scope, eC logic, String javaName) {
        ArrayList<ComponentBean> components = logic.e(javaName);
        for (ComponentBean component : components) {
            if (component.type == ComponentBean.COMPONENT_TYPE_FRAGMENT_ADAPTER) {
                continue;
            }
            scope.registerSymbol(component.componentId, "p", ComponentBean.getComponentTypeName(component.type),
                    JavaToBlocksScope.TypeHint.COMPONENT);
        }
    }

    private static void registerViews(JavaToBlocksScope scope, eC logic, ProjectFileBean projectFile,
                                      boolean isViewBindingEnabled) {
        String xmlName = projectFile.getXmlName();
        registerViewCollection(scope, logic.d(xmlName), isViewBindingEnabled, false);
        if (projectFile.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)) {
            registerViewCollection(scope, logic.d(projectFile.getDrawerXmlName()), isViewBindingEnabled, true);
        }
    }

    private static void registerViewCollection(JavaToBlocksScope scope, ArrayList<ViewBean> views,
                                               boolean isViewBindingEnabled, boolean drawer) {
        if (views == null) {
            return;
        }
        for (ViewBean view : views) {
            if (view == null || "include".equals(view.convert)) {
                continue;
            }
            String typeName = view.convert == null || view.convert.isEmpty()
                    ? ViewBean.getViewTypeName(view.type)
                    : view.convert.substring(view.convert.lastIndexOf('/') + 1);
            String rawId = drawer ? "_drawer_" + view.id : view.id;
            scope.registerSymbol(rawId, "v", typeName, JavaToBlocksScope.TypeHint.VIEW);
            if (drawer) {
                scope.registerSymbol(view.id, "v", typeName, JavaToBlocksScope.TypeHint.VIEW);
            }
            if (isViewBindingEnabled) {
                String bindingName = drawer
                        ? "binding.drawer." + ViewBindingBuilder.generateParameterFromId(view.id)
                        : "binding." + ViewBindingBuilder.generateParameterFromId(view.id);
                scope.registerSymbol(bindingName, "v", typeName, JavaToBlocksScope.TypeHint.VIEW);
            }
        }
    }

    private static void registerEventArguments(JavaToBlocksScope scope, String eventTitleSpec) {
        if (eventTitleSpec == null || eventTitleSpec.isEmpty()) {
            return;
        }
        Matcher matcher = EVENT_ARG_PATTERN.matcher(eventTitleSpec);
        while (matcher.find()) {
            String primitiveType = matcher.group(1);
            String primitiveName = matcher.group(2);
            String menuName = matcher.group(3);
            if (primitiveName != null) {
                registerArg(scope, primitiveName, primitiveType);
            } else if (menuName != null) {
                registerArg(scope, menuName, "m");
            }
        }
    }

    private static void registerArg(JavaToBlocksScope scope, String rawName, String kind) {
        String name = rawName.startsWith("_") ? rawName.substring(1) : rawName;
        String generatedName = "_" + name;
        switch (kind.toLowerCase(Locale.US)) {
            case "b" -> {
                scope.registerEventArgument(name, "b", "", JavaToBlocksScope.TypeHint.BOOLEAN);
                scope.registerEventArgument(generatedName, "b", "", JavaToBlocksScope.TypeHint.BOOLEAN);
            }
            case "d" -> {
                scope.registerEventArgument(name, "d", "", JavaToBlocksScope.TypeHint.NUMBER);
                scope.registerEventArgument(generatedName, "d", "", JavaToBlocksScope.TypeHint.NUMBER);
            }
            case "s" -> {
                scope.registerEventArgument(name, "s", "", JavaToBlocksScope.TypeHint.STRING);
                scope.registerEventArgument(generatedName, "s", "", JavaToBlocksScope.TypeHint.STRING);
            }
            default -> {
                scope.registerEventArgument(name, "v", "View", JavaToBlocksScope.TypeHint.VIEW);
                scope.registerEventArgument(generatedName, "v", "View", JavaToBlocksScope.TypeHint.VIEW);
            }
        }
    }
}
