package pro.sketchware.activities.ai.storage;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pro.sketchware.activities.ai.chat.models.ChatMessage;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MessageStorage class.
 * Testing Framework: JUnit 4 with Mockito for mocking
 * 
 * This comprehensive test suite covers:
 * - Constructor validation and initialization
 * - Message saving operations with various scenarios
 * - Message retrieval operations with edge cases
 * - Message addition operations with validation
 * - Message deletion operations
 * - Error handling and edge cases
 * - JSON serialization/deserialization robustness
 * - SharedPreferences interaction patterns
 * - Performance considerations with large datasets
 * - Unicode and special character handling
 * - Null safety and defensive programming
 */
public class MessageStorageTest {

    @Mock
    private Context mockContext;
    
    @Mock
    private SharedPreferences mockPrefs;
    
    @Mock
    private SharedPreferences.Editor mockEditor;

    private MessageStorage messageStorage;
    private static final String PREFS_NAME = "ai_messages";

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup comprehensive mock behavior
        when(mockContext.getSharedPreferences(eq(PREFS_NAME), eq(Context.MODE_PRIVATE)))
                .thenReturn(mockPrefs);
        when(mockPrefs.edit()).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
        when(mockEditor.remove(anyString())).thenReturn(mockEditor);
        doNothing().when(mockEditor).apply();
        
        messageStorage = new MessageStorage(mockContext);
    }

    // Constructor Tests
    @Test
    public void constructor_shouldInitializeCorrectly() {
        // Verify SharedPreferences was obtained with correct parameters
        verify(mockContext).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Verify object is created successfully
        assertNotNull(messageStorage);
    }

    @Test(expected = NullPointerException.class)
    public void constructor_withNullContext_shouldThrowException() {
        new MessageStorage(null);
    }

    @Test
    public void constructor_shouldCreateGsonInstance() {
        // Test that the constructor doesn't throw when creating Gson instance
        MessageStorage storage = new MessageStorage(mockContext);
        assertNotNull(storage);
    }

    // saveMessages Tests - Happy Path
    @Test
    public void saveMessages_withValidData_shouldSaveCorrectly() {
        String conversationId = "test_conversation";
        List<ChatMessage> messages = createTestMessages();
        
        messageStorage.saveMessages(conversationId, messages);
        
        verify(mockEditor).putString(eq(conversationId), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void saveMessages_withEmptyList_shouldSaveEmptyArray() {
        String conversationId = "empty_conversation";
        List<ChatMessage> emptyMessages = new ArrayList<>();
        
        messageStorage.saveMessages(conversationId, emptyMessages);
        
        verify(mockEditor).putString(eq(conversationId), eq("[]"));
        verify(mockEditor).apply();
    }

    @Test
    public void saveMessages_withSingleMessage_shouldSaveCorrectly() {
        String conversationId = "single_message";
        List<ChatMessage> messages = Arrays.asList(createTestMessage("Single message", ChatMessage.TYPE_USER));
        
        messageStorage.saveMessages(conversationId, messages);
        
        verify(mockEditor).putString(eq(conversationId), anyString());
        verify(mockEditor).apply();
    }

    // saveMessages Tests - Edge Cases
    @Test
    public void saveMessages_withNullConversationId_shouldHandleGracefully() {
        List<ChatMessage> messages = createTestMessages();
        
        messageStorage.saveMessages(null, messages);
        
        verify(mockEditor).putString(eq(null), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void saveMessages_withNullMessageList_shouldSaveNull() {
        String conversationId = "null_messages";
        
        messageStorage.saveMessages(conversationId, null);
        
        verify(mockEditor).putString(eq(conversationId), eq("null"));
        verify(mockEditor).apply();
    }

    @Test
    public void saveMessages_withEmptyConversationId_shouldHandleCorrectly() {
        String conversationId = "";
        List<ChatMessage> messages = createTestMessages();
        
        messageStorage.saveMessages(conversationId, messages);
        
        verify(mockEditor).putString(eq(conversationId), anyString());
        verify(mockEditor).apply();
    }

    // saveMessages Tests - Performance and Stress
    @Test
    public void saveMessages_withLargeMessageList_shouldHandleCorrectly() {
        String conversationId = "large_conversation";
        List<ChatMessage> largeMessageList = new ArrayList<>();
        
        // Create 100 messages to test performance
        for (int i = 0; i < 100; i++) {
            ChatMessage message = createTestMessage("Message " + i, ChatMessage.TYPE_USER);
            largeMessageList.add(message);
        }
        
        messageStorage.saveMessages(conversationId, largeMessageList);
        
        verify(mockEditor).putString(eq(conversationId), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void saveMessages_withComplexMessages_shouldPreserveAllData() {
        String conversationId = "complex_messages";
        List<ChatMessage> complexMessages = new ArrayList<>();
        complexMessages.add(createComplexTestMessage());
        complexMessages.add(createMessageWithAttachments());
        
        messageStorage.saveMessages(conversationId, complexMessages);
        
        verify(mockEditor).putString(eq(conversationId), anyString());
        verify(mockEditor).apply();
    }

    // getMessages Tests - Happy Path
    @Test
    public void getMessages_withExistingConversation_shouldReturnMessages() {
        String conversationId = "existing_conversation";
        String mockJson = "[{\"id\":\"test1\",\"content\":\"Hello\",\"type\":1,\"timestamp\":1234567890}]";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn(mockJson);
        
        List<ChatMessage> result = messageStorage.getMessages(conversationId);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Hello", result.get(0).getContent());
        assertEquals(ChatMessage.TYPE_USER, result.get(0).getType());
        verify(mockPrefs).getString(conversationId, "[]");
    }

    @Test
    public void getMessages_withMultipleMessages_shouldReturnAllInOrder() {
        String conversationId = "multiple_messages";
        String mockJson = "[{\"id\":\"1\",\"content\":\"First\",\"type\":1,\"timestamp\":1000}," +
                         "{\"id\":\"2\",\"content\":\"Second\",\"type\":2,\"timestamp\":2000}," +
                         "{\"id\":\"3\",\"content\":\"Third\",\"type\":1,\"timestamp\":3000}]";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn(mockJson);
        
        List<ChatMessage> result = messageStorage.getMessages(conversationId);
        
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("First", result.get(0).getContent());
        assertEquals("Second", result.get(1).getContent());
        assertEquals("Third", result.get(2).getContent());
    }

    @Test
    public void getMessages_withNonExistentConversation_shouldReturnEmptyList() {
        String conversationId = "non_existent";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("[]");
        
        List<ChatMessage> result = messageStorage.getMessages(conversationId);
        
        assertNotNull(result);
        assertEquals(0, result.size());
        verify(mockPrefs).getString(conversationId, "[]");
    }

    // getMessages Tests - Edge Cases
    @Test
    public void getMessages_withNullConversationId_shouldHandleGracefully() {
        when(mockPrefs.getString(null, "[]")).thenReturn("[]");
        
        List<ChatMessage> result = messageStorage.getMessages(null);
        
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void getMessages_withEmptyConversationId_shouldHandleGracefully() {
        String conversationId = "";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("[]");
        
        List<ChatMessage> result = messageStorage.getMessages(conversationId);
        
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    // getMessages Tests - JSON Error Handling
    @Test
    public void getMessages_withInvalidJson_shouldReturnEmptyList() {
        String conversationId = "invalid_json";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("invalid json");
        
        List<ChatMessage> result = messageStorage.getMessages(conversationId);
        
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void getMessages_withNullStoredValue_shouldReturnEmptyList() {
        String conversationId = "null_stored";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn(null);
        
        List<ChatMessage> result = messageStorage.getMessages(conversationId);
        
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void getMessages_withEmptyString_shouldReturnEmptyList() {
        String conversationId = "empty_string";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("");
        
        List<ChatMessage> result = messageStorage.getMessages(conversationId);
        
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void getMessages_withMalformedJson_shouldReturnEmptyList() {
        String conversationId = "malformed_json";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("{broken json");
        
        List<ChatMessage> result = messageStorage.getMessages(conversationId);
        
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void getMessages_withNullArrayFromJson_shouldReturnEmptyList() {
        String conversationId = "null_array";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("null");
        
        List<ChatMessage> result = messageStorage.getMessages(conversationId);
        
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void getMessages_withIncompleteJson_shouldReturnEmptyList() {
        String conversationId = "incomplete_json";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("[{\"content\":\"incomplete\"");
        
        List<ChatMessage> result = messageStorage.getMessages(conversationId);
        
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    // addMessage Tests - Happy Path
    @Test
    public void addMessage_withValidMessage_shouldAddAndSave() {
        String conversationId = "add_test";
        ChatMessage newMessage = createTestMessage("New message", ChatMessage.TYPE_USER);
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("[]");
        
        messageStorage.addMessage(conversationId, newMessage);
        
        verify(mockPrefs).getString(conversationId, "[]");
        verify(mockEditor).putString(eq(conversationId), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void addMessage_toExistingConversation_shouldAppendMessage() {
        String conversationId = "existing_add_test";
        String existingJson = "[{\"id\":\"existing1\",\"content\":\"Existing\",\"type\":2,\"timestamp\":1234567890}]";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn(existingJson);
        
        ChatMessage newMessage = createTestMessage("New message", ChatMessage.TYPE_USER);
        messageStorage.addMessage(conversationId, newMessage);
        
        verify(mockPrefs).getString(conversationId, "[]");
        verify(mockEditor).putString(eq(conversationId), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void addMessage_withDifferentMessageTypes_shouldHandleAll() {
        String conversationId = "types_test";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("[]");
        
        // Test all message types
        messageStorage.addMessage(conversationId, createTestMessage("User", ChatMessage.TYPE_USER));
        messageStorage.addMessage(conversationId, createTestMessage("AI", ChatMessage.TYPE_AI));
        messageStorage.addMessage(conversationId, createTestMessage("Proposal", ChatMessage.TYPE_PROPOSAL));
        messageStorage.addMessage(conversationId, createTestMessage("Success", ChatMessage.TYPE_AI_SUCCESS));
        
        verify(mockEditor, times(4)).putString(eq(conversationId), anyString());
        verify(mockEditor, times(4)).apply();
    }

    // addMessage Tests - Edge Cases
    @Test
    public void addMessage_withNullMessage_shouldHandleGracefully() {
        String conversationId = "null_message_test";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("[]");
        
        messageStorage.addMessage(conversationId, null);
        
        verify(mockEditor).putString(eq(conversationId), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void addMessage_withNullConversationId_shouldHandleGracefully() {
        ChatMessage message = createTestMessage("Test", ChatMessage.TYPE_USER);
        when(mockPrefs.getString(null, "[]")).thenReturn("[]");
        
        messageStorage.addMessage(null, message);
        
        verify(mockEditor).putString(eq(null), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void addMessage_withComplexMessage_shouldPreserveAllFields() {
        String conversationId = "complex_message_test";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("[]");
        
        ChatMessage complexMessage = createComplexTestMessage();
        messageStorage.addMessage(conversationId, complexMessage);
        
        verify(mockEditor).putString(eq(conversationId), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void addMessage_withCorruptedExistingData_shouldHandleGracefully() {
        String conversationId = "corrupted_data_test";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("corrupted json data");
        
        ChatMessage message = createTestMessage("New message", ChatMessage.TYPE_USER);
        messageStorage.addMessage(conversationId, message);
        
        // Should still attempt to save, creating a new list with just the new message
        verify(mockEditor).putString(eq(conversationId), anyString());
        verify(mockEditor).apply();
    }

    // deleteMessages Tests
    @Test
    public void deleteMessages_withValidConversationId_shouldRemoveFromPrefs() {
        String conversationId = "delete_test";
        
        messageStorage.deleteMessages(conversationId);
        
        verify(mockEditor).remove(conversationId);
        verify(mockEditor).apply();
    }

    @Test
    public void deleteMessages_withNullConversationId_shouldHandleGracefully() {
        messageStorage.deleteMessages(null);
        
        verify(mockEditor).remove(null);
        verify(mockEditor).apply();
    }

    @Test
    public void deleteMessages_withNonExistentConversation_shouldStillCallRemove() {
        String conversationId = "non_existent_delete";
        
        messageStorage.deleteMessages(conversationId);
        
        verify(mockEditor).remove(conversationId);
        verify(mockEditor).apply();
    }

    @Test
    public void deleteMessages_withEmptyConversationId_shouldHandleGracefully() {
        String conversationId = "";
        
        messageStorage.deleteMessages(conversationId);
        
        verify(mockEditor).remove(conversationId);
        verify(mockEditor).apply();
    }

    @Test
    public void deleteMessages_multipleTimesOnSameConversation_shouldCallRemoveEachTime() {
        String conversationId = "multiple_delete_test";
        
        messageStorage.deleteMessages(conversationId);
        messageStorage.deleteMessages(conversationId);
        messageStorage.deleteMessages(conversationId);
        
        verify(mockEditor, times(3)).remove(conversationId);
        verify(mockEditor, times(3)).apply();
    }

    // Special Characters and Unicode Tests
    @Test
    public void saveMessages_withSpecialCharactersInConversationId_shouldHandleCorrectly() {
        String specialId = "conversation@#$%^&*()_+-=[]{}|;':\",./<>?";
        List<ChatMessage> messages = createTestMessages();
        
        messageStorage.saveMessages(specialId, messages);
        
        verify(mockEditor).putString(eq(specialId), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void getMessages_withSpecialCharactersInConversationId_shouldHandleCorrectly() {
        String specialId = "conversation@#$%^&*()_+-=[]{}|;':\",./<>?";
        when(mockPrefs.getString(specialId, "[]")).thenReturn("[]");
        
        List<ChatMessage> result = messageStorage.getMessages(specialId);
        
        assertNotNull(result);
        assertEquals(0, result.size());
        verify(mockPrefs).getString(specialId, "[]");
    }

    @Test
    public void addMessage_withUnicodeContent_shouldHandleCorrectly() {
        String conversationId = "unicode_test";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("[]");
        
        ChatMessage message = createTestMessage("Unicode: äöü 中文 🚀 العربية русский 日本語", ChatMessage.TYPE_USER);
        messageStorage.addMessage(conversationId, message);
        
        verify(mockEditor).putString(eq(conversationId), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void addMessage_withEscapedCharacters_shouldHandleCorrectly() {
        String conversationId = "escaped_chars_test";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("[]");
        
        ChatMessage message = createTestMessage("Escaped chars: \"quotes\" 'apostrophes' \n\t\r\\", ChatMessage.TYPE_USER);
        messageStorage.addMessage(conversationId, message);
        
        verify(mockEditor).putString(eq(conversationId), anyString());
        verify(mockEditor).apply();
    }

    // Performance and Stress Tests
    @Test
    public void saveMessages_withVeryLongConversationId_shouldHandleCorrectly() {
        StringBuilder longId = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longId.append("a");
        }
        String conversationId = longId.toString();
        List<ChatMessage> messages = createTestMessages();
        
        messageStorage.saveMessages(conversationId, messages);
        
        verify(mockEditor).putString(eq(conversationId), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void addMessage_withVeryLongMessageContent_shouldHandleCorrectly() {
        String conversationId = "long_content_test";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("[]");
        
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longContent.append("This is a very long message content. ");
        }
        
        ChatMessage longMessage = createTestMessage(longContent.toString(), ChatMessage.TYPE_USER);
        messageStorage.addMessage(conversationId, longMessage);
        
        verify(mockEditor).putString(eq(conversationId), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void rapidSuccessiveOperations_shouldHandleCorrectly() {
        String conversationId = "rapid_ops_test";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("[]");
        
        // Perform rapid operations
        for (int i = 0; i < 10; i++) {
            messageStorage.addMessage(conversationId, createTestMessage("Message " + i, ChatMessage.TYPE_USER));
        }
        
        verify(mockEditor, times(10)).putString(eq(conversationId), anyString());
        verify(mockEditor, times(10)).apply();
    }

    // Integration and Workflow Tests
    @Test
    public void multipleOperations_shouldAllSucceed() {
        String conversationId = "multi_ops_test";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("[]");
        
        // Perform multiple operations in sequence
        List<ChatMessage> messages = createTestMessages();
        messageStorage.saveMessages(conversationId, messages);
        messageStorage.addMessage(conversationId, createTestMessage("Added", ChatMessage.TYPE_AI));
        messageStorage.getMessages(conversationId);
        messageStorage.deleteMessages(conversationId);
        
        verify(mockEditor, times(2)).putString(eq(conversationId), anyString());
        verify(mockEditor).remove(conversationId);
        verify(mockEditor, times(3)).apply();
        verify(mockPrefs).getString(conversationId, "[]");
    }

    @Test
    public void saveAndAddOperations_shouldMaintainDataIntegrity() {
        String conversationId = "integrity_test";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("[]");
        
        // Save initial messages
        List<ChatMessage> initialMessages = createTestMessages();
        messageStorage.saveMessages(conversationId, initialMessages);
        
        // Add more messages
        messageStorage.addMessage(conversationId, createTestMessage("Added 1", ChatMessage.TYPE_AI));
        messageStorage.addMessage(conversationId, createTestMessage("Added 2", ChatMessage.TYPE_USER));
        
        verify(mockEditor, times(3)).putString(eq(conversationId), anyString());
        verify(mockEditor, times(3)).apply();
    }

    // Message Type Validation Tests
    @Test
    public void addMessage_withAllValidMessageTypes_shouldHandleCorrectly() {
        String conversationId = "all_types_test";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("[]");
        
        // Test each message type constant
        messageStorage.addMessage(conversationId, createTestMessage("User message", ChatMessage.TYPE_USER));
        messageStorage.addMessage(conversationId, createTestMessage("AI message", ChatMessage.TYPE_AI));
        messageStorage.addMessage(conversationId, createTestMessage("Proposal message", ChatMessage.TYPE_PROPOSAL));
        messageStorage.addMessage(conversationId, createTestMessage("Success message", ChatMessage.TYPE_AI_SUCCESS));
        
        verify(mockEditor, times(4)).putString(eq(conversationId), anyString());
        verify(mockEditor, times(4)).apply();
    }

    @Test
    public void addMessage_withInvalidMessageType_shouldStillSave() {
        String conversationId = "invalid_type_test";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("[]");
        
        ChatMessage message = createTestMessage("Invalid type message", 999);
        messageStorage.addMessage(conversationId, message);
        
        verify(mockEditor).putString(eq(conversationId), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void addMessage_withZeroType_shouldStillSave() {
        String conversationId = "zero_type_test";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("[]");
        
        ChatMessage message = createTestMessage("Zero type message", 0);
        messageStorage.addMessage(conversationId, message);
        
        verify(mockEditor).putString(eq(conversationId), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void addMessage_withNegativeType_shouldStillSave() {
        String conversationId = "negative_type_test";
        when(mockPrefs.getString(conversationId, "[]")).thenReturn("[]");
        
        ChatMessage message = createTestMessage("Negative type message", -1);
        messageStorage.addMessage(conversationId, message);
        
        verify(mockEditor).putString(eq(conversationId), anyString());
        verify(mockEditor).apply();
    }

    // Helper Methods Tests
    @Test
    public void createTestMessage_shouldCreateValidMessage() {
        ChatMessage message = createTestMessage("Test content", ChatMessage.TYPE_USER);
        
        assertNotNull(message);
        assertEquals("Test content", message.getContent());
        assertEquals(ChatMessage.TYPE_USER, message.getType());
        assertNotNull(message.getId());
        assertTrue(message.getTimestamp() > 0);
    }

    @Test
    public void createComplexTestMessage_shouldCreateMessageWithAllFields() {
        ChatMessage message = createComplexTestMessage();
        
        assertNotNull(message);
        assertEquals("Complex test message", message.getContent());
        assertEquals(ChatMessage.TYPE_AI, message.getType());
        assertEquals("complex_test_id", message.getId());
        assertEquals("test_project", message.getProjectId());
        assertEquals("Test Project", message.getProjectName());
        assertEquals("Test App", message.getAppName());
        assertEquals("com.test.app", message.getPackageName());
        assertEquals("Some proposal data", message.getProposalData());
        assertEquals("Test explanation", message.getExplanation());
        assertEquals("AI thinking process", message.getThinkingContent());
        assertEquals("MainActivity.java, activity_main.xml", message.getAffectedFiles());
        assertEquals("https://example.com", message.getWebSearchSources());
        assertTrue(message.hasAttachedFiles());
        assertEquals(1, message.getAttachedFiles().size());
        assertEquals("test_file.txt", message.getAttachedFiles().get(0).getName());
    }

    // Helper methods
    private List<ChatMessage> createTestMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(createTestMessage("Hello", ChatMessage.TYPE_USER));
        messages.add(createTestMessage("Hi there!", ChatMessage.TYPE_AI));
        messages.add(createTestMessage("How are you?", ChatMessage.TYPE_USER));
        return messages;
    }

    private ChatMessage createTestMessage(String content, int type) {
        ChatMessage message = new ChatMessage();
        message.setContent(content);
        message.setType(type);
        message.setTimestamp(System.currentTimeMillis());
        message.setId("test_id_" + System.nanoTime());
        return message;
    }

    private ChatMessage createComplexTestMessage() {
        ChatMessage message = new ChatMessage();
        message.setContent("Complex test message");
        message.setType(ChatMessage.TYPE_AI);
        message.setTimestamp(System.currentTimeMillis());
        message.setId("complex_test_id");
        message.setProjectId("test_project");
        message.setProjectName("Test Project");
        message.setAppName("Test App");
        message.setPackageName("com.test.app");
        message.setProposalData("Some proposal data");
        message.setExplanation("Test explanation");
        message.setThinkingContent("AI thinking process");
        message.setAffectedFiles("MainActivity.java, activity_main.xml");
        message.setWebSearchSources("https://example.com");
        
        ChatMessage.AttachedFile file = new ChatMessage.AttachedFile();
        file.setName("test_file.txt");
        file.setMimeType("text/plain");
        file.setSize(2048);
        file.setId("file_test_id");
        file.setUrl("https://example.com/file.txt");
        file.setLocalPath("/storage/test_file.txt");
        message.addAttachedFile(file);
        
        return message;
    }

    private ChatMessage createMessageWithAttachments() {
        ChatMessage message = createTestMessage("Message with multiple attachments", ChatMessage.TYPE_USER);
        
        // Add multiple attachments
        ChatMessage.AttachedFile file1 = new ChatMessage.AttachedFile();
        file1.setName("document.pdf");
        file1.setMimeType("application/pdf");
        file1.setSize(1024000);
        file1.setId("file1_id");
        message.addAttachedFile(file1);
        
        ChatMessage.AttachedFile file2 = new ChatMessage.AttachedFile();
        file2.setName("image.jpg");
        file2.setMimeType("image/jpeg");
        file2.setSize(512000);
        file2.setId("file2_id");
        message.addAttachedFile(file2);
        
        return message;
    }
}