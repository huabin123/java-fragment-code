package com.fragment.core.annotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

public final class AnnotationInspector {
    private AnnotationInspector() {}

    public static void inspectAll(Class<?> clazz) {
        System.out.println("== Class: " + clazz.getName() + " ==");
        printAnnotations("Class", clazz.getAnnotations());

        inspectFields(clazz);
        inspectMethods(clazz);
    }

    public static void inspectFields(Class<?> clazz) {
        for (Field f : clazz.getDeclaredFields()) {
            f.setAccessible(true);
            System.out.println("-- Field: " + f.getName());
            printAnnotations("Field", f.getAnnotations());
            AnnotatedType at = f.getAnnotatedType();
            printAnnotatedType(at, "FieldType");
        }
    }

    public static void inspectMethods(Class<?> clazz) {
        for (Method m : clazz.getDeclaredMethods()) {
            System.out.println("-- Method: " + m.getName());
            printAnnotations("Method", m.getAnnotations());

            Annotation[][] paramAnnos = m.getParameterAnnotations();
            Parameter[] params = m.getParameters();
            for (int i = 0; i < params.length; i++) {
                System.out.println("   Param[" + i + "] " + params[i].getName());
                printAnnotations("Param", paramAnnos[i]);
                AnnotatedType pat = m.getAnnotatedParameterTypes()[i];
                printAnnotatedType(pat, "ParamType");
            }
        }
    }

    private static void printAnnotatedType(AnnotatedType at, String title) {
        if (at == null) return;
        System.out.println("   [" + title + "] " + at.getType().getTypeName());
        for (Annotation a : at.getAnnotations()) {
            System.out.println("      @" + a.annotationType().getSimpleName() + " " + a.toString());
        }
        if (at instanceof AnnotatedParameterizedType) {
            AnnotatedParameterizedType apt = (AnnotatedParameterizedType) at;
            AnnotatedType[] args = apt.getAnnotatedActualTypeArguments();
            for (int i = 0; i < args.length; i++) {
                printAnnotatedType(args[i], title + ".arg" + i);
            }
        }
    }

    private static void printAnnotations(String where, Annotation[] annos) {
        if (annos.length == 0) {
            System.out.println("   [" + where + "] <none>");
        } else {
            Arrays.stream(annos)
                .forEach(a -> System.out.println("   [" + where + "] @" + a.annotationType().getSimpleName() + " -> " + a.toString()));
        }
    }
}
