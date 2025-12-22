package com.fragment.core.annotations;

public class ExampleServiceImpl implements ExampleService {

    @Override
    public String greet(String name) {
        return "Hello, " + name;
    }

    @Override
    @LogExecutionTime
    public int sum(int a, int b) {
        try { Thread.sleep(20); } catch (InterruptedException ignored) {}
        return a + b;
    }
}
