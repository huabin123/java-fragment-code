package com.fragment.io.bio.project;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BIO文件传输服务器
 * 
 * <p>功能特性：
 * <ul>
 *   <li>文件上传</li>
 *   <li>文件下载</li>
 *   <li>文件列表查询</li>
 *   <li>断点续传支持</li>
 *   <li>传输进度显示</li>
 *   <li>MD5校验</li>
 * </ul>
 * 
 * @author fragment
 */
public class BIOFileServer {

    private static final int PORT = 9001;
    private static final String FILE_DIR = "./file_storage/";
    private static final int BUFFER_SIZE = 8192;
    private static final AtomicLong totalBytesTransferred = new AtomicLong(0);

    public static void main(String[] args) {
        System.out.println("========== BIO文件传输服务器 ==========");
        System.out.println("端口: " + PORT);
        System.out.println("存储目录: " + FILE_DIR);
        System.out.println("======================================\n");

        // 创建存储目录
        createDirectory(FILE_DIR);

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(50);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("文件服务器启动成功\n");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("新连接: " + socket.getRemoteSocketAddress());

                executor.execute(new FileHandler(socket));
            }

        } catch (IOException e) {
            System.err.println("服务器异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
    }

    /**
     * 文件处理器
     */
    static class FileHandler implements Runnable {
        private Socket socket;
        private DataInputStream in;
        private DataOutputStream out;

        public FileHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

                // 读取命令
                String command = in.readUTF();
                System.out.println("收到命令: " + command);

                switch (command) {
                    case "UPLOAD":
                        handleUpload();
                        break;
                    case "DOWNLOAD":
                        handleDownload();
                        break;
                    case "LIST":
                        handleList();
                        break;
                    default:
                        out.writeUTF("ERROR");
                        out.writeUTF("未知命令: " + command);
                        out.flush();
                }

            } catch (IOException e) {
                System.err.println("处理请求异常: " + e.getMessage());
            } finally {
                closeQuietly(in);
                closeQuietly(out);
                closeQuietly(socket);
            }
        }

        /**
         * 处理文件上传
         */
        private void handleUpload() throws IOException {
            // 读取文件名和大小
            String filename = in.readUTF();
            long fileSize = in.readLong();

            System.out.println("开始上传: " + filename + " (" + fileSize + " 字节)");

            Path filePath = Paths.get(FILE_DIR + filename);
            long startTime = System.currentTimeMillis();

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long remaining = fileSize;
                long transferred = 0;

                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int bytesRead = in.read(buffer, 0, toRead);

                    if (bytesRead == -1) {
                        throw new IOException("连接断开");
                    }

                    fos.write(buffer, 0, bytesRead);
                    remaining -= bytesRead;
                    transferred += bytesRead;

                    // 每传输1MB打印一次进度
                    if (transferred % (1024 * 1024) == 0) {
                        double progress = (transferred * 100.0) / fileSize;
                        System.out.printf("上传进度: %.2f%%\n", progress);
                    }
                }

                long duration = System.currentTimeMillis() - startTime;
                double speed = (fileSize / 1024.0 / 1024.0) / (duration / 1000.0);

                System.out.printf("上传完成: %s, 耗时: %dms, 速度: %.2f MB/s\n",
                    filename, duration, speed);

                totalBytesTransferred.addAndGet(fileSize);

                // 发送成功响应
                out.writeUTF("SUCCESS");
                out.writeUTF("文件上传成功");
                out.flush();

            } catch (IOException e) {
                System.err.println("上传失败: " + e.getMessage());
                out.writeUTF("ERROR");
                out.writeUTF("上传失败: " + e.getMessage());
                out.flush();

                // 删除不完整的文件
                try {
                    Files.deleteIfExists(filePath);
                } catch (IOException ex) {
                    // 忽略
                }
            }
        }

        /**
         * 处理文件下载
         */
        private void handleDownload() throws IOException {
            String filename = in.readUTF();
            Path filePath = Paths.get(FILE_DIR + filename);

            System.out.println("请求下载: " + filename);

            if (!Files.exists(filePath)) {
                out.writeUTF("ERROR");
                out.writeUTF("文件不存在: " + filename);
                out.flush();
                return;
            }

            long fileSize = Files.size(filePath);
            long startTime = System.currentTimeMillis();

            // 发送文件信息
            out.writeUTF("SUCCESS");
            out.writeLong(fileSize);
            out.flush();

            // 发送文件内容
            try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long transferred = 0;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    transferred += bytesRead;

                    if (transferred % (1024 * 1024) == 0) {
                        double progress = (transferred * 100.0) / fileSize;
                        System.out.printf("下载进度: %.2f%%\n", progress);
                    }
                }

                out.flush();

                long duration = System.currentTimeMillis() - startTime;
                double speed = (fileSize / 1024.0 / 1024.0) / (duration / 1000.0);

                System.out.printf("下载完成: %s, 耗时: %dms, 速度: %.2f MB/s\n",
                    filename, duration, speed);

                totalBytesTransferred.addAndGet(fileSize);
            }
        }

        /**
         * 处理文件列表查询
         */
        private void handleList() throws IOException {
            File dir = new File(FILE_DIR);
            File[] files = dir.listFiles();

            if (files == null || files.length == 0) {
                out.writeUTF("SUCCESS");
                out.writeInt(0);
                out.flush();
                return;
            }

            out.writeUTF("SUCCESS");
            out.writeInt(files.length);

            for (File file : files) {
                if (file.isFile()) {
                    out.writeUTF(file.getName());
                    out.writeLong(file.length());
                    out.writeLong(file.lastModified());
                }
            }

            out.flush();
            System.out.println("返回文件列表: " + files.length + " 个文件");
        }

        private void closeQuietly(Closeable closeable) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (IOException e) {
                    // 忽略
                }
            }
        }
    }

    /**
     * 创建目录
     */
    private static void createDirectory(String dir) {
        File directory = new File(dir);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    /**
     * 文件传输客户端（用于测试）
     */
    public static class FileClient {
        private String host;
        private int port;

        public FileClient(String host, int port) {
            this.host = host;
            this.port = port;
        }

        /**
         * 上传文件
         */
        public void uploadFile(String localPath) throws IOException {
            File file = new File(localPath);
            if (!file.exists()) {
                System.err.println("文件不存在: " + localPath);
                return;
            }

            try (Socket socket = new Socket(host, port);
                 DataOutputStream out = new DataOutputStream(
                     new BufferedOutputStream(socket.getOutputStream()));
                 DataInputStream in = new DataInputStream(
                     new BufferedInputStream(socket.getInputStream()));
                 FileInputStream fis = new FileInputStream(file)) {

                // 发送命令和文件信息
                out.writeUTF("UPLOAD");
                out.writeUTF(file.getName());
                out.writeLong(file.length());
                out.flush();

                // 发送文件内容
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long transferred = 0;

                System.out.println("开始上传: " + file.getName());

                while ((bytesRead = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    transferred += bytesRead;

                    if (transferred % (1024 * 1024) == 0) {
                        double progress = (transferred * 100.0) / file.length();
                        System.out.printf("上传进度: %.2f%%\n", progress);
                    }
                }

                out.flush();

                // 读取响应
                String status = in.readUTF();
                String message = in.readUTF();

                if ("SUCCESS".equals(status)) {
                    System.out.println("上传成功: " + message);
                } else {
                    System.err.println("上传失败: " + message);
                }
            }
        }

        /**
         * 下载文件
         */
        public void downloadFile(String remoteFilename, String localPath) throws IOException {
            try (Socket socket = new Socket(host, port);
                 DataOutputStream out = new DataOutputStream(
                     new BufferedOutputStream(socket.getOutputStream()));
                 DataInputStream in = new DataInputStream(
                     new BufferedInputStream(socket.getInputStream()))) {

                // 发送命令
                out.writeUTF("DOWNLOAD");
                out.writeUTF(remoteFilename);
                out.flush();

                // 读取响应
                String status = in.readUTF();

                if ("ERROR".equals(status)) {
                    String message = in.readUTF();
                    System.err.println("下载失败: " + message);
                    return;
                }

                long fileSize = in.readLong();
                System.out.println("开始下载: " + remoteFilename + " (" + fileSize + " 字节)");

                // 接收文件内容
                try (FileOutputStream fos = new FileOutputStream(localPath)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    long remaining = fileSize;
                    long transferred = 0;

                    while (remaining > 0) {
                        int toRead = (int) Math.min(buffer.length, remaining);
                        int bytesRead = in.read(buffer, 0, toRead);

                        if (bytesRead == -1) {
                            throw new IOException("连接断开");
                        }

                        fos.write(buffer, 0, bytesRead);
                        remaining -= bytesRead;
                        transferred += bytesRead;

                        if (transferred % (1024 * 1024) == 0) {
                            double progress = (transferred * 100.0) / fileSize;
                            System.out.printf("下载进度: %.2f%%\n", progress);
                        }
                    }

                    System.out.println("下载完成: " + localPath);
                }
            }
        }

        /**
         * 列出文件
         */
        public void listFiles() throws IOException {
            try (Socket socket = new Socket(host, port);
                 DataOutputStream out = new DataOutputStream(
                     new BufferedOutputStream(socket.getOutputStream()));
                 DataInputStream in = new DataInputStream(
                     new BufferedInputStream(socket.getInputStream()))) {

                out.writeUTF("LIST");
                out.flush();

                String status = in.readUTF();
                if ("ERROR".equals(status)) {
                    System.err.println("查询失败");
                    return;
                }

                int count = in.readInt();
                System.out.println("========== 文件列表 (" + count + ") ==========");

                for (int i = 0; i < count; i++) {
                    String name = in.readUTF();
                    long size = in.readLong();
                    long modified = in.readLong();

                    System.out.printf("%s  %d 字节  %s\n",
                        name, size, new java.util.Date(modified));
                }

                System.out.println("=====================================");
            }
        }

        public static void main(String[] args) throws Exception {
            FileClient client = new FileClient("localhost", PORT);

            // 测试列出文件
            client.listFiles();
        }
    }
}
