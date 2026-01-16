package com.fragment.io.nio.project;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Scanner;

/**
 * NIO文件传输客户端
 * 
 * @author fragment
 */
public class NIOFileClient {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 9999;
    private static final int BUFFER_SIZE = 8192;
    
    private SocketChannel socketChannel;
    private ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        
        if (args.length >= 1) {
            host = args[0];
        }
        if (args.length >= 2) {
            port = Integer.parseInt(args[1]);
        }
        
        try {
            new NIOFileClient().start(host, port);
        } catch (IOException e) {
            System.err.println("客户端启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 启动客户端
     */
    public void start(String host, int port) throws IOException {
        socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress(host, port));
        socketChannel.configureBlocking(true); // 使用阻塞模式简化客户端实现
        
        System.out.println("已连接到服务器 " + host + ":" + port);
        
        // 接收欢迎消息
        receiveResponse();
        receiveResponse();
        
        // 处理用户命令
        handleUserCommands();
    }

    /**
     * 处理用户命令
     */
    private void handleUserCommands() {
        Scanner scanner = new Scanner(System.in);
        
        try {
            while (true) {
                System.out.print("\n请输入命令 (LIST/DOWNLOAD/UPLOAD/QUIT): ");
                String input = scanner.nextLine().trim();
                
                if (input.isEmpty()) {
                    continue;
                }
                
                String[] parts = input.split("\\s+");
                String command = parts[0].toUpperCase();
                
                switch (command) {
                    case "LIST":
                        handleListCommand();
                        break;
                        
                    case "DOWNLOAD":
                        if (parts.length < 2) {
                            System.out.println("用法: DOWNLOAD <文件名>");
                        } else {
                            handleDownloadCommand(parts[1]);
                        }
                        break;
                        
                    case "UPLOAD":
                        if (parts.length < 2) {
                            System.out.println("用法: UPLOAD <本地文件路径>");
                        } else {
                            handleUploadCommand(parts[1]);
                        }
                        break;
                        
                    case "QUIT":
                        sendCommand("QUIT\n");
                        System.out.println("再见！");
                        return;
                        
                    default:
                        System.out.println("未知命令: " + command);
                }
            }
        } finally {
            scanner.close();
            close();
        }
    }

    /**
     * 处理LIST命令
     */
    private void handleListCommand() throws IOException {
        sendCommand("LIST\n");
        
        // 接收文件列表
        while (true) {
            String response = receiveResponse();
            if (response == null || !response.startsWith("OK|")) {
                break;
            }
        }
    }

    /**
     * 处理DOWNLOAD命令
     */
    private void handleDownloadCommand(String filename) throws IOException {
        sendCommand("DOWNLOAD|" + filename + "\n");
        
        // 接收响应
        String response = receiveResponse();
        
        if (response.startsWith("ERROR|")) {
            return;
        }
        
        // 解析文件信息：OK|开始下载|文件名|文件大小
        String[] parts = response.split("\\|");
        if (parts.length < 4) {
            System.out.println("服务器响应格式错误");
            return;
        }
        
        String serverFilename = parts[2];
        long fileSize = Long.parseLong(parts[3].trim());
        
        // 保存到本地
        Path localPath = Paths.get("download_" + serverFilename);
        
        System.out.println("开始下载: " + serverFilename + " (" + fileSize + " 字节)");
        
        try (FileChannel fileChannel = FileChannel.open(
                localPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            
            long received = 0;
            int lastProgress = -1;
            
            while (received < fileSize) {
                readBuffer.clear();
                int bytesRead = socketChannel.read(readBuffer);
                
                if (bytesRead == -1) {
                    System.out.println("连接断开");
                    break;
                }
                
                if (bytesRead > 0) {
                    readBuffer.flip();
                    fileChannel.write(readBuffer);
                    received += bytesRead;
                    
                    // 显示进度
                    int progress = (int) ((received * 100) / fileSize);
                    if (progress % 10 == 0 && progress != lastProgress) {
                        System.out.println("下载进度: " + progress + "%");
                        lastProgress = progress;
                    }
                }
            }
            
            System.out.println("下载完成: " + localPath.toAbsolutePath());
        }
        
        // 接收完成消息
        receiveResponse();
    }

    /**
     * 处理UPLOAD命令
     */
    private void handleUploadCommand(String localFilePath) throws IOException {
        Path filePath = Paths.get(localFilePath);
        
        if (!Files.exists(filePath)) {
            System.out.println("文件不存在: " + localFilePath);
            return;
        }
        
        if (!Files.isRegularFile(filePath)) {
            System.out.println("不是一个文件: " + localFilePath);
            return;
        }
        
        String filename = filePath.getFileName().toString();
        long fileSize = Files.size(filePath);
        
        // 发送上传命令
        sendCommand("UPLOAD|" + filename + "|" + fileSize + "\n");
        
        // 接收响应
        String response = receiveResponse();
        
        if (response.startsWith("ERROR|")) {
            return;
        }
        
        System.out.println("开始上传: " + filename + " (" + fileSize + " 字节)");
        
        // 发送文件内容
        try (FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.READ)) {
            long position = 0;
            int lastProgress = -1;
            
            while (position < fileSize) {
                long transferred = fileChannel.transferTo(
                    position,
                    Math.min(fileSize - position, 1024 * 1024),
                    socketChannel
                );
                
                position += transferred;
                
                // 显示进度
                int progress = (int) ((position * 100) / fileSize);
                if (progress % 10 == 0 && progress != lastProgress) {
                    System.out.println("上传进度: " + progress + "%");
                    lastProgress = progress;
                }
            }
            
            System.out.println("上传完成");
        }
        
        // 接收完成消息
        receiveResponse();
    }

    /**
     * 发送命令
     */
    private void sendCommand(String command) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(command.getBytes(StandardCharsets.UTF_8));
        
        while (buffer.hasRemaining()) {
            socketChannel.write(buffer);
        }
    }

    /**
     * 接收响应
     */
    private String receiveResponse() throws IOException {
        readBuffer.clear();
        
        StringBuilder response = new StringBuilder();
        
        while (true) {
            int bytesRead = socketChannel.read(readBuffer);
            
            if (bytesRead == -1) {
                return null;
            }
            
            if (bytesRead > 0) {
                readBuffer.flip();
                
                while (readBuffer.hasRemaining()) {
                    char c = (char) readBuffer.get();
                    response.append(c);
                    
                    if (c == '\n') {
                        String line = response.toString();
                        System.out.println(line.trim());
                        return line;
                    }
                }
                
                readBuffer.clear();
            }
        }
    }

    /**
     * 关闭客户端
     */
    private void close() {
        try {
            if (socketChannel != null) {
                socketChannel.close();
            }
        } catch (IOException e) {
            System.err.println("关闭连接时发生错误: " + e.getMessage());
        }
    }
}
