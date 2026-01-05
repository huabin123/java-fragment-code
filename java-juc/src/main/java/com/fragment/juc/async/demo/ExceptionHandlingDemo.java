package com.fragment.juc.async.demo;

import java.util.concurrent.*;

/**
 * 异常处理演示
 * 
 * 演示内容：
 * 1. exceptionally - 处理异常
 * 2. handle - 处理结果或异常
 * 3. whenComplete - 观察完成
 * 4. 异常传播
 * 5. 超时控制
 * 
 * @author huabin
 */
public class ExceptionHandlingDemo {

    private static final ExecutorService executor = Executors.newFixedThreadPool(5);

    /**
     * 演示1：exceptionally - 处理异常
     */
    public static void demoExceptionally() throws Exception {
        System.out.println("\n========== 演示1：exceptionally处理异常 ==========\n");

        CompletableFuture<String> future = CompletableFuture
            .supplyAsync(() -> {
                System.out.println("  执行任务");
                if (Math.random() > 0.5) {
                    throw new RuntimeException("随机异常");
                }
                return "成功";
            }, executor)
            .exceptionally(ex -> {
                System.err.println("  捕获异常: " + ex.getMessage());
                return "默认值"; // 返回降级结果
            });

        String result = future.get();
        System.out.println("结果: " + result);

        System.out.println("\n✅ exceptionally提供了降级方案");
    }

    /**
     * 演示2：handle - 处理结果或异常
     */
    public static void demoHandle() throws Exception {
        System.out.println("\n========== 演示2：handle处理结果或异常 ==========\n");

        CompletableFuture<String> future = CompletableFuture
            .supplyAsync(() -> {
                if (Math.random() > 0.5) {
                    throw new RuntimeException("错误");
                }
                return "成功";
            }, executor)
            .handle((result, ex) -> {
                if (ex != null) {
                    System.err.println("  处理异常: " + ex.getMessage());
                    return "错误处理: " + ex.getMessage();
                } else {
                    System.out.println("  处理成功: " + result);
                    return "成功处理: " + result;
                }
            });

        String result = future.get();
        System.out.println("结果: " + result);

        System.out.println("\n✅ handle可以同时处理成功和失败");
    }

    /**
     * 演示3：whenComplete - 观察完成
     */
    public static void demoWhenComplete() throws Exception {
        System.out.println("\n========== 演示3：whenComplete观察完成 ==========\n");

        long startTime = System.currentTimeMillis();

        CompletableFuture<String> future = CompletableFuture
            .supplyAsync(() -> {
                sleep(1000);
                return "结果";
            }, executor)
            .whenComplete((result, ex) -> {
                long duration = System.currentTimeMillis() - startTime;
                if (ex != null) {
                    System.err.println("  失败，耗时: " + duration + "ms");
                } else {
                    System.out.println("  成功，耗时: " + duration + "ms，结果: " + result);
                }
            });

        String result = future.get();
        System.out.println("最终结果: " + result);

        System.out.println("\n✅ whenComplete适合记录日志和监控");
    }

    /**
     * 演示4：异常传播
     */
    public static void demoExceptionPropagation() throws Exception {
        System.out.println("\n========== 演示4：异常传播 ==========\n");

        System.out.println("场景1：异常跳过后续步骤\n");

        CompletableFuture.supplyAsync(() -> {
            System.out.println("  步骤1");
            throw new RuntimeException("步骤1异常");
        }, executor)
        .thenApply(result -> {
            System.out.println("  步骤2"); // 不会执行
            return result.toString().toUpperCase();
        })
        .thenApply(result -> {
            System.out.println("  步骤3"); // 不会执行
            return result + "!";
        })
        .exceptionally(ex -> {
            System.err.println("  捕获异常: " + ex.getMessage());
            return "默认值";
        })
        .thenAccept(result -> {
            System.out.println("  最终结果: " + result);
        })
        .get();

        System.out.println("\n场景2：中途捕获异常，恢复流程\n");

        CompletableFuture.supplyAsync(() -> {
            System.out.println("  步骤1");
            throw new RuntimeException("步骤1异常");
        }, executor)
        .exceptionally(ex -> {
            System.err.println("  捕获步骤1异常，恢复流程");
            return "恢复值";
        })
        .thenApply(result -> {
            System.out.println("  步骤2: " + result); // 会执行
            return result.toUpperCase();
        })
        .thenAccept(result -> {
            System.out.println("  步骤3: " + result); // 会执行
        })
        .get();

        System.out.println("\n✅ 异常会传播到最近的异常处理器");
    }

    /**
     * 演示5：超时控制（JDK 8兼容）
     */
    public static void demoTimeout() throws Exception {
        System.out.println("\n========== 演示5：超时控制 ==========\n");

        System.out.println("场景1：任务超时\n");

        CompletableFuture<String> slowTask = CompletableFuture.supplyAsync(() -> {
            System.out.println("  开始执行慢任务");
            sleep(3000);
            return "慢任务结果";
        }, executor);

        CompletableFuture<String> timeout = new CompletableFuture<>();
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> {
            timeout.completeExceptionally(new TimeoutException("任务超时"));
        }, 1, TimeUnit.SECONDS);

        CompletableFuture<String> result = slowTask.applyToEither(timeout, r -> r)
            .exceptionally(ex -> {
                System.err.println("  " + ex.getMessage());
                return "超时降级值";
            });

        System.out.println("结果: " + result.get());
        scheduler.shutdown();

        System.out.println("\n场景2：超时后使用降级数据\n");

        String key = "user123";
        CompletableFuture<String> primary = CompletableFuture.supplyAsync(() -> {
            System.out.println("  查询主数据源");
            sleep(2000);
            return "主数据源结果";
        }, executor);

        CompletableFuture<String> timeoutFuture = new CompletableFuture<>();
        ScheduledExecutorService scheduler2 = Executors.newScheduledThreadPool(1);
        scheduler2.schedule(() -> {
            timeoutFuture.completeExceptionally(new TimeoutException());
        }, 1, TimeUnit.SECONDS);

        CompletableFuture<String> withFallback = primary.applyToEither(timeoutFuture, r -> r)
            .exceptionally(ex -> {
                System.out.println("  主数据源超时，使用降级数据");
                return "降级数据: " + key;
            });

        System.out.println("结果: " + withFallback.get());
        scheduler2.shutdown();

        System.out.println("\n✅ 超时控制保证了响应时间");
    }

    /**
     * 演示6：重试机制
     */
    public static void demoRetry() throws Exception {
        System.out.println("\n========== 演示6：重试机制 ==========\n");

        CompletableFuture<String> result = retryAsync(() -> {
            System.out.println("  尝试调用外部服务");
            if (Math.random() > 0.7) {
                return "成功";
            }
            throw new RuntimeException("调用失败");
        }, 3);

        System.out.println("最终结果: " + result.get());

        System.out.println("\n✅ 重试机制提高了成功率");
    }

    /**
     * 演示7：组合异常处理
     */
    public static void demoCombinedExceptionHandling() throws Exception {
        System.out.println("\n========== 演示7：组合异常处理 ==========\n");

        CompletableFuture<String> future1 = CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("Future1异常");
        }, executor);

        CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> {
            return "Future2成功";
        }, executor);

        CompletableFuture<Void> allOf = CompletableFuture.allOf(future1, future2);

        allOf.handle((result, ex) -> {
            if (ex != null) {
                System.err.println("  有Future失败");
            }
            
            // 收集成功的结果
            try {
                String r1 = future1.join();
                System.out.println("  Future1: " + r1);
            } catch (Exception e) {
                System.err.println("  Future1失败: " + e.getMessage());
            }
            
            try {
                String r2 = future2.join();
                System.out.println("  Future2: " + r2);
            } catch (Exception e) {
                System.err.println("  Future2失败: " + e.getMessage());
            }
            
            return null;
        }).get();

        System.out.println("\n✅ 可以单独处理每个Future的异常");
    }

    // ========== 工具方法 ==========

    /**
     * 带重试的异步调用
     */
    public static CompletableFuture<String> retryAsync(Callable<String> task, int maxRetries) {
        return retryAsync(task, maxRetries, 0);
    }

    private static CompletableFuture<String> retryAsync(Callable<String> task, int maxRetries, int attempt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor).exceptionally(ex -> {
            if (attempt < maxRetries) {
                System.out.println("  调用失败，重试第" + (attempt + 1) + "次");
                sleep(1000 * (attempt + 1)); // 指数退避
                return retryAsync(task, maxRetries, attempt + 1).join();
            } else {
                System.err.println("  调用失败，已达最大重试次数");
                throw new RuntimeException("调用失败", ex);
            }
        });
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== 异常处理总结 ==========");

        System.out.println("\n✅ 核心方法:");
        System.out.println("   exceptionally  - 处理异常，返回默认值");
        System.out.println("   handle         - 处理结果或异常");
        System.out.println("   whenComplete   - 观察完成，不改变结果");

        System.out.println("\n💡 使用场景:");
        System.out.println("   exceptionally:");
        System.out.println("     ✅ 提供降级方案");
        System.out.println("     ✅ 返回默认值");
        System.out.println("   handle:");
        System.out.println("     ✅ 统一处理成功和失败");
        System.out.println("     ✅ 转换结果或异常");
        System.out.println("   whenComplete:");
        System.out.println("     ✅ 记录日志");
        System.out.println("     ✅ 监控统计");

        System.out.println("\n⚠️  最佳实践:");
        System.out.println("   1. 总是处理异常");
        System.out.println("   2. 设置超时时间");
        System.out.println("   3. 提供降级方案");
        System.out.println("   4. 记录异常日志");
        System.out.println("   5. 考虑重试机制");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            异常处理演示                                     ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        try {
            // 演示1：exceptionally
            demoExceptionally();

            // 演示2：handle
            demoHandle();

            // 演示3：whenComplete
            demoWhenComplete();

            // 演示4：异常传播
            demoExceptionPropagation();

            // 演示5：超时控制
            demoTimeout();

            // 演示6：重试机制
            demoRetry();

            // 演示7：组合异常处理
            demoCombinedExceptionHandling();

            // 总结
            summarize();

        } finally {
            executor.shutdown();
        }

        System.out.println("\n===========================");
        System.out.println("核心要点：");
        System.out.println("1. 异常处理是异步编程的关键");
        System.out.println("2. 提供降级方案保证服务可用性");
        System.out.println("3. 超时控制避免无限期等待");
        System.out.println("4. 重试机制提高成功率");
        System.out.println("===========================");
    }
}
