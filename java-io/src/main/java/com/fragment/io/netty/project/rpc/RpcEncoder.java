package com.fragment.io.netty.project.rpc;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

/**
 * RPC编码器
 * 
 * 协议格式：
 * ┌──────┬─────┬──────┬────────┬──────┬─────────┐
 * │ 魔数 │版本 │ 类型 │ 请求ID │ 长度 │  数据    │
 * │ 2字节│1字节│1字节 │ 8字节  │4字节 │ N字节   │
 * └──────┴─────┴──────┴────────┴──────┴─────────┘
 * 
 * @author fragment
 * @date 2026-01-14
 */
public class RpcEncoder extends MessageToByteEncoder<Object> {
    
    private static final short MAGIC_NUMBER = (short) 0xCAFE;
    private static final byte VERSION = 0x01;
    private static final byte TYPE_REQUEST = 0x01;
    private static final byte TYPE_RESPONSE = 0x02;
    
    private Class<?> genericClass;
    
    public RpcEncoder(Class<?> genericClass) {
        this.genericClass = genericClass;
    }
    
    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        if (genericClass.isInstance(msg)) {
            // 1. 魔数
            out.writeShort(MAGIC_NUMBER);
            
            // 2. 版本
            out.writeByte(VERSION);
            
            // 3. 类型
            byte type = msg instanceof RpcRequest ? TYPE_REQUEST : TYPE_RESPONSE;
            out.writeByte(type);
            
            // 4. 请求ID
            String requestId = msg instanceof RpcRequest 
                ? ((RpcRequest) msg).getRequestId()
                : ((RpcResponse) msg).getRequestId();
            out.writeLong(Long.parseLong(requestId));
            
            // 5. 序列化数据
            byte[] data = serialize(msg);
            
            // 6. 长度
            out.writeInt(data.length);
            
            // 7. 数据
            out.writeBytes(data);
        }
    }
    
    /**
     * 序列化对象
     */
    private byte[] serialize(Object obj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.close();
        return baos.toByteArray();
    }
}
