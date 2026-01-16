# 第二章：AIO核心组件 - AsynchronousChannel与CompletionHandler

> **学习目标**：掌握AIO的核心组件，理解异步文件和网络操作的实现

---

## 一、AIO核心组件概览

### 1.1 核心类图

```
java.nio.channels.AsynchronousChannel (接口)
    ├── AsynchronousFileChannel        # 异步文件通道
    ├── AsynchronousSocketChannel      # 异步Socket通道
    └── AsynchronousServerSocketChannel # 异步ServerSocket通道

java.nio.channels.CompletionHandler<V, A> (接口)
    ├── completed(V result, A attachment)  # 成功回调
    └── failed(Throwable exc, A attachment) # 失败回调

java.nio.channels.AsynchronousChannelGroup
    └── 管理异步通道的线程池
```

### 1.2 两种编程模式

```
模式1：Future模式（同步等待）
┌─────────────────────────────────┐
│ Future<Integer> future =        │
│     channel.read(buffer, pos);  │
│                                 │
│ // 继续执行其他任务...           │
│                                 │
│ Integer result = future.get();  │ ← 阻塞等待结果
└─────────────────────────────────┘

模式2：CompletionHandler模式（异步回调）
┌─────────────────────────────────┐
│ channel.read(buffer, pos, null, │
│     new CompletionHandler<>() { │
│         completed(...) {        │ ← 异步回调
│             // 处理结果          │
│         }                       │
│     });                         │
│                                 │
│ // 立即返回，继续执行...         │
└─────────────────────────────────┘
```

---

## 二、AsynchronousFileChannel - 异步文件操作

### 2.1 为什么需要异步文件操作？

#### 传统文件I/O的问题

```java
// 传统方式：阻塞读取
FileInputStream fis = new FileInputStream("large-file.dat");
byte[] buffer = new byte[1024];
int len = fis.read(buffer);  // 阻塞，直到读取完成

// 问题：
// 1. ❌ 读取大文件时线程被阻塞
// 2. ❌ 无法同时处理多个文件
// 3. ❌ CPU利用率低
```

#### 异步文件I/O的优势

```java
// 异步方式：非阻塞读取
AsynchronousFileChannel channel = AsynchronousFileChannel.open(path);
ByteBuffer buffer = ByteBuffer.allocate(1024);

channel.read(buffer, 0, buffer, new CompletionHandler<>() {
    public void completed(Integer result, ByteBuffer attachment) {
        // 读取完成后的回调
    }
    public void failed(Throwable exc, ByteBuffer attachment) {
        // 失败回调
    }
});

// 优势：
// 1. ✅ 立即返回，不阻塞线程
// 2. ✅ 可以同时读取多个文件
// 3. ✅ 提高CPU利用率
```

### 2.2 打开异步文件通道

```java
// 方式1：使用默认选项
Path path = Paths.get("data.txt");
AsynchronousFileChannel channel = AsynchronousFileChannel.open(path);

// 方式2：指定打开选项
AsynchronousFileChannel channel = AsynchronousFileChannel.open(
    path,
    StandardOpenOption.READ,      // 读模式
    StandardOpenOption.WRITE,     // 写模式
    StandardOpenOption.CREATE     // 不存在则创建
);

// 方式3：指定线程池
ExecutorService executor = Executors.newFixedThreadPool(10);
AsynchronousFileChannel channel = AsynchronousFileChannel.open(
    path,
    Collections.emptySet(),
    executor  // 自定义线程池执行回调
);
```

### 2.3 异步读取文件

#### Future模式

```java
public class AsyncFileReadFuture {
    public static void main(String[] args) throws Exception {
        Path path = Paths.get("data.txt");
        AsynchronousFileChannel channel = AsynchronousFileChannel.open(path);
        
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        long position = 0;
        
        // 发起异步读取
        Future<Integer> future = channel.read(buffer, position);
        
        // 可以做其他事情...
        System.out.println("读取已发起，继续执行其他任务...");
        
        // 等待读取完成
        Integer bytesRead = future.get();  // 阻塞等待
        
        System.out.println("读取了 " + bytesRead + " 字节");
        
        buffer.flip();
        byte[] data = new byte[buffer.limit()];
        buffer.get(data);
        System.out.println("内容: " + new String(data));
        
        channel.close();
    }
}
```

#### CompletionHandler模式（推荐）

```java
public class AsyncFileReadCallback {
    public static void main(String[] args) throws Exception {
        Path path = Paths.get("data.txt");
        AsynchronousFileChannel channel = AsynchronousFileChannel.open(path);
        
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        long position = 0;
        
        // 发起异步读取，注册回调
        channel.read(buffer, position, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer attachment) {
                System.out.println("读取完成，读取了 " + bytesRead + " 字节");
                
                attachment.flip();
                byte[] data = new byte[attachment.limit()];
                attachment.get(data);
                System.out.println("内容: " + new String(data));
                
                try {
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("读取失败: " + exc.getMessage());
                exc.printStackTrace();
            }
        });
        
        // 立即返回，不阻塞
        System.out.println("读取已发起，主线程继续执行...");
        
        // 防止主线程退出
        Thread.sleep(1000);
    }
}
```

### 2.4 异步写入文件

```java
public class AsyncFileWrite {
    public static void main(String[] args) throws Exception {
        Path path = Paths.get("output.txt");
        AsynchronousFileChannel channel = AsynchronousFileChannel.open(
            path,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE
        );
        
        String content = "Hello, AIO!";
        ByteBuffer buffer = ByteBuffer.wrap(content.getBytes());
        long position = 0;
        
        // 异步写入
        channel.write(buffer, position, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesWritten, ByteBuffer attachment) {
                System.out.println("写入完成，写入了 " + bytesWritten + " 字节");
                
                try {
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("写入失败: " + exc.getMessage());
            }
        });
        
        System.out.println("写入已发起...");
        Thread.sleep(1000);
    }
}
```

### 2.5 读取大文件（分块读取）

```java
public class AsyncLargeFileRead {
    public static void readLargeFile(Path path) throws IOException {
        AsynchronousFileChannel channel = AsynchronousFileChannel.open(path);
        long fileSize = channel.size();
        int bufferSize = 1024 * 1024;  // 1MB
        
        readChunk(channel, 0, fileSize, bufferSize);
    }
    
    private static void readChunk(AsynchronousFileChannel channel, 
                                  long position, 
                                  long fileSize, 
                                  int bufferSize) {
        if (position >= fileSize) {
            try {
                channel.close();
                System.out.println("文件读取完成");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        
        channel.read(buffer, position, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer attachment) {
                if (bytesRead == -1) {
                    try {
                        channel.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return;
                }
                
                System.out.println("读取位置 " + position + "，读取了 " + bytesRead + " 字节");
                
                // 处理数据
                attachment.flip();
                processData(attachment);
                
                // 读取下一块
                readChunk(channel, position + bytesRead, fileSize, bufferSize);
            }
            
            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("读取失败: " + exc.getMessage());
            }
        });
    }
    
    private static void processData(ByteBuffer buffer) {
        // 处理数据...
    }
}
```

---

## 三、AsynchronousSocketChannel - 异步网络操作

### 3.1 为什么需要异步网络操作？

```
传统Socket（BIO）：
┌─────────────────────────────────┐
│ 线程1 → Socket1 → read() 阻塞    │
│ 线程2 → Socket2 → read() 阻塞    │
│ 线程3 → Socket3 → read() 阻塞    │
│ ...                             │
│ 线程N → SocketN → read() 阻塞    │
└─────────────────────────────────┘
问题：大量线程阻塞，资源浪费

异步Socket（AIO）：
┌─────────────────────────────────┐
│ 发起read() → 立即返回            │
│ 发起read() → 立即返回            │
│ 发起read() → 立即返回            │
│ ...                             │
│ 数据到达 → 回调通知              │
└─────────────────────────────────┘
优势：不阻塞线程，高效利用资源
```

### 3.2 异步客户端

```java
public class AsyncClient {
    public static void main(String[] args) throws Exception {
        // 1. 打开异步Socket通道
        AsynchronousSocketChannel client = AsynchronousSocketChannel.open();
        
        // 2. 异步连接服务器
        InetSocketAddress serverAddress = new InetSocketAddress("localhost", 8080);
        Future<Void> connectFuture = client.connect(serverAddress);
        
        // 等待连接完成
        connectFuture.get();
        System.out.println("连接成功");
        
        // 3. 异步发送数据
        String message = "Hello, Server!";
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
        
        client.write(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesSent, ByteBuffer attachment) {
                System.out.println("发送了 " + bytesSent + " 字节");
                
                // 4. 异步接收响应
                ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                client.read(readBuffer, readBuffer, new CompletionHandler<Integer, ByteBuffer>() {
                    @Override
                    public void completed(Integer bytesRead, ByteBuffer attachment) {
                        System.out.println("接收了 " + bytesRead + " 字节");
                        
                        attachment.flip();
                        byte[] data = new byte[attachment.limit()];
                        attachment.get(data);
                        System.out.println("服务器响应: " + new String(data));
                        
                        try {
                            client.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    
                    @Override
                    public void failed(Throwable exc, ByteBuffer attachment) {
                        System.err.println("接收失败: " + exc.getMessage());
                    }
                });
            }
            
            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("发送失败: " + exc.getMessage());
            }
        });
        
        // 防止主线程退出
        Thread.sleep(3000);
    }
}
```

### 3.3 异步服务器

```java
public class AsyncServer {
    public static void main(String[] args) throws Exception {
        // 1. 打开异步ServerSocket通道
        AsynchronousServerSocketChannel server = 
            AsynchronousServerSocketChannel.open();
        
        // 2. 绑定端口
        server.bind(new InetSocketAddress(8080));
        System.out.println("服务器启动在端口 8080");
        
        // 3. 异步接受连接
        server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override
            public void completed(AsynchronousSocketChannel client, Void attachment) {
                // 继续接受下一个连接
                server.accept(null, this);
                
                System.out.println("接受新连接: " + client);
                
                // 异步读取客户端数据
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                client.read(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
                    @Override
                    public void completed(Integer bytesRead, ByteBuffer attachment) {
                        if (bytesRead == -1) {
                            try {
                                client.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            return;
                        }
                        
                        System.out.println("接收了 " + bytesRead + " 字节");
                        
                        // 处理数据
                        attachment.flip();
                        byte[] data = new byte[attachment.limit()];
                        attachment.get(data);
                        String message = new String(data);
                        System.out.println("客户端消息: " + message);
                        
                        // 异步发送响应
                        String response = "Echo: " + message;
                        ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes());
                        
                        client.write(responseBuffer, responseBuffer, 
                            new CompletionHandler<Integer, ByteBuffer>() {
                                @Override
                                public void completed(Integer bytesSent, ByteBuffer att) {
                                    System.out.println("发送了 " + bytesSent + " 字节");
                                    
                                    try {
                                        client.close();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                                
                                @Override
                                public void failed(Throwable exc, ByteBuffer att) {
                                    System.err.println("发送失败: " + exc.getMessage());
                                }
                            });
                    }
                    
                    @Override
                    public void failed(Throwable exc, ByteBuffer attachment) {
                        System.err.println("读取失败: " + exc.getMessage());
                    }
                });
            }
            
            @Override
            public void failed(Throwable exc, Void attachment) {
                System.err.println("接受连接失败: " + exc.getMessage());
            }
        });
        
        // 保持服务器运行
        Thread.sleep(Long.MAX_VALUE);
    }
}
```

---

## 四、CompletionHandler - 异步回调

### 4.1 CompletionHandler接口

```java
public interface CompletionHandler<V, A> {
    /**
     * 操作成功完成时调用
     * @param result 操作结果（如读取的字节数）
     * @param attachment 附加对象（传递上下文）
     */
    void completed(V result, A attachment);
    
    /**
     * 操作失败时调用
     * @param exc 异常
     * @param attachment 附加对象
     */
    void failed(Throwable exc, A attachment);
}
```

### 4.2 回调在哪个线程执行？

```java
// 情况1：使用默认线程池
AsynchronousFileChannel channel = AsynchronousFileChannel.open(path);
// 回调在系统默认的异步I/O线程池中执行

// 情况2：使用自定义线程池
ExecutorService executor = Executors.newFixedThreadPool(10);
AsynchronousFileChannel channel = AsynchronousFileChannel.open(
    path,
    Collections.emptySet(),
    executor
);
// 回调在自定义线程池中执行

// 注意：
// 1. 回调不在发起I/O操作的线程执行
// 2. 回调线程不确定，不要依赖ThreadLocal
// 3. 回调中的异常需要自己处理
```

### 4.3 使用attachment传递上下文

```java
public class AttachmentExample {
    static class Context {
        String fileName;
        long startTime;
        int totalBytes;
        
        Context(String fileName) {
            this.fileName = fileName;
            this.startTime = System.currentTimeMillis();
        }
    }
    
    public static void readFile(Path path) throws IOException {
        AsynchronousFileChannel channel = AsynchronousFileChannel.open(path);
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        
        // 创建上下文对象
        Context context = new Context(path.getFileName().toString());
        
        channel.read(buffer, 0, context, new CompletionHandler<Integer, Context>() {
            @Override
            public void completed(Integer bytesRead, Context ctx) {
                ctx.totalBytes += bytesRead;
                long duration = System.currentTimeMillis() - ctx.startTime;
                
                System.out.println("文件: " + ctx.fileName);
                System.out.println("读取字节: " + ctx.totalBytes);
                System.out.println("耗时: " + duration + "ms");
                
                try {
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void failed(Throwable exc, Context ctx) {
                System.err.println("读取文件失败: " + ctx.fileName);
                exc.printStackTrace();
            }
        });
    }
}
```

### 4.4 链式异步操作

```java
public class ChainedAsyncOperations {
    public static void processFile(Path inputPath, Path outputPath) throws IOException {
        AsynchronousFileChannel inputChannel = AsynchronousFileChannel.open(inputPath);
        AsynchronousFileChannel outputChannel = AsynchronousFileChannel.open(
            outputPath,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE
        );
        
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        
        // 步骤1：读取输入文件
        inputChannel.read(buffer, 0, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer attachment) {
                System.out.println("步骤1完成：读取了 " + bytesRead + " 字节");
                
                // 步骤2：处理数据
                attachment.flip();
                processData(attachment);
                
                // 步骤3：写入输出文件
                outputChannel.write(attachment, 0, attachment, 
                    new CompletionHandler<Integer, ByteBuffer>() {
                        @Override
                        public void completed(Integer bytesWritten, ByteBuffer att) {
                            System.out.println("步骤3完成：写入了 " + bytesWritten + " 字节");
                            
                            // 关闭资源
                            try {
                                inputChannel.close();
                                outputChannel.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        
                        @Override
                        public void failed(Throwable exc, ByteBuffer att) {
                            System.err.println("写入失败");
                        }
                    });
            }
            
            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("读取失败");
            }
        });
    }
    
    private static void processData(ByteBuffer buffer) {
        // 处理数据（如加密、压缩等）
    }
}
```

---

## 五、AsynchronousChannelGroup - 线程池管理

### 5.1 为什么需要ChannelGroup？

```
默认情况：
每个AsynchronousChannel使用系统默认线程池
    ↓
问题：
1. 无法控制线程数量
2. 无法自定义线程池参数
3. 无法统一管理

解决方案：
使用AsynchronousChannelGroup自定义线程池
```

### 5.2 创建ChannelGroup

```java
// 方式1：固定线程数
AsynchronousChannelGroup group = AsynchronousChannelGroup.withFixedThreadPool(
    10,  // 线程数
    Executors.defaultThreadFactory()
);

// 方式2：使用自定义线程池
ExecutorService executor = Executors.newCachedThreadPool();
AsynchronousChannelGroup group = AsynchronousChannelGroup.withThreadPool(executor);

// 使用ChannelGroup创建Channel
AsynchronousFileChannel channel = AsynchronousFileChannel.open(
    path,
    Collections.emptySet(),
    group  // 指定ChannelGroup
);

// 关闭ChannelGroup
group.shutdown();
group.awaitTermination(10, TimeUnit.SECONDS);
```

---

## 六、总结

### 6.1 核心要点

```
1. AsynchronousFileChannel：
   - 异步文件操作
   - 支持Future和CompletionHandler两种模式
   - 适合大文件、批量文件处理

2. AsynchronousSocketChannel：
   - 异步网络操作
   - 客户端和服务器端都支持
   - 适合长连接、低频消息场景

3. CompletionHandler：
   - 异步回调接口
   - completed() 处理成功
   - failed() 处理失败
   - 使用attachment传递上下文

4. AsynchronousChannelGroup：
   - 管理异步通道的线程池
   - 可以自定义线程池参数
   - 统一管理多个Channel
```

### 6.2 最佳实践

```
1. 优先使用CompletionHandler模式（而不是Future）
2. 在回调中处理异常，不要抛出
3. 使用attachment传递上下文，避免闭包捕获
4. 及时关闭资源，避免泄漏
5. 使用自定义ChannelGroup控制线程池
6. 避免回调嵌套过深，考虑使用CompletableFuture
```

---

**下一章**：我们将学习Proactor模式，理解AIO的设计思想。

**继续阅读**：[第三章：Proactor模式](./03_Proactor模式.md)
