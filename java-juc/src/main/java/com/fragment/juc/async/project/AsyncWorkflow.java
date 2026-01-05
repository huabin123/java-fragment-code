package com.fragment.juc.async.project;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * 异步工作流引擎
 * 
 * 功能：
 * 1. 定义工作流步骤
 * 2. 串行/并行执行
 * 3. 条件分支
 * 4. 异常处理
 * 5. 执行监控
 * 
 * @author huabin
 */
public class AsyncWorkflow<T> {

    private final ExecutorService executor;
    private final List<WorkflowStep<T>> steps;
    private final Map<String, Object> context;

    public AsyncWorkflow() {
        this(Executors.newFixedThreadPool(10));
    }

    public AsyncWorkflow(ExecutorService executor) {
        this.executor = executor;
        this.steps = new ArrayList<>();
        this.context = new ConcurrentHashMap<>();
    }

    /**
     * 添加步骤
     */
    public AsyncWorkflow<T> addStep(String name, Function<T, T> action) {
        steps.add(new WorkflowStep<>(name, action, null));
        return this;
    }

    /**
     * 添加条件步骤
     */
    public AsyncWorkflow<T> addConditionalStep(
            String name,
            Function<T, Boolean> condition,
            Function<T, T> action) {
        steps.add(new WorkflowStep<>(name, action, condition));
        return this;
    }

    /**
     * 执行工作流
     */
    public CompletableFuture<WorkflowResult<T>> execute(T initialData) {
        System.out.println("========== 工作流开始执行 ==========\n");
        long startTime = System.currentTimeMillis();

        CompletableFuture<T> result = CompletableFuture.completedFuture(initialData);

        for (WorkflowStep<T> step : steps) {
            result = result.thenComposeAsync(data -> executeStep(step, data), executor);
        }

        return result.handle((finalData, ex) -> {
            long duration = System.currentTimeMillis() - startTime;
            System.out.println("\n========== 工作流执行完成 ==========");
            System.out.println("总耗时: " + duration + "ms\n");

            if (ex != null) {
                return new WorkflowResult<>(null, false, ex.getMessage(), duration);
            } else {
                return new WorkflowResult<>(finalData, true, "成功", duration);
            }
        });
    }

    /**
     * 执行单个步骤
     */
    private CompletableFuture<T> executeStep(WorkflowStep<T> step, T data) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("[步骤] " + step.name);

            // 检查条件
            if (step.condition != null && !step.condition.apply(data)) {
                System.out.println("  条件不满足，跳过");
                return data;
            }

            try {
                long stepStart = System.currentTimeMillis();
                T result = step.action.apply(data);
                long stepDuration = System.currentTimeMillis() - stepStart;
                System.out.println("  完成，耗时: " + stepDuration + "ms");
                return result;
            } catch (Exception e) {
                System.err.println("  失败: " + e.getMessage());
                throw e;
            }
        }, executor);
    }

    /**
     * 并行执行多个步骤
     */
    public static <T> CompletableFuture<T> parallel(
            T data,
            List<Function<T, T>> actions,
            ExecutorService executor) {

        List<CompletableFuture<T>> futures = actions.stream()
            .map(action -> CompletableFuture.supplyAsync(() -> action.apply(data), executor))
            .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                // 返回最后一个结果（或者可以合并所有结果）
                return futures.get(futures.size() - 1).join();
            });
    }

    /**
     * 设置上下文变量
     */
    public void setContext(String key, Object value) {
        context.put(key, value);
    }

    /**
     * 获取上下文变量
     */
    public Object getContext(String key) {
        return context.get(key);
    }

    // ========== 辅助类 ==========

    private static class WorkflowStep<T> {
        final String name;
        final Function<T, T> action;
        final Function<T, Boolean> condition;

        WorkflowStep(String name, Function<T, T> action, Function<T, Boolean> condition) {
            this.name = name;
            this.action = action;
            this.condition = condition;
        }
    }

    public static class WorkflowResult<T> {
        private final T data;
        private final boolean success;
        private final String message;
        private final long duration;

        public WorkflowResult(T data, boolean success, String message, long duration) {
            this.data = data;
            this.success = success;
            this.message = message;
            this.duration = duration;
        }

        public T getData() {
            return data;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public long getDuration() {
            return duration;
        }

        @Override
        public String toString() {
            return "WorkflowResult{success=" + success + 
                   ", message='" + message + "'" +
                   ", duration=" + duration + "ms}";
        }
    }

    // ========== 预定义工作流 ==========

    /**
     * 订单处理工作流
     */
    public static class OrderWorkflow {
        public static AsyncWorkflow<Order> create() {
            AsyncWorkflow<Order> workflow = new AsyncWorkflow<>();

            return workflow
                .addStep("验证订单", order -> {
                    sleep(300);
                    if (order.amount <= 0) {
                        throw new RuntimeException("订单金额无效");
                    }
                    order.status = "已验证";
                    return order;
                })
                .addStep("检查库存", order -> {
                    sleep(500);
                    order.status = "库存充足";
                    return order;
                })
                .addConditionalStep(
                    "应用优惠券",
                    order -> order.amount > 100,
                    order -> {
                        sleep(200);
                        order.amount *= 0.9; // 9折
                        order.status = "已优惠";
                        return order;
                    }
                )
                .addStep("创建订单", order -> {
                    sleep(400);
                    order.orderId = "ORD_" + System.currentTimeMillis();
                    order.status = "已创建";
                    return order;
                })
                .addStep("扣减库存", order -> {
                    sleep(300);
                    order.status = "库存已扣";
                    return order;
                })
                .addStep("发送通知", order -> {
                    sleep(200);
                    order.status = "已通知";
                    return order;
                });
        }
    }

    /**
     * 用户注册工作流
     */
    public static class UserRegistrationWorkflow {
        public static AsyncWorkflow<User> create() {
            AsyncWorkflow<User> workflow = new AsyncWorkflow<>();

            return workflow
                .addStep("验证用户名", user -> {
                    sleep(200);
                    if (user.username == null || user.username.isEmpty()) {
                        throw new RuntimeException("用户名不能为空");
                    }
                    return user;
                })
                .addStep("检查用户名重复", user -> {
                    sleep(300);
                    // 模拟数据库查询
                    return user;
                })
                .addStep("加密密码", user -> {
                    sleep(100);
                    user.password = "encrypted_" + user.password;
                    return user;
                })
                .addStep("创建用户", user -> {
                    sleep(400);
                    user.userId = "USER_" + System.currentTimeMillis();
                    return user;
                })
                .addStep("发送欢迎邮件", user -> {
                    sleep(200);
                    System.out.println("  发送欢迎邮件到: " + user.email);
                    return user;
                });
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ========== 数据类 ==========

    public static class Order {
        String orderId;
        double amount;
        String status;

        public Order(double amount) {
            this.amount = amount;
            this.status = "初始";
        }

        @Override
        public String toString() {
            return "Order{id='" + orderId + "', amount=" + amount + ", status='" + status + "'}";
        }
    }

    public static class User {
        String userId;
        String username;
        String password;
        String email;

        public User(String username, String password, String email) {
            this.username = username;
            this.password = password;
            this.email = email;
        }

        @Override
        public String toString() {
            return "User{id='" + userId + "', username='" + username + "', email='" + email + "'}";
        }
    }

    // ========== 测试 ==========

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            异步工作流引擎演示                               ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 测试1：订单处理工作流
        System.out.println("\n========== 测试1：订单处理工作流 ==========\n");
        AsyncWorkflow<Order> orderWorkflow = OrderWorkflow.create();
        Order order = new Order(150.0);

        WorkflowResult<Order> orderResult = orderWorkflow.execute(order).get();
        System.out.println("结果: " + orderResult);
        System.out.println("订单: " + orderResult.getData());

        // 测试2：用户注册工作流
        System.out.println("\n========== 测试2：用户注册工作流 ==========\n");
        AsyncWorkflow<User> userWorkflow = UserRegistrationWorkflow.create();
        User user = new User("zhangsan", "password123", "zhangsan@example.com");

        WorkflowResult<User> userResult = userWorkflow.execute(user).get();
        System.out.println("结果: " + userResult);
        System.out.println("用户: " + userResult.getData());

        // 测试3：自定义工作流
        System.out.println("\n========== 测试3：自定义工作流 ==========\n");
        AsyncWorkflow<Integer> customWorkflow = new AsyncWorkflow<>();
        customWorkflow
            .addStep("步骤1：加10", n -> {
                sleep(200);
                return n + 10;
            })
            .addStep("步骤2：乘2", n -> {
                sleep(200);
                return n * 2;
            })
            .addConditionalStep(
                "步骤3：如果大于50则减20",
                n -> n > 50,
                n -> {
                    sleep(200);
                    return n - 20;
                }
            )
            .addStep("步骤4：除以2", n -> {
                sleep(200);
                return n / 2;
            });

        WorkflowResult<Integer> customResult = customWorkflow.execute(20).get();
        System.out.println("结果: " + customResult);
        System.out.println("最终值: " + customResult.getData());

        // 测试4：异常处理
        System.out.println("\n========== 测试4：异常处理 ==========\n");
        AsyncWorkflow<Order> failWorkflow = OrderWorkflow.create();
        Order invalidOrder = new Order(-100.0); // 无效金额

        WorkflowResult<Order> failResult = failWorkflow.execute(invalidOrder).get();
        System.out.println("结果: " + failResult);

        System.out.println("\n===========================");
        System.out.println("✅ 异步工作流引擎演示完成");
        System.out.println("===========================");

        System.exit(0);
    }
}
