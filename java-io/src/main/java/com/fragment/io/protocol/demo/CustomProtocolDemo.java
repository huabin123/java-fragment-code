package com.fragment.io.protocol.demo;

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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;

/**
 * 自定义协议演示
 * 
 * 演示内容：
 * 1. 自定义协议设计（魔数、版本、消息类型、序列号、长度、数据、校验）
 * 2. 协议编解码器实现
 * 3. CRC32校验
 * 4. 协议版本兼容
 * 5. 请求响应模型
 * 
 * 协议格式：
 * +-------+-------+-------+----------+--------+----------+----------+
 * | 魔数  | 版本  | 类型  | 序列号   | 长度   | 数据     | 校验码   |
 * | 2字节 | 1字节 | 1字节 | 4字节    | 4字节  | N字节    | 4字节    |
 * +-------+-------+-------+----------+--------+----------+----------+
 * 
 * @author fragment
 */
public class CustomProtocolDemo {
    
    // 协议常量
    private static final short MAGIC_NUMBER = (short) 0xCAFE;
    private static final byte VERSION = 1;
    
    // 消息类型
    private static final byte TYPE_REQUEST = 1;
    private static final byte TYPE_RESPONSE = 2;
    private static final byte TYPE_HEARTBEAT = 3;

    public static void main(String[] args) throws Exception {
        System.out.println("=== 自定义协议演示 ===\n");
        
        // 启动服务器
        CustomProtocolServer server = new CustomProtocolServer(8083);
        new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        
        // 等待服务器启动
        Thread.sleep(1000);
        
        // 启动客户端
        CustomProtocolClient client = new CustomProtocolClient("localhost", 8083);
        client.connect();
        
        // 等待连接建立
        Thread.sleep(500);
        
        // 发送请求消息
        System.out.println("\n--- 发送请求消息 ---");
        client.sendRequest(1, "查询用户信息");
        Thread.sleep(500);
        
        client.sendRequest(2, "更新用户数据");
        Thread.sleep(500);
        
        // 发送心跳消息
        System.out.println("\n--- 发送心跳消息 ---");
        client.sendHeartbeat(3);
        Thread.sleep(500);
        
        // 关闭连接
        System.out.println("\n--- 关闭连接 ---");
        client.close();
        
        Thread.sleep(1000);
        server.stop();
    }
    
    /**
     * 自定义协议消息
     */
    static class ProtocolMessage {
        private short magicNumber;      // 魔数
        private byte version;           // 版本
        private byte type;              // 类型
        private int sequenceId;         // 序列号
        private int length;             // 数据长度
        private byte[] data;            // 数据
        private int checksum;           // 校验码
        
        public ProtocolMessage() {
            this.magicNumber = MAGIC_NUMBER;
            this.version = VERSION;
        }
        
        public ProtocolMessage(byte type, int sequenceId, byte[] data) {
            this();
            this.type = type;
            this.sequenceId = sequenceId;
            this.data = data;
            this.length = data != null ? data.length : 0;
            this.checksum = calculateChecksum();
        }
        
        /**
         * 计算校验码
         */
        private int calculateChecksum() {
            CRC32 crc32 = new CRC32();
            crc32.update(magicNumber);
            crc32.update(version);
            crc32.update(type);
            crc32.update(sequenceId);
            crc32.update(length);
            if (data != null) {
                crc32.update(data);
            }
            return (int) crc32.getValue();
        }
        
        /**
         * 验证校验码
         */
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
        public int getSequenceId() { return sequenceId; }
        public void setSequenceId(int sequenceId) { this.sequenceId = sequenceId; }
        public int getLength() { return length; }
        public void setLength(int length) { this.length = length; }
        public byte[] getData() { return data; }
        public void setData(byte[] data) { this.data = data; this.length = data != null ? data.length : 0; }
        public int getChecksum() { return checksum; }
        public void setChecksum(int checksum) { this.checksum = checksum; }
        
        @Override
        public String toString() {
            String dataStr = data != null ? new String(data, StandardCharsets.UTF_8) : "null";
            return String.format("ProtocolMessage{magic=0x%04X, version=%d, type=%d, seq=%d, length=%d, data='%s', checksum=0x%08X}",
                magicNumber, version, type, sequenceId, length, dataStr, checksum);
        }
    }
    
    /**
     * 协议编码器
     */
    static class ProtocolEncoder extends MessageToByteEncoder<ProtocolMessage> {
        
        @Override
        protected void encode(ChannelHandlerContext ctx, ProtocolMessage msg, ByteBuf out) {
            System.out.println("[编码器] 编码消息: " + msg);
            
            // 写入魔数
            out.writeShort(msg.getMagicNumber());
            // 写入版本
            out.writeByte(msg.getVersion());
            // 写入类型
            out.writeByte(msg.getType());
            // 写入序列号
            out.writeInt(msg.getSequenceId());
            // 写入长度
            out.writeInt(msg.getLength());
            // 写入数据
            if (msg.getData() != null && msg.getData().length > 0) {
                out.writeBytes(msg.getData());
            }
            // 写入校验码
            out.writeInt(msg.getChecksum());
        }
    }
    
    /**
     * 协议解码器
     */
    static class ProtocolDecoder extends ByteToMessageDecoder {
        
        // 协议头长度：魔数(2) + 版本(1) + 类型(1) + 序列号(4) + 长度(4) + 校验码(4) = 16字节
        private static final int HEADER_LENGTH = 16;
        
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            // 可读字节数不足，等待更多数据
            if (in.readableBytes() < HEADER_LENGTH) {
                return;
            }
            
            // 标记读指针位置
            in.markReaderIndex();
            
            // 读取魔数
            short magicNumber = in.readShort();
            if (magicNumber != MAGIC_NUMBER) {
                System.err.println("[解码器] 魔数错误: 0x" + Integer.toHexString(magicNumber));
                in.resetReaderIndex();
                in.skipBytes(1); // 跳过一个字节继续寻找
                return;
            }
            
            // 读取版本
            byte version = in.readByte();
            if (version != VERSION) {
                System.err.println("[解码器] 版本不支持: " + version);
                ctx.close();
                return;
            }
            
            // 读取类型
            byte type = in.readByte();
            
            // 读取序列号
            int sequenceId = in.readInt();
            
            // 读取长度
            int length = in.readInt();
            
            // 检查数据长度是否合法
            if (length < 0 || length > 1024 * 1024) { // 限制最大1MB
                System.err.println("[解码器] 数据长度非法: " + length);
                ctx.close();
                return;
            }
            
            // 检查可读字节数是否足够
            if (in.readableBytes() < length + 4) { // 数据 + 校验码
                in.resetReaderIndex();
                return;
            }
            
            // 读取数据
            byte[] data = null;
            if (length > 0) {
                data = new byte[length];
                in.readBytes(data);
            }
            
            // 读取校验码
            int checksum = in.readInt();
            
            // 构建消息对象
            ProtocolMessage message = new ProtocolMessage();
            message.setMagicNumber(magicNumber);
            message.setVersion(version);
            message.setType(type);
            message.setSequenceId(sequenceId);
            message.setLength(length);
            message.setData(data);
            message.setChecksum(checksum);
            
            // 验证校验码
            if (!message.verifyChecksum()) {
                System.err.println("[解码器] 校验码错误");
                ctx.close();
                return;
            }
            
            System.out.println("[解码器] 解码消息: " + message);
            out.add(message);
        }
    }
    
    /**
     * 自定义协议服务器
     */
    static class CustomProtocolServer {
        private final int port;
        private EventLoopGroup bossGroup;
        private EventLoopGroup workerGroup;
        private Channel serverChannel;
        
        public CustomProtocolServer(int port) {
            this.port = port;
        }
        
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
                            
                            // 添加编解码器
                            pipeline.addLast(new ProtocolDecoder());
                            pipeline.addLast(new ProtocolEncoder());
                            
                            // 添加业务处理器
                            pipeline.addLast(new ServerHandler());
                        }
                    });
                
                System.out.println("自定义协议服务器启动在端口: " + port);
                ChannelFuture future = bootstrap.bind(port).sync();
                serverChannel = future.channel();
                
                serverChannel.closeFuture().sync();
            } finally {
                workerGroup.shutdownGracefully();
                bossGroup.shutdownGracefully();
            }
        }
        
        public void stop() {
            if (serverChannel != null) {
                serverChannel.close();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
        }
    }
    
    /**
     * 服务器处理器
     */
    static class ServerHandler extends SimpleChannelInboundHandler<ProtocolMessage> {
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ProtocolMessage msg) {
            System.out.println("\n[服务器] 收到消息: " + msg);
            
            // 根据消息类型处理
            if (msg.getType() == TYPE_REQUEST) {
                // 处理请求，返回响应
                String requestData = new String(msg.getData(), StandardCharsets.UTF_8);
                String responseData = "处理结果: " + requestData;
                
                ProtocolMessage response = new ProtocolMessage(
                    TYPE_RESPONSE,
                    msg.getSequenceId(),
                    responseData.getBytes(StandardCharsets.UTF_8)
                );
                
                ctx.writeAndFlush(response);
                
            } else if (msg.getType() == TYPE_HEARTBEAT) {
                // 心跳响应
                System.out.println("[服务器] 收到心跳，回复心跳");
                ProtocolMessage heartbeat = new ProtocolMessage(
                    TYPE_HEARTBEAT,
                    msg.getSequenceId(),
                    new byte[0]
                );
                ctx.writeAndFlush(heartbeat);
            }
        }
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            System.out.println("[服务器] 客户端连接: " + ctx.channel().remoteAddress());
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            System.out.println("[服务器] 客户端断开: " + ctx.channel().remoteAddress());
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[服务器] 异常: " + cause.getMessage());
            ctx.close();
        }
    }
    
    /**
     * 自定义协议客户端
     */
    static class CustomProtocolClient {
        private final String host;
        private final int port;
        private EventLoopGroup group;
        private Channel channel;
        
        public CustomProtocolClient(String host, int port) {
            this.host = host;
            this.port = port;
        }
        
        public void connect() throws Exception {
            group = new NioEventLoopGroup();
            
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        
                        // 添加编解码器
                        pipeline.addLast(new ProtocolDecoder());
                        pipeline.addLast(new ProtocolEncoder());
                        
                        // 添加业务处理器
                        pipeline.addLast(new ClientHandler());
                    }
                });
            
            System.out.println("[客户端] 连接服务器: " + host + ":" + port);
            ChannelFuture future = bootstrap.connect(host, port).sync();
            channel = future.channel();
        }
        
        public void sendRequest(int sequenceId, String data) {
            if (channel != null && channel.isActive()) {
                ProtocolMessage message = new ProtocolMessage(
                    TYPE_REQUEST,
                    sequenceId,
                    data.getBytes(StandardCharsets.UTF_8)
                );
                System.out.println("[客户端] 发送请求: " + data);
                channel.writeAndFlush(message);
            }
        }
        
        public void sendHeartbeat(int sequenceId) {
            if (channel != null && channel.isActive()) {
                ProtocolMessage message = new ProtocolMessage(
                    TYPE_HEARTBEAT,
                    sequenceId,
                    new byte[0]
                );
                System.out.println("[客户端] 发送心跳");
                channel.writeAndFlush(message);
            }
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
     * 客户端处理器
     */
    static class ClientHandler extends SimpleChannelInboundHandler<ProtocolMessage> {
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ProtocolMessage msg) {
            System.out.println("\n[客户端] 收到响应: " + msg);
            
            if (msg.getType() == TYPE_RESPONSE) {
                String responseData = new String(msg.getData(), StandardCharsets.UTF_8);
                System.out.println("[客户端] 响应内容: " + responseData);
            } else if (msg.getType() == TYPE_HEARTBEAT) {
                System.out.println("[客户端] 收到心跳响应");
            }
        }
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            System.out.println("[客户端] 连接建立成功");
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            System.out.println("[客户端] 连接已断开");
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[客户端] 异常: " + cause.getMessage());
            ctx.close();
        }
    }
}
