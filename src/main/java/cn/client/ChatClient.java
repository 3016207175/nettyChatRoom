package cn.client;

import cn.message.*;
import cn.protocol.MessageCodecSharable;
import cn.protocol.ProcotolFrameDecoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;


import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ChatClient {
    public static void main(String[] args) {
        NioEventLoopGroup group = new NioEventLoopGroup();
        LoggingHandler LOGGING_HANDLER = new LoggingHandler(LogLevel.DEBUG);
        MessageCodecSharable MESSAGE_CODEC = new MessageCodecSharable();
        CountDownLatch WAIT_FOR_LOGIN = new CountDownLatch(1);
        // 标记是否登录成功
        AtomicBoolean LOGIN = new AtomicBoolean(false);
        AtomicBoolean EXIT = new AtomicBoolean(false);
        Scanner scanner = new Scanner(System.in);
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.channel(NioSocketChannel.class);
            bootstrap.group(group);
            // 设置客户端连接超时时间 底层使用的是connectPromise进行线程间通信和定时任务 该参数属于socketchannel
            // 如果超过设置时间会执行定时任务然后查看连接状态然后提供消息给主线程 让主线程决定是否抛出异常
            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,1000);
            bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast(new ProcotolFrameDecoder());
                    //pipeline.addLast(LOGGING_HANDLER);//调试时可以打开
                    pipeline.addLast(MESSAGE_CODEC);
                    // 3s 内如果没有向服务器写数据 会触发一个 IdleState#WRITER_IDLE 事件
                    pipeline.addLast(new IdleStateHandler(0, 3, 0));
                    // 实现心跳功能
                    pipeline.addLast(new ChannelDuplexHandler() {
                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                            IdleStateEvent event = (IdleStateEvent) evt;
                            if (event.state() == IdleState.WRITER_IDLE) {
                                ctx.writeAndFlush(new PingMessage());
                            }
                        }
                    });
                    pipeline.addLast("client handler", new ChannelInboundHandlerAdapter() {
                        // 接收响应消息
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            System.out.println(msg);//TODO
                            if ((msg instanceof LoginResponseMessage)) {
                                LoginResponseMessage response = (LoginResponseMessage) msg;
                                if (response.isSuccess()) {
                                    // 如果登录成功
                                    LOGIN.set(true);
                                }
                                // 唤醒 system in 线程
                                WAIT_FOR_LOGIN.countDown();
                            }
                        }

                        // 在连接建立后触发 active 事件
                        // 开启一个线程负责接受用户在控制台的输入并发送给服务器
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            new Thread(() -> {
                                System.out.println("请输入您的用户名:");
                                String username = scanner.nextLine();
                                if (EXIT.get()) {
                                    return;
                                }
                                System.out.println("请输入您的密码:");
                                String password = scanner.nextLine();
                                if (EXIT.get()) {
                                    return;
                                }
                                // 构造消息对象
                                LoginRequestMessage message = new LoginRequestMessage(username, password);
                                // 发送消息 只要写入消息就会触发出栈操作
                                ctx.writeAndFlush(message);
                                System.out.println("等待服务器验证和连接......");
                                try {
                                    WAIT_FOR_LOGIN.await();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                // 如果登录失败
                                if (!LOGIN.get()) {
                                    ctx.channel().close();
                                    return;
                                }

                                System.out.println("恭喜您登录成功 请按照提示输入指令信息");
                                System.out.println("==================================");
                                System.out.println("send [username] [content]");
                                System.out.println("gsend [group name] [content]");
                                System.out.println("gcreate [group name] [m1,m2,m3...]");
                                System.out.println("gmembers [group name]");
                                System.out.println("gjoin [group name]");
                                System.out.println("gquit [group name]");
                                System.out.println("quit");
                                System.out.println("==================================");

                                while (true) {
                                    String command = null;
                                    try {
                                        command = scanner.nextLine();
                                    } catch (Exception e) {
                                        break;
                                    }
                                    if (EXIT.get()) {
                                        return;
                                    }
                                    // 命令解析过程
                                    String[] s = command.split(" ");
                                    try {
                                        switch (s[0]) {
                                            case "send":
                                                ctx.writeAndFlush(new ChatRequestMessage(username, s[1], s[2]));
                                                break;
                                            case "gsend":
                                                ctx.writeAndFlush(new GroupChatRequestMessage(username, s[1], s[2]));
                                                break;
                                            case "gcreate":
                                                Set<String> set = new HashSet<>(Arrays.asList(s[2].split(",")));
                                                set.add(username);
                                                ctx.writeAndFlush(new GroupCreateRequestMessage(s[1], set));
                                                break;
                                            case "gmembers":
                                                ctx.writeAndFlush(new GroupMembersRequestMessage(s[1]));
                                                break;
                                            case "gjoin":
                                                ctx.writeAndFlush(new GroupJoinRequestMessage(username, s[1]));
                                                break;
                                            case "gquit":
                                                ctx.writeAndFlush(new GroupQuitRequestMessage(username, s[1]));
                                                break;
                                            case "quit":
                                                ctx.channel().close();
                                                return;
                                        }
                                    } catch (Exception e) {
                                        System.out.println("客户端提示: 您的输入信息有错误 请按照提示输入指令信息");
                                        System.out.println("==================================");
                                        System.out.println("send [username] [content]");
                                        System.out.println("gsend [group name] [content]");
                                        System.out.println("gcreate [group name] [m1,m2,m3...]");
                                        System.out.println("gmembers [group name]");
                                        System.out.println("gjoin [group name]");
                                        System.out.println("gquit [group name]");
                                        System.out.println("quit");
                                        System.out.println("==================================");
                                    }
                                }
                            }, "用户输入线程").start();
                        }

                        // 在连接断开时触发
//                                @Override
//                                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
//                                    log.debug("连接已经断开，按任意键退出..");
//                                    EXIT.set(true);
//                                }
//
//                                // 在出现异常时触发
//                                @Override
//                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
//                                    log.debug("连接已经断开，按任意键退出..{}", cause.getMessage());
//                                    EXIT.set(true);
//                                }
                    });
                }
            });
            Channel channel = bootstrap.connect("localhost", 8081).sync().channel();
            channel.closeFuture().sync();
        } catch (Exception e) {
            log.error("client error", e);
        } finally {
            group.shutdownGracefully();
        }
    }
}
