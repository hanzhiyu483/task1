import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TCPServer — TCP 文本反转服务器。
 *
 * 功能：
 *   1. 监听指定端口，支持多客户端并发处理。
 *   2. 接收客户端 Initialization 报文（获取块数 N），回复 agree。
 *   3. 逐块接收 reverseRequest，反转文本后回复 reverseAnswer。
 *   4. 将所有收发事件记录到 run_log.txt，带时间戳。
 *
 * 用法：
 *   java TCPServer <port>
 *
 * 示例：
 *   java TCPServer 8888
 */
public class TCPServer {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final int port;
    private final ExecutorService threadPool;

    /** 全局日志写入器（线程安全） */
    private final PrintWriter logger;

    public TCPServer(int port) throws IOException {
        this.port = port;
        // 使用缓存线程池，支持多个 client 同时连接
        this.threadPool = Executors.newCachedThreadPool();
        this.logger = new PrintWriter(new FileWriter("run_log.txt", true), true);
    }

    /**
     * 启动服务器，持续接受客户端连接。
     */
    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log("Server started on port " + port);
            System.out.println("TCPServer listening on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                String clientAddr = clientSocket.getInetAddress().getHostAddress()
                        + ":" + clientSocket.getPort();
                log("Accepted connection from " + clientAddr);

                // 提交给线程池处理
                threadPool.submit(() -> handleClient(clientSocket, clientAddr));
            }
        }
    }

    /**
     * 处理单个客户端请求。
     */
    private void handleClient(Socket socket, String clientAddr) {
        try (socket;
             DataInputStream dis = new DataInputStream(
                     new BufferedInputStream(socket.getInputStream()));
             DataOutputStream dos = new DataOutputStream(
                     new BufferedOutputStream(socket.getOutputStream()))) {

            // --- 1. 接收 Initialization ---
            Message initMsg = Message.fromStream(dis);
            if (initMsg.getType() != MessageType.INITIALIZATION) {
                log("[" + clientAddr + "] ERROR: Expected Initialization, got "
                        + initMsg.getType());
                return;
            }
            int n = initMsg.getN();
            log("[" + clientAddr + "] Received: " + initMsg);
            System.out.println("[" + clientAddr + "] Expecting " + n + " blocks");

            // --- 2. 发送 agree ---
            Message agreeMsg = Message.createAgree();
            dos.write(agreeMsg.toBytes());
            dos.flush();
            log("[" + clientAddr + "] Sent: agree");

            // --- 3. 逐块处理 reverseRequest → reverseAnswer ---
            for (int i = 1; i <= n; i++) {
                Message reqMsg = Message.fromStream(dis);
                if (reqMsg.getType() != MessageType.REVERSE_REQUEST) {
                    log("[" + clientAddr + "] ERROR: Expected reverseRequest, got "
                            + reqMsg.getType());
                    return;
                }

                String originalText = reqMsg.getDataAsString();
                String reversedText = reverse(originalText);
                byte[] reversedBytes = reversedText.getBytes("UTF-8");

                log("[" + clientAddr + "] Received block " + i + "/" + n
                        + ": length=" + reqMsg.getLength()
                        + ", text=\"" + originalText + "\"");

                Message ansMsg = Message.createReverseAnswer(reversedBytes);
                dos.write(ansMsg.toBytes());
                dos.flush();

                log("[" + clientAddr + "] Sent block " + i + "/" + n
                        + ": reversed=\"" + reversedText + "\"");
            }

            log("[" + clientAddr + "] Completed — all " + n + " blocks processed");
            System.out.println("[" + clientAddr + "] Completed " + n + " blocks");

        } catch (EOFException e) {
            log("[" + clientAddr + "] Client disconnected prematurely");
        } catch (IOException e) {
            log("[" + clientAddr + "] Connection error: " + e.getMessage());
        }
    }

    /**
     * 反转字符串。保持每个字符位置镜像颠倒。
     * 例如 "a little monkey" → "yeknom elttil a"
     */
    private static String reverse(String s) {
        return new StringBuilder(s).reverse().toString();
    }

    /**
     * 线程安全地写入日志（同时输出到控制台和文件）。
     */
    private synchronized void log(String msg) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        String line = "[" + timestamp + "] " + msg;
        System.out.println(line);
        if (logger != null) {
            logger.println(line);
            logger.flush();
        }
    }

    // ==================== main ====================

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java TCPServer <port>");
            System.out.println("Example: java TCPServer 8888");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);

        try {
            TCPServer server = new TCPServer(port);
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
