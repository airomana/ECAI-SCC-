package com.eldercare.ai.llm

sealed class LlmException(message: String) : RuntimeException(message)

class LlmAuthException(message: String) : LlmException(message)

class LlmRateLimitException(message: String) : LlmException(message)
