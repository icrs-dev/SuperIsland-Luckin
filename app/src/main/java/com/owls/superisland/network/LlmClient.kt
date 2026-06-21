package com.owls.superisland.network

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

// --- Models for OpenAI Chat Completions ---

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val tools: List<Tool>? = null,
    val tool_choice: String? = null // "auto" or "none"
)

data class Message(
    val role: String, // "system", "user", "assistant", "tool"
    val content: String? = null,
    val tool_calls: List<ToolCall>? = null,
    val tool_call_id: String? = null,
    val name: String? = null
)

data class Tool(
    val type: String = "function",
    val function: FunctionDef
)

data class FunctionDef(
    val name: String,
    val description: String,
    val parameters: JsonElement // The schema from MCP tools/list
)

data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunction
)

data class ToolCallFunction(
    val name: String,
    val arguments: String // JSON string
)

data class ChatResponse(
    val id: String?,
    val choices: List<Choice>?
)

data class Choice(
    val index: Int,
    val message: Message,
    val finish_reason: String?
)

interface LlmApi {
    @POST
    suspend fun chatCompletions(
        @Url url: String,
        @Body request: ChatRequest
    ): ChatResponse
}

object LlmClient {
    var apiKey: String = ""
    var endpoint: String = "https://api.deepseek.com/v1/chat/completions"
    var model: String = "deepseek-chat"

    val gson = GsonBuilder().create()

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // We use a generic Retrofit builder but we override the URL in the @POST method
    // so we can dynamically change the endpoint.
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.openai.com/") // Placeholder, actual URL is passed in method
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val api: LlmApi = retrofit.create(LlmApi::class.java)

    suspend fun chat(messages: List<Message>, tools: List<Tool>? = null): Message? {
        val req = ChatRequest(
            model = model,
            messages = messages,
            tools = tools,
            tool_choice = if (tools != null && tools.isNotEmpty()) "auto" else null
        )
        return try {
            val res = api.chatCompletions(endpoint, req)
            res.choices?.firstOrNull()?.message
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
