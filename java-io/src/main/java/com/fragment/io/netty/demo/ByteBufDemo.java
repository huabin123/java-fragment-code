package com.fragment.io.netty.demo;

import io.netty.buffer.*;
import io.netty.util.CharsetUtil;

/**
 * ByteBuf操作演示
 * 
 * 功能：
 * 1. 演示ByteBuf的基本操作
 * 2. 演示ByteBuf vs ByteBuffer的区别
 * 3. 演示引用计数机制
 * 4. 演示零拷贝操作
 * 
 * 运行方式：
 * 直接运行main方法
 * 
 * @author fragment
 * @date 2026-01-14
 */
public class ByteBufDemo {
    
    public static void main(String[] args) {
        System.out.println("=== ByteBuf操作演示 ===\n");
        
        // 1. ByteBuf的基本操作
        basicOperations();
        
        // 2. ByteBuf vs ByteBuffer
        compareWithByteBuffer();
        
        // 3. 引用计数
        referenceCount();
        
        // 4. 零拷贝
        zeroCopy();
        
        // 5. 池化
        pooling();
    }
    
    /**
     * 1. ByteBuf的基本操作
     */
    private static void basicOperations() {
        System.out.println("1. ByteBuf的基本操作");
        System.out.println("─────────────────────");
        
        // 创建ByteBuf
        ByteBuf buffer = Unpooled.buffer(10);
        System.out.println("初始状态:");
        printBufferInfo(buffer);
        
        // 写入数据
        buffer.writeBytes("Hello".getBytes());
        System.out.println("\n写入'Hello'后:");
        printBufferInfo(buffer);
        
        // 读取数据
        byte[] data = new byte[5];
        buffer.readBytes(data);
        System.out.println("\n读取5字节后:");
        System.out.println("读取的数据: " + new String(data));
        printBufferInfo(buffer);
        
        // 继续写入
        buffer.writeBytes(" World".getBytes());
        System.out.println("\n写入' World'后:");
        printBufferInfo(buffer);
        
        // 清空
        buffer.clear();
        System.out.println("\nclear()后:");
        printBufferInfo(buffer);
        
        buffer.release();
        System.out.println();
    }
    
    /**
     * 2. ByteBuf vs ByteBuffer
     */
    private static void compareWithByteBuffer() {
        System.out.println("2. ByteBuf vs ByteBuffer");
        System.out.println("─────────────────────");
        
        ByteBuf buf = Unpooled.buffer();
        
        // ByteBuf不需要flip()
        System.out.println("ByteBuf的优势：双指针，不需要flip()");
        buf.writeBytes("Hello".getBytes());
        System.out.println("写入后 - readerIndex: " + buf.readerIndex() + ", writerIndex: " + buf.writerIndex());
        
        byte[] data = new byte[5];
        buf.readBytes(data);
        System.out.println("读取后 - readerIndex: " + buf.readerIndex() + ", writerIndex: " + buf.writerIndex());
        System.out.println("读取的数据: " + new String(data));
        
        // 可以继续写入
        buf.writeBytes(" World".getBytes());
        System.out.println("继续写入后 - readerIndex: " + buf.readerIndex() + ", writerIndex: " + buf.writerIndex());
        
        buf.release();
        System.out.println();
    }
    
    /**
     * 3. 引用计数
     */
    private static void referenceCount() {
        System.out.println("3. 引用计数机制");
        System.out.println("─────────────────────");
        
        ByteBuf buf = Unpooled.buffer();
        System.out.println("创建后 - refCnt: " + buf.refCnt());
        
        // 增加引用
        buf.retain();
        System.out.println("retain()后 - refCnt: " + buf.refCnt());
        
        buf.retain();
        System.out.println("再次retain()后 - refCnt: " + buf.refCnt());
        
        // 释放引用
        buf.release();
        System.out.println("release()后 - refCnt: " + buf.refCnt());
        
        buf.release();
        System.out.println("再次release()后 - refCnt: " + buf.refCnt());
        
        buf.release();
        System.out.println("最后release()后 - refCnt: " + buf.refCnt() + " (已释放)\n");
    }
    
    /**
     * 4. 零拷贝
     */
    private static void zeroCopy() {
        System.out.println("4. 零拷贝操作");
        System.out.println("─────────────────────");
        
        // 4.1 slice（切片）
        System.out.println("4.1 slice（切片）- 零拷贝");
        ByteBuf buffer = Unpooled.copiedBuffer("Hello World", CharsetUtil.UTF_8);
        ByteBuf slice = buffer.slice(0, 5);
        System.out.println("原始数据: " + buffer.toString(CharsetUtil.UTF_8));
        System.out.println("切片数据: " + slice.toString(CharsetUtil.UTF_8));
        
        // 修改切片会影响原始buffer
        slice.setByte(0, 'h');
        System.out.println("修改切片后:");
        System.out.println("原始数据: " + buffer.toString(CharsetUtil.UTF_8));
        System.out.println("切片数据: " + slice.toString(CharsetUtil.UTF_8));
        buffer.release();
        
        // 4.2 duplicate（复制）
        System.out.println("\n4.2 duplicate（复制）- 零拷贝");
        ByteBuf original = Unpooled.copiedBuffer("Netty", CharsetUtil.UTF_8);
        ByteBuf duplicate = original.duplicate();
        System.out.println("原始数据: " + original.toString(CharsetUtil.UTF_8));
        System.out.println("复制数据: " + duplicate.toString(CharsetUtil.UTF_8));
        System.out.println("共享数据: " + (original.array() == duplicate.array()));
        original.release();
        
        // 4.3 CompositeByteBuf（组合）
        System.out.println("\n4.3 CompositeByteBuf（组合）- 零拷贝");
        ByteBuf header = Unpooled.copiedBuffer("Header", CharsetUtil.UTF_8);
        ByteBuf body = Unpooled.copiedBuffer("Body", CharsetUtil.UTF_8);
        
        CompositeByteBuf composite = Unpooled.compositeBuffer();
        composite.addComponents(true, header, body);
        
        System.out.println("组合后的数据: " + composite.toString(CharsetUtil.UTF_8));
        System.out.println("组件数量: " + composite.numComponents());
        composite.release();
        
        System.out.println();
    }
    
    /**
     * 5. 池化
     */
    private static void pooling() {
        System.out.println("5. 池化ByteBuf");
        System.out.println("─────────────────────");
        
        // 非池化
        ByteBuf unpooled = Unpooled.buffer(1024);
        System.out.println("非池化ByteBuf: " + unpooled.getClass().getSimpleName());
        unpooled.release();
        
        // 池化
        ByteBuf pooled = PooledByteBufAllocator.DEFAULT.buffer(1024);
        System.out.println("池化ByteBuf: " + pooled.getClass().getSimpleName());
        pooled.release();
        
        // 直接内存
        ByteBuf direct = PooledByteBufAllocator.DEFAULT.directBuffer(1024);
        System.out.println("直接内存ByteBuf: " + direct.getClass().getSimpleName());
        System.out.println("是否直接内存: " + direct.isDirect());
        direct.release();
        
        System.out.println();
    }
    
    /**
     * 打印ByteBuf信息
     */
    private static void printBufferInfo(ByteBuf buffer) {
        System.out.println("  readerIndex: " + buffer.readerIndex());
        System.out.println("  writerIndex: " + buffer.writerIndex());
        System.out.println("  capacity: " + buffer.capacity());
        System.out.println("  maxCapacity: " + buffer.maxCapacity());
        System.out.println("  readableBytes: " + buffer.readableBytes());
        System.out.println("  writableBytes: " + buffer.writableBytes());
    }
}
