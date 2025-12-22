package com.fragment.core.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * 简单的AOP框架实现
 */
public class AopFramework {

    /**
     * 创建AOP代理
     */
    @SuppressWarnings("unchecked")
    public static <T> T createProxy(T target, Aspect... aspects) {
        return (T) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                target.getClass().getInterfaces(),
                new AopInvocationHandler(target, aspects)
        );
    }

    /**
     * AOP调用处理器
     */
    static class AopInvocationHandler implements InvocationHandler {
        private final Object target;
        private final List<Aspect> aspects;

        public AopInvocationHandler(Object target, Aspect... aspects) {
            this.target = target;
            this.aspects = new ArrayList<>();
            for (Aspect aspect : aspects) {
                this.aspects.add(aspect);
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 前置通知
            for (Aspect aspect : aspects) {
                aspect.before(method, args);
            }

            Object result = null;
            Throwable exception = null;

            try {
                // 执行目标方法
                result = method.invoke(target, args);
                
                // 返回后通知
                for (Aspect aspect : aspects) {
                    aspect.afterReturning(method, args, result);
                }
            } catch (Throwable e) {
                exception = e;
                
                // 异常通知
                for (Aspect aspect : aspects) {
                    aspect.afterThrowing(method, args, e);
                }
                throw e;
            } finally {
                // 最终通知
                for (Aspect aspect : aspects) {
                    aspect.afterFinally(method, args, result, exception);
                }
            }

            return result;
        }
    }

    /**
     * 切面接口
     */
    public interface Aspect {
        default void before(Method method, Object[] args) {}
        default void afterReturning(Method method, Object[] args, Object result) {}
        default void afterThrowing(Method method, Object[] args, Throwable exception) {}
        default void afterFinally(Method method, Object[] args, Object result, Throwable exception) {}
    }

    /**
     * 日志切面
     */
    public static class LoggingAspect implements Aspect {
        @Override
        public void before(Method method, Object[] args) {
            System.out.println("[LOG] 开始执行: " + method.getName());
        }

        @Override
        public void afterReturning(Method method, Object[] args, Object result) {
            System.out.println("[LOG] 执行成功: " + method.getName());
        }

        @Override
        public void afterThrowing(Method method, Object[] args, Throwable exception) {
            System.out.println("[LOG] 执行异常: " + method.getName() + ", " + exception.getMessage());
        }
    }

    /**
     * 性能监控切面
     */
    public static class PerformanceAspect implements Aspect {
        private long startTime;

        @Override
        public void before(Method method, Object[] args) {
            startTime = System.currentTimeMillis();
        }

        @Override
        public void afterFinally(Method method, Object[] args, Object result, Throwable exception) {
            long duration = System.currentTimeMillis() - startTime;
            System.out.println("[PERF] " + method.getName() + " 耗时: " + duration + "ms");
        }
    }
}
