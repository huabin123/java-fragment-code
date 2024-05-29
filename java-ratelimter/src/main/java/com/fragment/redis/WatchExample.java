package com.fragment.redis;

/**
 * @Author huabin
 * @DateTime 2024-05-29 14:38
 * @Desc
 */

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

public class WatchExample {
    public static void main(String[] args) {
        Jedis jedis = new Jedis("localhost", 6379);

        // 监视键 "foo"
        jedis.watch("foo");

        // 开始事务
        Transaction transaction = jedis.multi();

        // 在事务中执行命令
        transaction.set("foo", "bar");
        transaction.incr("counter");

        // 提交事务，如果有另外一个县城改了修改了foo的值，则失败
        if (transaction.exec() == null) {
            System.out.println("Transaction failed due to concurrent modification.");
        } else {
            System.out.println("Transaction succeeded.");
        }

        jedis.close();
    }
}

