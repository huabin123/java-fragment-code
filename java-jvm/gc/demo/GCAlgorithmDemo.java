package com.example.jvm.gc.demo;

import java.lang.ref.*;
import java.util.*;

/**
 * GC算法演示
 * 
 * 演示内容：
 * 1. 对象存活判定
 * 2. 引用类型
 * 3. GC算法模拟
 * 4. 可达性分析
 * 
 * @author JavaGuide
 */
public class GCAlgorithmDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== 1. 对象存活判定演示 ==========");
        demonstrateReachabilityAnalysis();
        
        System.out.println("\n========== 2. 引用类型演示 ==========");
        demonstrateReferenceTypes();
        
        System.out.println("\n========== 3. GC算法模拟 ==========");
        demonstrateGCAlgorithms();
        
        System.out.println("\n========== 4. 循环引用问题 ==========");
        demonstrateCircularReference();
    }

    /**
     * 演示可达性分析
     */
    private static void demonstrateReachabilityAnalysis() {
        System.out.println("可达性分析原理：从GC Roots出发，标记所有可达对象\n");
        
        // GC Root 1: 局部变量
        Object localVar = new Object();
        System.out.println("1. 局部变量（GC Root）: " + localVar);
        
        // GC Root 2: 静态变量
        GCRootsDemo.staticVar = new Object();
        System.out.println("2. 静态变量（GC Root）: " + GCRootsDemo.staticVar);
        
        // 可达对象
        Object reachable = localVar;
        System.out.println("3. 可达对象: " + reachable);
        
        // 不可达对象
        Object unreachable = new Object();
        unreachable = null;
        System.out.println("4. 不可达对象: " + unreachable + " (已被回收)");
        
        System.out.println("\nGC Roots包括：");
        System.out.println("- 虚拟机栈中的引用");
        System.out.println("- 方法区中的静态变量");
        System.out.println("- 方法区中的常量");
        System.out.println("- 本地方法栈中的引用");
    }

    /**
     * 演示引用类型
     */
    private static void demonstrateReferenceTypes() throws InterruptedException {
        // 1. 强引用
        System.out.println("1. 强引用（Strong Reference）:");
        Object strongRef = new Object();
        System.out.println("   对象: " + strongRef);
        System.out.println("   特点: 只要强引用存在，对象永不回收");
        
        // 2. 软引用
        System.out.println("\n2. 软引用（Soft Reference）:");
        Object obj = new Object();
        SoftReference<Object> softRef = new SoftReference<>(obj);
        obj = null;
        System.out.println("   对象: " + softRef.get());
        System.out.println("   特点: 内存充足时保留，内存不足时回收");
        
        // 3. 弱引用
        System.out.println("\n3. 弱引用（Weak Reference）:");
        obj = new Object();
        WeakReference<Object> weakRef = new WeakReference<>(obj);
        System.out.println("   GC前: " + weakRef.get());
        obj = null;
        System.gc();
        Thread.sleep(100);
        System.out.println("   GC后: " + weakRef.get());
        System.out.println("   特点: 下次GC时必定回收");
        
        // 4. 虚引用
        System.out.println("\n4. 虚引用（Phantom Reference）:");
        obj = new Object();
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        PhantomReference<Object> phantomRef = new PhantomReference<>(obj, queue);
        System.out.println("   get()返回: " + phantomRef.get());
        System.out.println("   特点: 无法通过虚引用获取对象，用于跟踪回收");
    }

    /**
     * 演示GC算法
     */
    private static void demonstrateGCAlgorithms() {
        System.out.println("1. 标记-清除算法:");
        MarkSweepSimulator markSweep = new MarkSweepSimulator();
        markSweep.demonstrate();
        
        System.out.println("\n2. 标记-复制算法:");
        CopyingSimulator copying = new CopyingSimulator();
        copying.demonstrate();
        
        System.out.println("\n3. 标记-整理算法:");
        CompactingSimulator compacting = new CompactingSimulator();
        compacting.demonstrate();
    }

    /**
     * 演示循环引用问题
     */
    private static void demonstrateCircularReference() {
        System.out.println("引用计数法的问题：无法解决循环引用\n");
        
        // 创建循环引用
        Node a = new Node("A");
        Node b = new Node("B");
        a.next = b;
        b.next = a;
        
        System.out.println("创建循环引用: A <-> B");
        System.out.println("A引用B: " + (a.next == b));
        System.out.println("B引用A: " + (b.next == a));
        
        // 断开外部引用
        a = null;
        b = null;
        
        System.out.println("\n断开外部引用后:");
        System.out.println("引用计数法: 两个对象互相引用，引用计数不为0，无法回收");
        System.out.println("可达性分析: 从GC Roots无法到达，可以回收");
        
        System.gc();
        System.out.println("\nJava使用可达性分析，循环引用的对象会被正确回收");
    }

    // ==================== 辅助类 ====================

    /**
     * GC Roots演示
     */
    static class GCRootsDemo {
        // 静态变量（GC Root）
        static Object staticVar;
        
        // 常量（GC Root）
        static final Object CONSTANT = new Object();
    }

    /**
     * 节点类（用于循环引用演示）
     */
    static class Node {
        String name;
        Node next;
        
        Node(String name) {
            this.name = name;
        }
    }

    /**
     * 标记-清除算法模拟
     */
    static class MarkSweepSimulator {
        void demonstrate() {
            System.out.println("   初始状态: [A][B][C][D][E]");
            System.out.println("   标记阶段: 标记存活对象A、C、E");
            System.out.println("   清除阶段: [A][空][C][空][E]");
            System.out.println("   特点: 产生内存碎片");
        }
    }

    /**
     * 标记-复制算法模拟
     */
    static class CopyingSimulator {
        void demonstrate() {
            System.out.println("   From区: [A][B][C][D][E]");
            System.out.println("   复制存活对象A、C、E到To区");
            System.out.println("   To区: [A][C][E]");
            System.out.println("   清空From区，交换From和To");
            System.out.println("   特点: 无碎片，但浪费50%空间");
        }
    }

    /**
     * 标记-整理算法模拟
     */
    static class CompactingSimulator {
        void demonstrate() {
            System.out.println("   初始状态: [A][B][C][D][E]");
            System.out.println("   标记阶段: 标记存活对象A、C、E");
            System.out.println("   整理阶段: 移动存活对象到一端");
            System.out.println("   结果: [A][C][E][空闲空间]");
            System.out.println("   特点: 无碎片，不浪费空间，但需要移动对象");
        }
    }
}

/**
 * 引用类型详细演示
 */
class ReferenceTypeDemo {
    
    private static final int _1MB = 1024 * 1024;
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 引用类型详细演示 ==========\n");
        
        demonstrateSoftReference();
        demonstrateWeakReference();
        demonstratePhantomReference();
        demonstrateWeakHashMap();
    }

    /**
     * 软引用演示：实现缓存
     */
    private static void demonstrateSoftReference() {
        System.out.println("1. 软引用实现缓存:");
        
        Map<String, SoftReference<byte[]>> cache = new HashMap<>();
        
        // 添加缓存
        for (int i = 0; i < 10; i++) {
            byte[] data = new byte[_1MB];
            cache.put("key" + i, new SoftReference<>(data));
        }
        
        System.out.println("   缓存大小: " + cache.size());
        
        // 检查缓存
        int available = 0;
        for (Map.Entry<String, SoftReference<byte[]>> entry : cache.entrySet()) {
            if (entry.getValue().get() != null) {
                available++;
            }
        }
        System.out.println("   可用缓存: " + available);
        System.out.println("   内存不足时，软引用会被回收\n");
    }

    /**
     * 弱引用演示
     */
    private static void demonstrateWeakReference() throws InterruptedException {
        System.out.println("2. 弱引用演示:");
        
        Object obj = new Object();
        WeakReference<Object> weakRef = new WeakReference<>(obj);
        
        System.out.println("   创建弱引用: " + weakRef.get());
        
        obj = null;
        System.out.println("   断开强引用");
        
        System.gc();
        Thread.sleep(100);
        
        System.out.println("   GC后: " + weakRef.get());
        System.out.println("   弱引用的对象已被回收\n");
    }

    /**
     * 虚引用演示：跟踪对象回收
     */
    private static void demonstratePhantomReference() throws InterruptedException {
        System.out.println("3. 虚引用演示:");
        
        Object obj = new Object();
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        PhantomReference<Object> phantomRef = new PhantomReference<>(obj, queue);
        
        System.out.println("   创建虚引用");
        System.out.println("   get()返回: " + phantomRef.get());
        
        obj = null;
        System.gc();
        Thread.sleep(100);
        
        Reference<?> ref = queue.poll();
        if (ref != null) {
            System.out.println("   对象已被回收，可以执行清理操作");
        }
        System.out.println();
    }

    /**
     * WeakHashMap演示
     */
    private static void demonstrateWeakHashMap() throws InterruptedException {
        System.out.println("4. WeakHashMap演示:");
        
        Map<Object, String> map = new WeakHashMap<>();
        
        Object key1 = new Object();
        Object key2 = new Object();
        
        map.put(key1, "value1");
        map.put(key2, "value2");
        
        System.out.println("   GC前大小: " + map.size());
        
        key1 = null;
        System.gc();
        Thread.sleep(100);
        
        System.out.println("   GC后大小: " + map.size());
        System.out.println("   key被回收后，entry自动删除");
    }
}

/**
 * 对象可达性分析演示
 */
class ReachabilityAnalysisDemo {
    
    public static void main(String[] args) {
        System.out.println("========== 对象可达性分析演示 ==========\n");
        
        // 创建对象图
        ObjectGraph graph = new ObjectGraph();
        graph.demonstrate();
    }

    /**
     * 对象图
     */
    static class ObjectGraph {
        
        static class Node {
            String name;
            List<Node> references = new ArrayList<>();
            
            Node(String name) {
                this.name = name;
            }
            
            void addReference(Node node) {
                references.add(node);
            }
            
            @Override
            public String toString() {
                return name;
            }
        }
        
        void demonstrate() {
            // 创建对象图
            Node root = new Node("Root(GC Root)");
            Node a = new Node("A");
            Node b = new Node("B");
            Node c = new Node("C");
            Node d = new Node("D");
            Node e = new Node("E");
            
            // 建立引用关系
            root.addReference(a);
            root.addReference(b);
            a.addReference(c);
            b.addReference(d);
            // e没有被引用
            
            System.out.println("对象图:");
            System.out.println("  Root -> A -> C");
            System.out.println("       -> B -> D");
            System.out.println("  E (无引用)");
            
            System.out.println("\n可达性分析:");
            Set<Node> reachable = new HashSet<>();
            mark(root, reachable);
            
            System.out.println("可达对象: " + reachable);
            System.out.println("不可达对象: E");
            System.out.println("\n结论: E会被GC回收");
        }
        
        void mark(Node node, Set<Node> reachable) {
            if (reachable.contains(node)) {
                return;
            }
            
            reachable.add(node);
            
            for (Node ref : node.references) {
                mark(ref, reachable);
            }
        }
    }
}

/**
 * finalize方法演示
 */
class FinalizeDemo {
    
    private static FinalizeDemo instance;
    
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        System.out.println("finalize方法被调用");
        
        // 对象自救：重新建立引用
        instance = this;
    }
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== finalize方法演示 ==========\n");
        
        // 第一次自救
        instance = new FinalizeDemo();
        instance = null;
        
        System.out.println("第一次GC:");
        System.gc();
        Thread.sleep(500);
        
        if (instance != null) {
            System.out.println("对象自救成功");
        } else {
            System.out.println("对象被回收");
        }
        
        // 第二次无法自救
        instance = null;
        
        System.out.println("\n第二次GC:");
        System.gc();
        Thread.sleep(500);
        
        if (instance != null) {
            System.out.println("对象自救成功");
        } else {
            System.out.println("对象被回收");
        }
        
        System.out.println("\n注意: finalize方法只会被调用一次");
        System.out.println("不推荐使用finalize，应该使用try-with-resources");
    }
}
