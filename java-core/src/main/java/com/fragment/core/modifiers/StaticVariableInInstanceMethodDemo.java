package com.fragment.core.modifiers;

import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 演示：为什么实例方法中不建议对static变量赋值
 * 
 * 核心问题：
 * 1. 所有实例共享状态被污染
 * 2. 线程安全问题
 * 3. 语义混乱
 * 4. 测试困难
 */
public class StaticVariableInInstanceMethodDemo {
    
    // ========== 问题演示1：状态污染 ==========
    
    /**
     * ❌ 错误示例：实例方法修改static变量导致状态污染
     */
    static class BadUserSession {
        private static String currentUser;  // 所有实例共享
        private String sessionId;
        
        public BadUserSession(String sessionId) {
            this.sessionId = sessionId;
        }
        
        // ❌ 实例方法修改static变量
        public void login(String username) {
            currentUser = username;  // 危险！影响所有实例
            System.out.println("[BAD] Session " + sessionId + " logged in as: " + currentUser);
        }
        
        public String getCurrentUser() {
            return currentUser;
        }
    }
    
    /**
     * ✓ 正确示例：使用实例变量
     */
    static class GoodUserSession {
        private String currentUser;  // 每个实例独立
        private String sessionId;
        
        public GoodUserSession(String sessionId) {
            this.sessionId = sessionId;
        }
        
        public void login(String username) {
            this.currentUser = username;  // 只修改当前实例
            System.out.println("[GOOD] Session " + sessionId + " logged in as: " + currentUser);
        }
        
        public String getCurrentUser() {
            return currentUser;
        }
    }
    
    // ========== 问题演示2：线程安全问题 ==========
    
    /**
     * ❌ 错误示例：多线程环境下的数据竞争
     */
    static class BadOrderProcessor {
        private static BigDecimal totalAmount = BigDecimal.ZERO;
        private String orderId;
        
        public BadOrderProcessor(String orderId) {
            this.orderId = orderId;
        }
        
        // ❌ 线程不安全
        public void processOrder(BigDecimal amount) {
            totalAmount = totalAmount.add(amount);  // 竞态条件！
        }
        
        public static BigDecimal getTotalAmount() {
            return totalAmount;
        }
        
        public static void reset() {
            totalAmount = BigDecimal.ZERO;
        }
    }
    
    /**
     * ✓ 正确示例：使用static方法 + 线程安全的数据结构
     */
    static class GoodOrderProcessor {
        private static final AtomicReference<BigDecimal> totalAmount = 
            new AtomicReference<>(BigDecimal.ZERO);
        private String orderId;
        
        public GoodOrderProcessor(String orderId) {
            this.orderId = orderId;
        }
        
        // 实例方法调用static方法
        public void processOrder(BigDecimal amount) {
            addToTotal(amount);
        }
        
        // ✓ static方法修改static变量（线程安全）
        private static void addToTotal(BigDecimal amount) {
            totalAmount.updateAndGet(current -> current.add(amount));
        }
        
        public static BigDecimal getTotalAmount() {
            return totalAmount.get();
        }
        
        public static void reset() {
            totalAmount.set(BigDecimal.ZERO);
        }
    }
    
    // ========== 问题演示3：语义混乱 ==========
    
    /**
     * ❌ 错误示例：计数器的错误实现
     */
    static class BadCounter {
        private static int count = 0;
        private String name;
        
        public BadCounter(String name) {
            this.name = name;
        }
        
        // ❌ 实例方法修改static变量 - 语义不清
        public void increment() {
            count++;  // 这是在修改类级别的状态，但看起来像实例操作
            System.out.println("[BAD] " + name + " incremented, count: " + count);
        }
        
        public int getCount() {
            return count;
        }
    }
    
    /**
     * ✓ 正确示例：清晰的语义
     */
    static class GoodCounter {
        private static int count = 0;
        private String name;
        
        public GoodCounter(String name) {
            this.name = name;
        }
        
        // ✓ static方法修改static变量 - 语义清晰
        public static void increment() {
            count++;
        }
        
        public static int getCount() {
            return count;
        }
        
        // 实例方法只操作实例状态
        public void doSomething() {
            System.out.println("[GOOD] " + name + " doing something");
            increment();  // 调用static方法
        }
    }
    
    // ========== 问题演示4：构造器中的特殊情况 ==========
    
    /**
     * ✓ 可接受：在构造器中修改static变量用于计数
     */
    static class Employee {
        private static int employeeCount = 0;
        private int employeeId;
        private String name;
        
        public Employee(String name) {
            this.name = name;
            // ✓ 构造器中修改static变量是常见模式
            this.employeeId = ++employeeCount;
            System.out.println("Created employee #" + employeeId + ": " + name);
        }
        
        // ✓ 实例方法读取static变量 - 没问题
        public int getTotalEmployees() {
            return employeeCount;
        }
        
        // ❌ 普通实例方法修改static变量 - 不推荐
        public void badIncrementCount() {
            employeeCount++;  // 不推荐！
        }
        
        // ✓ static方法修改static变量 - 推荐
        public static void resetCount() {
            employeeCount = 0;
        }
    }
    
    // ========== 测试方法 ==========
    
    /**
     * 测试1：状态污染问题
     */
    public static void testStatePollution() {
        System.out.println("\n========== Test 1: State Pollution ==========");
        
        System.out.println("\n--- Bad Example ---");
        BadUserSession badSession1 = new BadUserSession("SESSION-001");
        BadUserSession badSession2 = new BadUserSession("SESSION-002");
        
        badSession1.login("Alice");
        System.out.println("BadSession1 user: " + badSession1.getCurrentUser());
        
        badSession2.login("Bob");
        System.out.println("BadSession2 user: " + badSession2.getCurrentUser());
        System.out.println("BadSession1 user: " + badSession1.getCurrentUser() + " ❌ (被污染了！)");
        
        System.out.println("\n--- Good Example ---");
        GoodUserSession goodSession1 = new GoodUserSession("SESSION-001");
        GoodUserSession goodSession2 = new GoodUserSession("SESSION-002");
        
        goodSession1.login("Alice");
        System.out.println("GoodSession1 user: " + goodSession1.getCurrentUser());
        
        goodSession2.login("Bob");
        System.out.println("GoodSession2 user: " + goodSession2.getCurrentUser());
        System.out.println("GoodSession1 user: " + goodSession1.getCurrentUser() + " ✓ (独立状态)");
    }
    
    /**
     * 测试2：线程安全问题
     */
    public static void testThreadSafety() throws InterruptedException {
        System.out.println("\n========== Test 2: Thread Safety ==========");
        
        // 测试错误的实现
        System.out.println("\n--- Bad Example (Thread Unsafe) ---");
        BadOrderProcessor.reset();
        ExecutorService badExecutor = Executors.newFixedThreadPool(10);
        
        for (int i = 0; i < 100; i++) {
            final int orderId = i;
            badExecutor.submit(() -> {
                BadOrderProcessor processor = new BadOrderProcessor("ORDER-" + orderId);
                processor.processOrder(new BigDecimal("100"));
            });
        }
        
        badExecutor.shutdown();
        badExecutor.awaitTermination(1, TimeUnit.SECONDS);
        
        System.out.println("Expected: 10000");
        System.out.println("Actual: " + BadOrderProcessor.getTotalAmount() + " ❌ (数据丢失！)");
        
        // 测试正确的实现
        System.out.println("\n--- Good Example (Thread Safe) ---");
        GoodOrderProcessor.reset();
        ExecutorService goodExecutor = Executors.newFixedThreadPool(10);
        
        for (int i = 0; i < 100; i++) {
            final int orderId = i;
            goodExecutor.submit(() -> {
                GoodOrderProcessor processor = new GoodOrderProcessor("ORDER-" + orderId);
                processor.processOrder(new BigDecimal("100"));
            });
        }
        
        goodExecutor.shutdown();
        goodExecutor.awaitTermination(1, TimeUnit.SECONDS);
        
        System.out.println("Expected: 10000");
        System.out.println("Actual: " + GoodOrderProcessor.getTotalAmount() + " ✓ (正确！)");
    }
    
    /**
     * 测试3：语义混乱问题
     */
    public static void testSemanticClarity() {
        System.out.println("\n========== Test 3: Semantic Clarity ==========");
        
        System.out.println("\n--- Bad Example (Confusing) ---");
        BadCounter bad1 = new BadCounter("Counter1");
        BadCounter bad2 = new BadCounter("Counter2");
        
        bad1.increment();  // 看起来像实例操作，实际修改的是类级别状态
        bad2.increment();
        System.out.println("Bad1 count: " + bad1.getCount() + " (混乱：为什么是2？)");
        System.out.println("Bad2 count: " + bad2.getCount() + " (混乱：为什么是2？)");
        
        System.out.println("\n--- Good Example (Clear) ---");
        GoodCounter good1 = new GoodCounter("Counter1");
        GoodCounter good2 = new GoodCounter("Counter2");
        
        GoodCounter.increment();  // 清晰：这是类级别操作
        GoodCounter.increment();
        System.out.println("Good count: " + GoodCounter.getCount() + " ✓ (清晰：类级别计数)");
    }
    
    /**
     * 测试4：构造器中的特殊情况
     */
    public static void testConstructorUsage() {
        System.out.println("\n========== Test 4: Constructor Usage ==========");
        
        Employee.resetCount();
        
        Employee emp1 = new Employee("Alice");
        Employee emp2 = new Employee("Bob");
        Employee emp3 = new Employee("Charlie");
        
        System.out.println("\nTotal employees: " + emp1.getTotalEmployees());
        System.out.println("✓ 构造器中修改static变量用于计数是可接受的");
    }
    
    // ========== 主方法 ==========
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========================================");
        System.out.println("实例方法中不建议对static变量赋值 - 演示");
        System.out.println("========================================");
        
        // 测试1：状态污染
        testStatePollution();
        
        // 测试2：线程安全
        testThreadSafety();
        
        // 测试3：语义混乱
        testSemanticClarity();
        
        // 测试4：构造器特殊情况
        testConstructorUsage();
        
        System.out.println("\n========================================");
        System.out.println("总结：");
        System.out.println("1. ✅ 实例方法操作实例变量");
        System.out.println("2. ✅ static方法操作static变量");
        System.out.println("3. ✅ 实例方法可以读取static变量");
        System.out.println("4. ⚠️  实例方法修改static变量需谨慎");
        System.out.println("5. ❌ 避免在普通实例方法中随意修改static变量");
        System.out.println("========================================");
    }
}
