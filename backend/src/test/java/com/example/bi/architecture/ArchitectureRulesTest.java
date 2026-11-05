package com.example.bi.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureRulesTest {

    private static final String ROOT_PACKAGE = "com.example.bi";

    private List<Class<?>> getClassesInPackage(String packageName) throws Exception {
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL packageUrl = classLoader.getResource(path);
        assertThat(packageUrl).as("Package URL for %s should exist", packageName).isNotNull();

        File directory = new File(packageUrl.toURI());
        List<Class<?>> classes = new ArrayList<>();
        findClasses(directory, packageName, classes);
        return classes;
    }

    private void findClasses(File directory, String packageName, List<Class<?>> classes) throws Exception {
        if (!directory.exists()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                findClasses(file, packageName + "." + file.getName(), classes);
            } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                classes.add(Class.forName(className));
            }
        }
    }

    @Test
    @DisplayName("Layering: Controllers must NOT directly reference or inject Repository classes")
    void controllersDoNotDependOnRepositories() throws Exception {
        List<Class<?>> classes = getClassesInPackage(ROOT_PACKAGE + ".web");
        List<String> violations = new ArrayList<>();

        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(RestController.class) || clazz.isAnnotationPresent(Controller.class)) {
                for (Field field : clazz.getDeclaredFields()) {
                    Class<?> type = field.getType();
                    if (type.isAnnotationPresent(Repository.class) || type.getPackage().getName().contains(".repository")) {
                        violations.add(clazz.getSimpleName() + " injects repository " + type.getSimpleName());
                    }
                }
            }
        }

        assertThat(violations)
                .as("Controllers should communicate through Services, not directly with Repositories")
                .isEmpty();
    }

    @Test
    @DisplayName("Layering: Services must NOT depend on Web Controllers")
    void servicesDoNotDependOnWeb() throws Exception {
        List<Class<?>> classes = getClassesInPackage(ROOT_PACKAGE + ".service");
        List<String> violations = new ArrayList<>();

        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(Service.class)) {
                for (Field field : clazz.getDeclaredFields()) {
                    Class<?> type = field.getType();
                    if (type.getPackage() != null && type.getPackage().getName().contains(".web")) {
                        violations.add(clazz.getSimpleName() + " depends on web class " + type.getSimpleName());
                    }
                }
            }
        }

        assertThat(violations)
                .as("Services must not depend on the web/controller layer")
                .isEmpty();
    }

    @Test
    @DisplayName("API Hygiene: Controller endpoints must NOT return JPA entities directly")
    void controllersDoNotReturnJpaEntities() throws Exception {
        List<Class<?>> classes = getClassesInPackage(ROOT_PACKAGE + ".web");
        List<String> violations = new ArrayList<>();

        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(RestController.class) || clazz.isAnnotationPresent(Controller.class)) {
                for (Method method : clazz.getDeclaredMethods()) {
                    Class<?> returnType = method.getReturnType();
                    if (returnType.getPackage() != null && returnType.getPackage().getName().contains(".domain")) {
                        violations.add(clazz.getSimpleName() + "." + method.getName() + " returns entity " + returnType.getSimpleName());
                    }
                }
            }
        }

        assertThat(violations)
                .as("Controller endpoints should return DTOs or ProblemDetail, not domain entities directly")
                .isEmpty();
    }

    @Test
    @DisplayName("Layering: Domain package does not depend on Web or Service layers")
    void domainDoesNotDependOnWebOrService() throws Exception {
        List<Class<?>> classes = getClassesInPackage(ROOT_PACKAGE + ".domain");
        List<String> violations = new ArrayList<>();

        for (Class<?> clazz : classes) {
            for (Field field : clazz.getDeclaredFields()) {
                Class<?> type = field.getType();
                if (type.getPackage() != null) {
                    String pkg = type.getPackage().getName();
                    if (pkg.contains(".web") || pkg.contains(".service")) {
                        violations.add(clazz.getSimpleName() + " references " + type.getName());
                    }
                }
            }
        }

        assertThat(violations)
                .as("Domain entities must be decoupled from service and web layers")
                .isEmpty();
    }
}

