package pro.sketchware.activities.ai.config;

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

    // Default values from reverse-engineered API
    private static final String DEFAULT_AUTHORIZATION = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6IjhiYjQ1NjVmLTk3NjUtNDQwNi04OWQ5LTI3NmExMTIxMjBkNiIsImxhc3RfcGFzc3dvcmRfY2hhbmdlIjoxNzUwNjYwODczLCJleHAiOjE3NTU0MTY4MjJ9.jmyaxu5mrr1M1rvtRtpGi2DKyp6RM8xRZ1nEx-rHRgQ";
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 12; itel A662LM) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Mobile Safari/537.36";
    private static final String DEFAULT_BX_V = "2.5.31";
    private static final String DEFAULT_SOURCE = "h5";
    private static final String DEFAULT_TIMEZONE = "Asia/Kathmandu";
    private static final String DEFAULT_COOKIE = "";
    private static final String DEFAULT_BX_UA = "defaultFY2_load_failed with timeout@@https://chat.qwen.ai/@@1752824836210";
    private static final String DEFAULT_BX_UMIDTOKEN = "defaultFY2_load_failed with timeout@@https://chat.qwen.ai/@@1752824836210";

    private SharedPreferences prefs;

    public ApiConfig(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getAuthorization() {
        return prefs.getString(KEY_AUTHORIZATION, DEFAULT_AUTHORIZATION);
    }

    public void setAuthorization(String authorization) {
        prefs.edit().putString(KEY_AUTHORIZATION, authorization).apply();
    }

    public String getUserAgent() {
        return prefs.getString(KEY_USER_AGENT, DEFAULT_USER_AGENT);
    }

    public void setUserAgent(String userAgent) {
        prefs.edit().putString(KEY_USER_AGENT, userAgent).apply();
    }

    public String getBxV() {
        return prefs.getString(KEY_BX_V, DEFAULT_BX_V);
    }

    public void setBxV(String bxV) {
        prefs.edit().putString(KEY_BX_V, bxV).apply();
    }

    public String getSource() {
        return prefs.getString(KEY_SOURCE, DEFAULT_SOURCE);
    }

    public void setSource(String source) {
        prefs.edit().putString(KEY_SOURCE, source).apply();
    }

    public String getTimezone() {
        return prefs.getString(KEY_TIMEZONE, DEFAULT_TIMEZONE);
    }

    public void setTimezone(String timezone) {
        prefs.edit().putString(KEY_TIMEZONE, timezone).apply();
    }

    public String getCookie() {
        return prefs.getString(KEY_COOKIE, DEFAULT_COOKIE);
    }

    public void setCookie(String cookie) {
        prefs.edit().putString(KEY_COOKIE, cookie).apply();
    }

    public String getBxUa() {
        return prefs.getString(KEY_BX_UA, DEFAULT_BX_UA);
    }

    public void setBxUa(String bxUa) {
        prefs.edit().putString(KEY_BX_UA, bxUa).apply();
    }

    public String getBxUmidtoken() {
        return prefs.getString(KEY_BX_UMIDTOKEN, DEFAULT_BX_UMIDTOKEN);
    }

    public void setBxUmidtoken(String bxUmidtoken) {
        prefs.edit().putString(KEY_BX_UMIDTOKEN, bxUmidtoken).apply();
    }

    public void resetToDefaults() {
        prefs.edit()
                .putString(KEY_AUTHORIZATION, DEFAULT_AUTHORIZATION)
                .putString(KEY_USER_AGENT, DEFAULT_USER_AGENT)
                .putString(KEY_BX_V, DEFAULT_BX_V)
                .putString(KEY_SOURCE, DEFAULT_SOURCE)
                .putString(KEY_TIMEZONE, DEFAULT_TIMEZONE)
                .putString(KEY_COOKIE, DEFAULT_COOKIE)
                .putString(KEY_BX_UA, DEFAULT_BX_UA)
                .putString(KEY_BX_UMIDTOKEN, DEFAULT_BX_UMIDTOKEN)
                .apply();
    }
}