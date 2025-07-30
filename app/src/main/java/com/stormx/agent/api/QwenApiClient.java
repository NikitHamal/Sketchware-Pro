package com.stormx.agent.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QwenApiClient {
    private static final String TAG="QwenApiClient";
    private static final String BASE_URL="https://chat.qwen.ai/api/v2";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String,String> chatIdMap = new HashMap<>();
    private final ApiConfig apiConfig;

    public interface ChatCallback{
        void onResponse(String response);
        void onError(String error);
        default void onStreamingResponse(String partial){}
    }

    public QwenApiClient(Context ctx){ apiConfig=new ApiConfig(ctx);}    

    public void sendMessage(String convId,String model,String message,ChatCallback cb){
        executor.execute(()->{
            try{
                String chatId = chatIdMap.get(convId);
                if(chatId==null){
                    chatId = createNewChat(model);
                    if(chatId==null){
                        mainHandler.post(()->cb.onError("Failed to create chat"));
                        return;}
                    chatIdMap.put(convId,chatId);
                }
                String resp = sendChatMessage(chatId,model,message);
                if(resp!=null) mainHandler.post(()->cb.onResponse(resp)); else mainHandler.post(()->cb.onError("No response"));
            }catch(Exception e){ Log.e(TAG,"sendMessage",e); mainHandler.post(()->cb.onError(e.getMessage())); }
        });
    }

    private String createNewChat(String model) throws Exception{
        URL url=new URL(BASE_URL+"/chats/new");
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Accept","application/json");
        conn.setRequestProperty("Authorization",apiConfig.getAuthorization());
        conn.setRequestProperty("Content-Type","application/json");
        conn.setRequestProperty("User-Agent",apiConfig.getUserAgent());
        conn.setRequestProperty("bx-v",apiConfig.getBxV());
        conn.setRequestProperty("source",apiConfig.getSource());
        conn.setRequestProperty("timezone",apiConfig.getTimezone());
        conn.setRequestProperty("x-request-id", UUID.randomUUID().toString());
        conn.setDoOutput(true);
        JSONObject body=new JSONObject();
        body.put("title","New Chat");
        JSONArray models=new JSONArray(); models.put(model); body.put("models",models);
        body.put("chat_mode","normal"); body.put("chat_type","t2t"); body.put("timestamp",System.currentTimeMillis());
        try(OutputStream os=conn.getOutputStream()){ os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        if(conn.getResponseCode()==HttpURLConnection.HTTP_OK){ String resp=readResponse(conn.getInputStream()); JSONObject obj=new JSONObject(resp); if(obj.getBoolean("success")) return obj.getJSONObject("data").getString("id"); }
        return null;
    }

    private String sendChatMessage(String chatId,String model,String message) throws Exception{
        URL url=new URL(BASE_URL+"/chat/completions?chat_id="+chatId);
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Accept","*/*");
        conn.setRequestProperty("Authorization",apiConfig.getAuthorization());
        conn.setRequestProperty("Content-Type","application/json");
        conn.setRequestProperty("User-Agent",apiConfig.getUserAgent());
        conn.setRequestProperty("bx-v",apiConfig.getBxV());
        conn.setRequestProperty("source",apiConfig.getSource());
        conn.setRequestProperty("timezone",apiConfig.getTimezone());
        conn.setRequestProperty("x-accel-buffering","no");
        conn.setRequestProperty("x-request-id",UUID.randomUUID().toString());
        conn.setDoOutput(true);
        JSONObject msg=new JSONObject(); msg.put("fid",UUID.randomUUID().toString()); msg.put("parentId",JSONObject.NULL); msg.put("childrenIds",new JSONArray()); msg.put("role","user"); msg.put("content",message); msg.put("user_action","chat"); msg.put("files",new JSONArray()); msg.put("timestamp",System.currentTimeMillis()/1000);
        JSONArray models=new JSONArray(); models.put(model); msg.put("models",models); msg.put("chat_type","t2t");
        JSONObject req=new JSONObject(); req.put("stream",true); req.put("incremental_output",true); req.put("chat_id",chatId); req.put("chat_mode","normal"); req.put("model",model); req.put("parent_id",JSONObject.NULL);
        req.put("messages", new JSONArray().put(msg)); req.put("timestamp",System.currentTimeMillis()/1000);
        try(OutputStream os=conn.getOutputStream()){ os.write(req.toString().getBytes(StandardCharsets.UTF_8)); }
        if(conn.getResponseCode()==HttpURLConnection.HTTP_OK) return readStreaming(conn.getInputStream());
        return null;
    }

    private String readStreaming(InputStream is) throws IOException{
        StringBuilder sb=new StringBuilder(); BufferedReader br=new BufferedReader(new InputStreamReader(is)); String line;
        while((line=br.readLine())!=null){ if(line.startsWith("data: ")){ String data=line.substring(6); if(!data.trim().isEmpty()){ try{ JSONObject obj=new JSONObject(data); JSONArray choices=obj.getJSONArray("choices"); if(choices.length()>0){ JSONObject delta=choices.getJSONObject(0).getJSONObject("delta"); if(delta.has("content")){ sb.append(delta.getString("content")); if("finished".equals(delta.optString("status"))) break; } } }catch(JSONException ignore){} } } }
        return sb.toString().trim();
    }
    private String readResponse(InputStream is) throws IOException{ BufferedReader br=new BufferedReader(new InputStreamReader(is)); StringBuilder sb=new StringBuilder(); String l; while((l=br.readLine())!=null){ sb.append(l);} return sb.toString(); }
}