package com.owls.superisland

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.owls.superisland.network.McpClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// --- Theme ---
val BrutalistBlack = Color(0xFF121212)
val BrutalistWhite = Color(0xFFF5F5F5)
val BrutalistGray = Color(0xFF888888)

// --- Models ---
data class ChatMessage(val text: String, val isUser: Boolean)

data class SessionProduct(val productId: Long, val skuCode: String, val name: String)

object OrderSession {
    var deptId: Int? = null
    var productId: Long? = null
    var skuCode: String? = null
    var productName: String? = null
    var orderId: String? = null
    var lat: Double? = null
    var lon: Double? = null
    val shops = mutableListOf<Int>()
    val products = mutableListOf<SessionProduct>()
}

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Permission launcher for location
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // We handle the result implicitly when the user clicks the button again
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Request notification permission for HyperOS Island
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            BrutalistApp(
                activity = this,
                fusedLocationClient = fusedLocationClient,
                requestLocationPerm = {
                    locationPermissionRequest.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                }
            )
        }
    }
}

@Composable
fun BrutalistApp(
    activity: ComponentActivity,
    fusedLocationClient: FusedLocationProviderClient,
    requestLocationPerm: () -> Unit
) {
    var messages by remember { mutableStateOf(listOf(
        ChatMessage("LUCKIN AGENT ONLINE.", false),
        ChatMessage("AWAITING COMMAND...", false)
    )) }
    var inputText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val sharedPrefs = context.getSharedPreferences("superisland_prefs", Context.MODE_PRIVATE)
        val savedToken = sharedPrefs.getString("mcp_token", "") ?: ""
        if (savedToken.isNotEmpty()) {
            McpClient.token = savedToken
        } else {
            messages = listOf(
                ChatMessage("LUCKIN AGENT ONLINE.", false),
                ChatMessage("NO LUCKIN TOKEN FOUND.", false),
                ChatMessage("PLEASE SET IT USING: 设置TOKEN [你的Token]", false)
            )
        }

        // Initialize LLM configs
        com.owls.superisland.network.LlmClient.apiKey = sharedPrefs.getString("llm_key", "") ?: ""
        val savedEndpoint = sharedPrefs.getString("llm_endpoint", "") ?: ""
        if (savedEndpoint.isNotEmpty()) com.owls.superisland.network.LlmClient.endpoint = savedEndpoint
        val savedModel = sharedPrefs.getString("llm_model", "") ?: ""
        if (savedModel.isNotEmpty()) com.owls.superisland.network.LlmClient.model = savedModel
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrutalistBlack)
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "SUPER ISLAND TERMINAL",
            color = BrutalistWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Divider(color = BrutalistWhite, thickness = 2.dp)

        // Chat Area
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { msg ->
                val alignment = if (msg.isUser) Alignment.End else Alignment.Start
                val bgColor = if (msg.isUser) BrutalistWhite else Color.Transparent
                val textColor = if (msg.isUser) BrutalistBlack else BrutalistWhite

                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
                    Box(
                        modifier = Modifier
                            .background(bgColor, RoundedCornerShape(4.dp))
                            .border(if (!msg.isUser) 1.dp else 0.dp, BrutalistWhite, RoundedCornerShape(4.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = msg.text,
                            color = textColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Input Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, BrutalistWhite)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("> ", color = BrutalistWhite, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = BrutalistWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp
                ),
                cursorBrush = SolidColor(BrutalistWhite),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank()) {
                            val userText = inputText
                            messages = messages + ChatMessage(userText, true)
                            inputText = ""
                            
                            // Process Command
                            coroutineScope.launch {
                                processCommand(
                                    cmd = userText, 
                                    context = context, 
                                    fusedLocationClient = fusedLocationClient,
                                    requestLocationPerm = requestLocationPerm
                                ) { response ->
                                    messages = messages + ChatMessage(response, false)
                                }
                            }
                        }
                    }
                )
            )
        }
    }
}

@SuppressLint("MissingPermission")
suspend fun processCommand(
    cmd: String, 
    context: Context, 
    fusedLocationClient: FusedLocationProviderClient,
    requestLocationPerm: () -> Unit,
    onResponse: (String) -> Unit
) {
    try {
        if (cmd.startsWith("设置TOKEN ")) {
            val token = cmd.substring("设置TOKEN ".length).trim()
            if (token.isNotEmpty()) {
                val sharedPrefs = context.getSharedPreferences("superisland_prefs", Context.MODE_PRIVATE)
                sharedPrefs.edit().putString("mcp_token", token).apply()
                McpClient.token = token
                onResponse("TOKEN SAVED SUCCESSFULLY.")
            } else {
                onResponse("INVALID TOKEN FORMAT.")
            }
            return
        }
        
        if (McpClient.token.isEmpty()) {
            onResponse("ERROR: NO TOKEN. PLEASE SET IT USING: 设置TOKEN [你的Token]")
            return
        }

        if (cmd.startsWith("设置LLM_KEY ")) {
            val key = cmd.substring("设置LLM_KEY ".length).trim()
            context.getSharedPreferences("superisland_prefs", android.content.Context.MODE_PRIVATE).edit().putString("llm_key", key).apply()
            com.owls.superisland.network.LlmClient.apiKey = key
            onResponse("LLM KEY SAVED.")
            return
        }
        if (cmd.startsWith("设置ENDPOINT ")) {
            val url = cmd.substring("设置ENDPOINT ".length).trim()
            context.getSharedPreferences("superisland_prefs", android.content.Context.MODE_PRIVATE).edit().putString("llm_endpoint", url).apply()
            com.owls.superisland.network.LlmClient.endpoint = url
            onResponse("LLM ENDPOINT SAVED.")
            return
        }
        if (cmd.startsWith("设置MODEL ")) {
            val model = cmd.substring("设置MODEL ".length).trim()
            context.getSharedPreferences("superisland_prefs", android.content.Context.MODE_PRIVATE).edit().putString("llm_model", model).apply()
            com.owls.superisland.network.LlmClient.model = model
            onResponse("LLM MODEL SAVED.")
            return
        }

        if (cmd == "PAY") {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                onResponse("MISSING LOCATION PERMISSION.")
                return
            }
            onResponse("ACQUIRING GPS SIGNAL...")
            val location = fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null).await()
            val lat = location?.latitude ?: 0.0
            val lon = location?.longitude ?: 0.0
            
            LlmAgent.processPay(lat, lon) { resultStr ->
                onResponse(resultStr)
                // If it looks like JSON with orderId, start polling
                val match = Regex("\"orderId\"\\s*:\\s*\"([a-zA-Z0-9]+)\"").find(resultStr)
                if (match != null) {
                    OrderSession.orderId = match.groupValues[1]
                    onResponse("ORDER SUCCESS! ID: ${OrderSession.orderId}\nSTARTING POLLING...")
                    startPolling(context)
                }
            }
            return
        }

        if (cmd == "测" || cmd == "岛") {
            onResponse("EXECUTING ISLAND TEST...")
            HyperOSIslandHelper.updateOrderNotification(context, "测试中", "A000")
            onResponse("TEST ONGOING NOTIFICATION SENT.")
            return
        }

        // Pass natural language to LLM
        if (com.owls.superisland.network.LlmClient.apiKey.isEmpty()) {
            onResponse("ERROR: NO LLM KEY. PLEASE SET IT USING: 设置LLM_KEY [KEY]")
            return
        }
        
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            onResponse("MISSING LOCATION PERMISSION. REQUESTING...")
            requestLocationPerm()
            return
        }
        onResponse("ACQUIRING GPS SIGNAL...")
        val location = fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null).await()
        val lat = location?.latitude ?: 0.0
        val lon = location?.longitude ?: 0.0

        onResponse("THINKING...")
        LlmAgent.processUserInput(cmd, lat, lon, onResponse)
    } catch (e: Exception) {
        onResponse("ERROR: ${e.message}")
    }
}

fun startPolling(context: Context) {
    kotlinx.coroutines.GlobalScope.launch {
        while (OrderSession.orderId != null) {
            try {
                val res = McpClient.queryOrderDetailInfo(OrderSession.orderId!!)
                if (res != null) {
                    val statusMatch = Regex("\"status\"\\s*:\\s*\"([^\"]+)\"").find(res)
                    val pickupMatch = Regex("\"pickupCode\"\\s*:\\s*\"([^\"]+)\"").find(res)
                    val status = statusMatch?.groupValues?.get(1) ?: "未知状态"
                    val code = pickupMatch?.groupValues?.get(1) ?: "N/A"
                    
                    HyperOSIslandHelper.updateOrderNotification(context, status, code)
                }
            } catch (e: Exception) {
                // Ignore network errors in polling
            }
            kotlinx.coroutines.delay(10000) // Poll every 10 seconds
        }
    }
}
