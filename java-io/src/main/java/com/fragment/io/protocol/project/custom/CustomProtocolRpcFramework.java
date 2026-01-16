package com.fragment.io.protocol.project.custom;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

/**
 * 自定义协议RPC框架
 * 
 * 功能特性：
 * 1. 自定义二进制协议
 * 2. 支持同步/异步调用
 * 3. 服务注册与发现
 * 4. 请求响应匹配（通过requestId）
 * 5. 序列化/反序列化（Java原生）
 * 6. 异常传播
 * 7. 超时控制
 * 
 * 协议格式：
 * +-------+-------+-------+----------+----------+----------+----------+
 * | 魔数  | 版本  | 类型  | 请求ID   | 长度     | 数据     | 校验码   |
 * | 2字节 | 1字节 | 1字节 | 8字节    | 4字节    | N字节    | 4字节    |
 * +-------+-------+-------+----------+----------+----------+----------+
 * 
 * @author fragment
 */
public class CustomProtocolRpcFramework {
    
    // 协议常量
    private static final short MAGIC_NUMBER = (short) 0xABCD;
    private static final byte VERSION = 1;
    private static final byte TYPE_REQUEST = 1;
    private static final byte TYPE_RESPONSE = 2;
    
    /**
     * RPC请求
     */
    static class RpcRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private long requestId;
        private String interfaceName;
        private String methodName;
        private Class<?>[] parameterTypes;
        private Object[] parameters;
        
        public RpcRequest() {}
        
        public RpcRequest(long requestId, String interfaceName, String methodName, 
                         Class<?>[] parameterTypes, Object[] parameters) {
            this.requestId = requestId;
            this.interfaceName = interfaceName;
            this.methodName = methodName;
            this.parameterTypes = parameterTypes;
            this.parameters = parameters;
        }
        
        // Getters and Setters
        public long getRequestId() { return requestId; }
        public void setRequestId(long requestId) { this.requestId = requestId; }
        public String getInterfaceName() { return interfaceName; }
        public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }
        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        public Class<?>[] getParameterTypes() { return parameterTypes; }
        public void setParameterTypes(Class<?>[] parameterTypes) { this.parameterTypes = parameterTypes; }
        public Object[] getParameters() { return parameters; }
        public void setParameters(Object[] parameters) { this.parameters = parameters; }
    }
    
    /**
     * RPC响应
     */
    static class RpcResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private long requestId;
        private Object result;
        private Throwable error;
        
        public RpcResponse() {}
        
        public RpcResponse(long requestId, Object result, Throwable error) {
            this.requestId = requestId;
            this.result = result;
            this.error = error;
        }
        
        // Getters and Setters
        public long getRequestId() { return requestId; }
        public void setRequestId(long requestId) { this.requestId = requestId; }
        public Object getResult() { return result; }
        public void setResult(Object result) { this.result = result; }
        public Throwable getError() { return error; }
        public void setError(Throwable error) { this.error = error; }
    }
    
    /**
     * 协议消息
     */
    static class ProtocolMessage {
        private short magicNumber;
        private byte version;
        private byte type;
        private long requestId;
        private int length;
        private byte[] data;
        private int checksum;
        
        public ProtocolMessage() {
            this.magicNumber = MAGIC_NUMBER;
            this.version = VERSION;
        }
        
        public ProtocolMessage(byte type, long requestId, byte[] data) {
            this();
            this.type = type;
            this.requestId = requestId;
            this.data = data;
            this.length = data != null ? data.length : 0;
            this.checksum = calculateChecksum();
        }
        
        private int calculateChecksum() {
            CRC32 crc32 = new CRC32();
            crc32.update(magicNumber);
            crc32.update(version);
            crc32.update(type);
            crc32.update((int) (requestId >> 32));
            crc32.update((int) requestId);
            crc32.update(length);
            if (data != null) {
                crc32.update(data);
            }
            return (int) crc32.getValue();
        }
        
        public boolean verifyChecksum() {
            return this.checksum == calculateChecksum();
        }
        
        // Getters and Setters
        public short getMagicNumber() { return magicNumber; }
        public void setMagicNumber(short magicNumber) { this.magicNumber = magicNumber; }
        public byte getVersion() { return version; }
        public void setVersion(byte version) { this.version = version; }
        public byte getType() { return type; }
        public void setType(byte type) { this.type = type; }
        public long getRequestId() { return requestId; }
        public void setRequestId(long requestId) { this.requestId = requestId; }
        public int getLength() { return length; }
        public void setLength(int length) { this.length = length; }
        public byte[] getData() { return data; }
        public void setData(byte[] data) { this.data = data; this.length = data != null ? data.length : 0; }
        public int getChecksum() { return checksum; }
        public void setChecksum(int checksum) { this.checksum = checksum; }
    }
    
    /**
     * 协议编码器
     */
    static class ProtocolEncoder extends MessageToByteEncoder<ProtocolMessage> {
        @Override
        protected void encode(ChannelHandlerContext ctx, ProtocolMessage msg, ByteBuf out) {
            out.writeShort(msg.getMagicNumber());
            out.writeByte(msg.getVersion());
            out.writeByte(msg.getType());
            out.writeLong(msg.getRequestId());
            out.writeInt(msg.getLength());
            if (msg.getData() != null && msg.getData().length > 0) {
                out.writeBytes(msg.getData());
            }
            out.writeInt(msg.getChecksum());
        }
    }
    
    /**
     * 协议解码器
     */
    static class ProtocolDecoder extends ByteToMessageDecoder {
        private static final int HEADER_LENGTH = 20; // 2+1+1+8+4+4
        
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (in.readableBytes() < HEADER_LENGTH) {
                return;
            }
            
            in.markReaderIndex();
            
            short magicNumber = in.readShort();
            if (magicNumber != MAGIC_NUMBER) {
                in.resetReaderIndex();
                in.skipBytes(1);
                return;
            }
            
            byte version = in.readByte();
            byte type = in.readByte();
            long requestId = in.readLong();
            int length = in.readInt();
            
            if (length < 0 || length > 10 * 1024 * 1024) {
                ctx.close();
                return;
            }
            
            if (in.readableBytes() < length + 4) {
                in.resetReaderIndex();
                return;
            }
            
            byte[] data = null;
            if (length > 0) {
                data = new byte[length];
                in.readBytes(data);
            }
            
            int checksum = in.readInt();
            
            ProtocolMessage message = new ProtocolMessage();
            message.setMagicNumber(magicNumber);
            message.setVersion(version);
            message.setType(type);
            message.setRequestId(requestId);
            message.setLength(length);
            message.setData(data);
            message.setChecksum(checksum);
            
            if (!message.verifyChecksum()) {
                ctx.close();
                return;
            }
            
            out.add(message);
        }
    }
    
    /**
     * RPC服务器
     */
    public static class RpcServer {
        private final int port;
        private final Map<String, Object> serviceMap = new ConcurrentHashMap<>();
        private EventLoopGroup bossGroup;
        private EventLoopGroup workerGroup;
        
        public RpcServer(int port) {
            this.port = port;
        }
        
        /**
         * 注册服务
         */
        public <T> void registerService(Class<T> interfaceClass, T serviceImpl) {
            serviceMap.put(interfaceClass.getName(), serviceImpl);
            System.out.println("[服务器] 注册服务: " + interfaceClass.getName());
        }
        
        /**
         * 启动服务器
         */
        public void start() throws Exception {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            
            try {
                ServerBootstrap bootstrap = new ServerBootstrap();
                bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new ProtocolDecoder());
                            pipeline.addLast(new ProtocolEncoder());
                            pipeline.addLast(new RpcServerHandler(serviceMap));
                        }
                    });
                
                System.out.println("[服务器] RPC服务器启动在端口: " + port);
                ChannelFuture future = bootstrap.bind(port).sync();
                future.channel().closeFuture().sync();
            } finally {
                shutdown();
            }
        }
        
        public void shutdown() {
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
        }
    }
    
    /**
     * RPC服务器处理器
     */
    static class RpcServerHandler extends SimpleChannelInboundHandler<ProtocolMessage> {
        private final Map<String, Object> serviceMap;
        
        public RpcServerHandler(Map<String, Object> serviceMap) {
            this.serviceMap = serviceMap;
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ProtocolMessage msg) {
            if (msg.getType() != TYPE_REQUEST) {
                return;
            }
            
            // 反序列化请求
            RpcRequest request = deserialize(msg.getData(), RpcRequest.class);
            System.out.println("[服务器] 收到RPC请求: " + request.getInterfaceName() + 
                "." + request.getMethodName());
            
            // 处理请求
            RpcResponse response = new RpcResponse();
            response.setRequestId(request.getRequestId());
            
            try {
                Object service = serviceMap.get(request.getInterfaceName());
                if (service == null) {
                    throw new RuntimeException("服务不存在: " + request.getInterfaceName());
                }
                
                Method method = service.getClass().getMethod(
                    request.getMethodName(), request.getParameterTypes());
                Object result = method.invoke(service, request.getParameters());
                
                response.setResult(result);
                System.out.println("[服务器] RPC调用成功，返回结果: " + result);
                
            } catch (Exception e) {
                response.setError(e);
                System.err.println("[服务器] RPC调用失败: " + e.getMessage());
            }
            
            // 序列化响应
            byte[] data = serialize(response);
            ProtocolMessage responseMsg = new ProtocolMessage(TYPE_RESPONSE, request.getRequestId(), data);
            
            ctx.writeAndFlush(responseMsg);
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
    
    /**
     * RPC客户端
     */
    public static class RpcClient {
        private final String host;
        private final int port;
        private EventLoopGroup group;
        private Channel channel;
        private final Map<Long, RpcFuture> futureMap = new ConcurrentHashMap<>();
        
        public RpcClient(String host, int port) {
            this.host = host;
            this.port = port;
        }
        
        /**
         * 连接服务器
         */
        public void connect() throws Exception {
            group = new NioEventLoopGroup();
            
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new ProtocolDecoder());
                        pipeline.addLast(new ProtocolEncoder());
                        pipeline.addLast(new RpcClientHandler(futureMap));
                    }
                });
            
            System.out.println("[客户端] 连接RPC服务器: " + host + ":" + port);
            ChannelFuture future = bootstrap.connect(host, port).sync();
            channel = future.channel();
            System.out.println("[客户端] 连接成功");
        }
        
        /**
         * 创建代理对象
         */
        @SuppressWarnings("unchecked")
        public <T> T createProxy(Class<T> interfaceClass) {
            return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                new RpcInvocationHandler(interfaceClass, this)
            );
        }
        
        /**
         * 发送RPC请求
         */
        public Object sendRequest(RpcRequest request, long timeout) throws Exception {
            // 序列化请求
            byte[] data = serialize(request);
            ProtocolMessage message = new ProtocolMessage(TYPE_REQUEST, request.getRequestId(), data);
            
            // 创建Future
            RpcFuture future = new RpcFuture();
            futureMap.put(request.getRequestId(), future);
            
            // 发送请求
            channel.writeAndFlush(message);
            System.out.println("[客户端] 发送RPC请求: " + request.getInterfaceName() + 
                "." + request.getMethodName());
            
            // 等待响应
            return future.get(timeout);
        }
        
        public void close() {
            if (channel != null) {
                channel.close();
            }
            if (group != null) {
                group.shutdownGracefully();
            }
        }
    }
    
    /**
     * RPC客户端处理器
     */
    static class RpcClientHandler extends SimpleChannelInboundHandler<ProtocolMessage> {
        private final Map<Long, RpcFuture> futureMap;
        
        public RpcClientHandler(Map<Long, RpcFuture> futureMap) {
            this.futureMap = futureMap;
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ProtocolMessage msg) {
            if (msg.getType() != TYPE_RESPONSE) {
                return;
            }
            
            // 反序列化响应
            RpcResponse response = deserialize(msg.getData(), RpcResponse.class);
            
            // 获取对应的Future
            RpcFuture future = futureMap.remove(response.getRequestId());
            if (future != null) {
                future.setResponse(response);
                System.out.println("[客户端] 收到RPC响应");
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
    
    /**
     * RPC调用处理器
     */
    static class RpcInvocationHandler implements InvocationHandler {
        private final Class<?> interfaceClass;
        private final RpcClient client;
        private static long requestIdGenerator = 0;
        
        public RpcInvocationHandler(Class<?> interfaceClass, RpcClient client) {
            this.interfaceClass = interfaceClass;
            this.client = client;
        }
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 构建请求
            RpcRequest request = new RpcRequest(
                ++requestIdGenerator,
                interfaceClass.getName(),
                method.getName(),
                method.getParameterTypes(),
                args
            );
            
            // 发送请求并等待响应
            return client.sendRequest(request, 5000);
        }
    }
    
    /**
     * RPC Future
     */
    static class RpcFuture {
        private RpcResponse response;
        private final CountDownLatch latch = new CountDownLatch(1);
        
        public void setResponse(RpcResponse response) {
            this.response = response;
            latch.countDown();
        }
        
        public Object get(long timeout) throws Exception {
            if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("RPC调用超时");
            }
            
            if (response.getError() != null) {
                throw new RuntimeException("RPC调用失败", response.getError());
            }
            
            return response.getResult();
        }
    }
    
    /**
     * 序列化
     */
    private static byte[] serialize(Object obj) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(obj);
            oos.close();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("序列化失败", e);
        }
    }
    
    /**
     * 反序列化
     */
    @SuppressWarnings("unchecked")
    private static <T> T deserialize(byte[] data, Class<T> clazz) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            ObjectInputStream ois = new ObjectInputStream(bais);
            return (T) ois.readObject();
        } catch (Exception e) {
            throw new RuntimeException("反序列化失败", e);
        }
    }
    
    // ==================== 测试代码 ====================
    
    /**
     * 用户服务接口
     */
    public interface UserService {
        String getUserName(int userId);
        int getUserAge(int userId);
        boolean updateUser(int userId, String name, int age);
    }
    
    /**
     * 用户服务实现
     */
    public static class UserServiceImpl implements UserService {
        @Override
        public String getUserName(int userId) {
            return "用户" + userId;
        }
        
        @Override
        public int getUserAge(int userId) {
            return 20 + userId;
        }
        
        @Override
        public boolean updateUser(int userId, String name, int age) {
            System.out.println("更新用户: userId=" + userId + ", name=" + name + ", age=" + age);
            return true;
        }
    }
    
    /**
     * 主函数
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== 自定义协议RPC框架演示 ===\n");
        
        // 启动服务器
        Thread serverThread = new Thread(() -> {
            try {
                RpcServer server = new RpcServer(8084);
                server.registerService(UserService.class, new UserServiceImpl());
                server.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.start();
        
        // 等待服务器启动
        Thread.sleep(2000);
        
        // 启动客户端
        RpcClient client = new RpcClient("localhost", 8084);
        client.connect();
        
        // 创建代理对象
        UserService userService = client.createProxy(UserService.class);
        
        // 调用远程方法
        System.out.println("\n--- 调用远程方法 ---");
        
        String name = userService.getUserName(1);
        System.out.println("结果: " + name);
        
        Thread.sleep(500);
        
        int age = userService.getUserAge(1);
        System.out.println("结果: " + age);
        
        Thread.sleep(500);
        
        boolean result = userService.updateUser(1, "张三", 25);
        System.out.println("结果: " + result);
        
        Thread.sleep(2000);
        
        // 关闭客户端
        client.close();
        System.exit(0);
    }
}
