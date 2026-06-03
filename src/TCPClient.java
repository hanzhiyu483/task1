import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TCPClient — TCP 文本反转客户端。
 *
 * 功能：
 *   1. 读取指定 ASCII 文件，按随机块大小分块。
 *   2. 与 Server 建立 TCP 连接，发送 Initialization（块数 N），接收 agree。
 *   3. 逐块发送 reverseRequest，接收 reverseAnswer，在终端打印反转文本。
 *   4. 最终输出完整反转文件（文件名 = 原文件名 + ".reversed"）。
 *   5. 所有收发事件记录到 run_log.txt，带时间戳（与 Wireshark 抓包印证）。
 *
 * 用法：
 *   java TCPClient <serverIP> <serverPort> <filePath> <Lmin> <Lmax> <seed>
 *
 * 示例：
 *   java TCPClient 127.0.0.1 8888 test.txt 50 100 42
 */
public class TCPClient {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final String serverIP;
    private final int serverPort;
    private final String filePath;
    private final int lmin;
    private final int lmax;
    private final long seed;

    /** 全局日志写入器 */
    private PrintWriter logger;

    public TCPClient(String serverIP, int serverPort, String filePath,
                     int lmin, int lmax, long seed) {
        this.serverIP = serverIP;
        this.serverPort = serverPort;
        this.filePath = filePath;
        this.lmin = lmin;
        this.lmax = lmax;
        this.seed = seed;
    }

    /**
     * 执行完整的 TCP 反转流程。
     */
    public void run() throws IOException {
        // 初始化日志
        this.logger = new PrintWriter(new FileWriter("run_log.txt", true), true);

        // --- 1. 读取文件 ---
        byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
        // 验证是否为可打印 ASCII
        String fileContent = new String(fileBytes, StandardCharsets.UTF_8);
        log("[Client] Read file: " + filePath + " (" + fileBytes.length + " bytes)");

        // --- 2. 计算分块 ---
        int[] chunkSizes = computeChunkSizes(fileBytes.length);
        int n = chunkSizes.length;
        log("[Client] Chunk plan: N=" + n + ", Lmin=" + lmin + ", Lmax=" + lmax
                + ", seed=" + seed);
        for (int i = 0; i < n; i++) {
            log("[Client]   Chunk " + (i + 1) + ": " + chunkSizes[i] + " bytes");
        }

        // --- 3. 建立 TCP 连接 ---
        try (Socket socket = new Socket(serverIP, serverPort);
             DataOutputStream dos = new DataOutputStream(
                     new BufferedOutputStream(socket.getOutputStream()));
             DataInputStream dis = new DataInputStream(
                     new BufferedInputStream(socket.getInputStream()))) {

            log("[Client] Connected to " + serverIP + ":" + serverPort);

            // --- 4. 发送 Initialization ---
            Message initMsg = Message.createInitialization(n);
            dos.write(initMsg.toBytes());
            dos.flush();
            log("[Client] Sent: Initialization[N=" + n + "]");

            // --- 5. 接收 agree ---
            Message agreeMsg = Message.fromStream(dis);
            if (agreeMsg.getType() != MessageType.AGREE) {
                throw new IOException("Expected agree, got " + agreeMsg.getType());
            }
            log("[Client] Received: agree");

            // --- 6. 逐块发送 reverseRequest、接收 reverseAnswer ---
            StringBuilder fullReversed = new StringBuilder();
            int offset = 0;

            for (int i = 0; i < n; i++) {
                int chunkSize = chunkSizes[i];
                byte[] chunk = new byte[chunkSize];
                System.arraycopy(fileBytes, offset, chunk, 0, chunkSize);
                offset += chunkSize;

                String originalText = new String(chunk, StandardCharsets.UTF_8);

                // 发送 reverseRequest
                Message reqMsg = Message.createReverseRequest(chunk);
                dos.write(reqMsg.toBytes());
                dos.flush();
                log("[Client] Sent block " + (i + 1) + "/" + n
                        + ": length=" + chunkSize + ", text=\"" + originalText + "\"");

                // 接收 reverseAnswer
                Message ansMsg = Message.fromStream(dis);
                if (ansMsg.getType() != MessageType.REVERSE_ANSWER) {
                    throw new IOException("Expected reverseAnswer, got " + ansMsg.getType());
                }

                String reversedText = ansMsg.getDataAsString();
                fullReversed.append(reversedText);

                log("[Client] Received block " + (i + 1) + "/" + n
                        + ": reversed=\"" + reversedText + "\"");

                // 终端打印
                System.out.println((i + 1) + "：" + reversedText);
            }

            // --- 7. 输出完整反转文件 ---
            String outputPath = filePath + ".reversed";
            Files.write(Paths.get(outputPath),
                    fullReversed.toString().getBytes(StandardCharsets.UTF_8));
            log("[Client] Wrote reversed file: " + outputPath
                    + " (" + fullReversed.length() + " bytes)");

            System.out.println();
            System.out.println("Done! Reversed file saved to: " + outputPath);
        }
    }

    /**
     * 使用随机种子计算分块方案。
     *
     * 算法：
     *   使用 seed 初始化 Random，依次生成 [Lmin, Lmax] 之间的随机数作为每块长度，
     *   直到覆盖整个文件。最后一块可能小于 Lmin（剩余字节不足）。
     *
     * @param totalBytes 文件总字节数
     * @return 每块的字节长度数组
     */
    public int[] computeChunkSizes(int totalBytes) {
        if (totalBytes <= 0) {
            return new int[0];
        }

        Random random = new Random(seed);
        List<Integer> sizes = new ArrayList<>();

        int remaining = totalBytes;
        while (remaining > 0) {
            int chunkSize;
            if (remaining <= lmax) {
                // 剩余字节不足 Lmax，全部作为最后一块
                chunkSize = remaining;
            } else {
                // 随机生成 [Lmin, Lmax] 之间的长度
                // 但要确保不会超过 remaining - Lmin（为后续块预留最小空间）
                int maxForThis = Math.min(lmax, remaining);
                if (maxForThis < lmin) {
                    chunkSize = remaining; // 最后一块
                } else {
                    chunkSize = lmin + random.nextInt(maxForThis - lmin + 1);
                }
            }
            sizes.add(chunkSize);
            remaining -= chunkSize;
        }

        int[] result = new int[sizes.size()];
        for (int i = 0; i < sizes.size(); i++) {
            result[i] = sizes.get(i);
        }
        return result;
    }

    /**
     * 线程安全日志输出（同时输出到控制台和文件）。
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
        if (args.length < 6) {
            System.out.println("Usage: java TCPClient <serverIP> <serverPort> " +
                    "<filePath> <Lmin> <Lmax> <seed>");
            System.out.println("Example: java TCPClient 127.0.0.1 8888 " +
                    "test.txt 50 100 42");
            System.exit(1);
        }

        String serverIP = args[0];
        int serverPort = Integer.parseInt(args[1]);
        String filePath = args[2];
        int lmin = Integer.parseInt(args[3]);
        int lmax = Integer.parseInt(args[4]);
        long seed = Long.parseLong(args[5]);

        // 参数校验
        if (lmin <= 0 || lmax <= 0 || lmin > lmax) {
            System.err.println("Error: Lmin and Lmax must be positive, with Lmin <= Lmax");
            System.exit(1);
        }

        try {
            TCPClient client = new TCPClient(serverIP, serverPort, filePath,
                    lmin, lmax, seed);
            client.run();
        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
