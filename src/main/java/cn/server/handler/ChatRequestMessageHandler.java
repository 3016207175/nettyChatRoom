package cn.server.handler;


import cn.message.ChatRequestMessage;
import cn.message.ChatResponseMessage;
import cn.server.session.SessionFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;


@ChannelHandler.Sharable
public class ChatRequestMessageHandler extends SimpleChannelInboundHandler<ChatRequestMessage> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ChatRequestMessage msg) throws Exception {
        String to = msg.getTo();
        // 根据接收用户名得到channel
        Channel channel = SessionFactory.getSession().getChannel(to);
        // 对方在线
        if (channel != null) {
            ChatResponseMessage chatResponseMessage = new ChatResponseMessage(msg.getFrom(), msg.getContent());
            chatResponseMessage.setSuccess(true);
            channel.writeAndFlush(chatResponseMessage);
        }
        // 对方不在线
        else {
            ctx.writeAndFlush(new ChatResponseMessage(false, "对方用户不存在或者不在线"));
        }
    }
}
