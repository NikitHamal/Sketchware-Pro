package pro.sketchware.activities.ai.chat.actions;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.util.Map;

import pro.sketchware.utility.FilePathUtil;

/**
 * Action to create XML resource files in proper Sketchware project structure
 */
public class CreateXMLResourceAction extends BaseUniversalAction {
    
    private final FilePathUtil filePathUtil = new FilePathUtil();
    
    @Override
    public String execute(Map<String, Object> parameters, String projectId, Context context) {
        try {
            if (!validateParameters(parameters)) {
                return createErrorResult("Invalid parameters for create_xml_resource action");
            }
            
            String fileName = getStringParameter(parameters, "file_name", null);
            String resourceType = getStringParameter(parameters, "resource_type", "layout"); // layout, drawable, values, etc.
            String xmlContent = getStringParameter(parameters, "content", null);
            String variant = getStringParameter(parameters, "variant", ""); // e.g., "night", "hdpi", etc.
            boolean createDirectories = getBooleanParameter(parameters, "create_directories", true);
            
            // Build resource directory path
            String resourceBasePath = filePathUtil.getPathResource(projectId);
            String resourceDirName = variant.isEmpty() ? resourceType : resourceType + "-" + variant;
            String resourceDirPath = resourceBasePath + "/" + resourceDirName;
            
            if (createDirectories) {
                File resourceDir = new File(resourceDirPath);
                if (!resourceDir.exists() && !resourceDir.mkdirs()) {
                    return createErrorResult("Failed to create resource directory: " + resourceDirPath);
                }
            }
            
            // Ensure filename has .xml extension
            String xmlFileName = fileName.endsWith(".xml") ? fileName : fileName + ".xml";
            File xmlFile = new File(resourceDirPath, xmlFileName);
            
            if (xmlFile.exists()) {
                return createErrorResult("XML resource file already exists: " + xmlFile.getAbsolutePath());
            }
            
            // Generate content if not provided
            String content = xmlContent != null ? xmlContent : generateXMLContent(resourceType, fileName);
            
            // Write file
            try (FileWriter writer = new FileWriter(xmlFile)) {
                writer.write(content);
                writer.flush();
            }
            
            // Verify file was created
            if (!xmlFile.exists()) {
                return createErrorResult("XML resource file was not created successfully");
            }
            
            JSONObject result = new JSONObject();
            result.put("file_path", xmlFile.getAbsolutePath());
            result.put("file_name", xmlFileName);
            result.put("resource_type", resourceType);
            result.put("variant", variant);
            result.put("size", xmlFile.length());
            
            logAction(getActionName(), parameters, "Successfully created XML resource: " + xmlFile.getAbsolutePath());
            return createSuccessResult(getActionName(), result, "XML resource file created successfully");
            
        } catch (Exception e) {
            return createErrorResult("Error creating XML resource file: " + e.getMessage());
        }
    }
    
    private String generateXMLContent(String resourceType, String fileName) {
        StringBuilder content = new StringBuilder();
        content.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        
        switch (resourceType.toLowerCase()) {
            case "layout":
                content.append("<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
                content.append("    android:layout_width=\"match_parent\"\n");
                content.append("    android:layout_height=\"match_parent\"\n");
                content.append("    android:orientation=\"vertical\">\n\n");
                content.append("    <!-- TODO: Add layout components -->\n\n");
                content.append("</LinearLayout>\n");
                break;
                
            case "drawable":
                if (fileName.contains("selector") || fileName.contains("state")) {
                    content.append("<selector xmlns:android=\"http://schemas.android.com/apk/res/android\">\n");
                    content.append("    <!-- TODO: Add selector states -->\n");
                    content.append("    <item android:state_pressed=\"true\" android:drawable=\"@color/pressed_color\" />\n");
                    content.append("    <item android:drawable=\"@color/default_color\" />\n");
                    content.append("</selector>\n");
                } else {
                    content.append("<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
                    content.append("    android:shape=\"rectangle\">\n");
                    content.append("    \n");
                    content.append("    <solid android:color=\"#FFFFFF\" />\n");
                    content.append("    <corners android:radius=\"4dp\" />\n");
                    content.append("    \n");
                    content.append("    <!-- TODO: Customize shape properties -->\n");
                    content.append("    \n");
                    content.append("</shape>\n");
                }
                break;
                
            case "values":
                if (fileName.contains("string")) {
                    content.append("<resources>\n");
                    content.append("    <!-- TODO: Add string resources -->\n");
                    content.append("    <string name=\"app_name\">My App</string>\n");
                    content.append("</resources>\n");
                } else if (fileName.contains("color")) {
                    content.append("<resources>\n");
                    content.append("    <!-- TODO: Add color resources -->\n");
                    content.append("    <color name=\"primary_color\">#2196F3</color>\n");
                    content.append("    <color name=\"primary_dark\">#1976D2</color>\n");
                    content.append("    <color name=\"accent_color\">#FF4081</color>\n");
                    content.append("</resources>\n");
                } else if (fileName.contains("style")) {
                    content.append("<resources>\n");
                    content.append("    <!-- TODO: Add style resources -->\n");
                    content.append("    <style name=\"AppTheme\" parent=\"Theme.AppCompat.Light.DarkActionBar\">\n");
                    content.append("        <item name=\"colorPrimary\">@color/primary_color</item>\n");
                    content.append("        <item name=\"colorPrimaryDark\">@color/primary_dark</item>\n");
                    content.append("        <item name=\"colorAccent\">@color/accent_color</item>\n");
                    content.append("    </style>\n");
                    content.append("</resources>\n");
                } else {
                    content.append("<resources>\n");
                    content.append("    <!-- TODO: Add resource values -->\n");
                    content.append("</resources>\n");
                }
                break;
                
            case "menu":
                content.append("<menu xmlns:android=\"http://schemas.android.com/apk/res/android\">\n");
                content.append("    <!-- TODO: Add menu items -->\n");
                content.append("    <item\n");
                content.append("        android:id=\"@+id/menu_item\"\n");
                content.append("        android:title=\"Menu Item\"\n");
                content.append("        android:showAsAction=\"never\" />\n");
                content.append("</menu>\n");
                break;
                
            default:
                content.append("<!-- TODO: Add XML content for ").append(resourceType).append(" -->\n");
                break;
        }
        
        return content.toString();
    }
    
    @Override
    public boolean validateParameters(Map<String, Object> parameters) {
        return hasRequiredParameter(parameters, "file_name") && 
               hasRequiredParameter(parameters, "resource_type");
    }
    
    @Override
    public String getActionName() {
        return "create_xml_resource";
    }
    
    @Override
    public String getDescription() {
        return "Create XML resource files in proper Sketchware project structure";
    }
    
    @Override
    public JSONObject getParametersSchema() {
        try {
            JSONObject schema = new JSONObject();
            schema.put("file_name", "string (required) - Name of the XML file (without .xml extension)");
            schema.put("resource_type", "string (required) - Type: layout, drawable, values, menu, etc.");
            schema.put("variant", "string (optional) - Resource variant (night, hdpi, sw600dp, etc.)");
            schema.put("content", "string (optional) - Custom XML content, auto-generated if not provided");
            schema.put("create_directories", "boolean (optional) - Create resource directories, default true");
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
        return "android_resources";
    }
}