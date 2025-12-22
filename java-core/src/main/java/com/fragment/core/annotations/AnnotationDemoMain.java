package com.fragment.core.annotations;

import java.util.Arrays;
import java.util.List;

@Role("demo")
public class AnnotationDemoMain {
    public static void main(String[] args) {
        // 1) Inspect annotations on AnnotatedUser
        AnnotationInspector.inspectAll(AnnotatedUser.class);

        // 2) Demonstrate dynamic proxy with @LogExecutionTime
        ExampleService svc = ProxyFactory.createLoggingProxy(new ExampleServiceImpl(), ExampleService.class);
        System.out.println("greet => " + svc.greet("World"));
        System.out.println("sum => " + svc.sum(1, 2));

        // 3) Create instance to trigger type-use presence
        List<String> tags = Arrays.asList("a", "b");
        AnnotatedUser u = new AnnotatedUser(1L, tags);
        System.out.println("user.id=" + u.getId() + ", tags=" + u.getTags());
    }
}
