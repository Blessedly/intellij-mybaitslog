package com.plugins.mybaitslog.console;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * 验证 SQL 类型识别无需旧版 Commons Lang，使用 main 可直接执行。
 */
public class PrintlnUtilCompatibilityTest {
    /**
     * 覆盖空值、空白、大小写及四种常见 SQL，失败时抛出断言错误。
     */
    public static void main(String[] args) throws Exception {
        // 模拟插件隔离的类加载器，禁止从 IDE 的其他运行库借用旧版 Commons Lang。
        URL location = PrintlnUtil.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{location},
                PrintlnUtilCompatibilityTest.class.getClassLoader()) {
            /** 禁止旧依赖，并让待测类通过当前隔离加载器链接依赖。 */
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("org.apache.commons.lang.")) {
                    throw new ClassNotFoundException(name);
                }
                if (name.equals("com.plugins.mybaitslog.console.PrintlnUtil")) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        loaded = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
                return super.loadClass(name, resolve);
            }
        }) {
            Method method = loader.loadClass("com.plugins.mybaitslog.console.PrintlnUtil")
                    .getDeclaredMethod("getSqlType", String.class);
            method.setAccessible(true);
            String[][] cases = {
                    {null, "other"}, {"", "other"}, {" \t\r\n", "other"},
                    {"\u2003", "other"}, {"  SELECT 1\n", "select"},
                    {"insert into t values (1)", "insert"}, {"UPDATE t SET a=1", "update"},
                    {"delete from t", "delete"}, {"create table t (a int)", "other"}
            };
            for (String[] item : cases) {
                Object actual = method.invoke(null, new Object[]{item[0]});
                if (!item[1].equals(actual)) {
                    throw new AssertionError("SQL 类型识别失败: " + item[0] + " -> " + actual);
                }
            }
            System.out.println("PASS: " + cases.length + " SQL type cases");
        }
    }
}
