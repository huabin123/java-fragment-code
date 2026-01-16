package com.fragment.io.netty.project.rpc;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * RPC解码器
 * 
 * @author fragment
 * @date 2026-01-14
 */
public class RpcDecoder extends ByteToMessageDecoder {
    
    private static final short MAGIC_NUMBER = (short) 0xCAFE;
    private static final int HEADER_LENGTH = 16;  // 魔数(2) + 版本(1) + 类型(1) + 请求ID(8) + 长度(4)
    
    private Class<?> genericClass;
    
    public RpcDecoder(Class<?> genericClass) {
        this.genericClass = genericClass;
    }
    
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 1. 检查是否有足够的数据读取消息头
        if (in.readableBytes() < HEADER_LENGTH) {
            return;
        }
        
        // 2. 标记读指针位置
        in.markReaderIndex();
        
        // 3. 读取魔数
        short magic = in.readShort();
        if (magic != MAGIC_NUMBER) {
            in.resetReaderIndex();
            throw new RuntimeException("Invalid magic number: " + magic);
        }
        
        // 4. 读取版本
        byte version = in.readByte();
        
        // 5. 读取类型
        byte type = in.readByte();
        
        // 6. 读取请求ID
        long requestId = in.readLong();
        
        // 7. 读取长度
        int dataLength = in.readInt();
        
        // 8. 检查数据是否完整
        if (in.readableBytes() < dataLength) {
            in.resetReaderIndex();
            return;
        }
        
        // 9. 读取数据
        byte[] data = new byte[dataLength];
        in.readBytes(data);
        
        // 10. 反序列化
        Object obj = deserialize(data, genericClass);
        out.add(obj);
    }
    
    /**
     * 反序列化对象
     */
    private Object deserialize(byte[] data, Class<?> clazz) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bais);
        Object obj = ois.readObject();
        ois.close();
        return obj;
    }
}
