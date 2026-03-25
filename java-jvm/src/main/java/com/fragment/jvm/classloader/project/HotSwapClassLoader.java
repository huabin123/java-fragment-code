package com.example.jvm.classloader.project;

import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 热交换类加载器实战项目
 * 
 * 功能：
 * 1. 类文件热替换
 * 2. 文件监控
 * 3. 版本管理
 * 4. 内存泄漏检测
 * 5. 回滚支持
 * 
 * @author JavaGuide
 */
public class HotSwapClassLoader {

    public static void main(String[] args) throws Exception {
        System.out.println("========== 热交换类加载器演示 ==========\n");
        
        // 创建热交换管理器
        HotSwapManager manager = new HotSwapManager("/tmp/classes");
        
        // 注册类
        System.out.println("1. 注册类:");
        manager.registerClass("com.example.Service");
        manager.registerClass("com.example.Controller");
        
        // 获取类实例
        System.out.println("\n2. 获取类实例:");
        Object service = manager.getInstance("com.example.Service");
        System.out.println("Service实例: " + service);
        
        // 调用方法
        System.out.println("\n3. 调用方法:");
        manager.invoke("com.example.Service", "execute");
        
        // 模拟文件修改
        System.out.println("\n4. 模拟文件修改...");
        Thread.sleep(3000);
        
        // 手动重新加载
        System.out.println("\n5. 手动重新加载:");
        manager.reload("com.example.Service");
        
        // 再次调用方法
        System.out.println("\n6. 再次调用方法:");
        manager.invoke("com.example.Service", "execute");
        
        // 查看版本历史
        System.out.println("\n7. 查看版本历史:");
        manager.showVersionHistory("com.example.Service");
        
        // 回滚到上一个版本
        System.out.println("\n8. 回滚到上一个版本:");
        manager.rollback("com.example.Service");
        
        // 检测内存泄漏
        System.out.println("\n9. 检测内存泄漏:");
        manager.checkMemoryLeak();
        
        // 关闭管理器
        manager.shutdown();
    }

    // ==================== 热交换类加载器 ====================

    /**
     * 热交换类加载器
     */
    public static class SwappableClassLoader extends ClassLoader {
        
        private String classPath;
        private int version;
        private long createTime;
        private Set<String> loadedClasses = new HashSet<>();
        
        public SwappableClassLoader(String classPath, int version) {
            this.classPath = classPath;
            this.version = version;
            this.createTime = System.currentTimeMillis();
        }
        
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                byte[] classData = loadClassData(name);
                loadedClasses.add(name);
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
        
        public int getVersion() {
            return version;
        }
        
        public long getCreateTime() {
            return createTime;
        }
        
        public Set<String> getLoadedClasses() {
            return new HashSet<>(loadedClasses);
        }
        
        @Override
        public String toString() {
            return "SwappableClassLoader[v" + version + ", created=" + 
                   new Date(createTime) + "]";
        }
    }

    // ==================== 类版本信息 ====================

    /**
     * 类版本信息
     */
    public static class ClassVersion {
        private int version;
        private SwappableClassLoader loader;
        private Map<String, Object> instances;
        private long createTime;
        private long lastAccessTime;
        
        public ClassVersion(int version, SwappableClassLoader loader) {
            this.version = version;
            this.loader = loader;
            this.instances = new ConcurrentHashMap<>();
            this.createTime = System.currentTimeMillis();
            this.lastAccessTime = createTime;
        }
        
        public int getVersion() {
            return version;
        }
        
        public SwappableClassLoader getLoader() {
            return loader;
        }
        
        public Object getInstance(String className) throws Exception {
            lastAccessTime = System.currentTimeMillis();
            
            Object instance = instances.get(className);
            if (instance == null) {
                Class<?> clazz = loader.loadClass(className);
                instance = clazz.newInstance();
                instances.put(className, instance);
            }
            return instance;
        }
        
        public long getCreateTime() {
            return createTime;
        }
        
        public long getLastAccessTime() {
            return lastAccessTime;
        }
    }

    // ==================== 类信息 ====================

    /**
     * 类信息
     */
    public static class ClassInfo {
        private String className;
        private String classPath;
        private AtomicInteger versionCounter = new AtomicInteger(0);
        private List<ClassVersion> versions = new ArrayList<>();
        private ClassVersion currentVersion;
        private Map<String, Long> fileTimestamps = new ConcurrentHashMap<>();
        
        public ClassInfo(String className, String classPath) {
            this.className = className;
            this.classPath = classPath;
        }
        
        public String getClassName() {
            return className;
        }
        
        public ClassVersion getCurrentVersion() {
            return currentVersion;
        }
        
        public void setCurrentVersion(ClassVersion version) {
            this.currentVersion = version;
        }
        
        public List<ClassVersion> getVersions() {
            return new ArrayList<>(versions);
        }
        
        public void addVersion(ClassVersion version) {
            versions.add(version);
            currentVersion = version;
        }
        
        public int nextVersion() {
            return versionCounter.incrementAndGet();
        }
        
        public void updateTimestamp() {
            String fileName = classPath + File.separator + 
                             className.replace('.', File.separatorChar) + ".class";
            File file = new File(fileName);
            if (file.exists()) {
                fileTimestamps.put(className, file.lastModified());
            }
        }
        
        public boolean isModified() {
            String fileName = classPath + File.separator + 
                             className.replace('.', File.separatorChar) + ".class";
            File file = new File(fileName);
            if (!file.exists()) {
                return false;
            }
            
            Long lastModified = fileTimestamps.get(className);
            return lastModified == null || file.lastModified() > lastModified;
        }
    }

    // ==================== 热交换管理器 ====================

    /**
     * 热交换管理器
     */
    public static class HotSwapManager {
        
        private String classPath;
        private Map<String, ClassInfo> classes = new ConcurrentHashMap<>();
        private Map<Integer, WeakReference<SwappableClassLoader>> loaderRefs = new ConcurrentHashMap<>();
        private volatile boolean monitoring = false;
        private Thread monitorThread;
        
        public HotSwapManager(String classPath) {
            this.classPath = classPath;
        }
        
        /**
         * 注册类
         */
        public void registerClass(String className) throws Exception {
            System.out.println("注册类: " + className);
            
            if (classes.containsKey(className)) {
                System.out.println("类已注册: " + className);
                return;
            }
            
            ClassInfo info = new ClassInfo(className, classPath);
            classes.put(className, info);
            
            // 加载初始版本
            loadVersion(info);
            
            System.out.println("类注册成功: " + className);
        }
        
        /**
         * 加载新版本
         */
        private void loadVersion(ClassInfo info) throws Exception {
            int version = info.nextVersion();
            
            System.out.println("加载类 " + info.getClassName() + " 的版本 " + version);
            
            // 创建新的类加载器
            SwappableClassLoader loader = new SwappableClassLoader(classPath, version);
            
            // 创建版本信息
            ClassVersion classVersion = new ClassVersion(version, loader);
            info.addVersion(classVersion);
            
            // 更新时间戳
            info.updateTimestamp();
            
            // 保存类加载器的弱引用
            loaderRefs.put(version, new WeakReference<>(loader));
            
            System.out.println("版本加载成功: v" + version);
        }
        
        /**
         * 重新加载类
         */
        public void reload(String className) throws Exception {
            System.out.println("重新加载类: " + className);
            
            ClassInfo info = classes.get(className);
            if (info == null) {
                throw new IllegalArgumentException("类未注册: " + className);
            }
            
            loadVersion(info);
            
            System.out.println("类重新加载成功: " + className);
        }
        
        /**
         * 获取类实例
         */
        public Object getInstance(String className) throws Exception {
            ClassInfo info = classes.get(className);
            if (info == null) {
                throw new IllegalArgumentException("类未注册: " + className);
            }
            
            ClassVersion version = info.getCurrentVersion();
            return version.getInstance(className);
        }
        
        /**
         * 调用方法
         */
        public Object invoke(String className, String methodName, Object... args) throws Exception {
            Object instance = getInstance(className);
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
        
        /**
         * 回滚到上一个版本
         */
        public void rollback(String className) {
            System.out.println("回滚类: " + className);
            
            ClassInfo info = classes.get(className);
            if (info == null) {
                System.err.println("类未注册: " + className);
                return;
            }
            
            List<ClassVersion> versions = info.getVersions();
            if (versions.size() < 2) {
                System.err.println("没有可回滚的版本");
                return;
            }
            
            // 回滚到上一个版本
            ClassVersion previousVersion = versions.get(versions.size() - 2);
            info.setCurrentVersion(previousVersion);
            
            System.out.println("回滚成功，当前版本: v" + previousVersion.getVersion());
        }
        
        /**
         * 显示版本历史
         */
        public void showVersionHistory(String className) {
            ClassInfo info = classes.get(className);
            if (info == null) {
                System.err.println("类未注册: " + className);
                return;
            }
            
            System.out.println("\n========== 版本历史: " + className + " ==========");
            List<ClassVersion> versions = info.getVersions();
            for (ClassVersion version : versions) {
                boolean isCurrent = version == info.getCurrentVersion();
                System.out.println((isCurrent ? "* " : "  ") + 
                                 "v" + version.getVersion() + 
                                 " - 创建时间: " + new Date(version.getCreateTime()) +
                                 " - 最后访问: " + new Date(version.getLastAccessTime()));
            }
        }
        
        /**
         * 启动文件监控
         */
        public void startMonitoring() {
            if (monitoring) {
                System.out.println("文件监控已启动");
                return;
            }
            
            monitoring = true;
            monitorThread = new Thread(() -> {
                System.out.println("文件监控线程启动");
                
                while (monitoring) {
                    try {
                        Thread.sleep(2000);  // 每2秒检查一次
                        
                        for (ClassInfo info : classes.values()) {
                            if (info.isModified()) {
                                System.out.println("\n检测到文件变化: " + info.getClassName());
                                loadVersion(info);
                            }
                        }
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                
                System.out.println("文件监控线程停止");
            });
            monitorThread.setDaemon(true);
            monitorThread.start();
            
            System.out.println("文件监控已启动");
        }
        
        /**
         * 停止文件监控
         */
        public void stopMonitoring() {
            if (!monitoring) {
                return;
            }
            
            monitoring = false;
            if (monitorThread != null) {
                monitorThread.interrupt();
            }
            
            System.out.println("文件监控已停止");
        }
        
        /**
         * 检测内存泄漏
         */
        public void checkMemoryLeak() {
            System.out.println("\n========== 内存泄漏检测 ==========");
            
            // 触发GC
            System.gc();
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            // 检查类加载器是否被回收
            int totalLoaders = 0;
            int collectedLoaders = 0;
            
            for (Map.Entry<Integer, WeakReference<SwappableClassLoader>> entry : loaderRefs.entrySet()) {
                totalLoaders++;
                if (entry.getValue().get() == null) {
                    collectedLoaders++;
                }
            }
            
            System.out.println("总类加载器数: " + totalLoaders);
            System.out.println("已回收: " + collectedLoaders);
            System.out.println("未回收: " + (totalLoaders - collectedLoaders));
            
            if (collectedLoaders < totalLoaders - classes.size()) {
                System.out.println("\n⚠️  警告: 可能存在内存泄漏");
                System.out.println("建议检查:");
                System.out.println("1. 是否有静态字段持有类实例");
                System.out.println("2. 是否有ThreadLocal未清理");
                System.out.println("3. 是否有监听器未注销");
            } else {
                System.out.println("\n✓ 未检测到内存泄漏");
            }
        }
        
        /**
         * 清理旧版本
         */
        public void cleanupOldVersions(int keepVersions) {
            System.out.println("\n清理旧版本，保留最近 " + keepVersions + " 个版本");
            
            for (ClassInfo info : classes.values()) {
                List<ClassVersion> versions = info.getVersions();
                if (versions.size() > keepVersions) {
                    int toRemove = versions.size() - keepVersions;
                    for (int i = 0; i < toRemove; i++) {
                        ClassVersion version = versions.remove(0);
                        System.out.println("清理 " + info.getClassName() + " v" + version.getVersion());
                    }
                }
            }
            
            System.gc();
        }
        
        /**
         * 关闭管理器
         */
        public void shutdown() {
            System.out.println("\n正在关闭热交换管理器...");
            
            stopMonitoring();
            classes.clear();
            loaderRefs.clear();
            
            System.out.println("热交换管理器已关闭");
        }
    }
}

/**
 * 热交换测试
 */
class HotSwapTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("========== 热交换测试 ==========\n");
        
        // 创建管理器
        HotSwapClassLoader.HotSwapManager manager = 
            new HotSwapClassLoader.HotSwapManager("/tmp/classes");
        
        // 注册类
        manager.registerClass("com.example.Service");
        
        // 启动文件监控
        manager.startMonitoring();
        
        System.out.println("\n开始测试，每3秒调用一次服务...");
        System.out.println("修改类文件后会自动重新加载\n");
        
        // 模拟运行
        for (int i = 0; i < 10; i++) {
            try {
                Object result = manager.invoke("com.example.Service", "execute");
                System.out.println("[" + new Date() + "] 调用结果: " + result);
            } catch (Exception e) {
                System.err.println("[" + new Date() + "] 调用失败: " + e.getMessage());
            }
            
            Thread.sleep(3000);
        }
        
        // 显示版本历史
        manager.showVersionHistory("com.example.Service");
        
        // 检测内存泄漏
        manager.checkMemoryLeak();
        
        // 清理旧版本
        manager.cleanupOldVersions(3);
        
        // 关闭管理器
        manager.shutdown();
    }
}

/**
 * 使用示例
 */
class HotSwapUsageExample {
    
    public static void main(String[] args) {
        System.out.println("========== 热交换使用示例 ==========\n");
        
        System.out.println("1. 创建热交换管理器:");
        System.out.println("   HotSwapManager manager = new HotSwapManager(\"/path/to/classes\");");
        
        System.out.println("\n2. 注册需要热交换的类:");
        System.out.println("   manager.registerClass(\"com.example.Service\");");
        
        System.out.println("\n3. 启动文件监控:");
        System.out.println("   manager.startMonitoring();");
        
        System.out.println("\n4. 获取类实例:");
        System.out.println("   Object instance = manager.getInstance(\"com.example.Service\");");
        
        System.out.println("\n5. 调用方法:");
        System.out.println("   Object result = manager.invoke(\"com.example.Service\", \"execute\");");
        
        System.out.println("\n6. 手动重新加载:");
        System.out.println("   manager.reload(\"com.example.Service\");");
        
        System.out.println("\n7. 回滚到上一个版本:");
        System.out.println("   manager.rollback(\"com.example.Service\");");
        
        System.out.println("\n8. 查看版本历史:");
        System.out.println("   manager.showVersionHistory(\"com.example.Service\");");
        
        System.out.println("\n9. 检测内存泄漏:");
        System.out.println("   manager.checkMemoryLeak();");
        
        System.out.println("\n10. 清理旧版本:");
        System.out.println("    manager.cleanupOldVersions(3);  // 保留最近3个版本");
        
        System.out.println("\n特性:");
        System.out.println("✓ 自动检测文件变化");
        System.out.println("✓ 版本管理");
        System.out.println("✓ 支持回滚");
        System.out.println("✓ 内存泄漏检测");
        System.out.println("✓ 自动清理旧版本");
    }
}
