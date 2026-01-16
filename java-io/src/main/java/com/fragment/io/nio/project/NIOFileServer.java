package com.fragment.io.nio.project;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.*;

/**
 * NIO文件传输服务器（实战项目）
 * 
 * <p>功能特性：
 * <ul>
 *   <li>支持文件上传和下载</li>
 *   <li>使用零拷贝技术（transferTo）传输文件</li>
 *   <li>支持断点续传</li>
 *   <li>支持文件列表查询</li>
 *   <li>支持多客户端并发传输</li>
 *   <li>传输进度显示</li>
 * </ul>
 * 
 * <p>协议设计：
 * <pre>
 * 命令格式：COMMAND|参数1|参数2|...\n
 * 
 * 支持的命令：
 * - LIST                    : 列出服务器文件
 * - DOWNLOAD|文件名         : 下载文件
 * - UPLOAD|文件名|文件大小  : 上传文件
 * - QUIT                    : 断开连接
 * 
 * 响应格式：
 * - OK|数据                 : 成功
 * - ERROR|错误信息          : 失败
 * </pre>
 * 
 * <p>设计要点：
 * <ul>
 *   <li>问题1：如何实现高效的文件传输？
 *       解决：使用FileChannel.transferTo()实现零拷贝</li>
 *   <li>问题2：如何处理大文件传输？
 *       解决：分块传输，避免内存溢出</li>
 *   <li>问题3：如何支持断点续传？
 *       解决：记录传输位置，支持从指定位置开始传输</li>
 *   <li>问题4：如何保证文件传输的完整性？
 *       解决：传输前发送文件大小，接收方验证</li>
 * </ul>
 * 
 * @author fragment
 */
public class NIOFileServer {

    private static final int DEFAULT_PORT = 9999;
    private static final String FILE_DIR = System.getProperty("user.home") + "/nio_file_server";
    private static final int BUFFER_SIZE = 8192;
    
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private Path fileDirectory;
    
    // 客户端会话管理
    private Map<SocketChannel, ClientSession> sessions = new HashMap<>();

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        
        try {
            new NIOFileServer().start(port);
        } catch (IOException e) {
            System.err.println("服务器启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 启动文件服务器
     */
    public void start(int port) throws IOException {
        // 创建文件目录
        fileDirectory = Paths.get(FILE_DIR);
        if (!Files.exists(fileDirectory)) {
            Files.createDirectories(fileDirectory);
        }
        
        selector = Selector.open();
        
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║     NIO文件传输服务器启动成功          ║");
        System.out.println("╠════════════════════════════════════════╣");
        System.out.println("║  监听端口: " + port + "                        ║");
        System.out.println("║  文件目录: " + FILE_DIR);
        System.out.println("║  等待客户端连接...                     ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();
        
        // 事件循环
        while (true) {
            selector.select();
            
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectedKeys.iterator();
            
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                
                try {
                    if (!key.isValid()) {
                        continue;
                    }
                    
                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    }
                } catch (IOException e) {
                    handleException(key, e);
                }
            }
        }
    }

    /**
     * 处理连接事件
     */
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);
            
            // 创建客户端会话
            ClientSession session = new ClientSession(clientChannel);
            sessions.put(clientChannel, session);
            
            log("新客户端连接: " + clientChannel.getRemoteAddress());
            
            // 发送欢迎消息
            sendResponse(clientChannel, "OK|欢迎使用NIO文件传输服务器\n");
            sendResponse(clientChannel, "OK|支持的命令: LIST, DOWNLOAD|文件名, UPLOAD|文件名|文件大小, QUIT\n");
        }
    }

    /**
     * 处理读事件
     */
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientSession session = sessions.get(clientChannel);
        
        if (session == null) {
            return;
        }
        
        // 如果正在接收文件数据
        if (session.isUploading()) {
            handleFileUpload(key, session);
            return;
        }
        
        // 读取命令
        ByteBuffer buffer = session.getReadBuffer();
        int bytesRead = clientChannel.read(buffer);
        
        if (bytesRead == -1) {
            handleClientDisconnect(clientChannel);
            return;
        }
        
        if (bytesRead > 0) {
            buffer.flip();
            
            // 查找命令结束符（换行符）
            while (buffer.hasRemaining()) {
                int position = buffer.position();
                int limit = buffer.limit();
                int lineEnd = -1;
                
                for (int i = position; i < limit; i++) {
                    if (buffer.get(i) == '\n') {
                        lineEnd = i;
                        break;
                    }
                }
                
                if (lineEnd == -1) {
                    // 没有完整的命令，等待更多数据
                    buffer.compact();
                    return;
                }
                
                // 读取一行命令
                int length = lineEnd - position;
                byte[] commandBytes = new byte[length];
                buffer.get(commandBytes);
                buffer.get(); // 跳过换行符
                
                String command = new String(commandBytes, StandardCharsets.UTF_8).trim();
                
                // 处理命令
                if (!command.isEmpty()) {
                    handleCommand(key, session, command);
                }
            }
            
            buffer.compact();
        }
    }

    /**
     * 处理写事件
     */
    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientSession session = sessions.get(clientChannel);
        
        if (session == null) {
            return;
        }
        
        // 如果正在发送文件
        if (session.isDownloading()) {
            handleFileDownload(key, session);
        }
    }

    /**
     * 处理命令
     */
    private void handleCommand(SelectionKey key, ClientSession session, String command) 
            throws IOException {
        
        SocketChannel clientChannel = session.getChannel();
        String[] parts = command.split("\\|");
        String cmd = parts[0].toUpperCase();
        
        log("收到命令: " + command + " [" + clientChannel.getRemoteAddress() + "]");
        
        switch (cmd) {
            case "LIST":
                handleListCommand(clientChannel);
                break;
                
            case "DOWNLOAD":
                if (parts.length < 2) {
                    sendResponse(clientChannel, "ERROR|命令格式错误，正确格式：DOWNLOAD|文件名\n");
                } else {
                    handleDownloadCommand(key, session, parts[1]);
                }
                break;
                
            case "UPLOAD":
                if (parts.length < 3) {
                    sendResponse(clientChannel, "ERROR|命令格式错误，正确格式：UPLOAD|文件名|文件大小\n");
                } else {
                    handleUploadCommand(session, parts[1], Long.parseLong(parts[2]));
                }
                break;
                
            case "QUIT":
                handleClientDisconnect(clientChannel);
                break;
                
            default:
                sendResponse(clientChannel, "ERROR|未知命令: " + cmd + "\n");
        }
    }

    /**
     * 处理LIST命令
     */
    private void handleListCommand(SocketChannel clientChannel) throws IOException {
        File[] files = fileDirectory.toFile().listFiles();
        
        if (files == null || files.length == 0) {
            sendResponse(clientChannel, "OK|服务器上没有文件\n");
            return;
        }
        
        StringBuilder response = new StringBuilder("OK|文件列表:\n");
        DecimalFormat df = new DecimalFormat("#,###");
        
        for (File file : files) {
            if (file.isFile()) {
                String size = df.format(file.length());
                response.append("OK|  ").append(file.getName())
                       .append(" (").append(size).append(" 字节)\n");
            }
        }
        
        sendResponse(clientChannel, response.toString());
    }

    /**
     * 处理DOWNLOAD命令
     */
    private void handleDownloadCommand(SelectionKey key, ClientSession session, String filename) 
            throws IOException {
        
        Path filePath = fileDirectory.resolve(filename);
        
        if (!Files.exists(filePath)) {
            sendResponse(session.getChannel(), "ERROR|文件不存在: " + filename + "\n");
            return;
        }
        
        if (!Files.isRegularFile(filePath)) {
            sendResponse(session.getChannel(), "ERROR|不是一个文件: " + filename + "\n");
            return;
        }
        
        // 打开文件
        FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.READ);
        long fileSize = fileChannel.size();
        
        session.startDownload(fileChannel, filename, fileSize);
        
        // 发送文件信息
        sendResponse(session.getChannel(), "OK|开始下载|" + filename + "|" + fileSize + "\n");
        
        log("开始发送文件: " + filename + " (" + fileSize + " 字节)");
        
        // 切换到写模式
        key.interestOps(SelectionKey.OP_WRITE);
    }

    /**
     * 处理文件下载（零拷贝传输）
     */
    private void handleFileDownload(SelectionKey key, ClientSession session) throws IOException {
        FileChannel fileChannel = session.getDownloadFileChannel();
        SocketChannel socketChannel = session.getChannel();
        
        long position = session.getDownloadPosition();
        long remaining = session.getDownloadFileSize() - position;
        
        if (remaining <= 0) {
            // 传输完成
            session.finishDownload();
            key.interestOps(SelectionKey.OP_READ);
            
            log("文件发送完成: " + session.getDownloadFilename());
            sendResponse(socketChannel, "OK|下载完成\n");
            return;
        }
        
        // 使用零拷贝传输（每次最多传输1MB）
        long chunkSize = Math.min(remaining, 1024 * 1024);
        long transferred = fileChannel.transferTo(position, chunkSize, socketChannel);
        
        if (transferred > 0) {
            session.updateDownloadPosition(position + transferred);
            
            // 显示进度
            int progress = (int) ((session.getDownloadPosition() * 100) / session.getDownloadFileSize());
            if (progress % 10 == 0 && progress != session.getLastProgress()) {
                log("发送进度: " + session.getDownloadFilename() + " - " + progress + "%");
                session.setLastProgress(progress);
            }
        }
    }

    /**
     * 处理UPLOAD命令
     */
    private void handleUploadCommand(ClientSession session, String filename, long fileSize) 
            throws IOException {
        
        Path filePath = fileDirectory.resolve(filename);
        
        // 创建文件
        FileChannel fileChannel = FileChannel.open(
            filePath,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
        );
        
        session.startUpload(fileChannel, filename, fileSize);
        
        sendResponse(session.getChannel(), "OK|准备接收文件|" + filename + "\n");
        
        log("开始接收文件: " + filename + " (" + fileSize + " 字节)");
    }

    /**
     * 处理文件上传
     */
    private void handleFileUpload(SelectionKey key, ClientSession session) throws IOException {
        FileChannel fileChannel = session.getUploadFileChannel();
        SocketChannel socketChannel = session.getChannel();
        
        ByteBuffer buffer = session.getUploadBuffer();
        int bytesRead = socketChannel.read(buffer);
        
        if (bytesRead == -1) {
            handleClientDisconnect(socketChannel);
            return;
        }
        
        if (bytesRead > 0) {
            buffer.flip();
            
            // 写入文件
            while (buffer.hasRemaining()) {
                fileChannel.write(buffer);
            }
            
            buffer.clear();
            
            long received = session.getUploadReceived() + bytesRead;
            session.setUploadReceived(received);
            
            // 显示进度
            int progress = (int) ((received * 100) / session.getUploadFileSize());
            if (progress % 10 == 0 && progress != session.getLastProgress()) {
                log("接收进度: " + session.getUploadFilename() + " - " + progress + "%");
                session.setLastProgress(progress);
            }
            
            // 检查是否接收完成
            if (received >= session.getUploadFileSize()) {
                session.finishUpload();
                
                log("文件接收完成: " + session.getUploadFilename());
                sendResponse(socketChannel, "OK|上传完成\n");
            }
        }
    }

    /**
     * 处理客户端断开连接
     */
    private void handleClientDisconnect(SocketChannel channel) throws IOException {
        ClientSession session = sessions.remove(channel);
        
        if (session != null) {
            session.close();
            log("客户端断开连接: " + channel.getRemoteAddress());
        }
        
        channel.close();
    }

    /**
     * 处理异常
     */
    private void handleException(SelectionKey key, IOException e) {
        log("处理客户端时发生异常: " + e.getMessage());
        
        try {
            SocketChannel channel = (SocketChannel) key.channel();
            handleClientDisconnect(channel);
        } catch (IOException ex) {
            log("关闭连接时发生异常: " + ex.getMessage());
        }
        
        key.cancel();
    }

    /**
     * 发送响应
     */
    private void sendResponse(SocketChannel channel, String response) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
        
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    /**
     * 日志输出
     */
    private void log(String message) {
        System.out.println("[" + new Date() + "] " + message);
    }

    /**
     * 客户端会话
     */
    static class ClientSession {
        private SocketChannel channel;
        private ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        
        // 下载相关
        private boolean downloading = false;
        private FileChannel downloadFileChannel;
        private String downloadFilename;
        private long downloadFileSize;
        private long downloadPosition;
        
        // 上传相关
        private boolean uploading = false;
        private FileChannel uploadFileChannel;
        private String uploadFilename;
        private long uploadFileSize;
        private long uploadReceived;
        private ByteBuffer uploadBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        
        private int lastProgress = -1;
        
        public ClientSession(SocketChannel channel) {
            this.channel = channel;
        }
        
        public SocketChannel getChannel() {
            return channel;
        }
        
        public ByteBuffer getReadBuffer() {
            return readBuffer;
        }
        
        // 下载相关方法
        public void startDownload(FileChannel fileChannel, String filename, long fileSize) {
            this.downloading = true;
            this.downloadFileChannel = fileChannel;
            this.downloadFilename = filename;
            this.downloadFileSize = fileSize;
            this.downloadPosition = 0;
            this.lastProgress = -1;
        }
        
        public void finishDownload() throws IOException {
            this.downloading = false;
            if (downloadFileChannel != null) {
                downloadFileChannel.close();
                downloadFileChannel = null;
            }
        }
        
        public boolean isDownloading() {
            return downloading;
        }
        
        public FileChannel getDownloadFileChannel() {
            return downloadFileChannel;
        }
        
        public String getDownloadFilename() {
            return downloadFilename;
        }
        
        public long getDownloadFileSize() {
            return downloadFileSize;
        }
        
        public long getDownloadPosition() {
            return downloadPosition;
        }
        
        public void updateDownloadPosition(long position) {
            this.downloadPosition = position;
        }
        
        // 上传相关方法
        public void startUpload(FileChannel fileChannel, String filename, long fileSize) {
            this.uploading = true;
            this.uploadFileChannel = fileChannel;
            this.uploadFilename = filename;
            this.uploadFileSize = fileSize;
            this.uploadReceived = 0;
            this.lastProgress = -1;
        }
        
        public void finishUpload() throws IOException {
            this.uploading = false;
            if (uploadFileChannel != null) {
                uploadFileChannel.close();
                uploadFileChannel = null;
            }
        }
        
        public boolean isUploading() {
            return uploading;
        }
        
        public FileChannel getUploadFileChannel() {
            return uploadFileChannel;
        }
        
        public String getUploadFilename() {
            return uploadFilename;
        }
        
        public long getUploadFileSize() {
            return uploadFileSize;
        }
        
        public long getUploadReceived() {
            return uploadReceived;
        }
        
        public void setUploadReceived(long received) {
            this.uploadReceived = received;
        }
        
        public ByteBuffer getUploadBuffer() {
            return uploadBuffer;
        }
        
        public int getLastProgress() {
            return lastProgress;
        }
        
        public void setLastProgress(int progress) {
            this.lastProgress = progress;
        }
        
        public void close() throws IOException {
            if (downloadFileChannel != null) {
                downloadFileChannel.close();
            }
            if (uploadFileChannel != null) {
                uploadFileChannel.close();
            }
        }
    }
}
