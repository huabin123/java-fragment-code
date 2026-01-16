package com.fragment.io.bio.project;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BIO多人聊天室服务器
 * 
 * <p>功能特性：
 * <ul>
 *   <li>支持多用户同时在线</li>
 *   <li>用户登录和昵称设置</li>
 *   <li>消息广播</li>
 *   <li>私聊功能</li>
 *   <li>在线用户列表</li>
 *   <li>用户上下线通知</li>
 * </ul>
 * 
 * @author fragment
 */
public class BIOChatServer {

    private static final int PORT = 9000;
    private static final int THREAD_POOL_SIZE = 100;
    
    // 在线用户管理：昵称 -> 客户端处理器
    private static final Map<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();
    private static final AtomicInteger userIdGenerator = new AtomicInteger(0);

    public static void main(String[] args) {
        System.out.println("========== BIO聊天室服务器 ==========");
        System.out.println("端口: " + PORT);
        System.out.println("线程池大小: " + THREAD_POOL_SIZE);
        System.out.println("====================================\n");

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("聊天室服务器启动成功，等待用户连接...\n");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("新连接: " + socket.getRemoteSocketAddress());

                // 提交到线程池处理
                executor.execute(new ClientHandler(socket));
            }

        } catch (IOException e) {
            System.err.println("服务器异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
    }

    /**
     * 客户端处理器
     */
    static class ClientHandler implements Runnable {
        private Socket socket;
        private String nickname;
        private BufferedReader reader;
        private PrintWriter writer;
        private boolean running = true;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                // 初始化输入输出流
                reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

                // 用户登录
                if (!login()) {
                    return;
                }

                // 通知其他用户
                broadcast(nickname + " 加入了聊天室", null);
                sendUserList();

                // 处理消息
                handleMessages();

            } catch (IOException e) {
                System.err.println("处理客户端异常: " + e.getMessage());
            } finally {
                cleanup();
            }
        }

        /**
         * 用户登录
         */
        private boolean login() throws IOException {
            writer.println("欢迎来到聊天室！请输入您的昵称:");

            String inputNickname = reader.readLine();
            if (inputNickname == null || inputNickname.trim().isEmpty()) {
                writer.println("昵称不能为空，连接关闭");
                return false;
            }

            inputNickname = inputNickname.trim();

            // 检查昵称是否已存在
            if (onlineUsers.containsKey(inputNickname)) {
                writer.println("昵称已被使用，请重新连接");
                return false;
            }

            this.nickname = inputNickname;
            onlineUsers.put(nickname, this);

            writer.println("登录成功！欢迎 " + nickname);
            writer.println("命令列表:");
            writer.println("  @用户名 消息  - 私聊");
            writer.println("  /list        - 查看在线用户");
            writer.println("  /quit        - 退出聊天室");
            writer.println("直接输入消息发送给所有人\n");

            System.out.println(nickname + " 登录成功 (在线: " + onlineUsers.size() + ")");
            return true;
        }

        /**
         * 处理消息
         */
        private void handleMessages() throws IOException {
            String message;
            while (running && (message = reader.readLine()) != null) {
                message = message.trim();

                if (message.isEmpty()) {
                    continue;
                }

                // 处理命令
                if (message.startsWith("/")) {
                    handleCommand(message);
                }
                // 处理私聊
                else if (message.startsWith("@")) {
                    handlePrivateMessage(message);
                }
                // 广播消息
                else {
                    broadcast(nickname + ": " + message, nickname);
                    System.out.println("[广播] " + nickname + ": " + message);
                }
            }
        }

        /**
         * 处理命令
         */
        private void handleCommand(String command) {
            if ("/quit".equalsIgnoreCase(command)) {
                writer.println("再见！");
                running = false;
            } else if ("/list".equalsIgnoreCase(command)) {
                sendUserList();
            } else {
                writer.println("未知命令: " + command);
            }
        }

        /**
         * 处理私聊消息
         */
        private void handlePrivateMessage(String message) {
            // 格式: @用户名 消息内容
            int spaceIndex = message.indexOf(' ');
            if (spaceIndex == -1) {
                writer.println("私聊格式错误，正确格式: @用户名 消息");
                return;
            }

            String targetNickname = message.substring(1, spaceIndex);
            String content = message.substring(spaceIndex + 1);

            ClientHandler target = onlineUsers.get(targetNickname);
            if (target == null) {
                writer.println("用户 " + targetNickname + " 不在线");
                return;
            }

            // 发送给目标用户
            target.sendMessage("[私聊] " + nickname + ": " + content);
            // 回显给自己
            writer.println("[私聊给 " + targetNickname + "]: " + content);

            System.out.println("[私聊] " + nickname + " -> " + targetNickname + ": " + content);
        }

        /**
         * 发送在线用户列表
         */
        private void sendUserList() {
            writer.println("========== 在线用户 (" + onlineUsers.size() + ") ==========");
            for (String user : onlineUsers.keySet()) {
                writer.println("  " + user);
            }
            writer.println("=====================================");
        }

        /**
         * 广播消息给所有用户（除了发送者）
         */
        private void broadcast(String message, String sender) {
            for (Map.Entry<String, ClientHandler> entry : onlineUsers.entrySet()) {
                if (!entry.getKey().equals(sender)) {
                    entry.getValue().sendMessage(message);
                }
            }
        }

        /**
         * 发送消息给当前用户
         */
        public void sendMessage(String message) {
            writer.println(message);
        }

        /**
         * 清理资源
         */
        private void cleanup() {
            running = false;

            if (nickname != null) {
                onlineUsers.remove(nickname);
                broadcast(nickname + " 离开了聊天室", null);
                System.out.println(nickname + " 断开连接 (在线: " + onlineUsers.size() + ")");
            }

            closeQuietly(reader);
            closeQuietly(writer);
            closeQuietly(socket);
        }

        private void closeQuietly(Closeable closeable) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (IOException e) {
                    // 忽略
                }
            }
        }
    }

    /**
     * 简单的聊天客户端（用于测试）
     */
    public static class ChatClient {
        public static void main(String[] args) {
            try (Socket socket = new Socket("localhost", PORT);
                 BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                 BufferedReader console = new BufferedReader(
                     new InputStreamReader(System.in))) {

                // 启动接收消息的线程
                new Thread(() -> {
                    try {
                        String message;
                        while ((message = reader.readLine()) != null) {
                            System.out.println(message);
                        }
                    } catch (IOException e) {
                        System.err.println("接收消息异常: " + e.getMessage());
                    }
                }).start();

                // 发送消息
                String input;
                while ((input = console.readLine()) != null) {
                    writer.println(input);
                    if ("/quit".equalsIgnoreCase(input)) {
                        break;
                    }
                }

            } catch (IOException e) {
                System.err.println("客户端异常: " + e.getMessage());
            }
        }
    }
}
