package pro.sketchware.activities.ai.storage;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class JsonObjectTypeAdapter extends TypeAdapter<JSONObject> {

    @Override
    public void write(JsonWriter out, JSONObject value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.jsonValue(value.toString());
    }

    @Override
    public JSONObject read(JsonReader in) throws IOException {
        try {
            return new JSONObject(in.nextString());
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }
}
