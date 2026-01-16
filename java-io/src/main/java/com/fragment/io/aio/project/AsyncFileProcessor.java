package com.fragment.io.aio.project;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 异步文件处理器
 * 
 * <p>功能特性：
 * <ul>
 *   <li>批量异步读取文件</li>
 *   <li>异步文件拷贝</li>
 *   <li>大文件分块处理</li>
 *   <li>文件内容搜索</li>
 *   <li>进度跟踪</li>
 *   <li>错误处理和重试</li>
 * </ul>
 * 
 * @author fragment
 */
public class AsyncFileProcessor {

    private static final int BUFFER_SIZE = 8192; // 8KB
    private static final int MAX_RETRY = 3;

    /**
     * 批量异步读取文件
     * 
     * @param files 文件列表
     * @param callback 每个文件读取完成后的回调
     */
    public void batchReadFiles(List<Path> files, Consumer<FileReadResult> callback) {
        System.out.println("开始批量读取 " + files.size() + " 个文件...\n");

        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        for (Path file : files) {
            readFileAsync(file, new CompletionHandler<String, Path>() {
                @Override
                public void completed(String content, Path attachment) {
                    int completed = completedCount.incrementAndGet();
                    
                    FileReadResult result = new FileReadResult(
                        attachment, true, content, null, 
                        System.currentTimeMillis() - startTime
                    );
                    
                    callback.accept(result);
                    
                    System.out.println("✅ [" + completed + "/" + files.size() + "] " + 
                                     attachment.getFileName() + " 读取成功");
                }

                @Override
                public void failed(Throwable exc, Path attachment) {
                    int failed = failedCount.incrementAndGet();
                    int completed = completedCount.incrementAndGet();
                    
                    FileReadResult result = new FileReadResult(
                        attachment, false, null, exc.getMessage(),
                        System.currentTimeMillis() - startTime
                    );
                    
                    callback.accept(result);
                    
                    System.out.println("❌ [" + completed + "/" + files.size() + "] " + 
                                     attachment.getFileName() + " 读取失败: " + exc.getMessage());
                }
            });
        }
    }

    /**
     * 异步读取单个文件
     */
    private void readFileAsync(Path file, CompletionHandler<String, Path> handler) {
        try {
            AsynchronousFileChannel channel = AsynchronousFileChannel.open(file, StandardOpenOption.READ);
            long fileSize = Files.size(file);
            ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);

            channel.read(buffer, 0, file, new CompletionHandler<Integer, Path>() {
                @Override
                public void completed(Integer result, Path attachment) {
                    try {
                        channel.close();
                        buffer.flip();
                        String content = StandardCharsets.UTF_8.decode(buffer).toString();
                        handler.completed(content, attachment);
                    } catch (IOException e) {
                        handler.failed(e, attachment);
                    }
                }

                @Override
                public void failed(Throwable exc, Path attachment) {
                    try {
                        channel.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    handler.failed(exc, attachment);
                }
            });
        } catch (IOException e) {
            handler.failed(e, file);
        }
    }

    /**
     * 异步文件拷贝（带进度）
     */
    public void copyFileAsync(Path source, Path target, ProgressCallback progressCallback) {
        System.out.println("开始异步拷贝: " + source + " -> " + target);

        try {
            long fileSize = Files.size(source);
            AsynchronousFileChannel sourceChannel = AsynchronousFileChannel.open(
                source, StandardOpenOption.READ);
            AsynchronousFileChannel targetChannel = AsynchronousFileChannel.open(
                target, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

            copyChunk(sourceChannel, targetChannel, 0, fileSize, progressCallback);

        } catch (IOException e) {
            System.out.println("❌ 拷贝失败: " + e.getMessage());
            progressCallback.onError(e);
        }
    }

    /**
     * 递归拷贝文件块
     */
    private void copyChunk(AsynchronousFileChannel source, AsynchronousFileChannel target,
                          long position, long totalSize, ProgressCallback progressCallback) {
        
        if (position >= totalSize) {
            // 拷贝完成
            try {
                source.close();
                target.close();
                progressCallback.onComplete();
                System.out.println("✅ 拷贝完成！");
            } catch (IOException e) {
                progressCallback.onError(e);
            }
            return;
        }

        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        
        source.read(buffer, position, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer attachment) {
                if (bytesRead == -1) {
                    // 文件结束
                    try {
                        source.close();
                        target.close();
                        progressCallback.onComplete();
                    } catch (IOException e) {
                        progressCallback.onError(e);
                    }
                    return;
                }

                attachment.flip();
                
                // 写入目标文件
                target.write(attachment, position, attachment, new CompletionHandler<Integer, ByteBuffer>() {
                    @Override
                    public void completed(Integer bytesWritten, ByteBuffer attachment) {
                        long newPosition = position + bytesWritten;
                        double progress = (double) newPosition / totalSize * 100;
                        progressCallback.onProgress(newPosition, totalSize, progress);
                        
                        // 继续拷贝下一块
                        copyChunk(source, target, newPosition, totalSize, progressCallback);
                    }

                    @Override
                    public void failed(Throwable exc, ByteBuffer attachment) {
                        System.out.println("❌ 写入失败: " + exc.getMessage());
                        progressCallback.onError(exc);
                        closeChannels(source, target);
                    }
                });
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.out.println("❌ 读取失败: " + exc.getMessage());
                progressCallback.onError(exc);
                closeChannels(source, target);
            }
        });
    }

    /**
     * 大文件分块处理
     */
    public void processLargeFile(Path file, int chunkSize, ChunkProcessor processor) {
        System.out.println("开始处理大文件: " + file);
        System.out.println("分块大小: " + chunkSize + " 字节\n");

        try {
            long fileSize = Files.size(file);
            AsynchronousFileChannel channel = AsynchronousFileChannel.open(file, StandardOpenOption.READ);

            AtomicLong position = new AtomicLong(0);
            AtomicInteger chunkIndex = new AtomicInteger(0);

            processNextChunk(channel, position, chunkIndex, fileSize, chunkSize, processor);

        } catch (IOException e) {
            System.out.println("❌ 处理失败: " + e.getMessage());
        }
    }

    /**
     * 处理下一个文件块
     */
    private void processNextChunk(AsynchronousFileChannel channel, AtomicLong position,
                                 AtomicInteger chunkIndex, long fileSize, int chunkSize,
                                 ChunkProcessor processor) {
        
        long currentPos = position.get();
        if (currentPos >= fileSize) {
            // 处理完成
            try {
                channel.close();
                processor.onComplete();
                System.out.println("\n✅ 文件处理完成！");
            } catch (IOException e) {
                processor.onError(e);
            }
            return;
        }

        int bufferSize = (int) Math.min(chunkSize, fileSize - currentPos);
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        int currentChunk = chunkIndex.getAndIncrement();

        channel.read(buffer, currentPos, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytesRead, ByteBuffer attachment) {
                if (bytesRead == -1) {
                    try {
                        channel.close();
                        processor.onComplete();
                    } catch (IOException e) {
                        processor.onError(e);
                    }
                    return;
                }

                attachment.flip();
                byte[] data = new byte[attachment.remaining()];
                attachment.get(data);

                // 处理数据块
                processor.processChunk(currentChunk, data, currentPos);

                // 更新位置并处理下一块
                position.addAndGet(bytesRead);
                processNextChunk(channel, position, chunkIndex, fileSize, chunkSize, processor);
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                System.out.println("❌ 读取块失败: " + exc.getMessage());
                processor.onError(exc);
                try {
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 异步搜索文件内容
     */
    public CompletableFuture<List<SearchResult>> searchInFiles(List<Path> files, String keyword) {
        System.out.println("在 " + files.size() + " 个文件中搜索关键字: \"" + keyword + "\"\n");

        CompletableFuture<List<SearchResult>> future = new CompletableFuture<>();
        List<SearchResult> results = new ArrayList<>();
        AtomicInteger completedCount = new AtomicInteger(0);

        for (Path file : files) {
            searchInFile(file, keyword, new CompletionHandler<SearchResult, Path>() {
                @Override
                public void completed(SearchResult result, Path attachment) {
                    synchronized (results) {
                        if (result.isFound()) {
                            results.add(result);
                            System.out.println("✅ 在 " + attachment.getFileName() + 
                                             " 中找到 " + result.getOccurrences() + " 处匹配");
                        }
                    }

                    if (completedCount.incrementAndGet() == files.size()) {
                        future.complete(results);
                    }
                }

                @Override
                public void failed(Throwable exc, Path attachment) {
                    System.out.println("❌ 搜索 " + attachment.getFileName() + " 失败: " + exc.getMessage());
                    
                    if (completedCount.incrementAndGet() == files.size()) {
                        future.complete(results);
                    }
                }
            });
        }

        return future;
    }

    /**
     * 在单个文件中搜索
     */
    private void searchInFile(Path file, String keyword, CompletionHandler<SearchResult, Path> handler) {
        readFileAsync(file, new CompletionHandler<String, Path>() {
            @Override
            public void completed(String content, Path attachment) {
                int count = 0;
                int index = 0;
                List<Integer> positions = new ArrayList<>();

                while ((index = content.indexOf(keyword, index)) != -1) {
                    count++;
                    positions.add(index);
                    index += keyword.length();
                }

                SearchResult result = new SearchResult(attachment, count > 0, count, positions);
                handler.completed(result, attachment);
            }

            @Override
            public void failed(Throwable exc, Path attachment) {
                handler.failed(exc, attachment);
            }
        });
    }

    /**
     * 关闭通道
     */
    private void closeChannels(AsynchronousFileChannel... channels) {
        for (AsynchronousFileChannel channel : channels) {
            try {
                if (channel != null) {
                    channel.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ==================== 内部类 ====================

    /**
     * 文件读取结果
     */
    public static class FileReadResult {
        private final Path file;
        private final boolean success;
        private final String content;
        private final String error;
        private final long duration;

        public FileReadResult(Path file, boolean success, String content, String error, long duration) {
            this.file = file;
            this.success = success;
            this.content = content;
            this.error = error;
            this.duration = duration;
        }

        public Path getFile() { return file; }
        public boolean isSuccess() { return success; }
        public String getContent() { return content; }
        public String getError() { return error; }
        public long getDuration() { return duration; }
    }

    /**
     * 进度回调接口
     */
    public interface ProgressCallback {
        void onProgress(long current, long total, double percentage);
        void onComplete();
        void onError(Throwable error);
    }

    /**
     * 文件块处理器接口
     */
    public interface ChunkProcessor {
        void processChunk(int chunkIndex, byte[] data, long position);
        void onComplete();
        void onError(Throwable error);
    }

    /**
     * 搜索结果
     */
    public static class SearchResult {
        private final Path file;
        private final boolean found;
        private final int occurrences;
        private final List<Integer> positions;

        public SearchResult(Path file, boolean found, int occurrences, List<Integer> positions) {
            this.file = file;
            this.found = found;
            this.occurrences = occurrences;
            this.positions = positions;
        }

        public Path getFile() { return file; }
        public boolean isFound() { return found; }
        public int getOccurrences() { return occurrences; }
        public List<Integer> getPositions() { return positions; }
    }

    // ==================== 测试示例 ====================

    public static void main(String[] args) throws Exception {
        AsyncFileProcessor processor = new AsyncFileProcessor();

        System.out.println("========== 异步文件处理器演示 ==========\n");

        // 创建测试文件
        createTestFiles();

        // 示例1: 批量读取文件
        demonstrateBatchRead(processor);

        Thread.sleep(1000);

        // 示例2: 异步文件拷贝
        demonstrateFileCopy(processor);

        Thread.sleep(2000);

        // 示例3: 大文件分块处理
        demonstrateLargeFileProcessing(processor);

        Thread.sleep(1000);

        // 示例4: 文件内容搜索
        demonstrateFileSearch(processor);

        Thread.sleep(2000);

        System.out.println("\n========== 演示完成 ==========");
    }

    private static void createTestFiles() throws IOException {
        System.out.println("创建测试文件...\n");
        
        Files.write(Paths.get("test1.txt"), "Hello World! This is test file 1.".getBytes());
        Files.write(Paths.get("test2.txt"), "Hello Java! This is test file 2.".getBytes());
        Files.write(Paths.get("test3.txt"), "Hello AIO! This is test file 3.".getBytes());
        
        // 创建大文件
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeContent.append("Line ").append(i).append(": This is a large file for testing.\n");
        }
        Files.write(Paths.get("large-file.txt"), largeContent.toString().getBytes());
    }

    private static void demonstrateBatchRead(AsyncFileProcessor processor) throws InterruptedException {
        System.out.println("========== 示例1: 批量读取文件 ==========\n");

        List<Path> files = new ArrayList<>();
        files.add(Paths.get("test1.txt"));
        files.add(Paths.get("test2.txt"));
        files.add(Paths.get("test3.txt"));

        CountDownLatch latch = new CountDownLatch(files.size());

        processor.batchReadFiles(files, result -> {
            if (result.isSuccess()) {
                System.out.println("  内容预览: " + 
                    result.getContent().substring(0, Math.min(30, result.getContent().length())) + "...");
            }
            latch.countDown();
        });

        latch.await();
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    private static void demonstrateFileCopy(AsyncFileProcessor processor) throws InterruptedException {
        System.out.println("========== 示例2: 异步文件拷贝 ==========\n");

        CountDownLatch latch = new CountDownLatch(1);

        processor.copyFileAsync(
            Paths.get("large-file.txt"),
            Paths.get("large-file-copy.txt"),
            new AsyncFileProcessor.ProgressCallback() {
                private long lastPrintTime = System.currentTimeMillis();

                @Override
                public void onProgress(long current, long total, double percentage) {
                    long now = System.currentTimeMillis();
                    if (now - lastPrintTime > 100) { // 每100ms打印一次
                        System.out.printf("进度: %.2f%% (%d/%d 字节)\n", percentage, current, total);
                        lastPrintTime = now;
                    }
                }

                @Override
                public void onComplete() {
                    System.out.println("拷贝完成！");
                    latch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    System.out.println("拷贝失败: " + error.getMessage());
                    latch.countDown();
                }
            }
        );

        latch.await();
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    private static void demonstrateLargeFileProcessing(AsyncFileProcessor processor) throws InterruptedException {
        System.out.println("========== 示例3: 大文件分块处理 ==========\n");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger lineCount = new AtomicInteger(0);

        processor.processLargeFile(
            Paths.get("large-file.txt"),
            1024, // 1KB per chunk
            new AsyncFileProcessor.ChunkProcessor() {
                @Override
                public void processChunk(int chunkIndex, byte[] data, long position) {
                    String content = new String(data, StandardCharsets.UTF_8);
                    int lines = content.split("\n").length;
                    lineCount.addAndGet(lines);
                    
                    System.out.printf("处理块 #%d: 位置=%d, 大小=%d字节, 行数=%d\n",
                        chunkIndex, position, data.length, lines);
                }

                @Override
                public void onComplete() {
                    System.out.println("总行数: " + lineCount.get());
                    latch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    System.out.println("处理失败: " + error.getMessage());
                    latch.countDown();
                }
            }
        );

        latch.await();
        System.out.println("\n" + createSeparator(60) + "\n");
    }

    private static void demonstrateFileSearch(AsyncFileProcessor processor) throws Exception {
        System.out.println("========== 示例4: 文件内容搜索 ==========\n");

        List<Path> files = new ArrayList<>();
        files.add(Paths.get("test1.txt"));
        files.add(Paths.get("test2.txt"));
        files.add(Paths.get("test3.txt"));

        List<SearchResult> results = processor.searchInFiles(files, "Hello").get();

        System.out.println("\n搜索结果:");
        System.out.println("找到 " + results.size() + " 个文件包含关键字");
        for (SearchResult result : results) {
            System.out.println("  " + result.getFile().getFileName() + ": " + 
                             result.getOccurrences() + " 处匹配，位置: " + result.getPositions());
        }

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    private static String createSeparator(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append("=");
        }
        return sb.toString();
    }
}
