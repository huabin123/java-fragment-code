package com.fragment.io.nio.project;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NIO聊天室服务器（实战项目）
 * 
 * <p>功能特性：
 * <ul>
 *   <li>支持多客户端同时在线</li>
 *   <li>消息广播：一个客户端发送，所有客户端接收</li>
 *   <li>用户管理：登录、退出、在线列表</li>
 *   <li>私聊功能：@用户名 消息内容</li>
 *   <li>系统命令：/help, /list, /quit</li>
 *   <li>使用主从Reactor模式</li>
 * </ul>
 * 
 * <p>设计要点：
 * <ul>
 *   <li>问题1：如何管理多个客户端？
 *       解决：使用Map存储Channel和用户信息的映射关系</li>
 *   <li>问题2：如何实现消息广播？
 *       解决：遍历所有在线Channel，逐个发送消息</li>
 *   <li>问题3：如何处理半包/粘包？
 *       解决：使用换行符作为消息分隔符，每个Channel维护独立的Buffer</li>
 *   <li>问题4：如何优雅地处理客户端断开？
 *       解决：捕获异常，清理资源，通知其他用户</li>
 * </ul>
 * 
 * @author fragment
 */
public class NIOChatServer {

    private static final int DEFAULT_PORT = 8888;
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");
    
    private Selector selector;
    private ServerSocketChannel serverChannel;
    
    // 用户管理：Channel -> 用户信息
    private Map<SocketChannel, ClientInfo> clients = new ConcurrentHashMap<>();
    
    // 用户名索引：用户名 -> Channel
    private Map<String, SocketChannel> usernameIndex = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        
        try {
            new NIOChatServer().start(port);
        } catch (IOException e) {
            System.err.println("服务器启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 启动聊天服务器
     */
    public void start(int port) throws IOException {
        selector = Selector.open();
        
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║      NIO聊天室服务器启动成功           ║");
        System.out.println("╠════════════════════════════════════════╣");
        System.out.println("║  监听端口: " + port + "                        ║");
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
            
            // 创建客户端信息
            ClientInfo clientInfo = new ClientInfo(clientChannel);
            clients.put(clientChannel, clientInfo);
            
            String address = clientChannel.getRemoteAddress().toString();
            log("新客户端连接: " + address);
            
            // 发送欢迎消息
            sendWelcomeMessage(clientChannel);
        }
    }

    /**
     * 处理读事件
     */
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientInfo clientInfo = clients.get(clientChannel);
        
        if (clientInfo == null) {
            return;
        }
        
        ByteBuffer buffer = clientInfo.getReadBuffer();
        int bytesRead = clientChannel.read(buffer);
        
        if (bytesRead == -1) {
            // 客户端断开连接
            handleClientDisconnect(clientChannel);
            return;
        }
        
        if (bytesRead > 0) {
            buffer.flip();
            
            // 处理接收到的数据（可能包含多条消息）
            while (buffer.hasRemaining()) {
                // 查找换行符
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
                    // 没有完整的消息，等待更多数据
                    buffer.compact();
                    return;
                }
                
                // 读取一行消息
                int length = lineEnd - position;
                byte[] messageBytes = new byte[length];
                buffer.get(messageBytes);
                buffer.get(); // 跳过换行符
                
                String message = new String(messageBytes, CHARSET).trim();
                
                // 处理消息
                if (!message.isEmpty()) {
                    handleMessage(clientChannel, clientInfo, message);
                }
            }
            
            buffer.compact();
        }
    }

    /**
     * 处理客户端消息
     */
    private void handleMessage(SocketChannel clientChannel, ClientInfo clientInfo, String message) 
            throws IOException {
        
        // 如果用户还未登录，将消息作为用户名
        if (clientInfo.getUsername() == null) {
            handleLogin(clientChannel, clientInfo, message);
            return;
        }
        
        // 处理系统命令
        if (message.startsWith("/")) {
            handleCommand(clientChannel, clientInfo, message);
            return;
        }
        
        // 处理私聊
        if (message.startsWith("@")) {
            handlePrivateMessage(clientChannel, clientInfo, message);
            return;
        }
        
        // 广播消息
        broadcastMessage(clientInfo.getUsername(), message);
    }

    /**
     * 处理用户登录
     */
    private void handleLogin(SocketChannel clientChannel, ClientInfo clientInfo, String username) 
            throws IOException {
        
        // 验证用户名
        if (username.length() < 2 || username.length() > 20) {
            sendMessage(clientChannel, "系统提示: 用户名长度必须在2-20个字符之间\n");
            sendMessage(clientChannel, "请重新输入用户名: ");
            return;
        }
        
        if (usernameIndex.containsKey(username)) {
            sendMessage(clientChannel, "系统提示: 用户名已被使用，请重新输入: ");
            return;
        }
        
        // 登录成功
        clientInfo.setUsername(username);
        usernameIndex.put(username, clientChannel);
        
        log("用户登录: " + username + " [" + clientChannel.getRemoteAddress() + "]");
        
        sendMessage(clientChannel, "╔════════════════════════════════════════╗\n");
        sendMessage(clientChannel, "║  登录成功！欢迎来到NIO聊天室          ║\n");
        sendMessage(clientChannel, "╠════════════════════════════════════════╣\n");
        sendMessage(clientChannel, "║  你的昵称: " + username + "\n");
        sendMessage(clientChannel, "║  输入 /help 查看帮助                   ║\n");
        sendMessage(clientChannel, "╚════════════════════════════════════════╝\n");
        
        // 通知其他用户
        broadcastSystemMessage(username + " 加入了聊天室", clientChannel);
    }

    /**
     * 处理系统命令
     */
    private void handleCommand(SocketChannel clientChannel, ClientInfo clientInfo, String command) 
            throws IOException {
        
        String[] parts = command.split("\\s+");
        String cmd = parts[0].toLowerCase();
        
        switch (cmd) {
            case "/help":
                sendHelpMessage(clientChannel);
                break;
                
            case "/list":
                sendOnlineList(clientChannel);
                break;
                
            case "/quit":
                handleClientDisconnect(clientChannel);
                break;
                
            default:
                sendMessage(clientChannel, "系统提示: 未知命令 '" + cmd + "'，输入 /help 查看帮助\n");
        }
    }

    /**
     * 处理私聊消息
     */
    private void handlePrivateMessage(SocketChannel senderChannel, ClientInfo senderInfo, String message) 
            throws IOException {
        
        // 解析私聊消息：@用户名 消息内容
        int spaceIndex = message.indexOf(' ');
        if (spaceIndex == -1) {
            sendMessage(senderChannel, "系统提示: 私聊格式错误，正确格式：@用户名 消息内容\n");
            return;
        }
        
        String targetUsername = message.substring(1, spaceIndex);
        String content = message.substring(spaceIndex + 1).trim();
        
        if (content.isEmpty()) {
            sendMessage(senderChannel, "系统提示: 消息内容不能为空\n");
            return;
        }
        
        SocketChannel targetChannel = usernameIndex.get(targetUsername);
        if (targetChannel == null) {
            sendMessage(senderChannel, "系统提示: 用户 '" + targetUsername + "' 不在线\n");
            return;
        }
        
        // 发送私聊消息
        String timestamp = DATE_FORMAT.format(new Date());
        String privateMsg = String.format("[%s] %s 悄悄对你说: %s\n", 
                                         timestamp, senderInfo.getUsername(), content);
        sendMessage(targetChannel, privateMsg);
        
        // 给发送者确认
        String confirmMsg = String.format("[%s] 你悄悄对 %s 说: %s\n", 
                                         timestamp, targetUsername, content);
        sendMessage(senderChannel, confirmMsg);
        
        log("私聊: " + senderInfo.getUsername() + " -> " + targetUsername + ": " + content);
    }

    /**
     * 广播消息给所有在线用户
     */
    private void broadcastMessage(String senderUsername, String message) {
        String timestamp = DATE_FORMAT.format(new Date());
        String formattedMessage = String.format("[%s] %s: %s\n", timestamp, senderUsername, message);
        
        log("广播: " + senderUsername + ": " + message);
        
        for (Map.Entry<SocketChannel, ClientInfo> entry : clients.entrySet()) {
            SocketChannel channel = entry.getKey();
            ClientInfo info = entry.getValue();
            
            // 已登录的用户才能收到消息
            if (info.getUsername() != null) {
                try {
                    sendMessage(channel, formattedMessage);
                } catch (IOException e) {
                    log("发送消息失败: " + info.getUsername());
                }
            }
        }
    }

    /**
     * 广播系统消息
     */
    private void broadcastSystemMessage(String message, SocketChannel excludeChannel) {
        String timestamp = DATE_FORMAT.format(new Date());
        String formattedMessage = String.format("[%s] 系统提示: %s\n", timestamp, message);
        
        log("系统消息: " + message);
        
        for (Map.Entry<SocketChannel, ClientInfo> entry : clients.entrySet()) {
            SocketChannel channel = entry.getKey();
            ClientInfo info = entry.getValue();
            
            // 排除指定的Channel，且只发送给已登录用户
            if (channel != excludeChannel && info.getUsername() != null) {
                try {
                    sendMessage(channel, formattedMessage);
                } catch (IOException e) {
                    log("发送系统消息失败: " + info.getUsername());
                }
            }
        }
    }

    /**
     * 发送欢迎消息
     */
    private void sendWelcomeMessage(SocketChannel channel) throws IOException {
        sendMessage(channel, "╔════════════════════════════════════════╗\n");
        sendMessage(channel, "║      欢迎来到NIO聊天室                 ║\n");
        sendMessage(channel, "╠════════════════════════════════════════╣\n");
        sendMessage(channel, "║  请输入你的昵称:                       ║\n");
        sendMessage(channel, "╚════════════════════════════════════════╝\n");
    }

    /**
     * 发送帮助信息
     */
    private void sendHelpMessage(SocketChannel channel) throws IOException {
        StringBuilder help = new StringBuilder();
        help.append("╔════════════════════════════════════════╗\n");
        help.append("║           命令帮助                     ║\n");
        help.append("╠════════════════════════════════════════╣\n");
        help.append("║  /help  - 显示此帮助信息               ║\n");
        help.append("║  /list  - 显示在线用户列表             ║\n");
        help.append("║  /quit  - 退出聊天室                   ║\n");
        help.append("║  @用户名 消息 - 发送私聊消息           ║\n");
        help.append("║  直接输入消息 - 发送群聊消息           ║\n");
        help.append("╚════════════════════════════════════════╝\n");
        
        sendMessage(channel, help.toString());
    }

    /**
     * 发送在线用户列表
     */
    private void sendOnlineList(SocketChannel channel) throws IOException {
        StringBuilder list = new StringBuilder();
        list.append("╔════════════════════════════════════════╗\n");
        list.append("║         在线用户列表                   ║\n");
        list.append("╠════════════════════════════════════════╣\n");
        
        int count = 0;
        for (ClientInfo info : clients.values()) {
            if (info.getUsername() != null) {
                count++;
                list.append(String.format("║  %d. %-32s ║\n", count, info.getUsername()));
            }
        }
        
        list.append("╠════════════════════════════════════════╣\n");
        list.append(String.format("║  总计: %d 人在线                       ║\n", count));
        list.append("╚════════════════════════════════════════╝\n");
        
        sendMessage(channel, list.toString());
    }

    /**
     * 处理客户端断开连接
     */
    private void handleClientDisconnect(SocketChannel channel) throws IOException {
        ClientInfo clientInfo = clients.remove(channel);
        
        if (clientInfo != null) {
            String username = clientInfo.getUsername();
            
            if (username != null) {
                usernameIndex.remove(username);
                log("用户退出: " + username);
                
                // 通知其他用户
                broadcastSystemMessage(username + " 离开了聊天室", channel);
            } else {
                log("未登录用户断开连接: " + channel.getRemoteAddress());
            }
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
     * 发送消息到客户端
     */
    private void sendMessage(SocketChannel channel, String message) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes(CHARSET));
        
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    /**
     * 日志输出
     */
    private void log(String message) {
        String timestamp = DATE_FORMAT.format(new Date());
        System.out.println("[" + timestamp + "] " + message);
    }

    /**
     * 客户端信息
     */
    static class ClientInfo {
        private SocketChannel channel;
        private String username;
        private ByteBuffer readBuffer;
        private long connectTime;
        
        public ClientInfo(SocketChannel channel) {
            this.channel = channel;
            this.readBuffer = ByteBuffer.allocate(1024);
            this.connectTime = System.currentTimeMillis();
        }
        
        public SocketChannel getChannel() {
            return channel;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public ByteBuffer getReadBuffer() {
            return readBuffer;
        }
        
        public long getConnectTime() {
            return connectTime;
        }
    }
}
