package com.fragment.core.annotations;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class ProxyFactory {
    private ProxyFactory() {}

    @SuppressWarnings("unchecked")
    public static <T> T createLoggingProxy(T target, Class<T> iface) {
        return (T) Proxy.newProxyInstance(
            iface.getClassLoader(),
            new Class<?>[]{iface},
            new LogExecutionTimeHandler(target)
        );
    }

    private static class LogExecutionTimeHandler implements InvocationHandler {
        private final Object target;

        LogExecutionTimeHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Method implMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
            boolean enabled = implMethod.isAnnotationPresent(LogExecutionTime.class) || method.isAnnotationPresent(LogExecutionTime.class);
            if (!enabled) {
                return implMethod.invoke(target, args);
            }
            long start = System.nanoTime();
            try {
                return implMethod.invoke(target, args);
            } finally {
                long durMicros = (System.nanoTime() - start) / 1000;
                System.out.println("[LogExecutionTime] " + implMethod.getDeclaringClass().getSimpleName()
                        + "." + implMethod.getName() + " took " + durMicros + " µs");
            }
        }
    }
}
