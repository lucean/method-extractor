package org.example;

import com.google.common.reflect.ClassPath;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {

    private static final List<String> EXCLUDED_METHODS = List.of("equals", "hashCode", "toString");

    public static void main(final String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: java -jar <jar file> <root package> [class1,class2,...,classN]");
            System.exit(1);
        }

        final File jarFile = new File(args[0]);
        final String rootPackage = args[1];
        final List<String> instrumentedClassesList;

        if (args.length > 2) {
            final String instrumentedClasses = args[2];
            System.out.println("Instrumented classes: " + instrumentedClasses);
            instrumentedClassesList = Arrays.asList(instrumentedClasses.split(","));
        } else {
            instrumentedClassesList = Collections.emptyList();
        }

        System.out.println("Loading JAR file: " + jarFile.getAbsolutePath());

        final URLClassLoader classLoader = new URLClassLoader(new URL[]{jarFile.toURI().toURL()}, Main.class.getClassLoader());
        final Set<ClassPath.ClassInfo> classesInPackage = ClassPath.from(classLoader)
                                                                   .getAllClasses()
                                                                   .stream()
                                                                   .filter(ci -> ci.getName()
                                                                                   .startsWith(rootPackage))
                                                                   .collect(Collectors.toSet());

        System.out.println(classLoader);
        System.out.println(classesInPackage);

        final Set<ClassPath.ClassInfo> filteredClasses = classesInPackage.stream()
                                                                         .filter(ci -> instrumentedClassesList.isEmpty() || instrumentedClassesList.contains(ci.getName()))
                                                                         .collect(Collectors.toSet());

        final List<String> openTelemetryStatements = new ArrayList<>();
        for (final ClassPath.ClassInfo classInfo : filteredClasses) {
            System.out.println("Handling class: " + classInfo.getName());
            final Set<Method> methods = new HashSet<>(Arrays.asList(classInfo.load()
                                                                             .getDeclaredMethods()));

            openTelemetryStatements.add(classInfo.getName() + methods.stream()
                                                                     .map(Method::getName)
                                                                     .filter(methodName -> !EXCLUDED_METHODS.contains(methodName))
                                                                     .filter(methodName -> !methodName.contains("$"))
                                                                     .filter(methodName -> !methodName.contains("lambda"))
                                                                     .collect(Collectors.joining(", ", "[", "]")));
        }

        System.out.println("Instrumenting " + filteredClasses.size() + " classes.");
        System.out.println("OpenTelemetry statements: ");
        System.out.println(String.join(";", openTelemetryStatements));
    }
}