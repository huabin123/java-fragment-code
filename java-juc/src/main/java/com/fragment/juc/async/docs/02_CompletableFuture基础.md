# 第二章：CompletableFuture基础 - 现代异步编程

> **学习目标**：掌握CompletableFuture的创建、转换和基本使用

---

## 一、CompletableFuture的诞生背景

### 1.1 Future的痛点回顾

```java
// 痛点1：只能阻塞获取结果
Future<String> future = executor.submit(() -> "result");
String result = future.get(); // 阻塞！

// 痛点2：无法链式调用
// 想要：获取用户 -> 获取订单 -> 计算总金额
Future<User> userFuture = getUserAsync();
User user = userFuture.get(); // 必须阻塞
Future<List<Order>> ordersFuture = getOrdersAsync(user.getId());
List<Order> orders = ordersFuture.get(); // 又要阻塞
double total = calculateTotal(orders);

// 痛点3：无法组合多个Future
Future<String> future1 = task1();
Future<String> future2 = task2();
// 想要：等待两个都完成，然后合并
// 无法做到！

// 痛点4：异常处理繁琐
try {
    String result = future.get();
} catch (ExecutionException e) {
    Throwable cause = e.getCause(); // 需要unwrap
    // 处理异常...
}
```

### 1.2 CompletableFuture的解决方案

```java
// 解决痛点1：非阻塞获取结果
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> "result");
future.thenAccept(result -> System.out.println(result)); // 非阻塞！

// 解决痛点2：链式调用
CompletableFuture.supplyAsync(() -> getUser())
    .thenCompose(user -> getOrdersAsync(user.getId()))
    .thenApply(orders -> calculateTotal(orders))
    .thenAccept(total -> System.out.println("总金额: " + total));

// 解决痛点3：组合多个Future
CompletableFuture<String> future1 = task1();
CompletableFuture<String> future2 = task2();
CompletableFuture.allOf(future1, future2)
    .thenRun(() -> System.out.println("都完成了"));

// 解决痛点4：优雅的异常处理
CompletableFuture.supplyAsync(() -> riskyOperation())
    .exceptionally(ex -> "默认值")
    .thenAccept(result -> System.out.println(result));
```

---

## 二、CompletableFuture的核心特性

### 2.1 实现了两个接口

```java
public class CompletableFuture<T> 
    implements Future<T>, CompletionStage<T> {
    // ...
}
```

**Future接口**：
- 提供了get()、cancel()等基本方法
- 兼容旧代码

**CompletionStage接口**：
- 提供了40+个方法
- 支持链式调用、组合、异常处理等

### 2.2 核心设计思想

```
CompletionStage（完成阶段）：
- 每个阶段都是一个异步计算
- 阶段之间可以链式连接
- 支持串行、并行、组合等多种模式

流式API：
CompletableFuture.supplyAsync(() -> "step1")
    .thenApply(result -> result + " -> step2")
    .thenApply(result -> result + " -> step3")
    .thenAccept(result -> System.out.println(result));

// 类似于Stream API的流式处理
```

---

## 三、创建CompletableFuture

### 3.1 方式1：runAsync（无返回值）

```java
/**
 * 异步执行Runnable任务
 */
public static CompletableFuture<Void> runAsync(Runnable runnable)
public static CompletableFuture<Void> runAsync(Runnable runnable, Executor executor)
```

**使用示例**：

```java
// 使用默认线程池（ForkJoinPool.commonPool()）
CompletableFuture<Void> future1 = CompletableFuture.runAsync(() -> {
    System.out.println("执行异步任务");
    try {
        Thread.sleep(1000);
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
});

// 使用自定义线程池
ExecutorService executor = Executors.newFixedThreadPool(10);
CompletableFuture<Void> future2 = CompletableFuture.runAsync(() -> {
    System.out.println("使用自定义线程池");
}, executor);
```

**何时使用？**

```
适用场景：
✅ 不需要返回值的异步任务
✅ 异步日志记录
✅ 异步发送通知
✅ 异步清理资源

示例：
CompletableFuture.runAsync(() -> {
    logger.info("用户登录: " + userId);
});
```

### 3.2 方式2：supplyAsync（有返回值）

```java
/**
 * 异步执行Supplier任务
 */
public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier)
public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier, Executor executor)
```

**使用示例**：

```java
// 异步获取数据
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    // 模拟耗时操作
    try {
        Thread.sleep(1000);
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
    return "Hello CompletableFuture";
});

// 获取结果
String result = future.get(); // 阻塞
// 或者
future.thenAccept(r -> System.out.println(r)); // 非阻塞
```

**何时使用？**

```
适用场景：
✅ 需要返回值的异步任务
✅ 异步HTTP请求
✅ 异步数据库查询
✅ 异步文件读取

示例：
CompletableFuture<User> userFuture = CompletableFuture.supplyAsync(() -> {
    return userService.getUser(userId);
});
```

### 3.3 方式3：completedFuture（已完成）

```java
/**
 * 创建一个已完成的CompletableFuture
 */
public static <U> CompletableFuture<U> completedFuture(U value)
```

**使用示例**：

```java
// 创建已完成的Future
CompletableFuture<String> future = CompletableFuture.completedFuture("Immediate Result");

// 立即可用
String result = future.get(); // 不会阻塞
System.out.println(result); // Immediate Result
```

**何时使用？**

```
适用场景：
✅ 缓存命中，直接返回
✅ 参数校验失败，立即返回
✅ 测试代码

示例：
public CompletableFuture<User> getUser(String userId) {
    // 先查缓存
    User cached = cache.get(userId);
    if (cached != null) {
        return CompletableFuture.completedFuture(cached);
    }
    
    // 缓存未命中，异步查询
    return CompletableFuture.supplyAsync(() -> {
        return userService.getUser(userId);
    });
}
```

### 3.4 方式4：手动完成

```java
// 创建一个未完成的Future
CompletableFuture<String> future = new CompletableFuture<>();

// 在另一个线程中完成
new Thread(() -> {
    try {
        Thread.sleep(1000);
        future.complete("Manual Result"); // 手动完成
    } catch (InterruptedException e) {
        future.completeExceptionally(e); // 异常完成
    }
}).start();

// 等待结果
String result = future.get();
```

**何时使用？**

```
适用场景：
✅ 集成回调式API
✅ 自定义异步逻辑
✅ 桥接其他异步框架

示例：
public CompletableFuture<String> callbackToFuture() {
    CompletableFuture<String> future = new CompletableFuture<>();
    
    // 调用回调式API
    asyncApi.request(new Callback() {
        @Override
        public void onSuccess(String result) {
            future.complete(result);
        }
        
        @Override
        public void onFailure(Exception e) {
            future.completeExceptionally(e);
        }
    });
    
    return future;
}
```

---

## 四、转换操作

### 4.1 thenApply - 转换结果

```java
/**
 * 对结果进行转换
 * 
 * @param fn 转换函数
 * @return 新的CompletableFuture
 */
public <U> CompletableFuture<U> thenApply(Function<? super T,? extends U> fn)
public <U> CompletableFuture<U> thenApplyAsync(Function<? super T,? extends U> fn)
public <U> CompletableFuture<U> thenApplyAsync(Function<? super T,? extends U> fn, Executor executor)
```

**使用示例**：

```java
CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
    return 10;
}).thenApply(result -> {
    return result * 2; // 转换：10 -> 20
}).thenApply(result -> {
    return result + 5; // 再转换：20 -> 25
});

System.out.println(future.get()); // 25
```

**类比Stream API**：

```java
// Stream的map
List<Integer> list = Arrays.asList(1, 2, 3);
List<Integer> result = list.stream()
    .map(x -> x * 2)
    .map(x -> x + 1)
    .collect(Collectors.toList());

// CompletableFuture的thenApply
CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> 1)
    .thenApply(x -> x * 2)
    .thenApply(x -> x + 1);
```

### 4.2 thenAccept - 消费结果

```java
/**
 * 消费结果，无返回值
 */
public CompletableFuture<Void> thenAccept(Consumer<? super T> action)
public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action)
```

**使用示例**：

```java
CompletableFuture.supplyAsync(() -> {
    return "Hello";
}).thenAccept(result -> {
    System.out.println("结果: " + result); // 消费结果
    // 无返回值
});
```

**vs thenApply**：

```java
// thenApply：有返回值
CompletableFuture<String> future1 = CompletableFuture.supplyAsync(() -> "input")
    .thenApply(s -> s.toUpperCase()); // 返回新值

// thenAccept：无返回值
CompletableFuture<Void> future2 = CompletableFuture.supplyAsync(() -> "input")
    .thenAccept(s -> System.out.println(s)); // 只消费，不返回
```

### 4.3 thenRun - 执行后续操作

```java
/**
 * 执行后续操作，不关心前一步的结果
 */
public CompletableFuture<Void> thenRun(Runnable action)
public CompletableFuture<Void> thenRunAsync(Runnable action)
```

**使用示例**：

```java
CompletableFuture.supplyAsync(() -> {
    return "任务完成";
}).thenRun(() -> {
    System.out.println("执行后续操作"); // 不关心前一步的结果
});
```

**三者对比**：

```java
// thenApply：转换结果
future.thenApply(result -> result.toUpperCase())

// thenAccept：消费结果
future.thenAccept(result -> System.out.println(result))

// thenRun：不关心结果
future.thenRun(() -> System.out.println("完成"))
```

---

## 五、同步 vs 异步方法

### 5.1 方法命名规则

```
thenApply()      - 同步执行（在完成线程中执行）
thenApplyAsync() - 异步执行（在线程池中执行）
thenApplyAsync(executor) - 异步执行（在指定线程池中执行）
```

### 5.2 执行线程的区别

```java
ExecutorService executor = Executors.newFixedThreadPool(5);

CompletableFuture.supplyAsync(() -> {
    System.out.println("supplyAsync: " + Thread.currentThread().getName());
    return "result";
}, executor)
.thenApply(result -> {
    // 在完成supplyAsync的线程中执行
    System.out.println("thenApply: " + Thread.currentThread().getName());
    return result.toUpperCase();
})
.thenApplyAsync(result -> {
    // 在ForkJoinPool中执行
    System.out.println("thenApplyAsync: " + Thread.currentThread().getName());
    return result + "!";
});

// 输出示例：
// supplyAsync: pool-1-thread-1
// thenApply: pool-1-thread-1
// thenApplyAsync: ForkJoinPool.commonPool-worker-1
```

### 5.3 何时使用同步/异步？

```java
// 使用同步（thenApply）：
✅ 计算量小，不会阻塞
✅ 想要减少线程切换开销

CompletableFuture.supplyAsync(() -> getUser())
    .thenApply(user -> user.getName()) // 简单转换，用同步

// 使用异步（thenApplyAsync）：
✅ 计算量大，可能阻塞
✅ 想要并行执行

CompletableFuture.supplyAsync(() -> getUser())
    .thenApplyAsync(user -> {
        // 复杂计算，用异步
        return expensiveCalculation(user);
    })
```

---

## 六、默认线程池

### 6.1 ForkJoinPool.commonPool()

```java
// 不指定线程池时，使用默认线程池
CompletableFuture.supplyAsync(() -> "result");
// 等价于
CompletableFuture.supplyAsync(() -> "result", ForkJoinPool.commonPool());
```

**默认线程池的特点**：

```
线程数：
- CPU核心数 - 1
- 最少1个线程

优点：
✅ 自动管理，无需手动创建
✅ 适合CPU密集型任务

缺点：
❌ 所有CompletableFuture共享
❌ 不适合IO密集型任务
❌ 无法隔离不同业务
```

### 6.2 自定义线程池

```java
// 创建自定义线程池
ExecutorService executor = new ThreadPoolExecutor(
    10,                      // 核心线程数
    20,                      // 最大线程数
    60L,                     // 空闲线程存活时间
    TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(100),
    new ThreadFactoryBuilder()
        .setNameFormat("async-pool-%d")
        .build()
);

// 使用自定义线程池
CompletableFuture.supplyAsync(() -> {
    return "result";
}, executor);
```

**何时使用自定义线程池？**

```
推荐场景：
✅ IO密集型任务（数据库、HTTP）
✅ 需要隔离不同业务
✅ 需要监控线程池状态
✅ 生产环境

示例：
// 数据库查询线程池
ExecutorService dbExecutor = Executors.newFixedThreadPool(20);

// HTTP请求线程池
ExecutorService httpExecutor = Executors.newFixedThreadPool(50);

CompletableFuture.supplyAsync(() -> queryDatabase(), dbExecutor);
CompletableFuture.supplyAsync(() -> httpRequest(), httpExecutor);
```

---

## 七、实际应用示例

### 7.1 异步HTTP请求

```java
public CompletableFuture<String> fetchUrl(String url) {
    return CompletableFuture.supplyAsync(() -> {
        // 模拟HTTP请求
        try {
            Thread.sleep(1000);
            return "Response from " + url;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    });
}

// 使用
fetchUrl("http://api.example.com/user")
    .thenApply(response -> parseJson(response))
    .thenAccept(user -> System.out.println("User: " + user));
```

### 7.2 异步数据处理

```java
public CompletableFuture<List<String>> processData(List<Integer> ids) {
    return CompletableFuture.supplyAsync(() -> {
        return ids.stream()
            .map(id -> "Data-" + id)
            .collect(Collectors.toList());
    }).thenApply(dataList -> {
        // 进一步处理
        return dataList.stream()
            .map(String::toUpperCase)
            .collect(Collectors.toList());
    });
}
```

### 7.3 缓存 + 异步查询

```java
public CompletableFuture<User> getUser(String userId) {
    // 先查缓存
    User cached = cache.get(userId);
    if (cached != null) {
        return CompletableFuture.completedFuture(cached);
    }
    
    // 缓存未命中，异步查询
    return CompletableFuture.supplyAsync(() -> {
        User user = userService.getUser(userId);
        cache.put(userId, user); // 更新缓存
        return user;
    });
}
```

---

## 八、总结

### 8.1 CompletableFuture的优势

1. **非阻塞**：通过回调获取结果，不阻塞线程
2. **链式调用**：流式API，代码简洁
3. **组合能力**：支持多个Future的组合
4. **异常处理**：优雅的异常处理机制

### 8.2 创建方法总结

| 方法 | 返回值 | 使用场景 |
|------|--------|---------|
| runAsync | Void | 无返回值的异步任务 |
| supplyAsync | T | 有返回值的异步任务 |
| completedFuture | T | 已有结果，立即返回 |
| new CompletableFuture() | T | 手动控制完成 |

### 8.3 转换方法总结

| 方法 | 输入 | 输出 | 说明 |
|------|------|------|------|
| thenApply | T | U | 转换结果 |
| thenAccept | T | Void | 消费结果 |
| thenRun | - | Void | 执行后续操作 |

### 8.4 思考题

1. **thenApply和thenApplyAsync有什么区别？**
2. **何时使用自定义线程池？**
3. **completedFuture的使用场景是什么？**
4. **如何选择同步还是异步方法？**

---

**下一章预告**：我们将学习CompletableFuture的组合操作，包括串行组合、并行组合、等待所有/任一完成等高级特性。

---

**参考资料**：
- 《Java 8实战》第11章
- JDK源码：`java.util.concurrent.CompletableFuture`
- [CompletableFuture API文档](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html)
