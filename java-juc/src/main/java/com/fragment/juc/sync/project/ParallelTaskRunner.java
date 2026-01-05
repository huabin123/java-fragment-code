package com.fragment.juc.sync.project;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 并行任务执行器
 * 
 * 实现内容：
 * 1. 基于CountDownLatch的并行任务执行
 * 2. 基于CyclicBarrier的分阶段执行
 * 3. 任务超时控制
 * 4. 异常处理
 * 5. 结果收集
 * 
 * @author huabin
 */
public class ParallelTaskRunner {

    /**
     * 任务接口
     */
    interface Task<T> {
        T execute() throws Exception;
        String getName();
    }

    /**
     * 任务结果
     */
    static class TaskResult<T> {
        final String taskName;
        final T result;
        final Exception exception;
        final long executionTime;

        TaskResult(String taskName, T result, Exception exception, long executionTime) {
            this.taskName = taskName;
            this.result = result;
            this.exception = exception;
            this.executionTime = executionTime;
        }

        boolean isSuccess() {
            return exception == null;
        }

        @Override
        public String toString() {
            if (isSuccess()) {
                return "TaskResult{" + taskName + ", result=" + result + 
                       ", time=" + executionTime + "ms}";
            } else {
                return "TaskResult{" + taskName + ", error=" + exception.getMessage() + 
                       ", time=" + executionTime + "ms}";
            }
        }
    }

    /**
     * 基于CountDownLatch的并行执行器
     */
    static class CountDownLatchRunner<T> {
        private final ExecutorService executor;

        public CountDownLatchRunner(int threadPoolSize) {
            this.executor = Executors.newFixedThreadPool(threadPoolSize);
        }

        /**
         * 并行执行任务，等待所有任务完成
         */
        public List<TaskResult<T>> executeAll(List<Task<T>> tasks) throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(tasks.size());
            List<TaskResult<T>> results = new CopyOnWriteArrayList<>();

            for (Task<T> task : tasks) {
                executor.submit(() -> {
                    long startTime = System.currentTimeMillis();
                    try {
                        T result = task.execute();
                        long executionTime = System.currentTimeMillis() - startTime;
                        results.add(new TaskResult<>(task.getName(), result, null, executionTime));
                    } catch (Exception e) {
                        long executionTime = System.currentTimeMillis() - startTime;
                        results.add(new TaskResult<>(task.getName(), null, e, executionTime));
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            return results;
        }

        /**
         * 并行执行任务，支持超时
         */
        public List<TaskResult<T>> executeAll(List<Task<T>> tasks, long timeout, TimeUnit unit) 
                throws InterruptedException, TimeoutException {
            CountDownLatch latch = new CountDownLatch(tasks.size());
            List<TaskResult<T>> results = new CopyOnWriteArrayList<>();

            for (Task<T> task : tasks) {
                executor.submit(() -> {
                    long startTime = System.currentTimeMillis();
                    try {
                        T result = task.execute();
                        long executionTime = System.currentTimeMillis() - startTime;
                        results.add(new TaskResult<>(task.getName(), result, null, executionTime));
                    } catch (Exception e) {
                        long executionTime = System.currentTimeMillis() - startTime;
                        results.add(new TaskResult<>(task.getName(), null, e, executionTime));
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean finished = latch.await(timeout, unit);
            if (!finished) {
                throw new TimeoutException("任务执行超时");
            }

            return results;
        }

        public void shutdown() {
            executor.shutdown();
        }
    }

    /**
     * 基于CyclicBarrier的分阶段执行器
     */
    static class CyclicBarrierRunner<T> {
        private final int workerCount;

        public CyclicBarrierRunner(int workerCount) {
            this.workerCount = workerCount;
        }

        /**
         * 分阶段执行任务
         */
        public void executePhases(List<List<Task<T>>> phases, Consumer<Integer> phaseCallback) 
                throws InterruptedException {
            CyclicBarrier barrier = new CyclicBarrier(workerCount, () -> {
                // 阶段完成回调
                if (phaseCallback != null) {
                    phaseCallback.accept(barrier.getParties());
                }
            });

            CountDownLatch completionLatch = new CountDownLatch(workerCount);

            for (int i = 0; i < workerCount; i++) {
                final int workerId = i;
                new Thread(() -> {
                    try {
                        for (int phaseIndex = 0; phaseIndex < phases.size(); phaseIndex++) {
                            List<Task<T>> phaseTasks = phases.get(phaseIndex);
                            if (workerId < phaseTasks.size()) {
                                Task<T> task = phaseTasks.get(workerId);
                                System.out.println("[Worker-" + workerId + "] 阶段" + 
                                                 (phaseIndex + 1) + ": " + task.getName());
                                task.execute();
                            }
                            barrier.await(); // 等待所有worker完成当前阶段
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        completionLatch.countDown();
                    }
                }, "Worker-" + workerId).start();
            }

            completionLatch.await();
        }
    }

    /**
     * 演示1：并行任务执行
     */
    public static void demoParallelExecution() throws Exception {
        System.out.println("\n========== 演示1：并行任务执行 ==========\n");

        CountDownLatchRunner<Integer> runner = new CountDownLatchRunner<>(4);

        // 创建任务
        List<Task<Integer>> tasks = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            tasks.add(new Task<Integer>() {
                @Override
                public Integer execute() throws Exception {
                    System.out.println("[任务" + taskId + "] 开始执行");
                    Thread.sleep((long) (Math.random() * 2000));
                    int result = taskId * 10;
                    System.out.println("[任务" + taskId + "] 完成，结果: " + result);
                    return result;
                }

                @Override
                public String getName() {
                    return "Task-" + taskId;
                }
            });
        }

        System.out.println("执行5个并行任务...\n");
        long startTime = System.currentTimeMillis();
        List<TaskResult<Integer>> results = runner.executeAll(tasks);
        long totalTime = System.currentTimeMillis() - startTime;

        System.out.println("\n任务执行完成！");
        System.out.println("总耗时: " + totalTime + "ms");
        System.out.println("\n结果:");
        for (TaskResult<Integer> result : results) {
            System.out.println("  " + result);
        }

        runner.shutdown();
        System.out.println("\n✅ 并行执行提高了效率");
    }

    /**
     * 演示2：超时控制
     */
    public static void demoTimeout() throws Exception {
        System.out.println("\n========== 演示2：超时控制 ==========\n");

        CountDownLatchRunner<String> runner = new CountDownLatchRunner<>(3);

        List<Task<String>> tasks = new ArrayList<>();
        
        // 快速任务
        tasks.add(new Task<String>() {
            @Override
            public String execute() throws Exception {
                Thread.sleep(500);
                return "快速任务完成";
            }
            @Override
            public String getName() {
                return "FastTask";
            }
        });

        // 慢速任务
        tasks.add(new Task<String>() {
            @Override
            public String execute() throws Exception {
                Thread.sleep(5000);
                return "慢速任务完成";
            }
            @Override
            public String getName() {
                return "SlowTask";
            }
        });

        try {
            System.out.println("执行任务，最多等待2秒...\n");
            runner.executeAll(tasks, 2, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.out.println("\n❌ " + e.getMessage());
        }

        runner.shutdown();
        System.out.println("\n✅ 超时控制避免了无限期等待");
    }

    /**
     * 演示3：异常处理
     */
    public static void demoExceptionHandling() throws Exception {
        System.out.println("\n========== 演示3：异常处理 ==========\n");

        CountDownLatchRunner<String> runner = new CountDownLatchRunner<>(3);

        List<Task<String>> tasks = new ArrayList<>();
        
        // 正常任务
        tasks.add(new Task<String>() {
            @Override
            public String execute() {
                return "任务1成功";
            }
            @Override
            public String getName() {
                return "Task-1";
            }
        });

        // 异常任务
        tasks.add(new Task<String>() {
            @Override
            public String execute() throws Exception {
                throw new RuntimeException("任务2失败");
            }
            @Override
            public String getName() {
                return "Task-2";
            }
        });

        // 正常任务
        tasks.add(new Task<String>() {
            @Override
            public String execute() {
                return "任务3成功";
            }
            @Override
            public String getName() {
                return "Task-3";
            }
        });

        List<TaskResult<String>> results = runner.executeAll(tasks);

        System.out.println("任务执行结果:");
        int successCount = 0;
        int failureCount = 0;
        for (TaskResult<String> result : results) {
            System.out.println("  " + result);
            if (result.isSuccess()) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        System.out.println("\n统计:");
        System.out.println("  成功: " + successCount);
        System.out.println("  失败: " + failureCount);

        runner.shutdown();
        System.out.println("\n✅ 单个任务失败不影响其他任务");
    }

    /**
     * 演示4：分阶段执行
     */
    public static void demoPhaseExecution() throws Exception {
        System.out.println("\n========== 演示4：分阶段执行 ==========\n");

        CyclicBarrierRunner<Void> runner = new CyclicBarrierRunner<>(3);

        // 定义3个阶段的任务
        List<List<Task<Void>>> phases = new ArrayList<>();

        // 阶段1：初始化
        List<Task<Void>> phase1 = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            final int taskId = i;
            phase1.add(new Task<Void>() {
                @Override
                public Void execute() throws Exception {
                    Thread.sleep((long) (Math.random() * 1000));
                    return null;
                }
                @Override
                public String getName() {
                    return "初始化-" + taskId;
                }
            });
        }
        phases.add(phase1);

        // 阶段2：数据处理
        List<Task<Void>> phase2 = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            final int taskId = i;
            phase2.add(new Task<Void>() {
                @Override
                public Void execute() throws Exception {
                    Thread.sleep((long) (Math.random() * 1000));
                    return null;
                }
                @Override
                public String getName() {
                    return "数据处理-" + taskId;
                }
            });
        }
        phases.add(phase2);

        // 阶段3：结果输出
        List<Task<Void>> phase3 = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            final int taskId = i;
            phase3.add(new Task<Void>() {
                @Override
                public Void execute() throws Exception {
                    Thread.sleep((long) (Math.random() * 1000));
                    return null;
                }
                @Override
                public String getName() {
                    return "结果输出-" + taskId;
                }
            });
        }
        phases.add(phase3);

        // 执行
        runner.executePhases(phases, phase -> {
            System.out.println("\n>>> 阶段完成 <<<\n");
        });

        System.out.println("\n✅ 分阶段执行适合流水线处理");
    }

    /**
     * 演示5：实际应用 - 数据批量处理
     */
    public static void demoBatchProcessing() throws Exception {
        System.out.println("\n========== 演示5：数据批量处理 ==========\n");

        CountDownLatchRunner<Integer> runner = new CountDownLatchRunner<>(4);

        // 模拟1000条数据，分批处理
        int totalRecords = 1000;
        int batchSize = 250;
        List<Task<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < totalRecords; i += batchSize) {
            final int start = i;
            final int end = Math.min(i + batchSize, totalRecords);
            tasks.add(new Task<Integer>() {
                @Override
                public Integer execute() throws Exception {
                    System.out.println("[批次] 处理记录 " + start + "-" + end);
                    Thread.sleep(500); // 模拟处理
                    return end - start;
                }
                @Override
                public String getName() {
                    return "Batch-" + start + "-" + end;
                }
            });
        }

        System.out.println("处理" + totalRecords + "条记录，分" + tasks.size() + "批...\n");
        long startTime = System.currentTimeMillis();
        List<TaskResult<Integer>> results = runner.executeAll(tasks);
        long totalTime = System.currentTimeMillis() - startTime;

        int processedCount = 0;
        for (TaskResult<Integer> result : results) {
            if (result.isSuccess()) {
                processedCount += result.result;
            }
        }

        System.out.println("\n处理完成！");
        System.out.println("  总记录数: " + totalRecords);
        System.out.println("  已处理: " + processedCount);
        System.out.println("  总耗时: " + totalTime + "ms");
        System.out.println("  平均速度: " + (processedCount * 1000 / totalTime) + " 条/秒");

        runner.shutdown();
        System.out.println("\n✅ 并行处理大幅提升了吞吐量");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== 并行任务执行器总结 ==========");

        System.out.println("\n✅ 核心功能:");
        System.out.println("   1. 并行执行：多个任务同时执行");
        System.out.println("   2. 超时控制：避免无限期等待");
        System.out.println("   3. 异常处理：单个任务失败不影响整体");
        System.out.println("   4. 结果收集：统一收集所有任务结果");
        System.out.println("   5. 分阶段执行：支持流水线处理");

        System.out.println("\n📊 两种实现对比:");
        System.out.println("   CountDownLatch:");
        System.out.println("     - 适合一次性并行执行");
        System.out.println("     - 主线程等待所有任务完成");
        System.out.println("     - 支持超时控制");
        System.out.println("   CyclicBarrier:");
        System.out.println("     - 适合分阶段执行");
        System.out.println("     - 线程互相等待");
        System.out.println("     - 支持阶段回调");

        System.out.println("\n💡 适用场景:");
        System.out.println("   ✅ 批量数据处理");
        System.out.println("   ✅ 并行计算");
        System.out.println("   ✅ 多数据源查询");
        System.out.println("   ✅ 流水线处理");

        System.out.println("\n⚠️  注意事项:");
        System.out.println("   1. 合理设置线程池大小");
        System.out.println("   2. 注意任务粒度，避免过细");
        System.out.println("   3. 做好异常处理");
        System.out.println("   4. 考虑超时控制");
        System.out.println("   5. 及时关闭线程池");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              并行任务执行器                                  ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：并行执行
        demoParallelExecution();

        // 演示2：超时控制
        demoTimeout();

        // 演示3：异常处理
        demoExceptionHandling();

        // 演示4：分阶段执行
        demoPhaseExecution();

        // 演示5：批量处理
        demoBatchProcessing();

        // 总结
        summarize();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. 并行执行可以大幅提升性能");
        System.out.println("2. CountDownLatch适合一次性并行执行");
        System.out.println("3. CyclicBarrier适合分阶段执行");
        System.out.println("4. 要做好超时控制和异常处理");
        System.out.println("===========================");
    }
}
