package com.fragment.io.bio.demo;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池服务器演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>使用线程池处理连接</li>
 *   <li>线程复用，减少创建销毁开销</li>
 *   <li>控制并发数量</li>
 *   <li>任务队列管理</li>
 *   <li>拒绝策略处理</li>
 *   <li>性能对比</li>
 * </ul>
 * 
 * @author fragment
 */
public class ThreadPoolServerDemo {

    private static final int PORT = 8082;
    private static final int CORE_POOL_SIZE = 10;
    private static final int MAX_POOL_SIZE = 50;
    private static final int QUEUE_SIZE = 100;
    
    private static final AtomicInteger connectionCount = new AtomicInteger(0);
    private static final AtomicInteger processedCount = new AtomicInteger(0);
    private static final AtomicInteger rejectedCount = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        System.out.println("========== 线程池服务器演示 ==========\n");

        // 创建自定义线程池
        ThreadPoolExecutor executor = createThreadPool();

        // 启动服务器
        Thread serverThread = new Thread(() -> {
            try {
                runThreadPoolServer(executor);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.start();

        // 等待服务器启动
        Thread.sleep(1000);

        // 启动监控
        startMonitor(executor);

        // 模拟大量并发客户端
        System.out.println("========== 模拟100个并发客户端 ==========\n");
        for (int i = 0; i < 100; i++) {
            final int clientId = i + 1;
            new Thread(() -> runClient(clientId)).start();
            Thread.sleep(50);  // 间隔50ms
        }

        // 等待测试完成
        Thread.sleep(15000);

        // 打印最终统计
        System.out.println("\n========== 最终统计 ==========");
        System.out.println("总连接数: " + connectionCount.get());
        System.out.println("已处理: " + processedCount.get());
        System.out.println("已拒绝: " + rejectedCount.get());
        System.out.println("成功率: " + String.format("%.2f%%", 
            processedCount.get() * 100.0 / connectionCount.get()));

        // 关闭线程池
        shutdownGracefully(executor);
        
        System.out.println("\n========== 演示完成 ==========");
    }

    /**
     * 创建自定义线程池
     */
    private static ThreadPoolExecutor createThreadPool() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(QUEUE_SIZE),
            new ThreadFactory() {
                private AtomicInteger count = new AtomicInteger(0);
                
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("Worker-" + count.incrementAndGet());
                    thread.setDaemon(false);
                    return thread;
                }
            },
            new RejectedExecutionHandler() {
                @Override
                public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                    rejectedCount.incrementAndGet();
                    System.err.println("任务被拒绝，队列已满");
                    
                    // 关闭连接
                    if (r instanceof ClientTask) {
                        ClientTask task = (ClientTask) r;
                        try {
                            task.socket.close();
                        } catch (IOException e) {
                            // 忽略
                        }
                    }
                }
            }
        );

        System.out.println("线程池配置:");
        System.out.println("  核心线程数: " + CORE_POOL_SIZE);
        System.out.println("  最大线程数: " + MAX_POOL_SIZE);
        System.out.println("  队列大小: " + QUEUE_SIZE);
        System.out.println();

        return executor;
    }

    /**
     * 线程池服务器
     */
    private static void runThreadPoolServer(ThreadPoolExecutor executor) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("线程池服务器启动在端口 " + PORT + "\n");

            while (true) {
                Socket socket = serverSocket.accept();
                int connId = connectionCount.incrementAndGet();

                try {
                    // 提交任务到线程池
                    executor.execute(new ClientTask(socket, connId));
                } catch (RejectedExecutionException e) {
                    // 任务被拒绝
                    rejectedCount.incrementAndGet();
                    socket.close();
                }
            }
        }
    }

    /**
     * 客户端任务
     */
    static class ClientTask implements Runnable {
        Socket socket;
        private int connectionId;

        public ClientTask(Socket socket, int connectionId) {
            this.socket = socket;
            this.connectionId = connectionId;
        }

        @Override
        public void run() {
            String threadName = Thread.currentThread().getName();

            try (InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {

                socket.setSoTimeout(30000);

                byte[] buffer = new byte[1024];
                int len = in.read(buffer);

                if (len > 0) {
                    String request = new String(buffer, 0, len, StandardCharsets.UTF_8);

                    // 模拟处理时间
                    Thread.sleep(500);

                    // 响应
                    String response = "Echo from " + threadName + ": " + request;
                    out.write(response.getBytes(StandardCharsets.UTF_8));
                    out.flush();

                    processedCount.incrementAndGet();
                }

            } catch (SocketTimeoutException e) {
                System.err.println("[" + threadName + "] 超时");
            } catch (IOException e) {
                // 忽略客户端断开等异常
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    // 忽略
                }
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

            socket.setSoTimeout(5000);  // 5秒超时

            // 发送消息
            String message = "Request from Client " + clientId;
            out.write(message.getBytes(StandardCharsets.UTF_8));
            out.flush();

            // 接收响应
            byte[] buffer = new byte[1024];
            int len = in.read(buffer);
            String response = new String(buffer, 0, len, StandardCharsets.UTF_8);

            if (clientId % 10 == 0) {  // 每10个打印一次
                System.out.println("[客户端" + clientId + "] 成功");
            }

        } catch (IOException e) {
            if (clientId % 10 == 0) {
                System.err.println("[客户端" + clientId + "] 失败: " + e.getMessage());
            }
        }
    }

    /**
     * 启动监控
     */
    private static void startMonitor(ThreadPoolExecutor executor) {
        Thread monitor = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(3000);

                    System.out.println("\n========== 线程池状态 ==========");
                    System.out.println("活跃线程: " + executor.getActiveCount());
                    System.out.println("核心线程: " + executor.getCorePoolSize());
                    System.out.println("最大线程: " + executor.getMaximumPoolSize());
                    System.out.println("当前线程: " + executor.getPoolSize());
                    System.out.println("队列大小: " + executor.getQueue().size());
                    System.out.println("已完成: " + executor.getCompletedTaskCount());
                    System.out.println("总任务数: " + executor.getTaskCount());
                    System.out.println("总连接: " + connectionCount.get());
                    System.out.println("已处理: " + processedCount.get());
                    System.out.println("已拒绝: " + rejectedCount.get());
                    
                    // 内存使用
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

    /**
     * 优雅关闭线程池
     */
    private static void shutdownGracefully(ExecutorService executor) {
        System.out.println("\n关闭线程池...");
        
        executor.shutdown();
        
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                System.out.println("超时，强制关闭");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("线程池已关闭");
    }
}
