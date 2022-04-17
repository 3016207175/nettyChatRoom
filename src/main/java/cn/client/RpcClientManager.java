package cn.client;


import cn.client.handler.RpcResponseMessageHandler;
import cn.message.RpcRequestMessage;
import cn.protocol.MessageCodecSharable;
import cn.protocol.ProcotolFrameDecoder;
import cn.protocol.SequenceIdGenerator;
import cn.server.service.HelloService;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.DefaultPromise;
import lombok.extern.slf4j.Slf4j;


import java.lang.reflect.Proxy;

@Slf4j
public class RpcClientManager {

    // 调用举例
    public static void main(String[] args) {
        HelloService service = getProxyService(HelloService.class);
        String s = service.sayHello("zhangsan");
        System.out.println(s);
    }

    // 创建代理类 代理的是服务提供者的接口
    // 当主线程拿到代理对象调用方法 具体过程异步执行并且把消息结果放到了RpcResponseMessageHandler的map里面
    public static <T> T getProxyService(Class<T> serviceClass) {
        ClassLoader loader = serviceClass.getClassLoader();
        Class<?>[] interfaces = new Class[]{serviceClass};

        Object o = Proxy.newProxyInstance(loader, interfaces, (proxy, method, args) -> {
            // 将方法调用转换为 消息对象
            int sequenceId = SequenceIdGenerator.nextId();
            RpcRequestMessage msg = new RpcRequestMessage(
                    sequenceId,
                    serviceClass.getName(),
                    method.getName(),
                    method.getReturnType(),
                    method.getParameterTypes(),
                    args
            );
            // 将消息对象发送出去
            getChannel().writeAndFlush(msg);

            // 注意这里一般是主线程调用方法 但是返回结果是通过handler存在NIO线程中 所以需要promise去进行线程间通信
            // 这里使用了一个map去保留 然后使用自定义的id从promise集合去拿到指定的结果
            // 需要指定异步接受结果的线程来自eventloop
            DefaultPromise<Object> promise = new DefaultPromise<>(getChannel().eventLoop());
            RpcResponseMessageHandler.PROMISES.put(sequenceId, promise);

            // 等待 promise 结果 await方法不抛异常 sync会抛异常
            promise.await();
            if (promise.isSuccess()) {
                // 调用正常
                return promise.getNow();
            } else {
                // 调用失败
                throw new RuntimeException(promise.cause());
            }
        });
        return (T) o;
    }

    private static volatile Channel channel = null;
    private static final Object LOCK = new Object();


    public static Channel getChannel() {
        if (channel != null) {
            return channel;
        }
        synchronized (LOCK) {
            if (channel != null) {
                return channel;
            }
            initChannel();
            return channel;
        }
    }

    // 初始化 channel 方法
    private static void initChannel() {
        NioEventLoopGroup group = new NioEventLoopGroup();
        LoggingHandler LOGGING_HANDLER = new LoggingHandler(LogLevel.DEBUG);
        MessageCodecSharable MESSAGE_CODEC = new MessageCodecSharable();
        RpcResponseMessageHandler RPC_HANDLER = new RpcResponseMessageHandler();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.group(group);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new ProcotolFrameDecoder());
                //ch.pipeline().addLast(LOGGING_HANDLER);
                ch.pipeline().addLast(MESSAGE_CODEC);
                ch.pipeline().addLast(RPC_HANDLER);
            }
        });
        try {
            channel = bootstrap.connect("localhost", 8081).sync().channel();
            channel.closeFuture().addListener(future -> {
                group.shutdownGracefully();
            });
        } catch (Exception e) {
            log.error("client error", e);
        }
    }
}
