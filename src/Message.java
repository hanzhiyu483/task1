import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Message 类封装了 TCP 通信中的报文数据及二进制序列化/反序列化逻辑。
 *
 * 报文格式：
 *   - Initialization:  [Type:1B] [N:4B]
 *   - agree:           [Type:1B]
 *   - reverseRequest:  [Type:1B] [Length:4B] [Data:Length B]
 *   - reverseAnswer:   [Type:1B] [Length:4B] [ReversedData:Length B]
 *
 * 所有多字节整数采用大端序（Big-Endian / Network Byte Order）。
 */
public class Message {

    private final MessageType type;

    /** 块数 N（仅 Initialization 使用） */
    private int n;

    /** 数据长度（仅 reverseRequest / reverseAnswer 使用） */
    private int length;

    /** 数据载荷（reverseRequest 的原文 或 reverseAnswer 的反转文） */
    private byte[] data;

    // ==================== 构造方法 ====================

    /** 构造 Initialization 报文 */
    public static Message createInitialization(int n) {
        Message msg = new Message(MessageType.INITIALIZATION);
        msg.n = n;
        return msg;
    }

    /** 构造 agree 报文 */
    public static Message createAgree() {
        return new Message(MessageType.AGREE);
    }

    /** 构造 reverseRequest 报文 */
    public static Message createReverseRequest(byte[] data) {
        Message msg = new Message(MessageType.REVERSE_REQUEST);
        msg.length = data.length;
        msg.data = data;
        return msg;
    }

    /** 构造 reverseAnswer 报文 */
    public static Message createReverseAnswer(byte[] reversedData) {
        Message msg = new Message(MessageType.REVERSE_ANSWER);
        msg.length = reversedData.length;
        msg.data = reversedData;
        return msg;
    }

    private Message(MessageType type) {
        this.type = type;
    }

    // ==================== 序列化 ====================

    /**
     * 将 Message 序列化为字节数组，用于网络传输。
     * @return 字节数组
     */
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeByte(type.getCode());

        switch (type) {
            case INITIALIZATION:
                dos.writeInt(n);
                break;
            case AGREE:
                // 仅类型码，无额外字段
                break;
            case REVERSE_REQUEST:
            case REVERSE_ANSWER:
                dos.writeInt(length);
                dos.write(data);
                break;
        }

        dos.flush();
        return baos.toByteArray();
    }

    /**
     * 从输入流中反序列化一个 Message 对象。
     * @param dis 数据输入流
     * @return 解析后的 Message
     */
    public static Message fromStream(DataInputStream dis) throws IOException {
        byte typeCode = dis.readByte();
        MessageType type = MessageType.fromCode(typeCode);

        switch (type) {
            case INITIALIZATION: {
                int n = dis.readInt();
                Message msg = createInitialization(n);
                return msg;
            }
            case AGREE: {
                return createAgree();
            }
            case REVERSE_REQUEST:
            case REVERSE_ANSWER: {
                int length = dis.readInt();
                byte[] data = new byte[length];
                dis.readFully(data);
                if (type == MessageType.REVERSE_REQUEST) {
                    return createReverseRequest(data);
                } else {
                    return createReverseAnswer(data);
                }
            }
            default:
                throw new IOException("Unknown message type: " + typeCode);
        }
    }

    // ==================== Getter ====================

    public MessageType getType() { return type; }
    public int getN() { return n; }
    public int getLength() { return length; }
    public byte[] getData() { return data; }

    /**
     * 将 data 载荷解码为 UTF-8 字符串。
     */
    public String getDataAsString() {
        if (data == null) return "";
        return new String(data, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        switch (type) {
            case INITIALIZATION:
                return "Initialization[N=" + n + "]";
            case AGREE:
                return "agree";
            case REVERSE_REQUEST:
                return "reverseRequest[length=" + length + ", data=" + getDataAsString() + "]";
            case REVERSE_ANSWER:
                return "reverseAnswer[length=" + length + ", data=" + getDataAsString() + "]";
            default:
                return "Unknown";
        }
    }
}
