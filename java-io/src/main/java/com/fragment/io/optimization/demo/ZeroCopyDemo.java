package com.fragment.io.optimization.demo;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.DefaultFileRegion;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 * 零拷贝演示
 * 
 * 演示内容：
 * 1. 传统IO vs 零拷贝性能对比
 * 2. mmap内存映射
 * 3. FileChannel.transferTo
 * 4. DirectBuffer
 * 5. Netty零拷贝（CompositeByteBuf、slice、wrap）
 * 
 * @author fragment
 */
public class ZeroCopyDemo {

    private static final String TEST_FILE = "test_data.txt";
    private static final int FILE_SIZE = 10 * 1024 * 1024; // 10MB

    public static void main(String[] args) throws Exception {
        System.out.println("=== 零拷贝演示 ===\n");
        
        // 创建测试文件
        createTestFile();
        
        // 1. 传统IO vs 零拷贝性能对比
        System.out.println("--- 性能对比测试 ---");
        performanceComparison();
        
        // 2. mmap演示
        System.out.println("\n--- mmap内存映射演示 ---");
        mmapDemo();
        
        // 3. DirectBuffer演示
        System.out.println("\n--- DirectBuffer演示 ---");
        directBufferDemo();
        
        // 4. Netty零拷贝演示
        System.out.println("\n--- Netty零拷贝演示 ---");
        nettyZeroCopyDemo();
        
        // 清理测试文件
        new File(TEST_FILE).delete();
    }
    
    /**
     * 创建测试文件
     */
    private static void createTestFile() throws IOException {
        System.out.println("创建测试文件: " + TEST_FILE + " (10MB)");
        
        try (RandomAccessFile file = new RandomAccessFile(TEST_FILE, "rw")) {
            file.setLength(FILE_SIZE);
            
            // 写入一些数据
            byte[] data = "Hello Zero Copy! ".getBytes();
            for (int i = 0; i < 1000; i++) {
                file.write(data);
            }
        }
        
        System.out.println("测试文件创建完成\n");
    }
    
    /**
     * 性能对比测试
     */
    private static void performanceComparison() throws Exception {
        // 测试1：传统IO读取
        long time1 = testTraditionalIO();
        System.out.println("传统IO读取: " + time1 + "ms");
        
        // 测试2：mmap读取
        long time2 = testMmap();
        System.out.println("mmap读取:   " + time2 + "ms");
        
        // 测试3：DirectBuffer读取
        long time3 = testDirectBuffer();
        System.out.println("DirectBuffer: " + time3 + "ms");
        
        System.out.println("\n性能提升:");
        System.out.println("  mmap vs 传统IO: " + (time1 * 100 / time2) + "%");
        System.out.println("  DirectBuffer vs 传统IO: " + (time1 * 100 / time3) + "%");
    }
    
    /**
     * 传统IO读取测试
     */
    private static long testTraditionalIO() throws IOException {
        long start = System.currentTimeMillis();
        
        try (FileInputStream fis = new FileInputStream(TEST_FILE)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytes = 0;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                totalBytes += bytesRead;
                // 模拟处理数据
            }
        }
        
        return System.currentTimeMillis() - start;
    }
    
    /**
     * mmap读取测试
     */
    private static long testMmap() throws IOException {
        long start = System.currentTimeMillis();
        
        try (RandomAccessFile file = new RandomAccessFile(TEST_FILE, "r");
             FileChannel channel = file.getChannel()) {
            
            MappedByteBuffer buffer = channel.map(
                FileChannel.MapMode.READ_ONLY, 0, channel.size());
            
            long totalBytes = 0;
            while (buffer.hasRemaining()) {
                buffer.get();
                totalBytes++;
            }
        }
        
        return System.currentTimeMillis() - start;
    }
    
    /**
     * DirectBuffer读取测试
     */
    private static long testDirectBuffer() throws IOException {
        long start = System.currentTimeMillis();
        
        try (RandomAccessFile file = new RandomAccessFile(TEST_FILE, "r");
             FileChannel channel = file.getChannel()) {
            
            ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
            long totalBytes = 0;
            
            while (channel.read(buffer) != -1) {
                buffer.flip();
                totalBytes += buffer.remaining();
                buffer.clear();
            }
        }
        
        return System.currentTimeMillis() - start;
    }
    
    /**
     * mmap演示
     */
    private static void mmapDemo() throws IOException {
        System.out.println("使用mmap映射文件到内存");
        
        try (RandomAccessFile file = new RandomAccessFile(TEST_FILE, "r");
             FileChannel channel = file.getChannel()) {
            
            // 映射文件到内存
            long fileSize = channel.size();
            MappedByteBuffer buffer = channel.map(
                FileChannel.MapMode.READ_ONLY, 0, Math.min(1024, fileSize));
            
            // 读取前100个字节
            byte[] data = new byte[100];
            buffer.get(data);
            
            System.out.println("读取的数据: " + new String(data, StandardCharsets.UTF_8).substring(0, 50) + "...");
            System.out.println("映射缓冲区容量: " + buffer.capacity());
            System.out.println("映射缓冲区位置: " + buffer.position());
        }
    }
    
    /**
     * DirectBuffer演示
     */
    private static void directBufferDemo() {
        System.out.println("DirectBuffer vs HeapBuffer");
        
        // 堆内存Buffer
        ByteBuffer heapBuffer = ByteBuffer.allocate(1024);
        System.out.println("HeapBuffer:");
        System.out.println("  isDirect: " + heapBuffer.isDirect());
        System.out.println("  capacity: " + heapBuffer.capacity());
        
        // 直接内存Buffer
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024);
        System.out.println("\nDirectBuffer:");
        System.out.println("  isDirect: " + directBuffer.isDirect());
        System.out.println("  capacity: " + directBuffer.capacity());
        
        // 写入数据
        String message = "Hello DirectBuffer!";
        directBuffer.put(message.getBytes());
        directBuffer.flip();
        
        // 读取数据
        byte[] data = new byte[directBuffer.remaining()];
        directBuffer.get(data);
        System.out.println("  数据: " + new String(data));
        
        // DirectBuffer的优势：减少内存拷贝
        System.out.println("\nDirectBuffer优势:");
        System.out.println("  - 减少JVM堆到内核缓冲区的拷贝");
        System.out.println("  - 适合频繁的I/O操作");
        System.out.println("  - 不受GC影响");
    }
    
    /**
     * Netty零拷贝演示
     */
    private static void nettyZeroCopyDemo() {
        // 1. CompositeByteBuf - 组合多个ByteBuf，避免拷贝
        System.out.println("1. CompositeByteBuf演示");
        compositeByteBufDemo();
        
        // 2. slice - 切片共享底层数组
        System.out.println("\n2. slice演示");
        sliceDemo();
        
        // 3. wrap - 包装现有数组，避免拷贝
        System.out.println("\n3. wrap演示");
        wrapDemo();
    }
    
    /**
     * CompositeByteBuf演示
     */
    private static void compositeByteBufDemo() {
        // 传统方式：需要拷贝
        ByteBuf header = Unpooled.copiedBuffer("Header: ", StandardCharsets.UTF_8);
        ByteBuf body = Unpooled.copiedBuffer("Body Content", StandardCharsets.UTF_8);
        
        System.out.println("传统方式（需要拷贝）:");
        ByteBuf merged = Unpooled.buffer(header.readableBytes() + body.readableBytes());
        merged.writeBytes(header.duplicate());
        merged.writeBytes(body.duplicate());
        System.out.println("  合并后: " + merged.toString(StandardCharsets.UTF_8));
        merged.release();
        
        // Netty零拷贝方式：不需要拷贝
        System.out.println("\nNetty零拷贝方式（不需要拷贝）:");
        CompositeByteBuf composite = Unpooled.compositeBuffer();
        composite.addComponents(true, header, body);
        System.out.println("  合并后: " + composite.toString(StandardCharsets.UTF_8));
        System.out.println("  组件数量: " + composite.numComponents());
        System.out.println("  总可读字节: " + composite.readableBytes());
        
        composite.release();
    }
    
    /**
     * slice演示
     */
    private static void sliceDemo() {
        ByteBuf buffer = Unpooled.copiedBuffer("Hello World", StandardCharsets.UTF_8);
        
        System.out.println("原始buffer: " + buffer.toString(StandardCharsets.UTF_8));
        
        // 创建切片（共享底层数组）
        ByteBuf slice1 = buffer.slice(0, 5);
        ByteBuf slice2 = buffer.slice(6, 5);
        
        System.out.println("slice1: " + slice1.toString(StandardCharsets.UTF_8));
        System.out.println("slice2: " + slice2.toString(StandardCharsets.UTF_8));
        
        // 修改切片会影响原始buffer
        slice1.setByte(0, 'h');
        System.out.println("修改slice1后:");
        System.out.println("  原始buffer: " + buffer.toString(StandardCharsets.UTF_8));
        System.out.println("  slice1: " + slice1.toString(StandardCharsets.UTF_8));
        
        buffer.release();
    }
    
    /**
     * wrap演示
     */
    private static void wrapDemo() {
        byte[] array = "Hello Wrap".getBytes(StandardCharsets.UTF_8);
        
        System.out.println("传统方式（拷贝数据）:");
        ByteBuf buffer1 = Unpooled.buffer(array.length);
        buffer1.writeBytes(array);
        System.out.println("  buffer1: " + buffer1.toString(StandardCharsets.UTF_8));
        buffer1.release();
        
        System.out.println("\n零拷贝方式（包装数组）:");
        ByteBuf buffer2 = Unpooled.wrappedBuffer(array);
        System.out.println("  buffer2: " + buffer2.toString(StandardCharsets.UTF_8));
        
        // 修改原数组会影响buffer2
        array[0] = 'h';
        System.out.println("修改原数组后:");
        System.out.println("  buffer2: " + buffer2.toString(StandardCharsets.UTF_8));
        
        buffer2.release();
    }
    
    /**
     * FileChannel.transferTo演示（需要实际的Socket连接）
     */
    @SuppressWarnings("unused")
    private static void transferToDemo() throws IOException {
        System.out.println("\nFileChannel.transferTo演示");
        System.out.println("（需要实际的Socket连接，此处仅展示代码）");
        
        System.out.println("\n示例代码:");
        System.out.println("FileChannel fileChannel = new FileInputStream(file).getChannel();");
        System.out.println("SocketChannel socketChannel = SocketChannel.open(address);");
        System.out.println("fileChannel.transferTo(0, fileChannel.size(), socketChannel);");
        System.out.println("\n特点:");
        System.out.println("  - 直接在内核空间完成传输");
        System.out.println("  - 不经过用户空间");
        System.out.println("  - 性能最优");
    }
}
