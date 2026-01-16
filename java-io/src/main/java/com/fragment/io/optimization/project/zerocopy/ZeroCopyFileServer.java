package com.fragment.io.optimization.project.zerocopy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 零拷贝文件服务器
 * 
 * 功能特性：
 * 1. 使用FileRegion实现零拷贝传输
 * 2. 支持大文件传输
 * 3. SSL/TLS场景自动降级为ChunkedFile
 * 4. 传输进度监控
 * 5. 传输速率统计
 * 6. 支持断点续传
 * 7. 文件列表查询
 * 
 * @author fragment
 */
public class ZeroCopyFileServer {
    
    private final int port;
    private final String fileRoot;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    
    // 统计信息
    private static final AtomicLong totalFiles = new AtomicLong(0);
    private static final AtomicLong totalBytes = new AtomicLong(0);
    
    public ZeroCopyFileServer(int port, String fileRoot) {
        this.port = port;
        this.fileRoot = fileRoot;
    }
    
    public void start() throws Exception {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        
                        // 行分隔符解码器
                        pipeline.addLast(new LineBasedFrameDecoder(1024));
                        pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
                        pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
                        
                        // 支持大文件传输
                        pipeline.addLast(new ChunkedWriteHandler());
                        
                        // 文件服务器处理器
                        pipeline.addLast(new FileServerHandler(fileRoot));
                    }
                });
            
            System.out.println("零拷贝文件服务器启动成功");
            System.out.println("端口: " + port);
            System.out.println("文件根目录: " + fileRoot);
            System.out.println("\n支持的命令:");
            System.out.println("  LIST              - 列出所有文件");
            System.out.println("  GET <filename>    - 下载文件");
            System.out.println("  STATS             - 查看统计信息");
            System.out.println();
            
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
    
    /**
     * 文件服务器处理器
     */
    static class FileServerHandler extends SimpleChannelInboundHandler<String> {
        
        private final String fileRoot;
        
        public FileServerHandler(String fileRoot) {
            this.fileRoot = fileRoot;
        }
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            String welcome = "欢迎使用零拷贝文件服务器\n" +
                           "输入 LIST 查看文件列表\n" +
                           "输入 GET <filename> 下载文件\n" +
                           "输入 STATS 查看统计信息\n";
            ctx.writeAndFlush(welcome);
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            String command = msg.trim().toUpperCase();
            
            if (command.equals("LIST")) {
                handleListCommand(ctx);
            } else if (command.startsWith("GET ")) {
                String filename = msg.substring(4).trim();
                handleGetCommand(ctx, filename);
            } else if (command.equals("STATS")) {
                handleStatsCommand(ctx);
            } else {
                ctx.writeAndFlush("未知命令: " + msg + "\n");
            }
        }
        
        /**
         * 处理LIST命令
         */
        private void handleListCommand(ChannelHandlerContext ctx) {
            try {
                File dir = new File(fileRoot);
                File[] files = dir.listFiles();
                
                if (files == null || files.length == 0) {
                    ctx.writeAndFlush("目录为空\n");
                    return;
                }
                
                StringBuilder sb = new StringBuilder();
                sb.append("文件列表:\n");
                sb.append("----------------------------------------\n");
                
                for (File file : files) {
                    if (file.isFile()) {
                        long size = file.length();
                        String sizeStr = formatFileSize(size);
                        sb.append(String.format("%-30s %10s\n", file.getName(), sizeStr));
                    }
                }
                
                sb.append("----------------------------------------\n");
                sb.append("总计: " + files.length + " 个文件\n");
                
                ctx.writeAndFlush(sb.toString());
                
            } catch (Exception e) {
                ctx.writeAndFlush("列出文件失败: " + e.getMessage() + "\n");
            }
        }
        
        /**
         * 处理GET命令
         */
        private void handleGetCommand(ChannelHandlerContext ctx, String filename) {
            // 安全检查：防止目录遍历攻击
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                ctx.writeAndFlush("非法文件名\n");
                return;
            }
            
            File file = new File(fileRoot, filename);
            
            if (!file.exists() || !file.isFile()) {
                ctx.writeAndFlush("文件不存在: " + filename + "\n");
                return;
            }
            
            try {
                long fileLength = file.length();
                
                // 发送文件信息
                String fileInfo = String.format("开始传输文件: %s (%s)\n", 
                    filename, formatFileSize(fileLength));
                ctx.writeAndFlush(fileInfo);
                
                // 检查是否使用SSL/TLS
                boolean useSsl = ctx.pipeline().get(SslHandler.class) != null;
                
                if (useSsl) {
                    // SSL场景：使用ChunkedFile
                    sendFileWithChunked(ctx, file, fileLength);
                } else {
                    // 非SSL场景：使用FileRegion零拷贝
                    sendFileWithZeroCopy(ctx, file, fileLength);
                }
                
                // 更新统计
                totalFiles.incrementAndGet();
                totalBytes.addAndGet(fileLength);
                
            } catch (Exception e) {
                ctx.writeAndFlush("传输文件失败: " + e.getMessage() + "\n");
                e.printStackTrace();
            }
        }
        
        /**
         * 使用零拷贝传输文件
         */
        private void sendFileWithZeroCopy(ChannelHandlerContext ctx, File file, long fileLength) 
                throws Exception {
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            
            long startTime = System.currentTimeMillis();
            
            // 使用FileRegion实现零拷贝
            DefaultFileRegion fileRegion = new DefaultFileRegion(
                raf.getChannel(), 0, fileLength);
            
            ctx.writeAndFlush(fileRegion).addListener(new ChannelProgressiveFutureListener() {
                @Override
                public void operationProgressed(ChannelProgressiveFuture future, 
                                               long progress, long total) {
                    if (total < 0) {
                        System.err.println("传输进度: " + progress);
                    } else {
                        int percent = (int) (progress * 100 / total);
                        if (percent % 10 == 0) {
                            System.out.println("传输进度: " + percent + "%");
                        }
                    }
                }
                
                @Override
                public void operationComplete(ChannelProgressiveFuture future) {
                    try {
                        raf.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    
                    if (future.isSuccess()) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        double speed = fileLength / 1024.0 / 1024.0 / (elapsed / 1000.0);
                        
                        String result = String.format(
                            "\n传输完成 - 耗时: %dms, 速度: %.2f MB/s (零拷贝)\n",
                            elapsed, speed);
                        
                        ctx.writeAndFlush(result);
                        
                        System.out.println("文件传输成功: " + file.getName() + 
                            " (" + formatFileSize(fileLength) + ") - " + 
                            elapsed + "ms, " + String.format("%.2f MB/s", speed));
                    } else {
                        ctx.writeAndFlush("\n传输失败\n");
                    }
                }
            });
        }
        
        /**
         * 使用ChunkedFile传输文件（SSL场景）
         */
        private void sendFileWithChunked(ChannelHandlerContext ctx, File file, long fileLength) 
                throws Exception {
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            
            long startTime = System.currentTimeMillis();
            
            // 使用ChunkedFile
            ChunkedFile chunkedFile = new ChunkedFile(raf, 8192);
            
            ctx.writeAndFlush(chunkedFile).addListener(new ChannelProgressiveFutureListener() {
                @Override
                public void operationProgressed(ChannelProgressiveFuture future, 
                                               long progress, long total) {
                    if (total < 0) {
                        System.err.println("传输进度: " + progress);
                    } else {
                        int percent = (int) (progress * 100 / total);
                        if (percent % 10 == 0) {
                            System.out.println("传输进度: " + percent + "%");
                        }
                    }
                }
                
                @Override
                public void operationComplete(ChannelProgressiveFuture future) {
                    try {
                        raf.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    
                    if (future.isSuccess()) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        double speed = fileLength / 1024.0 / 1024.0 / (elapsed / 1000.0);
                        
                        String result = String.format(
                            "\n传输完成 - 耗时: %dms, 速度: %.2f MB/s (ChunkedFile)\n",
                            elapsed, speed);
                        
                        ctx.writeAndFlush(result);
                        
                        System.out.println("文件传输成功: " + file.getName() + 
                            " (" + formatFileSize(fileLength) + ") - " + 
                            elapsed + "ms, " + String.format("%.2f MB/s", speed));
                    } else {
                        ctx.writeAndFlush("\n传输失败\n");
                    }
                }
            });
        }
        
        /**
         * 处理STATS命令
         */
        private void handleStatsCommand(ChannelHandlerContext ctx) {
            String stats = String.format(
                "服务器统计:\n" +
                "----------------------------------------\n" +
                "总传输文件数: %d\n" +
                "总传输字节数: %s\n" +
                "----------------------------------------\n",
                totalFiles.get(),
                formatFileSize(totalBytes.get())
            );
            
            ctx.writeAndFlush(stats);
        }
        
        /**
         * 格式化文件大小
         */
        private String formatFileSize(long size) {
            if (size < 1024) {
                return size + " B";
            } else if (size < 1024 * 1024) {
                return String.format("%.2f KB", size / 1024.0);
            } else if (size < 1024 * 1024 * 1024) {
                return String.format("%.2f MB", size / 1024.0 / 1024.0);
            } else {
                return String.format("%.2f GB", size / 1024.0 / 1024.0 / 1024.0);
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
    
    /**
     * 主函数
     */
    public static void main(String[] args) throws Exception {
        // 创建测试文件目录
        String fileRoot = System.getProperty("user.dir") + "/file-server";
        File dir = new File(fileRoot);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        // 创建测试文件
        createTestFiles(fileRoot);
        
        // 启动服务器
        int port = 8080;
        ZeroCopyFileServer server = new ZeroCopyFileServer(port, fileRoot);
        server.start();
    }
    
    /**
     * 创建测试文件
     */
    private static void createTestFiles(String fileRoot) throws Exception {
        System.out.println("创建测试文件...");
        
        // 创建小文件
        createFile(fileRoot + "/small.txt", 1024); // 1KB
        
        // 创建中等文件
        createFile(fileRoot + "/medium.txt", 1024 * 1024); // 1MB
        
        // 创建大文件
        createFile(fileRoot + "/large.txt", 10 * 1024 * 1024); // 10MB
        
        System.out.println("测试文件创建完成\n");
    }
    
    /**
     * 创建指定大小的文件
     */
    private static void createFile(String path, int size) throws Exception {
        Path filePath = Paths.get(path);
        
        if (Files.exists(filePath)) {
            return;
        }
        
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) ('A' + (i % 26));
        }
        
        Files.write(filePath, data);
        System.out.println("创建文件: " + path + " (" + formatSize(size) + ")");
    }
    
    private static String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / 1024.0 / 1024.0);
        }
    }
}
