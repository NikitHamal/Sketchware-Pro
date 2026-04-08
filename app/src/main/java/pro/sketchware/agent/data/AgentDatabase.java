package pro.sketchware.agent.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class AgentDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "agent.db";
    private static final int DB_VERSION = 1;

    private static final String TABLE_WORKSPACES = "workspaces";
    private static final String TABLE_WORKSPACE_PROJECTS = "workspace_projects";
    private static final String TABLE_CONVERSATIONS = "conversations";
    private static final String TABLE_MESSAGES = "messages";
    private static final String TABLE_PROVIDER_SETTINGS = "provider_settings";
    private static final String TABLE_MODEL_CACHE = "model_cache";

    private static volatile AgentDatabase instance;

    public static AgentDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AgentDatabase.class) {
                if (instance == null) {
                    instance = new AgentDatabase(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private AgentDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_WORKSPACES + " (" +
                "id TEXT PRIMARY KEY," +
                "name TEXT NOT NULL," +
                "description TEXT DEFAULT ''," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_WORKSPACE_PROJECTS + " (" +
                "workspace_id TEXT NOT NULL," +
                "sc_id TEXT NOT NULL," +
                "added_at INTEGER NOT NULL," +
                "PRIMARY KEY (workspace_id, sc_id)," +
                "FOREIGN KEY (workspace_id) REFERENCES " + TABLE_WORKSPACES + "(id) ON DELETE CASCADE" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_CONVERSATIONS + " (" +
                "id TEXT PRIMARY KEY," +
                "workspace_id TEXT NOT NULL," +
                "title TEXT NOT NULL," +
                "provider TEXT DEFAULT ''," +
                "model TEXT DEFAULT ''," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "FOREIGN KEY (workspace_id) REFERENCES " + TABLE_WORKSPACES + "(id) ON DELETE CASCADE" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_MESSAGES + " (" +
                "id TEXT PRIMARY KEY," +
                "conversation_id TEXT NOT NULL," +
                "role TEXT NOT NULL," +
                "content TEXT NOT NULL," +
                "tool_calls TEXT DEFAULT ''," +
                "tool_results TEXT DEFAULT ''," +
                "status TEXT DEFAULT 'complete'," +
                "created_at INTEGER NOT NULL," +
                "FOREIGN KEY (conversation_id) REFERENCES " + TABLE_CONVERSATIONS + "(id) ON DELETE CASCADE" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_PROVIDER_SETTINGS + " (" +
                "provider TEXT PRIMARY KEY," +
                "api_key TEXT DEFAULT ''," +
                "enabled INTEGER DEFAULT 0" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_MODEL_CACHE + " (" +
                "provider TEXT NOT NULL," +
                "model_id TEXT NOT NULL," +
                "model_name TEXT NOT NULL," +
                "context_length INTEGER DEFAULT 0," +
                "cached_at INTEGER NOT NULL," +
                "PRIMARY KEY (provider, model_id)" +
                ")");

        db.execSQL("CREATE INDEX idx_wp_workspace ON " + TABLE_WORKSPACE_PROJECTS + "(workspace_id)");
        db.execSQL("CREATE INDEX idx_conv_workspace ON " + TABLE_CONVERSATIONS + "(workspace_id)");
        db.execSQL("CREATE INDEX idx_msg_conversation ON " + TABLE_MESSAGES + "(conversation_id)");
        db.execSQL("CREATE INDEX idx_model_provider ON " + TABLE_MODEL_CACHE + "(provider)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    // Workspace operations

    public void insertWorkspace(Workspace workspace) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("id", workspace.id);
        cv.put("name", workspace.name);
        cv.put("description", workspace.description);
        cv.put("created_at", workspace.createdAt);
        cv.put("updated_at", workspace.updatedAt);
        db.insertWithOnConflict(TABLE_WORKSPACES, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void updateWorkspace(Workspace workspace) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", workspace.name);
        cv.put("description", workspace.description);
        cv.put("updated_at", System.currentTimeMillis());
        db.update(TABLE_WORKSPACES, cv, "id = ?", new String[]{workspace.id});
    }

    public void deleteWorkspace(String workspaceId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_WORKSPACES, "id = ?", new String[]{workspaceId});
    }

    public List<Workspace> getAllWorkspaces() {
        List<Workspace> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_WORKSPACES, null, null, null, null, null, "updated_at DESC")) {
            while (c.moveToNext()) {
                list.add(workspaceFromCursor(c));
            }
        }
        return list;
    }

    public Workspace getWorkspace(String id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_WORKSPACES, null, "id = ?", new String[]{id}, null, null, null)) {
            if (c.moveToFirst()) {
                return workspaceFromCursor(c);
            }
        }
        return null;
    }

    private Workspace workspaceFromCursor(Cursor c) {
        Workspace w = new Workspace();
        w.id = c.getString(c.getColumnIndexOrThrow("id"));
        w.name = c.getString(c.getColumnIndexOrThrow("name"));
        w.description = c.getString(c.getColumnIndexOrThrow("description"));
        w.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        w.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        return w;
    }

    // Workspace project operations

    public void addProjectToWorkspace(String workspaceId, String scId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("workspace_id", workspaceId);
        cv.put("sc_id", scId);
        cv.put("added_at", System.currentTimeMillis());
        db.insertWithOnConflict(TABLE_WORKSPACE_PROJECTS, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        touchWorkspace(workspaceId);
    }

    public void removeProjectFromWorkspace(String workspaceId, String scId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_WORKSPACE_PROJECTS, "workspace_id = ? AND sc_id = ?", new String[]{workspaceId, scId});
        touchWorkspace(workspaceId);
    }

    public List<String> getWorkspaceProjectIds(String workspaceId) {
        List<String> ids = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_WORKSPACE_PROJECTS, new String[]{"sc_id"}, "workspace_id = ?", new String[]{workspaceId}, null, null, "added_at ASC")) {
            while (c.moveToNext()) {
                ids.add(c.getString(0));
            }
        }
        return ids;
    }

    private void touchWorkspace(String workspaceId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("updated_at", System.currentTimeMillis());
        db.update(TABLE_WORKSPACES, cv, "id = ?", new String[]{workspaceId});
    }

    // Conversation operations

    public void insertConversation(Conversation conversation) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("id", conversation.id);
        cv.put("workspace_id", conversation.workspaceId);
        cv.put("title", conversation.title);
        cv.put("provider", conversation.provider);
        cv.put("model", conversation.model);
        cv.put("created_at", conversation.createdAt);
        cv.put("updated_at", conversation.updatedAt);
        db.insertWithOnConflict(TABLE_CONVERSATIONS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        touchWorkspace(conversation.workspaceId);
    }

    public void updateConversation(Conversation conversation) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", conversation.title);
        cv.put("provider", conversation.provider);
        cv.put("model", conversation.model);
        cv.put("updated_at", System.currentTimeMillis());
        db.update(TABLE_CONVERSATIONS, cv, "id = ?", new String[]{conversation.id});
    }

    public void deleteConversation(String conversationId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_CONVERSATIONS, "id = ?", new String[]{conversationId});
    }

    public List<Conversation> getConversationsForWorkspace(String workspaceId) {
        List<Conversation> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_CONVERSATIONS, null, "workspace_id = ?", new String[]{workspaceId}, null, null, "updated_at DESC")) {
            while (c.moveToNext()) {
                list.add(conversationFromCursor(c));
            }
        }
        return list;
    }

    public Conversation getConversation(String id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_CONVERSATIONS, null, "id = ?", new String[]{id}, null, null, null)) {
            if (c.moveToFirst()) {
                return conversationFromCursor(c);
            }
        }
        return null;
    }

    private Conversation conversationFromCursor(Cursor c) {
        Conversation conv = new Conversation();
        conv.id = c.getString(c.getColumnIndexOrThrow("id"));
        conv.workspaceId = c.getString(c.getColumnIndexOrThrow("workspace_id"));
        conv.title = c.getString(c.getColumnIndexOrThrow("title"));
        conv.provider = c.getString(c.getColumnIndexOrThrow("provider"));
        conv.model = c.getString(c.getColumnIndexOrThrow("model"));
        conv.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        conv.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        return conv;
    }

    // Message operations

    public void insertMessage(ChatMessage message) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("id", message.id);
        cv.put("conversation_id", message.conversationId);
        cv.put("role", message.role);
        cv.put("content", message.content);
        cv.put("tool_calls", message.toolCalls);
        cv.put("tool_results", message.toolResults);
        cv.put("status", message.status);
        cv.put("created_at", message.createdAt);
        db.insertWithOnConflict(TABLE_MESSAGES, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void updateMessage(ChatMessage message) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("content", message.content);
        cv.put("tool_calls", message.toolCalls);
        cv.put("tool_results", message.toolResults);
        cv.put("status", message.status);
        db.update(TABLE_MESSAGES, cv, "id = ?", new String[]{message.id});
    }

    public List<ChatMessage> getMessagesForConversation(String conversationId) {
        List<ChatMessage> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_MESSAGES, null, "conversation_id = ?", new String[]{conversationId}, null, null, "created_at ASC")) {
            while (c.moveToNext()) {
                list.add(messageFromCursor(c));
            }
        }
        return list;
    }

    private ChatMessage messageFromCursor(Cursor c) {
        ChatMessage m = new ChatMessage();
        m.id = c.getString(c.getColumnIndexOrThrow("id"));
        m.conversationId = c.getString(c.getColumnIndexOrThrow("conversation_id"));
        m.role = c.getString(c.getColumnIndexOrThrow("role"));
        m.content = c.getString(c.getColumnIndexOrThrow("content"));
        m.toolCalls = c.getString(c.getColumnIndexOrThrow("tool_calls"));
        m.toolResults = c.getString(c.getColumnIndexOrThrow("tool_results"));
        m.status = c.getString(c.getColumnIndexOrThrow("status"));
        m.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        return m;
    }

    // Provider settings operations

    public void saveProviderSetting(String provider, String apiKey, boolean enabled) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("provider", provider);
        cv.put("api_key", apiKey);
        cv.put("enabled", enabled ? 1 : 0);
        db.insertWithOnConflict(TABLE_PROVIDER_SETTINGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String getApiKey(String provider) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_PROVIDER_SETTINGS, new String[]{"api_key"}, "provider = ?", new String[]{provider}, null, null, null)) {
            if (c.moveToFirst()) {
                return c.getString(0);
            }
        }
        return "";
    }

    public boolean isProviderEnabled(String provider) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_PROVIDER_SETTINGS, new String[]{"enabled"}, "provider = ?", new String[]{provider}, null, null, null)) {
            if (c.moveToFirst()) {
                return c.getInt(0) == 1;
            }
        }
        return false;
    }

    public List<ProviderSetting> getAllProviderSettings() {
        List<ProviderSetting> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_PROVIDER_SETTINGS, null, null, null, null, null, null)) {
            while (c.moveToNext()) {
                ProviderSetting ps = new ProviderSetting();
                ps.provider = c.getString(c.getColumnIndexOrThrow("provider"));
                ps.apiKey = c.getString(c.getColumnIndexOrThrow("api_key"));
                ps.enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) == 1;
                list.add(ps);
            }
        }
        return list;
    }

    // Model cache operations

    public void cacheModels(String provider, List<ModelInfo> models) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_MODEL_CACHE, "provider = ?", new String[]{provider});
            long now = System.currentTimeMillis();
            for (ModelInfo model : models) {
                ContentValues cv = new ContentValues();
                cv.put("provider", provider);
                cv.put("model_id", model.id);
                cv.put("model_name", model.name);
                cv.put("context_length", model.contextLength);
                cv.put("cached_at", now);
                db.insert(TABLE_MODEL_CACHE, null, cv);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<ModelInfo> getCachedModels(String provider) {
        List<ModelInfo> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_MODEL_CACHE, null, "provider = ?", new String[]{provider}, null, null, "model_name ASC")) {
            while (c.moveToNext()) {
                ModelInfo m = new ModelInfo();
                m.id = c.getString(c.getColumnIndexOrThrow("model_id"));
                m.name = c.getString(c.getColumnIndexOrThrow("model_name"));
                m.contextLength = c.getInt(c.getColumnIndexOrThrow("context_length"));
                list.add(m);
            }
        }
        return list;
    }

    public boolean hasModelsCache(String provider) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_MODEL_CACHE + " WHERE provider = ?", new String[]{provider})) {
            if (c.moveToFirst()) {
                return c.getInt(0) > 0;
            }
        }
        return false;
    }

    // Data classes

    public static class Workspace {
        public String id;
        public String name;
        public String description;
        public long createdAt;
        public long updatedAt;
    }

    public static class Conversation {
        public String id;
        public String workspaceId;
        public String title;
        public String provider;
        public String model;
        public long createdAt;
        public long updatedAt;
    }

    public static class ChatMessage {
        public String id;
        public String conversationId;
        public String role; // "user", "assistant", "system", "tool"
        public String content;
        public String toolCalls;
        public String toolResults;
        public String status; // "complete", "streaming", "error", "tool_calling"
        public long createdAt;
    }

    public static class ProviderSetting {
        public String provider;
        public String apiKey;
        public boolean enabled;
    }

    public static class ModelInfo {
        public String id;
        public String name;
        public int contextLength;
    }
}
