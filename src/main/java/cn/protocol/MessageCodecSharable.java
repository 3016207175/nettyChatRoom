package cn.protocol;


import cn.config.Config;
import cn.message.Message;
import com.alibaba.fastjson.JSON;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import lombok.extern.slf4j.Slf4j;


import java.util.List;

@Slf4j
@ChannelHandler.Sharable
/**
 * 必须和 LengthFieldBasedFrameDecoder 一起使用，确保接到的 ByteBuf 消息是完整的
 * 本类是负责传输协议 如何组织信息 如果避免粘包半包问题等
 */
public class MessageCodecSharable extends MessageToMessageCodec<ByteBuf, Message> {
    @Override
    public void encode(ChannelHandlerContext ctx, Message msg, List<Object> outList) {
        try {
            ByteBuf out = ctx.alloc().buffer();
            // 1. 4 字节的魔数
            out.writeBytes(new byte[]{1, 2, 3, 4});
            // 2. 1 字节的版本,
            out.writeByte(1);
            // 3. 1 字节的序列化方式 jdk 0 , json 1 这个是按照枚举对象的顺序
            out.writeByte(Config.getSerializerAlgorithm().ordinal());
            // 4. 1 字节的指令类型
            out.writeByte(msg.getMessageType());
            // 5. 4 个字节
            out.writeInt(msg.getSequenceId());
            // 无意义，对齐填充
            out.writeByte(0xff);
            // 6. 获取内容的字节数组
            byte[] bytes = Config.getSerializerAlgorithm().serialize(msg);
            // 7. 长度
            out.writeInt(bytes.length);
            // 8. 写入内容
            out.writeBytes(bytes);
            outList.add(out);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out){
        try {
            int magicNum = in.readInt();
            byte version = in.readByte();
            byte serializerAlgorithm = in.readByte(); // 0 或 1
            byte messageType = in.readByte(); // 0,1,2...
            int sequenceId = in.readInt();
            in.readByte();
            int length = in.readInt();
            byte[] bytes = new byte[length];
            in.readBytes(bytes, 0, length);

            // 找到反序列化算法
            Serializer.Algorithm algorithm = Serializer.Algorithm.values()[serializerAlgorithm];
            // 确定具体消息类型
            Class<? extends Message> messageClass = Message.getMessageClass(messageType);
            Message message = algorithm.deserialize(messageClass, bytes);
            System.out.println(JSON.toJSONString(message));
            out.add(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
