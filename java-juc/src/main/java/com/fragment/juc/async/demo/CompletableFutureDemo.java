package com.fragment.juc.async.demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * CompletableFuture演示
 *
 * 演示内容：
 * 1. 创建CompletableFuture
 * 2. 转换操作（thenApply、thenCompose）
 * 3. 组合操作（thenCombine、allOf、anyOf）
 * 4. 异常处理
 * 5. 实际应用场景
 *
 * @author huabin
 */
public class CompletableFutureDemo {

    /**
     * 演示1：创建CompletableFuture
     */
    public static void demoCreation() throws Exception {
        System.out.println("\n========== 演示1：创建CompletableFuture ==========\n");

        // 方式1：runAsync - 无返回值
        CompletableFuture<Void> future1 = CompletableFuture.runAsync(() -> {
            System.out.println("[runAsync] 执行任务，无返回值");
            sleep(1000);
        });
        future1.get();

        // 方式2：supplyAsync - 有返回值
        CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> {
            System.out.println("[supplyAsync] 执行任务，有返回值");
            sleep(1000);
            return "Result";
        });
        System.out.println("结果: " + future2.get());

        // 方式3：completedFuture - 已完成的Future
        CompletableFuture<String> future3 = CompletableFuture.completedFuture("Immediate");
        System.out.println("立即结果: " + future3.get());

        System.out.println("\n✅ 三种创建方式适用不同场景");
    }

    /**
     * 演示2：转换操作
     */
    public static void demoTransformation() throws Exception {
        System.out.println("\n========== 演示2：转换操作 ==========\n");

        // thenApply - 转换结果
        System.out.println("1. thenApply():");
        CompletableFuture<Integer> future1 = CompletableFuture.supplyAsync(() -> {
            System.out.println("  计算: 10");
            return 10;
        }).thenApply(result -> {
            System.out.println("  转换: " + result + " * 2");
            return result * 2;
        }).thenApply(result -> {
            System.out.println("  再转换: " + result + " + 5");
            return result + 5;
        });
        System.out.println("  最终结果: " + future1.get());

        // thenAccept - 消费结果
        System.out.println("\n2. thenAccept():");
        CompletableFuture.supplyAsync(() -> {
            return "Hello";
        }).thenAccept(result -> {
            System.out.println("  消费结果: " + result);
        }).get();

        // thenRun - 执行后续操作
        System.out.println("\n3. thenRun():");
        CompletableFuture.supplyAsync(() -> {
            return "Task";
        }).thenRun(() -> {
            System.out.println("  执行后续操作");
        }).get();

        System.out.println("\n✅ 转换操作可以链式调用");
    }

    /**
     * 演示3：组合操作
     */
    public static void demoCombination() throws Exception {
        System.out.println("\n========== 演示3：组合操作 ==========\n");

        // thenCompose - 串行组合
        System.out.println("1. thenCompose() - 串行:");
        CompletableFuture<String> future1 = CompletableFuture.supplyAsync(() -> {
            System.out.println("  任务1执行");
            sleep(1000);
            return "Result1";
        }).thenCompose(result -> {
            System.out.println("  任务2执行，依赖: " + result);
            return CompletableFuture.supplyAsync(() -> {
                sleep(1000);
                return result + " + Result2";
            });
        });
        System.out.println("  结果: " + future1.get());

        // thenCombine - 并行组合
        System.out.println("\n2. thenCombine() - 并行:");
        CompletableFuture<Integer> futureA = CompletableFuture.supplyAsync(() -> {
            System.out.println("  任务A执行");
            sleep(1000);
            return 10;
        });

        CompletableFuture<Integer> futureB = CompletableFuture.supplyAsync(() -> {
            System.out.println("  任务B执行");
            sleep(1000);
            return 20;
        });

        CompletableFuture<Integer> combined = futureA.thenCombine(futureB, (a, b) -> {
            System.out.println("  合并结果: " + a + " + " + b);
            return a + b;
        });
        System.out.println("  结果: " + combined.get());

        // allOf - 等待所有完成
        System.out.println("\n3. allOf() - 等待所有:");
        CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> {
            sleep(1000);
            System.out.println("  任务1完成");
            return "1";
        });
        CompletableFuture<String> f2 = CompletableFuture.supplyAsync(() -> {
            sleep(1500);
            System.out.println("  任务2完成");
            return "2";
        });
        CompletableFuture<String> f3 = CompletableFuture.supplyAsync(() -> {
            sleep(500);
            System.out.println("  任务3完成");
            return "3";
        });

        CompletableFuture<Void> allOf = CompletableFuture.allOf(f1, f2, f3);
        allOf.get();
        System.out.println("  所有任务完成");

        // anyOf - 等待任一完成
        System.out.println("\n4. anyOf() - 等待任一:");
        CompletableFuture<String> g1 = CompletableFuture.supplyAsync(() -> {
            sleep(1000);
            return "慢任务";
        });
        CompletableFuture<String> g2 = CompletableFuture.supplyAsync(() -> {
            sleep(500);
            return "快任务";
        });

        CompletableFuture<Object> anyOf = CompletableFuture.anyOf(g1, g2);
        System.out.println("  最快完成: " + anyOf.get());

        System.out.println("\n✅ 组合操作支持复杂的异步编排");
    }

    /**
     * 演示4：异常处理
     */
    public static void demoExceptionHandling() throws Exception {
        System.out.println("\n========== 演示4：异常处理 ==========\n");

        // exceptionally - 处理异常
        System.out.println("1. exceptionally():");
        CompletableFuture<String> future1 = CompletableFuture.supplyAsync(() -> {
            if (Math.random() > 0.5) {
                throw new RuntimeException("随机异常");
            }
            return "Success";
        }).exceptionally(ex -> {
            System.out.println("  捕获异常: " + ex.getMessage());
            return "Default";
        });
        System.out.println("  结果: " + future1.get());

        // handle - 处理结果或异常
        System.out.println("\n2. handle():");
        CompletableFuture<Object> future2 = CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("测试异常");
        }).handle((result, ex) -> {
            if (ex != null) {
                System.out.println("  处理异常: " + ex.getMessage());
                return "Error Handled";
            }
            return result;
        });
        System.out.println("  结果: " + future2.get());

        // whenComplete - 完成时执行
        System.out.println("\n3. whenComplete():");
        CompletableFuture<String> future3 = CompletableFuture.supplyAsync(() -> {
            return "Result";
        }).whenComplete((result, ex) -> {
            if (ex != null) {
                System.out.println("  异常: " + ex.getMessage());
            } else {
                System.out.println("  成功: " + result);
            }
        });
        future3.get();

        System.out.println("\n✅ 异常处理保证了程序的健壮性");
    }

    /**
     * 演示5：实际应用 - 并行查询
     */
    public static void demoParallelQuery() throws Exception {
        System.out.println("\n========== 演示5：并行查询 ==========\n");

        // 模拟查询用户、订单、商品信息
        CompletableFuture<String> userFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("[查询] 用户信息...");
            sleep(1000);
            return "User{id=1, name=张三}";
        });

        CompletableFuture<String> orderFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("[查询] 订单信息...");
            sleep(1500);
            return "Order{id=100, amount=500}";
        });

        CompletableFuture<String> productFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("[查询] 商品信息...");
            sleep(800);
            return "Product{id=200, name=手机}";
        });

        // 等待所有查询完成
        CompletableFuture<Void> allQueries = CompletableFuture.allOf(
            userFuture, orderFuture, productFuture
        );

        long startTime = System.currentTimeMillis();
        allQueries.get();
        long endTime = System.currentTimeMillis();

        System.out.println("\n查询结果:");
        System.out.println("  " + userFuture.get());
        System.out.println("  " + orderFuture.get());
        System.out.println("  " + productFuture.get());
        System.out.println("\n总耗时: " + (endTime - startTime) + "ms");
        System.out.println("✅ 并行查询大幅提升性能");
    }

    /**
     * 演示6：实际应用 - 异步工作流
     */
    public static void demoAsyncWorkflow() throws Exception {
        System.out.println("\n========== 演示6：异步工作流 ==========\n");

        CompletableFuture<String> workflow = CompletableFuture
            .supplyAsync(() -> {
                System.out.println("[步骤1] 验证用户");
                sleep(500);
                return "user123";
            })
            .thenApply(userId -> {
                System.out.println("[步骤2] 查询用户信息: " + userId);
                sleep(500);
                return "UserInfo{" + userId + "}";
            })
            .thenCompose(userInfo -> {
                System.out.println("[步骤3] 查询订单: " + userInfo);
                return CompletableFuture.supplyAsync(() -> {
                    sleep(500);
                    return "Orders[...]";
                });
            })
            .thenApply(orders -> {
                System.out.println("[步骤4] 计算总金额: " + orders);
                sleep(500);
                return "Total: 1000元";
            })
            .exceptionally(ex -> {
                System.out.println("[错误] " + ex.getMessage());
                return "Error";
            });

        System.out.println("\n最终结果: " + workflow.get());
        System.out.println("✅ 异步工作流简化了复杂流程");
    }

    /**
     * 演示7：超时控制
     */
    public static void demoTimeout() {
        System.out.println("\n========== 演示7：超时控制 ==========\n");

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            System.out.println("开始执行长时间任务...");
            sleep(5000);
            return "Result";
        });

        try {
            // JDK9+支持orTimeout
            // String result = future.orTimeout(2, TimeUnit.SECONDS).get();

            // JDK8兼容写法
            String result = future.get(2, TimeUnit.SECONDS);
            System.out.println("结果: " + result);
        } catch (Exception e) {
            System.out.println("任务超时: " + e.getClass().getSimpleName());
        }

        System.out.println("\n✅ 超时控制避免了无限期等待");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== CompletableFuture总结 ==========");

        System.out.println("\n✅ 核心特性:");
        System.out.println("   1. 异步执行：支持异步任务");
        System.out.println("   2. 链式调用：支持流式API");
        System.out.println("   3. 组合操作：支持复杂编排");
        System.out.println("   4. 异常处理：完善的异常处理机制");

        System.out.println("\n📊 核心方法:");
        System.out.println("   创建:");
        System.out.println("     runAsync()        - 异步执行（无返回值）");
        System.out.println("     supplyAsync()     - 异步执行（有返回值）");
        System.out.println("   转换:");
        System.out.println("     thenApply()       - 转换结果");
        System.out.println("     thenAccept()      - 消费结果");
        System.out.println("     thenRun()         - 执行后续操作");
        System.out.println("   组合:");
        System.out.println("     thenCompose()     - 串行组合");
        System.out.println("     thenCombine()     - 并行组合");
        System.out.println("     allOf()           - 等待所有");
        System.out.println("     anyOf()           - 等待任一");
        System.out.println("   异常:");
        System.out.println("     exceptionally()   - 处理异常");
        System.out.println("     handle()          - 处理结果或异常");
        System.out.println("     whenComplete()    - 完成时执行");

        System.out.println("\n💡 适用场景:");
        System.out.println("   ✅ 异步HTTP请求");
        System.out.println("   ✅ 并行数据查询");
        System.out.println("   ✅ 异步工作流");
        System.out.println("   ✅ 服务编排");

        System.out.println("\n⚠️  注意事项:");
        System.out.println("   1. 默认使用ForkJoinPool.commonPool()");
        System.out.println("   2. 可以指定自定义线程池");
        System.out.println("   3. 注意异常处理");
        System.out.println("   4. 避免阻塞操作");

        System.out.println("\n🔄 vs Future:");
        System.out.println("   CompletableFuture:");
        System.out.println("     - 支持链式调用");
        System.out.println("     - 支持组合操作");
        System.out.println("     - 完善的异常处理");
        System.out.println("   Future:");
        System.out.println("     - 只能get()阻塞等待");
        System.out.println("     - 不支持组合");
        System.out.println("     - 异常处理不便");

        System.out.println("===========================");
    }

    // 工具方法
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            CompletableFuture演示                            ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：创建
        demoCreation();

        // 演示2：转换
        demoTransformation();

        // 演示3：组合
        demoCombination();

        // 演示4：异常处理
        demoExceptionHandling();

        // 演示5：并行查询
        demoParallelQuery();

        // 演示6：异步工作流
        demoAsyncWorkflow();

        // 演示7：超时控制
        demoTimeout();

        // 总结
        summarize();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. CompletableFuture是现代异步编程的核心");
        System.out.println("2. 支持链式调用和复杂编排");
        System.out.println("3. 完善的异常处理机制");
        System.out.println("4. 适合构建高性能异步应用");
        System.out.println("===========================");
    }
}
