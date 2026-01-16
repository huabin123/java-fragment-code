package com.fragment.io.nio.project;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;

/**
 * NIO聊天室客户端
 * 
 * <p>功能特性：
 * <ul>
 *   <li>连接到聊天服务器</li>
 *   <li>发送和接收消息</li>
 *   <li>支持命令和私聊</li>
 * </ul>
 * 
 * @author fragment
 */
public class NIOChatClient {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8888;
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    
    private Selector selector;
    private SocketChannel socketChannel;
    private ByteBuffer readBuffer = ByteBuffer.allocate(1024);
    private volatile boolean running = true;

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        
        if (args.length >= 1) {
            host = args[0];
        }
        if (args.length >= 2) {
            port = Integer.parseInt(args[1]);
        }
        
        try {
            new NIOChatClient().start(host, port);
        } catch (IOException e) {
            System.err.println("客户端启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 启动客户端
     */
    public void start(String host, int port) throws IOException {
        selector = Selector.open();
        
        socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(new InetSocketAddress(host, port));
        socketChannel.register(selector, SelectionKey.OP_CONNECT);
        
        System.out.println("正在连接到服务器 " + host + ":" + port + "...");
        
        // 启动接收消息线程
        new Thread(this::receiveMessages).start();
        
        // 主线程处理用户输入
        handleUserInput();
    }

    /**
     * 接收服务器消息（在独立线程中运行）
     */
    private void receiveMessages() {
        try {
            while (running) {
                selector.select(1000);
                
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();
                
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    
                    if (!key.isValid()) {
                        continue;
                    }
                    
                    if (key.isConnectable()) {
                        handleConnect(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("接收消息时发生错误: " + e.getMessage());
        }
    }

    /**
     * 处理连接事件
     */
    private void handleConnect(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        
        if (channel.finishConnect()) {
            System.out.println("连接成功！");
            
            // 注册读事件
            channel.register(selector, SelectionKey.OP_READ);
        }
    }

    /**
     * 处理读事件
     */
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        
        readBuffer.clear();
        int bytesRead = channel.read(readBuffer);
        
        if (bytesRead == -1) {
            System.out.println("\n服务器已断开连接");
            running = false;
            channel.close();
            return;
        }
        
        if (bytesRead > 0) {
            readBuffer.flip();
            String message = CHARSET.decode(readBuffer).toString();
            System.out.print(message);
        }
    }

    /**
     * 处理用户输入（在主线程中运行）
     */
    private void handleUserInput() {
        Scanner scanner = new Scanner(System.in);
        
        try {
            while (running) {
                if (scanner.hasNextLine()) {
                    String input = scanner.nextLine();
                    
                    if (input.isEmpty()) {
                        continue;
                    }
                    
                    // 发送消息
                    sendMessage(input + "\n");
                    
                    // 如果是退出命令，关闭客户端
                    if (input.equalsIgnoreCase("/quit")) {
                        running = false;
                        break;
                    }
                }
            }
        } finally {
            scanner.close();
            close();
        }
    }

    /**
     * 发送消息到服务器
     */
    private void sendMessage(String message) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(message.getBytes(CHARSET));
            
            while (buffer.hasRemaining()) {
                socketChannel.write(buffer);
            }
        } catch (IOException e) {
            System.err.println("发送消息失败: " + e.getMessage());
        }
    }

    /**
     * 关闭客户端
     */
    private void close() {
        try {
            if (socketChannel != null) {
                socketChannel.close();
            }
            if (selector != null) {
                selector.close();
            }
            System.out.println("客户端已关闭");
        } catch (IOException e) {
            System.err.println("关闭客户端时发生错误: " + e.getMessage());
        }
    }
}
