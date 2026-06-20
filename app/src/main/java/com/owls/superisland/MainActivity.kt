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
        if (cmd.contains("门店") || cmd.contains("查")) {
            // Check location permissions
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                onResponse("MISSING LOCATION PERMISSION. REQUESTING...")
                requestLocationPerm()
                return
            }
            
            onResponse("ACQUIRING GPS SIGNAL...")
            // Fetch real location
            val location = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            if (location == null) {
                onResponse("FAILED TO GET LOCATION. GPS MIGHT BE OFF.")
                return
            }
            
            onResponse("LOCATION: ${location.latitude}, ${location.longitude}. EXECUTING QUERY...")
            val response = McpClient.queryShopList(location.latitude, location.longitude)
            if (response == null) {
                onResponse("NO DATA RETURNED FROM MCP.")
                return
            }
            try {
                // MCP returns a JSON string inside the text block, so we parse it
                val jsonObj = org.json.JSONObject(response)
                // The actual payload might be wrapped in another JSON string or directly available
                // In my test it was directly available as {"code":0,"msg":"success","data":[...]}
                val dataObj = if (jsonObj.has("data") && jsonObj.optJSONArray("data") == null) {
                     // Sometimes it might be double stringified
                     org.json.JSONObject(jsonObj.getString("data"))
                } else jsonObj

                val dataArray = dataObj.optJSONArray("data")
                if (dataArray != null && dataArray.length() > 0) {
                    val sb = java.lang.StringBuilder()
                    sb.append("FOUND ${dataArray.length()} STORES:\n")
                    OrderSession.shops.clear()
                    OrderSession.lat = location.latitude
                    OrderSession.lon = location.longitude
                    
                    val maxItems = kotlin.math.min(5, dataArray.length())
                    for (i in 0 until maxItems) {
                        val shop = dataArray.getJSONObject(i)
                        val deptId = shop.optInt("deptId")
                        val deptName = shop.optString("deptName")
                        val distance = shop.optDouble("distance")
                        OrderSession.shops.add(deptId)
                        sb.append("[${i + 1}] $deptName (${distance}km)\n")
                    }
                    sb.append("\nRUN '选 [1-$maxItems]' TO SELECT STORE.")
                    onResponse(sb.toString())
                } else {
                    onResponse("NO STORES FOUND OR BAD FORMAT.\nRAW: $response")
                }
            } catch (e: Exception) {
                onResponse("FAILED TO PARSE STORES JSON: ${e.message}\nRAW: $response")
            }
            
        } else if (cmd.startsWith("选 ")) {
            val parts = cmd.split(" ")
            if (parts.size >= 2) {
                val index = parts[1].toIntOrNull()
                if (index != null && index >= 1 && index <= OrderSession.shops.size) {
                    OrderSession.deptId = OrderSession.shops[index - 1]
                    onResponse("SAVED DEPT_ID: ${OrderSession.deptId}")
                } else {
                    onResponse("INVALID SHOP NUMBER. PLEASE CHOOSE BETWEEN 1 AND ${OrderSession.shops.size}.")
                }
            }
        } else if (cmd.contains("测") || cmd.contains("岛")) {
            onResponse("EXECUTING ISLAND TEST...")
            HyperOSIslandHelper.updateOrderNotification(context, "测试中", "A000")
            onResponse("TEST ONGOING NOTIFICATION SENT.")
        } else if (cmd.contains("搜") || cmd.contains("拿铁") || cmd.contains("美式")) {
            if (OrderSession.deptId == null) {
                onResponse("ERROR: MISSING DEPT_ID. PLEASE RUN '查门店' THEN '选 [号]' FIRST.")
                return
            }
            onResponse("EXECUTING: $cmd...")
            val query = if (cmd.startsWith("搜")) cmd.substring(1).trim() else cmd.trim()
            val response = McpClient.searchProduct(OrderSession.deptId!!, query)
            if (response == null) {
                onResponse("NO DATA RETURNED FROM MCP.")
                return
            }
            
            try {
                val jsonObj = org.json.JSONObject(response)
                val dataObj = if (jsonObj.has("data") && jsonObj.optJSONArray("data") == null) {
                     org.json.JSONObject(jsonObj.getString("data"))
                } else jsonObj
                
                val dataArray = dataObj.optJSONArray("data")
                if (dataArray != null && dataArray.length() > 0) {
                    val sb = java.lang.StringBuilder()
                    sb.append("FOUND PRODUCTS:\n")
                    OrderSession.products.clear()
                    val maxItems = kotlin.math.min(5, dataArray.length())
                    for (i in 0 until maxItems) {
                        val prod = dataArray.getJSONObject(i)
                        val name = prod.optString("name")
                        val productId = prod.optLong("productId")
                        val skuCode = prod.optString("skuCode")
                        OrderSession.products.add(SessionProduct(productId, skuCode, name))
                        sb.append("[${i + 1}] $name\n")
                    }
                    sb.append("\nRUN: 点 [1-$maxItems]")
                    onResponse(sb.toString())
                } else {
                    onResponse("NO PRODUCTS FOUND.\nRAW: $response")
                }
            } catch (e: Exception) {
                onResponse("FAILED TO PARSE PRODUCTS JSON: ${e.message}\nRAW: $response")
            }
        } else if (cmd.startsWith("点 ") || cmd.startsWith("买 ")) {
            val parts = cmd.split(" ")
            if (parts.size >= 2) {
                val index = parts[1].toIntOrNull()
                if (index != null && index >= 1 && index <= OrderSession.products.size) {
                    val prod = OrderSession.products[index - 1]
                    OrderSession.productId = prod.productId
                    OrderSession.skuCode = prod.skuCode
                    
                    if (OrderSession.deptId == null) {
                        onResponse("ERROR: MISSING DEPT ID. RUN '查门店' FIRST.")
                        return
                    }
                    
                    onResponse("PREVIEWING ORDER FOR [${prod.name}]...")
                    val response = McpClient.previewOrder(OrderSession.deptId!!, OrderSession.productId!!, OrderSession.skuCode!!)
                    onResponse(response ?: "FAILED TO PREVIEW ORDER.")
                    onResponse("ORDER PREPARED. TYPE 'PAY' TO CONFIRM AND PAY (WARNING: REAL CHARGE).")
                } else {
                    onResponse("INVALID PRODUCT NUMBER. PLEASE CHOOSE BETWEEN 1 AND ${OrderSession.products.size}.")
                }
            } else {
                onResponse("INVALID FORMAT. TRY: 点 1")
            }
        } else if (cmd.startsWith("下单")) {
            val parts = cmd.split(" ")
            if (parts.size >= 3) {
                OrderSession.productId = parts[1].toLongOrNull()
                OrderSession.skuCode = parts[2]
                
                if (OrderSession.deptId == null) {
                    onResponse("ERROR: MISSING DEPT ID. RUN '查门店' FIRST.")
                    return
                }
                
                onResponse("PREVIEWING ORDER...")
                val response = McpClient.previewOrder(OrderSession.deptId!!, OrderSession.productId!!, OrderSession.skuCode!!)
                onResponse(response ?: "FAILED TO PREVIEW ORDER.")
                onResponse("ORDER PREPARED. TYPE 'PAY' TO CONFIRM AND PAY (WARNING: REAL CHARGE).")
            } else {
                onResponse("INVALID FORMAT. TRY: 下单 [productId] [skuCode]")
            }
        } else if (cmd == "PAY") {
            if (OrderSession.deptId == null || OrderSession.productId == null || OrderSession.skuCode == null || OrderSession.lat == null) {
                onResponse("ERROR: NO ORDER TO PAY FOR.")
                return
            }
            onResponse("CREATING REAL ORDER...")
            val response = McpClient.createOrder(OrderSession.deptId!!, OrderSession.productId!!, OrderSession.skuCode!!, OrderSession.lat!!, OrderSession.lon!!)
            onResponse(response ?: "FAILED TO CREATE ORDER.")
            
            // Extract orderId from response JSON
            val match = Regex("\"orderId\"\\s*:\\s*\"([a-zA-Z0-9]+)\"").find(response ?: "")
            if (match != null) {
                OrderSession.orderId = match.groupValues[1]
                onResponse("ORDER SUCCESS! ID: ${OrderSession.orderId}")
                onResponse("STARTING POLLING FOR STATUS UPDATES...")
                startPolling(context)
            } else {
                onResponse("ORDER CREATED BUT FAILED TO EXTRACT ID. CANNOT POLL.")
            }
        } else {
            onResponse("UNKNOWN COMMAND. TRY: '查门店', '搜生椰拿铁', '下单 [id] [sku]', 'PAY'")
        }
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
