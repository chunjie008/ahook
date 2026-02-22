# AHOOK - Encryption Operation Monitoring Xposed Module

## Introduction
AHOOK is an advanced encryption operation monitoring module developed based on the Xposed framework, specifically designed to capture encryption activities within Android applications. This module can real-time monitor and record common encryption operations, including hashing (MessageDigest), message authentication code (Mac/HMAC), and encryption/decryption (Cipher), completely saving input data, output results, keys, IV (Initialization Vector), and call stack information. It provides comprehensive encryption behavior analysis capabilities for developers and security researchers.

## Features
*   **Comprehensive Encryption Algorithm Coverage**: Supports monitoring hash algorithms like MD5, SHA-1, SHA-256, HMAC algorithms, and encryption/decryption operations like AES, DES, RSA
*   **Detailed Data Recording**: Captures complete parameters including input data, output results, keys, IV, algorithm modes, etc.
*   **Multi-format Output Support**: Data is displayed in UTF-8 string, Hex, and Base64 formats for easy analysis
*   **Complete Call Chain Tracking**: Records full call stack information for precise code location
*   **Real-time Data Storage**: All logs are automatically saved to local SQLite database for随时查询
*   **High Performance Design**: Uses WeakHashMap to optimize memory management, minimizing impact on target application performance
*   **Plug and Play**: No device restart required after module activation, monitoring starts immediately after target app installation

## Application Scenarios
*   **Security Audit**: Analyze whether an application's encryption implementation has security vulnerabilities
*   **Reverse Engineering**: Study application's encryption protocols and data transmission methods
*   **Penetration Testing**: Discover improper use of encryption algorithms or weak encryption implementations
*   **Compliance Check**: Verify whether applications comply with data protection and encryption standards
*   **Development Debugging**: Assist developers in verifying the correctness of encryption logic

## Prerequisites
*   Rooted Android device (API level 21+)
*   Installed Xposed framework or its compatible implementations (such as LSPosed, EdXposed, etc.)
*   Target application must be running normally (no special permissions required)

## Installation Steps
1.  **Download Module**: Download the latest version of `AHOOK.apk` from the release page
2.  **Install Application**: Install the APK file to your Android device
3.  **Activate Module**:
    *   Open Xposed/LSPosed manager
    *   Find "AHOOK" in the module list and enable it
    *   Select the target applications to monitor (batch selection supported)
4.  **Immediate Effect**: No device restart required, monitoring starts immediately after installation

## MQTT Real-time Push
AHOOK supports real-time push of encryption logs to remote servers via MQTT protocol, facilitating real-time monitoring and data aggregation analysis.

### Feature Highlights
*   **Real-time Push**: Logs are pushed to MQTT Broker immediately after encryption operations occur
*   **Unified Topic**: All logs are sent to the same topic, with packageName field included in messages for easy filtering
*   **Automatic Reconnection**: Supports automatic reconnection when network is abnormal
*   **Background Keep-alive**: Uses foreground service to keep MQTT connection running continuously in background
*   **Flexible Configuration**: Supports MQTT server address, authentication information, topic prefix, etc.

### Configuration Steps
1.  Open AHOOK application and enter "Settings" page
2.  Enable "MQTT Push" switch
3.  Fill in MQTT Broker address (e.g., `tcp://192.168.1.100:1883`)
4.  Fill in username and password if authentication is required
5.  Set log topic prefix (default `ahook/logs/`)
6.  Click "Save Configuration", the system will automatically start the service and connect

### Background Keep-alive Configuration
To ensure MQTT connection continues to run in the background, please make the following settings:

**1. Disable Battery Optimization**
*   Enter System Settings → Apps → AHOOK → Battery → Select "No optimization" or "Unrestricted"

**2. Allow Background Running (required for some phones)**
*   MIUI: Settings → App Settings → App Management → AHOOK → Power Saving Strategy → Unrestricted
*   EMUI: Settings → Apps → App Launch Management → AHOOK → Disable automatic management
*   ColorOS: Settings → Battery → Power Consumption Protection → AHOOK → Allow background running

**3. Allow Notifications**
*   A persistent notification will appear in the notification bar after enabling MQTT, do not disable this notification or the service may be killed by the system

### Topic Format
*   Log Topic: `<topic_prefix>all`
    *   Example: `ahook/logs/all`
    *   All logs are uniformly sent to this topic, with `packageName` field included in messages
*   Control Topic: `ahook/control/#` (reserved)

### Message Format
Each log is pushed in JSON format:
```json
{
  "timestamp": "2024-01-01 12:00:00.000",
  "logName": "AES/CBC/PKCS5Padding.encrypt",
  "packageName": "com.target.app",
  "appName": "Target App",
  "keyType": "javax.crypto.spec.SecretKeySpec",
  "keyString": "your-secret-key",
  "keyHex": "796f7572-736563726574-6b6579",
  "keyBase64": "eW91ci1zZWNyZXQta2V5",
  "inputString": "hello",
  "inputHex": "68656c6c6f",
  "inputBase64": "aGVsbG8=",
  "outputString": "encrypted...",
  "outputHex": "656e637279707465642e2e2e",
  "ivString": "1234567890abcdef",
  "ivHex": "31323334353637383930616263646566",
  "stackTrace": "java.lang.Exception...\n\tat ..."
}
```

### Subscription Example
```bash
# Subscribe to all logs
mosquitto_sub -h your-mqtt-server -t "ahook/logs/all" -v

# Use emqx's WebSocket subscription
# Visit http://your-mqtt-server:18083 to view and manage messages
```

### Message Filtering
Since all logs are uniformly sent to one topic, subscribers can filter through the `packageName` field in messages:

**Python Example:**
```python
import json
import paho.mqtt.client as mqtt

def on_message(client, userdata, msg):
    log = json.loads(msg.payload)
    # Only process logs from target app
    if log.get('packageName') == 'com.target.app':
        print(f"Received {log['logName']}: {log.get('keyString', '')[:20]}...")

client = mqtt.Client()
client.on_message = on_message
client.connect("your-mqtt-server", 1883)
client.subscribe("ahook/logs/all")
client.loop_forever()
```

### Server-side Data Storage
The project provides a Python script `mqtt_to_mariadb.py` that can automatically receive MQTT messages and store them in MariaDB database.

**Install Dependencies:**
```bash
pip install -r requirements.txt
```

**Configure Database:**
Edit the configuration at the top of the script:
```python
MARIADB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': 'your_password',
    'database': 'traffic_capture',
    'charset': 'utf8mb4',
    'autocommit': True
}

MQTT_CONFIG = {
    'broker': '192.168.50.18',
    'port': 1883,
    'topic': 'ahook/logs/#',
    ...
}
```

**Run Script:**
```bash
python mqtt_to_mariadb.py
```

**Features:**
*   Automatically creates `ahook_logs` data table
*   Real-time receives messages and stores them in database
*   Supports automatic reconnection after disconnection
*   Logs output to console and `mqtt_receiver.log` file
*   Graceful exit with `Ctrl+C`

### MQTT Broker Recommendations
*   **Mosquitto**: Lightweight, open source and free, suitable for personal use
*   **EMQX**: Enterprise-grade, feature-rich, supports high concurrency
*   **HiveMQ**: Commercial-grade, high stability

### Notes
*   Ensure network connectivity between device and MQTT server
*   TLS encryption (mqtts://) is recommended for production environments
*   Transmitted data may contain sensitive information, please keep it properly

## Data Access Methods
Log information is stored in local SQLite database, which can be accessed through the following methods:

### Method 1: ADB + sqlite3 (Recommended)
```bash
# Directly enter database interactive mode
adb shell "sqlite3 /data/data/com.wzh.ai/databases/hook_logs.db"

# Common query command examples:
# View all records
SELECT * FROM logs;

# View the latest 20 records in reverse chronological order
SELECT * FROM logs ORDER BY timestamp DESC LIMIT 20;

# Filter by application package name
SELECT * FROM logs WHERE package_name='com.target.app';

# Find specific encryption operations (like AES encryption)
SELECT * FROM logs WHERE log_name LIKE '%encrypt%' AND package_name='com.target.app';

# View key-related information
SELECT key_type, key_string, key_hex FROM logs WHERE package_name='com.target.app';

# Count the number of encryption operations for a specific application
SELECT log_name, COUNT(*) as count FROM logs WHERE package_name='com.target.app' GROUP BY log_name;
```

### Method 2: Single-line Query Command
```bash
# Quick view of the latest 10 records
adb shell "sqlite3 -json /data/data/com.wzh.ai/databases/hook_logs.db 'SELECT * FROM logs ORDER BY _id DESC LIMIT 10;'"

# Query by application package name
adb shell "sqlite3 -json /data/data/com.wzh.ai/databases/hook_logs.db 'SELECT * FROM logs WHERE package_name=\"com.target.app\";'"

# Search for specific keywords
adb shell "sqlite3 -json /data/data/com.wzh.ai/databases/hook_logs.db 'SELECT * FROM logs WHERE log_name LIKE \"%AES%\";'"
```

## Database Structure
Database `hook_logs.db` contains `logs` table with the following main fields:
*   `_id`: Unique record identifier
*   `timestamp`: Operation timestamp
*   `log_name`: Operation name (e.g., AES/CBC/PKCS5Padding.encrypt)
*   `package_name`: Application package name
*   `app_name`: Application name
*   `key_type`: Key type
*   `key_string/key_hex/key_base64`: Multiple formats of key
*   `input_string/input_hex/input_base64`: Multiple formats of input data
*   `output_string/output_hex/output_base64`: Multiple formats of output data
*   `iv_string/iv_hex/iv_base64`: Multiple formats of IV parameter
*   `stack_trace`: Call stack information

## Common Issues
**Q: Why can't I see logs for some applications?**
A: Please check if AHOOK module has been enabled for that application in Xposed manager.

**Q: Do I need to restart after activating the module?**
A: No, AHOOK supports hot loading and no device restart is required after activation.

**Q: Where is the database file located?**
A: `/data/data/com.wzh.ai/databases/hook_logs.db`

## Legal Statement and Disclaimer

### Legal Use Requirements
This tool is only for legitimate security research, penetration testing, compliance auditing, and software development debugging. When using this tool, you must ensure:

1. **Obtain Clear Authorization**: Before monitoring any application, you must obtain explicit written authorization from the application owner
2. **Comply with Laws and Regulations**: Strictly comply with relevant laws and regulations in your country/region, including but not limited to the Cybersecurity Law, Personal Information Protection Law, etc.
3. **Prohibit Illegal Use**: Strictly prohibit using this tool for any unauthorized penetration, cracking, data theft, or other illegal activities

### Scope Limitations
This tool should only be used in the following legitimate scenarios:
- Authorized penetration testing
- Security audit of own applications
- Academic research and educational demonstrations
- Compliance checks that comply with legal regulations

### Privacy Protection Statement
This tool may capture sensitive information (including but not limited to encryption keys, user data, etc.). During use:
- Please strictly follow the principle of data minimization
- Properly keep captured data and do not disclose it to unrelated third parties
- Clean up relevant data promptly after testing is completed
- Comply with applicable data protection regulations

### Liability Limitation
1. The developer is not responsible for any consequences of using this tool
2. Any legal disputes arising from improper use of this tool shall be fully borne by the user
3. The developer reserves the right to pursue legal responsibility for improper users
4. Using this tool indicates that you agree to the above terms and commit to legal use

### Technical Circumvention Statement
This tool is a security research tool designed to help developers and security researchers discover and fix security vulnerabilities. Users should ensure that their use behavior complies with relevant laws and regulations on anti-technical circumvention in various countries.

## Contribution
Welcome to submit Pull Requests or Issues to improve this tool. We are committed to creating a better encryption analysis tool.

## Ethics and Best Practices

### Ethical Guidelines
When using this tool, please follow these ethical guidelines:

- Respect others' privacy and data security
- Only use with proper authorization
- Do not use for any behavior that harms others' interests
- Actively report discovered security vulnerabilities to relevant parties

### Best Practices
- Conduct testing in controlled environments (such as laboratories, virtual machines, etc.)
- Develop clear test plans and scope definitions
- Record test processes and results for easy tracking
- Follow responsible vulnerability disclosure principles
- Regularly clean up test data to avoid data leakage

## Contact
*   **Author**: water
*   **Technical Exchange Group**: 1037044062

## License
This project is licensed under the GNU General Public License v3.0 (GPLv3). For details, please refer to the LICENSE file.