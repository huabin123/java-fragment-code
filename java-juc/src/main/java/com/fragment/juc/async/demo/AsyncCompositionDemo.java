package com.fragment.juc.async.demo;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 异步组合演示
 * 
 * 演示内容：
 * 1. thenCompose - 串行组合
 * 2. thenCombine - 并行组合
 * 3. allOf - 等待所有完成
 * 4. anyOf - 等待任一完成
 * 5. 复杂组合场景
 * 
 * @author huabin
 */
public class AsyncCompositionDemo {

    private static final ExecutorService executor = Executors.newFixedThreadPool(10);

    /**
     * 演示1：thenCompose - 串行组合
     */
    public static void demoThenCompose() throws Exception {
        System.out.println("\n========== 演示1：thenCompose串行组合 ==========\n");

        System.out.println("场景：获取用户 -> 获取订单 -> 计算总金额\n");

        CompletableFuture<Double> totalFuture = CompletableFuture
            .supplyAsync(() -> {
                System.out.println("  [步骤1] 获取用户信息");
                sleep(500);
                return new User("user123", "张三");
            }, executor)
            .thenCompose(user -> {
                System.out.println("  [步骤2] 获取用户订单: " + user.name);
                return CompletableFuture.supplyAsync(() -> {
                    sleep(500);
                    return Arrays.asList(
                        new Order("order1", 100.0),
                        new Order("order2", 200.0)
                    );
                }, executor);
            })
            .thenCompose(orders -> {
                System.out.println("  [步骤3] 计算总金额，订单数: " + orders.size());
                return CompletableFuture.supplyAsync(() -> {
                    sleep(500);
                    return orders.stream()
                        .mapToDouble(o -> o.amount)
                        .sum();
                }, executor);
            });

        Double total = totalFuture.get();
        System.out.println("\n总金额: " + total);

        System.out.println("\n✅ thenCompose实现了串行依赖的异步流程");
    }

    /**
     * 演示2：thenCombine - 并行组合
     */
    public static void demoThenCombine() throws Exception {
        System.out.println("\n========== 演示2：thenCombine并行组合 ==========\n");

        System.out.println("场景：并行查询用户和订单，然后合并\n");

        long startTime = System.currentTimeMillis();

        CompletableFuture<User> userFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("  [并行1] 查询用户信息");
            sleep(1000);
            return new User("user123", "张三");
        }, executor);

        CompletableFuture<List<Order>> ordersFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("  [并行2] 查询订单信息");
            sleep(1000);
            return Arrays.asList(
                new Order("order1", 100.0),
                new Order("order2", 200.0)
            );
        }, executor);

        CompletableFuture<Dashboard> dashboardFuture = userFuture.thenCombine(
            ordersFuture,
            (user, orders) -> {
                System.out.println("  [合并] 创建Dashboard");
                return new Dashboard(user, orders);
            }
        );

        Dashboard dashboard = dashboardFuture.get();
        long duration = System.currentTimeMillis() - startTime;

        System.out.println("\nDashboard: " + dashboard);
        System.out.println("总耗时: " + duration + "ms");

        System.out.println("\n✅ thenCombine实现了并行执行，性能提升2倍");
    }

    /**
     * 演示3：allOf - 等待所有完成
     */
    public static void demoAllOf() throws Exception {
        System.out.println("\n========== 演示3：allOf等待所有完成 ==========\n");

        System.out.println("场景：批量查询5个用户\n");

        List<String> userIds = Arrays.asList("1", "2", "3", "4", "5");

        // 创建多个异步任务
        List<CompletableFuture<User>> futures = userIds.stream()
            .map(id -> CompletableFuture.supplyAsync(() -> {
                System.out.println("  查询用户: " + id);
                sleep(500);
                return new User(id, "用户" + id);
            }, executor))
            .collect(Collectors.toList());

        // 等待所有完成
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );

        allOf.thenRun(() -> {
            System.out.println("\n所有查询完成，收集结果:");
            List<User> users = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
            
            users.forEach(user -> System.out.println("  " + user));
        }).get();

        System.out.println("\n✅ allOf适合批量并行操作");
    }

    /**
     * 演示4：anyOf - 等待任一完成
     */
    public static void demoAnyOf() throws Exception {
        System.out.println("\n========== 演示4：anyOf等待任一完成 ==========\n");

        System.out.println("场景：查询多个数据源，返回最快的结果\n");

        CompletableFuture<String> source1 = CompletableFuture.supplyAsync(() -> {
            System.out.println("  数据源1开始查询");
            sleep(1000);
            System.out.println("  数据源1完成");
            return "数据源1的结果";
        }, executor);

        CompletableFuture<String> source2 = CompletableFuture.supplyAsync(() -> {
            System.out.println("  数据源2开始查询");
            sleep(500);
            System.out.println("  数据源2完成");
            return "数据源2的结果";
        }, executor);

        CompletableFuture<String> source3 = CompletableFuture.supplyAsync(() -> {
            System.out.println("  数据源3开始查询");
            sleep(1500);
            System.out.println("  数据源3完成");
            return "数据源3的结果";
        }, executor);

        CompletableFuture<Object> fastest = CompletableFuture.anyOf(source1, source2, source3);

        Object result = fastest.get();
        System.out.println("\n最快的结果: " + result);

        System.out.println("\n✅ anyOf适合竞速场景");
    }

    /**
     * 演示5：复杂组合 - 电商下单流程
     */
    public static void demoComplexComposition() throws Exception {
        System.out.println("\n========== 演示5：复杂组合 - 下单流程 ==========\n");

        String userId = "user123";
        String productId = "product456";

        CompletableFuture<String> orderFuture = CompletableFuture
            // 1. 验证用户
            .supplyAsync(() -> {
                System.out.println("  [步骤1] 验证用户: " + userId);
                sleep(300);
                return userId;
            }, executor)
            
            // 2. 并行检查库存和计算价格
            .thenCompose(uid -> {
                System.out.println("  [步骤2] 并行检查库存和计算价格");
                
                CompletableFuture<Boolean> stockCheck = CompletableFuture.supplyAsync(() -> {
                    System.out.println("    [2.1] 检查库存");
                    sleep(500);
                    return true;
                }, executor);
                
                CompletableFuture<Double> priceCalc = CompletableFuture.supplyAsync(() -> {
                    System.out.println("    [2.2] 计算价格");
                    sleep(500);
                    return 299.0;
                }, executor);
                
                return stockCheck.thenCombine(priceCalc, (hasStock, price) -> {
                    if (!hasStock) {
                        throw new RuntimeException("库存不足");
                    }
                    System.out.println("  [步骤2完成] 库存充足，价格: " + price);
                    return new OrderContext(uid, productId, price);
                });
            })
            
            // 3. 创建订单
            .thenCompose(context -> {
                System.out.println("  [步骤3] 创建订单");
                return CompletableFuture.supplyAsync(() -> {
                    sleep(300);
                    String orderId = "order_" + System.currentTimeMillis();
                    System.out.println("  [步骤3完成] 订单ID: " + orderId);
                    return orderId;
                }, executor);
            })
            
            // 4. 并行执行：扣减库存 + 发送通知
            .thenCompose(orderId -> {
                System.out.println("  [步骤4] 并行扣减库存和发送通知");
                
                CompletableFuture<Void> deductStock = CompletableFuture.runAsync(() -> {
                    System.out.println("    [4.1] 扣减库存");
                    sleep(300);
                }, executor);
                
                CompletableFuture<Void> sendNotification = CompletableFuture.runAsync(() -> {
                    System.out.println("    [4.2] 发送通知");
                    sleep(300);
                }, executor);
                
                return CompletableFuture.allOf(deductStock, sendNotification)
                    .thenApply(v -> orderId);
            })
            
            // 5. 异常处理
            .exceptionally(ex -> {
                System.err.println("  [错误] 订单创建失败: " + ex.getMessage());
                return "ERROR";
            });

        String orderId = orderFuture.get();
        System.out.println("\n订单结果: " + orderId);

        System.out.println("\n✅ 复杂组合可以构建完整的业务流程");
    }

    /**
     * 演示6：工具方法 - allOfList
     */
    public static void demoAllOfList() throws Exception {
        System.out.println("\n========== 演示6：工具方法allOfList ==========\n");

        List<String> userIds = Arrays.asList("1", "2", "3");

        // 创建异步任务
        List<CompletableFuture<User>> futures = userIds.stream()
            .map(id -> CompletableFuture.supplyAsync(() -> {
                sleep(500);
                return new User(id, "用户" + id);
            }, executor))
            .collect(Collectors.toList());

        // 使用工具方法
        CompletableFuture<List<User>> allUsers = allOfList(futures);

        List<User> users = allUsers.get();
        System.out.println("所有用户: " + users);

        System.out.println("\n✅ 封装工具方法简化了allOf的使用");
    }

    // ========== 工具方法 ==========

    /**
     * 等待所有完成并收集结果
     */
    public static <T> CompletableFuture<List<T>> allOfList(List<CompletableFuture<T>> futures) {
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        return allOf.thenApply(v -> 
            futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList())
        );
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ========== 辅助类 ==========

    static class User {
        String id;
        String name;

        User(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return "User{id='" + id + "', name='" + name + "'}";
        }
    }

    static class Order {
        String id;
        double amount;

        Order(String id, double amount) {
            this.id = id;
            this.amount = amount;
        }
    }

    static class Dashboard {
        User user;
        List<Order> orders;

        Dashboard(User user, List<Order> orders) {
            this.user = user;
            this.orders = orders;
        }

        @Override
        public String toString() {
            return "Dashboard{user=" + user + ", orders=" + orders.size() + "}";
        }
    }

    static class OrderContext {
        String userId;
        String productId;
        double price;

        OrderContext(String userId, String productId, double price) {
            this.userId = userId;
            this.productId = productId;
            this.price = price;
        }
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== 异步组合总结 ==========");

        System.out.println("\n✅ 核心方法:");
        System.out.println("   thenCompose  - 串行组合（前一个完成后执行下一个）");
        System.out.println("   thenCombine  - 并行组合（两个都完成后合并）");
        System.out.println("   allOf        - 等待所有完成");
        System.out.println("   anyOf        - 等待任一完成");

        System.out.println("\n💡 使用场景:");
        System.out.println("   thenCompose:");
        System.out.println("     ✅ 步骤之间有依赖");
        System.out.println("     ✅ 需要前一步的结果");
        System.out.println("   thenCombine:");
        System.out.println("     ✅ 两个独立任务");
        System.out.println("     ✅ 需要合并结果");
        System.out.println("   allOf:");
        System.out.println("     ✅ 批量并行任务");
        System.out.println("     ✅ 需要等待所有完成");
        System.out.println("   anyOf:");
        System.out.println("     ✅ 多个数据源");
        System.out.println("     ✅ 返回最快的结果");

        System.out.println("\n⚠️  注意事项:");
        System.out.println("   1. thenCompose用于扁平化嵌套的Future");
        System.out.println("   2. allOf返回Void，需要手动收集结果");
        System.out.println("   3. anyOf返回Object，需要类型转换");
        System.out.println("   4. 合理使用线程池，避免资源浪费");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            异步组合演示                                     ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        try {
            // 演示1：串行组合
            demoThenCompose();

            // 演示2：并行组合
            demoThenCombine();

            // 演示3：等待所有
            demoAllOf();

            // 演示4：等待任一
            demoAnyOf();

            // 演示5：复杂组合
            demoComplexComposition();

            // 演示6：工具方法
            demoAllOfList();

            // 总结
            summarize();

        } finally {
            executor.shutdown();
        }

        System.out.println("\n===========================");
        System.out.println("核心要点：");
        System.out.println("1. 组合操作是CompletableFuture的核心优势");
        System.out.println("2. 串行用thenCompose，并行用thenCombine");
        System.out.println("3. 批量用allOf，竞速用anyOf");
        System.out.println("4. 可以构建复杂的异步工作流");
        System.out.println("===========================");
    }
}
