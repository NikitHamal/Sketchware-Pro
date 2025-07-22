package pro.sketchware.activities.ai.chat.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatLogger {
    private static final String TAG = "ChatLogger";
    private static ChatLogger instance;
    private Map<String, List<String>> conversationLogs;
    private SimpleDateFormat timestampFormat;
    
    private ChatLogger() {
        conversationLogs = new HashMap<>();
        timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
    }
    
    public static synchronized ChatLogger getInstance() {
        if (instance == null) {
            instance = new ChatLogger();
        }
        return instance;
    }
    
    public void logMessage(String conversationId, String tag, String message) {
        String timestamp = timestampFormat.format(new Date());
        String logEntry = String.format("[%s] %s: %s", timestamp, tag, message);
        
        synchronized (conversationLogs) {
            List<String> logs = conversationLogs.get(conversationId);
            if (logs == null) {
                logs = new ArrayList<>();
                conversationLogs.put(conversationId, logs);
            }
            logs.add(logEntry);
            
            // Keep only last 1000 entries per conversation to prevent memory issues
            if (logs.size() > 1000) {
                logs.remove(0);
            }
        }
        
        // Also log to Android Log
        Log.d(TAG, "[" + conversationId + "] " + tag + ": " + message);
    }
    
    public void exportLogs(Context context, String conversationId, String conversationName) {
        try {
            List<String> logs;
            synchronized (conversationLogs) {
                logs = conversationLogs.get(conversationId);
                if (logs == null || logs.isEmpty()) {
                    Toast.makeText(context, "No logs found for this conversation", Toast.LENGTH_SHORT).show();
                    return;
                }
                logs = new ArrayList<>(logs); // Create a copy to avoid concurrent modification
            }
            
            // Create filename with timestamp
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = String.format("chat_logs_%s_%s.txt", 
                conversationName.replaceAll("[^a-zA-Z0-9]", "_"), timestamp);
            
            // Get Downloads directory
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }
            
            File logFile = new File(downloadsDir, filename);
            
            try (FileWriter writer = new FileWriter(logFile)) {
                writer.write("Chat Logs Export\n");
                writer.write("Conversation: " + conversationName + "\n");
                writer.write("Conversation ID: " + conversationId + "\n");
                writer.write("Export Date: " + timestampFormat.format(new Date()) + "\n");
                writer.write("Total Entries: " + logs.size() + "\n");
                writer.write("=".repeat(80) + "\n\n");
                
                for (String logEntry : logs) {
                    writer.write(logEntry + "\n");
                }
                
                writer.flush();
            }
            
            Toast.makeText(context, 
                "Logs exported to Downloads/" + filename, 
                Toast.LENGTH_LONG).show();
                
        } catch (IOException e) {
            Log.e(TAG, "Failed to export logs", e);
            Toast.makeText(context, 
                "Failed to export logs: " + e.getMessage(), 
                Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during log export", e);
            Toast.makeText(context, 
                "Unexpected error: " + e.getMessage(), 
                Toast.LENGTH_LONG).show();
        }
    }
    
    public void clearLogs(String conversationId) {
        synchronized (conversationLogs) {
            conversationLogs.remove(conversationId);
        }
    }
    
    public boolean hasLogs(String conversationId) {
        synchronized (conversationLogs) {
            List<String> logs = conversationLogs.get(conversationId);
            return logs != null && !logs.isEmpty();
        }
    }
}
