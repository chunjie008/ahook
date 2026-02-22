# AHOOK - 加密操作监控Xposed模块

## 简介
AHOOK 是一个基于 Xposed 框架开发的高级加密操作监控模块，专门用于捕获 Android 应用程序内部的加密活动。该模块能够实时监控并记录常见的加密操作，包括哈希（MessageDigest）、消息认证码（Mac/HMAC）和加解密（Cipher），完整保存输入数据、输出结果、密钥、IV（初始化向量）以及调用堆栈等关键信息，为开发者和安全研究人员提供全面的加密行为分析能力。

**英文版本**：[README_EN.md](https://github.com/chunjie008/ahook/blob/main/README_EN.md)

## 功能特性
*   **全面的加密算法覆盖**：支持监控 MD5、SHA-1、SHA-256 等哈希算法，HMAC 算法，以及 AES、DES、RSA 等加解密操作
*   **详细的数据记录**：捕获输入数据、输出结果、密钥、IV、算法模式等完整参数
*   **多格式输出支持**：数据以 UTF-8 字符串、Hex（十六进制）和 Base64 三种格式显示，便于分析
*   **完整的调用链追踪**：记录完整的调用堆栈信息，支持精准的代码定位
*   **实时数据存储**：所有日志自动保存至本地 SQLite 数据库，支持随时查询
*   **高性能设计**：采用 WeakHashMap 优化内存管理，最小化对目标应用性能影响
*   **即插即用**：模块激活后无需重启设备，目标应用安装后即可立即开始监控

## 应用场景
*   **安全审计**：分析应用的加密实现是否存在安全隐患
*   **逆向工程**：研究应用的加密协议和数据传输方式
*   **渗透测试**：发现加密算法使用不当或弱加密实现
*   **合规检查**：验证应用是否符合数据保护和加密标准
*   **开发调试**：协助开发者验证加密逻辑的正确性

## 前提条件
*   已 Root 的 Android 设备（API 级别 21+）
*   已安装 Xposed 框架或其兼容实现（如 LSPosed、EdXposed 等）
*   目标应用需正常运行（无需特殊权限）

## 安装步骤
1.  **下载模块**：从发布页面下载最新版本的 `AHOOK.apk`
2.  **安装应用**：将 APK 文件安装到 Android 设备
3.  **激活模块**：
    *   打开 Xposed/LSPosed 管理器
    *   在模块列表中找到 "AHOOK" 并启用
    *   选择需要监控的目标应用（支持批量选择）
4.  **立即生效**：无需重启设备，安装完成后即可开始监控

## MQTT 实时推送
AHOOK 支持通过 MQTT 协议实时推送加密日志到远程服务器，方便进行实时监控和数据聚合分析。

### 功能特点
*   **实时推送**：加密操作发生后立即推送到 MQTT Broker
*   **统一主题**：所有日志发送至同一主题，消息中已包含 packageName 字段便于过滤
*   **自动重连**：支持网络异常时的自动重连
*   **后台保活**：采用前台服务保持 MQTT 连接在后台持续运行
*   **灵活配置**：支持 MQTT 服务器地址、认证信息、主题前缀等配置

### 配置步骤
1.  打开 AHOOK 应用，进入"设置"页面
2.  启用"MQTT 推送"开关
3.  填写 MQTT Broker 地址（如 `tcp://192.168.1.100:1883`）
4.  如需认证，填写用户名和密码
5.  设置日志主题前缀（默认 `ahook/logs/`）
6.  点击"保存配置"，系统将自动启动服务并连接

### 后台保活配置
为确保 MQTT 连接在后台持续运行，请进行以下设置：

**1. 关闭电池优化**
*   进入系统设置 → 应用 → AHOOK → 电池 → 选择"不优化"或"无限制"

**2. 允许后台运行（部分手机需要）**
*   MIUI：设置 → 应用设置 → 应用管理 → AHOOK → 省电策略 → 无限制
*   EMUI：设置 → 应用 → 应用启动管理 → AHOOK → 关闭自动管理
*   ColorOS：设置 → 电池 → 耗电保护 → AHOOK → 允许后台运行

**3. 允许通知**
*   启用 MQTT 后会在通知栏显示常驻通知，请勿禁用此通知，否则服务可能被系统杀死

### 主题格式
*   日志主题：`<topic_prefix>all`
    *   例如：`ahook/logs/all`
    *   所有日志统一发送到此主题，消息中已包含 `packageName` 字段
*   控制主题：`ahook/control/#`（预留）

### 消息格式
每条日志以 JSON 格式推送：
```json
{
  "timestamp": "2024-01-01 12:00:00.000",
  "logName": "AES/CBC/PKCS5Padding.encrypt",
  "packageName": "com.target.app",
  "appName": "目标应用",
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

### 订阅示例
```bash
# 订阅所有日志
mosquitto_sub -h your-mqtt-server -t "ahook/logs/all" -v

# 使用 emqx 的 WebSocket 订阅
# 访问 http://your-mqtt-server:18083 查看和管理消息
```

### 消息过滤
由于所有日志统一发送到一个主题，订阅者可通过消息中的 `packageName` 字段进行过滤：

**Python 示例：**
```python
import json
import paho.mqtt.client as mqtt

def on_message(client, userdata, msg):
    log = json.loads(msg.payload)
    # 只处理目标应用的日志
    if log.get('packageName') == 'com.target.app':
        print(f"收到 {log['logName']}: {log.get('keyString', '')[:20]}...")

client = mqtt.Client()
client.on_message = on_message
client.connect("your-mqtt-server", 1883)
client.subscribe("ahook/logs/all")
client.loop_forever()
```

### 服务端数据入库
项目提供了 Python 脚本 `mqtt_to_mariadb.py`，可自动接收 MQTT 消息并存入 MariaDB 数据库。

**安装依赖：**
```bash
pip install -r requirements.txt
```

**配置数据库：**
编辑脚本顶部的配置：
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

**运行脚本：**
```bash
python mqtt_to_mariadb.py
```

**功能特点：**
*   自动创建 `ahook_logs` 数据表
*   实时接收消息并存入数据库
*   支持断线自动重连
*   日志输出到控制台和 `mqtt_receiver.log` 文件
*   `Ctrl+C` 优雅退出

### MQTT Broker 推荐
*   **Mosquitto**：轻量级，开源免费，适合个人使用
*   **EMQX**：企业级，功能丰富，支持高并发
*   **HiveMQ**：商业级，稳定性高

### 注意事项
*   确保设备与 MQTT 服务器网络互通
*   生产环境建议使用 TLS 加密（mqtts://）
*   传输的数据可能包含敏感信息，请妥善保管

## 数据访问方式
日志信息存储在本地 SQLite 数据库中，可通过以下方式访问：

### 方式一：ADB + sqlite3（推荐）
```bash
# 直接进入数据库交互模式
adb shell "sqlite3 /data/data/com.wzh.ai/databases/hook_logs.db"

# 常用查询命令示例：
# 查看所有记录
SELECT * FROM logs;

# 按时间倒序查看最近的20条记录
SELECT * FROM logs ORDER BY timestamp DESC LIMIT 20;

# 按应用包名筛选
SELECT * FROM logs WHERE package_name='com.target.app';

# 查找特定加密操作（如AES加密）
SELECT * FROM logs WHERE log_name LIKE '%encrypt%' AND package_name='com.target.app';

# 查看密钥相关信息
SELECT key_type, key_string, key_hex FROM logs WHERE package_name='com.target.app';

# 统计特定应用的加密操作数量
SELECT log_name, COUNT(*) as count FROM logs WHERE package_name='com.target.app' GROUP BY log_name;
```

### 方式二：单行查询命令
```bash
# 快速查看最近10条记录
adb shell "sqlite3 -json /data/data/com.wzh.ai/databases/hook_logs.db 'SELECT * FROM logs ORDER BY _id DESC LIMIT 10;'"

# 按应用包名查询
adb shell "sqlite3 -json /data/data/com.wzh.ai/databases/hook_logs.db 'SELECT * FROM logs WHERE package_name=\"com.target.app\";'"

# 搜索特定关键词
adb shell "sqlite3 -json /data/data/com.wzh.ai/databases/hook_logs.db 'SELECT * FROM logs WHERE log_name LIKE \"%AES%\";'"
```

## 数据库结构
数据库 `hook_logs.db` 包含 `logs` 表，主要字段如下：
*   `_id`: 记录唯一标识符
*   `timestamp`: 操作时间戳
*   `log_name`: 操作名称（如 AES/CBC/PKCS5Padding.encrypt）
*   `package_name`: 应用包名
*   `app_name`: 应用名称
*   `key_type`: 密钥类型
*   `key_string/key_hex/key_base64`: 密钥的多种格式
*   `input_string/input_hex/input_base64`: 输入数据的多种格式
*   `output_string/output_hex/output_base64`: 输出数据的多种格式
*   `iv_string/iv_hex/iv_base64`: IV 参数的多种格式
*   `stack_trace`: 调用堆栈信息

## 常见问题
**Q: 为什么看不到某些应用的日志？**
A: 请检查 Xposed 管理器中是否已为该应用启用 AHOOK 模块。

**Q: 模块激活后需要重启吗？**
A: 不需要，AHOOK 支持热加载，激活后无需重启设备。

**Q: 数据库文件位置在哪里？**
A: `/data/data/com.wzh.ai/databases/hook_logs.db`

## 法律声明与免责条款

### 合法使用要求
本工具仅供合法的安全研究、渗透测试、合规审计及软件开发调试使用。使用本工具时，您必须确保：

1. **获得明确授权**：在监控任何应用程序之前，必须获得该应用所有者的明确书面授权
2. **遵守法律法规**：严格遵守所在国家/地区的相关法律法规，包括但不限于《网络安全法》、《个人信息保护法》等
3. **禁止非法用途**：严禁将本工具用于任何未经授权的渗透、破解、窃取数据或其他违法行为

### 适用范围限制
本工具应仅用于以下合法场景：
- 经授权的渗透测试
- 自有应用的安全审计
- 学术研究和教育演示
- 符合法律规定的合规性检查

### 隐私保护声明
本工具可能捕获敏感信息（包括但不限于加密密钥、用户数据等）。使用过程中：
- 请严格遵守数据最小化原则
- 妥善保管捕获的数据，不得泄露给无关第三方
- 测试完成后及时清理相关数据
- 遵守适用的数据保护法规

### 责任限制
1. 开发者不对本工具的任何使用后果承担责任
2. 因不当使用本工具导致的任何法律纠纷，由使用者自行承担全部责任
3. 开发者保留追究不当使用者法律责任的权利
4. 使用本工具即表示您同意上述条款，并承诺合法使用

### 技术规避声明
本工具属于安全研究工具，旨在帮助开发者和安全研究人员发现和修复安全漏洞。使用者应确保其使用行为符合各国关于反技术规避的相关法律法规。

## 贡献
欢迎提交 Pull Request 或 Issue 来改进本工具。我们致力于打造更好用的加密分析工具。

## 使用道德与最佳实践

### 道德准则
使用本工具时，请遵循以下道德准则：

- 尊重他人隐私权和数据安全
- 仅在获得适当授权的情况下使用
- 不得用于任何损害他人利益的行为
- 积极报告发现的安全漏洞给相关方

### 最佳实践
- 在受控环境中进行测试（如实验室、虚拟机等）
- 制定清晰的测试计划和范围界定
- 记录测试过程和结果，便于追溯
- 遵循负责任的漏洞披露原则
- 定期清理测试数据，避免数据泄露

## 联系方式
*   **作者**: water
*   **技术交流群**: 1037044062

## 许可证
本项目采用 GNU General Public License v3.0 (GPLv3) 许可。有关详细信息，请参阅 LICENSE 文件。
