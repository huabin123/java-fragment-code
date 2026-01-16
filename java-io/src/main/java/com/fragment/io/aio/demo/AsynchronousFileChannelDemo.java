package com.fragment.io.aio.demo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * AsynchronousFileChannel演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>异步文件读取（Future模式）</li>
 *   <li>异步文件读取（CompletionHandler模式）</li>
 *   <li>异步文件写入</li>
 *   <li>大文件分块读取</li>
 *   <li>文件拷贝</li>
 * </ul>
 * 
 * @author fragment
 */
public class AsynchronousFileChannelDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== AsynchronousFileChannel演示 ==========\n");

        // 1. Future模式读取文件
        demonstrateFutureRead();

        // 2. CompletionHandler模式读取文件
        demonstrateCallbackRead();

        // 3. 异步写入文件
        demonstrateAsyncWrite();

        // 4. 大文件分块读取
        demonstrateChunkedRead();

        // 5. 异步文件拷贝
        demonstrateFileCopy();
    }

    /**
     * 1. Future模式读取文件
     */
    private static void demonstrateFutureRead() throws IOException, ExecutionException, InterruptedException {
        System.out.println("1. Future模式读取文件");
        System.out.println("特点: 发起异步读取，使用Future.get()等待结果\n");

        // 创建测试文件
        Path path = createTestFile("future-test.txt", "Hello, Future Mode!");

        // 打开异步文件通道
        AsynchronousFileChannel channel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        long position = 0;

        System.out.println("=== 发起异步读取 ===");
        long startTime = System.currentTimeMillis();

        // 发起异步读取，返回Future
        Future<Integer> future = channel.read(buffer, position);

        System.out.println("异步读取已发起，立即返回");
        System.out.println("可以继续执行其他任务...");

        // 模拟执行其他任务
        for (int i = 0; i < 3; i++) {
            System.out.println("执行其他任务 " + (i + 1));
            Thread.sleep(100);
        }

        // 等待读取完成
        System.out.println("\n等待读取完成...");
        Integer bytesRead = future.get();  // 阻塞等待

        long duration = System.currentTimeMillis() - startTime;

        System.out.println("读取完成！");
        System.out.println("读取字节数: " + bytesRead);
        System.out.println("总耗时: " + duration + "ms");

        // 处理数据
        buffer.flip();
        String content = StandardCharsets.UTF_8.decode(buffer).toString();
        System.out.println("文件内容: " + content);

        channel.close();
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 2. CompletionHandler模式读取文件（推荐）
     */
    private static void demonstrateCallbackRead() throws IOException, InterruptedException {
        System.out.println("2. CompletionHandler模式读取文件");
        System.out.println("特点: 异步回调，不阻塞线程\n");

        // 创建测试文件
        Path path = createTestFile("callback-test.txt", "Hello, Callback Mode!");

        // 打开异步文件通道
        AsynchronousFileChannel channel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        long position = 0;

        System.out.println("=== 发起异步读取 ===");
        long startTime = System.currentTimeMillis();

        // 发起异步读取，注册回调
        channel.read(buffer, position, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer attachment) {
                long duration = System.currentTimeMillis() - startTime;

                System.out.println("\n=== 读取完成回调 ===");
                System.out.println("读取字节数: " + bytesRead);
                System.out.println("耗时: " + duration + "ms");

                // 处理数据
                attachment.flip();
                String content = StandardCharsets.UTF_8.decode(attachment).toString();
                System.out.println("文件内容: " + content);

                // 关闭通道
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

                try {
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        System.out.println("异步读取已发起，立即返回");
        System.out.println("主线程继续执行其他任务...");

        // 模拟执行其他任务
        for (int i = 0; i < 3; i++) {
            System.out.println("执行其他任务 " + (i + 1));
            Thread.sleep(100);
        }

        // 等待回调完成
        Thread.sleep(500);
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 3. 异步写入文件
     */
    private static void demonstrateAsyncWrite() throws IOException, InterruptedException {
        System.out.println("3. 异步写入文件");
        System.out.println("特点: 异步写入，不阻塞业务线程\n");

        Path path = Paths.get("async-write-test.txt");

        // 打开异步文件通道（写模式）
        AsynchronousFileChannel channel = AsynchronousFileChannel.open(
            path,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        String content = "Hello, Async Write!\nThis is line 2.\nThis is line 3.";
        ByteBuffer buffer = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
        long position = 0;

        System.out.println("=== 发起异步写入 ===");
        System.out.println("写入内容:\n" + content);

        long startTime = System.currentTimeMillis();

        // 异步写入
        channel.write(buffer, position, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesWritten, ByteBuffer attachment) {
                long duration = System.currentTimeMillis() - startTime;

                System.out.println("\n=== 写入完成回调 ===");
                System.out.println("写入字节数: " + bytesWritten);
                System.out.println("耗时: " + duration + "ms");
                System.out.println("文件路径: " + path.toAbsolutePath());

                try {
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("写入失败: " + exc.getMessage());
                exc.printStackTrace();
            }
        });

        System.out.println("异步写入已发起，主线程继续执行...");

        // 等待写入完成
        Thread.sleep(500);
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 4. 大文件分块读取
     */
    private static void demonstrateChunkedRead() throws IOException, InterruptedException {
        System.out.println("4. 大文件分块读取");
        System.out.println("场景: 读取大文件，避免一次性加载到内存\n");

        // 创建一个较大的测试文件
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            largeContent.append("Line ").append(i + 1).append(": This is a test line.\n");
        }
        Path path = createTestFile("large-file.txt", largeContent.toString());

        AsynchronousFileChannel channel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);
        long fileSize = channel.size();

        System.out.println("=== 文件信息 ===");
        System.out.println("文件大小: " + fileSize + " 字节");

        int chunkSize = 256;  // 每次读取256字节
        System.out.println("分块大小: " + chunkSize + " 字节");
        System.out.println("预计分块数: " + ((fileSize + chunkSize - 1) / chunkSize));

        System.out.println("\n=== 开始分块读取 ===");

        // 递归读取
        readChunk(channel, 0, fileSize, chunkSize, 1);

        // 等待所有分块读取完成
        Thread.sleep(2000);
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 递归读取文件块
     */
    private static void readChunk(AsynchronousFileChannel channel, long position, 
                                  long fileSize, int chunkSize, int chunkNumber) {
        if (position >= fileSize) {
            try {
                channel.close();
                System.out.println("\n所有分块读取完成！");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        ByteBuffer buffer = ByteBuffer.allocate(chunkSize);

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

                System.out.println("分块 " + chunkNumber + ": 位置=" + position + 
                                 ", 读取=" + bytesRead + " 字节");

                // 读取下一块
                readChunk(channel, position + bytesRead, fileSize, chunkSize, chunkNumber + 1);
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("读取分块 " + chunkNumber + " 失败: " + exc.getMessage());
            }
        });
    }

    /**
     * 5. 异步文件拷贝
     */
    private static void demonstrateFileCopy() throws IOException, InterruptedException {
        System.out.println("5. 异步文件拷贝");
        System.out.println("场景: 异步读取源文件，异步写入目标文件\n");

        // 创建源文件
        String content = "This is the source file content.\nIt will be copied to another file.";
        Path sourcePath = createTestFile("source.txt", content);
        Path targetPath = Paths.get("target.txt");

        System.out.println("=== 文件拷贝 ===");
        System.out.println("源文件: " + sourcePath.getFileName());
        System.out.println("目标文件: " + targetPath.getFileName());

        // 打开源文件和目标文件
        AsynchronousFileChannel sourceChannel = AsynchronousFileChannel.open(
            sourcePath, 
            StandardOpenOption.READ
        );

        AsynchronousFileChannel targetChannel = AsynchronousFileChannel.open(
            targetPath,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        ByteBuffer buffer = ByteBuffer.allocate(1024);

        System.out.println("\n开始拷贝...");
        long startTime = System.currentTimeMillis();

        // 异步读取源文件
        sourceChannel.read(buffer, 0, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer attachment) {
                System.out.println("读取完成: " + bytesRead + " 字节");

                attachment.flip();

                // 异步写入目标文件
                targetChannel.write(attachment, 0, attachment, 
                    new CompletionHandler<Integer, ByteBuffer>() {
                        @Override
                        public void completed(Integer bytesWritten, ByteBuffer att) {
                            long duration = System.currentTimeMillis() - startTime;

                            System.out.println("写入完成: " + bytesWritten + " 字节");
                            System.out.println("拷贝完成！耗时: " + duration + "ms");

                            // 关闭通道
                            try {
                                sourceChannel.close();
                                targetChannel.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void failed(Throwable exc, ByteBuffer att) {
                            System.err.println("写入失败: " + exc.getMessage());
                        }
                    });
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.err.println("读取失败: " + exc.getMessage());
            }
        });

        // 等待拷贝完成
        Thread.sleep(1000);
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 创建测试文件
     */
    private static Path createTestFile(String filename, String content) throws IOException {
        Path path = Paths.get(filename);
        java.nio.file.Files.write(path, content.getBytes(StandardCharsets.UTF_8));
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
}
