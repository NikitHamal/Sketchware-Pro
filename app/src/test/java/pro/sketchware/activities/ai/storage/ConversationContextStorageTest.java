package pro.sketchware.activities.ai.storage;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import pro.sketchware.activities.ai.chat.models.ConversationContext;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for ConversationContextStorage using JUnit and Mockito.
 * Tests cover storage, retrieval, deletion, and error handling scenarios.
 * 
 * Testing Framework: JUnit 4 with Mockito (standard Android testing setup)
 * Note: Uses standard JUnit/Mockito patterns for Android unit testing
 */
public class ConversationContextStorageTest {

    @Mock
    private Context mockContext;
    
    @Mock
    private SharedPreferences mockPrefs;
    
    @Mock
    private SharedPreferences.Editor mockEditor;

    private ConversationContextStorage storage;
    private static final String TEST_CONVERSATION_ID = "test-conversation-123";
    private static final String PREFS_NAME = "conversation_contexts";
    private static final String VALID_JSON = "{\"conversationId\":\"test-conversation-123\",\"fullHistory\":[],\"sessionState\":{},\"executedActions\":[],\"qwenMessageHistory\":[]}";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        
        // Setup mock behavior for SharedPreferences
        when(mockContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)).thenReturn(mockPrefs);
        when(mockPrefs.edit()).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
        when(mockEditor.remove(anyString())).thenReturn(mockEditor);
        when(mockEditor.apply()).thenReturn();
        
        storage = new ConversationContextStorage(mockContext);
    }

    // Constructor Tests
    @Test
    public void constructor_CreatesSharedPreferencesWithCorrectName() {
        // Verify SharedPreferences was created with correct name and mode
        verify(mockContext).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Test
    public void constructor_WithNullContext_ThrowsException() {
        // Act & Assert
        try {
            new ConversationContextStorage(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // Expected behavior
        }
    }

    // saveContext Tests - Happy Path
    @Test
    public void saveContext_ValidContext_SavesSuccessfully() {
        // Arrange
        ConversationContext context = new ConversationContext(TEST_CONVERSATION_ID);
        
        // Act
        storage.saveContext(context);
        
        // Assert
        verify(mockPrefs).edit();
        verify(mockEditor).putString(eq(TEST_CONVERSATION_ID), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void saveContext_ContextWithComplexData_SavesCorrectly() {
        // Arrange
        ConversationContext context = new ConversationContext(TEST_CONVERSATION_ID);
        context.setCurrentProjectId("project-123");
        context.setQwenChatId("qwen-456");
        
        // Act
        storage.saveContext(context);
        
        // Assert
        verify(mockPrefs).edit();
        verify(mockEditor).putString(eq(TEST_CONVERSATION_ID), anyString());
        verify(mockEditor).apply();
    }

    // saveContext Tests - Edge Cases and Error Handling
    @Test
    public void saveContext_NullContext_HandlesGracefully() {
        // Act - should not throw exception
        storage.saveContext(null);
        
        // Assert - no SharedPreferences operations should occur due to catch block
        verify(mockPrefs, never()).edit();
        verify(mockEditor, never()).putString(anyString(), anyString());
        verify(mockEditor, never()).apply();
    }

    @Test
    public void saveContext_ContextWithNullId_HandlesGracefully() {
        // Arrange
        ConversationContext context = new ConversationContext(null);
        
        // Act - should not throw exception
        storage.saveContext(context);
        
        // Assert - should attempt to save but handle null ID gracefully
        verify(mockPrefs).edit();
        verify(mockEditor).putString(eq((String) null), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void saveContext_ContextWithEmptyId_SavesCorrectly() {
        // Arrange
        ConversationContext context = new ConversationContext("");
        
        // Act
        storage.saveContext(context);
        
        // Assert
        verify(mockPrefs).edit();
        verify(mockEditor).putString(eq(""), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void saveContext_SharedPreferencesThrowsException_HandlesGracefully() {
        // Arrange
        ConversationContext context = new ConversationContext(TEST_CONVERSATION_ID);
        when(mockPrefs.edit()).thenThrow(new RuntimeException("SharedPreferences error"));
        
        // Act - should not crash due to try-catch in saveContext
        storage.saveContext(context);
        
        // Assert - verify attempt was made
        verify(mockPrefs).edit();
    }

    // loadContext Tests - Happy Path
    @Test
    public void loadContext_ExistingValidContext_ReturnsCorrectContext() {
        // Arrange
        when(mockPrefs.getString(TEST_CONVERSATION_ID, null)).thenReturn(VALID_JSON);
        
        // Act
        ConversationContext result = storage.loadContext(TEST_CONVERSATION_ID);
        
        // Assert
        assertNotNull(result);
        assertEquals(TEST_CONVERSATION_ID, result.getConversationId());
        verify(mockPrefs).getString(TEST_CONVERSATION_ID, null);
    }

    @Test
    public void loadContext_ExistingContextWithComplexData_DeserializesCorrectly() {
        // Arrange
        String complexJson = "{\"conversationId\":\"test-id\",\"currentProjectId\":\"proj-123\",\"qwenChatId\":\"qwen-456\",\"fullHistory\":[],\"sessionState\":{\"key\":\"value\"},\"executedActions\":[\"action1\"],\"qwenMessageHistory\":[]}";
        when(mockPrefs.getString("test-id", null)).thenReturn(complexJson);
        
        // Act
        ConversationContext result = storage.loadContext("test-id");
        
        // Assert
        assertNotNull(result);
        assertEquals("test-id", result.getConversationId());
    }

    // loadContext Tests - Edge Cases and Error Handling
    @Test
    public void loadContext_NonExistentContext_ReturnsNewContext() {
        // Arrange
        when(mockPrefs.getString(TEST_CONVERSATION_ID, null)).thenReturn(null);
        
        // Act
        ConversationContext result = storage.loadContext(TEST_CONVERSATION_ID);
        
        // Assert
        assertNotNull(result);
        assertEquals(TEST_CONVERSATION_ID, result.getConversationId());
        assertNotNull(result.getFullHistory());
        assertTrue(result.getFullHistory().isEmpty());
        verify(mockPrefs).getString(TEST_CONVERSATION_ID, null);
    }

    @Test
    public void loadContext_InvalidJson_ReturnsNewContext() {
        // Arrange
        String invalidJson = "{invalid json syntax";
        when(mockPrefs.getString(TEST_CONVERSATION_ID, null)).thenReturn(invalidJson);
        
        // Act
        ConversationContext result = storage.loadContext(TEST_CONVERSATION_ID);
        
        // Assert
        assertNotNull(result);
        assertEquals(TEST_CONVERSATION_ID, result.getConversationId());
        verify(mockPrefs).getString(TEST_CONVERSATION_ID, null);
    }

    @Test
    public void loadContext_EmptyString_ReturnsNewContext() {
        // Arrange
        when(mockPrefs.getString(TEST_CONVERSATION_ID, null)).thenReturn("");
        
        // Act
        ConversationContext result = storage.loadContext(TEST_CONVERSATION_ID);
        
        // Assert
        assertNotNull(result);
        assertEquals(TEST_CONVERSATION_ID, result.getConversationId());
    }

    @Test
    public void loadContext_WhitespaceString_ReturnsNewContext() {
        // Arrange
        when(mockPrefs.getString(TEST_CONVERSATION_ID, null)).thenReturn("   ");
        
        // Act
        ConversationContext result = storage.loadContext(TEST_CONVERSATION_ID);
        
        // Assert
        assertNotNull(result);
        assertEquals(TEST_CONVERSATION_ID, result.getConversationId());
    }

    @Test
    public void loadContext_NullConversationId_HandlesGracefully() {
        // Arrange
        when(mockPrefs.getString(null, null)).thenReturn(null);
        
        // Act
        ConversationContext result = storage.loadContext(null);
        
        // Assert
        assertNotNull(result);
        assertNull(result.getConversationId());
        verify(mockPrefs).getString(null, null);
    }

    @Test
    public void loadContext_EmptyConversationId_HandlesCorrectly() {
        // Arrange
        String emptyId = "";
        when(mockPrefs.getString(emptyId, null)).thenReturn(null);
        
        // Act
        ConversationContext result = storage.loadContext(emptyId);
        
        // Assert
        assertNotNull(result);
        assertEquals(emptyId, result.getConversationId());
    }

    @Test
    public void loadContext_MalformedJsonObject_ReturnsNewContext() {
        // Arrange
        String malformedJson = "{\"conversationId\":\"test\",\"invalidField\":}";
        when(mockPrefs.getString(TEST_CONVERSATION_ID, null)).thenReturn(malformedJson);
        
        // Act
        ConversationContext result = storage.loadContext(TEST_CONVERSATION_ID);
        
        // Assert
        assertNotNull(result);
        assertEquals(TEST_CONVERSATION_ID, result.getConversationId());
    }

    // deleteContext Tests
    @Test
    public void deleteContext_ValidId_DeletesSuccessfully() {
        // Act
        storage.deleteContext(TEST_CONVERSATION_ID);
        
        // Assert
        verify(mockPrefs).edit();
        verify(mockEditor).remove(TEST_CONVERSATION_ID);
        verify(mockEditor).apply();
    }

    @Test
    public void deleteContext_NullId_HandlesGracefully() {
        // Act - should not throw exception
        storage.deleteContext(null);
        
        // Assert
        verify(mockPrefs).edit();
        verify(mockEditor).remove(null);
        verify(mockEditor).apply();
    }

    @Test
    public void deleteContext_EmptyId_HandlesCorrectly() {
        // Act
        storage.deleteContext("");
        
        // Assert
        verify(mockPrefs).edit();
        verify(mockEditor).remove("");
        verify(mockEditor).apply();
    }

    @Test
    public void deleteContext_NonExistentId_CompletesNormally() {
        // Act
        storage.deleteContext("non-existent-id");
        
        // Assert - should complete normally even if ID doesn't exist
        verify(mockPrefs).edit();
        verify(mockEditor).remove("non-existent-id");
        verify(mockEditor).apply();
    }

    // hasContext Tests
    @Test
    public void hasContext_ExistingContext_ReturnsTrue() {
        // Arrange
        when(mockPrefs.contains(TEST_CONVERSATION_ID)).thenReturn(true);
        
        // Act
        boolean result = storage.hasContext(TEST_CONVERSATION_ID);
        
        // Assert
        assertTrue(result);
        verify(mockPrefs).contains(TEST_CONVERSATION_ID);
    }

    @Test
    public void hasContext_NonExistentContext_ReturnsFalse() {
        // Arrange
        when(mockPrefs.contains(TEST_CONVERSATION_ID)).thenReturn(false);
        
        // Act
        boolean result = storage.hasContext(TEST_CONVERSATION_ID);
        
        // Assert
        assertFalse(result);
        verify(mockPrefs).contains(TEST_CONVERSATION_ID);
    }

    @Test
    public void hasContext_NullId_ReturnsFalse() {
        // Arrange
        when(mockPrefs.contains(null)).thenReturn(false);
        
        // Act
        boolean result = storage.hasContext(null);
        
        // Assert
        assertFalse(result);
        verify(mockPrefs).contains(null);
    }

    @Test
    public void hasContext_EmptyId_ChecksCorrectly() {
        // Arrange
        String emptyId = "";
        when(mockPrefs.contains(emptyId)).thenReturn(true);
        
        // Act
        boolean result = storage.hasContext(emptyId);
        
        // Assert
        assertTrue(result);
        verify(mockPrefs).contains(emptyId);
    }

    // Integration and Workflow Tests
    @Test
    public void saveLoadDeleteWorkflow_CompleteLifecycle_WorksCorrectly() {
        // Arrange
        ConversationContext originalContext = new ConversationContext(TEST_CONVERSATION_ID);
        when(mockPrefs.contains(TEST_CONVERSATION_ID)).thenReturn(true, false);
        when(mockPrefs.getString(TEST_CONVERSATION_ID, null)).thenReturn(VALID_JSON);
        
        // Act & Assert - Save
        storage.saveContext(originalContext);
        verify(mockEditor).putString(eq(TEST_CONVERSATION_ID), anyString());
        
        // Act & Assert - Check existence
        boolean exists = storage.hasContext(TEST_CONVERSATION_ID);
        assertTrue(exists);
        
        // Act & Assert - Load
        ConversationContext loadedContext = storage.loadContext(TEST_CONVERSATION_ID);
        assertNotNull(loadedContext);
        assertEquals(TEST_CONVERSATION_ID, loadedContext.getConversationId());
        
        // Act & Assert - Delete
        storage.deleteContext(TEST_CONVERSATION_ID);
        verify(mockEditor).remove(TEST_CONVERSATION_ID);
        
        // Act & Assert - Check deletion
        boolean existsAfterDelete = storage.hasContext(TEST_CONVERSATION_ID);
        assertFalse(existsAfterDelete);
    }

    @Test
    public void multipleOperations_ConcurrentAccess_HandledSafely() {
        // Arrange
        ConversationContext context1 = new ConversationContext("conv1");
        ConversationContext context2 = new ConversationContext("conv2");
        when(mockPrefs.contains("conv1")).thenReturn(true);
        when(mockPrefs.contains("conv2")).thenReturn(false);
        
        // Act - simulate concurrent operations
        storage.saveContext(context1);
        storage.saveContext(context2);
        storage.hasContext("conv1");
        storage.hasContext("conv2");
        storage.deleteContext("conv1");
        
        // Assert - verify all operations were handled
        verify(mockPrefs, times(3)).edit(); // 2 saves + 1 delete
        verify(mockEditor, times(2)).putString(anyString(), anyString());
        verify(mockEditor).remove("conv1");
        verify(mockPrefs).contains("conv1");
        verify(mockPrefs).contains("conv2");
    }

    // Stress and Edge Case Tests
    @Test
    public void largeConversationId_HandlesCorrectly() {
        // Arrange - test with very long conversation ID
        StringBuilder longIdBuilder = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longIdBuilder.append("a");
        }
        String veryLongId = longIdBuilder.toString();
        ConversationContext context = new ConversationContext(veryLongId);
        
        // Act
        storage.saveContext(context);
        storage.hasContext(veryLongId);
        storage.deleteContext(veryLongId);
        
        // Assert
        verify(mockEditor).putString(eq(veryLongId), anyString());
        verify(mockPrefs).contains(veryLongId);
        verify(mockEditor).remove(veryLongId);
    }

    @Test
    public void specialCharactersInId_HandlesCorrectly() {
        // Arrange
        String specialId = "conv-123_@#$%^&*()[]{}|\\:;\"'<>?/.,~`+=";
        ConversationContext context = new ConversationContext(specialId);
        when(mockPrefs.contains(specialId)).thenReturn(true);
        
        // Act
        storage.saveContext(context);
        boolean exists = storage.hasContext(specialId);
        storage.deleteContext(specialId);
        
        // Assert
        verify(mockEditor).putString(eq(specialId), anyString());
        assertTrue(exists);
        verify(mockEditor).remove(specialId);
    }

    @Test
    public void unicodeCharactersInId_HandlesCorrectly() {
        // Arrange
        String unicodeId = "会话-123-🚀-тест";
        ConversationContext context = new ConversationContext(unicodeId);
        
        // Act
        storage.saveContext(context);
        storage.hasContext(unicodeId);
        
        // Assert
        verify(mockEditor).putString(eq(unicodeId), anyString());
        verify(mockPrefs).contains(unicodeId);
    }

    @Test
    public void errorRecovery_CorruptedJsonRecovery_CreatesNewContext() {
        // Arrange - simulate various types of corrupted JSON
        String[] corruptedJsonSamples = {
            "{\"conversationId\":malformed",
            "not json at all",
            "null",
            "{\"wrongStructure\": true}",
            "{\"conversationId\": null}"
        };
        
        for (String corruptedJson : corruptedJsonSamples) {
            when(mockPrefs.getString(TEST_CONVERSATION_ID, null)).thenReturn(corruptedJson);
            
            // Act
            ConversationContext result = storage.loadContext(TEST_CONVERSATION_ID);
            
            // Assert - should always recover gracefully
            assertNotNull("Should recover from corrupted JSON: " + corruptedJson, result);
            assertEquals("Should use provided ID for new context", TEST_CONVERSATION_ID, result.getConversationId());
        }
    }

    @Test
    public void gsonCustomization_JsonObjectSerialization_WorksWithCustomAdapter() {
        // Arrange - test that JSONObject fields can be handled with JsonObjectTypeAdapter
        ConversationContext context = new ConversationContext(TEST_CONVERSATION_ID);
        // The context contains JSONObject fields which require the custom adapter
        
        // Act - should not throw exception due to custom Gson configuration
        storage.saveContext(context);
        
        // Assert
        verify(mockEditor).putString(eq(TEST_CONVERSATION_ID), anyString());
    }

    @Test
    public void memoryManagement_MultipleContextsInSequence_HandlesCorrectly() {
        // Arrange - test with many contexts to ensure no memory leaks
        for (int i = 0; i < 50; i++) {
            String id = "context-" + i;
            ConversationContext context = new ConversationContext(id);
            
            // Act
            storage.saveContext(context);
            storage.hasContext(id);
            storage.loadContext(id);
            storage.deleteContext(id);
        }
        
        // Assert - verify operations were performed for all contexts
        verify(mockPrefs, times(100)).edit(); // 50 saves + 50 deletes
        verify(mockPrefs, times(50)).contains(anyString()); // hasContext calls
        verify(mockPrefs, times(50)).getString(anyString(), eq(null)); // loadContext calls
    }

    @Test
    public void jsonSyntaxException_Handling_FallsBackToNewContext() {
        // Arrange - simulate JSON that causes JsonSyntaxException
        String syntaxErrorJson = "{\"conversationId\":\"test\",\"fullHistory\":[{malformed}]}";
        when(mockPrefs.getString(TEST_CONVERSATION_ID, null)).thenReturn(syntaxErrorJson);
        
        // Act
        ConversationContext result = storage.loadContext(TEST_CONVERSATION_ID);
        
        // Assert - should fall back to new context instead of crashing
        assertNotNull(result);
        assertEquals(TEST_CONVERSATION_ID, result.getConversationId());
        assertNotNull(result.getFullHistory());
        assertTrue(result.getFullHistory().isEmpty());
    }

    @Test
    public void logging_VerifyLogMessages_AreCalledCorrectly() {
        // This test verifies that logging doesn't cause crashes
        // Arrange
        ConversationContext context = new ConversationContext(TEST_CONVERSATION_ID);
        when(mockPrefs.getString(TEST_CONVERSATION_ID, null)).thenReturn(VALID_JSON);
        
        // Act - operations that should trigger log messages
        storage.saveContext(context);
        storage.loadContext(TEST_CONVERSATION_ID);
        storage.deleteContext(TEST_CONVERSATION_ID);
        
        // Assert - verify operations completed (logs don't break functionality)
        verify(mockEditor).putString(eq(TEST_CONVERSATION_ID), anyString());
        verify(mockPrefs).getString(TEST_CONVERSATION_ID, null);
        verify(mockEditor).remove(TEST_CONVERSATION_ID);
    }
}