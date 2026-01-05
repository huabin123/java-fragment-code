package com.fragment.jvm.memory.demo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 内存溢出演示
 * 
 * 演示各种OOM场景：
 * 1. Java heap space
 * 2. Metaspace
 * 3. Direct buffer memory
 * 4. unable to create new native thread
 * 5. StackOverflowError
 * 
 * @author huabin
 */
public class OOMDemo {
    
    /**
     * 示例1：堆内存溢出
     * JVM参数：-Xms20m -Xmx20m -XX:+HeapDumpOnOutOfMemoryError
     */
    static class HeapOOM {
        static class OOMObject {
            private byte[] data = new byte[1024 * 1024]; // 1MB
        }
        
        public static void test() {
            System.out.println("\n========== 堆内存溢出演示 ==========");
            System.out.println("JVM参数：-Xms20m -Xmx20m -XX:+HeapDumpOnOutOfMemoryError");
            
            List<OOMObject> list = new ArrayList<>();
            
            try {
                while (true) {
                    list.add(new OOMObject());
                    System.out.println("创建对象: " + list.size());
                }
            } catch (OutOfMemoryError e) {
                System.out.println("发生OOM，已创建对象数量: " + list.size());
                System.out.println("错误信息: " + e.getMessage());
            }
        }
    }
    
    /**
     * 示例2：元空间溢出
     * JVM参数：-XX:MaxMetaspaceSize=10m
     * 需要cglib依赖
     */
    static class MetaspaceOOM {
        static class OOMObject {
        }
        
        public static void test() {
            System.out.println("\n========== 元空间溢出演示 ==========");
            System.out.println("JVM参数：-XX:MaxMetaspaceSize=10m");
            System.out.println("注意：需要cglib依赖才能运行");
            System.out.println("此示例需要动态生成大量类，建议单独运行");
        }
    }
    
    /**
     * 示例3：直接内存溢出
     * JVM参数：-XX:MaxDirectMemorySize=10m
     */
    static class DirectMemoryOOM {
        private static final int _1MB = 1024 * 1024;
        
        public static void test() {
            System.out.println("\n========== 直接内存溢出演示 ==========");
            System.out.println("JVM参数：-XX:MaxDirectMemorySize=10m");
            
            List<ByteBuffer> list = new ArrayList<>();
            int count = 0;
            
            try {
                while (true) {
                    ByteBuffer buffer = ByteBuffer.allocateDirect(_1MB);
                    list.add(buffer);
                    count++;
                    System.out.println("分配直接内存: " + count + "MB");
                }
            } catch (Throwable e) {
                System.out.println("发生OOM，已分配: " + count + "MB");
                System.out.println("错误信息: " + e.getMessage());
            }
        }
    }
    
    /**
     * 示例4：无法创建新线程
     * JVM参数：-Xss256k
     * 警告：此测试可能导致系统不稳定，建议在虚拟机中运行
     */
    static class ThreadOOM {
        public static void test() {
            System.out.println("\n========== 无法创建新线程演示 ==========");
            System.out.println("JVM参数：-Xss256k");
            System.out.println("警告：此测试可能导致系统不稳定，建议谨慎运行");
            
            int count = 0;
            
            try {
                while (true) {
                    new Thread(() -> {
                        try {
                            Thread.sleep(Long.MAX_VALUE);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                    
                    count++;
                    if (count % 100 == 0) {
                        System.out.println("创建线程数: " + count);
                    }
                }
            } catch (Throwable e) {
                System.out.println("发生OOM，已创建线程数: " + count);
                System.out.println("错误信息: " + e.getMessage());
            }
        }
    }
    
    /**
     * 示例5：栈溢出
     * JVM参数：-Xss256k
     */
    static class StackOverflow {
        private static int stackDepth = 0;
        
        public static void test() {
            System.out.println("\n========== 栈溢出演示 ==========");
            System.out.println("JVM参数：-Xss256k");
            
            try {
                recursion();
            } catch (StackOverflowError e) {
                System.out.println("栈溢出，栈深度: " + stackDepth);
                System.out.println("错误信息: " + e.getMessage());
            }
        }
        
        private static void recursion() {
            stackDepth++;
            recursion();
        }
    }
    
    /**
     * 示例6：GC overhead limit exceeded
     * JVM参数：-Xms10m -Xmx10m -XX:+PrintGCDetails
     */
    static class GCOverhead {
        public static void test() {
            System.out.println("\n========== GC overhead limit exceeded演示 ==========");
            System.out.println("JVM参数：-Xms10m -Xmx10m -XX:+PrintGCDetails");
            
            List<String> list = new ArrayList<>();
            
            try {
                int i = 0;
                while (true) {
                    list.add(String.valueOf(i++).intern());
                }
            } catch (Throwable e) {
                System.out.println("发生异常: " + e.getMessage());
            }
        }
    }
    
    /**
     * 示例7：内存泄漏导致OOM
     */
    static class MemoryLeak {
        static class LeakObject {
            private byte[] data = new byte[1024 * 100]; // 100KB
        }
        
        // 静态集合，永远不释放
        private static List<LeakObject> list = new ArrayList<>();
        
        public static void test() {
            System.out.println("\n========== 内存泄漏导致OOM演示 ==========");
            
            try {
                for (int i = 0; i < 1000; i++) {
                    list.add(new LeakObject());
                    
                    if (i % 100 == 0) {
                        System.out.println("添加对象: " + i);
                        
                        // 查看内存使用情况
                        Runtime runtime = Runtime.getRuntime();
                        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
                        System.out.println("已使用内存: " + usedMemory + "MB");
                    }
                }
            } catch (OutOfMemoryError e) {
                System.out.println("发生OOM，列表大小: " + list.size());
                System.out.println("说明：静态集合持有对象引用，导致内存泄漏");
            }
        }
    }
    
    /**
     * 示例8：大对象导致OOM
     */
    static class LargeObject {
        public static void test() {
            System.out.println("\n========== 大对象导致OOM演示 ==========");
            
            try {
                // 尝试创建一个超大数组
                int size = Integer.MAX_VALUE / 2;
                System.out.println("尝试创建大小为 " + (size / 1024 / 1024) + "MB 的数组");
                byte[] largeArray = new byte[size];
                System.out.println("创建成功");
            } catch (OutOfMemoryError e) {
                System.out.println("发生OOM");
                System.out.println("错误信息: " + e.getMessage());
                System.out.println("说明：单个对象过大，超过可用内存");
            }
        }
    }
    
    public static void main(String[] args) {
        System.out.println("内存溢出演示");
        System.out.println("警告：某些测试可能导致JVM崩溃或系统不稳定");
        System.out.println("建议：每次只运行一个测试\n");
        
        // 选择要运行的测试（取消注释）
        
        // 1. 堆内存溢出（相对安全）
        // HeapOOM.test();
        
        // 2. 元空间溢出（需要cglib依赖）
        // MetaspaceOOM.test();
        
        // 3. 直接内存溢出（相对安全）
        // DirectMemoryOOM.test();
        
        // 4. 无法创建新线程（危险，可能导致系统不稳定）
        // ThreadOOM.test();
        
        // 5. 栈溢出（相对安全）
        StackOverflow.test();
        
        // 6. GC overhead limit exceeded（相对安全）
        // GCOverhead.test();
        
        // 7. 内存泄漏（相对安全）
        // MemoryLeak.test();
        
        // 8. 大对象（相对安全）
        // LargeObject.test();
        
        System.out.println("\n========== 总结 ==========");
        System.out.println("常见OOM类型：");
        System.out.println("1. Java heap space - 堆内存不足");
        System.out.println("2. Metaspace - 元空间不足");
        System.out.println("3. Direct buffer memory - 直接内存不足");
        System.out.println("4. unable to create new native thread - 无法创建线程");
        System.out.println("5. StackOverflowError - 栈溢出");
        System.out.println("6. GC overhead limit exceeded - GC开销过大");
        
        System.out.println("\n排查建议：");
        System.out.println("1. 使用 -XX:+HeapDumpOnOutOfMemoryError 生成堆转储");
        System.out.println("2. 使用MAT分析堆转储文件");
        System.out.println("3. 查看GC日志，分析GC情况");
        System.out.println("4. 使用jmap、jstat等工具监控内存");
        System.out.println("5. 检查代码中的内存泄漏");
    }
}
