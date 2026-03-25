# CPU飙高问题排查

## 💡 大白话精华总结

**CPU飙高是什么？**
- 就是CPU使用率突然飙到80%、90%甚至100%
- 就像发动机突然转速飙升，快要爆了

**CPU飙高的4大原因：**
1. **死循环**：代码一直转圈，停不下来
2. **频繁GC**：垃圾回收太频繁，CPU都在扫垃圾
3. **大量计算**：复杂的计算任务（如加密、正则表达式）
4. **线程过多**：线程太多，CPU忙着切换

**排查CPU飙高的5步法：**
```bash
# 1. 找到CPU占用高的Java进程
top
# 找到PID，比如12345

# 2. 找到该进程中CPU占用高的线程
top -Hp 12345
# 找到线程ID，比如12346

# 3. 将线程ID转为16进制
printf "%x\n" 12346
# 得到 0x303a

# 4. 打印线程堆栈
jstack 12345 | grep 0x303a -A 30
# 看到线程在执行什么代码

# 5. 分析代码，找到问题
```

**常见问题和解决方案：**

**问题1：死循环**
```java
// 问题代码
while (true) {
    // 没有sleep，CPU 100%
}

// 解决方案
while (true) {
    Thread.sleep(100);  // 加个sleep
}
```

**问题2：频繁GC**
```bash
# 查看GC情况
jstat -gc 12345 1000
# 如果GC频繁，说明内存不够

# 解决方案
-Xmx4g  # 增大堆内存
```

**问题3：正则表达式回溯**
```java
// 问题代码
input.matches("(a+)+b");  // 复杂正则，回溯严重

// 解决方案
使用简单正则，或者预编译Pattern
```

**问题4：线程过多**
```bash
# 查看线程数
jstack 12345 | grep "java.lang.Thread.State" | wc -l

# 解决方案
使用线程池，控制线程数量
```

**快速诊断命令：**
```bash
# 一键查看CPU占用高的线程
jstack $(pgrep java) | grep -A 10 $(printf "%x" $(ps H -eo pid,tid,%cpu | sort -rk 3 | head -1 | awk '{print $2}'))
```

**一句话记住：**
> CPU飙高先top找进程，再top -Hp找线程，jstack看堆栈，定位问题代码！

---

## 📚 概述

CPU飙高是生产环境最常见的性能问题之一。本文从架构师视角讲解CPU飙高问题的排查思路、方法和解决方案。

## 🎯 核心问题

- ❓ CPU飙高的常见原因有哪些？
- ❓ 如何快速定位CPU飙高的线程？
- ❓ 如何分析线程在做什么？
- ❓ 如何区分是业务代码问题还是GC问题？
- ❓ 如何预防CPU飙高？

---

## 一、CPU飙高的原因

### 1.1 常见原因分类

```
CPU飙高原因
    ↓
┌────┴────┬────┬────┐
│         │    │    │
业务代码  GC问题  系统问题  外部因素
```

### 1.2 业务代码问题

```java
// 原因1：死循环
public void deadLoop() {
    while (true) {
        // 没有退出条件
    }
}

// 原因2：大量计算
public void heavyComputation() {
    for (int i = 0; i < Integer.MAX_VALUE; i++) {
        Math.pow(i, 2);
    }
}

// 原因3：正则表达式回溯
public boolean validate(String input) {
    // 复杂正则导致回溯
    return input.matches("(a+)+b");
}

// 原因4：频繁创建对象
public void createObjects() {
    while (true) {
        new byte[1024 * 1024];  // 频繁创建大对象
    }
}
```

### 1.3 GC问题

```
GC导致CPU飙高：

1. 频繁Minor GC
   - 新生代太小
   - 对象创建速率高
   - CPU消耗在GC上

2. 频繁Full GC
   - 老年代空间不足
   - 元空间不足
   - System.gc()调用

3. GC时间长
   - 堆太大
   - 存活对象多
   - GC算法不合适
```

---

## 二、排查流程

### 2.1 完整排查流程图

```
发现CPU飙高
    ↓
1. 确认CPU使用率
   top / htop
    ↓
2. 定位Java进程
   top -H -p <pid>
    ↓
3. 找到占用CPU高的线程
   记录线程ID（十进制）
    ↓
4. 转换线程ID
   printf "%x\n" <线程ID>
    ↓
5. 导出线程栈
   jstack <pid> > thread.txt
    ↓
6. 分析线程栈
   grep <十六进制线程ID> thread.txt
    ↓
7. 定位问题代码
   分析栈信息
    ↓
8. 解决问题
   修复代码/调整参数
```

### 2.2 详细排查步骤

#### 步骤1：确认CPU使用率

```bash
# 查看整体CPU使用率
top

# 输出示例
top - 10:30:15 up 10 days,  2:15,  1 user,  load average: 4.50, 3.20, 2.10
Tasks: 150 total,   2 running, 148 sleeping,   0 stopped,   0 zombie
%Cpu(s): 85.0 us,  5.0 sy,  0.0 ni, 10.0 id,  0.0 wa,  0.0 hi,  0.0 si,  0.0 st
KiB Mem : 16384000 total,  2048000 free, 12288000 used,  2048000 buff/cache
KiB Swap:        0 total,        0 free,        0 used. 10240000 avail Mem

  PID USER      PR  NI    VIRT    RES    SHR S  %CPU %MEM     TIME+ COMMAND
12345 app       20   0 8192000 4096000  20480 R  95.0 25.0  10:30.50 java
```

#### 步骤2：定位占用CPU高的线程

```bash
# 查看进程内的线程CPU使用情况
top -H -p 12345

# 或使用ps命令
ps -mp 12345 -o THREAD,tid,time | sort -rn | head -20

# 输出示例
USER     %CPU PRI SCNT WCHAN  USER SYSTEM   TID     TIME
app      45.0  19    - -         -      - 12346 00:05:30
app      30.0  19    - -         -      - 12347 00:03:20
app      10.0  19    - -         -      - 12348 00:01:10
```

#### 步骤3：转换线程ID为十六进制

```bash
# 线程ID：12346（十进制）
printf "%x\n" 12346

# 输出：303a（十六进制）
```

#### 步骤4：导出线程栈

```bash
# 导出线程栈
jstack 12345 > thread.txt

# 或者多次导出对比
jstack 12345 > thread1.txt
sleep 3
jstack 12345 > thread2.txt
sleep 3
jstack 12345 > thread3.txt
```

#### 步骤5：分析线程栈

```bash
# 查找对应线程的栈信息
grep -A 20 "303a" thread.txt

# 输出示例
"business-thread-1" #12 prio=5 os_prio=0 tid=0x00007f8c3c001000 nid=0x303a runnable [0x00007f8c2b7fe000]
   java.lang.Thread.State: RUNNABLE
        at com.example.service.BusinessService.process(BusinessService.java:45)
        at com.example.controller.ApiController.handle(ApiController.java:30)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        ...
```

---

## 三、常见场景分析

### 3.1 场景1：业务代码死循环

#### 问题现象

```
- CPU使用率：95%+
- 线程状态：RUNNABLE
- 栈信息：一直在同一个方法
```

#### 线程栈示例

```
"business-thread-1" #12 prio=5 os_prio=0 tid=0x00007f8c3c001000 nid=0x303a runnable
   java.lang.Thread.State: RUNNABLE
        at com.example.service.DataProcessor.process(DataProcessor.java:100)
        at com.example.service.DataProcessor.process(DataProcessor.java:100)
        at com.example.service.DataProcessor.process(DataProcessor.java:100)
```

#### 问题代码

```java
public class DataProcessor {
    
    public void process(List<Data> dataList) {
        // 问题：条件永远为true
        while (dataList.size() > 0) {
            // 处理数据
            processData(dataList.get(0));
            // 忘记移除已处理的数据
            // dataList.remove(0);  // 缺少这行
        }
    }
}
```

#### 解决方案

```java
public class DataProcessor {
    
    public void process(List<Data> dataList) {
        while (dataList.size() > 0) {
            processData(dataList.get(0));
            dataList.remove(0);  // 添加移除逻辑
        }
    }
}
```

### 3.2 场景2：频繁GC导致CPU飙高

#### 问题现象

```
- CPU使用率：80%+
- GC线程占用CPU高
- GC日志显示频繁GC
```

#### 排查方法

```bash
# 查看GC统计
jstat -gcutil <pid> 1000

# 输出示例（频繁Minor GC）
  S0     S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT     GCT
  0.00  95.00  99.50  85.00  90.00  85.00  1000   10.500    5    2.500   13.000

# 分析：
# - E（Eden）使用率99.5%，接近满
# - YGC（Minor GC）次数1000次
# - 平均每次Minor GC：10.5ms
# - 问题：Minor GC过于频繁
```

#### 解决方案

```bash
# 方案1：增大新生代
-Xmn4g  # 原来2g，增加到4g

# 方案2：优化代码，减少对象创建
# 使用对象池、复用对象

# 方案3：调整Survivor比例
-XX:SurvivorRatio=6  # 原来8，调整为6
```

### 3.3 场景3：正则表达式回溯

#### 问题现象

```
- CPU使用率：90%+
- 线程栈显示在正则匹配
- 特定输入导致
```

#### 问题代码

```java
public class RegexValidator {
    
    // 问题：复杂正则导致回溯
    private static final Pattern PATTERN = Pattern.compile("(a+)+b");
    
    public boolean validate(String input) {
        // 输入"aaaaaaaaaaaaaaaaaaaaac"会导致大量回溯
        return PATTERN.matcher(input).matches();
    }
}
```

#### 线程栈示例

```
"http-nio-8080-exec-1" #20 prio=5 os_prio=0 tid=0x00007f8c3c002000 nid=0x303b runnable
   java.lang.Thread.State: RUNNABLE
        at java.util.regex.Pattern$Loop.match(Pattern.java:4785)
        at java.util.regex.Pattern$GroupTail.match(Pattern.java:4717)
        at java.util.regex.Pattern$BranchConn.match(Pattern.java:4568)
        at java.util.regex.Pattern$GroupHead.match(Pattern.java:4658)
        at java.util.regex.Pattern$Loop.match(Pattern.java:4785)
        ...（大量重复）
```

#### 解决方案

```java
public class RegexValidator {
    
    // 方案1：简化正则
    private static final Pattern PATTERN = Pattern.compile("a+b");
    
    // 方案2：添加长度检查
    public boolean validate(String input) {
        if (input.length() > 100) {
            return false;  // 拒绝过长输入
        }
        return PATTERN.matcher(input).matches();
    }
    
    // 方案3：使用超时机制
    public boolean validateWithTimeout(String input) {
        Future<Boolean> future = executor.submit(() -> 
            PATTERN.matcher(input).matches()
        );
        try {
            return future.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return false;
        }
    }
}
```

### 3.4 场景4：大量线程竞争

#### 问题现象

```
- CPU使用率：70%+
- 大量线程处于BLOCKED状态
- 系统吞吐量低
```

#### 线程栈示例

```
"business-thread-1" #12 waiting for monitor entry [0x00007f8c2b7fe000]
   java.lang.Thread.State: BLOCKED (on object monitor)
        at com.example.service.CacheService.get(CacheService.java:20)
        - waiting to lock <0x00000000e0000000> (a java.util.HashMap)

"business-thread-2" #13 waiting for monitor entry [0x00007f8c2b6fd000]
   java.lang.Thread.State: BLOCKED (on object monitor)
        at com.example.service.CacheService.get(CacheService.java:20)
        - waiting to lock <0x00000000e0000000> (a java.util.HashMap)
```

#### 问题代码

```java
public class CacheService {
    
    // 问题：使用synchronized HashMap
    private Map<String, Object> cache = new HashMap<>();
    
    public synchronized Object get(String key) {
        return cache.get(key);
    }
    
    public synchronized void put(String key, Object value) {
        cache.put(key, value);
    }
}
```

#### 解决方案

```java
public class CacheService {
    
    // 方案1：使用ConcurrentHashMap
    private Map<String, Object> cache = new ConcurrentHashMap<>();
    
    public Object get(String key) {
        return cache.get(key);
    }
    
    public void put(String key, Object value) {
        cache.put(key, value);
    }
    
    // 方案2：使用读写锁
    private Map<String, Object> cache2 = new HashMap<>();
    private ReadWriteLock lock = new ReentrantReadWriteLock();
    
    public Object getWithLock(String key) {
        lock.readLock().lock();
        try {
            return cache2.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }
}
```

---

## 四、排查工具

### 4.1 Arthas排查

```bash
# 启动Arthas
java -jar arthas-boot.jar

# 查看CPU占用高的线程
thread -n 5

# 输出示例
"business-thread-1" Id=12 RUNNABLE
    at com.example.service.BusinessService.process(BusinessService.java:45)
    at com.example.controller.ApiController.handle(ApiController.java:30)

# 查看线程详情
thread 12

# 监控方法执行
watch com.example.service.BusinessService process '{params, returnObj, throwExp}' -x 2
```

### 4.2 async-profiler

```bash
# 启动profiling（采样60秒）
./profiler.sh -d 60 -f cpu.html <pid>

# 生成火焰图
# 可以直观看到哪些方法消耗CPU最多
```

---

## 五、预防措施

### 5.1 代码规范

```java
/**
 * CPU密集型操作最佳实践
 */
public class CPUIntensiveTask {
    
    // 1. 避免死循环
    public void process() {
        int maxIterations = 10000;
        int count = 0;
        while (condition() && count++ < maxIterations) {
            // 处理逻辑
        }
    }
    
    // 2. 大量计算使用线程池
    private ExecutorService executor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors()
    );
    
    public void heavyComputation(List<Data> dataList) {
        List<Future<Result>> futures = new ArrayList<>();
        for (Data data : dataList) {
            futures.add(executor.submit(() -> compute(data)));
        }
        // 等待结果
        for (Future<Result> future : futures) {
            future.get();
        }
    }
    
    // 3. 正则表达式优化
    private static final Pattern PATTERN = Pattern.compile("simple-pattern");
    
    public boolean validate(String input) {
        // 长度检查
        if (input.length() > MAX_LENGTH) {
            return false;
        }
        return PATTERN.matcher(input).matches();
    }
}
```

### 5.2 监控告警

```yaml
# Prometheus告警规则
groups:
  - name: cpu_alerts
    rules:
      - alert: HighCPUUsage
        expr: process_cpu_usage > 0.8
        for: 5m
        annotations:
          summary: "CPU使用率过高"
          description: "CPU使用率超过80%持续5分钟"
```

---

## 六、总结

### 6.1 排查步骤

```
1. top 查看CPU使用率
2. top -H -p <pid> 找到占用CPU高的线程
3. printf "%x\n" <线程ID> 转换为十六进制
4. jstack <pid> 导出线程栈
5. grep <十六进制ID> 查找线程栈
6. 分析代码定位问题
7. 修复问题
```

### 6.2 常见原因

```
1. 业务代码死循环
2. 频繁GC
3. 正则表达式回溯
4. 大量线程竞争
5. 大量计算
```

---

**下一篇**：[内存泄漏排查](./04_内存泄漏排查.md)
