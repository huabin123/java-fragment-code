package com.fragment.io.netty.project.rpc;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPC服务端
 * 
 * 功能：
 * 1. 接收RPC请求
 * 2. 反射调用本地方法
 * 3. 返回执行结果
 * 
 * 使用方式：
 * 1. 创建RpcServer实例
 * 2. 注册服务：registerService(接口类, 实现类实例)
 * 3. 启动服务：start(端口)
 * 
 * @author fragment
 * @date 2026-01-14
 */
public class RpcServer {
    
    private final Map<String, Object> serviceMap = new ConcurrentHashMap<>();
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    
    /**
     * 注册服务
     */
    public void registerService(Class<?> interfaceClass, Object serviceImpl) {
        serviceMap.put(interfaceClass.getName(), serviceImpl);
        System.out.println("注册服务: " + interfaceClass.getName());
    }
    
    /**
     * 启动服务器
     */
    public void start(int port) throws Exception {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                            .addLast(new RpcDecoder(RpcRequest.class))
                            .addLast(new RpcEncoder(RpcResponse.class))
                            .addLast(new RpcServerHandler(serviceMap));
                    }
                });
            
            ChannelFuture future = bootstrap.bind(port).sync();
            System.out.println("RPC服务端启动成功，端口: " + port);
            System.out.println("已注册服务: " + serviceMap.keySet());
            
            future.channel().closeFuture().sync();
        } finally {
            shutdown();
        }
    }
    
    /**
     * 关闭服务器
     */
    public void shutdown() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        System.out.println("RPC服务端已关闭");
    }
    
    /**
     * RPC服务端处理器
     */
    static class RpcServerHandler extends SimpleChannelInboundHandler<RpcRequest> {
        
        private final Map<String, Object> serviceMap;
        
        public RpcServerHandler(Map<String, Object> serviceMap) {
            this.serviceMap = serviceMap;
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) {
            System.out.println("\n[服务端] 收到RPC请求: " + request);
            
            RpcResponse response = new RpcResponse();
            response.setRequestId(request.getRequestId());
            
            try {
                // 1. 获取服务实例
                Object serviceBean = serviceMap.get(request.getInterfaceName());
                if (serviceBean == null) {
                    throw new RuntimeException("服务不存在: " + request.getInterfaceName());
                }
                
                // 2. 获取方法
                Method method = serviceBean.getClass().getMethod(
                    request.getMethodName(),
                    request.getParameterTypes()
                );
                
                // 3. 反射调用
                Object result = method.invoke(serviceBean, request.getParameters());
                
                // 4. 设置返回值
                response.setResult(result);
                System.out.println("[服务端] 执行成功，返回结果: " + result);
                
            } catch (Exception e) {
                System.err.println("[服务端] 执行失败: " + e.getMessage());
                e.printStackTrace();
                response.setError(e);
            }
            
            // 5. 返回响应
            ctx.writeAndFlush(response);
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[服务端] 异常: " + cause.getMessage());
            cause.printStackTrace();
            ctx.close();
        }
    }
    
    /**
     * 测试示例
     */
    public static void main(String[] args) throws Exception {
        // 创建服务端
        RpcServer server = new RpcServer();
        
        // 注册服务
        server.registerService(HelloService.class, new HelloServiceImpl());
        
        // 启动服务
        server.start(8888);
    }
    
    /**
     * 测试服务接口
     */
    public interface HelloService {
        String sayHello(String name);
        int add(int a, int b);
    }
    
    /**
     * 测试服务实现
     */
    public static class HelloServiceImpl implements HelloService {
        @Override
        public String sayHello(String name) {
            return "Hello, " + name + "!";
        }
        
        @Override
        public int add(int a, int b) {
            return a + b;
        }
    }
}
