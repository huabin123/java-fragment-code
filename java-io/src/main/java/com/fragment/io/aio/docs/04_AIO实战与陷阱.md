# 第四章：AIO实战与陷阱 - 实际应用和最佳实践

> **学习目标**：掌握AIO的实际应用场景、常见陷阱和最佳实践

---

## 一、AIO的实际应用场景

### 1.1 场景1：批量文件处理

#### 为什么适合用AIO？

```
传统方式（BIO）：
┌─────────────────────────────────┐
│ for (File file : files) {       │
│     read(file);  // 阻塞         │
│     process(file);              │
│ }                               │
└─────────────────────────────────┘
问题：
- 串行处理，效率低
- 线程阻塞在I/O上
- 处理大量文件时间长

AIO方式：
┌─────────────────────────────────┐
│ for (File file : files) {       │
│     asyncRead(file, handler);   │
│     // 立即返回，不阻塞          │
│ }                               │
│ // 所有文件并发读取              │
└─────────────────────────────────┘
优势：
- 并发处理，效率高
- 不阻塞线程
- 充分利用I/O资源
```

#### 实现示例

```java
public class BatchFileProcessor {
    private final ExecutorService executor;
    private final AtomicInteger completedCount;
    private final AtomicInteger failedCount;
    
    public BatchFileProcessor(int threadPoolSize) {
        this.executor = Executors.newFixedThreadPool(threadPoolSize);
        this.completedCount = new AtomicInteger(0);
        this.failedCount = new AtomicInteger(0);
    }
    
    /**
     * 批量处理文件
     */
    public void processFiles(List<Path> files, Consumer<ByteBuffer> processor) 
            throws IOException {
        
        long startTime = System.currentTimeMillis();
        int totalFiles = files.size();
        
        System.out.println("开始处理 " + totalFiles + " 个文件...");
        
        CountDownLatch latch = new CountDownLatch(totalFiles);
        
        for (Path file : files) {
            processFile(file, processor, latch);
        }
        
        try {
            latch.await();  // 等待所有文件处理完成
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.println("\n处理完成！");
        System.out.println("总文件数: " + totalFiles);
        System.out.println("成功: " + completedCount.get());
        System.out.println("失败: " + failedCount.get());
        System.out.println("耗时: " + duration + "ms");
        System.out.println("平均: " + (duration / totalFiles) + "ms/文件");
    }
    
    /**
     * 异步处理单个文件
     */
    private void processFile(Path file, Consumer<ByteBuffer> processor, 
                            CountDownLatch latch) throws IOException {
        
        AsynchronousFileChannel channel = AsynchronousFileChannel.open(
            file,
            Collections.emptySet(),
            executor
        );
        
        long fileSize = channel.size();
        ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
        
        channel.read(buffer, 0, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer attachment) {
                try {
                    attachment.flip();
                    
                    // 处理文件内容
                    processor.accept(attachment);
                    
                    completedCount.incrementAndGet();
                    System.out.println("✓ 完成: " + file.getFileName());
                    
                } catch (Exception e) {
                    System.err.println("✗ 处理失败: " + file.getFileName() + 
                                     " - " + e.getMessage());
                    failedCount.incrementAndGet();
                } finally {
                    try {
                        channel.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    latch.countDown();
                }
            }
            
            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("✗ 读取失败: " + file.getFileName() + 
                                 " - " + exc.getMessage());
                failedCount.incrementAndGet();
                
                try {
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                latch.countDown();
            }
        });
    }
    
    public void shutdown() {
        executor.shutdown();
    }
    
    // 使用示例
    public static void main(String[] args) throws IOException {
        BatchFileProcessor processor = new BatchFileProcessor(10);
        
        // 准备文件列表
        List<Path> files = Arrays.asList(
            Paths.get("file1.txt"),
            Paths.get("file2.txt"),
            Paths.get("file3.txt")
            // ... 更多文件
        );
        
        // 处理文件（例如：统计字数）
        processor.processFiles(files, buffer -> {
            String content = StandardCharsets.UTF_8.decode(buffer).toString();
            int wordCount = content.split("\\s+").length;
            System.out.println("  字数: " + wordCount);
        });
        
        processor.shutdown();
    }
}
```

### 1.2 场景2：长连接聊天服务器

#### 为什么适合用AIO？

```
特点：
- 连接数多（成千上万）
- 消息频率低（偶尔发送）
- 需要长时间保持连接

BIO方式的问题：
┌─────────────────────────────────┐
│ 1000个连接 = 1000个线程          │
│ 每个线程阻塞在read()上           │
│ 资源消耗大                       │
└─────────────────────────────────┘

AIO方式的优势：
┌─────────────────────────────────┐
│ 1000个连接 = 少量线程            │
│ 异步等待消息                     │
│ 消息到达时回调处理               │
│ 资源消耗小                       │
└─────────────────────────────────┘
```

#### 实现示例

```java
public class AsyncChatServer {
    private final AsynchronousServerSocketChannel serverChannel;
    private final Map<String, AsynchronousSocketChannel> clients;
    private final AtomicInteger clientIdGenerator;
    
    public AsyncChatServer(int port) throws IOException {
        this.serverChannel = AsynchronousServerSocketChannel.open();
        this.serverChannel.bind(new InetSocketAddress(port));
        this.clients = new ConcurrentHashMap<>();
        this.clientIdGenerator = new AtomicInteger(0);
        
        System.out.println("聊天服务器启动在端口: " + port);
    }
    
    /**
     * 启动服务器
     */
    public void start() {
        acceptConnection();
        
        try {
            Thread.sleep(Long.MAX_VALUE);  // 保持服务器运行
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 异步接受连接
     */
    private void acceptConnection() {
        serverChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override
            public void completed(AsynchronousSocketChannel clientChannel, Void attachment) {
                // 继续接受下一个连接
                acceptConnection();
                
                // 处理当前连接
                String clientId = "Client-" + clientIdGenerator.incrementAndGet();
                clients.put(clientId, clientChannel);
                
                System.out.println(clientId + " 已连接，当前在线: " + clients.size());
                
                // 发送欢迎消息
                sendMessage(clientChannel, "欢迎! 你的ID是: " + clientId + "\n");
                
                // 开始读取客户端消息
                readMessage(clientChannel, clientId);
            }
            
            @Override
            public void failed(Throwable exc, Void attachment) {
                System.err.println("接受连接失败: " + exc.getMessage());
            }
        });
    }
    
    /**
     * 异步读取消息
     */
    private void readMessage(AsynchronousSocketChannel clientChannel, String clientId) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        
        clientChannel.read(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer attachment) {
                if (bytesRead == -1) {
                    // 客户端断开连接
                    handleDisconnect(clientChannel, clientId);
                    return;
                }
                
                // 处理消息
                attachment.flip();
                byte[] data = new byte[attachment.limit()];
                attachment.get(data);
                String message = new String(data).trim();
                
                System.out.println(clientId + ": " + message);
                
                // 广播消息给所有客户端
                broadcastMessage(clientId + ": " + message + "\n", clientId);
                
                // 继续读取下一条消息
                readMessage(clientChannel, clientId);
            }
            
            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println(clientId + " 读取失败: " + exc.getMessage());
                handleDisconnect(clientChannel, clientId);
            }
        });
    }
    
    /**
     * 发送消息
     */
    private void sendMessage(AsynchronousSocketChannel channel, String message) {
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
        
        channel.write(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesWritten, ByteBuffer attachment) {
                // 消息发送成功
            }
            
            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("发送消息失败: " + exc.getMessage());
            }
        });
    }
    
    /**
     * 广播消息
     */
    private void broadcastMessage(String message, String excludeClientId) {
        clients.forEach((clientId, channel) -> {
            if (!clientId.equals(excludeClientId)) {
                sendMessage(channel, message);
            }
        });
    }
    
    /**
     * 处理断开连接
     */
    private void handleDisconnect(AsynchronousSocketChannel channel, String clientId) {
        clients.remove(clientId);
        System.out.println(clientId + " 已断开，当前在线: " + clients.size());
        
        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // 通知其他客户端
        broadcastMessage(clientId + " 已离开\n", clientId);
    }
    
    public static void main(String[] args) throws IOException {
        AsyncChatServer server = new AsyncChatServer(8080);
        server.start();
    }
}
```

### 1.3 场景3：异步日志写入

#### 为什么适合用AIO？

```
日志写入特点：
- 频繁写入
- 不能阻塞业务线程
- 允许异步完成

传统方式问题：
┌─────────────────────────────────┐
│ 业务线程                         │
│   ↓                             │
│ log.write() ← 阻塞等待磁盘写入   │
│   ↓                             │
│ 影响业务性能                     │
└─────────────────────────────────┘

AIO方式优势：
┌─────────────────────────────────┐
│ 业务线程                         │
│   ↓                             │
│ asyncLog.write() ← 立即返回      │
│   ↓                             │
│ 继续执行业务                     │
│                                 │
│ 后台异步写入磁盘                 │
└─────────────────────────────────┘
```

#### 实现示例

```java
public class AsyncLogger {
    private final AsynchronousFileChannel logChannel;
    private final AtomicLong position;
    private final BlockingQueue<String> logQueue;
    private final Thread flushThread;
    private volatile boolean running;
    
    public AsyncLogger(String logFile) throws IOException {
        Path logPath = Paths.get(logFile);
        
        this.logChannel = AsynchronousFileChannel.open(
            logPath,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );
        
        this.position = new AtomicLong(logChannel.size());
        this.logQueue = new LinkedBlockingQueue<>(10000);
        this.running = true;
        
        // 启动刷新线程
        this.flushThread = new Thread(this::flushLoop);
        this.flushThread.setDaemon(true);
        this.flushThread.start();
    }
    
    /**
     * 异步写入日志
     */
    public void log(String level, String message) {
        String logEntry = String.format("[%s] [%s] %s%n",
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            level,
            message
        );
        
        try {
            logQueue.offer(logEntry, 1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("日志队列已满，丢弃日志: " + message);
        }
    }
    
    /**
     * 刷新循环
     */
    private void flushLoop() {
        while (running || !logQueue.isEmpty()) {
            try {
                String logEntry = logQueue.poll(100, TimeUnit.MILLISECONDS);
                if (logEntry != null) {
                    writeLog(logEntry);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * 异步写入日志
     */
    private void writeLog(String logEntry) {
        ByteBuffer buffer = ByteBuffer.wrap(logEntry.getBytes(StandardCharsets.UTF_8));
        long writePosition = position.getAndAdd(buffer.remaining());
        
        logChannel.write(buffer, writePosition, buffer, 
            new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer bytesWritten, ByteBuffer attachment) {
                    // 日志写入成功
                }
                
                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    System.err.println("日志写入失败: " + exc.getMessage());
                    exc.printStackTrace();
                }
            });
    }
    
    /**
     * 关闭日志器
     */
    public void close() throws IOException, InterruptedException {
        running = false;
        flushThread.join(5000);  // 等待刷新完成
        logChannel.close();
    }
    
    // 便捷方法
    public void info(String message) {
        log("INFO", message);
    }
    
    public void warn(String message) {
        log("WARN", message);
    }
    
    public void error(String message) {
        log("ERROR", message);
    }
    
    // 使用示例
    public static void main(String[] args) throws Exception {
        AsyncLogger logger = new AsyncLogger("app.log");
        
        // 模拟业务日志
        for (int i = 0; i < 1000; i++) {
            logger.info("处理请求 #" + i);
            
            if (i % 100 == 0) {
                logger.warn("检查点: " + i);
            }
            
            // 业务处理（不会被日志写入阻塞）
            Thread.sleep(1);
        }
        
        logger.close();
    }
}
```

---

## 二、AIO的常见陷阱

### 2.1 陷阱1：忘记处理失败回调

```java
// ❌ 错误：只处理成功，忽略失败
channel.read(buffer, 0, buffer, new CompletionHandler<Integer, ByteBuffer>() {
    @Override
    public void completed(Integer result, ByteBuffer attachment) {
        // 处理成功情况
        System.out.println("读取成功");
    }
    
    @Override
    public void failed(Throwable exc, ByteBuffer attachment) {
        // 什么都不做，吞掉异常
    }
});

// 问题：
// 1. 异常被吞掉，难以排查问题
// 2. 资源可能没有释放
// 3. 业务逻辑可能出错

// ✅ 正确：完整处理失败情况
channel.read(buffer, 0, buffer, new CompletionHandler<Integer, ByteBuffer>() {
    @Override
    public void completed(Integer result, ByteBuffer attachment) {
        System.out.println("读取成功: " + result + " 字节");
        // 处理数据...
    }
    
    @Override
    public void failed(Throwable exc, ByteBuffer attachment) {
        // 1. 记录日志
        logger.error("读取失败", exc);
        
        // 2. 释放资源
        try {
            channel.close();
        } catch (IOException e) {
            logger.error("关闭通道失败", e);
        }
        
        // 3. 通知上层
        notifyError(exc);
    }
});
```

### 2.2 陷阱2：回调地狱

```java
// ❌ 错误：多层嵌套回调
public void processRequest(AsynchronousSocketChannel client) {
    ByteBuffer buffer1 = ByteBuffer.allocate(1024);
    
    // 第1步：读取请求
    client.read(buffer1, buffer1, new CompletionHandler<>() {
        public void completed(Integer result, ByteBuffer att) {
            // 第2步：解析请求
            Request request = parseRequest(att);
            
            // 第3步：读取文件
            AsynchronousFileChannel fileChannel = openFile(request.getFile());
            ByteBuffer buffer2 = ByteBuffer.allocate(1024);
            
            fileChannel.read(buffer2, 0, buffer2, new CompletionHandler<>() {
                public void completed(Integer result, ByteBuffer att) {
                    // 第4步：处理数据
                    byte[] data = processData(att);
                    
                    // 第5步：写回响应
                    ByteBuffer buffer3 = ByteBuffer.wrap(data);
                    client.write(buffer3, buffer3, new CompletionHandler<>() {
                        public void completed(Integer result, ByteBuffer att) {
                            // 第6步：记录日志
                            logSuccess();
                            
                            // 回调嵌套太深！
                        }
                        public void failed(Throwable exc, ByteBuffer att) {
                            handleError(exc);
                        }
                    });
                }
                public void failed(Throwable exc, ByteBuffer att) {
                    handleError(exc);
                }
            });
        }
        public void failed(Throwable exc, ByteBuffer att) {
            handleError(exc);
        }
    });
}

// ✅ 正确：使用CompletableFuture重构
public CompletableFuture<Void> processRequest(AsynchronousSocketChannel client) {
    return readRequest(client)
        .thenCompose(request -> readFile(request.getFile()))
        .thenApply(data -> processData(data))
        .thenCompose(response -> writeResponse(client, response))
        .thenRun(() -> logSuccess())
        .exceptionally(exc -> {
            handleError(exc);
            return null;
        });
}

private CompletableFuture<Request> readRequest(AsynchronousSocketChannel client) {
    CompletableFuture<Request> future = new CompletableFuture<>();
    ByteBuffer buffer = ByteBuffer.allocate(1024);
    
    client.read(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
        public void completed(Integer result, ByteBuffer att) {
            att.flip();
            Request request = parseRequest(att);
            future.complete(request);
        }
        public void failed(Throwable exc, ByteBuffer att) {
            future.completeExceptionally(exc);
        }
    });
    
    return future;
}

// 其他方法类似...
```

### 2.3 陷阱3：资源泄漏

```java
// ❌ 错误：忘记关闭资源
public void readFile(Path file) throws IOException {
    AsynchronousFileChannel channel = AsynchronousFileChannel.open(file);
    ByteBuffer buffer = ByteBuffer.allocate(1024);
    
    channel.read(buffer, 0, buffer, new CompletionHandler<>() {
        public void completed(Integer result, ByteBuffer att) {
            // 处理数据...
            // 忘记关闭channel！
        }
        public void failed(Throwable exc, ByteBuffer att) {
            // 也忘记关闭channel！
        }
    });
}

// 问题：
// 1. 文件描述符泄漏
// 2. 内存泄漏
// 3. 最终导致"Too many open files"错误

// ✅ 正确：确保资源释放
public void readFile(Path file) throws IOException {
    AsynchronousFileChannel channel = AsynchronousFileChannel.open(file);
    ByteBuffer buffer = ByteBuffer.allocate(1024);
    
    channel.read(buffer, 0, buffer, new CompletionHandler<>() {
        public void completed(Integer result, ByteBuffer att) {
            try {
                // 处理数据...
            } finally {
                closeQuietly(channel);
            }
        }
        public void failed(Throwable exc, ByteBuffer att) {
            closeQuietly(channel);
        }
    });
}

private void closeQuietly(AsynchronousFileChannel channel) {
    try {
        if (channel != null) {
            channel.close();
        }
    } catch (IOException e) {
        logger.warn("关闭通道失败", e);
    }
}
```

### 2.4 陷阱4：Buffer复用问题

```java
// ❌ 错误：多个异步操作共享同一个Buffer
ByteBuffer sharedBuffer = ByteBuffer.allocate(1024);

for (Path file : files) {
    AsynchronousFileChannel channel = AsynchronousFileChannel.open(file);
    
    // 所有文件共享同一个buffer！
    channel.read(sharedBuffer, 0, sharedBuffer, new CompletionHandler<>() {
        public void completed(Integer result, ByteBuffer att) {
            // 数据可能被覆盖！
            processData(att);
        }
        public void failed(Throwable exc, ByteBuffer att) {
            exc.printStackTrace();
        }
    });
}

// 问题：
// 1. 多个异步操作同时写入同一个buffer
// 2. 数据互相覆盖
// 3. 结果不可预测

// ✅ 正确：每个操作使用独立的Buffer
for (Path file : files) {
    AsynchronousFileChannel channel = AsynchronousFileChannel.open(file);
    
    // 每个文件使用独立的buffer
    ByteBuffer buffer = ByteBuffer.allocate(1024);
    
    channel.read(buffer, 0, buffer, new CompletionHandler<>() {
        public void completed(Integer result, ByteBuffer att) {
            processData(att);
        }
        public void failed(Throwable exc, ByteBuffer att) {
            exc.printStackTrace();
        }
    });
}
```

### 2.5 陷阱5：回调线程问题

```java
// ❌ 错误：在回调中使用ThreadLocal
ThreadLocal<User> currentUser = new ThreadLocal<>();

public void handleRequest(AsynchronousSocketChannel client) {
    // 在请求线程中设置ThreadLocal
    currentUser.set(new User("Alice"));
    
    ByteBuffer buffer = ByteBuffer.allocate(1024);
    client.read(buffer, buffer, new CompletionHandler<>() {
        public void completed(Integer result, ByteBuffer att) {
            // 回调在不同的线程执行！
            User user = currentUser.get();  // 可能为null！
            
            if (user != null) {
                processRequest(user, att);
            }
        }
        public void failed(Throwable exc, ByteBuffer att) {
            exc.printStackTrace();
        }
    });
}

// 问题：
// 1. 回调在不同的线程执行
// 2. ThreadLocal在回调线程中不可用
// 3. 业务逻辑出错

// ✅ 正确：使用attachment传递上下文
public void handleRequest(AsynchronousSocketChannel client, User user) {
    ByteBuffer buffer = ByteBuffer.allocate(1024);
    
    // 使用attachment传递上下文
    Context context = new Context(user, buffer);
    
    client.read(buffer, context, new CompletionHandler<Integer, Context>() {
        public void completed(Integer result, Context ctx) {
            // 从attachment获取上下文
            processRequest(ctx.user, ctx.buffer);
        }
        public void failed(Throwable exc, Context ctx) {
            exc.printStackTrace();
        }
    });
}

static class Context {
    User user;
    ByteBuffer buffer;
    
    Context(User user, ByteBuffer buffer) {
        this.user = user;
        this.buffer = buffer;
    }
}
```

---

## 三、AIO最佳实践

### 3.1 实践1：使用自定义线程池

```java
// 创建自定义线程池
ExecutorService executor = new ThreadPoolExecutor(
    10,                      // 核心线程数
    50,                      // 最大线程数
    60L, TimeUnit.SECONDS,   // 空闲线程存活时间
    new LinkedBlockingQueue<>(1000),  // 任务队列
    new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger(0);
        
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("AIO-Worker-" + counter.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        }
    },
    new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
);

// 使用自定义线程池创建Channel
AsynchronousChannelGroup group = AsynchronousChannelGroup.withThreadPool(executor);

AsynchronousFileChannel channel = AsynchronousFileChannel.open(
    path,
    Collections.emptySet(),
    group
);

// 关闭时清理资源
group.shutdown();
group.awaitTermination(10, TimeUnit.SECONDS);
executor.shutdown();
```

### 3.2 实践2：统一异常处理

```java
public abstract class SafeCompletionHandler<V, A> implements CompletionHandler<V, A> {
    
    @Override
    public final void completed(V result, A attachment) {
        try {
            onCompleted(result, attachment);
        } catch (Exception e) {
            logger.error("回调处理异常", e);
            onError(e, attachment);
        }
    }
    
    @Override
    public final void failed(Throwable exc, A attachment) {
        logger.error("I/O操作失败", exc);
        onError(exc, attachment);
    }
    
    /**
     * 成功回调（子类实现）
     */
    protected abstract void onCompleted(V result, A attachment);
    
    /**
     * 错误处理（子类可选实现）
     */
    protected void onError(Throwable exc, A attachment) {
        // 默认实现：记录日志
    }
}

// 使用示例
channel.read(buffer, buffer, new SafeCompletionHandler<Integer, ByteBuffer>() {
    @Override
    protected void onCompleted(Integer result, ByteBuffer attachment) {
        // 只需要关注业务逻辑
        processData(attachment);
    }
    
    @Override
    protected void onError(Throwable exc, ByteBuffer attachment) {
        // 自定义错误处理
        notifyUser("读取失败");
    }
});
```

### 3.3 实践3：使用CompletableFuture封装

```java
public class AIOHelper {
    
    /**
     * 将AIO操作转换为CompletableFuture
     */
    public static CompletableFuture<Integer> readAsync(
            AsynchronousFileChannel channel,
            ByteBuffer buffer,
            long position) {
        
        CompletableFuture<Integer> future = new CompletableFuture<>();
        
        channel.read(buffer, position, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer result, ByteBuffer attachment) {
                future.complete(result);
            }
            
            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                future.completeExceptionally(exc);
            }
        });
        
        return future;
    }
    
    /**
     * 将AIO操作转换为CompletableFuture
     */
    public static CompletableFuture<Integer> writeAsync(
            AsynchronousFileChannel channel,
            ByteBuffer buffer,
            long position) {
        
        CompletableFuture<Integer> future = new CompletableFuture<>();
        
        channel.write(buffer, position, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer result, ByteBuffer attachment) {
                future.complete(result);
            }
            
            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                future.completeExceptionally(exc);
            }
        });
        
        return future;
    }
}

// 使用示例：链式调用
AsynchronousFileChannel inputChannel = AsynchronousFileChannel.open(inputPath);
AsynchronousFileChannel outputChannel = AsynchronousFileChannel.open(outputPath, 
    StandardOpenOption.WRITE, StandardOpenOption.CREATE);

ByteBuffer buffer = ByteBuffer.allocate(1024);

AIOHelper.readAsync(inputChannel, buffer, 0)
    .thenApply(bytesRead -> {
        buffer.flip();
        return processData(buffer);
    })
    .thenCompose(processedBuffer -> 
        AIOHelper.writeAsync(outputChannel, processedBuffer, 0))
    .thenRun(() -> {
        System.out.println("文件处理完成");
        closeQuietly(inputChannel);
        closeQuietly(outputChannel);
    })
    .exceptionally(exc -> {
        System.err.println("处理失败: " + exc.getMessage());
        return null;
    });
```

### 3.4 实践4：资源管理

```java
public class ManagedAsynchronousFileChannel implements AutoCloseable {
    private final AsynchronousFileChannel channel;
    private final AtomicBoolean closed;
    
    public ManagedAsynchronousFileChannel(Path path, OpenOption... options) 
            throws IOException {
        this.channel = AsynchronousFileChannel.open(path, options);
        this.closed = new AtomicBoolean(false);
    }
    
    public CompletableFuture<Integer> read(ByteBuffer buffer, long position) {
        checkClosed();
        
        CompletableFuture<Integer> future = new CompletableFuture<>();
        
        channel.read(buffer, position, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer result, ByteBuffer attachment) {
                future.complete(result);
            }
            
            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                future.completeExceptionally(exc);
            }
        });
        
        return future;
    }
    
    private void checkClosed() {
        if (closed.get()) {
            throw new IllegalStateException("Channel已关闭");
        }
    }
    
    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            channel.close();
        }
    }
}

// 使用示例：try-with-resources
try (ManagedAsynchronousFileChannel channel = 
        new ManagedAsynchronousFileChannel(path, StandardOpenOption.READ)) {
    
    ByteBuffer buffer = ByteBuffer.allocate(1024);
    Integer bytesRead = channel.read(buffer, 0).get();
    
    // 处理数据...
    
} catch (IOException | InterruptedException | ExecutionException e) {
    e.printStackTrace();
}
// channel自动关闭
```

---

## 四、性能优化建议

### 4.1 合理设置Buffer大小

```java
// ❌ 不好：Buffer太小，频繁I/O
ByteBuffer buffer = ByteBuffer.allocate(128);  // 太小

// ❌ 不好：Buffer太大，内存浪费
ByteBuffer buffer = ByteBuffer.allocate(10 * 1024 * 1024);  // 10MB，太大

// ✅ 推荐：根据实际情况调整
// 文件I/O：4KB - 8KB（页大小的倍数）
ByteBuffer buffer = ByteBuffer.allocate(8 * 1024);

// 网络I/O：1KB - 4KB
ByteBuffer buffer = ByteBuffer.allocate(4 * 1024);

// 大文件：可以更大，如64KB - 1MB
ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
```

### 4.2 使用DirectBuffer

```java
// 对于频繁的I/O操作，使用DirectBuffer可以减少拷贝
ByteBuffer directBuffer = ByteBuffer.allocateDirect(8 * 1024);

// 注意：
// 1. DirectBuffer分配和释放成本高
// 2. 适合长期使用的Buffer
// 3. 需要手动管理内存
```

### 4.3 批量操作

```java
// ❌ 不好：逐个处理
for (Path file : files) {
    processFile(file).get();  // 等待每个文件完成
}

// ✅ 好：批量并发处理
List<CompletableFuture<Void>> futures = new ArrayList<>();
for (Path file : files) {
    futures.add(processFile(file));
}

// 等待所有完成
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
```

---

## 五、总结

### 5.1 核心要点

```
1. 适用场景：
   - 批量文件处理
   - 长连接低频消息
   - 异步日志写入
   - I/O密集型应用

2. 常见陷阱：
   - 忘记处理失败回调
   - 回调地狱
   - 资源泄漏
   - Buffer复用问题
   - 回调线程问题

3. 最佳实践：
   - 使用自定义线程池
   - 统一异常处理
   - 使用CompletableFuture封装
   - 合理管理资源
   - 优化Buffer大小

4. 性能优化：
   - 合理设置Buffer大小
   - 使用DirectBuffer
   - 批量并发操作
```

### 5.2 何时使用AIO

```
✅ 适合：
- Windows平台
- I/O密集型应用
- 需要异步回调的场景
- 批量文件处理

❌ 不适合：
- Linux平台（性能提升有限）
- 高频短连接
- 简单的文件I/O
- 需要精确控制线程模型
```

---

**恭喜！** 你已经完成了AIO模块的学习。现在你应该能够：
- ✅ 理解AIO的工作原理
- ✅ 掌握AIO的核心组件
- ✅ 理解Proactor模式
- ✅ 在实际项目中应用AIO
- ✅ 避免常见陷阱
- ✅ 进行性能优化

**下一步**：学习Netty框架，它是基于NIO的高性能网络框架，在生产环境中更加成熟和稳定。
