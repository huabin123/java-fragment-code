package com.fragment.io.aio.demo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CompletionHandler回调机制演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>CompletionHandler基本用法</li>
 *   <li>成功和失败回调</li>
 *   <li>回调线程分析</li>
 *   <li>链式回调处理</li>
 *   <li>异常处理最佳实践</li>
 *   <li>避免回调地狱</li>
 * </ul>
 * 
 * @author fragment
 */
public class CompletionHandlerDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== CompletionHandler回调机制演示 ==========\n");

        // 1. 基本用法
        demonstrateBasicUsage();

        // 2. 回调线程分析
        demonstrateCallbackThread();

        // 3. 链式回调
        demonstrateChainedCallbacks();

        // 4. 异常处理
        demonstrateExceptionHandling();

        // 5. 避免回调地狱
        demonstrateAvoidCallbackHell();

        // 6. 网络场景的回调
        demonstrateNetworkCallback();
    }

    /**
     * 1. CompletionHandler基本用法
     */
    private static void demonstrateBasicUsage() throws IOException, InterruptedException {
        System.out.println("1. CompletionHandler基本用法");
        System.out.println("特点: 异步操作完成后自动调用completed()或failed()\n");

        // 创建测试文件
        Path path = createTestFile("callback-test.txt", "Hello, CompletionHandler!");

        // 打开异步文件通道
        AsynchronousFileChannel channel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        CountDownLatch latch = new CountDownLatch(1);

        System.out.println("=== 发起异步读取 ===");
        long startTime = System.currentTimeMillis();

        // 使用CompletionHandler
        channel.read(buffer, 0, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer result, ByteBuffer attachment) {
                long duration = System.currentTimeMillis() - startTime;
                
                System.out.println("\n✅ 读取成功回调");
                System.out.println("读取字节数: " + result);
                System.out.println("耗时: " + duration + "ms");
                System.out.println("回调线程: " + Thread.currentThread().getName());
                
                // 处理数据
                attachment.flip();
                String content = StandardCharsets.UTF_8.decode(attachment).toString();
                System.out.println("文件内容: " + content);
                
                // 关闭资源
                closeChannel(channel);
                latch.countDown();
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.out.println("\n❌ 读取失败回调");
                System.out.println("异常: " + exc.getMessage());
                System.out.println("回调线程: " + Thread.currentThread().getName());
                
                exc.printStackTrace();
                closeChannel(channel);
                latch.countDown();
            }
        });

        System.out.println("异步读取已发起，主线程继续执行");
        System.out.println("主线程: " + Thread.currentThread().getName());

        // 等待回调完成
        latch.await();
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 2. 回调线程分析
     */
    private static void demonstrateCallbackThread() throws IOException, InterruptedException {
        System.out.println("2. 回调线程分析");
        System.out.println("问题: CompletionHandler的回调在哪个线程执行？\n");

        Path path = createTestFile("thread-test.txt", "Thread Analysis");
        AsynchronousFileChannel channel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);

        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger callbackCount = new AtomicInteger(0);

        System.out.println("主线程: " + Thread.currentThread().getName());
        System.out.println("\n发起3次异步读取，观察回调线程...\n");

        // 发起多次异步读取
        for (int i = 0; i < 3; i++) {
            final int index = i;
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            
            channel.read(buffer, 0, index, new CompletionHandler<Integer, Integer>() {
                @Override
                public void completed(Integer result, Integer attachment) {
                    int count = callbackCount.incrementAndGet();
                    System.out.println("回调 #" + (attachment + 1) + " - 线程: " + 
                                     Thread.currentThread().getName() + 
                                     " (总回调数: " + count + ")");
                    latch.countDown();
                }

                @Override
                public void failed(Throwable exc, Integer attachment) {
                    exc.printStackTrace();
                    latch.countDown();
                }
            });
        }

        latch.await();
        closeChannel(channel);

        System.out.println("\n💡 结论:");
        System.out.println("- 回调可能在不同的线程执行");
        System.out.println("- 线程由系统的AsynchronousChannelGroup管理");
        System.out.println("- 不要在回调中假设特定的线程上下文");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 3. 链式回调处理
     */
    private static void demonstrateChainedCallbacks() throws IOException, InterruptedException {
        System.out.println("3. 链式回调处理");
        System.out.println("场景: 读取文件1 → 处理数据 → 写入文件2\n");

        Path inputPath = createTestFile("input.txt", "Original Content");
        Path outputPath = Paths.get("output.txt");

        CountDownLatch latch = new CountDownLatch(1);

        System.out.println("=== 开始链式操作 ===");

        // 步骤1: 读取输入文件
        AsynchronousFileChannel inputChannel = AsynchronousFileChannel.open(
            inputPath, StandardOpenOption.READ);
        ByteBuffer readBuffer = ByteBuffer.allocate(1024);

        inputChannel.read(readBuffer, 0, readBuffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer result, ByteBuffer attachment) {
                System.out.println("✅ 步骤1: 读取完成，字节数: " + result);
                
                try {
                    closeChannel(inputChannel);
                    
                    // 步骤2: 处理数据
                    attachment.flip();
                    String content = StandardCharsets.UTF_8.decode(attachment).toString();
                    String processed = content.toUpperCase() + " [PROCESSED]";
                    System.out.println("✅ 步骤2: 数据处理完成");
                    
                    // 步骤3: 写入输出文件
                    AsynchronousFileChannel outputChannel = AsynchronousFileChannel.open(
                        outputPath, 
                        StandardOpenOption.CREATE, 
                        StandardOpenOption.WRITE);
                    
                    ByteBuffer writeBuffer = ByteBuffer.wrap(processed.getBytes(StandardCharsets.UTF_8));
                    
                    outputChannel.write(writeBuffer, 0, writeBuffer, new CompletionHandler<Integer, ByteBuffer>() {
                        @Override
                        public void completed(Integer result, ByteBuffer attachment) {
                            System.out.println("✅ 步骤3: 写入完成，字节数: " + result);
                            System.out.println("✅ 链式操作全部完成！");
                            
                            closeChannel(outputChannel);
                            latch.countDown();
                        }

                        @Override
                        public void failed(Throwable exc, ByteBuffer attachment) {
                            System.out.println("❌ 步骤3失败: " + exc.getMessage());
                            closeChannel(outputChannel);
                            latch.countDown();
                        }
                    });
                    
                } catch (IOException e) {
                    System.out.println("❌ 处理失败: " + e.getMessage());
                    latch.countDown();
                }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.out.println("❌ 步骤1失败: " + exc.getMessage());
                closeChannel(inputChannel);
                latch.countDown();
            }
        });

        latch.await();
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 4. 异常处理最佳实践
     */
    private static void demonstrateExceptionHandling() throws IOException, InterruptedException {
        System.out.println("4. 异常处理最佳实践");
        System.out.println("演示: 正确处理各种异常情况\n");

        CountDownLatch latch = new CountDownLatch(2);

        // 场景1: 文件不存在
        System.out.println("场景1: 读取不存在的文件");
        try {
            Path nonExistentPath = Paths.get("non-existent-file.txt");
            AsynchronousFileChannel channel = AsynchronousFileChannel.open(
                nonExistentPath, StandardOpenOption.READ);
            
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            channel.read(buffer, 0, buffer, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    System.out.println("不应该到这里");
                    latch.countDown();
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    System.out.println("✅ 正确捕获异常: " + exc.getClass().getSimpleName());
                    System.out.println("   消息: " + exc.getMessage());
                    latch.countDown();
                }
            });
        } catch (IOException e) {
            System.out.println("✅ 打开文件时捕获异常: " + e.getClass().getSimpleName());
            latch.countDown();
        }

        // 场景2: 读取位置超出文件大小
        System.out.println("\n场景2: 读取位置超出文件大小");
        Path path = createTestFile("small-file.txt", "Small");
        AsynchronousFileChannel channel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        channel.read(buffer, 10000, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer result, ByteBuffer attachment) {
                if (result == -1) {
                    System.out.println("✅ 读取到文件末尾，返回-1");
                } else {
                    System.out.println("读取字节数: " + result);
                }
                closeChannel(channel);
                latch.countDown();
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.out.println("❌ 读取失败: " + exc.getMessage());
                closeChannel(channel);
                latch.countDown();
            }
        });

        latch.await();

        System.out.println("\n💡 异常处理要点:");
        System.out.println("1. 总是实现failed()回调");
        System.out.println("2. 在failed()中关闭资源");
        System.out.println("3. 记录详细的错误日志");
        System.out.println("4. 考虑重试机制");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 5. 避免回调地狱
     */
    private static void demonstrateAvoidCallbackHell() throws IOException, InterruptedException {
        System.out.println("5. 避免回调地狱");
        System.out.println("问题: 多层嵌套回调难以维护\n");

        System.out.println("❌ 回调地狱示例（伪代码）:");
        System.out.println("channel1.read(buffer1, handler1 {");
        System.out.println("    channel2.read(buffer2, handler2 {");
        System.out.println("        channel3.read(buffer3, handler3 {");
        System.out.println("            // 嵌套太深！");
        System.out.println("        });");
        System.out.println("    });");
        System.out.println("});");

        System.out.println("\n✅ 解决方案1: 抽取方法");
        demonstrateExtractMethod();

        System.out.println("\n✅ 解决方案2: 使用CompletableFuture");
        demonstrateCompletableFuture();

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 解决方案1: 抽取方法
     */
    private static void demonstrateExtractMethod() throws InterruptedException {
        System.out.println("使用独立的Handler类，避免嵌套");
        
        CountDownLatch latch = new CountDownLatch(1);
        
        // 创建可复用的Handler
        class ReadHandler implements CompletionHandler<Integer, ByteBuffer> {
            private final String name;
            private final Runnable onComplete;

            ReadHandler(String name, Runnable onComplete) {
                this.name = name;
                this.onComplete = onComplete;
            }

            @Override
            public void completed(Integer result, ByteBuffer attachment) {
                System.out.println("  " + name + " 完成，读取: " + result + " 字节");
                if (onComplete != null) {
                    onComplete.run();
                }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.out.println("  " + name + " 失败: " + exc.getMessage());
            }
        }

        System.out.println("  步骤1 → 步骤2 → 步骤3 (扁平化)");
        latch.countDown();
        latch.await();
    }

    /**
     * 解决方案2: 使用CompletableFuture
     */
    private static void demonstrateCompletableFuture() throws InterruptedException {
        System.out.println("使用CompletableFuture链式调用");
        System.out.println("  readFile1()");
        System.out.println("    .thenCompose(data -> processData(data))");
        System.out.println("    .thenCompose(result -> writeFile2(result))");
        System.out.println("    .exceptionally(ex -> handleError(ex))");
        System.out.println("  代码更清晰，易于维护");
    }

    /**
     * 6. 网络场景的回调
     */
    private static void demonstrateNetworkCallback() throws IOException, InterruptedException {
        System.out.println("6. 网络场景的CompletionHandler");
        System.out.println("演示: 异步服务器接受连接和读取数据\n");

        CountDownLatch serverLatch = new CountDownLatch(1);

        // 启动异步服务器
        AsynchronousServerSocketChannel serverChannel = AsynchronousServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress("localhost", 9999));

        System.out.println("服务器启动在端口 9999");

        // 异步接受连接
        serverChannel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override
            public void completed(AsynchronousSocketChannel clientChannel, Void attachment) {
                System.out.println("✅ 接受到客户端连接");

                // 继续接受下一个连接（重要！）
                serverChannel.accept(null, this);

                // 读取客户端数据
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                clientChannel.read(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
                    @Override
                    public void completed(Integer result, ByteBuffer attachment) {
                        if (result == -1) {
                            System.out.println("客户端关闭连接");
                            closeChannel(clientChannel);
                            serverLatch.countDown();
                            return;
                        }

                        System.out.println("✅ 读取到数据: " + result + " 字节");
                        
                        attachment.flip();
                        String message = StandardCharsets.UTF_8.decode(attachment).toString();
                        System.out.println("消息内容: " + message);

                        // 回写数据
                        String response = "Echo: " + message;
                        ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
                        
                        clientChannel.write(responseBuffer, responseBuffer, 
                            new CompletionHandler<Integer, ByteBuffer>() {
                            @Override
                            public void completed(Integer result, ByteBuffer attachment) {
                                System.out.println("✅ 回写完成: " + result + " 字节");
                                closeChannel(clientChannel);
                                serverLatch.countDown();
                            }

                            @Override
                            public void failed(Throwable exc, ByteBuffer attachment) {
                                System.out.println("❌ 回写失败: " + exc.getMessage());
                                closeChannel(clientChannel);
                                serverLatch.countDown();
                            }
                        });
                    }

                    @Override
                    public void failed(Throwable exc, ByteBuffer attachment) {
                        System.out.println("❌ 读取失败: " + exc.getMessage());
                        closeChannel(clientChannel);
                        serverLatch.countDown();
                    }
                });
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                System.out.println("❌ 接受连接失败: " + exc.getMessage());
                serverLatch.countDown();
            }
        });

        // 启动客户端连接
        new Thread(() -> {
            try {
                Thread.sleep(500); // 等待服务器启动
                
                AsynchronousSocketChannel client = AsynchronousSocketChannel.open();
                client.connect(new InetSocketAddress("localhost", 9999), null, 
                    new CompletionHandler<Void, Void>() {
                    @Override
                    public void completed(Void result, Void attachment) {
                        System.out.println("✅ 客户端连接成功");
                        
                        String message = "Hello, Server!";
                        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
                        
                        client.write(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
                            @Override
                            public void completed(Integer result, ByteBuffer attachment) {
                                System.out.println("✅ 客户端发送完成: " + result + " 字节");
                            }

                            @Override
                            public void failed(Throwable exc, ByteBuffer attachment) {
                                System.out.println("❌ 客户端发送失败: " + exc.getMessage());
                            }
                        });
                    }

                    @Override
                    public void failed(Throwable exc, Void attachment) {
                        System.out.println("❌ 客户端连接失败: " + exc.getMessage());
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // 等待完成
        serverLatch.await(5, TimeUnit.SECONDS);
        closeChannel(serverChannel);

        System.out.println("\n💡 网络回调要点:");
        System.out.println("1. accept()后要继续调用accept()接受下一个连接");
        System.out.println("2. 回调中可能需要继续发起异步操作");
        System.out.println("3. 注意资源管理和连接关闭");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    // ==================== 工具方法 ====================

    /**
     * 创建测试文件
     */
    private static Path createTestFile(String filename, String content) throws IOException {
        Path path = Paths.get(filename);
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        return path;
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

    /**
     * 安全关闭Channel
     */
    private static void closeChannel(AutoCloseable channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
