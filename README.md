# German Learning App

A mobile application to help you learn the German language through flashcards, dialogue practice, and AI assistance.

## Features

- **Flashcard System**: Create, study, and master German vocabulary with a spaced repetition system
- **AI Conversation Practice**: Practice German conversations with AI language models via OpenRouter integration
- **Dialogue Learning**: Create, edit, and study real-world German conversations
- **Text-to-Speech**: Hear correct German pronunciation for better learning
- **Scheduled Learning**: Customizable pop-up notifications to remind you to practice throughout the day
- **Google Drive Sync**: Back up and synchronize your learning progress across devices
- **Offline Mode**: Study your flashcards even without internet connection
- **Test Modes**: Challenge yourself with different testing methods to reinforce learning
- **Learning Statistics**: Track your progress with detailed statistics

## App Structure

The app is organized into multiple sections:

- **Flashcards**: Create, edit, and study German vocabulary organized by categories
- **Dialogues**: Create, edit, and study real-world German conversations
- **AI Chat**: Get help with translations, grammar explanations, and conversation practice
- **Study Sessions**: Track your learning progress and set goals
- **Settings**: Customize app behavior including notifications, text-to-speech, and backup

## AI Functionality

The app features several AI-powered learning modes through integration with large language models via OpenRouter:

### Chat Modes
- **Cards-Based Chat**: Practice with AI using your own flashcard vocabulary
- **Dialog-Based Chat**: Practice conversations based on your saved dialogue scenarios
- **Cards & Dialog-Based Chat**: Combined mode using both your vocabulary and dialogues
- **Story Chat**: AI creates stories using your vocabulary to help with context learning
- **General Chat**: Free-form conversation with the AI for general language practice

### AI Features
- **Context-Aware Learning**: The AI remembers your conversation context
- **Pronunciation Support**: Integrated text-to-speech for hearing correct pronunciation
- **Voice Customization**: Adjust speech rate, pitch, and select different German voices
- **Save Conversations**: Save and reload helpful AI conversations for later reference
- **Add to Flashcards**: Directly create new vocabulary cards from your AI conversations
- **Multiple AI Models**: Support for various AI models (Gemini, Claude, GPT-4, Llama, etc.)
- **Token Management**: Automatic context management to handle long conversations
- **Temperature Control**: Adjust AI creativity for different learning scenarios

## Setup

1. Clone the repository
2. Copy `gradle.properties.template` to `gradle.properties`
3. Set your Google OAuth client ID in the `GOOGLE_CLIENT_ID` field in `gradle.properties`
4. Open the project in Android Studio
5. Run the app on your device or emulator

## Required API Keys

To use this app with all features, you'll need:

1. A Google OAuth client ID for Google Drive integration
   - Get it from the [Google Cloud Console](https://console.cloud.google.com/)
   - Enable the Google Drive API

2. For AI features, you'll need an OpenRouter API key
   - This can be added directly in the app settings
   - Get it from [OpenRouter](https://openrouter.ai/)

## Development

The app is built with:

- **Kotlin**: Modern, concise programming language for Android
- **Jetpack Compose**: Declarative UI toolkit for building native Android UI
- **Room Database**: SQLite object mapping library for local data persistence
- **Hilt**: Dependency injection for clean architecture
- **Retrofit**: Type-safe HTTP client for API communication
- **DataStore**: Data storage solution for app preferences
- **Media3**: For audio playback functionality
- **Coroutines**: For asynchronous programming
- **Google Drive API**: For cloud backup and sync

## Security Notes

This project has been configured to keep sensitive information out of source control:
- API keys are stored in `gradle.properties` (not committed to git)
- A template file is provided for developers to set up their own keys
- No hardcoded credentials in the codebase

## License

MIT License

Copyright (c) 2025 parwarkucher

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE. 