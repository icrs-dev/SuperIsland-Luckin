# SuperIsland - Luckin MCP Edition ☕️🏝️

A Brutalist, terminal-style Android application that integrates deeply with Xiaomi's HyperOS Dynamic Island to place real Luckin Coffee orders using reverse-engineered MCP APIs.

## 🚀 Features

- **Brutalist Terminal UI**: Forget cluttered menus. Order your coffee entirely through a raw, high-contrast, text-based terminal interface.
- **HyperOS Dynamic Island Integration**: Once your order is placed, your coffee's real-time status (Preparing, Pickup Code) lives natively inside the Xiaomi HyperOS Dynamic Island.
- **Luckin MCP WAF Bypass**: Deeply integrates with the gwmcp.lkcoffee.com gateway. Bypasses the strict Zuul WAF filtering by injecting custom Mcp-Protocol-Version: 2025-06-18 and Accept: application/json, text/event-stream headers.
- **Stateful Ordering Session**: Seamlessly query shops and search for products using intuitive numbered list selections (e.g., 选 1, 点 2) without needing to copy-paste long Product IDs or SKU Codes.

## 🛠️ Usage

Simply type commands into the terminal to interact with the world:

1. 查门店 — Uses high-accuracy GPS to find nearby Luckin stores.
2. 选 [序号] — Locks in the store you want to order from.
3. 搜 [商品名] — Searches for your favorite coffee (e.g., 搜 拿铁).
4. 点 [序号] / 买 [序号] — Prepares the order and fetches the preview.
5. PAY — Confirms the order and triggers the real transaction.
6. 测 / 岛 — Triggers a test HyperOS Dynamic Island notification.

## 🧠 Technical Details

This project is a masterclass in Protocol Reverse Engineering and Android UI/UX mapping:
- Custom binary traffic interception to reverse engineer the Luckin Golang CLI.
- OkHttp Interceptors to spoof internal luckin-cli requests.
- Jetpack Compose for rendering a 60fps Brutalist Terminal.

## 🔧 Installation

`ash
git clone https://github.com/icrs-dev/SuperIsland-Luckin.git
cd SuperIsland-Luckin
./gradlew installDebug
`

## ⚠️ Disclaimer
This is an educational project exploring API integration, Android Compose, and HyperOS features. Please use responsibly. Real charges will apply when typing PAY.
