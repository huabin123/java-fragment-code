package com.fragment.io.netty.project.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * HTTP文件服务器
 * 
 * 功能：
 * 1. 文件浏览
 * 2. 文件下载
 * 3. 零拷贝传输
 * 4. 目录列表
 * 
 * 使用方式：
 * 1. 修改FILE_ROOT为你的文件目录
 * 2. 启动服务器
 * 3. 浏览器访问：http://localhost:8888/
 * 
 * @author fragment
 * @date 2026-01-14
 */
public class HttpFileServer {
    
    // 文件根目录（请修改为实际路径）
    private static final String FILE_ROOT = System.getProperty("user.home");
    private static final int PORT = 8888;
    
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
                            .addLast(new HttpServerCodec())
                            .addLast(new HttpObjectAggregator(65536))
                            .addLast(new ChunkedWriteHandler())
                            .addLast(new HttpFileServerHandler(FILE_ROOT));
                    }
                });
            
            ChannelFuture future = bootstrap.bind(PORT).sync();
            System.out.println("=== HTTP文件服务器启动成功 ===");
            System.out.println("访问地址: http://localhost:" + PORT + "/");
            System.out.println("文件目录: " + FILE_ROOT);
            System.out.println("============================\n");
            
            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
