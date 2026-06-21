package com.owls.superisland

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.owls.superisland.network.*

object LlmAgent {
    val conversationHistory = mutableListOf<Message>()
    private var cachedTools: List<Tool>? = null
    
    // For safety lock on createOrder
    var pendingCreateOrderArgs: Map<String, Any>? = null
    var pendingCreateOrderToolCallId: String? = null

    init {
        conversationHistory.add(Message(
            role = "system",
            content = "You are a highly efficient and geeky terminal assistant for ordering Luckin Coffee. " +
                    "You communicate concisely and clearly. " +
                    "Use the available tools to find nearby shops, search for products, preview orders, and create orders. " +
                    "When the user wants to order, FIRST use previewOrder to get the details. " +
                    "THEN ask the user '订单已准备好，确认支付请回复 PAY'. " +
                    "Wait for the user to explicitly say PAY. Only when the user says PAY, you invoke createOrder."
        ))
    }

    suspend fun loadTools(): List<Tool>? {
        if (cachedTools != null) return cachedTools

        try {
            val toolsStr = McpClient.listTools() ?: return null
            val root = JsonParser().parse(toolsStr).asJsonObject
            val toolsArray = root.getAsJsonArray("tools")
            
            val openAiTools = mutableListOf<Tool>()
            for (i in 0 until toolsArray.size()) {
                val toolObj = toolsArray.get(i).asJsonObject
                val functionDef = FunctionDef(
                    name = toolObj.get("name").asString,
                    description = toolObj.get("description").asString,
                    parameters = toolObj.get("inputSchema")
                )
                openAiTools.add(Tool(function = functionDef))
            }
            cachedTools = openAiTools
            return cachedTools
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    suspend fun processUserInput(input: String, lat: Double, lon: Double, onResponse: (String) -> Unit) {
        val tools = loadTools()
        if (tools == null) {
            onResponse("ERROR: Failed to load MCP tools from Luckin server.")
            return
        }

        // Add user input to history, also inject current location for context
        val userContent = "User Location: lat=$lat, lon=$lon\nUser Input: $input"
        conversationHistory.add(Message(role = "user", content = userContent))

        runExecutionLoop(tools, onResponse)
    }

    suspend fun processPay(lat: Double, lon: Double, onResponse: (String) -> Unit) {
        val args = pendingCreateOrderArgs
        val toolCallId = pendingCreateOrderToolCallId
        if (args != null && toolCallId != null) {
            // Execute the delayed createOrder!
            try {
                // Ensure lat/lon are injected if missing
                val finalArgs = args.toMutableMap()
                if (!finalArgs.containsKey("latitude")) finalArgs["latitude"] = lat
                if (!finalArgs.containsKey("longitude")) finalArgs["longitude"] = lon
                
                onResponse("> [EXECUTING] createOrder...")
                val result = McpClient.executeTool("createOrder", finalArgs) ?: "Success"
                
                // Return to LLM
                conversationHistory.add(Message(
                    role = "tool",
                    tool_call_id = toolCallId,
                    name = "createOrder",
                    content = result
                ))
                
                pendingCreateOrderArgs = null
                pendingCreateOrderToolCallId = null
                
                runExecutionLoop(cachedTools!!, onResponse)
                
            } catch (e: Exception) {
                onResponse("ERROR: ${e.message}")
            }
        } else {
            onResponse("ERROR: No pending order to PAY.")
        }
    }

    private suspend fun runExecutionLoop(tools: List<Tool>, onResponse: (String) -> Unit) {
        var isRunning = true
        val gson = Gson()
        
        while (isRunning) {
            try {
                val responseMsg = LlmClient.chat(conversationHistory, tools)
                if (responseMsg == null) {
                    onResponse("ERROR: LLM API request failed.")
                    return
                }

                // Append assistant's response to history
                conversationHistory.add(responseMsg)

                if (responseMsg.content != null && responseMsg.content.isNotEmpty()) {
                    onResponse(responseMsg.content)
                }

                val toolCalls = responseMsg.tool_calls
                if (toolCalls != null && toolCalls.isNotEmpty()) {
                    // LLM wants to call tools
                    for (toolCall in toolCalls) {
                        val fnName = toolCall.function.name
                        val fnArgsStr = toolCall.function.arguments
                        onResponse("> [CALLING] $fnName($fnArgsStr)")
                        
                        val argsMap: Map<String, Any> = gson.fromJson(fnArgsStr, Map::class.java) as Map<String, Any>

                        // Safety check for createOrder
                        if (fnName == "createOrder") {
                            pendingCreateOrderArgs = argsMap
                            pendingCreateOrderToolCallId = toolCall.id
                            onResponse("🚨 SECURITY LOCK: createOrder requested. Type 'PAY' to authorize real transaction.")
                            return // Exit loop to wait for user PAY command
                        }

                        // Normal tool execution
                        val toolResult = try {
                            McpClient.executeTool(fnName, argsMap) ?: "{\"status\":\"success\"}"
                        } catch (e: Exception) {
                            "{\"status\":\"error\", \"message\":\"${e.message}\"}"
                        }

                        conversationHistory.add(Message(
                            role = "tool",
                            tool_call_id = toolCall.id,
                            name = fnName,
                            content = toolResult
                        ))
                    }
                } else {
                    // No tools called, conversation turn ends
                    isRunning = false
                }

            } catch (e: Exception) {
                onResponse("ERROR: ${e.message}")
                isRunning = false
            }
        }
    }
}
