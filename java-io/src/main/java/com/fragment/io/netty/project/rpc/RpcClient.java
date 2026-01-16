package com.fragment.io.netty.project.rpc;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * RPC客户端
 * 
 * 功能：
 * 1. 连接RPC服务端
 * 2. 发送RPC请求
 * 3. 接收RPC响应
 * 4. 提供动态代理
 * 
 * 使用方式：
 * 1. 创建RpcClient实例
 * 2. 连接服务器：connect(host, port)
 * 3. 创建代理：create(接口类)
 * 4. 调用方法：代理对象.方法()
 * 
 * @author fragment
 * @date 2026-01-14
 */
public class RpcClient {
    
    private String host;
    private int port;
    private EventLoopGroup workerGroup;
    private Channel channel;
    private final Map<String, RpcFuture> futureMap = new ConcurrentHashMap<>();
    
    /**
     * 连接服务器
     */
    public void connect(String host, int port) throws Exception {
        this.host = host;
        this.port = port;
        this.workerGroup = new NioEventLoopGroup();
        
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.TCP_NODELAY, true)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline()
                        .addLast(new RpcEncoder(RpcRequest.class))
                        .addLast(new RpcDecoder(RpcResponse.class))
                        .addLast(new RpcClientHandler(futureMap));
                }
            });
        
        ChannelFuture future = bootstrap.connect(host, port).sync();
        this.channel = future.channel();
        System.out.println("RPC客户端连接成功: " + host + ":" + port);
    }
    
    /**
     * 发送请求
     */
    public RpcResponse send(RpcRequest request) throws Exception {
        if (channel == null || !channel.isActive()) {
            throw new RuntimeException("连接未建立或已断开");
        }
        
        // 创建Future
        RpcFuture future = new RpcFuture();
        futureMap.put(request.getRequestId(), future);
        
        // 发送请求
        channel.writeAndFlush(request);
        System.out.println("[客户端] 发送RPC请求: " + request);
        
        // 等待响应（超时5秒）
        return future.get(5, TimeUnit.SECONDS);
    }
    
    /**
     * 创建代理对象
     */
    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
            interfaceClass.getClassLoader(),
            new Class<?>[]{interfaceClass},
            new RpcInvocationHandler(this, interfaceClass)
        );
    }
    
    /**
     * 关闭客户端
     */
    public void close() {
        if (channel != null) {
            channel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        System.out.println("RPC客户端已关闭");
    }
    
    /**
     * RPC客户端处理器
     */
    static class RpcClientHandler extends SimpleChannelInboundHandler<RpcResponse> {
        
        private final Map<String, RpcFuture> futureMap;
        
        public RpcClientHandler(Map<String, RpcFuture> futureMap) {
            this.futureMap = futureMap;
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RpcResponse response) {
            System.out.println("[客户端] 收到RPC响应: " + response);
            
            // 获取Future
            RpcFuture future = futureMap.remove(response.getRequestId());
            if (future != null) {
                future.setResponse(response);
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[客户端] 异常: " + cause.getMessage());
            cause.printStackTrace();
            ctx.close();
        }
    }
    
    /**
     * RPC Future
     */
    static class RpcFuture {
        private RpcResponse response;
        private CountDownLatch latch = new CountDownLatch(1);
        
        public void setResponse(RpcResponse response) {
            this.response = response;
            latch.countDown();
        }
        
        public RpcResponse get(long timeout, TimeUnit unit) throws Exception {
            if (latch.await(timeout, unit)) {
                return response;
            }
            throw new RuntimeException("RPC调用超时");
        }
    }
    
    /**
     * RPC动态代理
     */
    static class RpcInvocationHandler implements InvocationHandler {
        
        private final RpcClient client;
        private final Class<?> interfaceClass;
        
        public RpcInvocationHandler(RpcClient client, Class<?> interfaceClass) {
            this.client = client;
            this.interfaceClass = interfaceClass;
        }
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 创建RPC请求
            RpcRequest request = new RpcRequest();
            request.setRequestId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            request.setInterfaceName(interfaceClass.getName());
            request.setMethodName(method.getName());
            request.setParameterTypes(method.getParameterTypes());
            request.setParameters(args);
            
            // 发送请求并等待响应
            RpcResponse response = client.send(request);
            
            if (response.isSuccess()) {
                return response.getResult();
            } else {
                throw response.getError();
            }
        }
    }
    
    /**
     * 测试示例
     */
    public static void main(String[] args) throws Exception {
        // 创建客户端
        RpcClient client = new RpcClient();
        
        try {
            // 连接服务器
            client.connect("localhost", 8888);
            
            // 创建代理
            RpcServer.HelloService helloService = client.create(RpcServer.HelloService.class);
            
            // 调用方法
            String result1 = helloService.sayHello("World");
            System.out.println("\n[结果] " + result1);
            
            int result2 = helloService.add(10, 20);
            System.out.println("\n[结果] 10 + 20 = " + result2);
            
            // 等待一段时间
            Thread.sleep(1000);
            
        } finally {
            client.close();
        }
    }
}
