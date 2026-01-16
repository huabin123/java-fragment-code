package com.fragment.io.netty.demo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

import java.util.List;

/**
 * 编解码器演示
 * 
 * 功能：
 * 1. 演示自定义协议的编解码
 * 2. 演示如何解决粘包拆包问题
 * 3. 演示编解码器的使用
 * 
 * 协议格式：
 * ┌──────┬──────┬─────────┐
 * │ 长度 │ 类型 │  数据    │
 * │ 4字节│1字节 │ N字节   │
 * └──────┴──────┴─────────┘
 * 
 * 运行方式：
 * 1. 启动服务端：运行main方法
 * 2. 使用telnet测试：telnet localhost 8082
 * 3. 输入任意内容，观察编解码过程
 * 
 * @author fragment
 * @date 2026-01-14
 */
public class CodecDemo {
    
    private static final int PORT = 8082;
    
    public static void main(String[] args) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                            // 解码器：ByteBuf → Message
                            .addLast("decoder", new MessageDecoder())
                            // 编码器：Message → ByteBuf
                            .addLast("encoder", new MessageEncoder())
                            // 业务处理器
                            .addLast("handler", new MessageHandler());
                    }
                });
            
            ChannelFuture future = bootstrap.bind(PORT).sync();
            System.out.println("编解码演示服务器启动，端口：" + PORT);
            System.out.println("\n协议格式：");
            System.out.println("┌──────┬──────┬─────────┐");
            System.out.println("│ 长度 │ 类型 │  数据    │");
            System.out.println("│ 4字节│1字节 │ N字节   │");
            System.out.println("└──────┴──────┴─────────┘\n");
            
            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
    
    /**
     * 消息对象
     */
    static class Message {
        private byte type;      // 消息类型
        private byte[] data;    // 消息数据
        
        public Message(byte type, byte[] data) {
            this.type = type;
            this.data = data;
        }
        
        public byte getType() {
            return type;
        }
        
        public byte[] getData() {
            return data;
        }
        
        @Override
        public String toString() {
            return "Message{type=" + type + ", data=" + new String(data) + "}";
        }
    }
    
    /**
     * 消息解码器：ByteBuf → Message
     */
    static class MessageDecoder extends ByteToMessageDecoder {
        
        private static final int HEADER_LENGTH = 5;  // 长度(4) + 类型(1)
        
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            System.out.println("\n[解码器] 开始解码，可读字节数: " + in.readableBytes());
            
            // 1. 检查是否有足够的数据读取消息头
            if (in.readableBytes() < HEADER_LENGTH) {
                System.out.println("[解码器] 数据不足，等待更多数据");
                return;
            }
            
            // 2. 标记读指针位置
            in.markReaderIndex();
            
            // 3. 读取长度
            int length = in.readInt();
            System.out.println("[解码器] 读取长度: " + length);
            
            // 4. 读取类型
            byte type = in.readByte();
            System.out.println("[解码器] 读取类型: " + type);
            
            // 5. 检查数据是否完整
            if (in.readableBytes() < length) {
                System.out.println("[解码器] 数据不完整，等待更多数据");
                in.resetReaderIndex();  // 重置读指针
                return;
            }
            
            // 6. 读取数据
            byte[] data = new byte[length];
            in.readBytes(data);
            System.out.println("[解码器] 读取数据: " + new String(data));
            
            // 7. 构造消息对象
            Message message = new Message(type, data);
            out.add(message);
            
            System.out.println("[解码器] 解码完成: " + message);
        }
    }
    
    /**
     * 消息编码器：Message → ByteBuf
     */
    static class MessageEncoder extends MessageToByteEncoder<Message> {
        
        @Override
        protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) {
            System.out.println("\n[编码器] 开始编码: " + msg);
            
            // 1. 写入长度
            out.writeInt(msg.getData().length);
            System.out.println("[编码器] 写入长度: " + msg.getData().length);
            
            // 2. 写入类型
            out.writeByte(msg.getType());
            System.out.println("[编码器] 写入类型: " + msg.getType());
            
            // 3. 写入数据
            out.writeBytes(msg.getData());
            System.out.println("[编码器] 写入数据: " + new String(msg.getData()));
            
            System.out.println("[编码器] 编码完成，总字节数: " + out.readableBytes());
        }
    }
    
    /**
     * 消息处理器
     */
    static class MessageHandler extends SimpleChannelInboundHandler<Message> {
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Message msg) {
            System.out.println("\n[业务处理] 接收到消息: " + msg);
            
            // 处理消息
            String response = "Echo: " + new String(msg.getData());
            
            // 构造响应消息
            Message responseMsg = new Message((byte) 0x02, response.getBytes());
            
            // 发送响应（会经过编码器）
            ctx.writeAndFlush(responseMsg);
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("[异常处理] " + cause.getMessage());
            cause.printStackTrace();
            ctx.close();
        }
    }
}
