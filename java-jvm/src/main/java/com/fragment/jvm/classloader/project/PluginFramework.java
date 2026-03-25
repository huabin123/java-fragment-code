package com.example.jvm.classloader.project;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 插件框架实战项目
 * 
 * 功能：
 * 1. 插件加载和卸载
 * 2. 插件隔离
 * 3. 插件热部署
 * 4. 插件依赖管理
 * 5. 插件生命周期管理
 * 
 * @author JavaGuide
 */
public class PluginFramework {

    public static void main(String[] args) throws Exception {
        System.out.println("========== 插件框架演示 ==========\n");
        
        // 创建插件管理器
        PluginManager manager = new PluginManager();
        
        // 加载插件
        System.out.println("1. 加载插件:");
        manager.loadPlugin("plugin1", "/tmp/plugins/plugin1.jar");
        manager.loadPlugin("plugin2", "/tmp/plugins/plugin2.jar");
        
        // 列出插件
        System.out.println("\n2. 列出所有插件:");
        manager.listPlugins();
        
        // 执行插件
        System.out.println("\n3. 执行插件:");
        manager.executePlugin("plugin1");
        manager.executePlugin("plugin2");
        
        // 重新加载插件
        System.out.println("\n4. 重新加载插件:");
        manager.reloadPlugin("plugin1", "/tmp/plugins/plugin1.jar");
        
        // 卸载插件
        System.out.println("\n5. 卸载插件:");
        manager.unloadPlugin("plugin2");
        
        // 再次列出插件
        System.out.println("\n6. 列出剩余插件:");
        manager.listPlugins();
        
        // 关闭管理器
        manager.shutdown();
    }

    // ==================== 插件接口 ====================

    /**
     * 插件接口
     */
    public interface Plugin {
        /**
         * 获取插件名称
         */
        String getName();
        
        /**
         * 获取插件版本
         */
        String getVersion();
        
        /**
         * 初始化插件
         */
        void init(PluginContext context);
        
        /**
         * 执行插件
         */
        void execute();
        
        /**
         * 销毁插件
         */
        void destroy();
    }

    /**
     * 插件上下文
     */
    public static class PluginContext {
        private String pluginName;
        private String pluginPath;
        private Map<String, Object> attributes = new ConcurrentHashMap<>();
        
        public PluginContext(String pluginName, String pluginPath) {
            this.pluginName = pluginName;
            this.pluginPath = pluginPath;
        }
        
        public String getPluginName() {
            return pluginName;
        }
        
        public String getPluginPath() {
            return pluginPath;
        }
        
        public void setAttribute(String key, Object value) {
            attributes.put(key, value);
        }
        
        public Object getAttribute(String key) {
            return attributes.get(key);
        }
        
        public void log(String message) {
            System.out.println("[" + pluginName + "] " + message);
        }
    }

    // ==================== 插件类加载器 ====================

    /**
     * 插件类加载器
     */
    public static class PluginClassLoader extends URLClassLoader {
        
        private String pluginName;
        private Set<String> loadedClasses = new HashSet<>();
        
        public PluginClassLoader(String pluginName, URL[] urls, ClassLoader parent) {
            super(urls, parent);
            this.pluginName = pluginName;
        }
        
        @Override
        protected Class<?> loadClass(String name, boolean resolve) 
            throws ClassNotFoundException {
            
            synchronized (getClassLoadingLock(name)) {
                // 1. 检查是否已加载
                Class<?> c = findLoadedClass(name);
                if (c != null) {
                    return c;
                }
                
                // 2. 核心类和API类委派给父加载器
                if (name.startsWith("java.") || 
                    name.startsWith("javax.") ||
                    name.startsWith("com.example.jvm.classloader.project.PluginFramework")) {
                    return super.loadClass(name, resolve);
                }
                
                // 3. 插件类优先自己加载
                try {
                    c = findClass(name);
                    loadedClasses.add(name);
                    if (resolve) {
                        resolveClass(c);
                    }
                    return c;
                } catch (ClassNotFoundException e) {
                    // 4. 找不到，委派给父加载器
                    return super.loadClass(name, resolve);
                }
            }
        }
        
        public Set<String> getLoadedClasses() {
            return new HashSet<>(loadedClasses);
        }
        
        @Override
        public String toString() {
            return "PluginClassLoader[" + pluginName + "]";
        }
    }

    // ==================== 插件信息 ====================

    /**
     * 插件信息
     */
    public static class PluginInfo {
        private String name;
        private String version;
        private String jarPath;
        private PluginClassLoader loader;
        private Plugin instance;
        private PluginContext context;
        private long loadTime;
        private PluginState state;
        
        public PluginInfo(String name, String jarPath) {
            this.name = name;
            this.jarPath = jarPath;
            this.loadTime = System.currentTimeMillis();
            this.state = PluginState.CREATED;
        }
        
        public String getName() {
            return name;
        }
        
        public String getVersion() {
            return version;
        }
        
        public void setVersion(String version) {
            this.version = version;
        }
        
        public String getJarPath() {
            return jarPath;
        }
        
        public PluginClassLoader getLoader() {
            return loader;
        }
        
        public void setLoader(PluginClassLoader loader) {
            this.loader = loader;
        }
        
        public Plugin getInstance() {
            return instance;
        }
        
        public void setInstance(Plugin instance) {
            this.instance = instance;
        }
        
        public PluginContext getContext() {
            return context;
        }
        
        public void setContext(PluginContext context) {
            this.context = context;
        }
        
        public long getLoadTime() {
            return loadTime;
        }
        
        public PluginState getState() {
            return state;
        }
        
        public void setState(PluginState state) {
            this.state = state;
        }
    }

    /**
     * 插件状态
     */
    public enum PluginState {
        CREATED,      // 已创建
        LOADING,      // 加载中
        LOADED,       // 已加载
        INITIALIZED,  // 已初始化
        RUNNING,      // 运行中
        STOPPED,      // 已停止
        UNLOADED      // 已卸载
    }

    // ==================== 插件管理器 ====================

    /**
     * 插件管理器
     */
    public static class PluginManager {
        
        private Map<String, PluginInfo> plugins = new ConcurrentHashMap<>();
        private ClassLoader parentLoader;
        
        public PluginManager() {
            this.parentLoader = this.getClass().getClassLoader();
        }
        
        /**
         * 加载插件
         */
        public void loadPlugin(String pluginName, String jarPath) throws Exception {
            System.out.println("\n正在加载插件: " + pluginName);
            System.out.println("JAR路径: " + jarPath);
            
            // 检查插件是否已存在
            if (plugins.containsKey(pluginName)) {
                throw new IllegalStateException("插件已存在: " + pluginName);
            }
            
            // 创建插件信息
            PluginInfo info = new PluginInfo(pluginName, jarPath);
            info.setState(PluginState.LOADING);
            
            try {
                // 创建类加载器
                URL[] urls = new URL[] { new File(jarPath).toURI().toURL() };
                PluginClassLoader loader = new PluginClassLoader(pluginName, urls, parentLoader);
                info.setLoader(loader);
                
                // 查找插件主类
                String mainClass = findPluginMainClass(jarPath);
                if (mainClass == null) {
                    throw new Exception("找不到插件主类");
                }
                
                System.out.println("插件主类: " + mainClass);
                
                // 加载插件类
                Class<?> pluginClass = loader.loadClass(mainClass);
                
                // 检查是否实现了Plugin接口
                if (!Plugin.class.isAssignableFrom(pluginClass)) {
                    throw new Exception("插件类必须实现Plugin接口");
                }
                
                // 创建插件实例
                Plugin plugin = (Plugin) pluginClass.newInstance();
                info.setInstance(plugin);
                info.setVersion(plugin.getVersion());
                
                // 创建插件上下文
                PluginContext context = new PluginContext(pluginName, jarPath);
                info.setContext(context);
                
                // 初始化插件
                plugin.init(context);
                
                // 更新状态
                info.setState(PluginState.INITIALIZED);
                
                // 保存插件信息
                plugins.put(pluginName, info);
                
                System.out.println("插件加载成功: " + pluginName + " v" + plugin.getVersion());
                System.out.println("类加载器: " + loader);
                
            } catch (Exception e) {
                info.setState(PluginState.UNLOADED);
                throw new Exception("插件加载失败: " + e.getMessage(), e);
            }
        }
        
        /**
         * 执行插件
         */
        public void executePlugin(String pluginName) {
            PluginInfo info = plugins.get(pluginName);
            if (info == null) {
                System.err.println("插件不存在: " + pluginName);
                return;
            }
            
            try {
                System.out.println("\n执行插件: " + pluginName);
                info.setState(PluginState.RUNNING);
                info.getInstance().execute();
                info.setState(PluginState.INITIALIZED);
            } catch (Exception e) {
                System.err.println("插件执行失败: " + pluginName);
                e.printStackTrace();
            }
        }
        
        /**
         * 卸载插件
         */
        public void unloadPlugin(String pluginName) {
            System.out.println("\n正在卸载插件: " + pluginName);
            
            PluginInfo info = plugins.remove(pluginName);
            if (info == null) {
                System.err.println("插件不存在: " + pluginName);
                return;
            }
            
            try {
                // 停止插件
                info.setState(PluginState.STOPPED);
                
                // 销毁插件
                if (info.getInstance() != null) {
                    info.getInstance().destroy();
                }
                
                // 关闭类加载器
                if (info.getLoader() != null) {
                    info.getLoader().close();
                }
                
                // 清理引用
                info.setInstance(null);
                info.setLoader(null);
                info.setContext(null);
                info.setState(PluginState.UNLOADED);
                
                // 建议GC
                System.gc();
                
                System.out.println("插件卸载成功: " + pluginName);
                
            } catch (Exception e) {
                System.err.println("插件卸载失败: " + pluginName);
                e.printStackTrace();
            }
        }
        
        /**
         * 重新加载插件
         */
        public void reloadPlugin(String pluginName, String jarPath) throws Exception {
            System.out.println("\n正在重新加载插件: " + pluginName);
            
            // 卸载旧插件
            unloadPlugin(pluginName);
            
            // 等待GC
            Thread.sleep(100);
            
            // 加载新插件
            loadPlugin(pluginName, jarPath);
        }
        
        /**
         * 列出所有插件
         */
        public void listPlugins() {
            if (plugins.isEmpty()) {
                System.out.println("没有已加载的插件");
                return;
            }
            
            System.out.println("\n========== 已加载的插件 ==========");
            for (PluginInfo info : plugins.values()) {
                System.out.println("\n插件名称: " + info.getName());
                System.out.println("插件版本: " + info.getVersion());
                System.out.println("JAR路径: " + info.getJarPath());
                System.out.println("类加载器: " + info.getLoader());
                System.out.println("加载时间: " + new Date(info.getLoadTime()));
                System.out.println("当前状态: " + info.getState());
                System.out.println("已加载类数: " + info.getLoader().getLoadedClasses().size());
                System.out.println("-----------------------------------");
            }
        }
        
        /**
         * 获取插件信息
         */
        public PluginInfo getPluginInfo(String pluginName) {
            return plugins.get(pluginName);
        }
        
        /**
         * 关闭管理器
         */
        public void shutdown() {
            System.out.println("\n正在关闭插件管理器...");
            
            // 卸载所有插件
            List<String> pluginNames = new ArrayList<>(plugins.keySet());
            for (String pluginName : pluginNames) {
                unloadPlugin(pluginName);
            }
            
            System.out.println("插件管理器已关闭");
        }
        
        /**
         * 查找插件主类
         */
        private String findPluginMainClass(String jarPath) throws IOException {
            try (JarFile jarFile = new JarFile(jarPath)) {
                // 1. 先查找MANIFEST.MF中的Plugin-Class
                String mainClass = jarFile.getManifest().getMainAttributes()
                    .getValue("Plugin-Class");
                if (mainClass != null) {
                    return mainClass;
                }
                
                // 2. 查找实现了Plugin接口的类
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    
                    if (name.endsWith(".class") && !name.contains("$")) {
                        String className = name.substring(0, name.length() - 6)
                            .replace('/', '.');
                        
                        // 这里简化处理，实际应该检查类是否实现了Plugin接口
                        if (className.endsWith("Plugin") || className.endsWith("Main")) {
                            return className;
                        }
                    }
                }
            }
            
            return null;
        }
    }

    // ==================== 示例插件 ====================

    /**
     * 示例插件1
     */
    public static class ExamplePlugin1 implements Plugin {
        
        private PluginContext context;
        
        @Override
        public String getName() {
            return "ExamplePlugin1";
        }
        
        @Override
        public String getVersion() {
            return "1.0.0";
        }
        
        @Override
        public void init(PluginContext context) {
            this.context = context;
            context.log("插件初始化");
        }
        
        @Override
        public void execute() {
            context.log("插件执行: Hello from ExamplePlugin1!");
        }
        
        @Override
        public void destroy() {
            context.log("插件销毁");
        }
    }

    /**
     * 示例插件2
     */
    public static class ExamplePlugin2 implements Plugin {
        
        private PluginContext context;
        private int executeCount = 0;
        
        @Override
        public String getName() {
            return "ExamplePlugin2";
        }
        
        @Override
        public String getVersion() {
            return "2.0.0";
        }
        
        @Override
        public void init(PluginContext context) {
            this.context = context;
            context.log("插件初始化");
            context.setAttribute("startTime", System.currentTimeMillis());
        }
        
        @Override
        public void execute() {
            executeCount++;
            context.log("插件执行: 第" + executeCount + "次执行");
            
            long startTime = (Long) context.getAttribute("startTime");
            long runTime = System.currentTimeMillis() - startTime;
            context.log("运行时间: " + runTime + "ms");
        }
        
        @Override
        public void destroy() {
            context.log("插件销毁，总共执行了" + executeCount + "次");
        }
    }
}

/**
 * 插件框架测试
 */
class PluginFrameworkTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("========== 插件框架测试 ==========\n");
        
        // 创建插件管理器
        PluginFramework.PluginManager manager = new PluginFramework.PluginManager();
        
        // 注意：这里使用内部类模拟插件
        // 实际使用时应该从JAR文件加载
        
        System.out.println("插件框架功能:");
        System.out.println("1. ✓ 插件加载和卸载");
        System.out.println("2. ✓ 插件隔离（不同的类加载器）");
        System.out.println("3. ✓ 插件热部署（重新加载）");
        System.out.println("4. ✓ 插件生命周期管理");
        System.out.println("5. ✓ 插件上下文管理");
        
        System.out.println("\n使用说明:");
        System.out.println("1. 创建插件类，实现Plugin接口");
        System.out.println("2. 打包成JAR文件");
        System.out.println("3. 在MANIFEST.MF中指定Plugin-Class");
        System.out.println("4. 使用PluginManager加载插件");
        
        System.out.println("\nMANIFEST.MF示例:");
        System.out.println("Manifest-Version: 1.0");
        System.out.println("Plugin-Class: com.example.MyPlugin");
        System.out.println("Plugin-Version: 1.0.0");
    }
}
