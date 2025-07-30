# StormX - AI Chat App

A simple and clean Android chat application that allows users to have conversations with AI using the Qwen API.

## Features

- **Clean Chat Interface**: Simple and intuitive chat UI with user and AI message bubbles
- **Conversation Management**: Create, rename, and delete conversations
- **Message History**: All conversations and messages are saved locally
- **File Attachments**: Support for file attachments (basic implementation)
- **Material Design**: Modern Material Design 3 UI components

## Architecture

The app follows a simple architecture with the following components:

### Activities
- `MainActivity`: Main activity that hosts the conversation list
- `ChatActivity`: Chat interface for individual conversations

### Fragments
- `ConversationListFragment`: Displays list of conversations

### Models
- `Conversation`: Represents a conversation with metadata
- `ChatMessage`: Represents individual chat messages

### Storage
- `ConversationStorage`: Manages conversation data using SharedPreferences
- `MessageStorage`: Manages message data using SharedPreferences

### API
- `QwenApiClient`: Handles communication with the Qwen AI API
- `ApiConfig`: Manages API configuration and authentication

### Adapters
- `ConversationAdapter`: RecyclerView adapter for conversation list
- `ChatAdapter`: RecyclerView adapter for chat messages

## Setup

1. Clone the repository
2. Open the project in Android Studio
3. Build and run the app

## Configuration

The app uses the Qwen API for AI chat functionality. API configuration is managed through the `ApiConfig` class and stored in SharedPreferences.

## Dependencies

- AndroidX AppCompat
- Material Design Components
- RecyclerView
- Gson for JSON serialization
- OkHttp for network requests
- Markwon for text rendering

## Package Structure

```
com.stormx.agent/
├── activities/
│   ├── MainActivity.java
│   └── ChatActivity.java
├── fragments/
│   └── ConversationListFragment.java
├── models/
│   ├── Conversation.java
│   └── ChatMessage.java
├── storage/
│   ├── ConversationStorage.java
│   └── MessageStorage.java
├── api/
│   └── QwenApiClient.java
├── config/
│   └── ApiConfig.java
├── adapters/
│   ├── ConversationAdapter.java
│   └── ChatAdapter.java
└── StormXApplication.java
```

## License

This project is a simplified version of a chat application, created for educational and demonstration purposes.
