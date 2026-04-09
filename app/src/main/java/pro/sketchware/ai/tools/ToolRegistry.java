package pro.sketchware.ai.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.ai.api.ToolDefinition;

/**
 * Registry that holds all available AI agent tools. Provides lookup by name and
 * conversion to API-compatible tool definitions.
 */
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    /**
     * Registers a tool in this registry. If a tool with the same name already exists,
     * it will be replaced.
     *
     * @param tool the tool to register
     */
    public void register(AgentTool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("Tool must not be null");
        }
        tools.put(tool.getName(), tool);
    }

    /**
     * Returns the tool with the given name, or null if not found.
     *
     * @param name the tool name
     * @return the tool, or null
     */
    public AgentTool getTool(String name) {
        return tools.get(name);
    }

    /**
     * Returns an unmodifiable list of all registered tools in registration order.
     */
    public List<AgentTool> getAllTools() {
        return Collections.unmodifiableList(new ArrayList<>(tools.values()));
    }

    /**
     * Converts all registered tools to API-compatible ToolDefinition objects.
     *
     * @return list of tool definitions for sending to the AI provider
     */
    public List<ToolDefinition> getToolDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            definitions.add(new ToolDefinition(
                    tool.getName(),
                    tool.getDescription(),
                    tool.getParametersSchema()
            ));
        }
        return definitions;
    }

    /**
     * Returns the number of registered tools.
     */
    public int size() {
        return tools.size();
    }

    /**
     * Checks whether a tool with the given name is registered.
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    /**
     * Creates a registry with all default tools registered.
     * This includes project, file, activity, layout, resource, compile, and library tools.
     *
     * @return a fully populated ToolRegistry
     */
    public static ToolRegistry createDefault() {
        ToolRegistry registry = new ToolRegistry();

        // Project tools
        registry.register(new ProjectTools.ListProjectsTool());
        registry.register(new ProjectTools.GetProjectInfoTool());
        registry.register(new ProjectTools.CreateProjectTool());
        registry.register(new ProjectTools.DeleteProjectTool());
        registry.register(new ProjectTools.DuplicateProjectTool());

        // File tools
        registry.register(new FileTools.ReadFileTool());
        registry.register(new FileTools.WriteFileTool());
        registry.register(new FileTools.DeleteFileTool());
        registry.register(new FileTools.ListFilesTool());
        registry.register(new FileTools.CopyFileTool());
        registry.register(new FileTools.MoveFileTool());

        // Activity tools
        registry.register(new ActivityTools.ListActivitiesTool());
        registry.register(new ActivityTools.CreateActivityTool());
        registry.register(new ActivityTools.DeleteActivityTool());

        // Layout tools
        registry.register(new LayoutTools.GetLayoutTool());
        registry.register(new LayoutTools.EditLayoutTool());

        // Resource tools
        registry.register(new ResourceTools.AddStringResourceTool());
        registry.register(new ResourceTools.AddColorResourceTool());
        registry.register(new ResourceTools.ListResourcesTool());

        // Compile and build tools
        registry.register(new CompileTools.GetCompileLogsTool());
        registry.register(new CompileTools.GetProjectStructureTool());
        registry.register(new BuildTools.BuildProjectTool());

        // Library and dependency tools
        registry.register(new LibraryTools.ListLibrariesTool());
        registry.register(new LibraryTools.ValidateLibrariesTool());
        registry.register(new LibraryTools.AddLibraryTool());
        registry.register(new LibraryTools.RemoveLibraryTool());
        registry.register(new LibraryTools.AttachLocalLibraryTool());
        registry.register(new LibraryTools.DetachLocalLibraryTool());
        registry.register(new LibraryTools.DownloadDependencyTool());

        return registry;
    }
}
