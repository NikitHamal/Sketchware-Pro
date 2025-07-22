package pro.sketchware.activities.ai.chat.context;

/**
 * Comprehensive guide for AI about Sketchware project structure and available actions
 */
public class SketchwareProjectGuide {
    
    public static String getProjectStructureGuide() {
        return """
            SKETCHWARE PROJECT STRUCTURE GUIDE FOR AI
            ==========================================
            
            BASE STRUCTURE:
            /storage/emulated/0/.sketchware/data/<PROJECT_ID>/
            ├── files/
            │   ├── java/                    # Java source files
            │   │   └── <package_path>/      # Package structure (e.g., com/my/app/)
            │   │       ├── MainActivity.java
            │   │       └── <other_classes>.java
            │   ├── resource/                # Android resources
            │   │   ├── layout/              # Layout XML files
            │   │   │   ├── activity_main.xml
            │   │   │   └── <layout_name>.xml
            │   │   ├── drawable/            # Drawable resources
            │   │   │   ├── icon.xml
            │   │   │   └── <drawable_name>.xml
            │   │   ├── values/              # String, color, style resources
            │   │   │   ├── strings.xml
            │   │   │   ├── colors.xml
            │   │   │   └── styles.xml
            │   │   ├── values-night/        # Dark theme resources
            │   │   ├── menu/                # Menu XML files
            │   │   └── <other_resources>/
            │   ├── assets/                  # Asset files
            │   └── native_libs/             # Native libraries
            ├── compile_log                  # Compilation logs
            └── <other_project_files>
            
            PACKAGE STRUCTURE:
            - Project package name: Retrieved from project metadata (my_sc_pkg_name)
            - Java files location: /files/java/<package_path>/
            - Package path example: com.my.app → com/my/app/
            - Sub-packages: com.my.app.util → com/my/app/util/
            
            AVAILABLE UNIVERSAL ACTIONS:
            ============================
            
            1. CREATE_JAVA_FILE
            Purpose: Create Java classes, activities, interfaces in proper package structure
            Parameters:
            - class_name (required): Name of the Java class
            - package_path (optional): Sub-package (e.g., "util", "model.data")
            - file_type (optional): "class", "activity", "interface"
            - content (optional): Custom content or auto-generated
            - create_directories (optional): Create package dirs, default true
            
            Example Usage:
            {
              "action": "create_java_file",
              "parameters": {
                "class_name": "DatabaseHelper",
                "package_path": "util",
                "file_type": "class"
              }
            }
            
            2. CREATE_XML_RESOURCE
            Purpose: Create XML resources (layouts, drawables, values, etc.)
            Parameters:
            - file_name (required): Name without .xml extension
            - resource_type (required): "layout", "drawable", "values", "menu"
            - variant (optional): "night", "hdpi", "sw600dp", etc.
            - content (optional): Custom XML or auto-generated
            - create_directories (optional): Create resource dirs, default true
            
            Example Usage:
            {
              "action": "create_xml_resource",
              "parameters": {
                "file_name": "fragment_settings",
                "resource_type": "layout"
              }
            }
            
            3. READ_FILE
            Purpose: Read file contents
            Parameters:
            - file_path (required): Full path to file
            - encoding (optional): File encoding, default UTF-8
            - max_size (optional): Max file size in bytes, default 1MB
            
            4. EDIT_FILE
            Purpose: Edit existing files
            Parameters:
            - file_path (required): Full path to file
            - content (optional): New content
            - operation (optional): "overwrite", "append", "prepend"
            - create_backup (optional): Create backup, default true
            
            5. LIST_DIRECTORY
            Purpose: List directory contents
            Parameters:
            - path (required): Directory path
            - recursive (optional): Include subdirectories, default false
            - filter (optional): Filter by name substring
            - include_hidden (optional): Include hidden files, default false
            
            6. FIX_FILE_ERROR (Legacy)
            Purpose: Backward compatibility for file operations
            Parameters:
            - action (required): "create_file", "edit_file", "delete_file"
            - file_path (required): Path to file
            - content (optional): File content
            - explanation (optional): Description of change
            
            COMMON FILE OPERATIONS FOR COMPILATION ERRORS:
            ==============================================
            
            1. MISSING BINDING CLASS (MainBinding cannot be resolved):
            - Problem: ViewBinding class not generated
            - Solution: Create/fix layout XML file that generates the binding
            - Action: Use create_xml_resource for layout files
            
            2. MISSING DRAWABLE RESOURCES:
            - Problem: Referenced drawable doesn't exist
            - Solution: Create drawable XML or image file
            - Action: Use create_xml_resource with resource_type="drawable"
            
            3. MISSING STRING RESOURCES:
            - Problem: Referenced string resource doesn't exist
            - Solution: Add string to strings.xml
            - Action: Use edit_file to modify values/strings.xml
            
            4. MISSING JAVA CLASSES:
            - Problem: Referenced class doesn't exist
            - Solution: Create the missing Java class
            - Action: Use create_java_file with appropriate package_path
            
            5. PACKAGE IMPORTS:
            - Problem: Import statements refer to missing classes
            - Solution: Create missing classes or fix import statements
            - Action: Use create_java_file or edit_file to fix imports
            
            BEST PRACTICES:
            ==============
            
            1. Always use proper package structure for Java files
            2. Follow Android naming conventions (snake_case for resources, PascalCase for classes)
            3. Create directories automatically when needed
            4. Use appropriate resource variants (night, hdpi, etc.) when necessary
            5. Generate proper XML declarations and namespaces
            6. Include TODO comments in generated templates
            7. Validate file paths before operations
            8. Provide meaningful error messages
            
            ERROR HANDLING:
            ==============
            
            - Validate all required parameters before execution
            - Check if files/directories already exist
            - Verify package names and paths
            - Handle file system permissions appropriately
            - Provide clear error messages with context
            - Log all operations for debugging
            
            INTEGRATION WITH COMPILE ERRORS:
            ===============================
            
            When fixing compilation errors:
            1. Parse error messages to identify missing files/resources
            2. Determine appropriate action based on error type
            3. Use correct file paths and package structure
            4. Generate appropriate file content based on context
            5. Verify fixes resolve the compilation issues
            
            This guide ensures AI can properly create and manage files within
            the Sketchware project ecosystem following Android development best practices.
            """;
    }
    
    public static String getActionSchemas() {
        return """
            DETAILED ACTION SCHEMAS:
            =======================
            
            CREATE_JAVA_FILE:
            - Creates Java files with proper package structure
            - Automatically generates package directories
            - Supports class, activity, and interface templates
            - Includes proper imports and basic structure
            
            CREATE_XML_RESOURCE:
            - Creates Android XML resources in correct directories
            - Supports all resource types (layout, drawable, values, menu)
            - Handles resource variants (night, density, size qualifiers)
            - Generates appropriate XML templates with namespaces
            
            EDIT_FILE:
            - Modifies existing files with backup support
            - Supports overwrite, append, and prepend operations
            - Maintains file encoding and structure
            - Includes error recovery mechanisms
            
            READ_FILE:
            - Safely reads file contents with size limits
            - Supports different encodings
            - Provides file metadata (size, modification date)
            - Handles large files appropriately
            
            LIST_DIRECTORY:
            - Lists directory contents with filtering
            - Supports recursive directory traversal
            - Provides file metadata and permissions
            - Respects hidden file settings
            """;
    }
}