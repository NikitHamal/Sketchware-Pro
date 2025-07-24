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
    public static final int DEFAULT_MAX_FILE_SIZE = 50 * 1024; // 50KB max per file
    public static final int DEFAULT_MAX_LINES = 1000; // Max lines per file

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

    public static class FileReadException extends IOException {
        public FileReadException(String message) {
            super(message);
        }

        public FileReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static List<FileContent> readFiles(List<String> filePaths) {
        return readFiles(filePaths, DEFAULT_MAX_FILE_SIZE, DEFAULT_MAX_LINES);
    }

    public static List<FileContent> readFiles(List<String> filePaths, int maxFileSize, int maxLines) {
        List<FileContent> fileContents = new ArrayList<>();
        for (String filePath : filePaths) {
            try {
                fileContents.add(readFile(filePath, maxFileSize, maxLines));
            } catch (FileReadException e) {
                Log.e(TAG, "Error reading file: " + filePath, e);
                FileContent fileContent = new FileContent(filePath);
                fileContent.errorMessage = e.getMessage();
                fileContents.add(fileContent);
            }
        }
        return fileContents;
    }

    public static FileContent readFile(String filePath) throws FileReadException {
        return readFile(filePath, DEFAULT_MAX_FILE_SIZE, DEFAULT_MAX_LINES);
    }

    public static FileContent readFile(String filePath, int maxFileSize, int maxLines) throws FileReadException {
        FileContent fileContent = new FileContent(filePath);
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new FileReadException("File does not exist");
            }
            if (!file.canRead()) {
                throw new FileReadException("File is not readable");
            }
            if (file.length() > maxFileSize) {
                fileContent.isTruncated = true;
            }

            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < maxLines) {
                    content.append(line).append("\n");
                    lineCount++;
                }
                fileContent.totalLines = lineCount;
                fileContent.content = content.toString();
                if (lineCount >= maxLines) {
                    fileContent.isTruncated = true;
                }
                Log.d(TAG, "Read file: " + filePath + " (" + lineCount + " lines)");
            }
        } catch (IOException e) {
            throw new FileReadException("Error reading file: " + e.getMessage(), e);
        } catch (SecurityException e) {
            throw new FileReadException("Permission denied: " + e.getMessage(), e);
        }
        return fileContent;
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