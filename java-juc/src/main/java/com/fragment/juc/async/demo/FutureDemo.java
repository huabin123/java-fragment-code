package com.fragment.juc.async.demo;

import java.util.concurrent.*;

/**
 * Future基础演示
 * 
 * 演示内容：
 * 1. Future基本使用
 * 2. Callable vs Runnable
 * 3. 超时控制
 * 4. 取消任务
 * 5. Future的局限性
 * 
 * @author huabin
 */
public class FutureDemo {

    /**
     * 演示1：Future基本使用
     */
    public static void demoBasicFuture() throws Exception {
        System.out.println("\n========== 演示1：Future基本使用 ==========\n");

        ExecutorService executor = Executors.newFixedThreadPool(3);

        // 提交任务
        Future<String> future = executor.submit(() -> {
            System.out.println("  [" + Thread.currentThread().getName() + "] 任务开始执行");
            Thread.sleep(2000);
            System.out.println("  [" + Thread.currentThread().getName() + "] 任务执行完成");
            return "Hello Future";
        });

        System.out.println("任务已提交，继续做其他事情");

        // 做其他事情
        Thread.sleep(1000);
        System.out.println("做了1秒的其他工作");

        // 获取结果
        System.out.println("开始获取结果（阻塞等待）");
        String result = future.get();
        System.out.println("结果: " + result);

        executor.shutdown();

        System.out.println("\n✅ Future实现了计算和获取结果的分离");
    }

    /**
     * 演示2：Callable vs Runnable
     */
    public static void demoCallableVsRunnable() throws Exception {
        System.out.println("\n========== 演示2：Callable vs Runnable ==========\n");

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Runnable：无返回值
        System.out.println("1. Runnable（无返回值）:");
        Future<?> runnableFuture = executor.submit(() -> {
            System.out.println("  执行Runnable任务");
        });
        Object runnableResult = runnableFuture.get();
        System.out.println("  Runnable结果: " + runnableResult); // null

        // Callable：有返回值
        System.out.println("\n2. Callable（有返回值）:");
        Future<Integer> callableFuture = executor.submit(() -> {
            System.out.println("  执行Callable任务");
            return 42;
        });
        Integer callableResult = callableFuture.get();
        System.out.println("  Callable结果: " + callableResult); // 42

        executor.shutdown();

        System.out.println("\n✅ Callable可以返回结果和抛出异常");
    }

    /**
     * 演示3：超时控制
     */
    public static void demoTimeout() {
        System.out.println("\n========== 演示3：超时控制 ==========\n");

        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<String> future = executor.submit(() -> {
            System.out.println("  开始执行长时间任务");
            Thread.sleep(5000); // 模拟耗时操作
            return "Result";
        });

        try {
            // 最多等待2秒
            System.out.println("等待结果（最多2秒）");
            String result = future.get(2, TimeUnit.SECONDS);
            System.out.println("结果: " + result);
        } catch (TimeoutException e) {
            System.out.println("  任务超时！");
            future.cancel(true); // 取消任务
            System.out.println("  任务已取消");
        } catch (Exception e) {
            e.printStackTrace();
        }

        executor.shutdown();

        System.out.println("\n✅ 超时控制避免了无限期等待");
    }

    /**
     * 演示4：取消任务
     */
    public static void demoCancelTask() throws Exception {
        System.out.println("\n========== 演示4：取消任务 ==========\n");

        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<String> future = executor.submit(() -> {
            for (int i = 0; i < 10; i++) {
                // 检查中断标志
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println("  任务被中断");
                    return "Cancelled";
                }
                Thread.sleep(500);
                System.out.println("  执行中: " + i);
            }
            return "Completed";
        });

        // 2秒后取消
        Thread.sleep(2000);
        System.out.println("\n取消任务");
        boolean cancelled = future.cancel(true); // 中断线程

        System.out.println("是否取消成功: " + cancelled);
        System.out.println("是否已取消: " + future.isCancelled());
        System.out.println("是否已完成: " + future.isDone());

        executor.shutdown();

        System.out.println("\n✅ cancel()可以取消正在执行的任务");
    }

    /**
     * 演示5：Future的局限性
     */
    public static void demoLimitations() throws Exception {
        System.out.println("\n========== 演示5：Future的局限性 ==========\n");

        ExecutorService executor = Executors.newFixedThreadPool(3);

        System.out.println("局限1：只能通过get()阻塞获取结果");
        Future<String> future1 = executor.submit(() -> "result");
        String result1 = future1.get(); // 必须阻塞
        System.out.println("  结果: " + result1);

        System.out.println("\n局限2：无法链式调用");
        Future<String> future2 = executor.submit(() -> "hello");
        String result2 = future2.get(); // 阻塞
        // 想要对结果再处理，必须手动提交新任务
        Future<String> future3 = executor.submit(() -> result2.toUpperCase());
        String result3 = future3.get(); // 又要阻塞
        System.out.println("  结果: " + result3);

        System.out.println("\n局限3：无法组合多个Future");
        Future<String> futureA = executor.submit(() -> "A");
        Future<String> futureB = executor.submit(() -> "B");
        // 想要等待两个都完成，必须分别get()
        String resultA = futureA.get(); // 阻塞
        String resultB = futureB.get(); // 阻塞
        String combined = resultA + resultB;
        System.out.println("  组合结果: " + combined);

        System.out.println("\n局限4：异常处理不便");
        Future<String> future4 = executor.submit(() -> {
            throw new RuntimeException("error");
        });
        try {
            future4.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause(); // 需要unwrap
            System.out.println("  异常: " + cause.getMessage());
        }

        executor.shutdown();

        System.out.println("\n✅ 这些局限导致了CompletableFuture的诞生");
    }

    /**
     * 演示6：批量任务处理
     */
    public static void demoBatchTasks() throws Exception {
        System.out.println("\n========== 演示6：批量任务处理 ==========\n");

        ExecutorService executor = Executors.newFixedThreadPool(5);

        // 提交多个任务
        System.out.println("提交5个任务:");
        Future<Integer>[] futures = new Future[5];
        for (int i = 0; i < 5; i++) {
            final int taskId = i + 1;
            futures[i] = executor.submit(() -> {
                Thread.sleep(taskId * 500);
                System.out.println("  任务" + taskId + "完成");
                return taskId * 10;
            });
        }

        // 等待所有任务完成
        System.out.println("\n等待所有任务完成:");
        int sum = 0;
        for (Future<Integer> future : futures) {
            sum += future.get(); // 按顺序阻塞等待
        }

        System.out.println("所有任务完成，总和: " + sum);

        executor.shutdown();

        System.out.println("\n✅ Future可以处理批量任务，但需要手动管理");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== Future总结 ==========");

        System.out.println("\n✅ 核心特性:");
        System.out.println("   1. 异步执行：提交任务后立即返回");
        System.out.println("   2. 结果获取：通过get()获取结果");
        System.out.println("   3. 超时控制：get(timeout)避免无限等待");
        System.out.println("   4. 任务取消：cancel()取消任务");

        System.out.println("\n📊 核心方法:");
        System.out.println("   get()              - 阻塞获取结果");
        System.out.println("   get(timeout)       - 超时获取结果");
        System.out.println("   cancel(interrupt)  - 取消任务");
        System.out.println("   isDone()           - 是否完成");
        System.out.println("   isCancelled()      - 是否取消");

        System.out.println("\n💡 适用场景:");
        System.out.println("   ✅ 简单的异步任务");
        System.out.println("   ✅ 需要获取结果的计算");
        System.out.println("   ✅ 批量并行处理");

        System.out.println("\n⚠️  局限性:");
        System.out.println("   ❌ 只能阻塞获取结果");
        System.out.println("   ❌ 无法链式调用");
        System.out.println("   ❌ 无法组合多个Future");
        System.out.println("   ❌ 异常处理不便");

        System.out.println("\n🔄 vs CompletableFuture:");
        System.out.println("   Future:");
        System.out.println("     - 简单，易用");
        System.out.println("     - 功能有限");
        System.out.println("   CompletableFuture:");
        System.out.println("     - 功能强大");
        System.out.println("     - 支持链式调用和组合");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            Future基础演示                                   ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：基本使用
        demoBasicFuture();

        // 演示2：Callable vs Runnable
        demoCallableVsRunnable();

        // 演示3：超时控制
        demoTimeout();

        // 演示4：取消任务
        demoCancelTask();

        // 演示5：局限性
        demoLimitations();

        // 演示6：批量任务
        demoBatchTasks();

        // 总结
        summarize();

        System.out.println("\n===========================");
        System.out.println("核心要点：");
        System.out.println("1. Future实现了计算和获取结果的分离");
        System.out.println("2. Callable可以返回结果和抛出异常");
        System.out.println("3. 超时控制避免了无限期等待");
        System.out.println("4. Future的局限性导致了CompletableFuture的诞生");
        System.out.println("===========================");
    }
}
