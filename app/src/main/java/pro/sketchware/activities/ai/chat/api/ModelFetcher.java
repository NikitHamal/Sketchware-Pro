package pro.sketchware.activities.ai.chat.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.activities.ai.chat.models.QwenModel;

public class ModelFetcher {
    private static final String TAG = "ModelFetcher";
    private static final String MODELS_URL = "https://chat.qwen.ai/api/models";
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String authToken;
    
    public interface ModelFetchCallback {
        void onModelsLoaded(List<QwenModel> models);
        void onError(String error);
    }
    
    public ModelFetcher() {}
    
    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }
    
    public void fetchModels(ModelFetchCallback callback) {
        executor.execute(() -> {
            try {
                URL url = new URL(MODELS_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                
                // Set headers based on API documentation
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + getAuthToken());
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; itel A662LM) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Mobile Safari/537.36");
                conn.setRequestProperty("bx-v", "2.5.31");
                conn.setRequestProperty("source", "h5");
                conn.setRequestProperty("timezone", "Wed Jul 23 2025 14:02:07 GMT+0545");
                conn.setRequestProperty("x-request-id", UUID.randomUUID().toString());
                
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    String responseStr = readResponse(conn);
                    List<QwenModel> models = parseModelsResponse(responseStr);
                    
                    mainHandler.post(() -> callback.onModelsLoaded(models));
                } else {
                    String error = "Failed to fetch models. Response code: " + responseCode;
                    Log.e(TAG, error);
                    mainHandler.post(() -> callback.onError(error));
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching models", e);
                mainHandler.post(() -> callback.onError("Error fetching models: " + e.getMessage()));
            }
        });
    }
    
    private List<QwenModel> parseModelsResponse(String responseStr) throws JSONException {
        List<QwenModel> models = new ArrayList<>();
        
        JSONObject response = new JSONObject(responseStr);
        JSONArray dataArray = response.getJSONArray("data");
        
        for (int i = 0; i < dataArray.length(); i++) {
            JSONObject modelData = dataArray.getJSONObject(i);
            JSONObject info = modelData.optJSONObject("info");
            
            if (info != null) {
                String id = modelData.getString("id");
                String name = modelData.getString("name");
                String description = "";
                boolean isActive = modelData.optBoolean("is_active", true);
                boolean isVisitorActive = modelData.optBoolean("is_visitor_active", true);
                
                // Get description from meta if available
                JSONObject meta = info.optJSONObject("meta");
                if (meta != null) {
                    description = meta.optString("description", "");
                }
                
                // Only include active models that are available to visitors
                if (isActive && isVisitorActive) {
                    QwenModel model = new QwenModel(id, name, description);
                    models.add(model);
                }
            }
        }
        
        return models;
    }
    
    private String getAuthToken() {
        if (authToken != null && !authToken.isEmpty()) {
            return authToken;
        }
        // Fallback token - this should be set properly via setAuthToken()
        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6IjhiYjQ1NjVmLTk3NjUtNDQwNi04OWQ5LTI3NmExMTIxMjBkNiIsImxhc3RfcGFzc3dvcmRfY2hhbmdlIjoxNzUwNjYwODczLCJleHAiOjE3NTU0MTY4MjJ9.jmyaxu5mrr1M1rvtRtpGi2DKyp6RM8xRZ1nEx-rHRgQ";
    }
    
    private String readResponse(HttpURLConnection conn) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }
}