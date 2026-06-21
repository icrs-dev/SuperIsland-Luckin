package com.owls.superisland.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// Models for MCP JSON-RPC
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: Int = 1,
    val method: String = "tools/call",
    val params: com.google.gson.JsonElement? = null
)

data class McpParams(
    val name: String,
    val arguments: Map<String, Any>
)

data class McpResponse(
    val jsonrpc: String,
    val id: Int,
    val result: com.google.gson.JsonElement?,
    val error: McpError?
)

data class McpError(
    val code: Int,
    val message: String
)

interface LuckinMcpApi {
    @POST("order/user/mcp")
    suspend fun callTool(@Body request: McpRequest): McpResponse
}

object McpClient {
    private const val BASE_URL = "https://gwmcp.lkcoffee.com/"
    // The token provided by the user, dynamically set at runtime
    var token: String = ""

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .addHeader("Mcp-Protocol-Version", "2025-06-18")
            .addHeader("Accept", "application/json, text/event-stream")
            .build()
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: LuckinMcpApi = retrofit.create(LuckinMcpApi::class.java)
    
    val gson = Gson()
    
    private fun extractTextFromResult(result: com.google.gson.JsonElement?): String? {
        if (result == null || !result.isJsonObject) return null
        val contentArray = result.asJsonObject.getAsJsonArray("content")
        if (contentArray != null && contentArray.size() > 0) {
            val firstItem = contentArray.get(0).asJsonObject
            if (firstItem.has("text")) {
                return firstItem.get("text").asString
            }
        }
        return null
    }

    suspend fun listTools(): String? {
        val req = McpRequest(
            method = "tools/list"
        )
        val response = api.callTool(req)
        return response.result?.toString()
    }

    suspend fun executeTool(name: String, args: Map<String, Any>): String? {
        val req = McpRequest(
            params = gson.toJsonTree(McpParams(name, args))
        )
        val response = api.callTool(req)
        if (response.error != null) {
            throw Exception("MCP Error: ${response.error.message}")
        }
        return extractTextFromResult(response.result)
    }

    suspend fun queryShopList(lat: Double, lon: Double): String? {
        return executeTool("queryShopList", mapOf(
            "latitude" to lat,
            "longitude" to lon
        ))
    }
    
    suspend fun searchProduct(deptId: Int, query: String): String? {
        return executeTool("searchProduct", mapOf(
            "deptId" to deptId,
            "keyword" to query
        ))
    }
    
    suspend fun previewOrder(deptId: Int, productId: Int, skuCode: String): String? {
        val productList = listOf(
            mapOf("amount" to 1, "productId" to productId, "skuCode" to skuCode)
        )
        return executeTool("previewOrder", mapOf(
            "deptId" to deptId,
            "productList" to productList
        ))
    }
    
    suspend fun createOrder(deptId: Int, productId: Int, skuCode: String, lat: Double, lon: Double): String? {
        val productList = listOf(
            mapOf("amount" to 1, "productId" to productId, "skuCode" to skuCode)
        )
        return executeTool("createOrder", mapOf(
            "deptId" to deptId,
            "productList" to productList,
            "latitude" to lat,
            "longitude" to lon
        ))
    }

    suspend fun queryOrderDetailInfo(orderId: String): String? {
        return executeTool("queryOrderDetailInfo", mapOf(
            "orderId" to orderId
        ))
    }
}
