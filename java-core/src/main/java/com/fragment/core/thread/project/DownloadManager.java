package com.fragment.core.thread.project;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 实际项目Demo：多线程下载管理器
 * 
 * <p>场景：实现一个支持多线程下载的文件下载管理器
 * <ul>
 *   <li>支持多个文件同时下载</li>
 *   <li>每个文件使用多线程分块下载</li>
 *   <li>支持暂停、恢复、取消</li>
 *   <li>实时显示下载进度</li>
 * </ul>
 * 
 * @author fragment
 */
public class DownloadManager {
    
    /** 最大同时下载文件数 */
    private static final int MAX_CONCURRENT_DOWNLOADS = 3;
    
    /** 每个文件的下载线程数 */
    private static final int THREADS_PER_FILE = 4;
    
    /** 当前下载任务列表 */
    private final List<DownloadTask> tasks = new ArrayList<>();
    
    /** 当前活跃下载数 */
    private final AtomicInteger activeDownloads = new AtomicInteger(0);
    
    /**
     * 添加下载任务
     */
    public synchronized void addDownload(String url, String savePath) {
        DownloadTask task = new DownloadTask(url, savePath, THREADS_PER_FILE);
        tasks.add(task);
        
        System.out.println("添加下载任务: " + url);
        
        // 如果未达到最大并发数，立即开始下载
        if (activeDownloads.get() < MAX_CONCURRENT_DOWNLOADS) {
            startDownload(task);
        } else {
            System.out.println("等待队列中...");
        }
    }
    
    /**
     * 开始下载
     */
    private void startDownload(DownloadTask task) {
        activeDownloads.incrementAndGet();
        task.start();
        
        // 监控下载完成
        new Thread(() -> {
            task.waitForCompletion();
            activeDownloads.decrementAndGet();
            
            // 启动下一个等待的任务
            startNextWaitingTask();
        }, "Monitor-" + task.getUrl()).start();
    }
    
    /**
     * 启动下一个等待的任务
     */
    private synchronized void startNextWaitingTask() {
        for (DownloadTask task : tasks) {
            if (task.getStatus() == DownloadStatus.WAITING) {
                startDownload(task);
                break;
            }
        }
    }
    
    /**
     * 暂停下载
     */
    public synchronized void pauseDownload(String url) {
        for (DownloadTask task : tasks) {
            if (task.getUrl().equals(url)) {
                task.pause();
                break;
            }
        }
    }
    
    /**
     * 恢复下载
     */
    public synchronized void resumeDownload(String url) {
        for (DownloadTask task : tasks) {
            if (task.getUrl().equals(url)) {
                task.resume();
                break;
            }
        }
    }
    
    /**
     * 取消下载
     */
    public synchronized void cancelDownload(String url) {
        for (DownloadTask task : tasks) {
            if (task.getUrl().equals(url)) {
                task.cancel();
                tasks.remove(task);
                break;
            }
        }
    }
    
    /**
     * 获取所有任务状态
     */
    public synchronized void printStatus() {
        System.out.println("\n========== 下载状态 ==========");
        System.out.println("活跃下载数: " + activeDownloads.get() + "/" + MAX_CONCURRENT_DOWNLOADS);
        System.out.println("总任务数: " + tasks.size());
        System.out.println("----------------------------");
        
        for (DownloadTask task : tasks) {
            System.out.println(task.getStatusString());
        }
        System.out.println("============================\n");
    }
    
    /**
     * 关闭下载管理器
     */
    public synchronized void shutdown() {
        System.out.println("关闭下载管理器...");
        
        for (DownloadTask task : tasks) {
            task.cancel();
        }
        
        tasks.clear();
        System.out.println("下载管理器已关闭");
    }
    
    /**
     * 下载任务
     */
    private static class DownloadTask {
        private final String url;
        private final String savePath;
        private final int threadCount;
        private final List<DownloadThread> downloadThreads = new ArrayList<>();
        private volatile DownloadStatus status = DownloadStatus.WAITING;
        private final AtomicInteger completedChunks = new AtomicInteger(0);
        private volatile long totalSize = 0;
        private final AtomicInteger downloadedSize = new AtomicInteger(0);
        
        public DownloadTask(String url, String savePath, int threadCount) {
            this.url = url;
            this.savePath = savePath;
            this.threadCount = threadCount;
            
            // 模拟文件大小
            this.totalSize = 1024 * 1024 * 10; // 10MB
        }
        
        /**
         * 开始下载
         */
        public void start() {
            if (status != DownloadStatus.WAITING && status != DownloadStatus.PAUSED) {
                return;
            }
            
            status = DownloadStatus.DOWNLOADING;
            System.out.println("开始下载: " + url);
            
            // 计算每个线程下载的块大小
            long chunkSize = totalSize / threadCount;
            
            // 创建下载线程
            for (int i = 0; i < threadCount; i++) {
                long start = i * chunkSize;
                long end = (i == threadCount - 1) ? totalSize : (i + 1) * chunkSize;
                
                DownloadThread thread = new DownloadThread(i, start, end);
                downloadThreads.add(thread);
                thread.start();
            }
        }
        
        /**
         * 暂停下载
         */
        public void pause() {
            if (status != DownloadStatus.DOWNLOADING) {
                return;
            }
            
            status = DownloadStatus.PAUSED;
            System.out.println("暂停下载: " + url);
            
            for (DownloadThread thread : downloadThreads) {
                thread.pauseDownload();
            }
        }
        
        /**
         * 恢复下载
         */
        public void resume() {
            if (status != DownloadStatus.PAUSED) {
                return;
            }
            
            status = DownloadStatus.DOWNLOADING;
            System.out.println("恢复下载: " + url);
            
            for (DownloadThread thread : downloadThreads) {
                thread.resumeDownload();
            }
        }
        
        /**
         * 取消下载
         */
        public void cancel() {
            status = DownloadStatus.CANCELLED;
            System.out.println("取消下载: " + url);
            
            for (DownloadThread thread : downloadThreads) {
                thread.cancelDownload();
            }
        }
        
        /**
         * 等待下载完成
         */
        public void waitForCompletion() {
            for (DownloadThread thread : downloadThreads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            
            if (status == DownloadStatus.DOWNLOADING) {
                status = DownloadStatus.COMPLETED;
                System.out.println("下载完成: " + url);
            }
        }
        
        /**
         * 获取状态字符串
         */
        public String getStatusString() {
            int progress = totalSize > 0 ? 
                (int) (downloadedSize.get() * 100.0 / totalSize) : 0;
            
            return String.format("[%s] %s - %d%% (%d/%d线程完成)",
                status, url, progress, completedChunks.get(), threadCount);
        }
        
        public String getUrl() {
            return url;
        }
        
        public DownloadStatus getStatus() {
            return status;
        }
        
        /**
         * 下载线程
         */
        private class DownloadThread extends Thread {
            private final int chunkId;
            private final long startPos;
            private final long endPos;
            private volatile boolean paused = false;
            private volatile boolean cancelled = false;
            private long currentPos;
            
            public DownloadThread(int chunkId, long startPos, long endPos) {
                super("Download-" + url + "-Chunk-" + chunkId);
                this.chunkId = chunkId;
                this.startPos = startPos;
                this.endPos = endPos;
                this.currentPos = startPos;
            }
            
            @Override
            public void run() {
                System.out.println("  线程 " + chunkId + " 开始下载: " + 
                                 startPos + "-" + endPos);
                
                try {
                    while (currentPos < endPos && !cancelled) {
                        // 检查暂停
                        synchronized (this) {
                            while (paused && !cancelled) {
                                wait();
                            }
                        }
                        
                        if (cancelled) {
                            break;
                        }
                        
                        // 模拟下载数据
                        long chunkSize = Math.min(1024 * 100, endPos - currentPos); // 100KB
                        Thread.sleep(100); // 模拟网络延迟
                        
                        currentPos += chunkSize;
                        downloadedSize.addAndGet((int) chunkSize);
                    }
                    
                    if (!cancelled && currentPos >= endPos) {
                        completedChunks.incrementAndGet();
                        System.out.println("  线程 " + chunkId + " 下载完成");
                    }
                    
                } catch (InterruptedException e) {
                    System.out.println("  线程 " + chunkId + " 被中断");
                }
            }
            
            public synchronized void pauseDownload() {
                paused = true;
            }
            
            public synchronized void resumeDownload() {
                paused = false;
                notify();
            }
            
            public void cancelDownload() {
                cancelled = true;
                interrupt();
            }
        }
    }
    
    /**
     * 下载状态
     */
    private enum DownloadStatus {
        WAITING("等待中"),
        DOWNLOADING("下载中"),
        PAUSED("已暂停"),
        COMPLETED("已完成"),
        CANCELLED("已取消");
        
        private final String description;
        
        DownloadStatus(String description) {
            this.description = description;
        }
        
        @Override
        public String toString() {
            return description;
        }
    }
    
    /**
     * 测试主方法
     */
    public static void main(String[] args) throws InterruptedException {
        DownloadManager manager = new DownloadManager();
        
        // 添加5个下载任务
        manager.addDownload("http://example.com/file1.zip", "/downloads/file1.zip");
        manager.addDownload("http://example.com/file2.zip", "/downloads/file2.zip");
        manager.addDownload("http://example.com/file3.zip", "/downloads/file3.zip");
        manager.addDownload("http://example.com/file4.zip", "/downloads/file4.zip");
        manager.addDownload("http://example.com/file5.zip", "/downloads/file5.zip");
        
        // 定期打印状态
        Thread monitor = new Thread(() -> {
            for (int i = 0; i < 20; i++) {
                try {
                    Thread.sleep(2000);
                    manager.printStatus();
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        monitor.start();
        
        // 测试暂停和恢复
        Thread.sleep(5000);
        System.out.println("\n>>> 暂停第一个下载");
        manager.pauseDownload("http://example.com/file1.zip");
        
        Thread.sleep(5000);
        System.out.println("\n>>> 恢复第一个下载");
        manager.resumeDownload("http://example.com/file1.zip");
        
        // 等待所有下载完成
        Thread.sleep(30000);
        
        // 关闭
        monitor.interrupt();
        manager.shutdown();
    }
}
