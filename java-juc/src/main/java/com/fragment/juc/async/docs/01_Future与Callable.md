# 第一章：Future与Callable - 异步编程的起点

> **学习目标**：理解为什么需要异步编程，掌握Future和Callable的使用

---

## 一、为什么需要异步编程？

### 1.1 同步编程的困境

#### 问题1：阻塞等待浪费资源

```java
// 同步调用：主线程被阻塞
public String getUserInfo(String userId) {
    String user = httpClient.get("/user/" + userId);     // 耗时100ms
    String orders = httpClient.get("/orders/" + userId); // 耗时100ms
    String products = httpClient.get("/products/" + userId); // 耗时100ms
    
    return merge(user, orders, products);
}

// 总耗时：300ms
// 问题：三个请求可以并行，但串行执行浪费了时间
```

**存在的问题**：
- ❌ 串行执行，总耗时是各步骤之和
- ❌ 主线程阻塞，无法处理其他请求
- ❌ 资源利用率低

#### 问题2：无法充分利用多核CPU

```java
// 处理大量数据
public List<Result> processData(List<Data> dataList) {
    List<Result> results = new ArrayList<>();
    
    for (Data data : dataList) {
        Result result = process(data); // 耗时操作
        results.add(result);
    }
    
    return results;
}

// 问题：
// - 单线程处理，只用了一个CPU核心
// - 多核CPU的优势无法发挥
```

#### 问题3：用户体验差

```java
// Web应用场景
@GetMapping("/dashboard")
public Dashboard getDashboard(String userId) {
    // 串行查询多个数据源
    UserInfo user = userService.getUser(userId);           // 100ms
    List<Order> orders = orderService.getOrders(userId);   // 200ms
    Statistics stats = statsService.getStats(userId);      // 150ms
    
    return new Dashboard(user, orders, stats);
}

// 总响应时间：450ms
// 用户感觉：慢！
```

---

### 1.2 异步编程的解决方案

```java
// 异步并行执行
public CompletableFuture<Dashboard> getDashboardAsync(String userId) {
    CompletableFuture<UserInfo> userFuture = 
        CompletableFuture.supplyAsync(() -> userService.getUser(userId));
    
    CompletableFuture<List<Order>> ordersFuture = 
        CompletableFuture.supplyAsync(() -> orderService.getOrders(userId));
    
    CompletableFuture<Statistics> statsFuture = 
        CompletableFuture.supplyAsync(() -> statsService.getStats(userId));
    
    return CompletableFuture.allOf(userFuture, ordersFuture, statsFuture)
        .thenApply(v -> new Dashboard(
            userFuture.join(),
            ordersFuture.join(),
            statsFuture.join()
        ));
}

// 总响应时间：200ms（最慢的那个）
// 性能提升：2.25倍！
```

**异步编程的优势**：
- ✅ 并行执行，总耗时取决于最慢的步骤
- ✅ 主线程不阻塞，可以处理其他请求
- ✅ 充分利用多核CPU
- ✅ 提升用户体验

---

## 二、Future：异步计算的抽象

### 2.1 Future的设计思想

**核心思想**：将"计算"和"获取结果"分离

```
传统同步：
调用方法 ──> 等待计算 ──> 获取结果
         (阻塞)

Future异步：
提交任务 ──> 立即返回Future ──> 继续其他工作 ──> 需要时获取结果
         (不阻塞)              (可以做其他事)
```

### 2.2 Future接口定义

```java
public interface Future<V> {
    /**
     * 取消任务
     * @param mayInterruptIfRunning 是否中断正在执行的任务
     */
    boolean cancel(boolean mayInterruptIfRunning);
    
    /**
     * 任务是否被取消
     */
    boolean isCancelled();
    
    /**
     * 任务是否完成（正常、异常、取消都算完成）
     */
    boolean isDone();
    
    /**
     * 获取结果（阻塞）
     */
    V get() throws InterruptedException, ExecutionException;
    
    /**
     * 获取结果（超时）
     */
    V get(long timeout, TimeUnit unit) 
        throws InterruptedException, ExecutionException, TimeoutException;
}
```

**为什么这样设计？**

```
1. cancel() - 允许取消不再需要的任务
   场景：用户取消了请求，后台任务应该停止

2. isCancelled() / isDone() - 查询任务状态
   场景：UI显示任务进度

3. get() - 获取结果
   场景：需要结果时阻塞等待

4. get(timeout) - 超时获取
   场景：避免无限期等待
```

---

### 2.3 Future的局限性

```java
// 局限1：只能通过get()阻塞获取结果
Future<String> future = executor.submit(() -> "result");
String result = future.get(); // 阻塞！

// 局限2：无法链式调用
Future<String> future1 = executor.submit(() -> "hello");
// 想要：future1完成后，再执行另一个任务
// 无法做到！必须手动get()然后提交新任务

// 局限3：无法组合多个Future
Future<String> future1 = executor.submit(() -> "A");
Future<String> future2 = executor.submit(() -> "B");
// 想要：等待两个都完成，然后合并结果
// 无法做到！必须分别get()

// 局限4：异常处理不便
Future<String> future = executor.submit(() -> {
    throw new RuntimeException("error");
});
try {
    future.get(); // 异常被包装在ExecutionException中
} catch (ExecutionException e) {
    Throwable cause = e.getCause(); // 需要unwrap
}
```

**这些局限导致了CompletableFuture的诞生！**

---

## 三、Callable：有返回值的任务

### 3.1 Runnable vs Callable

```java
// Runnable：无返回值
public interface Runnable {
    void run();
}

// Callable：有返回值
public interface Callable<V> {
    V call() throws Exception;
}
```

**对比**：

| 特性 | Runnable | Callable |
|------|----------|----------|
| 返回值 | 无 | 有 |
| 异常 | 不能抛出检查异常 | 可以抛出Exception |
| 使用场景 | 不需要结果的任务 | 需要结果的任务 |

### 3.2 Callable的使用

```java
// 创建Callable
Callable<Integer> task = () -> {
    Thread.sleep(1000);
    return 42;
};

// 提交到线程池
ExecutorService executor = Executors.newFixedThreadPool(10);
Future<Integer> future = executor.submit(task);

// 获取结果
Integer result = future.get(); // 阻塞直到任务完成
System.out.println("结果: " + result);
```

---

## 四、Future的实际使用

### 4.1 基本使用示例

```java
public class FutureExample {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        // 提交任务
        Future<String> future = executor.submit(() -> {
            System.out.println("任务开始执行");
            Thread.sleep(2000);
            System.out.println("任务执行完成");
            return "Hello Future";
        });
        
        System.out.println("任务已提交，继续做其他事情");
        
        // 做其他事情
        Thread.sleep(1000);
        System.out.println("做了1秒的其他工作");
        
        // 获取结果
        System.out.println("开始获取结果");
        String result = future.get(); // 阻塞等待
        System.out.println("结果: " + result);
        
        executor.shutdown();
    }
}

// 输出：
// 任务已提交，继续做其他事情
// 任务开始执行
// 做了1秒的其他工作
// 开始获取结果
// 任务执行完成
// 结果: Hello Future
```

### 4.2 超时控制

```java
public class TimeoutExample {
    public static void main(String[] args) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        
        Future<String> future = executor.submit(() -> {
            Thread.sleep(5000); // 模拟耗时操作
            return "Result";
        });
        
        try {
            // 最多等待2秒
            String result = future.get(2, TimeUnit.SECONDS);
            System.out.println("结果: " + result);
        } catch (TimeoutException e) {
            System.out.println("任务超时！");
            future.cancel(true); // 取消任务
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        executor.shutdown();
    }
}
```

**为什么需要超时？**

```
场景1：避免无限期等待
- 远程服务可能挂了
- 设置超时，快速失败

场景2：保证响应时间
- Web请求有SLA要求
- 超时后返回降级结果
```

### 4.3 取消任务

```java
public class CancelExample {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        
        Future<String> future = executor.submit(() -> {
            for (int i = 0; i < 10; i++) {
                // 检查中断标志
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println("任务被中断");
                    return "Cancelled";
                }
                Thread.sleep(500);
                System.out.println("执行中: " + i);
            }
            return "Completed";
        });
        
        // 2秒后取消
        Thread.sleep(2000);
        System.out.println("取消任务");
        future.cancel(true); // 中断线程
        
        System.out.println("是否取消: " + future.isCancelled());
        System.out.println("是否完成: " + future.isDone());
        
        executor.shutdown();
    }
}
```

**cancel()的参数含义**：

```java
boolean cancel(boolean mayInterruptIfRunning)

mayInterruptIfRunning = true:
- 如果任务正在执行，尝试中断线程
- 任务需要检查Thread.interrupted()

mayInterruptIfRunning = false:
- 如果任务还未开始，取消执行
- 如果任务已开始，不中断
```

---

## 五、Future的实现原理

### 5.1 FutureTask的结构

```java
public class FutureTask<V> implements RunnableFuture<V> {
    // 任务状态
    private volatile int state;
    private static final int NEW          = 0; // 新建
    private static final int COMPLETING   = 1; // 完成中
    private static final int NORMAL       = 2; // 正常完成
    private static final int EXCEPTIONAL  = 3; // 异常完成
    private static final int CANCELLED    = 4; // 取消
    private static final int INTERRUPTING = 5; // 中断中
    private static final int INTERRUPTED  = 6; // 已中断
    
    // 任务
    private Callable<V> callable;
    
    // 结果或异常
    private Object outcome;
    
    // 执行任务的线程
    private volatile Thread runner;
    
    // 等待线程队列
    private volatile WaitNode waiters;
}
```

**状态转换**：

```
NEW -> COMPLETING -> NORMAL       (正常完成)
NEW -> COMPLETING -> EXCEPTIONAL  (异常完成)
NEW -> CANCELLED                  (取消)
NEW -> INTERRUPTING -> INTERRUPTED (中断)
```

### 5.2 get()的实现原理

```java
public V get() throws InterruptedException, ExecutionException {
    int s = state;
    
    // 如果任务未完成，等待
    if (s <= COMPLETING)
        s = awaitDone(false, 0L);
    
    // 返回结果
    return report(s);
}

private int awaitDone(boolean timed, long nanos) {
    // 加入等待队列
    WaitNode q = new WaitNode();
    
    for (;;) {
        // 检查中断
        if (Thread.interrupted()) {
            removeWaiter(q);
            throw new InterruptedException();
        }
        
        int s = state;
        if (s > COMPLETING) {
            // 任务完成，返回
            return s;
        } else if (s == COMPLETING) {
            // 任务即将完成，让出CPU
            Thread.yield();
        } else {
            // 阻塞等待
            LockSupport.park(this);
        }
    }
}
```

**为什么这样设计？**

```
1. 使用volatile state保证可见性
2. 使用CAS更新state保证原子性
3. 使用LockSupport.park()阻塞等待
4. 任务完成时，唤醒所有等待线程
```

---

## 六、Future的最佳实践

### 6.1 避免阻塞主线程

```java
// ❌ 不好的做法：立即get()
Future<String> future = executor.submit(task);
String result = future.get(); // 立即阻塞，失去了异步的意义

// ✅ 好的做法：先做其他事，最后再get()
Future<String> future = executor.submit(task);
doOtherWork(); // 做其他工作
String result = future.get(); // 最后获取结果
```

### 6.2 设置超时

```java
// ❌ 不好的做法：无限期等待
String result = future.get(); // 可能永远阻塞

// ✅ 好的做法：设置超时
try {
    String result = future.get(5, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    future.cancel(true);
    // 返回降级结果或抛出异常
}
```

### 6.3 正确处理异常

```java
// ❌ 不好的做法：忽略异常
try {
    future.get();
} catch (Exception e) {
    // 什么都不做
}

// ✅ 好的做法：正确处理异常
try {
    String result = future.get();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt(); // 恢复中断状态
    throw new RuntimeException("Task interrupted", e);
} catch (ExecutionException e) {
    Throwable cause = e.getCause(); // 获取真正的异常
    if (cause instanceof IOException) {
        // 处理IO异常
    } else {
        // 处理其他异常
    }
}
```

---

## 七、总结

### 7.1 为什么需要异步编程？

1. **提升性能**：并行执行，减少总耗时
2. **提高资源利用率**：充分利用多核CPU
3. **改善用户体验**：减少响应时间
4. **避免阻塞**：主线程不被阻塞

### 7.2 Future的核心要点

1. **设计思想**：将计算和获取结果分离
2. **核心方法**：get()、cancel()、isDone()
3. **局限性**：只能阻塞获取，无法链式调用和组合
4. **实现原理**：状态机 + 等待队列

### 7.3 Callable的核心要点

1. **vs Runnable**：有返回值，可抛出异常
2. **使用方式**：通过ExecutorService.submit()提交
3. **返回Future**：可以获取结果和控制任务

### 7.4 思考题

1. **为什么Future.get()会阻塞？如何避免？**
2. **cancel(true)和cancel(false)有什么区别？**
3. **如何实现多个Future的组合？**
4. **Future的局限性导致了什么问题？**

---

**下一章预告**：我们将学习CompletableFuture，它解决了Future的所有局限性，提供了强大的异步编程能力。

---

**参考资料**：
- 《Java并发编程实战》第6章
- JDK源码：`java.util.concurrent.Future`
- JDK源码：`java.util.concurrent.FutureTask`
