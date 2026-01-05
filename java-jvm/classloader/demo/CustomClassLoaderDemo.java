package com.example.jvm.classloader.demo;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 自定义类加载器演示
 * 
 * 演示内容：
 * 1. 基本的自定义类加载器
 * 2. 文件系统类加载器
 * 3. 加密类加载器
 * 4. 网络类加载器
 * 5. 类加载器隔离
 * 6. 双亲委派模型的验证
 * 
 * @author JavaGuide
 */
public class CustomClassLoaderDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== 1. 基本自定义类加载器 ==========");
        demonstrateBasicCustomLoader();
        
        System.out.println("\n========== 2. 文件系统类加载器 ==========");
        demonstrateFileSystemLoader();
        
        System.out.println("\n========== 3. 加密类加载器 ==========");
        demonstrateEncryptedLoader();
        
        System.out.println("\n========== 4. 类加载器隔离 ==========");
        demonstrateClassLoaderIsolation();
        
        System.out.println("\n========== 5. 双亲委派模型验证 ==========");
        demonstrateParentDelegation();
        
        System.out.println("\n========== 6. 打破双亲委派模型 ==========");
        demonstrateBreakParentDelegation();
    }

    /**
     * 演示基本的自定义类加载器
     */
    private static void demonstrateBasicCustomLoader() throws Exception {
        // 创建自定义类加载器
        SimpleClassLoader loader = new SimpleClassLoader();
        
        // 加载类
        Class<?> clazz = loader.loadClass("com.example.TestClass");
        System.out.println("加载的类: " + clazz.getName());
        System.out.println("类加载器: " + clazz.getClassLoader());
        
        // 创建实例
        Object instance = clazz.newInstance();
        System.out.println("实例: " + instance);
    }

    /**
     * 演示文件系统类加载器
     */
    private static void demonstrateFileSystemLoader() throws Exception {
        String classPath = "/tmp/classes";
        FileSystemClassLoader loader = new FileSystemClassLoader(classPath);
        
        System.out.println("类加载路径: " + classPath);
        System.out.println("类加载器: " + loader);
        
        // 注意：这里需要实际的class文件
        // Class<?> clazz = loader.loadClass("com.example.MyClass");
        // Object instance = clazz.newInstance();
    }

    /**
     * 演示加密类加载器
     */
    private static void demonstrateEncryptedLoader() throws Exception {
        String classPath = "/tmp/encrypted";
        String key = "mySecretKey";
        
        EncryptedClassLoader loader = new EncryptedClassLoader(classPath, key);
        System.out.println("加密类加载器创建成功");
        System.out.println("加密密钥: " + key);
        
        // 注意：这里需要实际的加密class文件
        // Class<?> clazz = loader.loadClass("com.example.SecureClass");
    }

    /**
     * 演示类加载器隔离
     */
    private static void demonstrateClassLoaderIsolation() throws Exception {
        // 创建两个隔离的类加载器
        SimpleClassLoader loader1 = new SimpleClassLoader();
        SimpleClassLoader loader2 = new SimpleClassLoader();
        
        System.out.println("类加载器1: " + loader1);
        System.out.println("类加载器2: " + loader2);
        
        // 加载相同的类
        Class<?> class1 = loader1.loadClass("java.lang.String");
        Class<?> class2 = loader2.loadClass("java.lang.String");
        
        // 核心类由Bootstrap ClassLoader加载，所以是同一个类
        System.out.println("\n加载String类:");
        System.out.println("class1 == class2: " + (class1 == class2));
        System.out.println("class1的类加载器: " + class1.getClassLoader());
        System.out.println("class2的类加载器: " + class2.getClassLoader());
        
        // 如果是自定义类，会是不同的类
        System.out.println("\n加载自定义类（模拟）:");
        System.out.println("不同的类加载器加载相同的类，会被视为不同的类");
    }

    /**
     * 演示双亲委派模型
     */
    private static void demonstrateParentDelegation() throws Exception {
        CustomClassLoader loader = new CustomClassLoader();
        
        // 加载核心类（会委派给Bootstrap ClassLoader）
        Class<?> stringClass = loader.loadClass("java.lang.String");
        System.out.println("String类的类加载器: " + stringClass.getClassLoader());
        
        // 加载扩展类（会委派给Extension ClassLoader）
        Class<?> scriptEngineClass = loader.loadClass("javax.script.ScriptEngine");
        System.out.println("ScriptEngine类的类加载器: " + scriptEngineClass.getClassLoader());
        
        // 加载应用类（会委派给Application ClassLoader）
        Class<?> currentClass = loader.loadClass(CustomClassLoaderDemo.class.getName());
        System.out.println("当前类的类加载器: " + currentClass.getClassLoader());
    }

    /**
     * 演示打破双亲委派模型
     */
    private static void demonstrateBreakParentDelegation() throws Exception {
        SelfFirstClassLoader loader = new SelfFirstClassLoader("/tmp/classes");
        
        System.out.println("SelfFirstClassLoader: 优先自己加载类");
        System.out.println("对于非核心类，会先尝试自己加载，失败后再委派给父加载器");
    }

    // ==================== 自定义类加载器实现 ====================

    /**
     * 简单的自定义类加载器
     */
    static class SimpleClassLoader extends ClassLoader {
        
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            System.out.println("SimpleClassLoader.findClass: " + name);
            
            // 这里简化处理，实际应该读取class文件
            // 为了演示，我们只是抛出异常
            throw new ClassNotFoundException("Class not found: " + name);
        }
    }

    /**
     * 文件系统类加载器
     */
    static class FileSystemClassLoader extends ClassLoader {
        
        private String classPath;
        
        public FileSystemClassLoader(String classPath) {
            this.classPath = classPath;
        }
        
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                // 读取class文件
                byte[] classData = loadClassData(name);
                if (classData == null) {
                    throw new ClassNotFoundException(name);
                }
                
                // 定义类
                return defineClass(name, classData, 0, classData.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
        
        private byte[] loadClassData(String name) throws IOException {
            // 将类名转换为文件路径
            String fileName = classPath + File.separator + 
                             name.replace('.', File.separatorChar) + ".class";
            
            File file = new File(fileName);
            if (!file.exists()) {
                return null;
            }
            
            // 读取文件
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
            return "FileSystemClassLoader[" + classPath + "]";
        }
    }

    /**
     * 加密类加载器
     */
    static class EncryptedClassLoader extends ClassLoader {
        
        private String classPath;
        private String key;
        
        public EncryptedClassLoader(String classPath, String key) {
            this.classPath = classPath;
            this.key = key;
        }
        
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                // 读取加密的class文件
                byte[] encryptedData = loadEncryptedClassData(name);
                if (encryptedData == null) {
                    throw new ClassNotFoundException(name);
                }
                
                // 解密
                byte[] classData = decrypt(encryptedData);
                
                // 定义类
                return defineClass(name, classData, 0, classData.length);
            } catch (Exception e) {
                throw new ClassNotFoundException(name, e);
            }
        }
        
        private byte[] loadEncryptedClassData(String name) throws IOException {
            String fileName = classPath + File.separator + 
                             name.replace('.', File.separatorChar) + ".encrypted";
            
            File file = new File(fileName);
            if (!file.exists()) {
                return null;
            }
            
            return Files.readAllBytes(Paths.get(fileName));
        }
        
        private byte[] decrypt(byte[] data) {
            // 简单的异或加密/解密
            byte[] result = new byte[data.length];
            byte[] keyBytes = key.getBytes();
            
            for (int i = 0; i < data.length; i++) {
                result[i] = (byte) (data[i] ^ keyBytes[i % keyBytes.length]);
            }
            
            return result;
        }
    }

    /**
     * 自定义类加载器（遵循双亲委派）
     */
    static class CustomClassLoader extends ClassLoader {
        
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            System.out.println("CustomClassLoader.findClass: " + name);
            throw new ClassNotFoundException(name);
        }
    }

    /**
     * 自己优先的类加载器（打破双亲委派）
     */
    static class SelfFirstClassLoader extends ClassLoader {
        
        private String classPath;
        
        public SelfFirstClassLoader(String classPath) {
            this.classPath = classPath;
        }
        
        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            // 1. 检查是否已加载
            Class<?> c = findLoadedClass(name);
            if (c != null) {
                return c;
            }
            
            // 2. 对于java.*等核心类，委派给父加载器
            if (name.startsWith("java.") || name.startsWith("javax.")) {
                return super.loadClass(name);
            }
            
            // 3. 优先自己加载
            try {
                c = findClass(name);
                System.out.println("由SelfFirstClassLoader加载: " + name);
                return c;
            } catch (ClassNotFoundException e) {
                // 4. 自己加载失败，委派给父加载器
                System.out.println("委派给父加载器: " + name);
                return super.loadClass(name);
            }
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
            
            return Files.readAllBytes(Paths.get(fileName));
        }
    }
}

/**
 * 网络类加载器演示
 */
class NetworkClassLoaderDemo {
    
    /**
     * 网络类加载器
     */
    static class NetworkClassLoader extends ClassLoader {
        
        private String baseUrl;
        
        public NetworkClassLoader(String baseUrl) {
            this.baseUrl = baseUrl;
        }
        
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                // 构建URL
                String url = baseUrl + "/" + name.replace('.', '/') + ".class";
                
                // 从网络下载class文件
                byte[] classData = downloadClassData(url);
                
                // 定义类
                return defineClass(name, classData, 0, classData.length);
            } catch (Exception e) {
                throw new ClassNotFoundException(name, e);
            }
        }
        
        private byte[] downloadClassData(String urlString) throws IOException {
            URL url = new URL(urlString);
            try (InputStream is = url.openStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                return baos.toByteArray();
            }
        }
    }
    
    public static void main(String[] args) throws Exception {
        String baseUrl = "http://example.com/classes";
        NetworkClassLoader loader = new NetworkClassLoader(baseUrl);
        
        System.out.println("网络类加载器创建成功");
        System.out.println("基础URL: " + baseUrl);
        
        // 注意：这里需要实际的网络资源
        // Class<?> clazz = loader.loadClass("com.example.RemoteClass");
    }
}

/**
 * 类加载器隔离演示
 */
class ClassLoaderIsolationDemo {
    
    /**
     * 隔离的类加载器
     */
    static class IsolatedClassLoader extends ClassLoader {
        
        private String name;
        private String classPath;
        
        public IsolatedClassLoader(String name, String classPath) {
            this.name = name;
            this.classPath = classPath;
        }
        
        @Override
        protected Class<?> findClass(String className) throws ClassNotFoundException {
            try {
                byte[] classData = loadClassData(className);
                return defineClass(className, classData, 0, classData.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(className, e);
            }
        }
        
        private byte[] loadClassData(String className) throws IOException {
            String fileName = classPath + File.separator + 
                             className.replace('.', File.separatorChar) + ".class";
            return Files.readAllBytes(Paths.get(fileName));
        }
        
        @Override
        public String toString() {
            return "IsolatedClassLoader[" + name + "]";
        }
    }
    
    public static void main(String[] args) throws Exception {
        // 创建两个隔离的类加载器
        IsolatedClassLoader loader1 = new IsolatedClassLoader("App1", "/tmp/app1");
        IsolatedClassLoader loader2 = new IsolatedClassLoader("App2", "/tmp/app2");
        
        System.out.println("类加载器1: " + loader1);
        System.out.println("类加载器2: " + loader2);
        
        // 加载相同的类
        // Class<?> class1 = loader1.loadClass("com.example.Service");
        // Class<?> class2 = loader2.loadClass("com.example.Service");
        
        // 验证隔离
        // System.out.println("类1: " + class1);
        // System.out.println("类2: " + class2);
        // System.out.println("是否相同: " + (class1 == class2));  // false
        
        System.out.println("\n不同的类加载器加载相同的类，会被视为不同的类");
        System.out.println("这样可以实现应用隔离，避免类冲突");
    }
}

/**
 * URLClassLoader演示
 */
class URLClassLoaderDemo {
    
    public static void main(String[] args) throws Exception {
        // 创建URLClassLoader
        URL[] urls = new URL[] {
            new File("/tmp/lib/mylib.jar").toURI().toURL()
        };
        
        URLClassLoader loader = new URLClassLoader(urls);
        
        System.out.println("URLClassLoader创建成功");
        System.out.println("加载的JAR: " + urls[0]);
        
        // 加载JAR中的类
        // Class<?> clazz = loader.loadClass("com.example.MyClass");
        // Object instance = clazz.newInstance();
        
        // 关闭类加载器（JDK 7+）
        loader.close();
    }
}

/**
 * 类加载器的命名空间演示
 */
class ClassLoaderNamespaceDemo {
    
    public static void main(String[] args) throws Exception {
        ClassLoader appLoader = ClassLoader.getSystemClassLoader();
        ClassLoader extLoader = appLoader.getParent();
        
        System.out.println("Application ClassLoader: " + appLoader);
        System.out.println("Extension ClassLoader: " + extLoader);
        
        // Application ClassLoader可以看到Extension ClassLoader加载的类
        Class<?> class1 = appLoader.loadClass("javax.script.ScriptEngine");
        System.out.println("\nApplication ClassLoader加载javax.script.ScriptEngine成功");
        
        // Extension ClassLoader看不到Application ClassLoader加载的类
        try {
            Class<?> class2 = extLoader.loadClass(ClassLoaderNamespaceDemo.class.getName());
            System.out.println("Extension ClassLoader加载当前类成功");
        } catch (ClassNotFoundException e) {
            System.out.println("Extension ClassLoader无法加载当前类");
        }
        
        System.out.println("\n命名空间规则:");
        System.out.println("1. 子加载器可以看见父加载器加载的类");
        System.out.println("2. 父加载器看不见子加载器加载的类");
        System.out.println("3. 兄弟加载器之间互相看不见");
    }
}
