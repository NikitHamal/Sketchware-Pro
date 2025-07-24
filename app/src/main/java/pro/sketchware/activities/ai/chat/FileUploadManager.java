package pro.sketchware.activities.ai.chat;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import pro.sketchware.activities.ai.chat.models.ChatMessage;

public class FileUploadManager {
    private static final String TAG = "FileUploadManager";
    // TODO: Move these to a configuration file
    private static final String UPLOAD_STS_URL = "https://chat.qwen.ai/api/v2/files/getstsToken";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 12; itel A662LM) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Mobile Safari/537.36";
    private static final String BX_V = "2.5.31";
    private static final String SOURCE = "h5";
    private static final String TIMEZONE = "Fri Jul 18 2025 13:32:16 GMT+0545";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String authToken;
    private Future<?> uploadTask;

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
        uploadTask = executor.submit(() -> {
            try {
                String fileName = getFileName(fileUri);
                String mimeType = getMimeType(fileUri);
                long fileSize = getFileSize(fileUri);

                mainHandler.post(() -> callback.onUploadStart(fileName));

                JSONObject stsResponse = getStsToken(fileName, fileSize, mimeType);
                if (stsResponse == null) {
                    mainHandler.post(() -> callback.onUploadError(fileName, "Failed to get upload token"));
                    return;
                }

                boolean uploadSuccess = uploadToOss(fileUri, stsResponse, callback, fileName);
                if (!uploadSuccess) {
                    mainHandler.post(() -> callback.onUploadError(fileName, "Failed to upload file"));
                    return;
                }

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

    public void cancel() {
        if (uploadTask != null && !uploadTask.isDone()) {
            uploadTask.cancel(true);
        }
    }

    private JSONObject getStsToken(String fileName, long fileSize, String mimeType) throws Exception {
        URL url = new URL(UPLOAD_STS_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Authorization", "Bearer " + getAuthToken());
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("bx-v", BX_V);
        conn.setRequestProperty("source", SOURCE);
        conn.setRequestProperty("timezone", TIMEZONE);
        conn.setRequestProperty("x-request-id", UUID.randomUUID().toString());
        conn.setDoOutput(true);

        JSONObject requestBody = new JSONObject();
        requestBody.put("filename", fileName);
        requestBody.put("filesize", fileSize);
        requestBody.put("filetype", "file");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            String responseStr = readResponse(conn.getInputStream());
            JSONObject response = new JSONObject(responseStr);
            if (response.getBoolean("success")) {
                return response.getJSONObject("data");
            }
        }
        return null;
    }

    private boolean uploadToOss(Uri fileUri, JSONObject stsData, FileUploadCallback callback, String fileName) throws Exception {
        String bucketName = stsData.getString("bucketname");
        String region = stsData.getString("region");
        String endpoint = stsData.getString("endpoint");
        String filePath = stsData.getString("file_path");
        String accessKeyId = stsData.getString("access_key_id");
        String accessKeySecret = stsData.getString("access_key_secret");
        String securityToken = stsData.getString("security_token");

        String uploadUrl = "https://" + bucketName + "." + endpoint + "/" + filePath;
        URL url = new URL(uploadUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Content-Type", getMimeType(fileUri));
        conn.setRequestProperty("x-oss-content-sha256", "UNSIGNED-PAYLOAD");
        conn.setRequestProperty("x-oss-date", getCurrentDateISO());
        conn.setRequestProperty("x-oss-security-token", securityToken);
        conn.setRequestProperty("x-oss-user-agent", "aliyun-sdk-js/6.23.0 Chrome Mobile 107.0.0.0 on Android");

        String authorization = buildOssAuthorization(accessKeyId, accessKeySecret, "PUT",
                "/" + filePath, getCurrentDateISO(), region,
                bucketName, endpoint, filePath);
        conn.setRequestProperty("authorization", authorization);

        conn.setDoOutput(true);

        try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
             OutputStream outputStream = conn.getOutputStream()) {

            if (inputStream != null) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalUploaded = 0;
                long fileSize = getFileSize(fileUri);

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    if (Thread.interrupted()) {
                        throw new InterruptedException("Upload cancelled");
                    }
                    outputStream.write(buffer, 0, bytesRead);
                    totalUploaded += bytesRead;
                    int progress = (int) ((totalUploaded * 100) / fileSize);
                    mainHandler.post(() -> callback.onUploadProgress(fileName, progress));
                }
            }
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String errorResponse = readResponse(conn.getErrorStream());
            Log.e(TAG, "OSS upload error response: " + errorResponse);
            return false;
        }

        return true;
    }

    private String buildOssAuthorization(String accessKeyId, String accessKeySecret,
                                       String method, String canonicalUri, String date, String region,
                                       String bucketName, String endpoint, String filePath) throws Exception {
        String dateOnly = date.substring(0, 8);
        String credential = accessKeyId + "/" + dateOnly + "/" + region + "/oss/aliyun_v4_request";

        String hostHeader = bucketName + "." + endpoint;
        String canonicalHeaders = "host:" + hostHeader + "\n" +
                "x-oss-content-sha256:UNSIGNED-PAYLOAD" + "\n" +
                "x-oss-date:" + date + "\n";
        String signedHeaders = "host;x-oss-content-sha256;x-oss-date";
        String canonicalRequest = method + "\n" + canonicalUri + "\n" + "\n" +
                canonicalHeaders + "\n" + signedHeaders + "\nUNSIGNED-PAYLOAD";

        String algorithm = "OSS4-HMAC-SHA256";
        String credentialScope = dateOnly + "/" + region + "/oss/aliyun_v4_request";
        String stringToSign = algorithm + "\n" + date + "\n" + credentialScope + "\n" +
                sha256Hash(canonicalRequest);

        byte[] signingKey = getSigningKey(accessKeySecret, dateOnly, region, "oss");
        String signature = hmacSha256Hex(signingKey, stringToSign);

        return algorithm + " Credential=" + credential + ",Signature=" + signature;
    }

    private String sha256Hash(String data) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private byte[] getSigningKey(String secretKey, String date, String region, String service) throws Exception {
        byte[] kDate = hmacSha256(("OSS4" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, "aliyun_v4_request");
    }

    private byte[] hmacSha256(byte[] key, String data) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256");
        mac.init(keySpec);
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private String hmacSha256Hex(byte[] key, String data) throws Exception {
        byte[] hash = hmacSha256(key, data);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private String getCurrentDateISO() {
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
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex);
                }
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
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0) {
                    return cursor.getLong(sizeIndex);
                }
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