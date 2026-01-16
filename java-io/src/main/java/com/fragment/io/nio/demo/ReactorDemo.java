package com.fragment.io.nio.demo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reactor模式演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>单Reactor单线程模式</li>
 *   <li>单Reactor多线程模式</li>
 *   <li>主从Reactor多线程模式</li>
 * </ul>
 * 
 * @author fragment
 */
public class ReactorDemo {

    public static void main(String[] args) throws IOException {
        System.out.println("========== Reactor模式演示 ==========\n");
        
        // 演示1：单Reactor单线程
        System.out.println("演示1：单Reactor单线程模式");
        System.out.println("启动命令：java ReactorDemo single 8001");
        System.out.println("测试命令：telnet localhost 8001\n");
        
        // 演示2：单Reactor多线程
        System.out.println("演示2：单Reactor多线程模式");
        System.out.println("启动命令：java ReactorDemo multi 8002");
        System.out.println("测试命令：telnet localhost 8002\n");
        
        // 演示3：主从Reactor多线程
        System.out.println("演示3：主从Reactor多线程模式");
        System.out.println("启动命令：java ReactorDemo master-slave 8003");
        System.out.println("测试命令：telnet localhost 8003\n");
        
        System.out.println("========================================\n");
        
        // 根据参数启动不同模式
        if (args.length < 2) {
            System.out.println("使用默认模式：主从Reactor多线程，端口：8080");
            new MasterSlaveReactor().start(8080);
        } else {
            String mode = args[0];
            int port = Integer.parseInt(args[1]);
            
            switch (mode) {
                case "single":
                    new SingleThreadReactor().start(port);
                    break;
                case "multi":
                    new MultiThreadReactor().start(port);
                    break;
                case "master-slave":
                    new MasterSlaveReactor().start(port);
                    break;
                default:
                    System.out.println("未知模式: " + mode);
                    System.out.println("支持的模式: single, multi, master-slave");
            }
        }
    }

    /**
     * 单Reactor单线程模型
     * 
     * <p>特点：
     * <ul>
     *   <li>所有I/O事件在一个线程中处理</li>
     *   <li>简单但性能有限</li>
     *   <li>适合小规模应用</li>
     * </ul>
     */
    static class SingleThreadReactor {
        
        private Selector selector;
        private ServerSocketChannel serverChannel;
        
        public void start(int port) throws IOException {
            selector = Selector.open();
            
            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.configureBlocking(false);
            
            // 注册Acceptor
            serverChannel.register(selector, SelectionKey.OP_ACCEPT, new Acceptor());
            
            System.out.println("========================================");
            System.out.println("单Reactor单线程服务器启动");
            System.out.println("监听端口: " + port);
            System.out.println("线程模型: 单线程处理所有I/O事件");
            System.out.println("========================================\n");
            
            // 事件循环（单线程）
            while (true) {
                selector.select();
                
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();
                
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    
                    // 分发事件
                    dispatch(key);
                }
            }
        }
        
        private void dispatch(SelectionKey key) {
            Runnable handler = (Runnable) key.attachment();
            if (handler != null) {
                handler.run();  // 在当前线程执行
            }
        }
        
        // Acceptor：处理连接事件
        class Acceptor implements Runnable {
            @Override
            public void run() {
                try {
                    SocketChannel channel = serverChannel.accept();
                    if (channel != null) {
                        System.out.println("[单Reactor单线程] 接受连接: " + channel.getRemoteAddress());
                        new SingleThreadHandler(selector, channel);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        // Handler：处理读写事件
        class SingleThreadHandler implements Runnable {
            private SocketChannel channel;
            private SelectionKey key;
            private ByteBuffer buffer = ByteBuffer.allocate(1024);
            
            public SingleThreadHandler(Selector selector, SocketChannel channel) throws IOException {
                this.channel = channel;
                channel.configureBlocking(false);
                
                // 注册读事件
                key = channel.register(selector, SelectionKey.OP_READ);
                key.attach(this);
                
                selector.wakeup();
            }
            
            @Override
            public void run() {
                try {
                    if (key.isReadable()) {
                        read();
                    } else if (key.isWritable()) {
                        write();
                    }
                } catch (IOException e) {
                    close();
                }
            }
            
            private void read() throws IOException {
                buffer.clear();
                int len = channel.read(buffer);
                
                if (len == -1) {
                    System.out.println("[单Reactor单线程] 连接关闭: " + channel.getRemoteAddress());
                    close();
                    return;
                }
                
                if (len > 0) {
                    buffer.flip();
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    String message = new String(data).trim();
                    
                    System.out.println("[单Reactor单线程] 收到消息: " + message);
                    
                    // 处理数据（在当前线程）
                    process(message);
                }
            }
            
            private void process(String message) {
                // 模拟业务处理
                String response = "Echo: " + message + "\n";
                
                buffer.clear();
                buffer.put(response.getBytes());
                buffer.flip();
                
                // 切换到写模式
                key.interestOps(SelectionKey.OP_WRITE);
            }
            
            private void write() throws IOException {
                channel.write(buffer);
                
                if (!buffer.hasRemaining()) {
                    key.interestOps(SelectionKey.OP_READ);
                }
            }
            
            private void close() {
                try {
                    key.cancel();
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 单Reactor多线程模型
     * 
     * <p>特点：
     * <ul>
     *   <li>I/O事件在主线程处理</li>
     *   <li>业务逻辑在线程池处理</li>
     *   <li>充分利用多核CPU</li>
     * </ul>
     */
    static class MultiThreadReactor {
        
        private Selector selector;
        private ServerSocketChannel serverChannel;
        private ExecutorService threadPool;
        
        public void start(int port) throws IOException {
            selector = Selector.open();
            
            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.configureBlocking(false);
            
            serverChannel.register(selector, SelectionKey.OP_ACCEPT, new Acceptor());
            
            // 创建线程池
            int threadCount = Runtime.getRuntime().availableProcessors() * 2;
            threadPool = Executors.newFixedThreadPool(threadCount);
            
            System.out.println("========================================");
            System.out.println("单Reactor多线程服务器启动");
            System.out.println("监听端口: " + port);
            System.out.println("线程模型: 主线程处理I/O，线程池处理业务");
            System.out.println("工作线程数: " + threadCount);
            System.out.println("========================================\n");
            
            // 事件循环（主线程）
            while (true) {
                selector.select();
                
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();
                
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    
                    dispatch(key);
                }
            }
        }
        
        private void dispatch(SelectionKey key) {
            Runnable handler = (Runnable) key.attachment();
            if (handler != null) {
                handler.run();
            }
        }
        
        class Acceptor implements Runnable {
            @Override
            public void run() {
                try {
                    SocketChannel channel = serverChannel.accept();
                    if (channel != null) {
                        System.out.println("[单Reactor多线程] 接受连接: " + channel.getRemoteAddress());
                        new MultiThreadHandler(selector, channel, threadPool);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        // Handler：I/O在主线程，业务处理在工作线程
        class MultiThreadHandler implements Runnable {
            private SocketChannel channel;
            private SelectionKey key;
            private ByteBuffer buffer = ByteBuffer.allocate(1024);
            private ExecutorService threadPool;
            
            private static final int READING = 0;
            private static final int PROCESSING = 1;
            private static final int WRITING = 2;
            private int state = READING;
            
            public MultiThreadHandler(Selector selector, SocketChannel channel, 
                                     ExecutorService threadPool) throws IOException {
                this.channel = channel;
                this.threadPool = threadPool;
                
                channel.configureBlocking(false);
                key = channel.register(selector, SelectionKey.OP_READ);
                key.attach(this);
                
                selector.wakeup();
            }
            
            @Override
            public void run() {
                try {
                    if (state == READING) {
                        read();
                    } else if (state == WRITING) {
                        write();
                    }
                } catch (IOException e) {
                    close();
                }
            }
            
            private void read() throws IOException {
                buffer.clear();
                int len = channel.read(buffer);
                
                if (len == -1) {
                    System.out.println("[单Reactor多线程] 连接关闭: " + channel.getRemoteAddress());
                    close();
                    return;
                }
                
                if (len > 0) {
                    buffer.flip();
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    String message = new String(data).trim();
                    
                    System.out.println("[单Reactor多线程] 收到消息: " + message + 
                                     " [线程: " + Thread.currentThread().getName() + "]");
                    
                    state = PROCESSING;
                    
                    // 提交到线程池处理（异步）
                    threadPool.submit(() -> {
                        process(message);
                    });
                }
            }
            
            private void process(String message) {
                // 业务处理（在工作线程）
                System.out.println("[单Reactor多线程] 处理消息: " + message + 
                                 " [线程: " + Thread.currentThread().getName() + "]");
                
                // 模拟耗时操作
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
                String response = "Echo: " + message + "\n";
                
                buffer.clear();
                buffer.put(response.getBytes());
                buffer.flip();
                
                // 处理完成，切换到写模式
                state = WRITING;
                key.interestOps(SelectionKey.OP_WRITE);
                key.selector().wakeup();
            }
            
            private void write() throws IOException {
                channel.write(buffer);
                
                if (!buffer.hasRemaining()) {
                    state = READING;
                    key.interestOps(SelectionKey.OP_READ);
                }
            }
            
            private void close() {
                try {
                    key.cancel();
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 主从Reactor多线程模型（Netty模型）
     * 
     * <p>特点：
     * <ul>
     *   <li>主Reactor负责接收连接</li>
     *   <li>从Reactor负责处理I/O事件</li>
     *   <li>充分利用多核，负载均衡</li>
     * </ul>
     */
    static class MasterSlaveReactor {
        
        private Selector mainSelector;      // 主Reactor的Selector
        private ServerSocketChannel serverChannel;
        private SubReactor[] subReactors;   // 从Reactor数组
        private int next = 0;               // 轮询索引
        
        public void start(int port) throws IOException {
            // 1. 创建主Reactor
            mainSelector = Selector.open();
            
            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.configureBlocking(false);
            
            serverChannel.register(mainSelector, SelectionKey.OP_ACCEPT);
            
            // 2. 创建从Reactor线程池
            int subReactorCount = Runtime.getRuntime().availableProcessors();
            subReactors = new SubReactor[subReactorCount];
            
            for (int i = 0; i < subReactorCount; i++) {
                subReactors[i] = new SubReactor(i);
                new Thread(subReactors[i], "SubReactor-" + i).start();
            }
            
            System.out.println("========================================");
            System.out.println("主从Reactor多线程服务器启动");
            System.out.println("监听端口: " + port);
            System.out.println("线程模型: 主Reactor接收连接，从Reactor处理I/O");
            System.out.println("SubReactor数量: " + subReactorCount);
            System.out.println("========================================\n");
            
            // 3. 主Reactor事件循环
            while (true) {
                mainSelector.select();
                
                Set<SelectionKey> keys = mainSelector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();
                
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    
                    if (key.isAcceptable()) {
                        acceptConnection();
                    }
                }
            }
        }
        
        // 接收连接并分发到SubReactor
        private void acceptConnection() throws IOException {
            SocketChannel channel = serverChannel.accept();
            if (channel != null) {
                System.out.println("[主Reactor] 接受连接: " + channel.getRemoteAddress());
                
                // 轮询分发到SubReactor
                SubReactor subReactor = subReactors[next];
                System.out.println("[主Reactor] 分发到: SubReactor-" + next);
                next = (next + 1) % subReactors.length;
                
                subReactor.registerChannel(channel);
            }
        }
        
        // 从Reactor：处理I/O事件
        static class SubReactor implements Runnable {
            private int id;
            private Selector selector;
            private Queue<SocketChannel> pendingChannels = new ConcurrentLinkedQueue<>();
            
            public SubReactor(int id) throws IOException {
                this.id = id;
                this.selector = Selector.open();
            }
            
            // 注册Channel（由主Reactor调用）
            public void registerChannel(SocketChannel channel) {
                pendingChannels.offer(channel);
                selector.wakeup();  // 唤醒selector
            }
            
            @Override
            public void run() {
                System.out.println("[SubReactor-" + id + "] 启动");
                
                while (true) {
                    try {
                        // 处理待注册的Channel
                        registerPendingChannels();
                        
                        // 等待I/O事件
                        selector.select();
                        
                        // 处理就绪事件
                        Set<SelectionKey> keys = selector.selectedKeys();
                        Iterator<SelectionKey> iterator = keys.iterator();
                        
                        while (iterator.hasNext()) {
                            SelectionKey key = iterator.next();
                            iterator.remove();
                            
                            Handler handler = (Handler) key.attachment();
                            if (handler != null) {
                                handler.handle(key);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            
            private void registerPendingChannels() throws IOException {
                SocketChannel channel;
                while ((channel = pendingChannels.poll()) != null) {
                    channel.configureBlocking(false);
                    SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
                    key.attach(new Handler(id, channel));
                    
                    System.out.println("[SubReactor-" + id + "] 注册连接: " + channel.getRemoteAddress());
                }
            }
        }
        
        // Handler：处理读写事件
        static class Handler {
            private int reactorId;
            private SocketChannel channel;
            private ByteBuffer buffer = ByteBuffer.allocate(1024);
            
            public Handler(int reactorId, SocketChannel channel) {
                this.reactorId = reactorId;
                this.channel = channel;
            }
            
            public void handle(SelectionKey key) {
                try {
                    if (key.isReadable()) {
                        read(key);
                    } else if (key.isWritable()) {
                        write(key);
                    }
                } catch (IOException e) {
                    close(key);
                }
            }
            
            private void read(SelectionKey key) throws IOException {
                buffer.clear();
                int len = channel.read(buffer);
                
                if (len == -1) {
                    System.out.println("[SubReactor-" + reactorId + "] 连接关闭: " + 
                                     channel.getRemoteAddress());
                    close(key);
                    return;
                }
                
                if (len > 0) {
                    buffer.flip();
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    String message = new String(data).trim();
                    
                    System.out.println("[SubReactor-" + reactorId + "] 收到消息: " + message);
                    
                    // 处理数据
                    String response = "Echo: " + message + "\n";
                    
                    buffer.clear();
                    buffer.put(response.getBytes());
                    buffer.flip();
                    
                    // 切换到写模式
                    key.interestOps(SelectionKey.OP_WRITE);
                }
            }
            
            private void write(SelectionKey key) throws IOException {
                channel.write(buffer);
                
                if (!buffer.hasRemaining()) {
                    key.interestOps(SelectionKey.OP_READ);
                }
            }
            
            private void close(SelectionKey key) {
                try {
                    key.cancel();
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
