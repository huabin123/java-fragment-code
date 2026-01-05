# Synchronized使用方式与最佳实践

## 1. Synchronized有哪几种使用方式？

### 1.1 问题1：Synchronized可以修饰哪些地方？

Synchronized有**三种使用方式**，每种方式的**锁对象不同**：

```
Synchronized使用方式
         ↓
    ┌────┴────┐
    ↓         ↓         ↓
修饰实例方法 修饰静态方法 同步代码块
    ↓         ↓         ↓
锁:this    锁:Class   锁:指定对象
```

---

### 1.2 方式一：修饰实例方法

**语法**：

```java
public class Counter {
    private int count = 0;
    
    // synchronized修饰实例方法
    public synchronized void increment() {
        count++;
    }
    
    public synchronized int getCount() {
        return count;
    }
}
```

**锁对象**：**this**（当前实例对象）

**等价于**：

```java
public void increment() {
    synchronized(this) {
        count++;
    }
}
```

**特点**：
- ✅ 同一个对象的多个synchronized实例方法互斥
- ✅ 不同对象的synchronized实例方法不互斥
- ✅ 适合保护实例变量

**示例**：

```java
public class SynchronizedInstanceMethodDemo {
    static class Counter {
        private int count = 0;
        
        public synchronized void increment() {
            count++;
            System.out.println(Thread.currentThread().getName() + 
                " increment: " + count);
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();
        
        // 两个线程访问同一个对象
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                counter.increment();
            }
        }, "Thread-1");
        
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                counter.increment();
            }
        }, "Thread-2");
        
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        
        System.out.println("Final count: " + counter.count);
    }
}
```

**输出**：

```
Thread-1 increment: 1
Thread-1 increment: 2
Thread-1 increment: 3
Thread-1 increment: 4
Thread-1 increment: 5
Thread-2 increment: 6
Thread-2 increment: 7
Thread-2 increment: 8
Thread-2 increment: 9
Thread-2 increment: 10
Final count: 10  ✓ 正确
```

**不同对象不互斥**：

```java
public static void main(String[] args) {
    Counter counter1 = new Counter();
    Counter counter2 = new Counter();
    
    // 两个线程访问不同对象
    Thread t1 = new Thread(() -> counter1.increment(), "Thread-1");
    Thread t2 = new Thread(() -> counter2.increment(), "Thread-2");
    
    t1.start();
    t2.start();
    
    // t1和t2可以并发执行，因为锁对象不同
}
```

---

### 1.3 方式二：修饰静态方法

**语法**：

```java
public class Counter {
    private static int count = 0;
    
    // synchronized修饰静态方法
    public static synchronized void increment() {
        count++;
    }
    
    public static synchronized int getCount() {
        return count;
    }
}
```

**锁对象**：**Class对象**（Counter.class）

**等价于**：

```java
public static void increment() {
    synchronized(Counter.class) {
        count++;
    }
}
```

**特点**：
- ✅ 所有线程访问该静态方法都会互斥
- ✅ 即使是不同对象调用也会互斥
- ✅ 适合保护静态变量

**示例**：

```java
public class SynchronizedStaticMethodDemo {
    static class Counter {
        private static int count = 0;
        
        public static synchronized void increment() {
            count++;
            System.out.println(Thread.currentThread().getName() + 
                " increment: " + count);
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        // 多个线程访问静态方法
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                Counter.increment();
            }
        }, "Thread-1");
        
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                Counter.increment();
            }
        }, "Thread-2");
        
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        
        System.out.println("Final count: " + Counter.count);
    }
}
```

**输出**：

```
Thread-1 increment: 1
Thread-1 increment: 2
Thread-2 increment: 3
Thread-2 increment: 4
Thread-1 increment: 5
Thread-1 increment: 6
Thread-2 increment: 7
Thread-2 increment: 8
Thread-1 increment: 9
Thread-2 increment: 10
Final count: 10  ✓ 正确
```

---

### 1.4 方式三：同步代码块

**语法**：

```java
public class Counter {
    private int count = 0;
    private final Object lock = new Object();
    
    public void increment() {
        // synchronized同步代码块
        synchronized(lock) {
            count++;
        }
    }
}
```

**锁对象**：**指定的对象**（可以是任意对象）

**常见锁对象选择**：

```java
// 1. 使用this作为锁对象
synchronized(this) {
    // ...
}

// 2. 使用Class对象作为锁对象
synchronized(Counter.class) {
    // ...
}

// 3. 使用专门的锁对象
private final Object lock = new Object();
synchronized(lock) {
    // ...
}

// 4. 使用其他对象作为锁对象
private final List<String> list = new ArrayList<>();
synchronized(list) {
    // ...
}
```

**特点**：
- ✅ 灵活性最高，可以指定锁对象
- ✅ 可以缩小锁的范围，提高并发性
- ✅ 可以使用多个锁对象，实现细粒度锁

**示例：细粒度锁**

```java
public class FineGrainedLockDemo {
    static class Account {
        private int balance = 1000;
        private String name;
        
        // 两个不同的锁对象
        private final Object balanceLock = new Object();
        private final Object nameLock = new Object();
        
        // 修改余额
        public void updateBalance(int amount) {
            synchronized(balanceLock) {  // 只锁余额
                balance += amount;
            }
        }
        
        // 修改名称
        public void updateName(String newName) {
            synchronized(nameLock) {  // 只锁名称
                name = newName;
            }
        }
    }
    
    public static void main(String[] args) {
        Account account = new Account();
        
        // 线程1修改余额
        Thread t1 = new Thread(() -> account.updateBalance(100));
        
        // 线程2修改名称
        Thread t2 = new Thread(() -> account.updateName("New Name"));
        
        t1.start();
        t2.start();
        
        // t1和t2可以并发执行，因为锁对象不同
    }
}
```

---

## 2. 三种方式的对比

### 2.1 锁对象对比

| 使用方式 | 锁对象 | 作用范围 | 适用场景 |
|---------|--------|---------|---------|
| 修饰实例方法 | this | 当前对象 | 保护实例变量 |
| 修饰静态方法 | Class对象 | 所有对象 | 保护静态变量 |
| 同步代码块 | 指定对象 | 灵活控制 | 细粒度锁、复杂场景 |

### 2.2 使用场景对比

```java
public class UsageComparison {
    // 场景1：保护实例变量 - 使用实例方法
    private int instanceCount = 0;
    
    public synchronized void incrementInstance() {
        instanceCount++;
    }
    
    // 场景2：保护静态变量 - 使用静态方法
    private static int staticCount = 0;
    
    public static synchronized void incrementStatic() {
        staticCount++;
    }
    
    // 场景3：细粒度锁 - 使用同步代码块
    private int count1 = 0;
    private int count2 = 0;
    private final Object lock1 = new Object();
    private final Object lock2 = new Object();
    
    public void incrementCount1() {
        synchronized(lock1) {
            count1++;
        }
    }
    
    public void incrementCount2() {
        synchronized(lock2) {
            count2++;
        }
    }
    
    // 场景4：缩小锁范围 - 使用同步代码块
    public void process() {
        // 不需要同步的操作
        String data = prepareData();
        
        // 只同步必要的部分
        synchronized(this) {
            updateSharedState(data);
        }
        
        // 不需要同步的操作
        logResult();
    }
    
    private String prepareData() { return "data"; }
    private void updateSharedState(String data) {}
    private void logResult() {}
}
```

---

## 3. 常见陷阱与错误用法

### 3.1 陷阱1：锁对象选择错误

**❌ 错误：使用可变对象作为锁**

```java
public class WrongLockObject {
    private String lock = "lock";  // ❌ String是不可变的，但引用可变
    
    public void method1() {
        synchronized(lock) {
            // ...
        }
    }
    
    public void changeLock() {
        lock = "newLock";  // ❌ 锁对象改变了！
    }
}
```

**问题**：锁对象改变后，原来的同步失效。

**✅ 正确：使用final对象作为锁**

```java
public class CorrectLockObject {
    private final Object lock = new Object();  // ✅ final保证引用不变
    
    public void method1() {
        synchronized(lock) {
            // ...
        }
    }
}
```

---

### 3.2 陷阱2：锁粒度过大

**❌ 错误：整个方法都加锁**

```java
public class CoarseGrainedLock {
    private int count = 0;
    
    public synchronized void process() {  // ❌ 整个方法都加锁
        // 1. 读取文件（耗时操作，不需要同步）
        String data = readFile();
        
        // 2. 更新共享变量（需要同步）
        count++;
        
        // 3. 写入日志（耗时操作，不需要同步）
        writeLog(data);
    }
    
    private String readFile() { return "data"; }
    private void writeLog(String data) {}
}
```

**问题**：不需要同步的操作也被锁住，降低并发性能。

**✅ 正确：缩小锁范围**

```java
public class FineGrainedLock {
    private int count = 0;
    private final Object lock = new Object();
    
    public void process() {
        // 1. 读取文件（不加锁）
        String data = readFile();
        
        // 2. 只同步必要的部分
        synchronized(lock) {
            count++;
        }
        
        // 3. 写入日志（不加锁）
        writeLog(data);
    }
    
    private String readFile() { return "data"; }
    private void writeLog(String data) {}
}
```

---

### 3.3 陷阱3：锁对象不一致

**❌ 错误：不同方法使用不同锁对象**

```java
public class InconsistentLock {
    private int count = 0;
    private final Object lock1 = new Object();
    private final Object lock2 = new Object();
    
    public void increment() {
        synchronized(lock1) {  // ❌ 使用lock1
            count++;
        }
    }
    
    public int getCount() {
        synchronized(lock2) {  // ❌ 使用lock2
            return count;
        }
    }
}
```

**问题**：两个方法的锁对象不同，无法实现互斥。

**✅ 正确：使用相同锁对象**

```java
public class ConsistentLock {
    private int count = 0;
    private final Object lock = new Object();  // ✅ 同一个锁对象
    
    public void increment() {
        synchronized(lock) {
            count++;
        }
    }
    
    public int getCount() {
        synchronized(lock) {
            return count;
        }
    }
}
```

---

### 3.4 陷阱4：在锁内部调用外部方法

**❌ 错误：在锁内部调用不可控的外部方法**

```java
public class AlienMethodCall {
    private final Object lock = new Object();
    private List<Listener> listeners = new ArrayList<>();
    
    public void fireEvent() {
        synchronized(lock) {
            for (Listener listener : listeners) {
                listener.onEvent();  // ❌ 调用外部方法，可能导致死锁
            }
        }
    }
}

interface Listener {
    void onEvent();
}
```

**问题**：外部方法可能会：
1. 执行耗时操作，长时间持有锁
2. 尝试获取其他锁，导致死锁
3. 抛出异常，导致锁无法释放

**✅ 正确：在锁外部调用外部方法**

```java
public class SafeAlienMethodCall {
    private final Object lock = new Object();
    private List<Listener> listeners = new ArrayList<>();
    
    public void fireEvent() {
        List<Listener> snapshot;
        
        // 在锁内部复制列表
        synchronized(lock) {
            snapshot = new ArrayList<>(listeners);
        }
        
        // 在锁外部调用外部方法
        for (Listener listener : snapshot) {
            listener.onEvent();  // ✅ 不持有锁
        }
    }
}
```

---

### 3.5 陷阱5：字符串常量作为锁对象

**❌ 错误：使用字符串常量作为锁**

```java
public class StringLock {
    public void method1() {
        synchronized("lock") {  // ❌ 字符串常量
            // ...
        }
    }
    
    public void method2() {
        synchronized("lock") {  // ❌ 同一个字符串常量
            // ...
        }
    }
}
```

**问题**：字符串常量会被缓存在字符串常量池中，可能导致意外的锁共享。

**✅ 正确：使用专门的锁对象**

```java
public class ObjectLock {
    private final Object lock = new Object();  // ✅ 专门的锁对象
    
    public void method1() {
        synchronized(lock) {
            // ...
        }
    }
    
    public void method2() {
        synchronized(lock) {
            // ...
        }
    }
}
```

---

## 4. 死锁问题

### 4.1 问题1：什么是死锁？

**定义**：两个或多个线程互相持有对方需要的锁，导致所有线程都无法继续执行。

**死锁的四个必要条件**：

```
1. 互斥条件：资源不能被共享，只能由一个线程使用
2. 持有并等待：线程持有至少一个资源，并等待获取其他资源
3. 不可剥夺：资源不能被强制剥夺，只能由持有者主动释放
4. 循环等待：存在一个线程等待链，形成环路
```

**死锁示例**：

```java
public class DeadlockDemo {
    private static final Object lock1 = new Object();
    private static final Object lock2 = new Object();
    
    public static void main(String[] args) {
        Thread t1 = new Thread(() -> {
            synchronized(lock1) {
                System.out.println("Thread-1: 持有lock1，等待lock2");
                try { Thread.sleep(100); } catch (InterruptedException e) {}
                
                synchronized(lock2) {  // 等待lock2
                    System.out.println("Thread-1: 获得lock2");
                }
            }
        }, "Thread-1");
        
        Thread t2 = new Thread(() -> {
            synchronized(lock2) {
                System.out.println("Thread-2: 持有lock2，等待lock1");
                try { Thread.sleep(100); } catch (InterruptedException e) {}
                
                synchronized(lock1) {  // 等待lock1
                    System.out.println("Thread-2: 获得lock1");
                }
            }
        }, "Thread-2");
        
        t1.start();
        t2.start();
    }
}
```

**输出**：

```
Thread-1: 持有lock1，等待lock2
Thread-2: 持有lock2，等待lock1
（程序卡住，发生死锁）
```

**死锁流程图**：

```
时间轴：
T1: Thread-1获取lock1
T2: Thread-2获取lock2
T3: Thread-1等待lock2（被Thread-2持有）
T4: Thread-2等待lock1（被Thread-1持有）
    ↓
   死锁！
   
   Thread-1          Thread-2
      ↓                 ↓
   持有lock1         持有lock2
      ↓                 ↓
   等待lock2 ←───────→ 等待lock1
      ↓                 ↓
    阻塞              阻塞
```

---

### 4.2 如何避免死锁？

#### 方法1：按顺序获取锁

**✅ 正确：所有线程按相同顺序获取锁**

```java
public class AvoidDeadlockByOrder {
    private static final Object lock1 = new Object();
    private static final Object lock2 = new Object();
    
    public static void main(String[] args) {
        Thread t1 = new Thread(() -> {
            synchronized(lock1) {  // 先获取lock1
                System.out.println("Thread-1: 持有lock1");
                synchronized(lock2) {  // 再获取lock2
                    System.out.println("Thread-1: 持有lock2");
                }
            }
        }, "Thread-1");
        
        Thread t2 = new Thread(() -> {
            synchronized(lock1) {  // 先获取lock1（顺序一致）
                System.out.println("Thread-2: 持有lock1");
                synchronized(lock2) {  // 再获取lock2
                    System.out.println("Thread-2: 持有lock2");
                }
            }
        }, "Thread-2");
        
        t1.start();
        t2.start();
    }
}
```

**实际应用：银行转账**

```java
public class BankTransfer {
    static class Account {
        private int id;
        private int balance;
        
        public Account(int id, int balance) {
            this.id = id;
            this.balance = balance;
        }
        
        public int getId() { return id; }
        public int getBalance() { return balance; }
        public void debit(int amount) { balance -= amount; }
        public void credit(int amount) { balance += amount; }
    }
    
    // ✅ 按账户ID顺序获取锁
    public static void transfer(Account from, Account to, int amount) {
        Account first, second;
        
        // 确定锁的顺序
        if (from.getId() < to.getId()) {
            first = from;
            second = to;
        } else {
            first = to;
            second = from;
        }
        
        // 按顺序获取锁
        synchronized(first) {
            synchronized(second) {
                from.debit(amount);
                to.credit(amount);
                System.out.println("转账成功: " + amount);
            }
        }
    }
    
    public static void main(String[] args) {
        Account account1 = new Account(1, 1000);
        Account account2 = new Account(2, 1000);
        
        // 线程1: account1 -> account2
        Thread t1 = new Thread(() -> transfer(account1, account2, 100));
        
        // 线程2: account2 -> account1
        Thread t2 = new Thread(() -> transfer(account2, account1, 200));
        
        t1.start();
        t2.start();
        
        // 不会发生死锁，因为锁的顺序一致
    }
}
```

---

#### 方法2：使用tryLock设置超时

**使用ReentrantLock的tryLock**：

```java
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;

public class AvoidDeadlockByTimeout {
    private static final Lock lock1 = new ReentrantLock();
    private static final Lock lock2 = new ReentrantLock();
    
    public static void main(String[] args) {
        Thread t1 = new Thread(() -> {
            try {
                while (true) {
                    // 尝试获取lock1，超时时间1秒
                    if (lock1.tryLock(1, TimeUnit.SECONDS)) {
                        try {
                            System.out.println("Thread-1: 获得lock1");
                            
                            // 尝试获取lock2，超时时间1秒
                            if (lock2.tryLock(1, TimeUnit.SECONDS)) {
                                try {
                                    System.out.println("Thread-1: 获得lock2");
                                    // 执行业务逻辑
                                    return;
                                } finally {
                                    lock2.unlock();
                                }
                            } else {
                                System.out.println("Thread-1: 获取lock2超时，重试");
                            }
                        } finally {
                            lock1.unlock();
                        }
                    } else {
                        System.out.println("Thread-1: 获取lock1超时，重试");
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Thread-1");
        
        t1.start();
    }
}
```

---

#### 方法3：避免嵌套锁

**✅ 尽量避免在持有锁的情况下获取另一个锁**

```java
public class AvoidNestedLock {
    private final Object lock1 = new Object();
    private final Object lock2 = new Object();
    
    // ❌ 避免这样做
    public void badMethod() {
        synchronized(lock1) {
            synchronized(lock2) {  // 嵌套锁
                // ...
            }
        }
    }
    
    // ✅ 推荐这样做
    public void goodMethod() {
        // 方法1只需要lock1
        synchronized(lock1) {
            // ...
        }
    }
    
    public void anotherGoodMethod() {
        // 方法2只需要lock2
        synchronized(lock2) {
            // ...
        }
    }
}
```

---

#### 方法4：使用死锁检测工具

**JDK自带工具**：

```bash
# 1. jps查看Java进程ID
jps

# 2. jstack检测死锁
jstack <pid>
```

**jstack输出示例**：

```
Found one Java-level deadlock:
=============================
"Thread-2":
  waiting to lock monitor 0x00007f8b1c004e00 (object 0x000000076ab3e6d0, a java.lang.Object),
  which is held by "Thread-1"
"Thread-1":
  waiting to lock monitor 0x00007f8b1c007350 (object 0x000000076ab3e6e0, a java.lang.Object),
  which is held by "Thread-2"

Java stack information for the threads listed above:
===================================================
```

---

## 5. 最佳实践

### 5.1 实践1：锁对象使用final

**✅ 推荐**：

```java
public class BestPractice1 {
    private final Object lock = new Object();  // ✅ final保证引用不变
    
    public void method() {
        synchronized(lock) {
            // ...
        }
    }
}
```

---

### 5.2 实践2：缩小锁的范围

**✅ 推荐**：只锁必要的代码

```java
public class BestPractice2 {
    private int count = 0;
    private final Object lock = new Object();
    
    public void process() {
        // 不需要同步的操作
        String data = prepareData();
        
        // 只同步必要的部分
        synchronized(lock) {
            count++;
            updateState(data);
        }
        
        // 不需要同步的操作
        logResult();
    }
    
    private String prepareData() { return "data"; }
    private void updateState(String data) {}
    private void logResult() {}
}
```

---

### 5.3 实践3：避免在锁内部执行耗时操作

**❌ 不推荐**：

```java
public class BadPractice3 {
    private final Object lock = new Object();
    
    public void process() {
        synchronized(lock) {
            // ❌ 在锁内部执行耗时操作
            readFromDatabase();
            callRemoteService();
            writeToFile();
        }
    }
    
    private void readFromDatabase() {}
    private void callRemoteService() {}
    private void writeToFile() {}
}
```

**✅ 推荐**：

```java
public class GoodPractice3 {
    private final Object lock = new Object();
    private volatile String cachedData;
    
    public void process() {
        // 在锁外部执行耗时操作
        String data = readFromDatabase();
        String result = callRemoteService(data);
        
        // 只同步必要的部分
        synchronized(lock) {
            cachedData = result;
        }
        
        // 在锁外部执行耗时操作
        writeToFile(result);
    }
    
    private String readFromDatabase() { return "data"; }
    private String callRemoteService(String data) { return "result"; }
    private void writeToFile(String result) {}
}
```

---

### 5.4 实践4：使用私有锁对象

**❌ 不推荐**：使用this或公共对象

```java
public class BadPractice4 {
    public void method() {
        synchronized(this) {  // ❌ 外部可以对this加锁
            // ...
        }
    }
}

// 外部代码可能会这样做
BadPractice4 obj = new BadPractice4();
synchronized(obj) {  // 外部对obj加锁，可能导致死锁
    obj.method();
}
```

**✅ 推荐**：使用私有锁对象

```java
public class GoodPractice4 {
    private final Object lock = new Object();  // ✅ 私有锁对象
    
    public void method() {
        synchronized(lock) {
            // ...
        }
    }
}
```

---

### 5.5 实践5：文档化锁策略

**✅ 推荐**：在代码中注释锁策略

```java
public class GoodPractice5 {
    /**
     * 保护count变量的锁
     * 所有访问count的方法都必须持有此锁
     */
    private final Object countLock = new Object();
    private int count = 0;
    
    /**
     * 保护name变量的锁
     * 所有访问name的方法都必须持有此锁
     */
    private final Object nameLock = new Object();
    private String name;
    
    /**
     * 增加计数
     * 线程安全：使用countLock保护
     */
    public void increment() {
        synchronized(countLock) {
            count++;
        }
    }
    
    /**
     * 更新名称
     * 线程安全：使用nameLock保护
     */
    public void updateName(String newName) {
        synchronized(nameLock) {
            name = newName;
        }
    }
}
```

---

### 5.6 实践6：优先使用并发工具类

**✅ 推荐**：优先使用JUC提供的并发工具类

```java
// ❌ 不推荐：手动实现线程安全的计数器
public class ManualCounter {
    private int count = 0;
    
    public synchronized void increment() {
        count++;
    }
    
    public synchronized int get() {
        return count;
    }
}

// ✅ 推荐：使用AtomicInteger
import java.util.concurrent.atomic.AtomicInteger;

public class BetterCounter {
    private final AtomicInteger count = new AtomicInteger(0);
    
    public void increment() {
        count.incrementAndGet();
    }
    
    public int get() {
        return count.get();
    }
}
```

**常用并发工具类**：

```java
// 1. 原子类
AtomicInteger, AtomicLong, AtomicReference

// 2. 并发容器
ConcurrentHashMap, CopyOnWriteArrayList, ConcurrentLinkedQueue

// 3. 同步工具
CountDownLatch, CyclicBarrier, Semaphore

// 4. 显式锁
ReentrantLock, ReadWriteLock, StampedLock
```

---

## 6. 使用流程图

### 6.1 选择Synchronized使用方式的流程

```
开始
  ↓
需要保护什么？
  ↓
  ┌────────┴────────┐
  ↓                 ↓
实例变量          静态变量
  ↓                 ↓
是否需要细粒度锁？  使用静态方法
  ↓                 ↓
  ┌──┴──┐          public static synchronized void method()
  ↓     ↓
 是    否
  ↓     ↓
使用    使用实例方法
同步    ↓
代码块  public synchronized void method()
  ↓
synchronized(lockObject) {
    // 只锁必要的代码
}
```

### 6.2 避免死锁的流程

```
需要获取多个锁？
  ↓
  ┌──────┴──────┐
  ↓             ↓
 是            否
  ↓             ↓
能否按顺序    直接使用
获取锁？      synchronized
  ↓
  ┌──┴──┐
  ↓     ↓
 能    不能
  ↓     ↓
按固定  考虑使用
顺序    ReentrantLock
获取锁  的tryLock
  ↓     ↓
  └──┬──┘
     ↓
   成功
```

---

## 7. 总结

### 7.1 三种使用方式

| 方式 | 语法 | 锁对象 | 适用场景 |
|-----|------|--------|---------|
| 修饰实例方法 | `public synchronized void method()` | this | 保护实例变量 |
| 修饰静态方法 | `public static synchronized void method()` | Class对象 | 保护静态变量 |
| 同步代码块 | `synchronized(lock) {}` | 指定对象 | 细粒度锁、复杂场景 |

### 7.2 常见陷阱

1. ❌ 锁对象选择错误（使用可变对象）
2. ❌ 锁粒度过大（整个方法加锁）
3. ❌ 锁对象不一致（不同方法用不同锁）
4. ❌ 在锁内部调用外部方法
5. ❌ 使用字符串常量作为锁

### 7.3 避免死锁

1. ✅ 按顺序获取锁
2. ✅ 使用tryLock设置超时
3. ✅ 避免嵌套锁
4. ✅ 使用死锁检测工具

### 7.4 最佳实践

1. ✅ 锁对象使用final
2. ✅ 缩小锁的范围
3. ✅ 避免在锁内部执行耗时操作
4. ✅ 使用私有锁对象
5. ✅ 文档化锁策略
6. ✅ 优先使用并发工具类

---

**下一章**：我们将对比Synchronized和ReentrantLock，帮助你选择合适的锁机制。
