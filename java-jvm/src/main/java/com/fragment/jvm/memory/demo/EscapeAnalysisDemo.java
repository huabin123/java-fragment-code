/**
 * 逃逸分析演示
 * 
 * 演示内容：
 * 1. 对象逃逸的三种情况
 * 2. 标量替换优化
 * 3. 同步消除优化
 * 4. 性能对比测试
 * 
 * 运行方式：
 * 
 * # 编译
 * javac EscapeAnalysisDemo.java
 * 
 * # 开启逃逸分析（默认）
 * java -Xmx1g -Xms1g -XX:+DoEscapeAnalysis EscapeAnalysisDemo
 * 
 * # 关闭逃逸分析（对比）
 * java -Xmx1g -Xms1g -XX:-DoEscapeAnalysis EscapeAnalysisDemo
 * 
 * # 查看GC日志
 * java -Xmx1g -Xms1g -XX:+DoEscapeAnalysis -Xlog:gc EscapeAnalysisDemo
 * 
 * # 查看编译日志
 * java -XX:+UnlockDiagnosticVMOptions -XX:+PrintCompilation -XX:+PrintInlining EscapeAnalysisDemo
 * 
 * # 查看逃逸分析结果（需要debug版本JVM）
 * java -XX:+PrintEscapeAnalysis EscapeAnalysisDemo
 */
public class EscapeAnalysisDemo {
    
    public static void main(String[] args) {
        System.out.println("=== 逃逸分析演示 ===\n");
        
        // 1. 演示三种逃逸情况
        demonstrateEscapeTypes();
        
        // 2. 标量替换性能测试
        testScalarReplacement();
        
        // 3. 同步消除性能测试
        testLockElimination();
        
        // 4. 综合性能测试
        comprehensiveTest();
    }
    
    /**
     * 演示三种逃逸情况
     */
    private static void demonstrateEscapeTypes() {
        System.out.println("1. 演示三种逃逸情况");
        printSeparator();
        
        // 情况1：无逃逸
        noEscape();
        System.out.println("✓ 无逃逸：对象只在方法内使用");
        
        // 情况2：方法逃逸
        User user = methodEscape();
        System.out.println("✓ 方法逃逸：对象被返回 - " + user.getName());
        
        // 情况3：线程逃逸
        threadEscape();
        System.out.println("✓ 线程逃逸：对象赋值给全局变量");
        
        System.out.println();
    }
    
    /**
     * 情况1：无逃逸
     * JVM优化：可以进行标量替换，对象不会真正创建
     */
    private static void noEscape() {
        Point p = new Point(1, 2);
        int sum = p.getX() + p.getY();
        // p对象只在方法内使用，未逃逸
        // JVM会进行标量替换：
        // int x = 1;
        // int y = 2;
        // int sum = x + y;
    }
    
    /**
     * 情况2：方法逃逸
     * JVM优化：无法优化，必须在堆上创建对象
     */
    private static User methodEscape() {
        User user = new User("Alice", 25);
        return user;  // 对象被返回，发生方法逃逸
    }
    
    /**
     * 情况3：线程逃逸
     * JVM优化：无法优化，必须在堆上创建对象
     */
    private static User globalUser;
    
    private static void threadEscape() {
        User user = new User("Bob", 30);
        globalUser = user;  // 赋值给静态变量，发生线程逃逸
    }
    
    /**
     * 标量替换性能测试
     */
    private static void testScalarReplacement() {
        System.out.println("2. 标量替换性能测试");
        printSeparator();
        
        int iterations = 100_000_000;
        
        // 测试1：对象未逃逸（会进行标量替换）
        long start1 = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            allocPoint();
        }
        long time1 = System.currentTimeMillis() - start1;
        
        // 测试2：对象逃逸（无法优化）
        long start2 = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            allocPointEscape();
        }
        long time2 = System.currentTimeMillis() - start2;
        
        System.out.println("迭代次数: " + iterations);
        System.out.println("未逃逸（标量替换）: " + time1 + " ms");
        System.out.println("逃逸（堆分配）: " + time2 + " ms");
        System.out.println("性能提升: " + (time2 * 100 / Math.max(time1, 1)) + "%");
        System.out.println();
    }
    
    /**
     * 对象未逃逸 - 会进行标量替换
     */
    private static void allocPoint() {
        Point p = new Point(1, 2);
        int sum = p.getX() + p.getY();
        // JVM优化后：
        // int x = 1;
        // int y = 2;
        // int sum = x + y;
    }
    
    /**
     * 对象逃逸 - 无法优化
     */
    private static Point escapedPoint;
    
    private static void allocPointEscape() {
        Point p = new Point(1, 2);
        escapedPoint = p;  // 逃逸
    }
    
    /**
     * 同步消除性能测试
     */
    private static void testLockElimination() {
        System.out.println("3. 同步消除性能测试");
        printSeparator();
        
        int iterations = 10_000_000;
        
        // 测试1：StringBuffer（有synchronized，但会被消除）
        long start1 = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            createStringWithBuffer();
        }
        long time1 = System.currentTimeMillis() - start1;
        
        // 测试2：StringBuilder（无synchronized）
        long start2 = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            createStringWithBuilder();
        }
        long time2 = System.currentTimeMillis() - start2;
        
        // 测试3：Vector（有synchronized，但会被消除）
        long start3 = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            createListWithVector();
        }
        long time3 = System.currentTimeMillis() - start3;
        
        System.out.println("迭代次数: " + iterations);
        System.out.println("StringBuffer（同步消除）: " + time1 + " ms");
        System.out.println("StringBuilder（无同步）: " + time2 + " ms");
        System.out.println("Vector（同步消除）: " + time3 + " ms");
        System.out.println();
    }
    
    /**
     * StringBuffer - 有synchronized，但对象未逃逸，会进行同步消除
     */
    private static String createStringWithBuffer() {
        StringBuffer sb = new StringBuffer();
        sb.append("Hello");
        sb.append(" ");
        sb.append("World");
        return sb.toString();
    }
    
    /**
     * StringBuilder - 无synchronized
     */
    private static String createStringWithBuilder() {
        StringBuilder sb = new StringBuilder();
        sb.append("Hello");
        sb.append(" ");
        sb.append("World");
        return sb.toString();
    }
    
    /**
     * Vector - 有synchronized，但对象未逃逸，会进行同步消除
     */
    private static int createListWithVector() {
        java.util.Vector<Integer> list = new java.util.Vector<>();
        list.add(1);
        list.add(2);
        list.add(3);
        return list.size();
    }
    
    /**
     * 综合性能测试
     */
    private static void comprehensiveTest() {
        System.out.println("4. 综合性能测试");
        printSeparator();
        
        int iterations = 1_000_000;
        
        long start = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            processUser();
        }
        long time = System.currentTimeMillis() - start;
        
        System.out.println("迭代次数: " + iterations);
        System.out.println("执行时间: " + time + " ms");
        System.out.println();
        
        // 提示
        System.out.println("提示：");
        System.out.println("- 使用 -XX:+DoEscapeAnalysis 开启逃逸分析（默认开启）");
        System.out.println("- 使用 -XX:-DoEscapeAnalysis 关闭逃逸分析进行对比");
        System.out.println("- 使用 -Xlog:gc 查看GC情况");
        System.out.println("- 开启逃逸分析时，GC次数会明显减少");
    }
    
    /**
     * 综合测试方法 - 创建多个对象
     */
    private static void processUser() {
        // 创建User对象（未逃逸）
        User user = new User("Alice", 25);
        
        // 创建Address对象（未逃逸）
        Address addr = new Address("Beijing", "Main St");
        user.setAddress(addr);
        
        // 创建Point对象（未逃逸）
        Point p = new Point(user.getAge(), addr.getCity().length());
        
        // 使用StringBuffer（同步消除）
        StringBuffer sb = new StringBuffer();
        sb.append(user.getName());
        sb.append(", ");
        sb.append(user.getAge());
        sb.append(", ");
        sb.append(addr.getCity());
        
        String info = sb.toString();
        
        // 所有对象都未逃逸，JVM会进行优化：
        // 1. 标量替换：将对象拆分为基本类型
        // 2. 同步消除：消除StringBuffer的synchronized
        // 3. 栈上分配：通过标量替换实现
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 打印分隔线（Java 8兼容）
     */
    private static void printSeparator() {
        for (int i = 0; i < 50; i++) {
            System.out.print("-");
        }
        System.out.println();
    }
    
    // ========== 辅助类 ==========
    
    /**
     * Point类 - 用于演示标量替换
     */
    static class Point {
        private int x;
        private int y;
        
        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
        
        public int getX() {
            return x;
        }
        
        public int getY() {
            return y;
        }
        
        public int sum() {
            return x + y;
        }
    }
    
    /**
     * User类 - 用于演示逃逸分析
     */
    static class User {
        private String name;
        private int age;
        private Address address;
        
        public User(String name, int age) {
            this.name = name;
            this.age = age;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public int getAge() {
            return age;
        }
        
        public void setAge(int age) {
            this.age = age;
        }
        
        public Address getAddress() {
            return address;
        }
        
        public void setAddress(Address address) {
            this.address = address;
        }
    }
    
    /**
     * Address类 - 用于演示嵌套对象的逃逸分析
     */
    static class Address {
        private String city;
        private String street;
        
        public Address(String city, String street) {
            this.city = city;
            this.street = street;
        }
        
        public String getCity() {
            return city;
        }
        
        public void setCity(String city) {
            this.city = city;
        }
        
        public String getStreet() {
            return street;
        }
        
        public void setStreet(String street) {
            this.street = street;
        }
    }
}
