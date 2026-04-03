# LinkedList最佳实践与高级应用

## 1. 正确的使用方式

### 1.1 作为List使用

```java
// ✅ 正确：顺序遍历
List<String> list = new LinkedList<>();
list.add("A");
list.add("B");
list.add("C");

for (String item : list) {
    System.out.println(item);
}

// ❌ 错误：随机访问
for (int i = 0; i < list.size(); i++) {
    String item = list.get(i);  // O(n)，性能极差
}
```

---

### 1.2 作为Queue使用

```java
// ✅ 推荐：使用Queue接口
Queue<Task> queue = new LinkedList<>();

// 入队
queue.offer(new Task("task1"));
queue.offer(new Task("task2"));

// 出队
Task task = queue.poll();

// 查看队首
Task peek = queue.peek();
```

**Queue方法对比**：

| 操作 | 抛异常 | 返回特殊值 |
|------|-------|----------|
| **插入** | add(e) | offer(e) |
| **删除** | remove() | poll() |
| **查看** | element() | peek() |

**建议**：使用返回特殊值的方法（offer、poll、peek），更安全。

---

### 1.3 作为Deque使用

```java
// ✅ 推荐：使用Deque接口
Deque<String> deque = new LinkedList<>();

// 头部操作
deque.addFirst("A");
deque.removeFirst();
deque.getFirst();

// 尾部操作
deque.addLast("B");
deque.removeLast();
deque.getLast();

// 作为栈使用
deque.push("C");  // 等价于addFirst
deque.pop();      // 等价于removeFirst
```

---

### 1.4 作为Stack使用

```java
// ❌ 不推荐：使用Stack类（继承自Vector，性能差）
Stack<String> stack = new Stack<>();

// ✅ 推荐：使用Deque接口
Deque<String> stack = new LinkedList<>();

// 入栈
stack.push("A");
stack.push("B");
stack.push("C");

// 出栈
String top = stack.pop();  // "C"

// 查看栈顶
String peek = stack.peek();  // "B"
```

---

## 2. 常见陷阱与解决方案

### 2.1 陷阱1：使用for循环+get遍历

**问题**：

```java
// ❌ 错误：时间复杂度O(n²)
LinkedList<String> list = new LinkedList<>();
for (int i = 0; i < list.size(); i++) {
    String item = list.get(i);  // 每次都要遍历
}
```

**解决方案**：

```java
// ✅ 正确：使用迭代器，时间复杂度O(n)
for (String item : list) {
    System.out.println(item);
}

// ✅ 正确：使用迭代器
Iterator<String> iterator = list.iterator();
while (iterator.hasNext()) {
    String item = iterator.next();
    System.out.println(item);
}

// ✅ 正确：JDK 8的forEach
list.forEach(item -> System.out.println(item));
```

---

### 2.2 陷阱2：频繁在中间位置插入删除

**问题**：

```java
// ❌ 错误：需要先遍历到指定位置
LinkedList<String> list = new LinkedList<>();
for (int i = 0; i < 10000; i++) {
    list.add(5000, "element");  // 每次都要遍历到5000
}
```

**解决方案**：

```java
// ✅ 正确：使用ArrayList
List<String> list = new ArrayList<>();
for (int i = 0; i < 10000; i++) {
    list.add(5000, "element");
}

// ✅ 正确：如果必须用LinkedList，使用ListIterator
LinkedList<String> list = new LinkedList<>();
ListIterator<String> iterator = list.listIterator(5000);
for (int i = 0; i < 10000; i++) {
    iterator.add("element");
}
```

---

### 2.3 陷阱3：没有使用合适的接口

**问题**：

```java
// ❌ 不好：使用具体类
LinkedList<String> list = new LinkedList<>();
```

**解决方案**：

```java
// ✅ 好：使用接口
List<String> list = new LinkedList<>();

// ✅ 更好：根据用途选择接口
Queue<String> queue = new LinkedList<>();
Deque<String> deque = new LinkedList<>();
```

**优势**：
- 面向接口编程
- 易于切换实现
- 代码更灵活

---

### 2.4 陷阱4：在迭代时直接修改

**问题**：

```java
// ❌ 错误：抛出ConcurrentModificationException
LinkedList<String> list = new LinkedList<>();
list.add("A");
list.add("B");
list.add("C");

for (String item : list) {
    if (item.equals("B")) {
        list.remove(item);  // 抛出异常
    }
}
```

**解决方案**：

```java
// ✅ 正确：使用迭代器的remove方法
Iterator<String> iterator = list.iterator();
while (iterator.hasNext()) {
    String item = iterator.next();
    if (item.equals("B")) {
        iterator.remove();  // 正确
    }
}

// ✅ 正确：JDK 8的removeIf
list.removeIf(item -> item.equals("B"));

// ✅ 正确：先收集要删除的元素
List<String> toRemove = new ArrayList<>();
for (String item : list) {
    if (item.equals("B")) {
        toRemove.add(item);
    }
}
list.removeAll(toRemove);
```

---

## 3. 高级应用场景

### 3.1 实现LRU缓存

**需求**：实现一个LRU（Least Recently Used）缓存。

**实现**：

```java
public class LRUCache<K, V> {
    private final int capacity;
    private final Map<K, Node<K, V>> map;
    private final LinkedList<Node<K, V>> list;
    
    static class Node<K, V> {
        K key;
        V value;
        
        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
    
    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.map = new HashMap<>();
        this.list = new LinkedList<>();
    }
    
    public V get(K key) {
        Node<K, V> node = map.get(key);
        if (node == null) {
            return null;
        }
        
        // 移动到链表头部（最近使用）
        list.remove(node);
        list.addFirst(node);
        
        return node.value;
    }
    
    public void put(K key, V value) {
        Node<K, V> node = map.get(key);
        
        if (node != null) {
            // 更新值
            node.value = value;
            // 移动到链表头部
            list.remove(node);
            list.addFirst(node);
        } else {
            // 新增节点
            node = new Node<>(key, value);
            
            if (list.size() >= capacity) {
                // 删除链表尾部（最久未使用）
                Node<K, V> last = list.removeLast();
                map.remove(last.key);
            }
            
            list.addFirst(node);
            map.put(key, node);
        }
    }
    
    public int size() {
        return list.size();
    }
}
```

**注意**：实际项目中更推荐使用LinkedHashMap实现LRU缓存，性能更好。

---

### 3.2 实现浏览器历史记录

**需求**：实现浏览器的前进、后退功能。

**实现**：

```java
public class BrowserHistory {
    private final LinkedList<String> history;
    private int currentIndex;
    
    public BrowserHistory() {
        this.history = new LinkedList<>();
        this.currentIndex = -1;
    }
    
    // 访问新页面
    public void visit(String url) {
        // 删除当前位置之后的所有记录
        while (history.size() > currentIndex + 1) {
            history.removeLast();
        }
        
        // 添加新页面
        history.addLast(url);
        currentIndex++;
        
        System.out.println("访问: " + url);
    }
    
    // 后退
    public String back() {
        if (currentIndex > 0) {
            currentIndex--;
            String url = history.get(currentIndex);
            System.out.println("后退到: " + url);
            return url;
        }
        System.out.println("已经是第一页");
        return null;
    }
    
    // 前进
    public String forward() {
        if (currentIndex < history.size() - 1) {
            currentIndex++;
            String url = history.get(currentIndex);
            System.out.println("前进到: " + url);
            return url;
        }
        System.out.println("已经是最后一页");
        return null;
    }
    
    // 获取当前页面
    public String current() {
        if (currentIndex >= 0 && currentIndex < history.size()) {
            return history.get(currentIndex);
        }
        return null;
    }
    
    // 打印历史记录
    public void printHistory() {
        System.out.println("历史记录:");
        for (int i = 0; i < history.size(); i++) {
            String prefix = (i == currentIndex) ? "-> " : "   ";
            System.out.println(prefix + history.get(i));
        }
    }
}

// 使用示例
public class BrowserHistoryDemo {
    public static void main(String[] args) {
        BrowserHistory browser = new BrowserHistory();
        
        browser.visit("https://www.google.com");
        browser.visit("https://www.github.com");
        browser.visit("https://www.stackoverflow.com");
        
        browser.printHistory();
        // 历史记录:
        //    https://www.google.com
        //    https://www.github.com
        // -> https://www.stackoverflow.com
        
        browser.back();  // 后退到: https://www.github.com
        browser.back();  // 后退到: https://www.google.com
        
        browser.forward();  // 前进到: https://www.github.com
        
        browser.visit("https://www.baidu.com");  // 访问新页面，后面的记录被清除
        
        browser.printHistory();
        // 历史记录:
        //    https://www.google.com
        //    https://www.github.com
        // -> https://www.baidu.com
    }
}
```

---

### 3.3 实现任务队列

**需求**：实现一个支持优先级的任务队列。

**实现**：

```java
public class TaskQueue {
    private final LinkedList<Task> queue;
    
    static class Task {
        String name;
        int priority;  // 优先级：1-高，2-中，3-低
        
        Task(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }
        
        @Override
        public String toString() {
            return name + "(优先级:" + priority + ")";
        }
    }
    
    public TaskQueue() {
        this.queue = new LinkedList<>();
    }
    
    // 添加任务
    public void addTask(Task task) {
        if (task.priority == 1) {
            // 高优先级任务插入到队首
            queue.addFirst(task);
        } else {
            // 普通任务插入到队尾
            queue.addLast(task);
        }
        System.out.println("添加任务: " + task);
    }
    
    // 获取下一个任务
    public Task nextTask() {
        Task task = queue.pollFirst();
        if (task != null) {
            System.out.println("执行任务: " + task);
        }
        return task;
    }
    
    // 取消最后一个任务
    public Task cancelLastTask() {
        Task task = queue.pollLast();
        if (task != null) {
            System.out.println("取消任务: " + task);
        }
        return task;
    }
    
    // 获取任务数量
    public int size() {
        return queue.size();
    }
    
    // 打印所有任务
    public void printTasks() {
        System.out.println("当前任务队列:");
        for (Task task : queue) {
            System.out.println("  " + task);
        }
    }
}

// 使用示例
public class TaskQueueDemo {
    public static void main(String[] args) {
        TaskQueue taskQueue = new TaskQueue();
        
        taskQueue.addTask(new TaskQueue.Task("任务1", 2));
        taskQueue.addTask(new TaskQueue.Task("任务2", 3));
        taskQueue.addTask(new TaskQueue.Task("紧急任务", 1));
        taskQueue.addTask(new TaskQueue.Task("任务3", 2));
        
        taskQueue.printTasks();
        // 当前任务队列:
        //   紧急任务(优先级:1)
        //   任务1(优先级:2)
        //   任务2(优先级:3)
        //   任务3(优先级:2)
        
        taskQueue.nextTask();  // 执行任务: 紧急任务(优先级:1)
        taskQueue.nextTask();  // 执行任务: 任务1(优先级:2)
        
        taskQueue.cancelLastTask();  // 取消任务: 任务3(优先级:2)
        
        taskQueue.printTasks();
        // 当前任务队列:
        //   任务2(优先级:3)
    }
}
```

---

### 3.4 实现撤销/重做功能

**需求**：实现文本编辑器的撤销/重做功能。

**实现**：

```java
public class TextEditor {
    private final LinkedList<String> history;  // 历史记录
    private int currentIndex;  // 当前位置
    
    public TextEditor() {
        this.history = new LinkedList<>();
        this.currentIndex = -1;
        // 初始状态
        addState("");
    }
    
    // 添加新状态
    private void addState(String text) {
        // 删除当前位置之后的所有记录
        while (history.size() > currentIndex + 1) {
            history.removeLast();
        }
        
        history.addLast(text);
        currentIndex++;
    }
    
    // 输入文本
    public void type(String text) {
        String current = history.get(currentIndex);
        String newText = current + text;
        addState(newText);
        System.out.println("输入: " + text + " -> " + newText);
    }
    
    // 删除文本
    public void delete(int count) {
        String current = history.get(currentIndex);
        if (current.length() >= count) {
            String newText = current.substring(0, current.length() - count);
            addState(newText);
            System.out.println("删除 " + count + " 个字符 -> " + newText);
        }
    }
    
    // 撤销
    public void undo() {
        if (currentIndex > 0) {
            currentIndex--;
            System.out.println("撤销 -> " + history.get(currentIndex));
        } else {
            System.out.println("无法撤销");
        }
    }
    
    // 重做
    public void redo() {
        if (currentIndex < history.size() - 1) {
            currentIndex++;
            System.out.println("重做 -> " + history.get(currentIndex));
        } else {
            System.out.println("无法重做");
        }
    }
    
    // 获取当前文本
    public String getText() {
        return history.get(currentIndex);
    }
}

// 使用示例
public class TextEditorDemo {
    public static void main(String[] args) {
        TextEditor editor = new TextEditor();
        
        editor.type("Hello");     // 输入: Hello -> Hello
        editor.type(" World");    // 输入:  World -> Hello World
        editor.delete(5);         // 删除 5 个字符 -> Hello 
        editor.type("!");         // 输入: ! -> Hello!
        
        editor.undo();            // 撤销 -> Hello 
        editor.undo();            // 撤销 -> Hello World
        editor.undo();            // 撤销 -> Hello
        
        editor.redo();            // 重做 -> Hello World
        editor.redo();            // 重做 -> Hello 
        
        editor.type(" Java");     // 输入:  Java -> Hello  Java
        
        System.out.println("最终文本: " + editor.getText());
        // 最终文本: Hello  Java
    }
}
```

---

## 4. 性能优化技巧

### 4.1 使用迭代器遍历

```java
// ❌ 错误：O(n²)
for (int i = 0; i < list.size(); i++) {
    String item = list.get(i);
}

// ✅ 正确：O(n)
for (String item : list) {
    // 处理item
}
```

---

### 4.2 批量操作使用addAll

```java
// ❌ 不好：多次方法调用
for (String item : collection) {
    list.add(item);
}

// ✅ 好：批量添加
list.addAll(collection);
```

---

### 4.3 使用合适的方法

```java
// ❌ 不好：使用索引方法
list.add(0, element);  // 需要遍历
list.remove(0);        // 需要遍历

// ✅ 好：使用头尾方法
list.addFirst(element);  // O(1)
list.removeFirst();      // O(1)
```

---

### 4.4 考虑使用ArrayDeque

**场景**：实现队列或栈

```java
// LinkedList
Deque<String> deque1 = new LinkedList<>();

// ArrayDeque（通常性能更好）
Deque<String> deque2 = new ArrayDeque<>();
```

**ArrayDeque的优势**：
- 内存连续，缓存友好
- 不需要存储prev和next指针
- 大多数操作性能更好

**性能对比**：

| 操作 | LinkedList | ArrayDeque |
|------|-----------|-----------|
| **addFirst** | 快 | 更快 |
| **addLast** | 快 | 更快 |
| **removeFirst** | 快 | 更快 |
| **removeLast** | 快 | 更快 |
| **内存占用** | 大 | 小 |

**建议**：
- 实现队列或栈：优先使用ArrayDeque
- 需要List接口：使用LinkedList

---

## 5. 最佳实践清单

### 5.1 使用LinkedList

- ✅ 使用迭代器遍历，不要用for循环+get
- ✅ 使用合适的接口（List、Queue、Deque）
- ✅ 使用头尾操作方法（addFirst、addLast等）
- ✅ 批量操作使用addAll
- ✅ 在迭代时修改使用迭代器的remove方法

---

### 5.2 不要使用LinkedList

- ❌ 不要频繁随机访问
- ❌ 不要在中间位置频繁插入删除
- ❌ 不要用for循环+get遍历
- ❌ 不要在内存敏感的场景使用

---

### 5.3 选择合适的实现

```java
// 需要List接口 + 随机访问
List<String> list = new ArrayList<>();

// 需要List接口 + 头部操作
List<String> list = new LinkedList<>();

// 需要Queue接口
Queue<String> queue = new LinkedList<>();
// 或者
Queue<String> queue = new ArrayDeque<>();  // 通常更好

// 需要Deque接口
Deque<String> deque = new ArrayDeque<>();  // 推荐
// 或者
Deque<String> deque = new LinkedList<>();
```

---

## 6. 总结

### 6.1 核心要点

1. **正确遍历**：使用迭代器，不要用for循环+get
2. **合适场景**：队列、栈、双端队列、头部操作
3. **避免陷阱**：随机访问、中间操作、内存敏感
4. **性能优化**：使用头尾方法、批量操作、考虑ArrayDeque

---

### 6.2 使用建议

**适合使用LinkedList**：
- ✅ 实现队列（Queue）
- ✅ 实现栈（Stack）
- ✅ 实现双端队列（Deque）
- ✅ 频繁在头部插入删除

**不适合使用LinkedList**：
- ❌ 频繁随机访问
- ❌ 频繁在中间位置操作
- ❌ 内存敏感
- ❌ 需要高性能遍历

**默认选择**：
- List接口：ArrayList
- Queue接口：ArrayDeque
- Deque接口：ArrayDeque
- 特殊需求：LinkedList

---

### 6.3 继续学习

完成LinkedList的学习后，建议继续学习：

1. **LinkedHashMap**：结合HashMap和LinkedList的优点
2. **ArrayDeque**：基于数组的双端队列，性能更好
3. **ConcurrentLinkedQueue**：并发队列
4. **PriorityQueue**：优先级队列

---

**恭喜你完成了LinkedList的深度学习！🎉**

**下一步**：学习LinkedHashMap，了解如何保持插入顺序和实现LRU缓存。
