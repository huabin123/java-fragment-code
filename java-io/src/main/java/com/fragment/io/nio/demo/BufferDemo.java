package com.fragment.io.nio.demo;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Buffer操作演示
 * 
 * <p>演示内容：
 * <ul>
 *   <li>Buffer的创建方式</li>
 *   <li>Buffer的读写操作</li>
 *   <li>flip()、clear()、compact()的使用</li>
 *   <li>mark()和reset()的使用</li>
 *   <li>HeapBuffer vs DirectBuffer</li>
 *   <li>Buffer的高级操作</li>
 * </ul>
 * 
 * @author fragment
 */
public class BufferDemo {

    public static void main(String[] args) {
        System.out.println("========== Buffer操作演示 ==========\n");

        // 演示1：Buffer的基本操作
        demonstrateBasicOperations();

        // 演示2：flip()、clear()、compact()
        demonstrateBufferMethods();

        // 演示3：mark()和reset()
        demonstrateMarkAndReset();

        // 演示4：HeapBuffer vs DirectBuffer
        demonstrateHeapVsDirect();

        // 演示5：Buffer的高级操作
        demonstrateAdvancedOperations();

        // 演示6：不同类型的Buffer
        demonstrateBufferTypes();

        System.out.println("\n========== 演示完成 ==========");
    }

    /**
     * 演示1：Buffer的基本操作
     */
    private static void demonstrateBasicOperations() {
        System.out.println("========== 演示1: Buffer基本操作 ==========\n");

        // 创建Buffer
        ByteBuffer buffer = ByteBuffer.allocate(10);
        System.out.println("创建Buffer，容量: " + buffer.capacity());
        printBufferState("初始状态", buffer);

        // 写入数据
        buffer.put((byte) 'H');
        buffer.put((byte) 'e');
        buffer.put((byte) 'l');
        buffer.put((byte) 'l');
        buffer.put((byte) 'o');
        printBufferState("写入'Hello'后", buffer);

        // 切换到读模式
        buffer.flip();
        printBufferState("flip()后", buffer);

        // 读取数据
        System.out.println("\n读取数据:");
        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            System.out.print((char) b);
        }
        System.out.println();
        printBufferState("读取完成后", buffer);

        System.out.println("\n" + createSeparator(60) + "\n");
    }

    /**
     * 演示2：flip()、clear()、compact()的区别
     */
    private static void demonstrateBufferMethods() {
        System.out.println("========== 演示2: flip()、clear()、compact() ==========\n");

        // flip()演示
        System.out.println("--- flip()演示 ---");
        ByteBuffer buffer1 = ByteBuffer.allocate(10);
        buffer1.put("Hello".getBytes());
        printBufferState("写入'Hello'后", buffer1);
        
        buffer1.flip();
        printBufferState("flip()后", buffer1);
        System.out.println("说明: flip()将limit设为position，position设为0，用于从写模式切换到读模式\n");

        // clear()演示
        System.out.println("--- clear()演示 ---");
        ByteBuffer buffer2 = ByteBuffer.allocate(10);
        buffer2.put("Hello".getBytes());
        buffer2.flip();
        buffer2.get(); // 读取1个字节
        buffer2.get(); // 读取1个字节
        printBufferState("读取2个字节后", buffer2);
        
        buffer2.clear();
        printBufferState("clear()后", buffer2);
        System.out.println("说明: clear()将position设为0，limit设为capacity，丢弃所有数据\n");

        // compact()演示
        System.out.println("--- compact()演示 ---");
        ByteBuffer buffer3 = ByteBuffer.allocate(10);
        buffer3.put("Hello".getBytes());
        buffer3.flip();
        buffer3.get(); // 读取'H'
        buffer3.get(); // 读取'e'
        printBufferState("读取2个字节后", buffer3);
        
        buffer3.compact();
        printBufferState("compact()后", buffer3);
        System.out.println("说明: compact()保留未读数据(llo)，将其移到开头，position设为未读数据长度");
        
        // 验证compact()保留了数据
        buffer3.flip();
        System.out.print("验证保留的数据: ");
        while (buffer3.hasRemaining()) {
            System.out.print((char) buffer3.get());
        }
        System.out.println("\n");

        System.out.println(createSeparator(60) + "\n");
    }

    /**
     * 演示3：mark()和reset()
     */
    private static void demonstrateMarkAndReset() {
        System.out.println("========== 演示3: mark()和reset() ==========\n");

        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("Hello".getBytes());
        buffer.flip();

        // 读取并标记
        System.out.println("读取数据并标记:");
        System.out.print((char) buffer.get()); // 'H'
        System.out.print((char) buffer.get()); // 'e'
        
        buffer.mark(); // 标记当前位置
        System.out.println(" <- 在这里mark()");
        printBufferState("mark()后", buffer);

        System.out.print("继续读取: ");
        System.out.print((char) buffer.get()); // 'l'
        System.out.print((char) buffer.get()); // 'l'
        System.out.println();
        printBufferState("继续读取后", buffer);

        // 重置到mark位置
        buffer.reset();
        System.out.println("\nreset()回到mark位置");
        printBufferState("reset()后", buffer);

        System.out.print("从mark位置重新读取: ");
        while (buffer.hasRemaining()) {
            System.out.print((char) buffer.get());
        }
        System.out.println("\n");

        System.out.println(createSeparator(60) + "\n");
    }

    /**
     * 演示4：HeapBuffer vs DirectBuffer
     */
    private static void demonstrateHeapVsDirect() {
        System.out.println("========== 演示4: HeapBuffer vs DirectBuffer ==========\n");

        // HeapBuffer
        System.out.println("--- HeapBuffer ---");
        ByteBuffer heapBuffer = ByteBuffer.allocate(1024);
        System.out.println("类型: " + heapBuffer.getClass().getSimpleName());
        System.out.println("isDirect: " + heapBuffer.isDirect());
        System.out.println("hasArray: " + heapBuffer.hasArray());
        if (heapBuffer.hasArray()) {
            System.out.println("可以访问底层数组: byte[] array = buffer.array()");
        }
        System.out.println();

        // DirectBuffer
        System.out.println("--- DirectBuffer ---");
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024);
        System.out.println("类型: " + directBuffer.getClass().getSimpleName());
        System.out.println("isDirect: " + directBuffer.isDirect());
        System.out.println("hasArray: " + directBuffer.hasArray());
        System.out.println("说明: DirectBuffer在堆外内存，不能直接访问数组");
        System.out.println();

        // 性能对比
        System.out.println("--- 性能对比 ---");
        int iterations = 100000;

        // HeapBuffer性能
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
        }
        long heapTime = System.nanoTime() - start;
        System.out.println("HeapBuffer创建时间: " + heapTime / 1000000 + "ms");

        // DirectBuffer性能
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
        }
        long directTime = System.nanoTime() - start;
        System.out.println("DirectBuffer创建时间: " + directTime / 1000000 + "ms");

        System.out.println("\n结论:");
        System.out.println("- HeapBuffer创建快，适合小数据、临时使用");
        System.out.println("- DirectBuffer创建慢，但I/O性能好，适合大数据、频繁I/O");
        System.out.println();

        System.out.println(createSeparator(60) + "\n");
    }

    /**
     * 演示5：Buffer的高级操作
     */
    private static void demonstrateAdvancedOperations() {
        System.out.println("========== 演示5: Buffer高级操作 ==========\n");

        // 批量操作
        System.out.println("--- 批量操作 ---");
        ByteBuffer buffer = ByteBuffer.allocate(20);
        
        byte[] data = "Hello World".getBytes();
        buffer.put(data); // 批量写入
        System.out.println("批量写入: " + new String(data));
        
        buffer.flip();
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result); // 批量读取
        System.out.println("批量读取: " + new String(result));
        System.out.println();

        // 相对操作 vs 绝对操作
        System.out.println("--- 相对操作 vs 绝对操作 ---");
        buffer.clear();
        buffer.put((byte) 'A'); // 相对操作，position: 0 -> 1
        buffer.put((byte) 'B'); // 相对操作，position: 1 -> 2
        System.out.println("相对操作后 position: " + buffer.position());
        
        buffer.put(5, (byte) 'X'); // 绝对操作，position不变
        System.out.println("绝对操作后 position: " + buffer.position());
        System.out.println();

        // 只读Buffer
        System.out.println("--- 只读Buffer ---");
        buffer.clear();
        buffer.put("Hello".getBytes());
        buffer.flip();
        
        ByteBuffer readOnlyBuffer = buffer.asReadOnlyBuffer();
        System.out.println("创建只读Buffer: " + readOnlyBuffer.isReadOnly());
        System.out.print("读取数据: ");
        while (readOnlyBuffer.hasRemaining()) {
            System.out.print((char) readOnlyBuffer.get());
        }
        System.out.println();
        
        try {
            readOnlyBuffer.put((byte) 'X');
        } catch (Exception e) {
            System.out.println("尝试写入只读Buffer: " + e.getClass().getSimpleName());
        }
        System.out.println();

        // Buffer切片
        System.out.println("--- Buffer切片 ---");
        buffer.clear();
        buffer.put("0123456789".getBytes());
        
        buffer.position(2);
        buffer.limit(7);
        ByteBuffer slice = buffer.slice();
        
        System.out.println("原Buffer: position=" + buffer.position() + ", limit=" + buffer.limit());
        System.out.println("切片Buffer: position=" + slice.position() + ", limit=" + slice.limit());
        System.out.print("切片内容: ");
        while (slice.hasRemaining()) {
            System.out.print((char) slice.get());
        }
        System.out.println(" (包含索引2-6)");
        
        // 修改切片会影响原Buffer
        slice.position(0);
        slice.put((byte) 'X');
        buffer.position(2);
        System.out.println("修改切片后，原Buffer[2]: " + (char) buffer.get());
        System.out.println();

        // Buffer复制
        System.out.println("--- Buffer复制 ---");
        buffer.clear();
        buffer.put("Hello".getBytes());
        buffer.flip();
        
        ByteBuffer duplicate = buffer.duplicate();
        System.out.println("原Buffer position: " + buffer.position());
        System.out.println("复制Buffer position: " + duplicate.position());
        
        duplicate.get(); // 移动复制Buffer的position
        System.out.println("移动复制Buffer后:");
        System.out.println("原Buffer position: " + buffer.position());
        System.out.println("复制Buffer position: " + duplicate.position());
        System.out.println("说明: duplicate()共享数据，但有独立的position、limit、mark");
        System.out.println();

        System.out.println(createSeparator(60) + "\n");
    }

    /**
     * 演示6：不同类型的Buffer
     */
    private static void demonstrateBufferTypes() {
        System.out.println("========== 演示6: 不同类型的Buffer ==========\n");

        // ByteBuffer
        System.out.println("--- ByteBuffer ---");
        ByteBuffer byteBuffer = ByteBuffer.allocate(8);
        byteBuffer.put((byte) 1);
        byteBuffer.putShort((short) 2);
        byteBuffer.putInt(3);
        System.out.println("写入: byte(1), short(2), int(3)");
        
        byteBuffer.flip();
        System.out.println("读取: " + byteBuffer.get() + ", " + 
                         byteBuffer.getShort() + ", " + byteBuffer.getInt());
        System.out.println();

        // CharBuffer
        System.out.println("--- CharBuffer ---");
        CharBuffer charBuffer = CharBuffer.allocate(10);
        charBuffer.put("Hello");
        charBuffer.flip();
        System.out.print("CharBuffer内容: ");
        while (charBuffer.hasRemaining()) {
            System.out.print(charBuffer.get());
        }
        System.out.println();
        System.out.println();

        // IntBuffer
        System.out.println("--- IntBuffer ---");
        IntBuffer intBuffer = IntBuffer.allocate(5);
        intBuffer.put(1);
        intBuffer.put(2);
        intBuffer.put(3);
        intBuffer.flip();
        System.out.print("IntBuffer内容: ");
        while (intBuffer.hasRemaining()) {
            System.out.print(intBuffer.get() + " ");
        }
        System.out.println();
        System.out.println();

        // ByteBuffer包装数组
        System.out.println("--- ByteBuffer.wrap() ---");
        byte[] array = "Wrapped".getBytes();
        ByteBuffer wrapped = ByteBuffer.wrap(array);
        System.out.println("包装数组: " + new String(array));
        System.out.println("Buffer内容: " + StandardCharsets.UTF_8.decode(wrapped));
        
        // 修改数组会影响Buffer
        array[0] = 'M';
        wrapped.position(0);
        System.out.println("修改数组后Buffer内容: " + StandardCharsets.UTF_8.decode(wrapped));
        System.out.println();

        System.out.println(createSeparator(60) + "\n");
    }

    /**
     * 打印Buffer状态
     */
    private static void printBufferState(String label, ByteBuffer buffer) {
        System.out.println(label + ":");
        System.out.println("  position: " + buffer.position());
        System.out.println("  limit: " + buffer.limit());
        System.out.println("  capacity: " + buffer.capacity());
        System.out.println("  remaining: " + buffer.remaining());
    }

    /**
     * 创建分隔线
     */
    private static String createSeparator(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append("=");
        }
        return sb.toString();
    }
}
