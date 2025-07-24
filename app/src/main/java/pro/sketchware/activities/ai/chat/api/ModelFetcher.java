package pro.sketchware.activities.ai.chat.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;

import pro.sketchware.activities.ai.chat.api.retrofit.QwenApiService;
import pro.sketchware.activities.ai.chat.models.QwenModel;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ModelFetcher {
    private static final String TAG = "ModelFetcher";
    private static final String BASE_URL = "https://chat.qwen.ai/api/";

    private final QwenApiService apiService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String authToken;

    public interface ModelFetchCallback {
        void onModelsLoaded(List<QwenModel> models);
        void onError(String error);
    }

    public ModelFetcher() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        apiService = retrofit.create(QwenApiService.class);
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public void fetchModels(ModelFetchCallback callback) {
        apiService.getModels("models", "Bearer " + getAuthToken()).enqueue(new Callback<List<QwenModel>>() {
            @Override
            public void onResponse(Call<List<QwenModel>> call, Response<List<QwenModel>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    mainHandler.post(() -> callback.onModelsLoaded(response.body()));
                } else {
                    String error = "Failed to fetch models. Response code: " + response.code();
                    Log.e(TAG, error);
                    mainHandler.post(() -> callback.onError(error));
                }
            }

            @Override
            public void onFailure(Call<List<QwenModel>> call, Throwable t) {
                Log.e(TAG, "Error fetching models", t);
                mainHandler.post(() -> callback.onError("Error fetching models: " + t.getMessage()));
            }
        });
    }

    private String getAuthToken() {
        if (authToken != null && !authToken.isEmpty()) {
            return authToken;
        }
        // Fallback token - this should be set properly via setAuthToken()
        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6IjhiYjQ1NjVmLTk3NjUtNDQwNi04OWQ5LTI3NmExMTIxMjBkNiIsImxhc3RfcGFzc3dvcmRfY2hhbmdlIjoxNzUwNjYwODczLCJleHAiOjE3NTU0MTY4MjJ9.jmyaxu5mrr1M1rvtRtpGi2DKyp6RM8xRZ1nEx-rHRgQ";
    }
}