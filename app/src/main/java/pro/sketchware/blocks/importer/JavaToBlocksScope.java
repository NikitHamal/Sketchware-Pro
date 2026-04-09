package pro.sketchware.blocks.importer;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class JavaToBlocksScope {

    public enum TypeHint {
        BOOLEAN,
        NUMBER,
        STRING,
        MAP,
        LIST,
        VIEW,
        COMPONENT,
        OBJECT,
        UNKNOWN
    }

    public static final class Symbol {
        private final String name;
        private final String blockType;
        private final String typeName;
        private final TypeHint typeHint;
        private final boolean eventArgument;

        public Symbol(String name, String blockType, String typeName, TypeHint typeHint, boolean eventArgument) {
            this.name = name;
            this.blockType = blockType;
            this.typeName = typeName;
            this.typeHint = typeHint;
            this.eventArgument = eventArgument;
        }

        public String getName() {
            return name;
        }

        public String getBlockType() {
            return blockType;
        }

        public String getTypeName() {
            return typeName;
        }

        public TypeHint getTypeHint() {
            return typeHint;
        }

        public boolean isEventArgument() {
            return eventArgument;
        }
    }

    private final LinkedHashMap<String, Symbol> symbols = new LinkedHashMap<>();

    public void registerSymbol(String name, String blockType, String typeName, TypeHint typeHint) {
        registerSymbol(name, blockType, typeName, typeHint, false);
    }

    public void registerEventArgument(String name, String blockType, String typeName, TypeHint typeHint) {
        registerSymbol(name, blockType, typeName, typeHint, true);
    }

    public void registerSymbol(String name, String blockType, String typeName, TypeHint typeHint, boolean eventArgument) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        String normalized = normalize(name);
        symbols.put(normalized, new Symbol(name.trim(), blockType, typeName == null ? "" : typeName, typeHint, eventArgument));
    }

    public Symbol find(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String normalized = normalize(name);
        Symbol direct = symbols.get(normalized);
        if (direct != null) {
            return direct;
        }

        // Generated code may use underscored event arguments while users paste the plain label or vice versa.
        if (normalized.startsWith("_")) {
            Symbol withoutUnderscore = symbols.get(normalized.substring(1));
            if (withoutUnderscore != null) {
                return withoutUnderscore;
            }
        } else {
            Symbol withUnderscore = symbols.get("_" + normalized);
            if (withUnderscore != null) {
                return withUnderscore;
            }
        }
        return null;
    }

    public Collection<Symbol> getSymbols() {
        return Collections.unmodifiableCollection(symbols.values());
    }

    public static TypeHint typeHintForJavaType(String javaType) {
        if (javaType == null || javaType.trim().isEmpty()) {
            return TypeHint.UNKNOWN;
        }
        String normalized = javaType.trim().toLowerCase(Locale.US);
        return switch (normalized) {
            case "boolean", "java.lang.boolean" -> TypeHint.BOOLEAN;
            case "double", "java.lang.double", "int", "java.lang.integer", "float", "java.lang.float",
                    "long", "java.lang.long", "short", "java.lang.short", "byte", "java.lang.byte" -> TypeHint.NUMBER;
            case "string", "java.lang.string", "charsequence", "java.lang.charsequence", "char", "java.lang.character" -> TypeHint.STRING;
            default -> {
                if (normalized.contains("hashmap") || normalized.contains("map<")) {
                    yield TypeHint.MAP;
                }
                if (normalized.contains("arraylist") || normalized.contains("list<") || normalized.endsWith("[]")) {
                    yield TypeHint.LIST;
                }
                if (normalized.contains("view") || normalized.contains("layout")) {
                    yield TypeHint.VIEW;
                }
                if (normalized.contains("intent") || normalized.contains("requestnetwork") || normalized.contains("dialog")
                        || normalized.contains("calendar") || normalized.contains("firebase") || normalized.contains("speech")
                        || normalized.contains("animator") || normalized.contains("player") || normalized.contains("picker")
                        || normalized.contains("vibrator") || normalized.contains("locationmanager") || normalized.contains("bluetooth")) {
                    yield TypeHint.COMPONENT;
                }
                yield TypeHint.OBJECT;
            }
        };
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.US);
    }
}
