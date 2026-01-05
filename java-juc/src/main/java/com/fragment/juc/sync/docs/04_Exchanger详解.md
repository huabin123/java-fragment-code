# 第四章：Exchanger详解 - 交换器

> **学习目标**：深入理解Exchanger的原理和使用场景

---

## 一、什么是Exchanger？

### 1.1 定义

```
Exchanger（交换器）：
一个同步点，两个线程可以在此交换数据。

核心概念：
- 交换点：两个线程的同步点
- exchange()：交换数据，阻塞等待另一个线程
- 一对一：只能两个线程交换
- 双向传递：两个线程互相传递数据
```

### 1.2 应用场景

```
典型场景：

1. 数据校对：
   - 一个线程生成数据
   - 另一个线程校验数据
   - 交换结果

2. 遗传算法：
   - 两个线程各自计算
   - 交换基因数据
   - 继续计算

3. 管道设计：
   - 生产者和消费者
   - 交换缓冲区
   - 避免竞争

4. 数据转换：
   - 一个线程读取数据
   - 另一个线程处理数据
   - 交换数据和结果
```

---

## 二、Exchanger API

### 2.1 核心方法

```java
public class Exchanger<V> {
    /**
     * 交换数据（阻塞）
     * - 等待另一个线程到达交换点
     * - 交换数据
     * - 返回对方的数据
     * 
     * @param x 要交换的数据
     * @return 对方交换的数据
     */
    public V exchange(V x) throws InterruptedException;
    
    /**
     * 超时交换
     * - 在指定时间内等待另一个线程
     * - 超时抛TimeoutException
     * 
     * @param x 要交换的数据
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 对方交换的数据
     */
    public V exchange(V x, long timeout, TimeUnit unit)
        throws InterruptedException, TimeoutException;
}
```

### 2.2 标准使用模式

```java
// 模式1：基本使用
Exchanger<String> exchanger = new Exchanger<>();

// 线程1
new Thread(() -> {
    try {
        String data1 = "来自线程1的数据";
        String data2 = exchanger.exchange(data1);
        System.out.println("线程1收到：" + data2);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}).start();

// 线程2
new Thread(() -> {
    try {
        String data1 = "来自线程2的数据";
        String data2 = exchanger.exchange(data1);
        System.out.println("线程2收到：" + data2);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}).start();

// 模式2：超时交换
try {
    String received = exchanger.exchange(data, 5, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    System.out.println("交换超时");
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

---

## 三、实现原理

### 3.1 工作流程

```
Exchanger工作流程：

1. 线程1调用exchange(A)：
   - 将数据A放入交换槽
   - 等待线程2

2. 线程2调用exchange(B)：
   - 将数据B放入交换槽
   - 取出数据A
   - 唤醒线程1

3. 线程1被唤醒：
   - 取出数据B
   - 返回

流程图：
线程1: exchange(A) → 等待
线程2: exchange(B) → 取A，放B → 唤醒线程1
线程1: 取B → 返回B
线程2: 返回A

结果：
线程1收到B
线程2收到A
```

### 3.2 内部实现

```java
// Exchanger的内部实现（简化版）

public class Exchanger<V> {
    // 交换槽
    private static final class Node {
        Object item;    // 要交换的数据
        Object match;   // 匹配的数据
        Thread parked;  // 等待的线程
    }
    
    // 交换槽数组（用于减少竞争）
    private volatile Node[] arena;
    
    // 单槽交换
    private final Node slot = new Node();
    
    public V exchange(V x) throws InterruptedException {
        Node node = slot;
        
        // 尝试快速交换
        if (node.item == null) {
            // 第一个到达
            node.item = x;
            node.parked = Thread.currentThread();
            
            // 等待另一个线程
            while (node.match == null) {
                LockSupport.park(this);
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
            
            // 获取匹配的数据
            V result = (V) node.match;
            node.item = null;
            node.match = null;
            return result;
        } else {
            // 第二个到达
            Object item = node.item;
            node.match = x;
            
            // 唤醒第一个线程
            LockSupport.unpark(node.parked);
            
            return (V) item;
        }
    }
}
```

---

## 四、使用示例

### 4.1 数据校对

```java
public class DataVerificationExample {
    public static void main(String[] args) {
        Exchanger<String> exchanger = new Exchanger<>();
        
        // 生产者线程
        new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    String data = "数据-" + i;
                    System.out.println("生产者生成：" + data);
                    
                    // 交换数据，等待校验结果
                    String result = exchanger.exchange(data);
                    System.out.println("生产者收到校验结果：" + result);
                    
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Producer").start();
        
        // 校验者线程
        new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    // 接收数据
                    String data = exchanger.exchange(null);
                    System.out.println("校验者收到：" + data);
                    
                    // 校验数据
                    Thread.sleep(500);
                    String result = data + "-已校验";
                    
                    // 返回结果
                    exchanger.exchange(result);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Verifier").start();
    }
}
```

### 4.2 缓冲区交换

```java
public class BufferExchangeExample {
    static class Buffer {
        private final List<String> data = new ArrayList<>();
        
        public void add(String item) {
            data.add(item);
        }
        
        public List<String> getData() {
            return new ArrayList<>(data);
        }
        
        public void clear() {
            data.clear();
        }
        
        public boolean isFull() {
            return data.size() >= 10;
        }
    }
    
    public static void main(String[] args) {
        Exchanger<Buffer> exchanger = new Exchanger<>();
        
        // 生产者
        new Thread(() -> {
            Buffer currentBuffer = new Buffer();
            try {
                for (int i = 0; i < 50; i++) {
                    currentBuffer.add("Item-" + i);
                    
                    if (currentBuffer.isFull()) {
                        System.out.println("生产者：缓冲区满，交换");
                        currentBuffer = exchanger.exchange(currentBuffer);
                        currentBuffer.clear();
                    }
                    
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Producer").start();
        
        // 消费者
        new Thread(() -> {
            Buffer currentBuffer = new Buffer();
            try {
                while (true) {
                    currentBuffer = exchanger.exchange(currentBuffer);
                    System.out.println("消费者：收到" + 
                        currentBuffer.getData().size() + "个数据");
                    
                    // 处理数据
                    for (String item : currentBuffer.getData()) {
                        System.out.println("  处理：" + item);
                    }
                    
                    currentBuffer.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Consumer").start();
    }
}
```

### 4.3 遗传算法

```java
public class GeneticAlgorithmExample {
    static class Gene {
        private final int[] data;
        
        public Gene(int size) {
            this.data = new int[size];
            for (int i = 0; i < size; i++) {
                data[i] = (int) (Math.random() * 100);
            }
        }
        
        public Gene crossover(Gene other) {
            Gene child = new Gene(data.length);
            for (int i = 0; i < data.length; i++) {
                child.data[i] = (i % 2 == 0) ? this.data[i] : other.data[i];
            }
            return child;
        }
        
        public int getFitness() {
            int sum = 0;
            for (int value : data) {
                sum += value;
            }
            return sum;
        }
        
        @Override
        public String toString() {
            return "Gene{fitness=" + getFitness() + "}";
        }
    }
    
    public static void main(String[] args) {
        Exchanger<Gene> exchanger = new Exchanger<>();
        
        // 进化线程1
        new Thread(() -> {
            Gene gene = new Gene(10);
            try {
                for (int generation = 0; generation < 5; generation++) {
                    System.out.println("线程1第" + generation + "代：" + gene);
                    
                    // 交换基因
                    Gene otherGene = exchanger.exchange(gene);
                    
                    // 交叉产生新基因
                    gene = gene.crossover(otherGene);
                    
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Evolution-1").start();
        
        // 进化线程2
        new Thread(() -> {
            Gene gene = new Gene(10);
            try {
                for (int generation = 0; generation < 5; generation++) {
                    System.out.println("线程2第" + generation + "代：" + gene);
                    
                    // 交换基因
                    Gene otherGene = exchanger.exchange(gene);
                    
                    // 交叉产生新基因
                    gene = gene.crossover(otherGene);
                    
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Evolution-2").start();
    }
}
```

---

## 五、高级用法

### 5.1 超时交换

```java
public class TimeoutExchangeExample {
    public static void main(String[] args) {
        Exchanger<String> exchanger = new Exchanger<>();
        
        // 线程1：正常交换
        new Thread(() -> {
            try {
                String data = exchanger.exchange("数据1", 5, TimeUnit.SECONDS);
                System.out.println("线程1收到：" + data);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (TimeoutException e) {
                System.out.println("线程1：交换超时");
            }
        }).start();
        
        // 线程2：延迟交换
        new Thread(() -> {
            try {
                Thread.sleep(3000); // 延迟3秒
                String data = exchanger.exchange("数据2", 5, TimeUnit.SECONDS);
                System.out.println("线程2收到：" + data);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (TimeoutException e) {
                System.out.println("线程2：交换超时");
            }
        }).start();
    }
}
```

### 5.2 多次交换

```java
public class MultipleExchangeExample {
    public static void main(String[] args) {
        Exchanger<Integer> exchanger = new Exchanger<>();
        
        // 线程1
        new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    int received = exchanger.exchange(i);
                    System.out.println("线程1：发送" + i + "，收到" + received);
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        // 线程2
        new Thread(() -> {
            try {
                for (int i = 100; i < 105; i++) {
                    int received = exchanger.exchange(i);
                    System.out.println("线程2：发送" + i + "，收到" + received);
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}
```

---

## 六、常见陷阱

### 6.1 只有一个线程调用

```java
// ❌ 错误：只有一个线程调用exchange()
Exchanger<String> exchanger = new Exchanger<>();

new Thread(() -> {
    try {
        String data = exchanger.exchange("数据");
        // 永远等待，因为没有另一个线程
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}).start();

// ✅ 正确：必须有两个线程
new Thread(() -> {
    try {
        String data = exchanger.exchange("数据1");
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}).start();

new Thread(() -> {
    try {
        String data = exchanger.exchange("数据2");
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}).start();
```

### 6.2 超过两个线程

```java
// ❌ 错误：超过两个线程使用同一个Exchanger
Exchanger<String> exchanger = new Exchanger<>();

for (int i = 0; i < 3; i++) {
    new Thread(() -> {
        try {
            String data = exchanger.exchange("数据");
            // 行为不可预测
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }).start();
}

// ✅ 正确：每对线程使用独立的Exchanger
Exchanger<String> exchanger1 = new Exchanger<>();
Exchanger<String> exchanger2 = new Exchanger<>();
// 线程1和线程2使用exchanger1
// 线程3和线程4使用exchanger2
```

### 6.3 忘记处理超时

```java
// ❌ 错误：不处理TimeoutException
try {
    String data = exchanger.exchange("数据", 5, TimeUnit.SECONDS);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
// 编译错误：未捕获TimeoutException

// ✅ 正确：处理超时异常
try {
    String data = exchanger.exchange("数据", 5, TimeUnit.SECONDS);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
} catch (TimeoutException e) {
    System.out.println("交换超时");
}
```

---

## 七、性能考虑

### 7.1 性能特点

```
Exchanger的性能：

优点：
✅ 无锁设计（CAS）
✅ 适合两个线程交换数据
✅ 避免共享数据竞争

缺点：
❌ 只能两个线程使用
❌ 阻塞等待
❌ 不适合多对多场景
```

### 7.2 适用场景

```
✅ 适合使用：
- 两个线程交换数据
- 数据校对
- 缓冲区交换
- 遗传算法

❌ 不适合使用：
- 多个线程交换数据（用BlockingQueue）
- 单向传递数据（用BlockingQueue）
- 不需要等待对方
```

---

## 八、总结

### 8.1 核心要点

1. **定义**：交换器，两个线程交换数据
2. **核心方法**：exchange()交换数据
3. **特点**：一对一，双向传递
4. **实现**：基于CAS的无锁设计
5. **场景**：数据校对、缓冲区交换、遗传算法

### 8.2 使用建议

```
✅ 必须遵守：
- 必须有两个线程调用exchange()
- 处理InterruptedException
- 使用超时避免永久等待

✅ 推荐做法：
- 每对线程使用独立的Exchanger
- 使用超时机制
- 注意数据的不可变性
```

### 8.3 思考题

1. **Exchanger和BlockingQueue有什么区别？**
2. **为什么Exchanger只能两个线程使用？**
3. **如何避免Exchanger永久等待？**
4. **Exchanger适合什么场景？**

---

**下一章预告**：我们将学习Phaser（分阶段器）的使用。

---

**参考资料**：
- 《Java并发编程实战》第5章
- 《Java并发编程的艺术》第8章
- Exchanger API文档
