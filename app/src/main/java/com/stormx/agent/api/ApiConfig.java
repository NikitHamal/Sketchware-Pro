package com.stormx.agent.api;

import android.content.Context;
import android.content.SharedPreferences;

public class ApiConfig {
    private static final String PREFS_NAME = "qwen_api_config";
    private static final String KEY_AUTHORIZATION = "authorization";
    private static final String KEY_USER_AGENT = "user_agent";
    private static final String KEY_BX_V = "bx_v";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_TIMEZONE = "timezone";
    private static final String KEY_COOKIE = "cookie";
    private static final String KEY_BX_UA = "bx_ua";
    private static final String KEY_BX_UMIDTOKEN = "bx_umidtoken";

    // Default header values
    private static final String DEFAULT_AUTHORIZATION = "Bearer ...";
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0";
    private static final String DEFAULT_BX_V = "2.5.31";
    private static final String DEFAULT_SOURCE = "h5";
    private static final String DEFAULT_TIMEZONE = "UTC";
    private static final String DEFAULT_COOKIE = "";
    private static final String DEFAULT_BX_UA = "defaultUA";
    private static final String DEFAULT_BX_UMIDTOKEN = "defaultToken";

    private final SharedPreferences prefs;

    public ApiConfig(Context context){
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getAuthorization(){ return prefs.getString(KEY_AUTHORIZATION, DEFAULT_AUTHORIZATION);}    public String getUserAgent(){return prefs.getString(KEY_USER_AGENT, DEFAULT_USER_AGENT);}    public String getBxV(){return prefs.getString(KEY_BX_V, DEFAULT_BX_V);}    public String getSource(){return prefs.getString(KEY_SOURCE, DEFAULT_SOURCE);}    public String getTimezone(){return prefs.getString(KEY_TIMEZONE, DEFAULT_TIMEZONE);}    public String getCookie(){return prefs.getString(KEY_COOKIE, DEFAULT_COOKIE);}    public String getBxUa(){return prefs.getString(KEY_BX_UA, DEFAULT_BX_UA);}    public String getBxUmidtoken(){return prefs.getString(KEY_BX_UMIDTOKEN, DEFAULT_BX_UMIDTOKEN);} }