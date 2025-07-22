package pro.sketchware.activities.ai.chat.actions;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

import a.a.a.lC;
import a.a.a.yB;
import pro.sketchware.utility.FilePathUtil;

/**
 * Action to create Java files in proper Sketchware project structure
 */
public class CreateJavaFileAction extends BaseUniversalAction {
    
    private final FilePathUtil filePathUtil = new FilePathUtil();
    
    @Override
    public String execute(Map<String, Object> parameters, String projectId, Context context) {
        try {
            if (!validateParameters(parameters)) {
                return createErrorResult("Invalid parameters for create_java_file action");
            }
            
            String className = getStringParameter(parameters, "class_name", null);
            String packagePath = getStringParameter(parameters, "package_path", ""); // sub-package like "util" or "model"
            String fileType = getStringParameter(parameters, "file_type", "class"); // class, activity, interface
            String classContent = getStringParameter(parameters, "content", null);
            boolean createDirectories = getBooleanParameter(parameters, "create_directories", true);
            
            // Get project package name
            String projectPackage = getProjectPackageName(projectId);
            if (projectPackage == null) {
                return createErrorResult("Could not determine project package name");
            }
            
            // Build full package name
            String fullPackage = packagePath.isEmpty() ? projectPackage : projectPackage + "." + packagePath;
            
            // Create package directory structure
            String javaBasePath = filePathUtil.getPathJava(projectId);
            String packageDirPath = javaBasePath + "/" + fullPackage.replace(".", "/");
            
            if (createDirectories) {
                File packageDir = new File(packageDirPath);
                if (!packageDir.exists() && !packageDir.mkdirs()) {
                    return createErrorResult("Failed to create package directory: " + packageDirPath);
                }
            }
            
            // Create Java file
            String fileName = className + ".java";
            File javaFile = new File(packageDirPath, fileName);
            
            if (javaFile.exists()) {
                return createErrorResult("Java file already exists: " + javaFile.getAbsolutePath());
            }
            
            // Generate content if not provided
            String content = classContent != null ? classContent : generateJavaContent(fullPackage, className, fileType);
            
            // Write file
            try (FileWriter writer = new FileWriter(javaFile)) {
                writer.write(content);
                writer.flush();
            }
            
            // Verify file was created
            if (!javaFile.exists()) {
                return createErrorResult("Java file was not created successfully");
            }
            
            JSONObject result = new JSONObject();
            result.put("file_path", javaFile.getAbsolutePath());
            result.put("class_name", className);
            result.put("package_name", fullPackage);
            result.put("file_type", fileType);
            result.put("size", javaFile.length());
            
            logAction(getActionName(), parameters, "Successfully created Java file: " + javaFile.getAbsolutePath());
            return createSuccessResult(getActionName(), result, "Java file created successfully");
            
        } catch (Exception e) {
            return createErrorResult("Error creating Java file: " + e.getMessage());
        }
    }
    
    private String getProjectPackageName(String projectId) {
        try {
            HashMap<String, Object> projectData = lC.b(projectId);
            if (projectData != null) {
                return yB.c(projectData, "my_sc_pkg_name");
            }
        } catch (Exception e) {
            // Fallback handling
        }
        return null;
    }
    
    private String generateJavaContent(String packageName, String className, String fileType) {
        StringBuilder content = new StringBuilder();
        content.append("package ").append(packageName).append(";\n\n");
        
        switch (fileType.toLowerCase()) {
            case "activity":
                content.append("import android.app.Activity;\n");
                content.append("import android.os.Bundle;\n\n");
                content.append("public class ").append(className).append(" extends Activity {\n\n");
                content.append("    @Override\n");
                content.append("    protected void onCreate(Bundle savedInstanceState) {\n");
                content.append("        super.onCreate(savedInstanceState);\n");
                content.append("        // TODO: Set content view and initialize components\n");
                content.append("    }\n");
                content.append("}\n");
                break;
                
            case "interface":
                content.append("public interface ").append(className).append(" {\n");
                content.append("    // TODO: Add interface methods\n");
                content.append("}\n");
                break;
                
            case "class":
            default:
                content.append("public class ").append(className).append(" {\n\n");
                content.append("    public ").append(className).append("() {\n");
                content.append("        // Constructor\n");
                content.append("    }\n\n");
                content.append("    // TODO: Add class methods and fields\n");
                content.append("}\n");
                break;
        }
        
        return content.toString();
    }
    
    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        return hasRequiredParameter(parameters, "class_name");
    }
    
    @Override
    public String getActionName() {
        return "create_java_file";
    }
    
    @Override
    public String getDescription() {
        return "Create Java files in proper Sketchware project package structure";
    }
    
    @Override
    public JSONObject getParametersSchema() {
        try {
            JSONObject schema = new JSONObject();
            schema.put("class_name", "string (required) - Name of the Java class");
            schema.put("package_path", "string (optional) - Sub-package path (e.g., 'util', 'model.data')");
            schema.put("file_type", "string (optional) - Type: class (default), activity, interface");
            schema.put("content", "string (optional) - Custom Java content, auto-generated if not provided");
            schema.put("create_directories", "boolean (optional) - Create package directories, default true");
            return schema;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
    
    @Override
    public boolean isDestructive() {
        return false; // Creating is not destructive (unless file exists)
    }
    
    @Override
    public String getCategory() {
        return "java_development";
    }
}