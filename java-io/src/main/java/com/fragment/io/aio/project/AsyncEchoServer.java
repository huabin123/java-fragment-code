package com.fragment.io.aio.project;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步Echo服务器
 * 
 * <p>功能特性：
 * <ul>
 *   <li>异步接受客户端连接</li>
 *   <li>异步读取和回写数据</li>
 *   <li>支持多客户端并发</li>
 *   <li>连接管理和统计</li>
 *   <li>优雅关闭</li>
 *   <li>心跳检测</li>
 * </ul>
 * 
 * @author fragment
 */
public class AsyncEchoServer {

    private final int port;
    private final AsynchronousChannelGroup channelGroup;
    private AsynchronousServerSocketChannel serverChannel;
    
    // 连接管理
    private final ConcurrentHashMap<String, ClientSession> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger connectionCount = new AtomicInteger(0);
    private final AtomicLong totalBytesReceived = new AtomicLong(0);
    private final AtomicLong totalBytesSent = new AtomicLong(0);
    
    private volatile boolean running = false;

    public AsyncEchoServer(int port) throws IOException {
        this(port, Runtime.getRuntime().availableProcessors());
    }

    public AsyncEchoServer(int port, int threadPoolSize) throws IOException {
        this.port = port;
        // 创建自定义线程组
        this.channelGroup = AsynchronousChannelGroup.withFixedThreadPool(
            threadPoolSize,
            Executors.defaultThreadFactory()
        );
    }

    /**
     * 启动服务器
     */
    public void start() throws IOException {
        serverChannel = AsynchronousServerSocketChannel.open(channelGroup);
        serverChannel.bind(new InetSocketAddress(port));
        running = true;

        System.out.println("========== 异步Echo服务器 ==========");
        System.out.println("服务器启动在端口: " + port);
        System.out.println("线程池大小: " + Runtime.getRuntime().availableProcessors());
        System.out.println("等待客户端连接...\n");

        // 开始接受连接
        acceptConnection();

        // 启动统计线程
        startStatisticsThread();
    }

    /**
     * 异步接受连接
     */
    private void acceptConnection() {
        serverChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override
            public void completed(AsynchronousSocketChannel clientChannel, Void attachment) {
                // 继续接受下一个连接（重要！）
                if (running) {
                    acceptConnection();
                }

                // 处理当前连接
                handleNewConnection(clientChannel);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                if (running) {
                    System.err.println("❌ 接受连接失败: " + exc.getMessage());
                    // 继续接受连接
                    acceptConnection();
                }
            }
        });
    }

    /**
     * 处理新连接
     */
    private void handleNewConnection(AsynchronousSocketChannel clientChannel) {
        try {
            String clientId = "Client-" + connectionCount.incrementAndGet();
            String remoteAddress = clientChannel.getRemoteAddress().toString();
            
            ClientSession session = new ClientSession(clientId, clientChannel);
            sessions.put(clientId, session);

            System.out.println("✅ [" + clientId + "] 新连接: " + remoteAddress + 
                             " (当前连接数: " + sessions.size() + ")");

            // 发送欢迎消息
            String welcome = "Welcome to Async Echo Server! Your ID: " + clientId + "\n";
            ByteBuffer welcomeBuffer = ByteBuffer.wrap(welcome.getBytes(StandardCharsets.UTF_8));
            
            clientChannel.write(welcomeBuffer, welcomeBuffer, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    totalBytesSent.addAndGet(result);
                    // 开始读取客户端数据
                    readData(clientChannel, session);
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    System.err.println("❌ [" + clientId + "] 发送欢迎消息失败: " + exc.getMessage());
                    closeSession(clientId);
                }
            });

        } catch (IOException e) {
            System.err.println("❌ 处理新连接失败: " + e.getMessage());
            closeChannel(clientChannel);
        }
    }

    /**
     * 异步读取数据
     */
    private void readData(AsynchronousSocketChannel clientChannel, ClientSession session) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        
        clientChannel.read(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer attachment) {
                if (bytesRead == -1) {
                    // 客户端关闭连接
                    System.out.println("👋 [" + session.getClientId() + "] 客户端断开连接");
                    closeSession(session.getClientId());
                    return;
                }

                // 更新统计
                totalBytesReceived.addAndGet(bytesRead);
                session.incrementMessageCount();
                session.updateLastActiveTime();

                // 读取数据
                attachment.flip();
                byte[] data = new byte[attachment.remaining()];
                attachment.get(data);
                String message = new String(data, StandardCharsets.UTF_8);

                System.out.println("📨 [" + session.getClientId() + "] 收到: " + message.trim());

                // Echo回写数据
                String echoMessage = "[ECHO] " + message;
                ByteBuffer echoBuffer = ByteBuffer.wrap(echoMessage.getBytes(StandardCharsets.UTF_8));
                
                writeData(clientChannel, session, echoBuffer);
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("❌ [" + session.getClientId() + "] 读取失败: " + exc.getMessage());
                closeSession(session.getClientId());
            }
        });
    }

    /**
     * 异步写入数据
     */
    private void writeData(AsynchronousSocketChannel clientChannel, ClientSession session, ByteBuffer buffer) {
        clientChannel.write(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesWritten, ByteBuffer attachment) {
                totalBytesSent.addAndGet(bytesWritten);

                if (attachment.hasRemaining()) {
                    // 还有数据未写完，继续写
                    clientChannel.write(attachment, attachment, this);
                } else {
                    // 写入完成，继续读取下一条消息
                    readData(clientChannel, session);
                }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("❌ [" + session.getClientId() + "] 写入失败: " + exc.getMessage());
                closeSession(session.getClientId());
            }
        });
    }

    /**
     * 关闭会话
     */
    private void closeSession(String clientId) {
        ClientSession session = sessions.remove(clientId);
        if (session != null) {
            closeChannel(session.getChannel());
            System.out.println("🔒 [" + clientId + "] 会话关闭 (剩余连接数: " + sessions.size() + ")");
        }
    }

    /**
     * 关闭通道
     */
    private void closeChannel(AsynchronousSocketChannel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    /**
     * 启动统计线程
     */
    private void startStatisticsThread() {
        Thread statsThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(10000); // 每10秒打印一次统计
                    printStatistics();
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        statsThread.setDaemon(true);
        statsThread.setName("Statistics-Thread");
        statsThread.start();
    }

    /**
     * 打印统计信息
     */
    private void printStatistics() {
        System.out.println("\n========== 服务器统计 ==========");
        System.out.println("当前连接数: " + sessions.size());
        System.out.println("总连接数: " + connectionCount.get());
        System.out.println("接收字节数: " + totalBytesReceived.get());
        System.out.println("发送字节数: " + totalBytesSent.get());
        
        if (!sessions.isEmpty()) {
            System.out.println("\n活跃会话:");
            sessions.forEach((id, session) -> {
                long idleTime = System.currentTimeMillis() - session.getLastActiveTime();
                System.out.printf("  %s: 消息数=%d, 空闲时间=%dms\n",
                    id, session.getMessageCount(), idleTime);
            });
        }
        System.out.println("================================\n");
    }

    /**
     * 优雅关闭服务器
     */
    public void shutdown() {
        System.out.println("\n正在关闭服务器...");
        running = false;

        // 关闭所有客户端连接
        sessions.keySet().forEach(this::closeSession);

        // 关闭服务器通道
        if (serverChannel != null) {
            try {
                serverChannel.close();
            } catch (IOException e) {
                System.err.println("关闭服务器通道失败: " + e.getMessage());
            }
        }

        // 关闭线程组
        if (channelGroup != null) {
            channelGroup.shutdown();
            try {
                channelGroup.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                System.err.println("等待线程组关闭超时");
            }
        }

        System.out.println("服务器已关闭");
    }

    /**
     * 广播消息给所有客户端
     */
    public void broadcast(String message) {
        String broadcastMsg = "[BROADCAST] " + message + "\n";
        ByteBuffer buffer = ByteBuffer.wrap(broadcastMsg.getBytes(StandardCharsets.UTF_8));

        sessions.forEach((id, session) -> {
            ByteBuffer copy = ByteBuffer.allocate(buffer.capacity());
            buffer.rewind();
            copy.put(buffer);
            copy.flip();

            session.getChannel().write(copy, copy, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    System.out.println("📢 广播消息发送到 [" + id + "]");
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    System.err.println("❌ 广播消息到 [" + id + "] 失败: " + exc.getMessage());
                }
            });
        });
    }

    // ==================== 内部类 ====================

    /**
     * 客户端会话
     */
    private static class ClientSession {
        private final String clientId;
        private final AsynchronousSocketChannel channel;
        private final long connectTime;
        private volatile long lastActiveTime;
        private final AtomicInteger messageCount;

        public ClientSession(String clientId, AsynchronousSocketChannel channel) {
            this.clientId = clientId;
            this.channel = channel;
            this.connectTime = System.currentTimeMillis();
            this.lastActiveTime = connectTime;
            this.messageCount = new AtomicInteger(0);
        }

        public String getClientId() {
            return clientId;
        }

        public AsynchronousSocketChannel getChannel() {
            return channel;
        }

        public long getLastActiveTime() {
            return lastActiveTime;
        }

        public void updateLastActiveTime() {
            this.lastActiveTime = System.currentTimeMillis();
        }

        public int getMessageCount() {
            return messageCount.get();
        }

        public void incrementMessageCount() {
            messageCount.incrementAndGet();
        }
    }

    // ==================== 测试客户端 ====================

    /**
     * 简单的测试客户端
     */
    public static class TestClient {
        private final String host;
        private final int port;
        private AsynchronousSocketChannel channel;

        public TestClient(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public void connect() throws IOException, InterruptedException {
            channel = AsynchronousSocketChannel.open();
            
            System.out.println("客户端连接到 " + host + ":" + port);
            
            java.util.concurrent.CountDownLatch connectLatch = new java.util.concurrent.CountDownLatch(1);
            
            channel.connect(new InetSocketAddress(host, port), null, 
                new CompletionHandler<Void, Void>() {
                @Override
                public void completed(Void result, Void attachment) {
                    System.out.println("✅ 连接成功");
                    connectLatch.countDown();
                    
                    // 开始读取服务器响应
                    readResponse();
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    System.err.println("❌ 连接失败: " + exc.getMessage());
                    connectLatch.countDown();
                }
            });
            
            connectLatch.await();
        }

        public void sendMessage(String message) {
            if (channel == null || !channel.isOpen()) {
                System.err.println("❌ 连接未建立");
                return;
            }

            String msg = message + "\n";
            ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
            
            channel.write(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    System.out.println("📤 发送: " + message);
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    System.err.println("❌ 发送失败: " + exc.getMessage());
                }
            });
        }

        private void readResponse() {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            
            channel.read(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer bytesRead, ByteBuffer attachment) {
                    if (bytesRead == -1) {
                        System.out.println("服务器关闭连接");
                        return;
                    }

                    attachment.flip();
                    String response = StandardCharsets.UTF_8.decode(attachment).toString();
                    System.out.println("📥 收到: " + response.trim());

                    // 继续读取
                    readResponse();
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    System.err.println("❌ 读取失败: " + exc.getMessage());
                }
            });
        }

        public void close() {
            if (channel != null) {
                try {
                    channel.close();
                    System.out.println("客户端连接已关闭");
                } catch (IOException e) {
                    System.err.println("关闭连接失败: " + e.getMessage());
                }
            }
        }
    }

    // ==================== 主程序 ====================

    public static void main(String[] args) throws Exception {
        // 启动服务器
        AsyncEchoServer server = new AsyncEchoServer(8888);
        server.start();

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n检测到关闭信号...");
            server.shutdown();
        }));

        // 等待一会儿让服务器完全启动
        Thread.sleep(1000);

        // 启动测试客户端
        System.out.println("\n========== 启动测试客户端 ==========\n");
        
        TestClient client1 = new TestClient("localhost", 8888);
        client1.connect();
        Thread.sleep(500);

        TestClient client2 = new TestClient("localhost", 8888);
        client2.connect();
        Thread.sleep(500);

        // 发送测试消息
        client1.sendMessage("Hello from Client 1");
        Thread.sleep(500);
        
        client2.sendMessage("Hello from Client 2");
        Thread.sleep(500);

        client1.sendMessage("How are you?");
        Thread.sleep(500);

        // 测试广播
        System.out.println("\n========== 测试广播功能 ==========\n");
        server.broadcast("Server announcement: System will restart in 5 minutes");
        Thread.sleep(1000);

        // 发送更多消息
        client2.sendMessage("Got it!");
        Thread.sleep(500);

        client1.sendMessage("Goodbye");
        Thread.sleep(500);

        // 打印最终统计
        server.printStatistics();

        // 关闭客户端
        System.out.println("\n========== 关闭客户端 ==========\n");
        client1.close();
        Thread.sleep(500);
        client2.close();
        Thread.sleep(1000);

        // 关闭服务器
        server.shutdown();
        
        System.out.println("\n========== 演示完成 ==========");
    }
}
