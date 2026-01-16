package com.fragment.io.nio.demo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Channel操作演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>FileChannel的基本读写</li>
 *   <li>FileChannel的position和size</li>
 *   <li>零拷贝：transferTo/transferFrom</li>
 *   <li>内存映射文件：MappedByteBuffer</li>
 *   <li>Scatter/Gather操作</li>
 *   <li>文件锁</li>
 * </ul>
 * 
 * @author fragment
 */
public class ChannelDemo {

    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

    public static void main(String[] args) throws IOException {
        System.out.println("========== Channel操作演示 ==========\n");

        // 演示1：FileChannel基本读写
        demonstrateBasicReadWrite();

        // 演示2：position和size
        demonstratePositionAndSize();

        // 演示3：零拷贝transferTo
        demonstrateTransferTo();

        // 演示4：内存映射文件
        demonstrateMappedByteBuffer();

        // 演示5：Scatter/Gather
        demonstrateScatterGather();

        // 演示6：文件锁
        demonstrateFileLock();

        System.out.println("\n========== 演示完成 ==========");
    }

    /**
     * 演示1：FileChannel基本读写
     */
    private static void demonstrateBasicReadWrite() throws IOException {
        System.out.println("========== 演示1: FileChannel基本读写 ==========\n");

        Path filePath = Paths.get(TEMP_DIR, "channel_demo.txt");

        // 写入数据
        System.out.println("--- 写入数据 ---");
        try (FileChannel channel = FileChannel.open(
                filePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            String data = "Hello NIO Channel!";
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            buffer.put(data.getBytes(StandardCharsets.UTF_8));
            buffer.flip();

            int bytesWritten = channel.write(buffer);
            System.out.println("写入数据: " + data);
            System.out.println("写入字节数: " + bytesWritten);
        }

        // 读取数据
        System.out.println("\n--- 读取数据 ---");
        try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ)) {

            ByteBuffer buffer = ByteBuffer.allocate(1024);
            int bytesRead = channel.read(buffer);

            System.out.println("读取字节数: " + bytesRead);

            buffer.flip();
            String content = StandardCharsets.UTF_8.decode(buffer).toString();
            System.out.println("读取内容: " + content);
        }

        // 清理
        Files.deleteIfExists(filePath);

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 演示2：position和size
     */
    private static void demonstratePositionAndSize() throws IOException {
        System.out.println("========== 演示2: position和size ==========\n");

        Path filePath = Paths.get(TEMP_DIR, "position_demo.txt");

        // 创建文件并写入数据
        try (FileChannel channel = FileChannel.open(
                filePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.READ,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            // 写入数据
            String data = "0123456789ABCDEFGHIJ";
            ByteBuffer buffer = ByteBuffer.wrap(data.getBytes());
            channel.write(buffer);

            System.out.println("写入数据: " + data);
            System.out.println("文件大小: " + channel.size() + " 字节");
            System.out.println("当前位置: " + channel.position());

            // 设置position到第10个字节
            System.out.println("\n--- 设置position到10 ---");
            channel.position(10);
            System.out.println("当前位置: " + channel.position());

            // 从position=10开始读取
            buffer = ByteBuffer.allocate(5);
            channel.read(buffer);
            buffer.flip();
            String content = StandardCharsets.UTF_8.decode(buffer).toString();
            System.out.println("从position=10读取5个字节: " + content);

            // 跳到文件末尾
            System.out.println("\n--- 跳到文件末尾 ---");
            channel.position(channel.size());
            System.out.println("当前位置: " + channel.position());

            // 追加数据
            buffer = ByteBuffer.wrap("XYZ".getBytes());
            channel.write(buffer);
            System.out.println("追加数据: XYZ");
            System.out.println("新的文件大小: " + channel.size());

            // 读取整个文件
            System.out.println("\n--- 读取整个文件 ---");
            channel.position(0);
            buffer = ByteBuffer.allocate((int) channel.size());
            channel.read(buffer);
            buffer.flip();
            System.out.println("文件内容: " + StandardCharsets.UTF_8.decode(buffer));
        }

        // 清理
        Files.deleteIfExists(filePath);

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 演示3：零拷贝transferTo
     */
    private static void demonstrateTransferTo() throws IOException {
        System.out.println("========== 演示3: 零拷贝transferTo ==========\n");

        Path sourcePath = Paths.get(TEMP_DIR, "source.txt");
        Path destPath = Paths.get(TEMP_DIR, "dest.txt");

        // 创建源文件
        String data = "This is a test file for transferTo demonstration.\n".repeat(100);
        Files.write(sourcePath, data.getBytes());
        System.out.println("创建源文件: " + sourcePath);
        System.out.println("文件大小: " + Files.size(sourcePath) + " 字节");

        // 方式1：传统方式复制
        System.out.println("\n--- 方式1: 传统方式 (read + write) ---");
        long start = System.nanoTime();
        try (FileChannel sourceChannel = FileChannel.open(sourcePath, StandardOpenOption.READ);
             FileChannel destChannel = FileChannel.open(
                     destPath,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {

            ByteBuffer buffer = ByteBuffer.allocate(8192);
            while (sourceChannel.read(buffer) != -1) {
                buffer.flip();
                destChannel.write(buffer);
                buffer.clear();
            }
        }
        long traditionalTime = System.nanoTime() - start;
        System.out.println("传统方式耗时: " + traditionalTime / 1000000.0 + " ms");
        Files.deleteIfExists(destPath);

        // 方式2：零拷贝transferTo
        System.out.println("\n--- 方式2: 零拷贝 (transferTo) ---");
        start = System.nanoTime();
        try (FileChannel sourceChannel = FileChannel.open(sourcePath, StandardOpenOption.READ);
             FileChannel destChannel = FileChannel.open(
                     destPath,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {

            long transferred = sourceChannel.transferTo(0, sourceChannel.size(), destChannel);
            System.out.println("传输字节数: " + transferred);
        }
        long transferToTime = System.nanoTime() - start;
        System.out.println("零拷贝耗时: " + transferToTime / 1000000.0 + " ms");

        // 性能对比
        System.out.println("\n--- 性能对比 ---");
        System.out.println("传统方式: " + traditionalTime / 1000000.0 + " ms");
        System.out.println("零拷贝: " + transferToTime / 1000000.0 + " ms");
        double improvement = (traditionalTime - transferToTime) * 100.0 / traditionalTime;
        System.out.printf("性能提升: %.2f%%\n", improvement);

        // 验证文件内容
        System.out.println("\n--- 验证文件 ---");
        System.out.println("源文件大小: " + Files.size(sourcePath));
        System.out.println("目标文件大小: " + Files.size(destPath));
        System.out.println("内容一致: " + Files.mismatch(sourcePath, destPath) == -1);

        // 清理
        Files.deleteIfExists(sourcePath);
        Files.deleteIfExists(destPath);

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 演示4：内存映射文件
     */
    private static void demonstrateMappedByteBuffer() throws IOException {
        System.out.println("========== 演示4: 内存映射文件 ==========\n");

        Path filePath = Paths.get(TEMP_DIR, "mapped.txt");

        // 创建并映射文件
        System.out.println("--- 创建内存映射文件 ---");
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "rw");
             FileChannel channel = file.getChannel()) {

            // 映射1KB的文件
            int size = 1024;
            MappedByteBuffer mappedBuffer = channel.map(
                    FileChannel.MapMode.READ_WRITE,
                    0,
                    size
            );

            System.out.println("映射大小: " + size + " 字节");

            // 像操作内存一样操作文件
            System.out.println("\n--- 写入数据 ---");
            String data = "Memory Mapped File Example";
            mappedBuffer.put(data.getBytes());
            System.out.println("写入: " + data);

            // 随机访问
            System.out.println("\n--- 随机访问 ---");
            mappedBuffer.put(100, (byte) 'X');
            mappedBuffer.put(101, (byte) 'Y');
            mappedBuffer.put(102, (byte) 'Z');
            System.out.println("在位置100-102写入: XYZ");

            // 读取数据
            System.out.println("\n--- 读取数据 ---");
            mappedBuffer.position(0);
            byte[] bytes = new byte[data.length()];
            mappedBuffer.get(bytes);
            System.out.println("从位置0读取: " + new String(bytes));

            mappedBuffer.position(100);
            bytes = new byte[3];
            mappedBuffer.get(bytes);
            System.out.println("从位置100读取: " + new String(bytes));

            // 强制刷新到磁盘
            System.out.println("\n--- 强制刷新 ---");
            mappedBuffer.force();
            System.out.println("数据已刷新到磁盘");
        }

        // 验证数据持久化
        System.out.println("\n--- 验证数据持久化 ---");
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "r");
             FileChannel channel = file.getChannel()) {

            MappedByteBuffer mappedBuffer = channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    0,
                    channel.size()
            );

            byte[] bytes = new byte[26];
            mappedBuffer.get(bytes);
            System.out.println("读取到的数据: " + new String(bytes));

            mappedBuffer.position(100);
            bytes = new byte[3];
            mappedBuffer.get(bytes);
            System.out.println("位置100的数据: " + new String(bytes));
        }

        // 清理
        Files.deleteIfExists(filePath);

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 演示5：Scatter/Gather操作
     */
    private static void demonstrateScatterGather() throws IOException {
        System.out.println("========== 演示5: Scatter/Gather操作 ==========\n");

        Path filePath = Paths.get(TEMP_DIR, "scatter_gather.txt");

        // Gather Write：聚集写入
        System.out.println("--- Gather Write (聚集写入) ---");
        try (FileChannel channel = FileChannel.open(
                filePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            // 准备多个Buffer
            ByteBuffer header = ByteBuffer.allocate(10);
            header.put("HEADER".getBytes());
            header.flip();

            ByteBuffer body = ByteBuffer.allocate(20);
            body.put("BODY CONTENT".getBytes());
            body.flip();

            ByteBuffer footer = ByteBuffer.allocate(10);
            footer.put("FOOTER".getBytes());
            footer.flip();

            // 一次性写入多个Buffer
            ByteBuffer[] buffers = {header, body, footer};
            long bytesWritten = channel.write(buffers);

            System.out.println("写入3个Buffer:");
            System.out.println("  Header: HEADER");
            System.out.println("  Body: BODY CONTENT");
            System.out.println("  Footer: FOOTER");
            System.out.println("总共写入: " + bytesWritten + " 字节");
        }

        // Scatter Read：分散读取
        System.out.println("\n--- Scatter Read (分散读取) ---");
        try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ)) {

            // 准备多个Buffer
            ByteBuffer header = ByteBuffer.allocate(10);
            ByteBuffer body = ByteBuffer.allocate(20);
            ByteBuffer footer = ByteBuffer.allocate(10);

            // 一次性读取到多个Buffer
            ByteBuffer[] buffers = {header, body, footer};
            long bytesRead = channel.read(buffers);

            System.out.println("读取到3个Buffer:");
            
            header.flip();
            System.out.println("  Header: " + StandardCharsets.UTF_8.decode(header).toString().trim());
            
            body.flip();
            System.out.println("  Body: " + StandardCharsets.UTF_8.decode(body).toString().trim());
            
            footer.flip();
            System.out.println("  Footer: " + StandardCharsets.UTF_8.decode(footer).toString().trim());
            
            System.out.println("总共读取: " + bytesRead + " 字节");
        }

        // 清理
        Files.deleteIfExists(filePath);

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 演示6：文件锁
     */
    private static void demonstrateFileLock() throws IOException {
        System.out.println("========== 演示6: 文件锁 ==========\n");

        Path filePath = Paths.get(TEMP_DIR, "lock_demo.txt");

        // 创建文件
        Files.write(filePath, "Initial Content".getBytes());

        System.out.println("--- 排他锁（写锁）---");
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "rw");
             FileChannel channel = file.getChannel()) {

            // 获取排他锁
            System.out.println("尝试获取排他锁...");
            var lock = channel.lock();
            System.out.println("获取排他锁成功");
            System.out.println("  锁类型: " + (lock.isShared() ? "共享锁" : "排他锁"));
            System.out.println("  锁范围: " + lock.position() + " - " + 
                             (lock.position() + lock.size()));

            // 持有锁期间写入数据
            ByteBuffer buffer = ByteBuffer.wrap("Locked Content".getBytes());
            channel.write(buffer);
            System.out.println("写入数据: Locked Content");

            // 释放锁
            lock.release();
            System.out.println("释放锁");
        }

        System.out.println("\n--- 共享锁（读锁）---");
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "r");
             FileChannel channel = file.getChannel()) {

            // 获取共享锁
            System.out.println("尝试获取共享锁...");
            var lock = channel.lock(0, Long.MAX_VALUE, true); // shared=true
            System.out.println("获取共享锁成功");
            System.out.println("  锁类型: " + (lock.isShared() ? "共享锁" : "排他锁"));

            // 持有锁期间读取数据
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            channel.read(buffer);
            buffer.flip();
            String content = StandardCharsets.UTF_8.decode(buffer).toString();
            System.out.println("读取数据: " + content);

            // 释放锁
            lock.release();
            System.out.println("释放锁");
        }

        System.out.println("\n--- 部分文件锁 ---");
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "rw");
             FileChannel channel = file.getChannel()) {

            // 锁定文件的一部分
            System.out.println("锁定文件的前10个字节...");
            var lock = channel.lock(0, 10, false);
            System.out.println("获取部分文件锁成功");
            System.out.println("  锁范围: " + lock.position() + " - " + 
                             (lock.position() + lock.size()));

            lock.release();
            System.out.println("释放锁");
        }

        // 清理
        Files.deleteIfExists(filePath);

        System.out.println("\n说明:");
        System.out.println("- 文件锁是进程级别的，不是线程级别的");
        System.out.println("- 文件锁是建议性的，需要所有进程都遵守");
        System.out.println("- 排他锁：只有一个进程可以持有，用于写操作");
        System.out.println("- 共享锁：多个进程可以同时持有，用于读操作");

        System.out.println("\n" + createSeparator(60) + "\n");
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
