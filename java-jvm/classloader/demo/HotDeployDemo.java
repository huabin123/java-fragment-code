package com.example.jvm.classloader.demo;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 热部署演示
 * 
 * 演示内容：
 * 1. 基本的热部署实现
 * 2. 文件监控热部署
 * 3. WatchService热部署
 * 4. 热部署的限制
 * 
 * @author JavaGuide
 */
public class HotDeployDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== 1. 基本热部署演示 ==========");
        demonstrateBasicHotDeploy();
        
        System.out.println("\n========== 2. 文件监控热部署 ==========");
        demonstrateFileMonitorHotDeploy();
        
        System.out.println("\n========== 3. WatchService热部署 ==========");
        demonstrateWatchServiceHotDeploy();
        
        System.out.println("\n========== 4. 热部署限制演示 ==========");
        demonstrateHotDeployLimitations();
    }

    /**
     * 演示基本的热部署
     */
    private static void demonstrateBasicHotDeploy() throws Exception {
        System.out.println("创建热部署类加载器...");
        
        // 模拟热部署过程
        HotDeployClassLoader loader1 = new HotDeployClassLoader("/tmp/classes");
        System.out.println("第1次加载，类加载器: " + loader1);
        
        // 模拟类文件修改
        System.out.println("\n模拟类文件修改...");
        
        // 创建新的类加载器进行热部署
        HotDeployClassLoader loader2 = new HotDeployClassLoader("/tmp/classes");
        System.out.println("第2次加载（热部署），类加载器: " + loader2);
        
        System.out.println("\n热部署原理:");
        System.out.println("1. 检测到类文件变化");
        System.out.println("2. 创建新的类加载器");
        System.out.println("3. 使用新类加载器加载修改后的类");
        System.out.println("4. 替换旧的类实例");
    }

    /**
     * 演示文件监控热部署
     */
    private static void demonstrateFileMonitorHotDeploy() throws Exception {
        String classPath = "/tmp/classes";
        String className = "com.example.Service";
        
        System.out.println("类路径: " + classPath);
        System.out.println("监控类: " + className);
        
        // 创建热部署管理器
        FileMonitorHotDeployManager manager = new FileMonitorHotDeployManager(classPath, className);
        
        System.out.println("\n热部署管理器已启动");
        System.out.println("每2秒检查一次文件变化");
        System.out.println("检测到变化后自动重新加载");
        
        // 模拟运行一段时间
        System.out.println("\n模拟运行10秒...");
        for (int i = 0; i < 5; i++) {
            Thread.sleep(2000);
            System.out.println("运行中... " + (i + 1) * 2 + "秒");
        }
        
        manager.stop();
    }

    /**
     * 演示WatchService热部署
     */
    private static void demonstrateWatchServiceHotDeploy() throws Exception {
        String classPath = "/tmp/classes";
        String className = "com.example.Service";
        
        System.out.println("使用WatchService监控文件变化");
        System.out.println("类路径: " + classPath);
        System.out.println("监控类: " + className);
        
        System.out.println("\nWatchService优势:");
        System.out.println("1. 基于操作系统的文件系统事件");
        System.out.println("2. 实时性更好");
        System.out.println("3. 资源消耗更少");
    }

    /**
     * 演示热部署的限制
     */
    private static void demonstrateHotDeployLimitations() {
        System.out.println("热部署的限制:");
        System.out.println("\n1. 无法修改类的结构:");
        System.out.println("   ✓ 可以修改方法实现");
        System.out.println("   ✗ 不能添加/删除字段");
        System.out.println("   ✗ 不能添加/删除方法");
        System.out.println("   ✗ 不能修改方法签名");
        
        System.out.println("\n2. 静态字段和静态代码块:");
        System.out.println("   - 每次热部署都会重新初始化");
        System.out.println("   - 静态字段的值会丢失");
        
        System.out.println("\n3. 已创建的对象:");
        System.out.println("   - 已创建的对象不会更新");
        System.out.println("   - 只有新创建的对象才使用新版本");
        
        System.out.println("\n4. 类加载器内存泄漏:");
        System.out.println("   - 需要及时清理旧的类加载器");
        System.out.println("   - 避免静态字段持有引用");
    }

    // ==================== 热部署实现 ====================

    /**
     * 热部署类加载器
     */
    static class HotDeployClassLoader extends ClassLoader {
        
        private String classPath;
        private long createTime;
        
        public HotDeployClassLoader(String classPath) {
            this.classPath = classPath;
            this.createTime = System.currentTimeMillis();
        }
        
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                byte[] classData = loadClassData(name);
                return defineClass(name, classData, 0, classData.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
        
        private byte[] loadClassData(String name) throws IOException {
            String fileName = classPath + File.separator + 
                             name.replace('.', File.separatorChar) + ".class";
            
            File file = new File(fileName);
            if (!file.exists()) {
                throw new FileNotFoundException(fileName);
            }
            
            try (FileInputStream fis = new FileInputStream(file);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                return baos.toByteArray();
            }
        }
        
        @Override
        public String toString() {
            return "HotDeployClassLoader@" + Integer.toHexString(hashCode()) + 
                   "[created=" + new Date(createTime) + "]";
        }
    }

    /**
     * 文件监控热部署管理器
     */
    static class FileMonitorHotDeployManager {
        
        private String classPath;
        private String className;
        private Map<String, Long> fileTimestamps = new ConcurrentHashMap<>();
        private volatile HotDeployClassLoader loader;
        private volatile Object instance;
        private volatile boolean running = true;
        private Thread monitorThread;
        
        public FileMonitorHotDeployManager(String classPath, String className) {
            this.classPath = classPath;
            this.className = className;
            reload();
            startMonitor();
        }
        
        private void reload() {
            try {
                System.out.println("\n[" + new Date() + "] 重新加载类: " + className);
                
                // 创建新的类加载器
                loader = new HotDeployClassLoader(classPath);
                
                // 加载类
                Class<?> clazz = loader.loadClass(className);
                
                // 创建实例
                instance = clazz.newInstance();
                
                // 更新文件时间戳
                updateTimestamp(className);
                
                System.out.println("[" + new Date() + "] 重新加载成功");
                System.out.println("类加载器: " + loader);
            } catch (Exception e) {
                System.err.println("重新加载失败: " + e.getMessage());
            }
        }
        
        private void updateTimestamp(String className) {
            String fileName = classPath + File.separator + 
                             className.replace('.', File.separatorChar) + ".class";
            File file = new File(fileName);
            if (file.exists()) {
                fileTimestamps.put(className, file.lastModified());
            }
        }
        
        private boolean isModified(String className) {
            String fileName = classPath + File.separator + 
                             className.replace('.', File.separatorChar) + ".class";
            File file = new File(fileName);
            if (!file.exists()) {
                return false;
            }
            
            Long lastModified = fileTimestamps.get(className);
            return lastModified == null || file.lastModified() > lastModified;
        }
        
        private void startMonitor() {
            monitorThread = new Thread(() -> {
                System.out.println("文件监控线程启动");
                
                while (running) {
                    try {
                        Thread.sleep(2000);  // 每2秒检查一次
                        
                        if (isModified(className)) {
                            System.out.println("\n检测到类文件变化!");
                            reload();
                        }
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                
                System.out.println("文件监控线程停止");
            });
            monitorThread.setDaemon(true);
            monitorThread.start();
        }
        
        public void stop() {
            running = false;
            if (monitorThread != null) {
                monitorThread.interrupt();
            }
        }
        
        public Object invoke(String methodName, Object... args) throws Exception {
            Class<?> clazz = instance.getClass();
            
            // 查找方法
            Method method = null;
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(methodName) && 
                    m.getParameterCount() == args.length) {
                    method = m;
                    break;
                }
            }
            
            if (method == null) {
                throw new NoSuchMethodException(methodName);
            }
            
            return method.invoke(instance, args);
        }
    }
}

/**
 * WatchService热部署演示
 */
class WatchServiceHotDeployDemo {
    
    /**
     * WatchService热部署管理器
     */
    static class WatchServiceHotDeployManager {
        
        private String classPath;
        private String className;
        private volatile HotDeployDemo.HotDeployClassLoader loader;
        private volatile Object instance;
        private volatile boolean running = true;
        private Thread watcherThread;
        
        public WatchServiceHotDeployManager(String classPath, String className) {
            this.classPath = classPath;
            this.className = className;
            reload();
            startWatcher();
        }
        
        private void reload() {
            try {
                System.out.println("\n[" + new Date() + "] 重新加载类: " + className);
                
                loader = new HotDeployDemo.HotDeployClassLoader(classPath);
                Class<?> clazz = loader.loadClass(className);
                instance = clazz.newInstance();
                
                System.out.println("[" + new Date() + "] 重新加载成功");
            } catch (Exception e) {
                System.err.println("重新加载失败: " + e.getMessage());
            }
        }
        
        private void startWatcher() {
            watcherThread = new Thread(() -> {
                try {
                    WatchService watchService = FileSystems.getDefault().newWatchService();
                    Path path = Paths.get(classPath);
                    
                    // 注册监听
                    path.register(watchService,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_CREATE);
                    
                    System.out.println("开始监控目录: " + path);
                    
                    while (running) {
                        WatchKey key = watchService.take();
                        
                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();
                            
                            if (kind == StandardWatchEventKinds.OVERFLOW) {
                                continue;
                            }
                            
                            WatchEvent<Path> ev = (WatchEvent<Path>) event;
                            Path filename = ev.context();
                            
                            // 检查是否是我们关注的类文件
                            String changedFile = filename.toString();
                            String expectedFile = className.replace('.', File.separatorChar) + ".class";
                            
                            if (changedFile.endsWith(expectedFile)) {
                                System.out.println("\n检测到文件变化: " + changedFile);
                                
                                // 等待文件写入完成
                                Thread.sleep(100);
                                
                                reload();
                            }
                        }
                        
                        boolean valid = key.reset();
                        if (!valid) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    if (running) {
                        e.printStackTrace();
                    }
                }
                
                System.out.println("文件监控线程停止");
            });
            watcherThread.setDaemon(true);
            watcherThread.start();
        }
        
        public void stop() {
            running = false;
            if (watcherThread != null) {
                watcherThread.interrupt();
            }
        }
        
        public Object invoke(String methodName, Object... args) throws Exception {
            Class<?> clazz = instance.getClass();
            Method method = clazz.getMethod(methodName);
            return method.invoke(instance, args);
        }
    }
    
    public static void main(String[] args) throws Exception {
        String classPath = "/tmp/classes";
        String className = "com.example.Service";
        
        WatchServiceHotDeployManager manager = new WatchServiceHotDeployManager(classPath, className);
        
        System.out.println("\n热部署管理器已启动");
        System.out.println("修改类文件后会自动重新加载");
        
        // 模拟运行
        System.out.println("\n按Ctrl+C退出...");
        Thread.sleep(Long.MAX_VALUE);
    }
}

/**
 * 热部署限制演示
 */
class HotDeployLimitationsDemo {
    
    /**
     * 版本1的Service类
     */
    static class ServiceV1 {
        private static int counter = 0;
        
        static {
            System.out.println("ServiceV1 静态代码块执行");
        }
        
        public String execute() {
            counter++;
            return "Version 1, counter=" + counter;
        }
    }
    
    /**
     * 版本2的Service类（修改了方法实现）
     */
    static class ServiceV2 {
        private static int counter = 0;
        
        static {
            System.out.println("ServiceV2 静态代码块执行");
        }
        
        public String execute() {
            counter++;
            return "Version 2, counter=" + counter;
        }
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("========== 热部署限制演示 ==========");
        
        System.out.println("\n1. 静态字段会重新初始化:");
        ServiceV1 v1 = new ServiceV1();
        System.out.println(v1.execute());  // Version 1, counter=1
        System.out.println(v1.execute());  // Version 1, counter=2
        
        System.out.println("\n模拟热部署（创建新的类加载器）:");
        ServiceV2 v2 = new ServiceV2();
        System.out.println(v2.execute());  // Version 2, counter=1（重新从1开始）
        
        System.out.println("\n2. 已创建的对象不会更新:");
        System.out.println("v1仍然是旧版本:");
        System.out.println(v1.execute());  // Version 1, counter=3
        
        System.out.println("\n3. 只有新创建的对象才使用新版本:");
        ServiceV2 v2_new = new ServiceV2();
        System.out.println(v2_new.execute());  // Version 2, counter=2
    }
}

/**
 * 完整的热部署示例
 */
class CompleteHotDeployExample {
    
    public static void main(String[] args) throws Exception {
        String classPath = "/tmp/classes";
        String className = "com.example.Service";
        
        // 创建热部署管理器
        HotDeployDemo.FileMonitorHotDeployManager manager = 
            new HotDeployDemo.FileMonitorHotDeployManager(classPath, className);
        
        System.out.println("\n========== 热部署服务器启动 ==========");
        System.out.println("监控类: " + className);
        System.out.println("类路径: " + classPath);
        System.out.println("\n服务器运行中，每5秒调用一次服务...");
        System.out.println("修改类文件后会自动重新加载\n");
        
        // 模拟Web服务器
        for (int i = 0; i < 20; i++) {
            try {
                // 调用服务（使用最新版本的类）
                Object result = manager.invoke("execute");
                System.out.println("[" + new Date() + "] 调用结果: " + result);
            } catch (Exception e) {
                System.err.println("[" + new Date() + "] 调用失败: " + e.getMessage());
            }
            
            Thread.sleep(5000);
        }
        
        manager.stop();
        System.out.println("\n服务器停止");
    }
}

/**
 * 热部署内存泄漏检测
 */
class HotDeployMemoryLeakDetector {
    
    public static void main(String[] args) throws Exception {
        System.out.println("========== 热部署内存泄漏检测 ==========\n");
        
        for (int i = 0; i < 5; i++) {
            System.out.println("第 " + (i + 1) + " 次热部署:");
            
            // 创建类加载器
            HotDeployDemo.HotDeployClassLoader loader = 
                new HotDeployDemo.HotDeployClassLoader("/tmp/classes");
            
            System.out.println("类加载器: " + loader);
            
            // 清理引用
            loader = null;
            
            // 建议GC
            System.gc();
            Thread.sleep(100);
            
            // 检查内存
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            System.out.println("已使用内存: " + (usedMemory / 1024 / 1024) + " MB\n");
        }
        
        System.out.println("如果内存持续增长，可能存在内存泄漏");
        System.out.println("常见原因:");
        System.out.println("1. 静态字段持有类加载器引用");
        System.out.println("2. ThreadLocal未清理");
        System.out.println("3. 监听器未注销");
        System.out.println("4. 线程未停止");
    }
}
