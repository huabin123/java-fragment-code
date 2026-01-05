package com.fragment.juc.async.project;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 并行数据处理器
 * 
 * 功能：
 * 1. 批量并行处理
 * 2. 分批处理
 * 3. 进度监控
 * 4. 异常处理
 * 5. 结果聚合
 * 
 * @author huabin
 */
public class ParallelDataProcessor {

    private final ExecutorService executor;
    private final int batchSize;

    public ParallelDataProcessor() {
        this(Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2
        ), 100);
    }

    public ParallelDataProcessor(ExecutorService executor, int batchSize) {
        this.executor = executor;
        this.batchSize = batchSize;
    }

    /**
     * 并行处理数据
     */
    public <T, R> CompletableFuture<List<R>> processParallel(
            List<T> dataList,
            ProcessFunction<T, R> processor) {
        
        List<CompletableFuture<R>> futures = dataList.stream()
            .map(data -> CompletableFuture.supplyAsync(
                () -> processor.process(data),
                executor
            ))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList())
            );
    }

    /**
     * 分批处理（避免一次性提交太多任务）
     */
    public <T, R> CompletableFuture<List<R>> processBatches(
            List<T> dataList,
            ProcessFunction<T, R> processor) {
        
        List<List<T>> batches = partition(dataList, batchSize);
        System.out.println("总数据: " + dataList.size() + ", 分" + batches.size() + "批处理");

        List<CompletableFuture<List<R>>> batchFutures = batches.stream()
            .map(batch -> processBatch(batch, processor))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
            .thenApply(v -> batchFutures.stream()
                .flatMap(f -> f.join().stream())
                .collect(Collectors.toList())
            );
    }

    /**
     * 处理单个批次
     */
    private <T, R> CompletableFuture<List<R>> processBatch(
            List<T> batch,
            ProcessFunction<T, R> processor) {
        
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("  [批次] 处理" + batch.size() + "条数据");
            return batch.stream()
                .map(processor::process)
                .collect(Collectors.toList());
        }, executor);
    }

    /**
     * 带进度监控的处理
     */
    public <T, R> CompletableFuture<ProcessResult<R>> processWithProgress(
            List<T> dataList,
            ProcessFunction<T, R> processor) {
        
        ProgressTracker tracker = new ProgressTracker(dataList.size());

        List<CompletableFuture<R>> futures = dataList.stream()
            .map(data -> CompletableFuture.supplyAsync(() -> {
                try {
                    R result = processor.process(data);
                    tracker.incrementSuccess();
                    return result;
                } catch (Exception e) {
                    tracker.incrementFailure();
                    throw e;
                }
            }, executor))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<R> results = futures.stream()
                    .map(f -> {
                        try {
                            return f.join();
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

                return new ProcessResult<>(
                    results,
                    tracker.getSuccessCount(),
                    tracker.getFailureCount()
                );
            });
    }

    /**
     * 带异常处理的处理（部分失败不影响整体）
     */
    public <T, R> CompletableFuture<List<R>> processWithFallback(
            List<T> dataList,
            ProcessFunction<T, R> processor,
            R defaultValue) {
        
        List<CompletableFuture<R>> futures = dataList.stream()
            .map(data -> CompletableFuture.supplyAsync(
                () -> processor.process(data),
                executor
            ).exceptionally(ex -> {
                System.err.println("  处理失败，使用默认值: " + ex.getMessage());
                return defaultValue;
            }))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
            );
    }

    /**
     * MapReduce模式
     */
    public <T, R> CompletableFuture<R> mapReduce(
            List<T> dataList,
            ProcessFunction<T, R> mapper,
            ReduceFunction<R> reducer) {
        
        return processParallel(dataList, mapper)
            .thenApply(results -> results.stream()
                .reduce(reducer::reduce)
                .orElse(null)
            );
    }

    /**
     * 聚合统计
     */
    public <T> CompletableFuture<Statistics> aggregate(
            List<T> dataList,
            AggregateFunction<T> aggregator) {
        
        List<List<T>> batches = partition(dataList, batchSize);

        List<CompletableFuture<Statistics>> futures = batches.stream()
            .map(batch -> CompletableFuture.supplyAsync(
                () -> aggregator.aggregate(batch),
                executor
            ))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<Statistics> stats = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
                
                return Statistics.merge(stats);
            });
    }

    // ========== 工具方法 ==========

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    // ========== 函数式接口 ==========

    @FunctionalInterface
    public interface ProcessFunction<T, R> {
        R process(T data);
    }

    @FunctionalInterface
    public interface ReduceFunction<R> {
        R reduce(R r1, R r2);
    }

    @FunctionalInterface
    public interface AggregateFunction<T> {
        Statistics aggregate(List<T> batch);
    }

    // ========== 辅助类 ==========

    public static class ProcessResult<R> {
        private final List<R> results;
        private final int successCount;
        private final int failureCount;

        public ProcessResult(List<R> results, int successCount, int failureCount) {
            this.results = results;
            this.successCount = successCount;
            this.failureCount = failureCount;
        }

        public List<R> getResults() {
            return results;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailureCount() {
            return failureCount;
        }

        @Override
        public String toString() {
            return "ProcessResult{success=" + successCount + 
                   ", failure=" + failureCount + 
                   ", results=" + results.size() + "}";
        }
    }

    private static class ProgressTracker {
        private final int total;
        private int success = 0;
        private int failure = 0;

        ProgressTracker(int total) {
            this.total = total;
        }

        synchronized void incrementSuccess() {
            success++;
            printProgress();
        }

        synchronized void incrementFailure() {
            failure++;
            printProgress();
        }

        private void printProgress() {
            int completed = success + failure;
            int percentage = (int) ((completed * 100.0) / total);
            System.out.println("  [进度] " + percentage + "% (" + 
                             completed + "/" + total + 
                             ", 成功:" + success + ", 失败:" + failure + ")");
        }

        int getSuccessCount() {
            return success;
        }

        int getFailureCount() {
            return failure;
        }
    }

    public static class Statistics {
        private long count;
        private double sum;
        private double min;
        private double max;

        public Statistics(long count, double sum, double min, double max) {
            this.count = count;
            this.sum = sum;
            this.min = min;
            this.max = max;
        }

        public static Statistics merge(List<Statistics> statsList) {
            long totalCount = statsList.stream().mapToLong(s -> s.count).sum();
            double totalSum = statsList.stream().mapToDouble(s -> s.sum).sum();
            double minValue = statsList.stream().mapToDouble(s -> s.min).min().orElse(0);
            double maxValue = statsList.stream().mapToDouble(s -> s.max).max().orElse(0);
            
            return new Statistics(totalCount, totalSum, minValue, maxValue);
        }

        public double getAverage() {
            return count > 0 ? sum / count : 0;
        }

        @Override
        public String toString() {
            return String.format("Statistics{count=%d, sum=%.2f, avg=%.2f, min=%.2f, max=%.2f}",
                count, sum, getAverage(), min, max);
        }
    }

    // ========== 测试 ==========

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            并行数据处理器演示                               ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        ParallelDataProcessor processor = new ParallelDataProcessor();

        // 测试1：并行处理
        System.out.println("\n========== 测试1：并行处理 ==========\n");
        List<Integer> numbers = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            numbers.add(i);
        }

        List<Integer> results1 = processor.processParallel(
            numbers,
            n -> {
                System.out.println("  处理: " + n);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return n * 2;
            }
        ).get();

        System.out.println("结果: " + results1);

        // 测试2：分批处理
        System.out.println("\n========== 测试2：分批处理 ==========\n");
        List<Integer> largeData = new ArrayList<>();
        for (int i = 1; i <= 250; i++) {
            largeData.add(i);
        }

        List<Integer> results2 = processor.processBatches(
            largeData,
            n -> n * 2
        ).get();

        System.out.println("处理完成，结果数: " + results2.size());

        // 测试3：带进度监控
        System.out.println("\n========== 测试3：带进度监控 ==========\n");
        ProcessResult<Integer> result3 = processor.processWithProgress(
            numbers,
            n -> {
                if (n % 3 == 0) {
                    throw new RuntimeException("模拟失败");
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return n * 2;
            }
        ).get();

        System.out.println("\n" + result3);

        // 测试4：MapReduce
        System.out.println("\n========== 测试4：MapReduce求和 ==========\n");
        Integer sum = processor.mapReduce(
            numbers,
            n -> n,
            (a, b) -> a + b
        ).get();

        System.out.println("总和: " + sum);

        // 测试5：聚合统计
        System.out.println("\n========== 测试5：聚合统计 ==========\n");
        List<Double> values = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0);
        
        Statistics stats = processor.aggregate(
            values,
            batch -> {
                double sum1 = batch.stream().mapToDouble(d -> d).sum();
                double min = batch.stream().mapToDouble(d -> d).min().orElse(0);
                double max = batch.stream().mapToDouble(d -> d).max().orElse(0);
                return new Statistics(batch.size(), sum1, min, max);
            }
        ).get();

        System.out.println(stats);

        System.out.println("\n===========================");
        System.out.println("✅ 并行数据处理器演示完成");
        System.out.println("===========================");

        System.exit(0);
    }
}
