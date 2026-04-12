package com.fragment.core.collections.treemap.project;

import java.util.*;

/**
 * 价格区间索引
 *
 * 使用 TreeMap 实现商品价格区间的快速查询：
 * 给定价格，快速找到所属价格段及该段的商品列表。
 */
public class RangePriceIndex {

    // key = 价格区间下限，value = 该区间的商品列表
    private final TreeMap<Double, List<Product>> index = new TreeMap<>();

    public static void main(String[] args) {
        RangePriceIndex idx = new RangePriceIndex();

        idx.addBracket(0,    "经济");
        idx.addBracket(100,  "实惠");
        idx.addBracket(500,  "中档");
        idx.addBracket(1000, "高档");
        idx.addBracket(5000, "奢华");

        idx.addProduct(new Product("铅笔", 2.5));
        idx.addProduct(new Product("钢笔", 88.0));
        idx.addProduct(new Product("手表A", 350.0));
        idx.addProduct(new Product("手表B", 680.0));
        idx.addProduct(new Product("手机", 3999.0));
        idx.addProduct(new Product("笔记本", 8999.0));

        System.out.println("=== 价格区间索引 ===");
        idx.printAll();

        System.out.println("\n=== 查询 500 元的商品所属区间 ===");
        System.out.println(idx.getBracketName(500.0) + ": " + idx.getProducts(500.0));

        System.out.println("\n=== 查询 999 元的商品所属区间 ===");
        System.out.println(idx.getBracketName(999.0) + ": " + idx.getProducts(999.0));

        System.out.println("\n=== 1000~5000 区间的所有商品 ===");
        idx.getProductsInRange(1000.0, 5000.0).forEach(System.out::println);
    }

    public void addBracket(double lowerBound, String name) {
        index.put(lowerBound, new ArrayList<>());
        bracketNames.put(lowerBound, name);
    }

    public void addProduct(Product product) {
        Double key = index.floorKey(product.getPrice());
        if (key != null) index.get(key).add(product);
    }

    public List<Product> getProducts(double price) {
        Double key = index.floorKey(price);
        return key != null ? index.get(key) : Collections.emptyList();
    }

    public String getBracketName(double price) {
        Double key = index.floorKey(price);
        return key != null ? bracketNames.get(key) : "未知";
    }

    public List<Product> getProductsInRange(double from, double to) {
        List<Product> result = new ArrayList<>();
        index.subMap(from, true, to, true).values().forEach(result::addAll);
        return result;
    }

    public void printAll() {
        index.forEach((price, products) ->
            System.out.printf("  [%.0f+] %s: %s%n",
                price, bracketNames.get(price), products));
    }

    private final TreeMap<Double, String> bracketNames = new TreeMap<>();

    public static class Product {
        private final String name;
        private final double price;

        public Product(String name, double price) {
            this.name = name;
            this.price = price;
        }

        public double getPrice() { return price; }

        @Override
        public String toString() {
            return name + "(" + price + "元)";
        }
    }
}
