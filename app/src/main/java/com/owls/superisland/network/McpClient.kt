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
    val params: McpParams
)

data class McpParams(
    val name: String,
    val arguments: Map<String, Any>
)

data class McpResponse(
    val jsonrpc: String,
    val id: Int,
    val result: McpResult?,
    val error: McpError?
)

data class McpResult(
    val content: List<McpContent>?
)

data class McpContent(
    val type: String,
    val text: String?
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
    // The token provided by the user
    private const val TOKEN = "YOUR_LUCKIN_MCP_TOKEN_HERE"

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $TOKEN")
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
    
    suspend fun queryShopList(lat: Double, lon: Double): String? {
        val req = McpRequest(
            params = McpParams(
                name = "queryShopList",
                arguments = mapOf(
                    "latitude" to lat,
                    "longitude" to lon
                )
            )
        )
        val response = api.callTool(req)
        return response.result?.content?.firstOrNull()?.text
    }
    
    suspend fun searchProduct(deptId: Int, query: String): String? {
        val req = McpRequest(
            params = McpParams(
                name = "searchProductForMcp",
                arguments = mapOf(
                    "deptId" to deptId,
                    "query" to query
                )
            )
        )
        val response = api.callTool(req)
        return response.result?.content?.firstOrNull()?.text
    }
    
    suspend fun previewOrder(deptId: Int, productId: Long, skuCode: String): String? {
        val req = McpRequest(
            params = McpParams(
                name = "previewOrder",
                arguments = mapOf(
                    "deptId" to deptId,
                    "productList" to listOf(
                        mapOf(
                            "amount" to 1,
                            "productId" to productId,
                            "skuCode" to skuCode
                        )
                    )
                )
            )
        )
        val response = api.callTool(req)
        return response.result?.content?.firstOrNull()?.text
    }

    suspend fun createOrder(deptId: Int, productId: Long, skuCode: String, lat: Double, lon: Double): String? {
        val req = McpRequest(
            params = McpParams(
                name = "createOrder",
                arguments = mapOf(
                    "deptId" to deptId,
                    "productList" to listOf(
                        mapOf(
                            "amount" to 1,
                            "productId" to productId,
                            "skuCode" to skuCode
                        )
                    ),
                    "latitude" to lat,
                    "longitude" to lon
                )
            )
        )
        val response = api.callTool(req)
        return response.result?.content?.firstOrNull()?.text
    }

    suspend fun queryOrderDetailInfo(orderId: String): String? {
        val req = McpRequest(
            params = McpParams(
                name = "queryOrderDetailInfo",
                arguments = mapOf(
                    "orderId" to orderId
                )
            )
        )
        val response = api.callTool(req)
        return response.result?.content?.firstOrNull()?.text
    }
}
