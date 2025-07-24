package pro.sketchware.activities.ai.chat.models;

import java.util.List;

public class QwenResponse {
    public boolean success;
    public Data data;
    public List<Choice> choices;

    public static class Data {
        public String id;
    }

    public static class Choice {
        public Delta delta;
    }

    public static class Delta {
        public String content;
        public String status;
    }
}
