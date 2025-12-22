package com.fragment.core.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于反射的 Bean 拷贝工具
 */
public class BeanCopyUtil {

    private static final Map<String, Method> methodCache = new HashMap<>();

    /**
     * 浅拷贝对象属性
     */
    public static <T> T copyProperties(Object source, Class<T> targetClass) throws Exception {
        if (source == null) {
            return null;
        }

        T target = targetClass.getDeclaredConstructor().newInstance();
        
        Field[] sourceFields = source.getClass().getDeclaredFields();
        Field[] targetFields = targetClass.getDeclaredFields();

        Map<String, Field> targetFieldMap = new HashMap<>();
        for (Field field : targetFields) {
            targetFieldMap.put(field.getName(), field);
        }

        for (Field sourceField : sourceFields) {
            Field targetField = targetFieldMap.get(sourceField.getName());
            if (targetField != null && sourceField.getType().equals(targetField.getType())) {
                sourceField.setAccessible(true);
                targetField.setAccessible(true);
                
                Object value = sourceField.get(source);
                targetField.set(target, value);
            }
        }

        return target;
    }

    /**
     * 通过 getter/setter 拷贝属性
     */
    public static <T> T copyPropertiesByMethod(Object source, Class<T> targetClass) throws Exception {
        if (source == null) {
            return null;
        }

        T target = targetClass.getDeclaredConstructor().newInstance();
        
        Method[] sourceMethods = source.getClass().getMethods();
        Method[] targetMethods = targetClass.getMethods();

        Map<String, Method> setterMap = new HashMap<>();
        for (Method method : targetMethods) {
            if (method.getName().startsWith("set") && method.getParameterCount() == 1) {
                setterMap.put(method.getName(), method);
            }
        }

        for (Method method : sourceMethods) {
            if (method.getName().startsWith("get") && method.getParameterCount() == 0) {
                String setterName = method.getName().replace("get", "set");
                Method setter = setterMap.get(setterName);
                
                if (setter != null && setter.getParameterTypes()[0].equals(method.getReturnType())) {
                    Object value = method.invoke(source);
                    setter.invoke(target, value);
                }
            }
        }

        return target;
    }
}
