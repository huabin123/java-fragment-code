package com.fragment.io.nio.demo;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 零拷贝技术演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>传统I/O方式（read + write）</li>
 *   <li>NIO方式（HeapBuffer）</li>
 *   <li>NIO方式（DirectBuffer）</li>
 *   <li>零拷贝方式1：mmap + write</li>
 *   <li>零拷贝方式2：transferTo（sendfile）</li>
 *   <li>性能对比测试</li>
 * </ul>
 * 
 * @author fragment
 */
public class ZeroCopyDemo {

    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private static final int FILE_SIZE = 10 * 1024 * 1024; // 10MB

    public static void main(String[] args) throws IOException {
        System.out.println("========== 零拷贝技术演示 ==========\n");

        // 准备测试文件
        Path testFile = prepareTestFile();
        System.out.println("测试文件: " + testFile);
        System.out.println("文件大小: " + FILE_SIZE / 1024 / 1024 + " MB\n");
        System.out.println(createSeparator(60) + "\n");

        // 演示1：传统I/O方式
        demonstrateTraditionalIO(testFile);

        // 演示2：NIO HeapBuffer方式
        demonstrateNIOHeapBuffer(testFile);

        // 演示3：NIO DirectBuffer方式
        demonstrateNIODirectBuffer(testFile);

        // 演示4：mmap方式
        demonstrateMmap(testFile);

        // 演示5：transferTo方式（零拷贝）
        demonstrateTransferTo(testFile);

        // 演示6：网络传输零拷贝
        demonstrateNetworkZeroCopy(testFile);

        // 清理
        Files.deleteIfExists(testFile);

        System.out.println("\n========== 演示完成 ==========");
    }

    /**
     * 准备测试文件
     */
    private static Path prepareTestFile() throws IOException {
        Path filePath = Paths.get(TEMP_DIR, "zerocopy_test.dat");

        // 创建10MB的测试文件
        try (FileChannel channel = FileChannel.open(
                filePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            ByteBuffer buffer = ByteBuffer.allocate(1024);
            for (int i = 0; i < FILE_SIZE / 1024; i++) {
                buffer.clear();
                for (int j = 0; j < 1024; j++) {
                    buffer.put((byte) (i % 256));
                }
                buffer.flip();
                channel.write(buffer);
            }
        }

        return filePath;
    }

    /**
     * 演示1：传统I/O方式（read + write）
     */
    private static void demonstrateTraditionalIO(Path sourcePath) throws IOException {
        System.out.println("========== 方式1: 传统I/O (read + write) ==========\n");

        Path destPath = Paths.get(TEMP_DIR, "traditional_copy.dat");

        long startTime = System.currentTimeMillis();

        try (FileInputStream fis = new FileInputStream(sourcePath.toFile());
             FileOutputStream fos = new FileOutputStream(destPath.toFile())) {

            byte[] buffer = new byte[8192];
            int len;
            long totalBytes = 0;

            while ((len = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
                totalBytes += len;
            }

            System.out.println("传输字节数: " + totalBytes);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("耗时: " + duration + " ms");
        System.out.println("\n数据流转:");
        System.out.println("  磁盘 → 内核缓冲区 → JVM堆 → 内核缓冲区 → 磁盘");
        System.out.println("  拷贝次数: 4次（2次DMA + 2次CPU）");
        System.out.println("  上下文切换: 4次");

        Files.deleteIfExists(destPath);

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 演示2：NIO HeapBuffer方式
     */
    private static void demonstrateNIOHeapBuffer(Path sourcePath) throws IOException {
        System.out.println("========== 方式2: NIO HeapBuffer ==========\n");

        Path destPath = Paths.get(TEMP_DIR, "nio_heap_copy.dat");

        long startTime = System.currentTimeMillis();

        try (FileChannel sourceChannel = FileChannel.open(sourcePath, StandardOpenOption.READ);
             FileChannel destChannel = FileChannel.open(
                     destPath,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {

            ByteBuffer buffer = ByteBuffer.allocate(8192);
            long totalBytes = 0;

            while (sourceChannel.read(buffer) != -1) {
                buffer.flip();
                destChannel.write(buffer);
                totalBytes += buffer.limit();
                buffer.clear();
            }

            System.out.println("传输字节数: " + totalBytes);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("耗时: " + duration + " ms");
        System.out.println("\n说明:");
        System.out.println("  使用HeapBuffer，数据仍需在内核和JVM堆之间拷贝");
        System.out.println("  拷贝次数: 4次");

        Files.deleteIfExists(destPath);

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 演示3：NIO DirectBuffer方式
     */
    private static void demonstrateNIODirectBuffer(Path sourcePath) throws IOException {
        System.out.println("========== 方式3: NIO DirectBuffer ==========\n");

        Path destPath = Paths.get(TEMP_DIR, "nio_direct_copy.dat");

        long startTime = System.currentTimeMillis();

        try (FileChannel sourceChannel = FileChannel.open(sourcePath, StandardOpenOption.READ);
             FileChannel destChannel = FileChannel.open(
                     destPath,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {

            ByteBuffer buffer = ByteBuffer.allocateDirect(8192);
            long totalBytes = 0;

            while (sourceChannel.read(buffer) != -1) {
                buffer.flip();
                destChannel.write(buffer);
                totalBytes += buffer.limit();
                buffer.clear();
            }

            System.out.println("传输字节数: " + totalBytes);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("耗时: " + duration + " ms");
        System.out.println("\n说明:");
        System.out.println("  使用DirectBuffer，数据在堆外内存");
        System.out.println("  减少了JVM堆的拷贝");
        System.out.println("  拷贝次数: 3次（2次DMA + 1次CPU）");

        Files.deleteIfExists(destPath);

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 演示4：mmap方式
     */
    private static void demonstrateMmap(Path sourcePath) throws IOException {
        System.out.println("========== 方式4: mmap (内存映射) ==========\n");

        Path destPath = Paths.get(TEMP_DIR, "mmap_copy.dat");

        long startTime = System.currentTimeMillis();

        try (RandomAccessFile sourceFile = new RandomAccessFile(sourcePath.toFile(), "r");
             RandomAccessFile destFile = new RandomAccessFile(destPath.toFile(), "rw");
             FileChannel sourceChannel = sourceFile.getChannel();
             FileChannel destChannel = destFile.getChannel()) {

            long fileSize = sourceChannel.size();

            // 映射源文件
            MappedByteBuffer sourceMapped = sourceChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    0,
                    fileSize
            );

            // 映射目标文件
            MappedByteBuffer destMapped = destChannel.map(
                    FileChannel.MapMode.READ_WRITE,
                    0,
                    fileSize
            );

            // 直接在内存中拷贝
            destMapped.put(sourceMapped);

            // 强制刷新到磁盘
            destMapped.force();

            System.out.println("传输字节数: " + fileSize);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("耗时: " + duration + " ms");
        System.out.println("\n说明:");
        System.out.println("  使用mmap，文件映射到内存");
        System.out.println("  用户空间和内核空间共享内存");
        System.out.println("  拷贝次数: 3次（2次DMA + 1次CPU）");
        System.out.println("  适合大文件随机访问");

        Files.deleteIfExists(destPath);

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 演示5：transferTo方式（零拷贝）
     */
    private static void demonstrateTransferTo(Path sourcePath) throws IOException {
        System.out.println("========== 方式5: transferTo (零拷贝) ==========\n");

        Path destPath = Paths.get(TEMP_DIR, "transferto_copy.dat");

        long startTime = System.currentTimeMillis();

        try (FileChannel sourceChannel = FileChannel.open(sourcePath, StandardOpenOption.READ);
             FileChannel destChannel = FileChannel.open(
                     destPath,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {

            long fileSize = sourceChannel.size();
            long position = 0;

            // 分块传输（避免单次传输限制）
            long chunkSize = 1024 * 1024 * 1024; // 1GB
            while (position < fileSize) {
                long transferred = sourceChannel.transferTo(
                        position,
                        Math.min(chunkSize, fileSize - position),
                        destChannel
                );
                position += transferred;
            }

            System.out.println("传输字节数: " + position);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("耗时: " + duration + " ms");
        System.out.println("\n说明:");
        System.out.println("  使用transferTo，底层调用sendfile系统调用");
        System.out.println("  数据完全在内核空间传输，不经过用户空间");
        System.out.println("  拷贝次数: 2次（2次DMA，无CPU拷贝）");
        System.out.println("  上下文切换: 2次");
        System.out.println("  这是真正的零拷贝！");

        Files.deleteIfExists(destPath);

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 演示6：网络传输零拷贝
     */
    private static void demonstrateNetworkZeroCopy(Path filePath) throws IOException {
        System.out.println("========== 方式6: 网络传输零拷贝 ==========\n");

        int port = 9999;

        // 启动服务器（在新线程中）
        Thread serverThread = new Thread(() -> {
            try {
                runFileServer(port, filePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // 等待服务器启动
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 客户端接收文件
        receiveFile(port);

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 文件服务器（使用零拷贝发送文件）
     */
    private static void runFileServer(int port, Path filePath) throws IOException {
        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            serverChannel.bind(new InetSocketAddress(port));
            System.out.println("[服务器] 启动在端口: " + port);

            try (SocketChannel clientChannel = serverChannel.accept();
                 FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.READ)) {

                System.out.println("[服务器] 接受连接: " + clientChannel.getRemoteAddress());

                long fileSize = fileChannel.size();
                System.out.println("[服务器] 文件大小: " + fileSize + " 字节");

                long startTime = System.currentTimeMillis();

                // 使用transferTo零拷贝发送文件
                long position = 0;
                while (position < fileSize) {
                    long transferred = fileChannel.transferTo(
                            position,
                            fileSize - position,
                            clientChannel
                    );
                    position += transferred;
                }

                long endTime = System.currentTimeMillis();

                System.out.println("[服务器] 发送完成");
                System.out.println("[服务器] 发送字节数: " + position);
                System.out.println("[服务器] 耗时: " + (endTime - startTime) + " ms");
                System.out.println("[服务器] 使用零拷贝: transferTo");
            }
        }
    }

    /**
     * 接收文件
     */
    private static void receiveFile(int port) throws IOException {
        Path receivedPath = Paths.get(TEMP_DIR, "received.dat");

        try (SocketChannel channel = SocketChannel.open(new InetSocketAddress("localhost", port));
             FileChannel fileChannel = FileChannel.open(
                     receivedPath,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {

            System.out.println("\n[客户端] 连接到服务器: localhost:" + port);

            long startTime = System.currentTimeMillis();

            ByteBuffer buffer = ByteBuffer.allocateDirect(8192);
            long totalBytes = 0;

            int len;
            while ((len = channel.read(buffer)) != -1) {
                buffer.flip();
                fileChannel.write(buffer);
                totalBytes += len;
                buffer.clear();
            }

            long endTime = System.currentTimeMillis();

            System.out.println("[客户端] 接收完成");
            System.out.println("[客户端] 接收字节数: " + totalBytes);
            System.out.println("[客户端] 耗时: " + (endTime - startTime) + " ms");
        }

        Files.deleteIfExists(receivedPath);
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
