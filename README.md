# AHOOK - 加密操作监控Xposed模块

## 简介
AHOOK 是一个基于 Xposed 框架开发的高级加密操作监控模块，专门用于捕获 Android 应用程序内部的加密活动。该模块能够实时监控并记录常见的加密操作，包括哈希（MessageDigest）、消息认证码（Mac/HMAC）和加解密（Cipher），完整保存输入数据、输出结果、密钥、IV（初始化向量）以及调用堆栈等关键信息，为开发者和安全研究人员提供全面的加密行为分析能力。

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
