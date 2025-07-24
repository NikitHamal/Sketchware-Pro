package pro.sketchware.activities.ai.storage;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class JsonObjectTypeAdapterTest {

    private JsonObjectTypeAdapter adapter;
    
    @Mock
    private JsonReader mockJsonReader;
    
    @Mock
    private JsonWriter mockJsonWriter;

    @Before
    public void setUp() {
        adapter = new JsonObjectTypeAdapter();
    }

    // Tests for write() method - Happy Path scenarios
    @Test
    public void testWrite_ValidJsonObject_WritesCorrectJsonValue() throws IOException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("key", "value");
        jsonObject.put("number", 42);
        
        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);
        
        adapter.write(jsonWriter, jsonObject);
        jsonWriter.close();
        
        String result = stringWriter.toString();
        assertTrue("Should contain the JSON string representation", 
                   result.contains("\"key\":\"value\"") || result.contains("key") && result.contains("value"));
    }

    @Test
    public void testWrite_EmptyJsonObject_WritesEmptyObject() throws IOException {
        JSONObject emptyJsonObject = new JSONObject();
        
        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);
        
        adapter.write(jsonWriter, emptyJsonObject);
        jsonWriter.close();
        
        String result = stringWriter.toString();
        assertEquals("Should write empty JSON object", "{}", result);
    }

    @Test
    public void testWrite_ComplexJsonObject_WritesCorrectStructure() throws IOException {
        JSONObject complexObject = new JSONObject();
        JSONObject nestedObject = new JSONObject();
        nestedObject.put("nested", "value");
        complexObject.put("parent", nestedObject);
        complexObject.put("array", new int[]{1, 2, 3});
        complexObject.put("boolean", true);
        complexObject.put("nullValue", JSONObject.NULL);
        
        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);
        
        adapter.write(jsonWriter, complexObject);
        jsonWriter.close();
        
        String result = stringWriter.toString();
        assertTrue("Should contain nested structure", result.contains("parent"));
        assertTrue("Should contain boolean value", result.contains("true"));
    }

    // Tests for write() method - Edge cases
    @Test
    public void testWrite_NullJsonObject_WritesNull() throws IOException {
        adapter.write(mockJsonWriter, null);
        
        verify(mockJsonWriter).nullValue();
        verify(mockJsonWriter, never()).jsonValue(anyString());
    }

    @Test
    public void testWrite_JsonObjectWithSpecialCharacters_HandlesCorrectly() throws IOException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("special", "\"quotes\" and \\ backslashes \n newlines");
        
        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);
        
        adapter.write(jsonWriter, jsonObject);
        jsonWriter.close();
        
        String result = stringWriter.toString();
        assertNotNull("Should handle special characters without throwing", result);
    }

    @Test
    public void testWrite_JsonObjectWithUnicodeCharacters_HandlesCorrectly() throws IOException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("unicode", "测试 🌟 ñáéíóú");
        
        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);
        
        adapter.write(jsonWriter, jsonObject);
        jsonWriter.close();
        
        String result = stringWriter.toString();
        assertNotNull("Should handle unicode characters without throwing", result);
    }

    // Tests for read() method - Happy Path scenarios
    @Test
    public void testRead_ValidJsonString_ReturnsCorrectJsonObject() throws IOException {
        String jsonString = "{\"key\":\"value\",\"number\":42}";
        StringReader stringReader = new StringReader("\"" + jsonString + "\"");
        JsonReader jsonReader = new JsonReader(stringReader);
        
        JSONObject result = adapter.read(jsonReader);
        
        assertNotNull("Should return a JSONObject", result);
        assertEquals("Should have correct string value", "value", result.optString("key"));
        assertEquals("Should have correct number value", 42, result.optInt("number"));
    }

    @Test
    public void testRead_EmptyJsonObject_ReturnsEmptyObject() throws IOException {
        String jsonString = "{}";
        StringReader stringReader = new StringReader("\"" + jsonString + "\"");
        JsonReader jsonReader = new JsonReader(stringReader);
        
        JSONObject result = adapter.read(jsonReader);
        
        assertNotNull("Should return a JSONObject", result);
        assertEquals("Should be empty", 0, result.length());
    }

    @Test
    public void testRead_ComplexJsonStructure_ParsesCorrectly() throws IOException {
        String jsonString = "{\"parent\":{\"nested\":\"value\"},\"array\":[1,2,3],\"boolean\":true,\"nullValue\":null}";
        StringReader stringReader = new StringReader("\"" + jsonString + "\"");
        JsonReader jsonReader = new JsonReader(stringReader);
        
        JSONObject result = adapter.read(jsonReader);
        
        assertNotNull("Should return a JSONObject", result);
        assertTrue("Should have parent key", result.has("parent"));
        assertTrue("Should have array key", result.has("array"));
        assertTrue("Should have boolean key", result.has("boolean"));
        assertEquals("Should parse boolean correctly", true, result.optBoolean("boolean"));
    }

    // Tests for read() method - Edge cases and error conditions
    @Test(expected = IOException.class)
    public void testRead_InvalidJsonString_ThrowsIOException() throws IOException {
        String invalidJson = "{invalid json}";
        StringReader stringReader = new StringReader("\"" + invalidJson + "\"");
        JsonReader jsonReader = new JsonReader(stringReader);
        
        adapter.read(jsonReader);
    }

    @Test(expected = IOException.class)
    public void testRead_MalformedJsonString_ThrowsIOException() throws IOException {
        String malformedJson = "{\"key\":\"value\",}"; // trailing comma
        StringReader stringReader = new StringReader("\"" + malformedJson + "\"");
        JsonReader jsonReader = new JsonReader(stringReader);
        
        adapter.read(jsonReader);
    }

    @Test(expected = IOException.class)
    public void testRead_UnbalancedBraces_ThrowsIOException() throws IOException {
        String unbalancedJson = "{\"key\":\"value\""; // missing closing brace
        StringReader stringReader = new StringReader("\"" + unbalancedJson + "\"");
        JsonReader jsonReader = new JsonReader(stringReader);
        
        adapter.read(jsonReader);
    }

    @Test(expected = IOException.class)
    public void testRead_EmptyString_ThrowsIOException() throws IOException {
        StringReader stringReader = new StringReader("\"\"");
        JsonReader jsonReader = new JsonReader(stringReader);
        
        adapter.read(jsonReader);
    }

    @Test(expected = IOException.class)
    public void testRead_NonJsonString_ThrowsIOException() throws IOException {
        String nonJsonString = "just a regular string";
        StringReader stringReader = new StringReader("\"" + nonJsonString + "\"");
        JsonReader jsonReader = new JsonReader(stringReader);
        
        adapter.read(jsonReader);
    }

    // Tests with mocked JsonReader for more controlled scenarios
    @Test(expected = IOException.class)
    public void testRead_JsonReaderThrowsIOException_PropagatesException() throws IOException {
        when(mockJsonReader.nextString()).thenThrow(new IOException("Mock IO exception"));
        
        adapter.read(mockJsonReader);
    }

    @Test
    public void testRead_ValidJsonWithSpecialCharacters_HandlesCorrectly() throws IOException {
        String jsonWithSpecialChars = "{\"special\":\"\\\"quotes\\\" and \\\\ backslashes \\n newlines\"}";
        StringReader stringReader = new StringReader("\"" + jsonWithSpecialChars + "\"");
        JsonReader jsonReader = new JsonReader(stringReader);
        
        JSONObject result = adapter.read(jsonReader);
        
        assertNotNull("Should handle special characters", result);
        assertTrue("Should contain special key", result.has("special"));
    }

    @Test
    public void testRead_JsonWithUnicodeCharacters_HandlesCorrectly() throws IOException {
        String jsonWithUnicode = "{\"unicode\":\"测试 🌟 ñáéíóú\"}";
        StringReader stringReader = new StringReader("\"" + jsonWithUnicode + "\"");
        JsonReader jsonReader = new JsonReader(stringReader);
        
        JSONObject result = adapter.read(jsonReader);
        
        assertNotNull("Should handle unicode characters", result);
        assertTrue("Should contain unicode key", result.has("unicode"));
    }

    // Integration tests - Write then Read
    @Test
    public void testWriteThenRead_RoundTrip_PreservesData() throws IOException {
        JSONObject originalObject = new JSONObject();
        originalObject.put("string", "test");
        originalObject.put("number", 123);
        originalObject.put("boolean", false);
        
        // Write to string
        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);
        adapter.write(jsonWriter, originalObject);
        jsonWriter.close();
        
        // Extract the JSON string (remove surrounding quotes)
        String writtenJson = stringWriter.toString();
        
        // Read back from string
        StringReader stringReader = new StringReader(writtenJson);
        JsonReader jsonReader = new JsonReader(stringReader);
        JSONObject readObject = adapter.read(jsonReader);
        
        // Verify data integrity
        assertNotNull("Should read back successfully", readObject);
        assertEquals("String value should be preserved", "test", readObject.optString("string"));
        assertEquals("Number value should be preserved", 123, readObject.optInt("number"));
        assertEquals("Boolean value should be preserved", false, readObject.optBoolean("boolean"));
    }

    @Test
    public void testWriteThenRead_EmptyObject_RoundTrip() throws IOException {
        JSONObject emptyObject = new JSONObject();
        
        // Write to string
        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);
        adapter.write(jsonWriter, emptyObject);
        jsonWriter.close();
        
        // Read back from string
        String writtenJson = stringWriter.toString();
        StringReader stringReader = new StringReader(writtenJson);
        JsonReader jsonReader = new JsonReader(stringReader);
        JSONObject readObject = adapter.read(jsonReader);
        
        // Verify empty object
        assertNotNull("Should read back successfully", readObject);
        assertEquals("Should remain empty", 0, readObject.length());
    }

    // Performance and stress tests
    @Test
    public void testWrite_LargeJsonObject_HandlesEfficiently() throws IOException {
        JSONObject largeObject = new JSONObject();
        for (int i = 0; i < 1000; i++) {
            largeObject.put("key" + i, "value" + i);
        }
        
        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);
        
        long startTime = System.currentTimeMillis();
        adapter.write(jsonWriter, largeObject);
        long endTime = System.currentTimeMillis();
        
        jsonWriter.close();
        
        assertTrue("Should complete in reasonable time", (endTime - startTime) < 5000); // 5 seconds max
        assertTrue("Should produce substantial output", stringWriter.toString().length() > 5000);
    }

    @Test
    public void testRead_LargeJsonString_HandlesEfficiently() throws IOException {
        StringBuilder largeJsonBuilder = new StringBuilder("{");
        for (int i = 0; i < 1000; i++) {
            if (i > 0) largeJsonBuilder.append(",");
            largeJsonBuilder.append("\"key").append(i).append("\":\"value").append(i).append("\"");
        }
        largeJsonBuilder.append("}");
        
        String largeJsonString = largeJsonBuilder.toString();
        StringReader stringReader = new StringReader("\"" + largeJsonString + "\"");
        JsonReader jsonReader = new JsonReader(stringReader);
        
        long startTime = System.currentTimeMillis();
        JSONObject result = adapter.read(jsonReader);
        long endTime = System.currentTimeMillis();
        
        assertNotNull("Should parse large JSON successfully", result);
        assertEquals("Should have correct number of keys", 1000, result.length());
        assertTrue("Should complete in reasonable time", (endTime - startTime) < 5000); // 5 seconds max
    }

    // Boundary value tests
    @Test
    public void testWrite_JsonObjectWithNullValues_HandlesCorrectly() throws IOException {
        JSONObject objectWithNulls = new JSONObject();
        objectWithNulls.put("explicitNull", JSONObject.NULL);
        objectWithNulls.put("regularValue", "test");
        
        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);
        
        adapter.write(jsonWriter, objectWithNulls);
        jsonWriter.close();
        
        String result = stringWriter.toString();
        assertTrue("Should handle null values", result.contains("null"));
        assertTrue("Should handle regular values", result.contains("test"));
    }

    @Test
    public void testRead_JsonStringWithNullValues_ParsesCorrectly() throws IOException {
        String jsonWithNulls = "{\"nullValue\":null,\"regularValue\":\"test\"}";
        StringReader stringReader = new StringReader("\"" + jsonWithNulls + "\"");
        JsonReader jsonReader = new JsonReader(stringReader);
        
        JSONObject result = adapter.read(jsonReader);
        
        assertNotNull("Should parse successfully", result);
        assertTrue("Should have null value key", result.has("nullValue"));
        assertTrue("Should have regular value key", result.has("regularValue"));
        assertEquals("Should parse regular value correctly", "test", result.optString("regularValue"));
    }

    // Additional edge cases specific to JsonObjectTypeAdapter implementation
    @Test
    public void testWrite_JsonObjectToString_CallsToStringMethod() throws IOException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("test", "value");
        
        // Using a spy to verify toString() is called internally
        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);
        
        adapter.write(jsonWriter, jsonObject);
        jsonWriter.close();
        
        // Verify the result matches what toString() would produce
        String result = stringWriter.toString();
        String expectedContent = jsonObject.toString();
        assertEquals("Should write the toString() representation", expectedContent, result);
    }

    @Test
    public void testRead_JsonExceptionWrapping_ConvertsToIOException() throws IOException {
        // Test that JSONException is properly wrapped in IOException
        when(mockJsonReader.nextString()).thenReturn("invalid json string");
        
        try {
            adapter.read(mockJsonReader);
            fail("Should have thrown IOException");
        } catch (IOException e) {
            assertTrue("Should wrap JSONException", e.getCause() instanceof JSONException);
        }
    }

    @Test
    public void testWrite_IOExceptionFromWriter_PropagatesCorrectly() throws IOException {
        doThrow(new IOException("Writer error")).when(mockJsonWriter).jsonValue(anyString());
        
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("key", "value");
        
        try {
            adapter.write(mockJsonWriter, jsonObject);
            fail("Should have thrown IOException");
        } catch (IOException e) {
            assertEquals("Should propagate original exception message", "Writer error", e.getMessage());
        }
    }

    @Test
    public void testRead_NullJsonReader_ThrowsException() throws IOException {
        try {
            adapter.read(null);
            fail("Should throw exception for null JsonReader");
        } catch (Exception e) {
            // Expected - either NullPointerException or IOException
            assertTrue("Should throw appropriate exception", 
                       e instanceof NullPointerException || e instanceof IOException);
        }
    }

    @Test
    public void testWrite_NullJsonWriter_ThrowsException() throws IOException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("key", "value");
        
        try {
            adapter.write(null, jsonObject);
            fail("Should throw exception for null JsonWriter");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    // Test JSON object with various data types
    @Test
    public void testWriteRead_AllJsonDataTypes_HandlesCorrectly() throws IOException {
        JSONObject allTypesObject = new JSONObject();
        allTypesObject.put("string", "text");
        allTypesObject.put("integer", 42);
        allTypesObject.put("double", 3.14159);
        allTypesObject.put("boolean", true);
        allTypesObject.put("null", JSONObject.NULL);
        
        JSONObject nestedObject = new JSONObject();
        nestedObject.put("nested", "value");
        allTypesObject.put("object", nestedObject);
        
        // Write
        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);
        adapter.write(jsonWriter, allTypesObject);
        jsonWriter.close();
        
        // Read back
        String writtenJson = stringWriter.toString();
        StringReader stringReader = new StringReader(writtenJson);
        JsonReader jsonReader = new JsonReader(stringReader);
        JSONObject readObject = adapter.read(jsonReader);
        
        // Verify all types are preserved
        assertEquals("String should be preserved", "text", readObject.optString("string"));
        assertEquals("Integer should be preserved", 42, readObject.optInt("integer"));
        assertEquals("Double should be preserved", 3.14159, readObject.optDouble("double"), 0.001);
        assertTrue("Boolean should be preserved", readObject.optBoolean("boolean"));
        assertTrue("Object should have nested structure", readObject.has("object"));
    }
}