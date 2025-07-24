package pro.sketchware.activities.ai.chat.api.retrofit;

import java.util.List;

import pro.sketchware.activities.ai.chat.models.QwenModel;
import pro.sketchware.activities.ai.chat.models.QwenRequest;
import pro.sketchware.activities.ai.chat.models.QwenResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Url;

public interface QwenApiService {

    @POST
    Call<QwenResponse> createChat(@Url String url, @Header("Authorization") String authorization, @Body QwenRequest body);

    @POST
    Call<QwenResponse> sendMessage(@Url String url, @Header("Authorization") String authorization, @Body QwenRequest body);

    @GET
    Call<List<QwenModel>> getModels(@Url String url, @Header("Authorization") String authorization);
}
