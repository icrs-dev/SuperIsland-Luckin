import re

with open('app/src/main/java/com/owls/superisland/MainActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

pattern = re.compile(r'        if \(cmd\.contains\("门店"\) \|\| cmd\.contains\("查"\)\) \{.*?\} else \{\n            onResponse\("UNKNOWN COMMAND.*?\}\n', re.DOTALL)

replacement = '''        if (cmd.startsWith("设置LLM_KEY ")) {
            val key = cmd.substring("设置LLM_KEY ".length).trim()
            context.getSharedPreferences("superisland_prefs", android.content.Context.MODE_PRIVATE).edit().putString("llm_key", key).apply()
            com.owls.superisland.network.LlmClient.apiKey = key
            onResponse("LLM KEY SAVED.")
            return
        }
        if (cmd.startsWith("设置LLM_URL ")) {
            val url = cmd.substring("设置LLM_URL ".length).trim()
            context.getSharedPreferences("superisland_prefs", android.content.Context.MODE_PRIVATE).edit().putString("llm_url", url).apply()
            com.owls.superisland.network.LlmClient.baseUrl = url
            onResponse("LLM URL SAVED.")
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
                val match = Regex("\\"orderId\\"\\\\s*:\\\\s*\\"([a-zA-Z0-9]+)\\"").find(resultStr)
                if (match != null) {
                    OrderSession.orderId = match.groupValues[1]
                    onResponse("ORDER SUCCESS! ID: \\\nSTARTING POLLING...")
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
'''

new_content = pattern.sub(replacement, content)

with open('app/src/main/java/com/owls/superisland/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(new_content)
