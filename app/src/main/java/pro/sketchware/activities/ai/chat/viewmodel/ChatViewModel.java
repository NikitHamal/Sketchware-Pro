package pro.sketchware.activities.ai.chat.viewmodel;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import pro.sketchware.activities.ai.chat.api.AgenticQwenApiClient;
import pro.sketchware.activities.ai.chat.models.ChatMessage;
import pro.sketchware.activities.ai.storage.ConversationStorage;
import pro.sketchware.activities.ai.storage.MessageStorage;

public class ChatViewModel extends AndroidViewModel {

    private final MessageStorage messageStorage;
    private final ConversationStorage conversationStorage;
    private final AgenticQwenApiClient apiClient;

    private final MutableLiveData<List<ChatMessage>> messages = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isTyping = new MutableLiveData<>(false);

    private String conversationId;

    public ChatViewModel(@NonNull Application application) {
        super(application);
        messageStorage = new MessageStorage(application);
        conversationStorage = new ConversationStorage(application);
        apiClient = new AgenticQwenApiClient(application);
    }

    public void init(String conversationId) {
        if (this.conversationId != null) {
            return;
        }
        this.conversationId = conversationId;
        loadMessages();
    }

    public LiveData<List<ChatMessage>> getMessages() {
        return messages;
    }

    public LiveData<Boolean> isTyping() {
        return isTyping;
    }

    private void loadMessages() {
        List<ChatMessage> messageList = messageStorage.getMessages(conversationId);
        messages.setValue(messageList);
    }

    public void sendMessage(String messageText, String selectedModel, boolean thinkingEnabled, boolean webSearchEnabled, List<ChatMessage.AttachedFile> pendingFiles) {
        ChatMessage userMessage = new ChatMessage(
                UUID.randomUUID().toString(),
                messageText,
                ChatMessage.TYPE_USER,
                System.currentTimeMillis()
        );

        if (pendingFiles != null && !pendingFiles.isEmpty()) {
            userMessage.setAttachedFiles(new ArrayList<>(pendingFiles));
        }

        addMessage(userMessage);
        isTyping.setValue(true);

        apiClient.sendMessage(conversationId, selectedModel, messageText, pendingFiles,
                thinkingEnabled, webSearchEnabled, new AgenticQwenApiClient.AgenticChatCallback() {
                    @Override
                    public void onResponse(String response) {
                        isTyping.postValue(false);
                        try {
                            org.json.JSONObject responseJson = new org.json.JSONObject(response);
                            String content = responseJson.optString("content", response);
                            ChatMessage aiMessage = new ChatMessage(
                                    UUID.randomUUID().toString(),
                                    content,
                                    ChatMessage.TYPE_AI,
                                    System.currentTimeMillis()
                            );
                            if (responseJson.has("thinking_content")) {
                                aiMessage.setThinkingContent(responseJson.getString("thinking_content"));
                            }
                            if (responseJson.has("web_search_sources")) {
                                aiMessage.setWebSearchSources(responseJson.getJSONArray("web_search_sources").toString());
                            }
                            addMessage(aiMessage);
                        } catch (org.json.JSONException e) {
                            ChatMessage aiMessage = new ChatMessage(
                                    UUID.randomUUID().toString(),
                                    response,
                                    ChatMessage.TYPE_AI,
                                    System.currentTimeMillis()
                            );
                            addMessage(aiMessage);
                        }
                    }

                    @Override
                    public void onError(String error) {
                        isTyping.postValue(false);
                        ChatMessage errorMessage = new ChatMessage(
                                UUID.randomUUID().toString(),
                                "Error: " + error,
                                ChatMessage.TYPE_AI,
                                System.currentTimeMillis()
                        );
                        addMessage(errorMessage);
                    }

                    @Override
                    public void onActionExecuted(String actionResult, String projectId) {
                        isTyping.postValue(false);
                        ChatMessage actionMessage = new ChatMessage(
                                UUID.randomUUID().toString(),
                                "Action executed: " + actionResult,
                                ChatMessage.TYPE_AI,
                                System.currentTimeMillis()
                        );
                        addMessage(actionMessage);
                    }

                    @Override
                    public void onProjectCreated(String projectId, String projectName) {
                        isTyping.postValue(false);
                        ChatMessage projectMessage = new ChatMessage(
                                UUID.randomUUID().toString(),
                                "Project created: " + projectName,
                                ChatMessage.TYPE_AI,
                                System.currentTimeMillis()
                        );
                        addMessage(projectMessage);
                    }

                    @Override
                    public void onFixProposal(String explanation, String actionJson, String projectId) {
                        isTyping.postValue(false);
                        ChatMessage proposalMessage = new ChatMessage(
                                UUID.randomUUID().toString(),
                                explanation,
                                ChatMessage.TYPE_PROPOSAL,
                                System.currentTimeMillis()
                        );
                        proposalMessage.setProposalData(actionJson);
                        addMessage(proposalMessage);
                    }
                });
    }

    private void addMessage(ChatMessage message) {
        List<ChatMessage> currentMessages = messages.getValue();
        if (currentMessages == null) {
            currentMessages = new ArrayList<>();
        }
        currentMessages.add(message);
        messages.setValue(currentMessages);
        messageStorage.addMessage(conversationId, message);
    }
}
