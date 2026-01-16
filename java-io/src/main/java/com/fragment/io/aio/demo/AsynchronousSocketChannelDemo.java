package com.fragment.io.aio.demo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

/**
 * AsynchronousSocketChannel演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>异步Echo服务器</li>
 *   <li>异步客户端（Future模式）</li>
 *   <li>异步客户端（CompletionHandler模式）</li>
 *   <li>多客户端并发连接</li>
 *   <li>请求-响应模式</li>
 * </ul>
 * 
 * @author fragment
 */
public class AsynchronousSocketChannelDemo {

    private static final int PORT = 8888;

    public static void main(String[] args) throws Exception {
        System.out.println("========== AsynchronousSocketChannel演示 ==========\n");

        // 启动服务器（在后台线程运行）
        Thread serverThread = new Thread(() -> {
            try {
                startEchoServer();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // 等待服务器启动
        Thread.sleep(1000);

        // 1. Future模式客户端
        demonstrateFutureClient();

        // 2. CompletionHandler模式客户端
        demonstrateCallbackClient();

        // 3. 多客户端并发连接
        demonstrateMultipleClients();

        // 4. 请求-响应模式
        demonstrateRequestResponse();

        System.out.println("所有演示完成！");
    }

    /**
     * 启动Echo服务器
     */
    private static void startEchoServer() throws IOException, InterruptedException {
        System.out.println("=== 启动Echo服务器 ===");
        System.out.println("监听端口: " + PORT);
        System.out.println();

        AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();
        server.bind(new InetSocketAddress(PORT));

        // 异步接受连接
        acceptConnection(server);

        // 保持服务器运行
        Thread.sleep(Long.MAX_VALUE);
    }

    /**
     * 异步接受连接
     */
    private static void acceptConnection(AsynchronousServerSocketChannel server) {
        server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override
            public void completed(AsynchronousSocketChannel client, Void attachment) {
                // 继续接受下一个连接
                acceptConnection(server);

                System.out.println("[服务器] 接受新连接: " + client);

                // 处理客户端请求
                handleClient(client);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                System.err.println("[服务器] 接受连接失败: " + exc.getMessage());
            }
        });
    }

    /**
     * 处理客户端请求
     */
    private static void handleClient(AsynchronousSocketChannel client) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        // 异步读取客户端数据
        client.read(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer attachment) {
                if (bytesRead == -1) {
                    // 客户端关闭连接
                    try {
                        client.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return;
                }

                // 处理接收到的数据
                attachment.flip();
                byte[] data = new byte[attachment.limit()];
                attachment.get(data);
                String message = new String(data, StandardCharsets.UTF_8);

                System.out.println("[服务器] 接收: " + message.trim());

                // Echo回客户端
                String response = "Echo: " + message;
                ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));

                client.write(responseBuffer, responseBuffer, 
                    new CompletionHandler<Integer, ByteBuffer>() {
                        @Override
                        public void completed(Integer bytesWritten, ByteBuffer att) {
                            System.out.println("[服务器] 发送: " + bytesWritten + " 字节");

                            // 继续读取下一条消息
                            handleClient(client);
                        }

                        @Override
                        public void failed(Throwable exc, ByteBuffer att) {
                            System.err.println("[服务器] 发送失败: " + exc.getMessage());
                        }
                    });
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("[服务器] 读取失败: " + exc.getMessage());
                try {
                    client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 1. Future模式客户端
     */
    private static void demonstrateFutureClient() throws Exception {
        System.out.println("1. Future模式客户端");
        System.out.println("特点: 使用Future.get()等待结果\n");

        // 打开异步Socket通道
        AsynchronousSocketChannel client = AsynchronousSocketChannel.open();

        System.out.println("=== 连接服务器 ===");
        Future<Void> connectFuture = client.connect(new InetSocketAddress("localhost", PORT));
        connectFuture.get();  // 等待连接完成
        System.out.println("连接成功");

        // 发送数据
        String message = "Hello from Future Client!";
        ByteBuffer sendBuffer = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));

        System.out.println("\n=== 发送数据 ===");
        System.out.println("发送: " + message);
        Future<Integer> writeFuture = client.write(sendBuffer);
        Integer bytesSent = writeFuture.get();  // 等待发送完成
        System.out.println("发送完成: " + bytesSent + " 字节");

        // 接收响应
        ByteBuffer receiveBuffer = ByteBuffer.allocate(1024);

        System.out.println("\n=== 接收响应 ===");
        Future<Integer> readFuture = client.read(receiveBuffer);
        Integer bytesRead = readFuture.get();  // 等待接收完成
        System.out.println("接收完成: " + bytesRead + " 字节");

        receiveBuffer.flip();
        String response = StandardCharsets.UTF_8.decode(receiveBuffer).toString();
        System.out.println("响应: " + response);

        client.close();
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 2. CompletionHandler模式客户端（推荐）
     */
    private static void demonstrateCallbackClient() throws Exception {
        System.out.println("2. CompletionHandler模式客户端");
        System.out.println("特点: 异步回调，不阻塞线程\n");

        CountDownLatch latch = new CountDownLatch(1);

        // 打开异步Socket通道
        AsynchronousSocketChannel client = AsynchronousSocketChannel.open();

        System.out.println("=== 连接服务器 ===");

        // 异步连接
        client.connect(new InetSocketAddress("localhost", PORT), null, 
            new CompletionHandler<Void, Void>() {
                @Override
                public void completed(Void result, Void attachment) {
                    System.out.println("连接成功");

                    // 发送数据
                    String message = "Hello from Callback Client!";
                    ByteBuffer sendBuffer = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));

                    System.out.println("\n=== 发送数据 ===");
                    System.out.println("发送: " + message);

                    client.write(sendBuffer, sendBuffer, 
                        new CompletionHandler<Integer, ByteBuffer>() {
                            @Override
                            public void completed(Integer bytesSent, ByteBuffer att) {
                                System.out.println("发送完成: " + bytesSent + " 字节");

                                // 接收响应
                                ByteBuffer receiveBuffer = ByteBuffer.allocate(1024);

                                System.out.println("\n=== 接收响应 ===");

                                client.read(receiveBuffer, receiveBuffer, 
                                    new CompletionHandler<Integer, ByteBuffer>() {
                                        @Override
                                        public void completed(Integer bytesRead, ByteBuffer att) {
                                            System.out.println("接收完成: " + bytesRead + " 字节");

                                            att.flip();
                                            String response = StandardCharsets.UTF_8.decode(att).toString();
                                            System.out.println("响应: " + response);

                                            try {
                                                client.close();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }

                                            latch.countDown();
                                        }

                                        @Override
                                        public void failed(Throwable exc, ByteBuffer att) {
                                            System.err.println("接收失败: " + exc.getMessage());
                                            latch.countDown();
                                        }
                                    });
                            }

                            @Override
                            public void failed(Throwable exc, ByteBuffer att) {
                                System.err.println("发送失败: " + exc.getMessage());
                                latch.countDown();
                            }
                        });
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    System.err.println("连接失败: " + exc.getMessage());
                    latch.countDown();
                }
            });

        // 等待完成
        latch.await();
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 3. 多客户端并发连接
     */
    private static void demonstrateMultipleClients() throws Exception {
        System.out.println("3. 多客户端并发连接");
        System.out.println("场景: 模拟多个客户端同时连接服务器\n");

        int clientCount = 5;
        CountDownLatch latch = new CountDownLatch(clientCount);

        System.out.println("=== 启动 " + clientCount + " 个客户端 ===\n");

        for (int i = 0; i < clientCount; i++) {
            final int clientId = i + 1;

            // 创建客户端
            AsynchronousSocketChannel client = AsynchronousSocketChannel.open();

            // 异步连接
            client.connect(new InetSocketAddress("localhost", PORT), null, 
                new CompletionHandler<Void, Void>() {
                    @Override
                    public void completed(Void result, Void attachment) {
                        System.out.println("客户端 " + clientId + " 连接成功");

                        // 发送消息
                        String message = "Message from Client " + clientId;
                        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));

                        client.write(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {
                            @Override
                            public void completed(Integer bytesSent, ByteBuffer att) {
                                System.out.println("客户端 " + clientId + " 发送: " + message);

                                // 接收响应
                                ByteBuffer receiveBuffer = ByteBuffer.allocate(1024);

                                client.read(receiveBuffer, receiveBuffer, 
                                    new CompletionHandler<Integer, ByteBuffer>() {
                                        @Override
                                        public void completed(Integer bytesRead, ByteBuffer att) {
                                            att.flip();
                                            String response = StandardCharsets.UTF_8.decode(att).toString();
                                            System.out.println("客户端 " + clientId + " 接收: " + response.trim());

                                            try {
                                                client.close();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }

                                            latch.countDown();
                                        }

                                        @Override
                                        public void failed(Throwable exc, ByteBuffer att) {
                                            System.err.println("客户端 " + clientId + " 接收失败");
                                            latch.countDown();
                                        }
                                    });
                            }

                            @Override
                            public void failed(Throwable exc, ByteBuffer att) {
                                System.err.println("客户端 " + clientId + " 发送失败");
                                latch.countDown();
                            }
                        });
                    }

                    @Override
                    public void failed(Throwable exc, Void attachment) {
                        System.err.println("客户端 " + clientId + " 连接失败");
                        latch.countDown();
                    }
                });

            // 稍微延迟，避免同时连接
            Thread.sleep(100);
        }

        // 等待所有客户端完成
        latch.await();
        System.out.println("\n所有客户端完成");
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 4. 请求-响应模式
     */
    private static void demonstrateRequestResponse() throws Exception {
        System.out.println("4. 请求-响应模式");
        System.out.println("场景: 发送多个请求，接收多个响应\n");

        CountDownLatch latch = new CountDownLatch(1);

        AsynchronousSocketChannel client = AsynchronousSocketChannel.open();

        System.out.println("=== 连接服务器 ===");

        client.connect(new InetSocketAddress("localhost", PORT), null, 
            new CompletionHandler<Void, Void>() {
                @Override
                public void completed(Void result, Void attachment) {
                    System.out.println("连接成功\n");

                    // 发送第一个请求
                    sendRequest(client, "Request 1", 1, 3, latch);
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    System.err.println("连接失败: " + exc.getMessage());
                    latch.countDown();
                }
            });

        // 等待完成
        latch.await();
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 发送请求并接收响应
     */
    private static void sendRequest(AsynchronousSocketChannel client, String message, 
                                    int requestNum, int totalRequests, CountDownLatch latch) {
        System.out.println("=== 请求 " + requestNum + " ===");
        System.out.println("发送: " + message);

        ByteBuffer sendBuffer = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));

        client.write(sendBuffer, sendBuffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesSent, ByteBuffer att) {
                // 接收响应
                ByteBuffer receiveBuffer = ByteBuffer.allocate(1024);

                client.read(receiveBuffer, receiveBuffer, 
                    new CompletionHandler<Integer, ByteBuffer>() {
                        @Override
                        public void completed(Integer bytesRead, ByteBuffer att) {
                            att.flip();
                            String response = StandardCharsets.UTF_8.decode(att).toString();
                            System.out.println("接收: " + response.trim());
                            System.out.println();

                            // 发送下一个请求
                            if (requestNum < totalRequests) {
                                try {
                                    Thread.sleep(500);  // 延迟一下
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                sendRequest(client, "Request " + (requestNum + 1), 
                                          requestNum + 1, totalRequests, latch);
                            } else {
                                // 所有请求完成
                                try {
                                    client.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                latch.countDown();
                            }
                        }

                        @Override
                        public void failed(Throwable exc, ByteBuffer att) {
                            System.err.println("接收失败: " + exc.getMessage());
                            latch.countDown();
                        }
                    });
            }

            @Override
            public void failed(Throwable exc, ByteBuffer att) {
                System.err.println("发送失败: " + exc.getMessage());
                latch.countDown();
            }
        });
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
