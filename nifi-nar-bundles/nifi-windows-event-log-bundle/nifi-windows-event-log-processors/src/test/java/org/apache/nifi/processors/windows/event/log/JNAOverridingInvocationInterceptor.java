/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.processors.windows.event.log;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.sun.jna.Native;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import org.junit.Assert;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.platform.commons.util.ReflectionUtils;

public abstract class JNAOverridingInvocationInterceptor implements InvocationInterceptor {
    public static final String NATIVE_CANONICAL_NAME = Native.class.getCanonicalName();
    public static final String LOAD_LIBRARY = "loadLibrary";

    @Override
    public void interceptTestMethod(final Invocation<Void> invocation,
                                    final ReflectiveInvocationContext<Method> invocationContext,
                                    final ExtensionContext extensionContext) {
        intercept(invocation, invocationContext, extensionContext);
    }

    protected abstract Map<String, Map<String, String>> getClassOverrideMap();

    private void intercept(final Invocation<Void> invocation,
                           final ReflectiveInvocationContext<Method> invocationContext,
                           final ExtensionContext extensionContext) {
        invocation.skip();

        Map<String, Map<String, String>> classOverrideMap = getClassOverrideMap();
        final String classpath = System.getProperty("java.class.path");
        URL[] result = Pattern.compile(File.pathSeparator).splitAsStream(classpath).map(Paths::get).map(Path::toAbsolutePath).map(Path::toUri)
                .map(uri -> {
                    URL url = null;
                    try {
                        url = uri.toURL();
                    } catch (MalformedURLException e) {
                        Assert.fail(String.format("Unable to create URL for classpath entry '%s'", uri));
                    }
                    return url;
                })
                .toArray(URL[]::new);
        final URLClassLoader jnaMockClassLoader = new URLClassLoader(result, null) {
            @Override
            protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                Map<String, String> classOverrides = classOverrideMap.get(name);
                if (classOverrides != null) {
                    ClassPool classPool = ClassPool.getDefault();
                    try {
                        CtClass ctClass = classPool.get(name);
                        try {
                            for (final Map.Entry<String, String> methodAndBody : classOverrides.entrySet()) {
                                for (final CtMethod loadLibrary : ctClass.getDeclaredMethods(methodAndBody.getKey())) {
                                    loadLibrary.setBody(methodAndBody.getValue());
                                }
                            }

                            byte[] bytes = ctClass.toBytecode();
                            Class<?> definedClass = defineClass(name, bytes, 0, bytes.length);
                            if (resolve) {
                                resolveClass(definedClass);
                            }
                            return definedClass;
                        } finally {
                            ctClass.detach();
                        }
                    } catch (final Exception e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
                return super.loadClass(name, resolve);
            }
        };

        invokeMethodWithModifiedClassLoader(invocationContext, jnaMockClassLoader);
    }

    private void invokeMethodWithModifiedClassLoader(final ReflectiveInvocationContext<Method> invocationContext,
                                                     final ClassLoader classLoader) {
        final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);

        try {
            final String className = invocationContext.getExecutable().getDeclaringClass().getName();
            final String methodName = invocationContext.getExecutable().getName();
            invokeMethod(className, methodName, classLoader);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private void invokeMethod(final String className,
                              final String methodName,
                              final ClassLoader classLoader) {
        final Class<?> testClass;
        try {
            testClass = classLoader.loadClass(className);
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load test class [" + className + "] from modified classloader, " +
                    "verify that you did not exclude a path containing the test", e);
        }

        final Object testInstance = ReflectionUtils.newInstance(testClass);
        final Optional<Method> method = ReflectionUtils.findMethod(testClass, methodName);
        ReflectionUtils.invokeMethod(
                method.orElseThrow(() -> new IllegalStateException("No test method named " + methodName)),
                testInstance
        );
    }


}
