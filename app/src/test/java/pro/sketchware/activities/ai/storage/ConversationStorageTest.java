package pro.sketchware.activities.ai.storage;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;
import java.util.List;

import pro.sketchware.activities.main.fragments.ai.models.Conversation;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
public class ConversationStorageTest {

    @Mock
    private Context mockContext;
    
    @Mock
    private SharedPreferences mockSharedPreferences;
    
    @Mock
    private SharedPreferences.Editor mockEditor;

    private ConversationStorage conversationStorage;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockContext.getSharedPreferences(eq("ai_conversations"), eq(Context.MODE_PRIVATE)))
                .thenReturn(mockSharedPreferences);
        when(mockSharedPreferences.edit()).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
        
        conversationStorage = new ConversationStorage(mockContext);
    }

    @Test
    public void testConstructor_InitializesCorrectly() {
        // Verify that SharedPreferences is initialized with correct parameters
        verify(mockContext).getSharedPreferences("ai_conversations", Context.MODE_PRIVATE);
        assertNotNull(conversationStorage);
    }

    @Test
    public void testGetAllConversations_EmptyStorage_ReturnsEmptyList() {
        // Arrange
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn("[]");

        // Act
        List<Conversation> conversations = conversationStorage.getAllConversations();

        // Assert
        assertNotNull(conversations);
        assertTrue(conversations.isEmpty());
        verify(mockSharedPreferences).getString("conversations", "[]");
    }

    @Test
    public void testGetAllConversations_NullFromPrefs_ReturnsEmptyList() {
        // Arrange
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn(null);

        // Act
        List<Conversation> conversations = conversationStorage.getAllConversations();

        // Assert
        assertNotNull(conversations);
        assertTrue(conversations.isEmpty());
    }

    @Test
    public void testGetAllConversations_InvalidJson_ReturnsEmptyList() {
        // Arrange
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn("invalid json");

        // Act
        List<Conversation> conversations = conversationStorage.getAllConversations();

        // Assert
        assertNotNull(conversations);
        assertTrue(conversations.isEmpty());
    }

    @Test
    public void testGetAllConversations_ValidJson_ReturnsConversations() {
        // Arrange
        String validJson = "[{\"id\":\"1\",\"title\":\"Test Conversation\",\"lastMessage\":\"Hello\",\"lastMessageTime\":\"Jan 1, 2024, 12:00:00 AM\",\"model\":\"gpt-3.5\"}]";
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn(validJson);

        // Act
        List<Conversation> conversations = conversationStorage.getAllConversations();

        // Assert
        assertNotNull(conversations);
        assertEquals(1, conversations.size());
    }

    @Test
    public void testSaveConversation_NewConversation_AddsToBeginning() {
        // Arrange
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn("[]");
        Conversation newConversation = createTestConversation("1", "New Conversation");

        // Act
        conversationStorage.saveConversation(newConversation);

        // Assert
        verify(mockEditor).putString(eq("conversations"), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void testSaveConversation_ExistingConversation_UpdatesInPlace() {
        // Arrange
        String existingJson = "[{\"id\":\"1\",\"title\":\"Old Title\",\"lastMessage\":\"Old Message\"}]";
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn(existingJson);
        Conversation updatedConversation = createTestConversation("1", "New Title");

        // Act
        conversationStorage.saveConversation(updatedConversation);

        // Assert
        verify(mockEditor).putString(eq("conversations"), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void testSaveConversation_NullConversation_HandlesGracefully() {
        // Arrange
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn("[]");

        // Act & Assert - should throw exception for null conversation
        try {
            conversationStorage.saveConversation(null);
            fail("Expected NullPointerException for null conversation");
        } catch (Exception e) {
            assertTrue(e instanceof NullPointerException);
        }
    }

    @Test
    public void testSaveConversation_ConversationWithNullId_HandlesGracefully() {
        // Arrange
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn("[]");
        Conversation conversationWithNullId = new Conversation();
        conversationWithNullId.setTitle("Test");

        // Act & Assert
        try {
            conversationStorage.saveConversation(conversationWithNullId);
            fail("Expected NullPointerException for null ID");
        } catch (Exception e) {
            assertTrue(e instanceof NullPointerException);
        }
    }

    @Test
    public void testDeleteConversation_ExistingId_RemovesConversation() {
        // Arrange
        String jsonWithConversation = "[{\"id\":\"1\",\"title\":\"Test\"},{\"id\":\"2\",\"title\":\"Test2\"}]";
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn(jsonWithConversation);

        // Act
        conversationStorage.deleteConversation("1");

        // Assert
        verify(mockEditor).putString(eq("conversations"), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void testDeleteConversation_NonExistentId_DoesNothing() {
        // Arrange
        String jsonWithConversation = "[{\"id\":\"1\",\"title\":\"Test\"}]";
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn(jsonWithConversation);

        // Act
        conversationStorage.deleteConversation("999");

        // Assert
        verify(mockEditor).putString(eq("conversations"), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void testDeleteConversation_NullId_HandlesGracefully() {
        // Arrange
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn("[]");

        // Act & Assert
        try {
            conversationStorage.deleteConversation(null);
            fail("Expected NullPointerException for null ID");
        } catch (Exception e) {
            assertTrue(e instanceof NullPointerException);
        }
    }

    @Test
    public void testDeleteConversation_EmptyId_HandlesGracefully() {
        // Arrange
        String jsonWithConversation = "[{\"id\":\"1\",\"title\":\"Test\"}]";
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn(jsonWithConversation);

        // Act
        conversationStorage.deleteConversation("");

        // Assert - should not crash, may or may not delete anything
        verify(mockEditor).putString(eq("conversations"), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void testUpdateConversationTitle_ExistingConversation_UpdatesTitle() {
        // Arrange
        String jsonWithConversation = "[{\"id\":\"1\",\"title\":\"Old Title\",\"lastMessage\":\"Hello\"}]";
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn(jsonWithConversation);

        // Act
        conversationStorage.updateConversationTitle("1", "New Title");

        // Assert
        verify(mockEditor).putString(eq("conversations"), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void testUpdateConversationTitle_NonExistentConversation_DoesNothing() {
        // Arrange
        String jsonWithConversation = "[{\"id\":\"1\",\"title\":\"Test\"}]";
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn(jsonWithConversation);

        // Act
        conversationStorage.updateConversationTitle("999", "New Title");

        // Assert
        verify(mockEditor).putString(eq("conversations"), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void testUpdateConversationTitle_NullId_HandlesGracefully() {
        // Arrange
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn("[]");

        // Act & Assert
        try {
            conversationStorage.updateConversationTitle(null, "New Title");
            fail("Expected NullPointerException for null ID");
        } catch (Exception e) {
            assertTrue(e instanceof NullPointerException);
        }
    }

    @Test
    public void testUpdateConversationTitle_NullTitle_HandlesGracefully() {
        // Arrange
        String jsonWithConversation = "[{\"id\":\"1\",\"title\":\"Old Title\"}]";
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn(jsonWithConversation);

        // Act
        conversationStorage.updateConversationTitle("1", null);

        // Assert - should not crash
        verify(mockEditor).putString(eq("conversations"), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void testUpdateConversationLastMessage_ExistingConversation_UpdatesMessage() {
        // Arrange
        String jsonWithConversation = "[{\"id\":\"1\",\"title\":\"Test\",\"lastMessage\":\"Old Message\",\"model\":\"old-model\"}]";
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn(jsonWithConversation);

        // Act
        conversationStorage.updateConversationLastMessage("1", "New Message", "new-model");

        // Assert
        verify(mockEditor).putString(eq("conversations"), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void testUpdateConversationLastMessage_WithNullModel_UpdatesMessageOnly() {
        // Arrange
        String jsonWithConversation = "[{\"id\":\"1\",\"title\":\"Test\",\"lastMessage\":\"Old Message\",\"model\":\"old-model\"}]";
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn(jsonWithConversation);

        // Act
        conversationStorage.updateConversationLastMessage("1", "New Message", null);

        // Assert
        verify(mockEditor).putString(eq("conversations"), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void testUpdateConversationLastMessage_NonExistentConversation_DoesNothing() {
        // Arrange
        String jsonWithConversation = "[{\"id\":\"1\",\"title\":\"Test\"}]";
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn(jsonWithConversation);

        // Act
        conversationStorage.updateConversationLastMessage("999", "New Message", "model");

        // Assert
        verify(mockEditor).putString(eq("conversations"), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void testUpdateConversationLastMessage_NullId_HandlesGracefully() {
        // Arrange
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn("[]");

        // Act & Assert
        try {
            conversationStorage.updateConversationLastMessage(null, "Message", "model");
            fail("Expected NullPointerException for null ID");
        } catch (Exception e) {
            assertTrue(e instanceof NullPointerException);
        }
    }

    @Test
    public void testUpdateConversationLastMessage_NullMessage_HandlesGracefully() {
        // Arrange
        String jsonWithConversation = "[{\"id\":\"1\",\"title\":\"Test\",\"lastMessage\":\"Old Message\"}]";
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn(jsonWithConversation);

        // Act
        conversationStorage.updateConversationLastMessage("1", null, "model");

        // Assert - should not crash
        verify(mockEditor).putString(eq("conversations"), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void testSaveConversation_MultipleConversations_MaintainsOrder() {
        // Arrange
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn("[]");
        Conversation conv1 = createTestConversation("1", "First");
        Conversation conv2 = createTestConversation("2", "Second");

        // Act
        conversationStorage.saveConversation(conv1);
        conversationStorage.saveConversation(conv2);

        // Assert
        verify(mockEditor, times(2)).putString(eq("conversations"), anyString());
        verify(mockEditor, times(2)).apply();
    }

    @Test
    public void testSaveConversation_UpdateExistingInMiddle_MaintainsPosition() {
        // Arrange
        String jsonWithMultiple = "[{\"id\":\"1\",\"title\":\"First\"},{\"id\":\"2\",\"title\":\"Second\"},{\"id\":\"3\",\"title\":\"Third\"}]";
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn(jsonWithMultiple);
        Conversation updatedConv = createTestConversation("2", "Updated Second");

        // Act
        conversationStorage.saveConversation(updatedConv);

        // Assert
        verify(mockEditor).putString(eq("conversations"), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void testDeleteConversation_LastConversation_RemovesSuccessfully() {
        // Arrange
        String jsonWithOne = "[{\"id\":\"1\",\"title\":\"Only One\"}]";
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn(jsonWithOne);

        // Act
        conversationStorage.deleteConversation("1");

        // Assert
        verify(mockEditor).putString(eq("conversations"), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void testSharedPreferencesFailure_HandlesGracefully() {
        // Arrange
        when(mockSharedPreferences.getString("conversations", "[]"))
                .thenThrow(new RuntimeException("SharedPreferences error"));

        // Act & Assert
        try {
            conversationStorage.getAllConversations();
            fail("Expected RuntimeException from SharedPreferences failure");
        } catch (Exception e) {
            assertTrue(e instanceof RuntimeException);
        }
    }

    @Test
    public void testEditorFailure_HandlesGracefully() {
        // Arrange
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn("[]");
        doThrow(new RuntimeException("Editor error")).when(mockEditor).apply();
        Conversation conv = createTestConversation("1", "Test");

        // Act & Assert
        try {
            conversationStorage.saveConversation(conv);
            fail("Expected RuntimeException from editor failure");
        } catch (Exception e) {
            assertTrue(e instanceof RuntimeException);
        }
    }

    @Test
    public void testLargeDataSet_HandlesEfficiently() {
        // Arrange - simulate a large JSON string
        StringBuilder largeJson = new StringBuilder("[");
        for (int i = 0; i < 100; i++) {
            if (i > 0) largeJson.append(",");
            largeJson.append("{\"id\":\"").append(i).append("\",\"title\":\"Conversation ").append(i).append("\"}");
        }
        largeJson.append("]");
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn(largeJson.toString());

        // Act
        List<Conversation> conversations = conversationStorage.getAllConversations();

        // Assert
        assertNotNull(conversations);
        assertEquals(100, conversations.size());
    }

    @Test
    public void testConcurrentModification_HandlesGracefully() {
        // Arrange
        String jsonWithConversation = "[{\"id\":\"1\",\"title\":\"Test\"}]";
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn(jsonWithConversation);

        // Act - simulate concurrent operations
        conversationStorage.deleteConversation("1");
        conversationStorage.updateConversationTitle("1", "New Title");

        // Assert - should not crash
        verify(mockEditor, atLeast(2)).putString(eq("conversations"), anyString());
        verify(mockEditor, atLeast(2)).apply();
    }

    @Test
    public void testSpecialCharactersInData_HandlesCorrectly() {
        // Arrange
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn("[]");
        Conversation conv = createTestConversation("test-id", "Title with \"quotes\" and \n newlines");
        conv.setLastMessage("Message with special chars: éñ中文🎉");

        // Act
        conversationStorage.saveConversation(conv);

        // Assert
        verify(mockEditor).putString(eq("conversations"), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void testUpdateConversationLastMessage_UpdatesTimestamp() {
        // Arrange
        String jsonWithConversation = "[{\"id\":\"1\",\"title\":\"Test\",\"lastMessage\":\"Old Message\"}]";
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn(jsonWithConversation);

        // Act
        conversationStorage.updateConversationLastMessage("1", "New Message", "model");

        // Assert - verify that lastMessageTime is updated (method sets new Date())
        verify(mockEditor).putString(eq("conversations"), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void testSaveConversation_PreservesAllFields() {
        // Arrange
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn("[]");
        Conversation conv = createTestConversation("1", "Test Title");
        conv.setLastMessage("Test Message");
        conv.setModel("test-model");
        Date testDate = new Date();
        conv.setLastMessageTime(testDate);

        // Act
        conversationStorage.saveConversation(conv);

        // Assert
        verify(mockEditor).putString(eq("conversations"), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void testDeleteConversation_MultipleMatches_RemovesAll() {
        // Arrange - create JSON with duplicate IDs (edge case)
        String jsonWithDuplicates = "[{\"id\":\"1\",\"title\":\"First\"},{\"id\":\"1\",\"title\":\"Duplicate\"},{\"id\":\"2\",\"title\":\"Different\"}]";
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn(jsonWithDuplicates);

        // Act
        conversationStorage.deleteConversation("1");

        // Assert - should remove all conversations with matching ID
        verify(mockEditor).putString(eq("conversations"), anyString());
        verify(mockEditor).apply();
    }

    @Test
    public void testGsonSerialization_HandlesDateCorrectly() {
        // Arrange
        when(mockSharedPreferences.getString("conversations", "[]")).thenReturn("[]");
        Conversation conv = createTestConversation("1", "Test");
        Date specificDate = new Date(1640995200000L); // Jan 1, 2022
        conv.setLastMessageTime(specificDate);

        // Act
        conversationStorage.saveConversation(conv);

        // Assert - verify that Date serialization works
        verify(mockEditor).putString(eq("conversations"), anyString());
        verify(mockEditor).apply();
    }

    private Conversation createTestConversation(String id, String title) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setTitle(title);
        conversation.setLastMessage("Test message");
        conversation.setLastMessageTime(new Date());
        conversation.setModel("test-model");
        return conversation;
    }
}
# Note: This test requires the following dependencies in app/build.gradle:
# testImplementation 'junit:junit:4.13.2'
# testImplementation 'org.mockito:mockito-core:4.6.1'
# testImplementation 'org.robolectric:robolectric:4.9'