/**
 * MessageType 枚举定义了 TCP 通信中的 4 种报文类型。
 *
 * 报文交互流程：
 *   Client  --Initialization-->  Server   （告知要反转的块数 N）
 *   Client  <----agree---------  Server   （确认）
 *   Client  --reverseRequest-->  Server   （发送待反转数据）
 *   Client  <--reverseAnswer---  Server   （返回反转后的数据）
 */
public enum MessageType {
    /** 初始化报文：Client → Server，告知块数 N */
    INITIALIZATION((byte) 0x01),

    /** 确认报文：Server → Client */
    AGREE((byte) 0x02),

    /** 反转请求报文：Client → Server，携带待反转数据 */
    REVERSE_REQUEST((byte) 0x03),

    /** 反转应答报文：Server → Client，携带反转后的数据 */
    REVERSE_ANSWER((byte) 0x04);

    private final byte code;

    MessageType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    /**
     * 根据类型码获取对应的 MessageType。
     * @param code 类型码
     * @return 对应的 MessageType
     * @throws IllegalArgumentException 如果类型码无效
     */
    public static MessageType fromCode(byte code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type code: " + code);
    }
}
