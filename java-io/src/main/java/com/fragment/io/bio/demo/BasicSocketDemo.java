package com.fragment.io.bio.demo;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Socket基础操作演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>基本的客户端服务器通信</li>
 *   <li>数据读写操作</li>
 *   <li>粘包拆包处理</li>
 *   <li>超时设置</li>
 *   <li>优雅关闭连接</li>
 * </ul>
 * 
 * @author fragment
 */
public class BasicSocketDemo {

    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {
        System.out.println("========== Socket基础操作演示 ==========\n");

        // 启动服务器（在新线程中）
        Thread serverThread = new Thread(() -> {
            try {
                runServer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.start();

        // 等待服务器启动
        Thread.sleep(1000);

        // 演示1：基本通信
        demonstrateBasicCommunication();

        Thread.sleep(1000);

        // 演示2：使用协议处理粘包拆包
        demonstrateProtocol();

        Thread.sleep(1000);

        // 演示3：超时处理
        demonstrateTimeout();

        Thread.sleep(1000);

        System.out.println("\n========== 演示完成 ==========");
    }

    /**
     * 简单的Echo服务器
     */
    private static void runServer() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("服务器启动在端口 " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("\n[服务器] 接受连接：" + socket.getRemoteSocketAddress());

                // 简单处理（实际应该用线程池）
                handleClient(socket);
            }
        }
    }

    /**
     * 处理客户端连接
     */
    private static void handleClient(Socket socket) {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            socket.setSoTimeout(10000);  // 10秒超时

            byte[] buffer = new byte[1024];
            int len = in.read(buffer);

            if (len > 0) {
                String request = new String(buffer, 0, len, StandardCharsets.UTF_8);
                System.out.println("[服务器] 收到: " + request);

                // Echo回写
                String response = "Echo: " + request;
                out.write(response.getBytes(StandardCharsets.UTF_8));
                out.flush();
                System.out.println("[服务器] 发送: " + response);
            }

        } catch (SocketTimeoutException e) {
            System.err.println("[服务器] 超时");
        } catch (IOException e) {
            System.err.println("[服务器] I/O异常: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // 忽略
            }
        }
    }

    /**
     * 演示1：基本通信
     */
    private static void demonstrateBasicCommunication() {
        System.out.println("========== 演示1: 基本通信 ==========\n");

        try (Socket socket = new Socket("localhost", PORT);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            // 发送数据
            String message = "Hello, Server!";
            out.write(message.getBytes(StandardCharsets.UTF_8));
            out.flush();
            System.out.println("[客户端] 发送: " + message);

            // 接收响应
            byte[] buffer = new byte[1024];
            int len = in.read(buffer);
            String response = new String(buffer, 0, len, StandardCharsets.UTF_8);
            System.out.println("[客户端] 收到: " + response);

        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 演示2：使用协议处理粘包拆包
     */
    private static void demonstrateProtocol() {
        System.out.println("========== 演示2: 协议处理粘包拆包 ==========\n");

        try (Socket socket = new Socket("localhost", PORT)) {

            // 使用长度前缀协议
            MessageProtocol protocol = new MessageProtocol(socket);

            // 发送消息
            String message = "使用协议发送的消息";
            protocol.sendMessage(message);
            System.out.println("[客户端] 发送: " + message);

            // 接收响应
            String response = protocol.receiveMessage();
            System.out.println("[客户端] 收到: " + response);

        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 演示3：超时处理
     */
    private static void demonstrateTimeout() {
        System.out.println("========== 演示3: 超时处理 ==========\n");

        try (Socket socket = new Socket("localhost", PORT)) {

            // 设置读超时
            socket.setSoTimeout(2000);  // 2秒超时
            System.out.println("[客户端] 设置读超时: 2秒");

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // 发送数据
            out.write("Test Timeout".getBytes(StandardCharsets.UTF_8));
            out.flush();

            // 读取响应
            byte[] buffer = new byte[1024];
            int len = in.read(buffer);
            String response = new String(buffer, 0, len, StandardCharsets.UTF_8);
            System.out.println("[客户端] 收到: " + response);

        } catch (SocketTimeoutException e) {
            System.err.println("[客户端] 读取超时");
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 消息协议（长度前缀）
     */
    static class MessageProtocol {
        private Socket socket;
        private DataInputStream in;
        private DataOutputStream out;

        public MessageProtocol(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
        }

        /**
         * 发送消息
         * 协议格式：[4字节长度][数据]
         */
        public void sendMessage(String message) throws IOException {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);

            // 写入长度
            out.writeInt(data.length);
            // 写入数据
            out.write(data);
            out.flush();
        }

        /**
         * 接收消息
         */
        public String receiveMessage() throws IOException {
            // 读取长度
            int length = in.readInt();

            // 读取数据
            byte[] data = new byte[length];
            in.readFully(data);  // 确保读取完整数据

            return new String(data, StandardCharsets.UTF_8);
        }
    }

    /**
     * 创建分隔线
     */
    private static String createSeparator(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append("=");
        }
        return sb.toString();
    }
}
