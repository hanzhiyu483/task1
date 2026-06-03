# Task1: TCP Socket Programming — 文本反转服务

## 项目结构

```
task1/
├── src/
│   ├── MessageType.java    # 4 种报文类型枚举
│   ├── Message.java         # 报文封装与二进制序列化
│   ├── TCPServer.java       # TCP 服务器（多线程）
│   └── TCPClient.java       # TCP 客户端（分块发送）
├── test_file.txt            # 示例输入文件（可打印 ASCII）
├── run_log.txt              # 运行时自动生成的收发日志
└── README.md                # 本说明文档
```

## 运行环境

- **JDK**: Java 8 及以上
- **网络**: client 与 server 之间 TCP 可达
- **OS**: 任意支持 Java 的平台（开发环境：macOS / Ubuntu）

## 编译

```bash
cd task1
javac src/*.java -d out
```

## 运行

### 1. 启动 Server（运行在 guest OS 或本机另一终端）

```bash
cd task1
java -cp out TCPServer <port>
```

示例：
```bash
java -cp out TCPServer 8888
```

### 2. 启动 Client（运行在 host OS 或本机另一终端）

```bash
cd task1
java -cp out TCPClient <serverIP> <serverPort> <filePath> <Lmin> <Lmax> <seed>
```

示例：
```bash
java -cp out TCPClient 127.0.0.1 8888 test_file.txt 50 100 42
```

## 参数说明

| 参数 | 说明 |
|------|------|
| serverIP | Server 的 IP 地址 |
| serverPort | Server 的监听端口 |
| filePath | 待发送的 ASCII 文本文件路径 |
| Lmin | 每块的最小字节数 |
| Lmax | 每块的最大字节数（最后一块除外） |
| seed | 随机种子（用于分块算法的可复现性） |

## 报文协议

4 种报文类型，通过 1 字节 Type 字段区分：

| Type | 报文 | 方向 | 格式 |
|------|------|------|------|
| 0x01 | Initialization | Client → Server | `[Type:1B] [N:4B]` |
| 0x02 | agree | Server → Client | `[Type:1B]` |
| 0x03 | reverseRequest | Client → Server | `[Type:1B] [Length:4B] [Data:Length B]` |
| 0x04 | reverseAnswer | Server → Client | `[Type:1B] [Length:4B] [ReversedData:Length B]` |

所有多字节字段使用大端序（Network Byte Order）。

## 分块算法

1. 使用命令行指定的 `seed` 初始化 `java.util.Random`
2. 从文件起始位置 0 开始，依次生成 `[Lmin, Lmax]` 范围内的随机数作为块长度
3. 最后一块取剩余所有字节（可能 < Lmin）
4. 块数 N = 生成的块总数，发送在 Initialization 报文中

**验收示例**：文件 520 字节，Lmin=50，Lmax=100，seed=42，
Random 依次生成 73、91、58… → N = ?，第 3 块起始字节 = 73+91 = 164。

## 输出

| 输出 | 说明 |
|------|------|
| 终端打印 | 每块的反转文本（格式：`第x块：反转的文本`） |
| `<原文件名>.reversed` | 完整反转文件 |
| `run_log.txt` | 运行日志（含时间戳，与 Wireshark 截图印证） |

## 设计要点

### 1. 多客户端并发（Server）
- 使用 `Executors.newCachedThreadPool()` 为每个连接分配线程
- 每个 ClientHandler 独立处理一组 Initialization → agree → N×(Request/Answer) 流程

### 2. 二进制报文序列化
- 使用 Java `DataOutputStream`/`DataInputStream` 实现大端序二进制编码
- 工厂方法（`createInitialization`、`createAgree` 等）确保报文类型与字段一致

### 3. 可复现的分块
- 基于 `seed` 的 `Random` 实例确保同参数下每次分块结果一致
- 支持验收时的手动推算

### 4. 日志系统
- `synchronized` 方法保证多线程日志写入安全
- 时间戳精度到毫秒（`yyyy-MM-dd HH:mm:ss.SSS`）

## 验收核查项

- [ ] 4 种报文格式与要求一致
- [ ] 分块算法可手推复现
- [ ] run_log.txt 时间戳与 Wireshark 截图吻合
- [ ] Server 可同时处理 2+ 客户端
- [ ] 代码结构清晰、命名规范、注释充分
- [ ] 异常处理完善（连接中断、非法报文等）
