package pro.sketchware.activities.ai.chat.models;

import java.util.List;

public class QwenRequest {
    public String title;
    public List<String> models;
    public String chat_mode;
    public String chat_type;
    public long timestamp;
    public boolean stream;
    public boolean incremental_output;
    public String chat_id;
    public String model;
    public String parent_id;
    public List<ChatMessage> messages;
    public FeatureConfig feature_config;
    public Extra extra;
    public String sub_chat_type;

    public static class FeatureConfig {
        public boolean thinking_enabled;
        public String output_schema;
        public int thinking_budget;
        public String search_version;
    }

    public static class Extra {
        public Meta meta;
    }

    public static class Meta {
        public String subChatType;
    }
}
