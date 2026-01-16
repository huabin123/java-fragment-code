package com.fragment.io.nio.demo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

/**
 * Selector多路复用演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>Selector的创建和使用</li>
 *   <li>Channel注册到Selector</li>
 *   <li>事件循环和事件处理</li>
 *   <li>OP_ACCEPT、OP_READ、OP_WRITE事件</li>
 *   <li>完整的NIO Echo服务器</li>
 * </ul>
 * 
 * @author fragment
 */
public class SelectorDemo {

    private static final int PORT = 8080;

    public static void main(String[] args) {
        System.out.println("========== Selector多路复用演示 ==========\n");
        System.out.println("NIO Echo服务器启动在端口: " + PORT);
        System.out.println("使用telnet或nc命令连接测试:");
        System.out.println("  telnet localhost " + PORT);
        System.out.println("  或");
        System.out.println("  nc localhost " + PORT);
        System.out.println("\n按Ctrl+C停止服务器\n");
        System.out.println(createSeparator(60) + "\n");

        try {
            new NIOEchoServer().start(PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * NIO Echo服务器
     * 演示Selector的完整使用流程
     */
    static class NIOEchoServer {

        private Selector selector;
        private ServerSocketChannel serverChannel;
        private ByteBuffer readBuffer = ByteBuffer.allocate(8192);

        /**
         * 启动服务器
         */
        public void start(int port) throws IOException {
            // 1. 创建Selector
            selector = Selector.open();
            System.out.println("[服务器] Selector创建成功");

            // 2. 创建ServerSocketChannel
            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.configureBlocking(false); // 设置为非阻塞

            System.out.println("[服务器] ServerSocketChannel创建成功");
            System.out.println("[服务器] 绑定端口: " + port);
            System.out.println("[服务器] 非阻塞模式: " + !serverChannel.isBlocking());

            // 3. 注册ACCEPT事件
            SelectionKey serverKey = serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("[服务器] 注册OP_ACCEPT事件");
            System.out.println("[服务器] SelectionKey: " + serverKey);
            System.out.println();

            // 4. 事件循环
            System.out.println("[服务器] 开始事件循环...\n");
            eventLoop();
        }

        /**
         * 事件循环
         */
        private void eventLoop() throws IOException {
            int loopCount = 0;

            while (true) {
                // 阻塞等待事件（1秒超时）
                int readyCount = selector.select(1000);

                loopCount++;
                if (readyCount == 0) {
                    // 超时，没有就绪事件
                    if (loopCount % 10 == 0) {
                        System.out.println("[事件循环] 等待事件中... (循环次数: " + loopCount + ")");
                    }
                    continue;
                }

                // 有就绪事件
                System.out.println("\n[事件循环] 检测到 " + readyCount + " 个就绪事件");

                // 获取就绪的SelectionKey集合
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();

                // 遍历处理每个就绪事件
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();

                    // 必须移除已处理的key
                    iterator.remove();

                    // 检查key是否有效
                    if (!key.isValid()) {
                        System.out.println("[事件循环] SelectionKey无效，跳过");
                        continue;
                    }

                    try {
                        // 根据事件类型分发处理
                        if (key.isAcceptable()) {
                            handleAccept(key);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        } else if (key.isWritable()) {
                            handleWrite(key);
                        }
                    } catch (IOException e) {
                        System.err.println("[错误] 处理事件异常: " + e.getMessage());
                        closeChannel(key);
                    }
                }
            }
        }

        /**
         * 处理连接事件（OP_ACCEPT）
         */
        private void handleAccept(SelectionKey key) throws IOException {
            System.out.println("\n[OP_ACCEPT] 处理连接事件");

            ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();

            // 接受连接
            SocketChannel clientChannel = serverChannel.accept();
            if (clientChannel == null) {
                System.out.println("[OP_ACCEPT] 没有连接可接受");
                return;
            }

            // 设置为非阻塞
            clientChannel.configureBlocking(false);

            // 注册READ事件
            SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);

            System.out.println("[OP_ACCEPT] 接受连接: " + clientChannel.getRemoteAddress());
            System.out.println("[OP_ACCEPT] 注册OP_READ事件");
            System.out.println("[OP_ACCEPT] 当前连接数: " + (selector.keys().size() - 1));
        }

        /**
         * 处理读事件（OP_READ）
         */
        private void handleRead(SelectionKey key) throws IOException {
            SocketChannel channel = (SocketChannel) key.channel();

            System.out.println("\n[OP_READ] 处理读事件: " + channel.getRemoteAddress());

            // 清空读缓冲区
            readBuffer.clear();

            // 读取数据
            int bytesRead = channel.read(readBuffer);

            if (bytesRead == -1) {
                // 连接关闭
                System.out.println("[OP_READ] 连接关闭: " + channel.getRemoteAddress());
                closeChannel(key);
                return;
            }

            if (bytesRead == 0) {
                // 没有数据可读
                System.out.println("[OP_READ] 没有数据可读");
                return;
            }

            System.out.println("[OP_READ] 读取字节数: " + bytesRead);

            // 切换到读模式
            readBuffer.flip();

            // 解析数据
            String message = StandardCharsets.UTF_8.decode(readBuffer).toString();
            System.out.println("[OP_READ] 收到消息: " + message.trim());

            // 准备回写数据（Echo）
            ByteBuffer writeBuffer = ByteBuffer.allocate(bytesRead);
            readBuffer.rewind(); // 重新读取
            writeBuffer.put(readBuffer);
            writeBuffer.flip();

            // 尝试直接写入
            int bytesWritten = channel.write(writeBuffer);
            System.out.println("[OP_READ] 直接写入字节数: " + bytesWritten);

            if (writeBuffer.hasRemaining()) {
                // 写缓冲区满，无法一次写完
                System.out.println("[OP_READ] 写缓冲区满，注册OP_WRITE事件");

                // 将剩余数据附加到key
                key.attach(writeBuffer);

                // 注册WRITE事件
                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            } else {
                System.out.println("[OP_READ] 数据写入完成");
            }
        }

        /**
         * 处理写事件（OP_WRITE）
         */
        private void handleWrite(SelectionKey key) throws IOException {
            SocketChannel channel = (SocketChannel) key.channel();

            System.out.println("\n[OP_WRITE] 处理写事件: " + channel.getRemoteAddress());

            // 获取附加的写缓冲区
            ByteBuffer writeBuffer = (ByteBuffer) key.attachment();

            if (writeBuffer == null) {
                System.out.println("[OP_WRITE] 没有待写入数据");
                key.interestOps(SelectionKey.OP_READ);
                return;
            }

            // 继续写入
            int bytesWritten = channel.write(writeBuffer);
            System.out.println("[OP_WRITE] 写入字节数: " + bytesWritten);

            if (!writeBuffer.hasRemaining()) {
                // 写完了，取消WRITE事件
                System.out.println("[OP_WRITE] 数据写入完成，取消OP_WRITE事件");

                key.attach(null); // 清除附件
                key.interestOps(SelectionKey.OP_READ); // 只关注READ事件
            } else {
                System.out.println("[OP_WRITE] 还有 " + writeBuffer.remaining() + " 字节待写入");
            }
        }

        /**
         * 关闭连接
         */
        private void closeChannel(SelectionKey key) {
            try {
                SocketChannel channel = (SocketChannel) key.channel();
                System.out.println("[关闭连接] " + channel.getRemoteAddress());

                key.cancel(); // 取消注册
                channel.close(); // 关闭通道

                System.out.println("[关闭连接] 当前连接数: " + (selector.keys().size() - 1));
            } catch (IOException e) {
                System.err.println("[错误] 关闭连接异常: " + e.getMessage());
            }
        }
    }

    /**
     * 简单的测试客户端
     */
    static class SimpleClient {
        public static void main(String[] args) {
            try {
                SocketChannel channel = SocketChannel.open();
                channel.connect(new InetSocketAddress("localhost", PORT));

                System.out.println("连接到服务器: localhost:" + PORT);

                // 发送消息
                String message = "Hello NIO Server!\n";
                ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
                channel.write(buffer);
                System.out.println("发送: " + message.trim());

                // 接收响应
                buffer = ByteBuffer.allocate(1024);
                int len = channel.read(buffer);
                buffer.flip();
                String response = StandardCharsets.UTF_8.decode(buffer).toString();
                System.out.println("收到: " + response.trim());

                channel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
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
