# 第五章：InheritableThreadLocal与优化方案

## 引言

本章将深入分析InheritableThreadLocal的实现原理，探讨父子线程值传递机制，以及Netty的FastThreadLocal等优化方案。

---

## 1. InheritableThreadLocal原理

### 1.1 问题1：InheritableThreadLocal是什么？

**定义**：

```java
public class InheritableThreadLocal<T> extends ThreadLocal<T> {
    /**
     * 子线程继承父线程的值时调用
     * 可以重写此方法自定义继承逻辑
     */
    protected T childValue(T parentValue) {
        return parentValue;
    }
    
    /**
     * 获取ThreadLocalMap（重写父类方法）
     */
    ThreadLocalMap getMap(Thread t) {
        return t.inheritableThreadLocals;
    }
    
    /**
     * 创建ThreadLocalMap（重写父类方法）
     */
    void createMap(Thread t, T firstValue) {
        t.inheritableThreadLocals = new ThreadLocalMap(this, firstValue);
    }
}
```

**核心区别**：

| 维度 | ThreadLocal | InheritableThreadLocal |
|------|-------------|----------------------|
| **存储位置** | Thread.threadLocals | Thread.inheritableThreadLocals |
| **父子线程** | 不传递 | 自动传递 |
| **使用场景** | 线程隔离 | 父子线程共享 |

---

### 1.2 问题2：父子线程值传递的原理是什么？

**Thread类的相关字段**：

```java
public class Thread implements Runnable {
    // ThreadLocal的存储
    ThreadLocal.ThreadLocalMap threadLocals = null;
    
    // InheritableThreadLocal的存储
    ThreadLocal.ThreadLocalMap inheritableThreadLocals = null;
}
```

**Thread构造函数中的继承逻辑**：

```java
public class Thread implements Runnable {
    private void init(ThreadGroup g, Runnable target, String name,
                      long stackSize, AccessControlContext acc,
                      boolean inheritThreadLocals) {
        // ...
        
        // 获取父线程
        Thread parent = currentThread();
        
        // 如果父线程有inheritableThreadLocals，则继承
        if (inheritThreadLocals && parent.inheritableThreadLocals != null)
            this.inheritableThreadLocals =
                ThreadLocal.createInheritedMap(parent.inheritableThreadLocals);
        
        // ...
    }
}
```

**createInheritedMap()源码**：

```java
static ThreadLocalMap createInheritedMap(ThreadLocalMap parentMap) {
    return new ThreadLocalMap(parentMap);
}

// ThreadLocalMap的拷贝构造函数
private ThreadLocalMap(ThreadLocalMap parentMap) {
    Entry[] parentTable = parentMap.table;
    int len = parentTable.length;
    setThreshold(len);
    table = new Entry[len];
    
    // 遍历父线程的ThreadLocalMap
    for (int j = 0; j < len; j++) {
        Entry e = parentTable[j];
        if (e != null) {
            @SuppressWarnings("unchecked")
            ThreadLocal<Object> key = (ThreadLocal<Object>) e.get();
            if (key != null) {
                // 调用childValue()获取子线程的值
                Object value = key.childValue(e.value);
                Entry c = new Entry(key, value);
                int h = key.threadLocalHashCode & (len - 1);
                while (table[h] != null)
                    h = nextIndex(h, len);
                table[h] = c;
                size++;
            }
        }
    }
}
```

**继承流程图**：

```
父线程创建子线程
    ↓
调用Thread构造函数
    ↓
检查parent.inheritableThreadLocals
    ↓
不为null
    ↓
调用createInheritedMap()
    ↓
创建新的ThreadLocalMap
    ↓
遍历父线程的Entry
    ↓
调用childValue()获取值
    ↓
复制到子线程的ThreadLocalMap
    ↓
子线程启动
```

---

### 1.3 问题3：InheritableThreadLocal的使用示例

**基本使用**：

```java
public class InheritableThreadLocalDemo {
    private static InheritableThreadLocal<String> holder = new InheritableThreadLocal<>();
    
    public static void main(String[] args) {
        // 父线程设置值
        holder.set("parent value");
        System.out.println("父线程: " + holder.get());
        
        // 创建子线程
        new Thread(() -> {
            // 子线程可以获取父线程的值
            System.out.println("子线程: " + holder.get()); // "parent value"
            
            // 子线程修改值
            holder.set("child value");
            System.out.println("子线程修改后: " + holder.get()); // "child value"
        }).start();
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 父线程的值不受影响
        System.out.println("父线程: " + holder.get()); // "parent value"
    }
}
```

**输出**：

```
父线程: parent value
子线程: parent value
子线程修改后: child value
父线程: parent value
```

---

**自定义childValue()**：

```java
public class CustomInheritableThreadLocal extends InheritableThreadLocal<List<String>> {
    @Override
    protected List<String> childValue(List<String> parentValue) {
        // 深拷贝，避免父子线程共享同一个List
        return new ArrayList<>(parentValue);
    }
}

// 使用示例
public class CustomDemo {
    private static CustomInheritableThreadLocal holder = new CustomInheritableThreadLocal();
    
    public static void main(String[] args) {
        List<String> list = new ArrayList<>();
        list.add("item1");
        holder.set(list);
        
        new Thread(() -> {
            List<String> childList = holder.get();
            childList.add("item2"); // 不会影响父线程的list
            System.out.println("子线程: " + childList);
        }).start();
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        System.out.println("父线程: " + holder.get()); // 只有item1
    }
}
```

---

### 1.4 问题4：InheritableThreadLocal的局限性是什么？

**局限性1：线程池场景下失效**

```java
public class InheritableThreadLocalProblem {
    private static InheritableThreadLocal<String> holder = new InheritableThreadLocal<>();
    private static ExecutorService executor = Executors.newFixedThreadPool(1);
    
    public static void main(String[] args) throws InterruptedException {
        // 第1次提交任务
        holder.set("value1");
        executor.execute(() -> {
            System.out.println("任务1: " + holder.get()); // "value1"
        });
        
        Thread.sleep(100);
        
        // 第2次提交任务（复用同一个线程）
        holder.set("value2");
        executor.execute(() -> {
            System.out.println("任务2: " + holder.get()); // "value1"（错误！）
        });
    }
}
```

**问题分析**：

```
线程池的线程是预先创建的：
1. 线程池创建Worker线程时，继承了主线程的值
2. 后续任务复用Worker线程，不会重新继承
3. 导致获取到的是旧值

解决方案：
- 使用TransmittableThreadLocal（阿里开源）
```

---

**局限性2：内存占用增加**

```java
// 每个子线程都会拷贝父线程的所有InheritableThreadLocal
// 如果父线程有很多InheritableThreadLocal，内存占用会增加

public class MemoryIssue {
    private static InheritableThreadLocal<byte[]> holder1 = new InheritableThreadLocal<>();
    private static InheritableThreadLocal<byte[]> holder2 = new InheritableThreadLocal<>();
    private static InheritableThreadLocal<byte[]> holder3 = new InheritableThreadLocal<>();
    
    public static void main(String[] args) {
        // 父线程设置大对象
        holder1.set(new byte[1024 * 1024]); // 1MB
        holder2.set(new byte[1024 * 1024]); // 1MB
        holder3.set(new byte[1024 * 1024]); // 1MB
        
        // 创建100个子线程
        for (int i = 0; i < 100; i++) {
            new Thread(() -> {
                // 每个子线程都会拷贝3MB数据
                // 总内存占用：100 * 3MB = 300MB
            }).start();
        }
    }
}
```

---

## 2. TransmittableThreadLocal

### 2.1 问题5：TransmittableThreadLocal是什么？

**TransmittableThreadLocal（TTL）**是阿里开源的ThreadLocal增强库，解决了InheritableThreadLocal在线程池场景下的问题。

**Maven依赖**：

```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>transmittable-thread-local</artifactId>
    <version>2.14.2</version>
</dependency>
```

**基本使用**：

```java
import com.alibaba.ttl.TransmittableThreadLocal;
import com.alibaba.ttl.threadpool.TtlExecutors;

public class TTLDemo {
    private static TransmittableThreadLocal<String> context = new TransmittableThreadLocal<>();
    
    public static void main(String[] args) throws InterruptedException {
        // 使用TtlExecutors包装线程池
        ExecutorService executor = TtlExecutors.getTtlExecutorService(
            Executors.newFixedThreadPool(1)
        );
        
        // 第1次提交任务
        context.set("value1");
        executor.execute(() -> {
            System.out.println("任务1: " + context.get()); // "value1"
        });
        
        Thread.sleep(100);
        
        // 第2次提交任务
        context.set("value2");
        executor.execute(() -> {
            System.out.println("任务2: " + context.get()); // "value2"（正确！）
        });
        
        executor.shutdown();
    }
}
```

---

### 2.2 问题6：TransmittableThreadLocal的实现原理是什么？

**核心思想**：

```
1. 在任务提交时，捕获当前线程的所有TTL值
2. 在任务执行前，将捕获的值设置到Worker线程
3. 在任务执行后，恢复Worker线程的原始值
```

**实现原理**：

```java
// TtlRunnable包装原始Runnable
public final class TtlRunnable implements Runnable {
    private final AtomicReference<Object> capturedRef;
    private final Runnable runnable;
    
    private TtlRunnable(Runnable runnable) {
        // 捕获当前线程的所有TTL值
        this.capturedRef = new AtomicReference<>(capture());
        this.runnable = runnable;
    }
    
    @Override
    public void run() {
        Object captured = capturedRef.get();
        if (captured == null || releaseTtlValueReferenceAfterRun && !capturedRef.compareAndSet(captured, null)) {
            throw new IllegalStateException("TTL value reference is released after run!");
        }
        
        // 备份Worker线程的原始值
        Object backup = replay(captured);
        try {
            // 执行原始任务
            runnable.run();
        } finally {
            // 恢复Worker线程的原始值
            restore(backup);
        }
    }
}
```

**流程图**：

```
主线程提交任务
    ↓
TtlRunnable.capture()
    ↓
捕获主线程的所有TTL值
    ↓
任务提交到线程池
    ↓
Worker线程执行任务
    ↓
TtlRunnable.replay()
    ↓
将捕获的值设置到Worker线程
    ↓
执行原始任务
    ↓
TtlRunnable.restore()
    ↓
恢复Worker线程的原始值
```

---

## 3. Netty的FastThreadLocal

### 3.1 问题7：FastThreadLocal是什么？

**FastThreadLocal**是Netty优化的ThreadLocal实现，性能比JDK的ThreadLocal更好。

**性能对比**：

```
场景：100万次get/set操作

JDK ThreadLocal：
- get: 150ms
- set: 180ms

Netty FastThreadLocal：
- get: 80ms
- set: 100ms

性能提升：约2倍
```

---

### 3.2 问题8：FastThreadLocal的优化原理是什么？

**优化1：使用数组代替HashMap**

```java
// JDK ThreadLocal使用ThreadLocalMap（类似HashMap）
// 需要计算hash、处理冲突

// Netty FastThreadLocal使用数组
public class FastThreadLocal<V> {
    private final int index; // 数组索引
    
    public FastThreadLocal() {
        // 分配唯一的索引
        index = InternalThreadLocalMap.nextVariableIndex();
    }
    
    public final V get() {
        // 直接通过索引访问，O(1)时间复杂度
        return get(InternalThreadLocalMap.get());
    }
}
```

**优化2：使用FastThreadLocalThread**

```java
// 必须使用FastThreadLocalThread
public class FastThreadLocalThread extends Thread {
    private InternalThreadLocalMap threadLocalMap;
    
    public final InternalThreadLocalMap threadLocalMap() {
        return threadLocalMap;
    }
    
    public final void setThreadLocalMap(InternalThreadLocalMap threadLocalMap) {
        this.threadLocalMap = threadLocalMap;
    }
}
```

**优化3：InternalThreadLocalMap的设计**

```java
public final class InternalThreadLocalMap {
    // 使用数组存储，而非HashMap
    private Object[] indexedVariables;
    
    // 初始容量
    private static final int INITIAL_CAPACITY = 32;
    
    public Object indexedVariable(int index) {
        Object[] lookup = indexedVariables;
        return index < lookup.length ? lookup[index] : UNSET;
    }
    
    public boolean setIndexedVariable(int index, Object value) {
        Object[] lookup = indexedVariables;
        if (index < lookup.length) {
            lookup[index] = value;
            return true;
        } else {
            expandIndexedVariableTableAndSet(index, value);
            return true;
        }
    }
}
```

**性能优势**：

```
JDK ThreadLocal：
- 使用ThreadLocalMap（开放寻址法）
- 需要计算hash：hashCode & (len - 1)
- 需要处理冲突：线性探测
- 时间复杂度：O(1) ~ O(n)

Netty FastThreadLocal：
- 使用数组
- 直接通过索引访问：indexedVariables[index]
- 无需计算hash，无需处理冲突
- 时间复杂度：O(1)
```

---

### 3.3 问题9：FastThreadLocal的使用示例

**基本使用**：

```java
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;

public class FastThreadLocalDemo {
    private static final FastThreadLocal<String> context = new FastThreadLocal<>();
    
    public static void main(String[] args) {
        // 必须使用FastThreadLocalThread
        Thread thread = new FastThreadLocalThread(() -> {
            context.set("value");
            System.out.println(context.get());
            
            // 使用完后remove
            context.remove();
        });
        
        thread.start();
    }
}
```

**在Netty中使用**：

```java
// Netty的EventLoop默认使用FastThreadLocalThread
public class NettyServer {
    public static void main(String[] args) {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) {
                     // EventLoop线程是FastThreadLocalThread
                     // 可以使用FastThreadLocal
                 }
             });
            
            ChannelFuture f = b.bind(8080).sync();
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
```

---

## 4. ThreadLocal的替代方案

### 4.1 问题10：ThreadLocal的替代方案有哪些？

**方案1：使用Request Scope（Spring）**

```java
// 使用Spring的RequestScope
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class UserContext {
    private User user;
    
    public User getUser() {
        return user;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
}

// 使用
@Service
public class UserService {
    @Autowired
    private UserContext userContext;
    
    public void process() {
        User user = userContext.getUser();
        // 使用user
    }
}
```

**优势**：
- Spring自动管理生命周期
- 无需手动remove
- 类型安全

---

**方案2：使用MDC（日志上下文）**

```java
import org.slf4j.MDC;

public class MDCDemo {
    public void handleRequest(String traceId) {
        try {
            // 设置MDC
            MDC.put("traceId", traceId);
            
            // 日志会自动包含traceId
            log.info("处理请求"); // [traceId=123] 处理请求
            
        } finally {
            // 清理MDC
            MDC.clear();
        }
    }
}
```

**优势**：
- 专门用于日志上下文
- 自动传递到日志框架
- 支持异步日志

---

**方案3：使用Context对象传递**

```java
// 显式传递Context对象
public class ContextDemo {
    public static class Context {
        private String userId;
        private String traceId;
        // getters and setters
    }
    
    public void handleRequest(Context context) {
        processOrder(context);
    }
    
    private void processOrder(Context context) {
        validateOrder(context);
        saveOrder(context);
    }
    
    private void validateOrder(Context context) {
        // 使用context
    }
    
    private void saveOrder(Context context) {
        // 使用context
    }
}
```

**优势**：
- 显式传递，易于理解
- 无内存泄漏风险
- 易于测试

**劣势**：
- 需要层层传递参数
- 代码冗长

---

## 5. 核心问题总结

### Q1: InheritableThreadLocal的原理是什么？
**A**: 在Thread构造函数中，拷贝父线程的inheritableThreadLocals到子线程。

### Q2: InheritableThreadLocal的局限性是什么？
**A**: 
1. 线程池场景下失效（线程复用）
2. 内存占用增加（每个子线程都拷贝）

### Q3: TransmittableThreadLocal如何解决线程池问题？
**A**: 在任务提交时捕获值，执行前设置，执行后恢复。

### Q4: FastThreadLocal为什么更快？
**A**: 使用数组代替HashMap，直接通过索引访问，无需计算hash和处理冲突。

### Q5: FastThreadLocal的使用限制是什么？
**A**: 必须使用FastThreadLocalThread，不能用普通Thread。

### Q6: ThreadLocal的替代方案有哪些？
**A**: 
1. Spring的Request Scope
2. MDC（日志上下文）
3. 显式传递Context对象

### Q7: 如何选择合适的方案？
**A**: 
- 简单场景：ThreadLocal
- 父子线程传递：InheritableThreadLocal
- 线程池场景：TransmittableThreadLocal
- 高性能场景：FastThreadLocal
- Web应用：Request Scope
- 日志场景：MDC

---

## 总结

通过本系列的学习，我们深入理解了：

1. **ThreadLocal的必要性**：解决线程隔离问题
2. **核心原理**：数据存储在Thread对象中，使用ThreadLocalMap
3. **ThreadLocalMap**：斐波那契散列、开放寻址法、弱引用Entry
4. **内存泄漏**：使用后不remove导致，特别是线程池场景
5. **最佳实践**：try-finally保证remove、避免大对象、监控检测
6. **InheritableThreadLocal**：父子线程值传递
7. **优化方案**：TransmittableThreadLocal、FastThreadLocal

**核心收获**：

- ✅ 理解ThreadLocal的设计思想和实现原理
- ✅ 掌握ThreadLocalMap的精妙设计
- ✅ 知道如何避免内存泄漏
- ✅ 学会正确使用ThreadLocal
- ✅ 了解各种优化方案和替代方案

**继续学习**：

- 深入学习JUC并发包
- 研究Spring的Request Scope实现
- 学习分布式追踪系统（如Zipkin、SkyWalking）
- 了解Netty的线程模型

🚀 ThreadLocal系列完结！
