package com.fragment.redis;

/**
 * @Author huabin
 * @DateTime 2024-05-29 14:59
 * @Desc
 */

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

import java.util.List;

public class JedisPipelineExample {

    public static void main(String[] args) {
        Jedis jedis = new Jedis("localhost", 6379);

        // 创建 Pipeline 对象
        Pipeline pipeline = jedis.pipelined();

        // 需要删除的键列表
        String[] keysToDelete = {"key1", "key2", "key3"};

        // 使用 Pipeline 批量发送 DEL 命令
        for (String key : keysToDelete) {
            pipeline.del(key);
        }

        // 执行 Pipeline 中的所有命令
        List<Object> results = pipeline.syncAndReturnAll();

        // 检查删除结果
        for (Object result : results) {
            System.out.println("Delete result: " + result);
        }

        // 关闭连接
        jedis.close();
    }
}
