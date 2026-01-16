package com.fragment.io.bio.demo;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多线程服务器演示（一线程一连接模型）
 * 
 * <p>演示内容：
 * <ul>
 *   <li>为每个连接创建一个新线程</li>
 *   <li>支持多客户端并发</li>
 *   <li>线程数量监控</li>
 *   <li>资源消耗分析</li>
 *   <li>性能测试</li>
 * </ul>
 * 
 * @author fragment
 */
public class MultiThreadServerDemo {

    private static final int PORT = 8081;
    private static final AtomicInteger connectionCount = new AtomicInteger(0);
    private static final AtomicInteger threadCount = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        System.out.println("========== 多线程服务器演示 ==========\n");

        // 启动服务器
        Thread serverThread = new Thread(() -> {
            try {
                runMultiThreadServer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.start();

        // 等待服务器启动
        Thread.sleep(1000);

        // 启动监控线程
        startMonitor();

        // 模拟多个客户端并发连接
        System.out.println("========== 模拟10个并发客户端 ==========\n");
        for (int i = 0; i < 10; i++) {
            final int clientId = i + 1;
            new Thread(() -> runClient(clientId)).start();
            Thread.sleep(100);  // 间隔100ms
        }

        // 等待测试完成
        Thread.sleep(10000);

        System.out.println("\n========== 演示完成 ==========");
        System.out.println("总连接数: " + connectionCount.get());
        System.out.println("当前线程数: " + threadCount.get());
    }

    /**
     * 多线程服务器
     */
    private static void runMultiThreadServer() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("多线程服务器启动在端口 " + PORT);
            System.out.println("模型: 一线程一连接\n");

            while (true) {
                // 等待客户端连接
                Socket socket = serverSocket.accept();
                int connId = connectionCount.incrementAndGet();
                
                System.out.println("[服务器] 接受连接 #" + connId + ": " + 
                                 socket.getRemoteSocketAddress());

                // 为每个连接创建一个新线程
                Thread thread = new Thread(new ClientHandler(socket, connId));
                thread.setName("ClientHandler-" + connId);
                thread.start();
                
                threadCount.incrementAndGet();
            }
        }
    }

    /**
     * 客户端处理器
     */
    static class ClientHandler implements Runnable {
        private Socket socket;
        private int connectionId;

        public ClientHandler(Socket socket, int connectionId) {
            this.socket = socket;
            this.connectionId = connectionId;
        }

        @Override
        public void run() {
            String threadName = Thread.currentThread().getName();
            System.out.println("[" + threadName + "] 开始处理连接 #" + connectionId);

            try (InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {

                socket.setSoTimeout(30000);  // 30秒超时

                byte[] buffer = new byte[1024];
                int len = in.read(buffer);

                if (len > 0) {
                    String request = new String(buffer, 0, len, StandardCharsets.UTF_8);
                    System.out.println("[" + threadName + "] 收到: " + request);

                    // 模拟处理时间
                    Thread.sleep(1000);

                    // 响应
                    String response = "Echo from " + threadName + ": " + request;
                    out.write(response.getBytes(StandardCharsets.UTF_8));
                    out.flush();

                    System.out.println("[" + threadName + "] 处理完成");
                }

            } catch (SocketTimeoutException e) {
                System.err.println("[" + threadName + "] 超时");
            } catch (IOException e) {
                System.err.println("[" + threadName + "] I/O异常: " + e.getMessage());
            } catch (InterruptedException e) {
                System.err.println("[" + threadName + "] 被中断");
                Thread.currentThread().interrupt();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    // 忽略
                }
                threadCount.decrementAndGet();
                System.out.println("[" + threadName + "] 连接关闭");
            }
        }
    }

    /**
     * 客户端
     */
    private static void runClient(int clientId) {
        try (Socket socket = new Socket("localhost", PORT);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            // 发送消息
            String message = "Hello from Client " + clientId;
            out.write(message.getBytes(StandardCharsets.UTF_8));
            out.flush();
            System.out.println("[客户端" + clientId + "] 发送: " + message);

            // 接收响应
            byte[] buffer = new byte[1024];
            int len = in.read(buffer);
            String response = new String(buffer, 0, len, StandardCharsets.UTF_8);
            System.out.println("[客户端" + clientId + "] 收到: " + response);

        } catch (IOException e) {
            System.err.println("[客户端" + clientId + "] 异常: " + e.getMessage());
        }
    }

    /**
     * 启动监控线程
     */
    private static void startMonitor() {
        Thread monitor = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(2000);  // 每2秒打印一次
                    
                    System.out.println("\n========== 服务器状态 ==========");
                    System.out.println("总连接数: " + connectionCount.get());
                    System.out.println("活跃线程数: " + threadCount.get());
                    System.out.println("JVM线程数: " + Thread.activeCount());
                    
                    // 内存使用情况
                    Runtime runtime = Runtime.getRuntime();
                    long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
                    System.out.println("已用内存: " + usedMemory + " MB");
                    System.out.println("================================\n");
                    
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        
        monitor.setDaemon(true);
        monitor.setName("Monitor-Thread");
        monitor.start();
    }
}
