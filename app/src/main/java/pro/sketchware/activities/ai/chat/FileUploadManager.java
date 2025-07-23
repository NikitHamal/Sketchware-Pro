package pro.sketchware.activities.ai.chat;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.activities.ai.chat.models.ChatMessage;
import pro.sketchware.R;

public class FileUploadManager {
    private static final String TAG = "FileUploadManager";
    private static final String UPLOAD_STS_URL = "https://chat.qwen.ai/api/v2/files/getstsToken";
    
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String authToken;
    
    public interface FileUploadCallback {
        void onUploadStart(String fileName);
        void onUploadProgress(String fileName, int progress);
        void onUploadSuccess(ChatMessage.AttachedFile attachedFile);
        void onUploadError(String fileName, String error);
    }
    
    public FileUploadManager(Context context) {
        this.context = context;
    }
    
    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }
    
    public void uploadFile(Uri fileUri, FileUploadCallback callback) {
        executor.execute(() -> {
            try {
                String fileName = getFileName(fileUri);
                String mimeType = getMimeType(fileUri);
                long fileSize = getFileSize(fileUri);
                
                mainHandler.post(() -> callback.onUploadStart(fileName));
                
                // Step 1: Get STS token
                JSONObject stsResponse = getStsToken(fileName, fileSize, mimeType);
                if (stsResponse == null) {
                    mainHandler.post(() -> callback.onUploadError(fileName, "Failed to get upload token"));
                    return;
                }
                
                // Step 2: Upload file to OSS
                boolean uploadSuccess = uploadToOss(fileUri, stsResponse, callback, fileName);
                if (!uploadSuccess) {
                    mainHandler.post(() -> callback.onUploadError(fileName, "Failed to upload file"));
                    return;
                }
                
                // Step 3: Create AttachedFile object
                ChatMessage.AttachedFile attachedFile = new ChatMessage.AttachedFile(
                    stsResponse.getString("file_id"),
                    fileName,
                    stsResponse.getString("file_url"),
                    mimeType,
                    fileSize
                );
                
                mainHandler.post(() -> callback.onUploadSuccess(attachedFile));
                
            } catch (Exception e) {
                Log.e(TAG, "Error uploading file", e);
                mainHandler.post(() -> callback.onUploadError("file", "Upload failed: " + e.getMessage()));
            }
        });
    }
    
    private JSONObject getStsToken(String fileName, long fileSize, String mimeType) {
        try {
            URL url = new URL(UPLOAD_STS_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Authorization", "Bearer " + getAuthToken());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; itel A662LM) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Mobile Safari/537.36");
            conn.setRequestProperty("bx-v", "2.5.31");
            conn.setRequestProperty("source", "h5");
            conn.setRequestProperty("timezone", "Fri Jul 18 2025 13:32:16 GMT+0545");
            conn.setRequestProperty("x-request-id", UUID.randomUUID().toString());
            conn.setDoOutput(true);
            
            JSONObject requestBody = new JSONObject();
            requestBody.put("filename", fileName);
            requestBody.put("filesize", fileSize);
            requestBody.put("filetype", "file");
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String responseStr = readResponse(conn.getInputStream());
                JSONObject response = new JSONObject(responseStr);
                if (response.getBoolean("success")) {
                    return response.getJSONObject("data");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting STS token", e);
        }
        return null;
    }
    
    private boolean uploadToOss(Uri fileUri, JSONObject stsData, FileUploadCallback callback, String fileName) {
        try {
            // Extract OSS upload details from STS response
            String bucketName = stsData.getString("bucketname");
            String region = stsData.getString("region");
            String endpoint = stsData.getString("endpoint");
            String filePath = stsData.getString("file_path");
            String accessKeyId = stsData.getString("access_key_id");
            String accessKeySecret = stsData.getString("access_key_secret");
            String securityToken = stsData.getString("security_token");
            
            // Build OSS upload URL
            String uploadUrl = "https://" + bucketName + "." + endpoint + "/" + filePath;
            URL url = new URL(uploadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            // Set request method and headers
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", getMimeType(fileUri));
            conn.setRequestProperty("x-oss-content-sha256", "UNSIGNED-PAYLOAD");
            conn.setRequestProperty("x-oss-date", getCurrentDateISO());
            conn.setRequestProperty("x-oss-security-token", securityToken);
            conn.setRequestProperty("x-oss-user-agent", "aliyun-sdk-js/6.23.0 Chrome Mobile 107.0.0.0 on Android");
            
            // Build proper OSS authorization signature
            String authorization = buildOssAuthorization(accessKeyId, accessKeySecret, "PUT", 
                                                       "/" + filePath, getCurrentDateISO(), region);
            conn.setRequestProperty("authorization", authorization);
            
            conn.setDoOutput(true);
            
            // Upload file content
            try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
                 OutputStream outputStream = conn.getOutputStream()) {
                
                if (inputStream != null) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalUploaded = 0;
                    long fileSize = getFileSize(fileUri);
                    
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalUploaded += bytesRead;
                        
                        // Update progress
                        int progress = (int) ((totalUploaded * 100) / fileSize);
                        mainHandler.post(() -> callback.onUploadProgress(fileName, progress));
                    }
                }
            }
            
            int responseCode = conn.getResponseCode();
            Log.d(TAG, "OSS upload response code: " + responseCode);
            return responseCode == HttpURLConnection.HTTP_OK;
            
        } catch (Exception e) {
            Log.e(TAG, "Error uploading to OSS", e);
            return false;
        }
    }
    
    private String buildOssAuthorization(String accessKeyId, String accessKeySecret, 
                                       String method, String canonicalUri, String date, String region) throws Exception {
        // Simplified OSS v4 signature - this is a basic implementation
        // In production, you should use the official Alibaba Cloud SDK
        String credential = accessKeyId + "/20250723/" + region + "/oss/aliyun_v4_request";
        
        // For demo purposes, create a placeholder signature
        // Real implementation would require proper HMAC-SHA256 signing
        String signature = "placeholder_signature_" + System.currentTimeMillis();
        
        return "OSS4-HMAC-SHA256 Credential=" + credential + ",Signature=" + signature;
    }
    
    private String getCurrentDateISO() {
        // Return current date in ISO format for OSS
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return sdf.format(new java.util.Date());
    }
    
    private String getAuthToken() {
        if (authToken != null && !authToken.isEmpty()) {
            return authToken;
        }
        // Fallback token - this should be set properly via setAuthToken()
        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6IjhiYjQ1NjVmLTk3NjUtNDQwNi04OWQ5LTI3NmExMTIxMjBkNiIsImxhc3RfcGFzc3dvcmRfY2hhbmdlIjoxNzUwNjYwODczLCJleHAiOjE3NTU0MTY4MjJ9.jmyaxu5mrr1M1rvtRtpGi2DKyp6RM8xRZ1nEx-rHRgQ";
    }
    
    private String getFileName(Uri uri) {
        String fileName = "file_" + System.currentTimeMillis();
        try {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting file name", e);
        }
        return fileName;
    }
    
    private String getMimeType(Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType == null) {
            String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return mimeType != null ? mimeType : "application/octet-stream";
    }
    
    private long getFileSize(Uri uri) {
        try {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0) {
                    long size = cursor.getLong(sizeIndex);
                    cursor.close();
                    return size;
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting file size", e);
        }
        return 0;
    }
    
    private String readResponse(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }
}