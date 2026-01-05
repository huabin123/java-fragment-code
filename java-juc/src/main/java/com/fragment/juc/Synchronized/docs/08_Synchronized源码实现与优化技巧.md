# Synchronized源码实现与优化技巧

## 1. Synchronized在JVM中是如何实现的？

### 1.1 问题1：从Java代码到JVM底层经历了什么？

```
Java代码
    ↓
字节码（.class文件）
    ↓
JVM解释执行/JIT编译
    ↓
Monitor机制
    ↓
操作系统互斥锁（Mutex）
```

---

### 1.2 字节码层面的实现

#### 同步代码块的字节码

**Java代码**：

```java
public class SynchronizedBlock {
    private final Object lock = new Object();
    private int count = 0;
    
    public void increment() {
        synchronized(lock) {
            count++;
        }
    }
}
```

**字节码**：

```bash
javap -c -v SynchronizedBlock.class
```

```
public void increment();
  descriptor: ()V
  flags: ACC_PUBLIC
  Code:
    stack=3, locals=3, args_size=1
       0: aload_0                    // 加载this
       1: getfield      #2           // 获取lock字段
       4: dup                        // 复制lock引用
       5: astore_1                   // 存储到局部变量表
       6: monitorenter               // ← 进入Monitor（获取锁）
       7: aload_0                    // 加载this
       8: dup                        // 复制this
       9: getfield      #3           // 获取count字段
      12: iconst_1                   // 加载常量1
      13: iadd                       // count + 1
      14: putfield      #3           // 写回count字段
      17: aload_1                    // 加载lock
      18: monitorexit                // ← 退出Monitor（释放锁）
      19: goto          27           // 跳转到return
      22: astore_2                   // 异常处理
      23: aload_1                    // 加载lock
      24: monitorexit                // ← 异常时也要释放锁
      25: aload_2                    // 加载异常
      26: athrow                     // 抛出异常
      27: return
    Exception table:
       from    to  target type
           7    19    22   any      // 异常处理表
          22    25    22   any
```

**关键点**：

1. **monitorenter**：进入Monitor，获取锁
2. **monitorexit**：退出Monitor，释放锁
3. **异常处理**：即使发生异常，也会执行monitorexit释放锁
4. **两个monitorexit**：一个正常退出，一个异常退出

---

#### 同步方法的字节码

**Java代码**：

```java
public class SynchronizedMethod {
    private int count = 0;
    
    public synchronized void increment() {
        count++;
    }
}
```

**字节码**：

```
public synchronized void increment();
  descriptor: ()V
  flags: ACC_PUBLIC, ACC_SYNCHRONIZED  // ← ACC_SYNCHRONIZED标志
  Code:
    stack=3, locals=1, args_size=1
       0: aload_0
       1: dup
       2: getfield      #2
       5: iconst_1
       6: iadd
       7: putfield      #2
      10: return
```

**关键点**：

1. **ACC_SYNCHRONIZED标志**：方法访问标志中包含ACC_SYNCHRONIZED
2. **没有monitorenter/monitorexit指令**：JVM在方法调用和返回时隐式处理
3. **锁对象**：实例方法锁this，静态方法锁Class对象

---

### 1.3 问题2：monitorenter和monitorexit做了什么？

**monitorenter伪代码**：

```c++
// HotSpot源码：bytecodeInterpreter.cpp
CASE(_monitorenter): {
    oop lockee = STACK_OBJECT(-1);  // 获取锁对象
    
    // 1. 检查锁对象是否为null
    if (lockee == NULL) {
        throw NullPointerException();
    }
    
    // 2. 尝试获取锁
    BasicObjectLock* limit = istate->monitor_base();
    BasicObjectLock* most_recent = (BasicObjectLock*) istate->stack_base();
    BasicObjectLock* entry = NULL;
    
    // 3. 查找空闲的锁记录
    while (most_recent != limit) {
        if (most_recent->obj() == NULL) {
            entry = most_recent;
        } else if (most_recent->obj() == lockee) {
            // 可重入：已经持有该锁
            break;
        }
        most_recent++;
    }
    
    if (entry != NULL) {
        // 4. 设置锁记录
        entry->set_obj(lockee);
        
        // 5. 尝试CAS获取锁
        markOop mark = lockee->mark();
        markOop displaced_header = mark->set_unlocked();
        entry->lock()->set_displaced_header(displaced_header);
        
        if (mark == (markOop) Atomic::cmpxchg_ptr(entry, lockee->mark_addr(), mark)) {
            // CAS成功，获取轻量级锁
            continue;
        } else {
            // CAS失败，锁膨胀为重量级锁
            CALL_VM(InterpreterRuntime::monitorenter(THREAD, entry), handle_exception);
        }
    }
}
```

**monitorexit伪代码**：

```c++
// HotSpot源码：bytecodeInterpreter.cpp
CASE(_monitorexit): {
    oop lockee = STACK_OBJECT(-1);  // 获取锁对象
    
    // 1. 检查锁对象是否为null
    if (lockee == NULL) {
        throw NullPointerException();
    }
    
    // 2. 查找锁记录
    BasicObjectLock* limit = istate->monitor_base();
    BasicObjectLock* most_recent = (BasicObjectLock*) istate->stack_base();
    
    while (most_recent != limit) {
        if (most_recent->obj() == lockee) {
            // 找到锁记录
            BasicObjectLock* entry = most_recent;
            markOop header = entry->lock()->displaced_header();
            entry->set_obj(NULL);
            
            // 3. 尝试CAS释放锁
            if (header != NULL) {
                if (lockee->mark() == (markOop) entry) {
                    if (Atomic::cmpxchg_ptr(header, lockee->mark_addr(), entry) != entry) {
                        // CAS失败，需要重量级锁的释放流程
                        CALL_VM(InterpreterRuntime::monitorexit(THREAD, entry), handle_exception);
                    }
                }
            }
            break;
        }
        most_recent++;
    }
}
```

**核心流程**：

```
monitorenter
    ↓
检查锁对象
    ↓
查找锁记录
    ↓
    ┌──────────┴──────────┐
    ↓                     ↓
可重入（已持有锁）      首次获取
    ↓                     ↓
计数器+1              CAS尝试获取
    ↓                     ↓
    └──────────┬──────────┘
               ↓
            ┌──┴──┐
            ↓     ↓
        成功    失败
            ↓     ↓
        轻量级锁  锁膨胀
                  ↓
              重量级锁
```

---

## 2. ObjectMonitor源码实现

### 2.1 ObjectMonitor的数据结构

**源码位置**：`hotspot/src/share/vm/runtime/objectMonitor.hpp`

```c++
class ObjectMonitor {
private:
    // 锁的所有者线程
    void* volatile _owner;
    
    // 重入次数
    volatile intptr_t _recursions;
    
    // 等待队列（EntryList）
    ObjectWaiter* volatile _EntryList;
    
    // 条件等待队列（WaitSet）
    ObjectWaiter* volatile _WaitSet;
    
    // 等待线程数
    volatile int _WaitSetLock;
    
    // 自旋次数
    volatile int _SpinDuration;
    
public:
    // 进入Monitor
    void enter(TRAPS);
    
    // 退出Monitor
    void exit(bool not_suspended, TRAPS);
    
    // wait方法
    void wait(jlong millis, bool interruptable, TRAPS);
    
    // notify方法
    void notify(TRAPS);
    
    // notifyAll方法
    void notifyAll(TRAPS);
};
```

**数据结构图**：

```
ObjectMonitor
    ↓
┌─────────────────────────────────┐
│ _owner: Thread*                 │ ← 持有锁的线程
├─────────────────────────────────┤
│ _recursions: int                │ ← 重入次数
├─────────────────────────────────┤
│ _EntryList: ObjectWaiter*       │ ← 等待获取锁的线程队列
│   ↓                             │
│   Thread1 → Thread2 → Thread3   │
├─────────────────────────────────┤
│ _WaitSet: ObjectWaiter*         │ ← 调用wait()的线程队列
│   ↓                             │
│   Thread4 → Thread5             │
└─────────────────────────────────┘
```

---

### 2.2 enter方法源码分析

**源码位置**：`hotspot/src/share/vm/runtime/objectMonitor.cpp`

```c++
void ObjectMonitor::enter(TRAPS) {
    Thread* const Self = THREAD;
    void* cur;
    
    // 1. 快速路径：尝试CAS获取锁
    cur = Atomic::cmpxchg_ptr(Self, &_owner, NULL);
    if (cur == NULL) {
        // CAS成功，获取锁
        assert(_recursions == 0, "invariant");
        assert(_owner == Self, "invariant");
        return;
    }
    
    // 2. 可重入：当前线程已经持有锁
    if (cur == Self) {
        _recursions++;  // 重入次数+1
        return;
    }
    
    // 3. 尝试自旋获取锁
    if (Self->is_lock_owned((address)cur)) {
        assert(_recursions == 0, "internal state error");
        _recursions = 1;
        _owner = Self;
        return;
    }
    
    // 4. 自旋失败，进入等待队列
    EnterI(THREAD);
}

void ObjectMonitor::EnterI(TRAPS) {
    Thread* const Self = THREAD;
    
    // 1. 再次尝试获取锁
    if (TryLock(Self) > 0) {
        return;
    }
    
    // 2. 自旋等待
    if (TrySpin(Self) > 0) {
        return;
    }
    
    // 3. 创建ObjectWaiter节点
    ObjectWaiter node(Self);
    Self->_ParkEvent->reset();
    node._prev = (ObjectWaiter*) 0xBAD;
    node.TState = ObjectWaiter::TS_CXQ;
    
    // 4. 加入等待队列（_cxq）
    ObjectWaiter* nxt;
    for (;;) {
        node._next = nxt = _cxq;
        if (Atomic::cmpxchg_ptr(&node, &_cxq, nxt) == nxt) break;
        
        // CAS失败，重试前再次尝试获取锁
        if (TryLock(Self) > 0) {
            return;
        }
    }
    
    // 5. 阻塞等待
    for (;;) {
        if (TryLock(Self) > 0) break;
        
        // 阻塞当前线程
        if (_Responsible == Self || (SyncFlags & 1)) {
            Self->_ParkEvent->park((jlong) RecheckInterval);
        } else {
            Self->_ParkEvent->park();  // ← 调用操作系统的park，线程阻塞
        }
        
        // 被唤醒后，尝试获取锁
        TryLock(Self);
    }
    
    // 6. 获取锁成功，从等待队列移除
    UnlinkAfterAcquire(Self, &node);
}
```

**核心流程**：

```
enter()
    ↓
1. CAS尝试获取锁
    ↓
    ┌──────┴──────┐
    ↓             ↓
  成功          失败
    ↓             ↓
  返回      2. 检查可重入
              ↓
           ┌──┴──┐
           ↓     ↓
         是    否
           ↓     ↓
      计数器+1  3. 自旋等待
         返回     ↓
              ┌──┴──┐
              ↓     ↓
            成功  失败
              ↓     ↓
            返回  4. 加入等待队列
                    ↓
                5. 阻塞线程（park）
                    ↓
                6. 被唤醒后重试
```

---

### 2.3 exit方法源码分析

```c++
void ObjectMonitor::exit(bool not_suspended, TRAPS) {
    Thread* const Self = THREAD;
    
    // 1. 检查是否是锁的持有者
    if (THREAD != _owner) {
        if (THREAD->is_lock_owned((address) _owner)) {
            _owner = THREAD;
            _recursions = 0;
        } else {
            // 非法监视器状态异常
            TEVENT(Exit - Throw IMSX);
            throw IllegalMonitorStateException();
        }
    }
    
    // 2. 处理重入
    if (_recursions != 0) {
        _recursions--;  // 重入次数-1
        return;
    }
    
    // 3. 释放锁
    _Responsible = NULL;
    _recursions = 0;
    _owner = NULL;
    
    // 4. 唤醒等待线程
    ObjectWaiter* w = NULL;
    int QMode = Knob_QMode;
    
    if (QMode == 2 && _cxq != NULL) {
        // 从_cxq队列唤醒
        w = _cxq;
        _cxq = w->_next;
        w->_next = NULL;
    } else if (QMode == 3 && _cxq != NULL) {
        // 将_cxq队列转移到_EntryList
        w = _cxq;
        for (;;) {
            ObjectWaiter* u = _cxq;
            if (u == NULL) break;
            if (Atomic::cmpxchg_ptr(NULL, &_cxq, u) == u) {
                _EntryList = u;
                break;
            }
        }
    } else if (QMode == 4 && _cxq != NULL) {
        // 从_EntryList唤醒
        w = _EntryList;
        if (w != NULL) {
            _EntryList = w->_next;
        }
    }
    
    // 5. 唤醒线程
    if (w != NULL) {
        w->TState = ObjectWaiter::TS_ENTER;
        w->_prev = NULL;
        w->_next = NULL;
        
        // unpark唤醒线程
        ParkEvent* Trigger = w->_event;
        Trigger->unpark();  // ← 调用操作系统的unpark，唤醒线程
    }
}
```

**核心流程**：

```
exit()
    ↓
1. 检查是否是锁持有者
    ↓
    ┌──────┴──────┐
    ↓             ↓
   是            否
    ↓             ↓
2. 检查重入    抛出异常
    ↓
    ┌──────┴──────┐
    ↓             ↓
重入次数>0    重入次数=0
    ↓             ↓
计数器-1      3. 释放锁
  返回            ↓
            4. 选择唤醒策略
                ↓
            5. 唤醒等待线程（unpark）
```

---

### 2.4 wait/notify源码分析

**wait方法**：

```c++
void ObjectMonitor::wait(jlong millis, bool interruptible, TRAPS) {
    Thread* const Self = THREAD;
    
    // 1. 检查是否是锁的持有者
    if (THREAD != _owner) {
        throw IllegalMonitorStateException();
    }
    
    // 2. 创建ObjectWaiter节点
    ObjectWaiter node(Self);
    node.TState = ObjectWaiter::TS_WAIT;
    
    // 3. 加入WaitSet
    AddWaiter(&node);
    
    // 4. 释放锁
    intptr_t save = _recursions;  // 保存重入次数
    _recursions = 0;
    _owner = NULL;
    
    // 5. 唤醒EntryList中的线程
    exit(true, THREAD);
    
    // 6. 阻塞等待
    int ret = OS_OK;
    if (millis <= 0) {
        Self->_ParkEvent->park();  // 无限期等待
    } else {
        ret = Self->_ParkEvent->park(millis);  // 超时等待
    }
    
    // 7. 被唤醒后，重新获取锁
    enter(THREAD);
    
    // 8. 恢复重入次数
    _recursions = save;
}
```

**notify方法**：

```c++
void ObjectMonitor::notify(TRAPS) {
    // 1. 检查是否是锁的持有者
    if (THREAD != _owner) {
        throw IllegalMonitorStateException();
    }
    
    // 2. 从WaitSet中取出一个线程
    ObjectWaiter* iterator = _WaitSet;
    if (iterator != NULL) {
        // 3. 从WaitSet移除
        DequeueWaiter(&iterator);
        
        // 4. 加入EntryList
        iterator->TState = ObjectWaiter::TS_ENTER;
        iterator->_next = _EntryList;
        _EntryList = iterator;
        
        // 5. 唤醒线程
        iterator->_event->unpark();
    }
}
```

**notifyAll方法**：

```c++
void ObjectMonitor::notifyAll(TRAPS) {
    // 1. 检查是否是锁的持有者
    if (THREAD != _owner) {
        throw IllegalMonitorStateException();
    }
    
    // 2. 遍历WaitSet
    ObjectWaiter* iterator = _WaitSet;
    while (iterator != NULL) {
        ObjectWaiter* next = iterator->_next;
        
        // 3. 从WaitSet移除
        DequeueWaiter(&iterator);
        
        // 4. 加入EntryList
        iterator->TState = ObjectWaiter::TS_ENTER;
        iterator->_next = _EntryList;
        _EntryList = iterator;
        
        // 5. 唤醒线程
        iterator->_event->unpark();
        
        iterator = next;
    }
    
    _WaitSet = NULL;
}
```

**wait/notify流程图**：

```
wait()
    ↓
1. 检查锁持有者
    ↓
2. 加入WaitSet
    ↓
3. 释放锁
    ↓
4. 阻塞等待
    ↓
被notify()唤醒
    ↓
5. 从WaitSet移到EntryList
    ↓
6. 竞争锁
    ↓
7. 获取锁成功
    ↓
8. 继续执行

notify()
    ↓
1. 检查锁持有者
    ↓
2. 从WaitSet取出一个线程
    ↓
3. 移到EntryList
    ↓
4. unpark唤醒线程
```

---

## 3. 锁升级的源码实现

### 3.1 偏向锁的获取

**源码位置**：`hotspot/src/share/vm/interpreter/bytecodeInterpreter.cpp`

```c++
// 偏向锁的快速路径
if (mark->has_bias_pattern()) {
    // 1. 检查是否是偏向当前线程
    if (mark->bias_epoch() == klass->prototype_header()->bias_epoch() &&
        mark->biased_locker() == thread) {
        // 偏向锁命中，直接返回
        return;
    }
    
    // 2. 尝试重偏向
    markOop prototype_header = klass->prototype_header();
    if (prototype_header->has_bias_pattern()) {
        markOop biased_value = mark->set_bias_pattern();
        markOop res_mark = (markOop) Atomic::cmpxchg_ptr(
            biased_value, obj->mark_addr(), mark);
        
        if (res_mark == mark) {
            // CAS成功，偏向到当前线程
            return;
        }
    }
    
    // 3. 偏向锁撤销
    revoke_bias(obj, false, false, thread);
}
```

**偏向锁撤销**：

```c++
void BiasedLocking::revoke_bias(oop obj, bool attempt_rebias, bool allow_rebias, JavaThread* requesting_thread) {
    markOop mark = obj->mark();
    
    // 1. 检查是否需要撤销
    if (!mark->has_bias_pattern()) {
        return;
    }
    
    // 2. 获取偏向的线程
    JavaThread* biased_thread = mark->biased_locker();
    
    if (biased_thread == NULL) {
        // 匿名偏向，尝试重偏向
        if (attempt_rebias) {
            markOop biased_value = mark->set_bias_pattern();
            obj->set_mark(biased_value);
        }
        return;
    }
    
    // 3. 检查偏向线程是否还活着
    bool thread_is_alive = false;
    if (biased_thread == requesting_thread) {
        thread_is_alive = true;
    } else {
        for (JavaThread* cur_thread = Threads::first(); cur_thread != NULL; cur_thread = cur_thread->next()) {
            if (cur_thread == biased_thread) {
                thread_is_alive = true;
                break;
            }
        }
    }
    
    if (!thread_is_alive) {
        // 线程已死，重偏向
        if (allow_rebias) {
            obj->set_mark(mark->set_bias_pattern());
        }
        return;
    }
    
    // 4. 撤销偏向锁，升级为轻量级锁
    markOop unbiased_prototype = mark->prototype()->set_age(mark->age());
    obj->set_mark(unbiased_prototype);
}
```

---

### 3.2 轻量级锁的获取

```c++
// 轻量级锁的获取
void ObjectSynchronizer::slow_enter(Handle obj, BasicLock* lock, TRAPS) {
    markOop mark = obj->mark();
    
    // 1. 检查是否是无锁状态
    if (mark->is_neutral()) {
        // 2. 在栈上分配Lock Record
        lock->set_displaced_header(mark);
        
        // 3. CAS尝试将Mark Word替换为指向Lock Record的指针
        if (mark == (markOop) Atomic::cmpxchg_ptr(lock, obj()->mark_addr(), mark)) {
            // CAS成功，获取轻量级锁
            return;
        }
        // CAS失败，进入锁膨胀流程
    } else if (mark->has_locker() && THREAD->is_lock_owned((address)mark->locker())) {
        // 可重入
        lock->set_displaced_header(NULL);
        return;
    }
    
    // 4. 锁膨胀为重量级锁
    lock->set_displaced_header(markOopDesc::unused_mark());
    ObjectSynchronizer::inflate(THREAD, obj())->enter(THREAD);
}
```

---

### 3.3 锁膨胀的实现

```c++
ObjectMonitor* ObjectSynchronizer::inflate(Thread* Self, oop object) {
    // 1. 快速路径：如果已经是重量级锁，直接返回
    for (;;) {
        const markOop mark = object->mark();
        
        // 已经是重量级锁
        if (mark->has_monitor()) {
            ObjectMonitor* inf = mark->monitor();
            return inf;
        }
        
        // 2. 正在膨胀中
        if (mark == markOopDesc::INFLATING()) {
            continue;
        }
        
        // 3. 轻量级锁膨胀
        if (mark->has_locker()) {
            // 分配ObjectMonitor
            ObjectMonitor* m = omAlloc(Self);
            
            // 设置ObjectMonitor
            m->Recycle();
            m->_Responsible = NULL;
            m->OwnerIsThread = 0;
            m->_recursions = 0;
            m->_SpinDuration = ObjectMonitor::Knob_SpinLimit;
            
            // CAS设置INFLATING标记
            markOop cmp = (markOop) Atomic::cmpxchg_ptr(markOopDesc::INFLATING(), object->mark_addr(), mark);
            if (cmp != mark) {
                omRelease(Self, m, true);
                continue;
            }
            
            // 复制Lock Record信息到ObjectMonitor
            markOop dmw = mark->displaced_mark_helper();
            m->set_header(dmw);
            
            // 设置owner
            m->set_owner(mark->locker());
            
            // 更新Mark Word为指向ObjectMonitor的指针
            object->set_mark(markOopDesc::encode(m));
            
            return m;
        }
        
        // 4. 无锁状态膨胀
        ObjectMonitor* m = omAlloc(Self);
        m->Recycle();
        m->set_header(mark);
        m->set_owner(NULL);
        m->set_object(object);
        m->OwnerIsThread = 1;
        m->_recursions = 0;
        
        // CAS更新Mark Word
        if (Atomic::cmpxchg_ptr(markOopDesc::encode(m), object->mark_addr(), mark) != mark) {
            m->set_object(NULL);
            m->set_owner(NULL);
            m->Recycle();
            omRelease(Self, m, true);
            continue;
        }
        
        return m;
    }
}
```

**锁膨胀流程**：

```
inflate()
    ↓
1. 检查当前锁状态
    ↓
    ┌────────┴────────┐
    ↓        ↓        ↓
重量级锁  轻量级锁  无锁
    ↓        ↓        ↓
直接返回  2. 分配ObjectMonitor
          ↓
      3. 设置INFLATING标记
          ↓
      4. 复制Lock Record信息
          ↓
      5. 更新Mark Word
          ↓
      返回ObjectMonitor
```

---

## 4. 可以借鉴的编码技巧

### 4.1 技巧1：双重检查锁定（Double-Checked Locking）

**问题**：单例模式的线程安全实现

**JVM中的应用**：偏向锁的获取

```java
// ❌ 错误的双重检查锁定（JDK 1.5之前）
public class Singleton {
    private static Singleton instance;
    
    public static Singleton getInstance() {
        if (instance == null) {  // 第一次检查
            synchronized(Singleton.class) {
                if (instance == null) {  // 第二次检查
                    instance = new Singleton();  // 可能发生指令重排序
                }
            }
        }
        return instance;
    }
}
```

**问题**：`instance = new Singleton()`分为三步：
1. 分配内存
2. 初始化对象
3. 将引用指向内存

可能发生重排序：1 → 3 → 2，导致其他线程看到未初始化的对象。

**✅ 正确的双重检查锁定**：

```java
public class Singleton {
    // volatile防止指令重排序
    private static volatile Singleton instance;
    
    public static Singleton getInstance() {
        if (instance == null) {  // 第一次检查（无锁）
            synchronized(Singleton.class) {
                if (instance == null) {  // 第二次检查（有锁）
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}
```

**借鉴点**：
- 第一次检查避免不必要的同步
- volatile保证可见性和禁止重排序
- 第二次检查保证只创建一次

---

### 4.2 技巧2：CAS乐观锁

**JVM中的应用**：偏向锁、轻量级锁的获取

```java
// JVM中的CAS实现（伪代码）
markOop mark = obj->mark();
markOop new_mark = mark->set_bias_pattern();

// CAS更新Mark Word
if (Atomic::cmpxchg_ptr(new_mark, obj->mark_addr(), mark) == mark) {
    // CAS成功
} else {
    // CAS失败，重试或升级
}
```

**Java中的应用**：

```java
import java.util.concurrent.atomic.AtomicInteger;

public class CASCounter {
    private AtomicInteger count = new AtomicInteger(0);
    
    public void increment() {
        for (;;) {
            int current = count.get();
            int next = current + 1;
            
            // CAS更新
            if (count.compareAndSet(current, next)) {
                break;
            }
            // CAS失败，自旋重试
        }
    }
}
```

**借鉴点**：
- 无锁化，避免线程阻塞
- 适合低竞争场景
- 失败时自旋重试

---

### 4.3 技巧3：自适应自旋

**JVM中的应用**：轻量级锁的自旋

```c++
// JVM中的自适应自旋（伪代码）
int TrySpin(Thread* Self) {
    int ctr = Knob_SpinLimit;  // 自旋次数
    
    // 自适应调整自旋次数
    if (_owner != NULL && _owner->is_Java_thread()) {
        JavaThread* owner = (JavaThread*)_owner;
        
        // 如果上次自旋成功，增加自旋次数
        if (owner->_spin_success) {
            ctr = Knob_SpinLimit * 2;
        }
        
        // 如果上次自旋失败，减少自旋次数
        if (owner->_spin_failure) {
            ctr = Knob_SpinLimit / 2;
        }
    }
    
    // 自旋等待
    while (--ctr >= 0) {
        if (TryLock(Self) > 0) {
            Self->_spin_success = true;
            return 1;
        }
    }
    
    Self->_spin_failure = true;
    return -1;
}
```

**Java中的应用**：

```java
public class AdaptiveSpinLock {
    private AtomicReference<Thread> owner = new AtomicReference<>();
    private volatile int spinCount = 100;  // 初始自旋次数
    
    public void lock() {
        Thread current = Thread.currentThread();
        int spins = spinCount;
        
        // 自旋尝试获取锁
        for (int i = 0; i < spins; i++) {
            if (owner.compareAndSet(null, current)) {
                // 自旋成功，增加自旋次数
                spinCount = Math.min(spinCount * 2, 10000);
                return;
            }
        }
        
        // 自旋失败，减少自旋次数
        spinCount = Math.max(spinCount / 2, 10);
        
        // 阻塞等待
        while (!owner.compareAndSet(null, current)) {
            LockSupport.park();
        }
    }
    
    public void unlock() {
        owner.set(null);
    }
}
```

**借鉴点**：
- 根据历史情况动态调整策略
- 平衡CPU消耗和响应时间
- 适应不同的竞争场景

---

### 4.4 技巧4：锁粗化

**JVM中的应用**：JIT编译器的锁优化

```java
// 原始代码
public void append(String s1, String s2, String s3) {
    StringBuffer sb = new StringBuffer();
    sb.append(s1);  // 加锁
    sb.append(s2);  // 加锁
    sb.append(s3);  // 加锁
}

// JIT优化后（锁粗化）
public void append(String s1, String s2, String s3) {
    StringBuffer sb = new StringBuffer();
    synchronized(sb) {  // 只加一次锁
        sb.append(s1);
        sb.append(s2);
        sb.append(s3);
    }
}
```

**手动应用**：

```java
// ❌ 不推荐：频繁加锁
public class FrequentLock {
    private final Object lock = new Object();
    private List<String> list = new ArrayList<>();
    
    public void addAll(List<String> items) {
        for (String item : items) {
            synchronized(lock) {  // 每次循环都加锁
                list.add(item);
            }
        }
    }
}

// ✅ 推荐：锁粗化
public class CoarseLock {
    private final Object lock = new Object();
    private List<String> list = new ArrayList<>();
    
    public void addAll(List<String> items) {
        synchronized(lock) {  // 只加一次锁
            for (String item : items) {
                list.add(item);
            }
        }
    }
}
```

**借鉴点**：
- 减少加锁次数
- 降低锁的开销
- 注意锁的粒度平衡

---

### 4.5 技巧5：锁消除

**JVM中的应用**：逃逸分析

```java
// 原始代码
public String concat(String s1, String s2) {
    StringBuffer sb = new StringBuffer();  // 局部变量，不会逃逸
    sb.append(s1);
    sb.append(s2);
    return sb.toString();
}

// JIT优化后（锁消除）
public String concat(String s1, String s2) {
    // StringBuffer的锁被消除，等价于StringBuilder
    StringBuilder sb = new StringBuilder();
    sb.append(s1);
    sb.append(s2);
    return sb.toString();
}
```

**手动应用**：

```java
// ❌ 不推荐：不必要的同步
public class UnnecessarySync {
    public String process(String input) {
        StringBuffer sb = new StringBuffer();  // 局部变量，不需要同步
        sb.append(input);
        sb.append("_processed");
        return sb.toString();
    }
}

// ✅ 推荐：使用非同步版本
public class NoSync {
    public String process(String input) {
        StringBuilder sb = new StringBuilder();  // 不同步
        sb.append(input);
        sb.append("_processed");
        return sb.toString();
    }
}
```

**借鉴点**：
- 避免不必要的同步
- 局部变量不需要同步
- 优先使用非同步版本

---

### 4.6 技巧6：分段锁

**JVM中的应用**：ConcurrentHashMap（JDK 1.7）

```java
// ConcurrentHashMap的分段锁（JDK 1.7）
public class ConcurrentHashMap<K, V> {
    final Segment<K,V>[] segments;  // 分段数组
    
    static final class Segment<K,V> extends ReentrantLock {
        transient volatile HashEntry<K,V>[] table;
        
        V put(K key, int hash, V value, boolean onlyIfAbsent) {
            lock();  // 只锁一个Segment
            try {
                // 操作table
            } finally {
                unlock();
            }
        }
    }
}
```

**手动应用**：

```java
public class StripedMap<K, V> {
    private static final int N_LOCKS = 16;
    private final Node<K, V>[] buckets;
    private final Object[] locks;
    
    public StripedMap(int numBuckets) {
        buckets = (Node<K, V>[]) new Node[numBuckets];
        locks = new Object[N_LOCKS];
        for (int i = 0; i < N_LOCKS; i++) {
            locks[i] = new Object();
        }
    }
    
    // 根据key的hash值选择锁
    private final Object getLock(Object key) {
        return locks[Math.abs(key.hashCode() % N_LOCKS)];
    }
    
    public V get(K key) {
        int hash = hash(key);
        synchronized(getLock(key)) {
            for (Node<K, V> m = buckets[hash]; m != null; m = m.next) {
                if (m.key.equals(key)) {
                    return m.value;
                }
            }
        }
        return null;
    }
    
    public void put(K key, V value) {
        int hash = hash(key);
        synchronized(getLock(key)) {
            Node<K, V> node = new Node<>(key, value, buckets[hash]);
            buckets[hash] = node;
        }
    }
    
    private int hash(K key) {
        return Math.abs(key.hashCode() % buckets.length);
    }
    
    private static class Node<K, V> {
        final K key;
        final V value;
        final Node<K, V> next;
        
        Node(K key, V value, Node<K, V> next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }
}
```

**借鉴点**：
- 将大锁拆分为多个小锁
- 提高并发度
- 适合大数据结构

---

## 5. 总结

### 5.1 Synchronized的实现层次

```
Java代码
    ↓
字节码（monitorenter/monitorexit）
    ↓
JVM解释器/JIT编译器
    ↓
锁优化（偏向锁、轻量级锁、重量级锁）
    ↓
ObjectMonitor（C++实现）
    ↓
操作系统互斥锁（Mutex）
```

### 5.2 核心优化技巧

1. **双重检查锁定**：减少同步开销
2. **CAS乐观锁**：无锁化编程
3. **自适应自旋**：动态调整策略
4. **锁粗化**：减少加锁次数
5. **锁消除**：消除不必要的锁
6. **分段锁**：提高并发度

### 5.3 实际编码建议

1. **优先使用JUC工具类**：AtomicInteger、ConcurrentHashMap等
2. **避免不必要的同步**：局部变量不需要同步
3. **缩小锁的范围**：只锁必要的代码
4. **使用volatile**：配合双重检查锁定
5. **考虑分段锁**：大数据结构使用分段锁
6. **性能测试**：验证优化效果

---

**至此，Synchronized的文档部分已全部完成！接下来将创建Demo演示代码和实际项目代码。**
