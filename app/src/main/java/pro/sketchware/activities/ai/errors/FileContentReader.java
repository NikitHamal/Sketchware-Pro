package pro.sketchware.activities.ai.errors;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileContentReader {
    private static final String TAG = "FileContentReader";
    private static final int MAX_FILE_SIZE = 50 * 1024; // 50KB max per file
    private static final int MAX_LINES = 1000; // Max lines per file
    
    public static class FileContent {
        public String filePath;
        public String content;
        public String fileName;
        public String fileType;
        public boolean isTruncated;
        public int totalLines;
        public String errorMessage;
        
        public FileContent(String filePath) {
            this.filePath = filePath;
            this.fileName = new File(filePath).getName();
            this.fileType = getFileExtension(fileName);
        }
        
        private String getFileExtension(String fileName) {
            int lastDot = fileName.lastIndexOf('.');
            return lastDot > 0 ? fileName.substring(lastDot + 1) : "unknown";
        }
        
        public boolean isReadable() {
            return content != null && errorMessage == null;
        }
    }
    
    public static List<FileContent> readFiles(List<String> filePaths) {
        List<FileContent> fileContents = new ArrayList<>();
        
        for (String filePath : filePaths) {
            FileContent fileContent = readFile(filePath);
            if (fileContent != null) {
                fileContents.add(fileContent);
            }
        }
        
        return fileContents;
    }
    
    public static FileContent readFile(String filePath) {
        FileContent fileContent = new FileContent(filePath);
        
        try {
            File file = new File(filePath);
            
            // Check if file exists and is readable
            if (!file.exists()) {
                fileContent.errorMessage = "File does not exist";
                return fileContent;
            }
            
            if (!file.canRead()) {
                fileContent.errorMessage = "File is not readable";
                return fileContent;
            }
            
            // Check file size
            if (file.length() > MAX_FILE_SIZE) {
                fileContent.errorMessage = "File too large (" + file.length() + " bytes)";
                fileContent.isTruncated = true;
            }
            
            // Read file content
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                int lineCount = 0;
                
                while ((line = reader.readLine()) != null && lineCount < MAX_LINES) {
                    content.append(line).append("\n");
                    lineCount++;
                }
                
                fileContent.totalLines = lineCount;
                fileContent.content = content.toString();
                
                if (lineCount >= MAX_LINES) {
                    fileContent.isTruncated = true;
                }
                
                Log.d(TAG, "Read file: " + filePath + " (" + lineCount + " lines)");
                
            }
        } catch (IOException e) {
            fileContent.errorMessage = "Error reading file: " + e.getMessage();
            Log.e(TAG, "Error reading file: " + filePath, e);
        } catch (SecurityException e) {
            fileContent.errorMessage = "Permission denied: " + e.getMessage();
            Log.e(TAG, "Permission error reading file: " + filePath, e);
        }
        
        return fileContent;
    }
    
    public static Map<String, String> createFileContentsMap(List<FileContent> fileContents) {
        Map<String, String> contentsMap = new HashMap<>();
        
        for (FileContent fileContent : fileContents) {
            if (fileContent.isReadable()) {
                String key = fileContent.fileName + " (" + fileContent.fileType + ")";
                String value = fileContent.content;
                
                if (fileContent.isTruncated) {
                    value += "\n\n[File truncated - showing first " + fileContent.totalLines + " lines]";
                }
                
                contentsMap.put(key, value);
            } else {
                String key = fileContent.fileName + " (ERROR)";
                contentsMap.put(key, "Could not read file: " + fileContent.errorMessage);
            }
        }
        
        return contentsMap;
    }
    
    public static boolean isValidProjectFile(String filePath, String projectId) {
        // Check if file path belongs to the project
        return filePath != null && 
               (filePath.contains("/data/" + projectId + "/") || 
                filePath.contains("/.sketchware/data/" + projectId + "/"));
    }
    
    public static List<String> filterProjectFiles(List<String> filePaths, String projectId) {
        List<String> projectFiles = new ArrayList<>();
        
        for (String filePath : filePaths) {
            if (isValidProjectFile(filePath, projectId)) {
                projectFiles.add(filePath);
            }
        }
        
        return projectFiles;
    }
    
    public static String summarizeFileContents(List<FileContent> fileContents) {
        if (fileContents.isEmpty()) {
            return "No files to analyze";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("File Analysis Summary:\n\n");
        
        int readableFiles = 0;
        int totalLines = 0;
        
        for (FileContent fileContent : fileContents) {
            summary.append("📄 ").append(fileContent.fileName)
                   .append(" (").append(fileContent.fileType).append(")");
            
            if (fileContent.isReadable()) {
                readableFiles++;
                totalLines += fileContent.totalLines;
                summary.append(" - ").append(fileContent.totalLines).append(" lines");
                if (fileContent.isTruncated) {
                    summary.append(" [truncated]");
                }
            } else {
                summary.append(" - ❌ ").append(fileContent.errorMessage);
            }
            summary.append("\n");
        }
        
        summary.append("\nTotal: ").append(readableFiles).append("/").append(fileContents.size())
               .append(" files readable, ").append(totalLines).append(" lines of code");
        
        return summary.toString();
    }
}