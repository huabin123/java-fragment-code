package com.fragment.juc.sync.demo;

import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Exchanger交换器演示
 * 
 * 演示内容：
 * 1. 基本使用：两个线程交换数据
 * 2. 超时处理
 * 3. 实际应用：生产者-消费者、数据校对
 * 
 * @author huabin
 */
public class ExchangerDemo {

    /**
     * 演示1：基本使用 - 两个线程交换数据
     */
    public static void demoBasicUsage() throws InterruptedException {
        System.out.println("\n========== 演示1：基本使用 ==========\n");

        Exchanger<String> exchanger = new Exchanger<>();

        // 线程1
        Thread thread1 = new Thread(() -> {
            try {
                String data = "来自线程1的数据";
                System.out.println("[线程1] 准备交换数据: " + data);
                
                String received = exchanger.exchange(data);
                
                System.out.println("[线程1] 收到数据: " + received);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Thread-1");

        // 线程2
        Thread thread2 = new Thread(() -> {
            try {
                Thread.sleep(1000); // 延迟1秒
                String data = "来自线程2的数据";
                System.out.println("[线程2] 准备交换数据: " + data);
                
                String received = exchanger.exchange(data);
                
                System.out.println("[线程2] 收到数据: " + received);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Thread-2");

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        System.out.println("\n✅ Exchanger实现了两个线程的数据交换");
    }

    /**
     * 演示2：超时处理
     */
    public static void demoTimeout() throws InterruptedException {
        System.out.println("\n========== 演示2：超时处理 ==========\n");

        Exchanger<String> exchanger = new Exchanger<>();

        // 线程1：正常交换
        Thread thread1 = new Thread(() -> {
            try {
                String data = "线程1的数据";
                System.out.println("[线程1] 准备交换，最多等待2秒...");
                
                String received = exchanger.exchange(data, 2, TimeUnit.SECONDS);
                
                System.out.println("[线程1] 交换成功: " + received);
            } catch (TimeoutException e) {
                System.out.println("[线程1] 超时！没有其他线程来交换");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Thread-1");

        thread1.start();
        thread1.join();

        System.out.println("\n✅ exchange(timeout)可以避免无限期等待");
    }

    /**
     * 演示3：多次交换
     */
    public static void demoMultipleExchanges() throws InterruptedException {
        System.out.println("\n========== 演示3：多次交换 ==========\n");

        Exchanger<Integer> exchanger = new Exchanger<>();

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    System.out.println("[生产者] 生产数据: " + i);
                    Integer received = exchanger.exchange(i);
                    System.out.println("[生产者] 收到确认: " + received);
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Producer");

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    Integer data = exchanger.exchange(i * 100);
                    System.out.println("[消费者] 收到数据: " + data);
                    System.out.println("[消费者] 发送确认: " + (i * 100));
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Consumer");

        producer.start();
        consumer.start();

        producer.join();
        consumer.join();

        System.out.println("\n✅ Exchanger可以重复使用");
    }

    /**
     * 演示4：实际应用 - 缓冲区交换
     */
    public static void demoBufferExchange() throws InterruptedException {
        System.out.println("\n========== 演示4：缓冲区交换 ==========\n");

        class Buffer {
            private String[] data;
            private int count;

            Buffer(int size) {
                this.data = new String[size];
                this.count = 0;
            }

            void add(String item) {
                if (count < data.length) {
                    data[count++] = item;
                }
            }

            boolean isFull() {
                return count == data.length;
            }

            void clear() {
                count = 0;
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < count; i++) {
                    sb.append(data[i]);
                    if (i < count - 1) sb.append(", ");
                }
                sb.append("]");
                return sb.toString();
            }
        }

        Exchanger<Buffer> exchanger = new Exchanger<>();

        // 生产者：填充缓冲区
        Thread producer = new Thread(() -> {
            Buffer currentBuffer = new Buffer(3);
            try {
                for (int i = 1; i <= 10; i++) {
                    currentBuffer.add("Item-" + i);
                    System.out.println("[生产者] 添加: Item-" + i + 
                                     " (缓冲区: " + currentBuffer.count + "/3)");

                    if (currentBuffer.isFull()) {
                        System.out.println("[生产者] 缓冲区已满，交换...");
                        currentBuffer = exchanger.exchange(currentBuffer);
                        System.out.println("[生产者] 获得空缓冲区");
                    }

                    Thread.sleep(200);
                }

                // 交换最后的部分填充缓冲区
                if (currentBuffer.count > 0) {
                    System.out.println("[生产者] 交换最后的缓冲区");
                    exchanger.exchange(currentBuffer);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Producer");

        // 消费者：处理缓冲区
        Thread consumer = new Thread(() -> {
            Buffer emptyBuffer = new Buffer(3);
            try {
                while (true) {
                    Buffer fullBuffer = exchanger.exchange(emptyBuffer);
                    System.out.println("[消费者] 收到缓冲区: " + fullBuffer);
                    
                    // 处理数据
                    Thread.sleep(500);
                    
                    fullBuffer.clear();
                    emptyBuffer = fullBuffer;
                    System.out.println("[消费者] 处理完成，返回空缓冲区\n");
                }
            } catch (InterruptedException e) {
                System.out.println("[消费者] 结束");
            }
        }, "Consumer");

        producer.start();
        consumer.start();

        producer.join();
        Thread.sleep(1000);
        consumer.interrupt();

        System.out.println("✅ Exchanger适合实现缓冲区交换");
    }

    /**
     * 演示5：实际应用 - 数据校对
     */
    public static void demoDataValidation() throws InterruptedException {
        System.out.println("\n========== 演示5：数据校对 ==========\n");

        Exchanger<String> exchanger = new Exchanger<>();

        // 系统A：从数据库读取
        Thread systemA = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    System.out.println("[系统A] 从数据库读取记录" + i);
                    Thread.sleep(300);
                    String data = "Record-" + i + "-DB";
                    
                    String otherData = exchanger.exchange(data);
                    
                    if (data.substring(0, 9).equals(otherData.substring(0, 9))) {
                        System.out.println("[系统A] ✅ 记录" + i + "校对一致");
                    } else {
                        System.out.println("[系统A] ❌ 记录" + i + "校对不一致");
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "System-A");

        // 系统B：从文件读取
        Thread systemB = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    System.out.println("[系统B] 从文件读取记录" + i);
                    Thread.sleep(300);
                    String data = "Record-" + i + "-File";
                    
                    String otherData = exchanger.exchange(data);
                    
                    if (data.substring(0, 9).equals(otherData.substring(0, 9))) {
                        System.out.println("[系统B] ✅ 记录" + i + "校对一致");
                    } else {
                        System.out.println("[系统B] ❌ 记录" + i + "校对不一致");
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "System-B");

        systemA.start();
        systemB.start();

        systemA.join();
        systemB.join();

        System.out.println("\n✅ Exchanger适合数据校对场景");
    }

    /**
     * 演示6：实际应用 - 遗传算法
     */
    public static void demoGeneticAlgorithm() throws InterruptedException {
        System.out.println("\n========== 演示6：遗传算法 ==========\n");

        class Gene {
            String dna;
            int fitness;

            Gene(String dna) {
                this.dna = dna;
                this.fitness = calculateFitness();
            }

            int calculateFitness() {
                return dna.length();
            }

            Gene crossover(Gene other) {
                int mid = dna.length() / 2;
                String newDna = dna.substring(0, mid) + other.dna.substring(mid);
                return new Gene(newDna);
            }

            @Override
            public String toString() {
                return "Gene{dna='" + dna + "', fitness=" + fitness + "}";
            }
        }

        Exchanger<Gene> exchanger = new Exchanger<>();

        // 种群A
        Thread populationA = new Thread(() -> {
            try {
                Gene gene = new Gene("AAAA");
                System.out.println("[种群A] 初始基因: " + gene);

                for (int generation = 1; generation <= 3; generation++) {
                    System.out.println("\n[种群A] 第" + generation + "代，交换基因...");
                    Gene otherGene = exchanger.exchange(gene);
                    
                    gene = gene.crossover(otherGene);
                    System.out.println("[种群A] 新基因: " + gene);
                    
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Population-A");

        // 种群B
        Thread populationB = new Thread(() -> {
            try {
                Gene gene = new Gene("BBBB");
                System.out.println("[种群B] 初始基因: " + gene);

                for (int generation = 1; generation <= 3; generation++) {
                    System.out.println("\n[种群B] 第" + generation + "代，交换基因...");
                    Gene otherGene = exchanger.exchange(gene);
                    
                    gene = gene.crossover(otherGene);
                    System.out.println("[种群B] 新基因: " + gene);
                    
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Population-B");

        populationA.start();
        populationB.start();

        populationA.join();
        populationB.join();

        System.out.println("\n✅ Exchanger适合遗传算法的基因交换");
    }

    /**
     * 总结
     */
    public static void summarize() {
        System.out.println("\n========== Exchanger总结 ==========");

        System.out.println("\n✅ 核心特性:");
        System.out.println("   1. 两两交换：只能两个线程交换数据");
        System.out.println("   2. 同步点：exchange()是同步的");
        System.out.println("   3. 可重用：可以多次交换");
        System.out.println("   4. 泛型：支持任意类型数据");

        System.out.println("\n📊 核心方法:");
        System.out.println("   exchange(V)        - 交换数据（阻塞）");
        System.out.println("   exchange(V, timeout) - 超时交换");

        System.out.println("\n💡 适用场景:");
        System.out.println("   ✅ 两个线程交换数据");
        System.out.println("   ✅ 缓冲区交换（双缓冲）");
        System.out.println("   ✅ 数据校对");
        System.out.println("   ✅ 遗传算法");
        System.out.println("   ✅ 流水线处理");

        System.out.println("\n⚠️  注意事项:");
        System.out.println("   1. 只能两个线程使用");
        System.out.println("   2. 如果只有一个线程会阻塞");
        System.out.println("   3. 建议使用超时版本");
        System.out.println("   4. 交换的数据可以是null");

        System.out.println("\n🔄 vs 其他工具:");
        System.out.println("   Exchanger:");
        System.out.println("     - 两个线程交换数据");
        System.out.println("     - 双向传递");
        System.out.println("   BlockingQueue:");
        System.out.println("     - 多个线程传递数据");
        System.out.println("     - 单向传递");

        System.out.println("\n💡 使用建议:");
        System.out.println("   1. 适合一对一的数据交换");
        System.out.println("   2. 可以实现双缓冲机制");
        System.out.println("   3. 注意超时处理");
        System.out.println("   4. 多线程场景考虑使用队列");

        System.out.println("===========================");
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              Exchanger交换器演示                            ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");

        // 演示1：基本使用
        demoBasicUsage();

        // 演示2：超时处理
        demoTimeout();

        // 演示3：多次交换
        demoMultipleExchanges();

        // 演示4：缓冲区交换
        demoBufferExchange();

        // 演示5：数据校对
        demoDataValidation();

        // 演示6：遗传算法
        demoGeneticAlgorithm();

        // 总结
        summarize();

        System.out.println("\n" + "===========================");
        System.out.println("核心要点：");
        System.out.println("1. Exchanger用于两个线程交换数据");
        System.out.println("2. 适合双缓冲、数据校对等场景");
        System.out.println("3. exchange()是同步的交换点");
        System.out.println("4. 建议使用超时版本避免死锁");
        System.out.println("===========================");
    }
}
