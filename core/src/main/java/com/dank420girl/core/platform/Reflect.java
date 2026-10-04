package com.dank420girl.core.platform;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Small reflection helpers. Lookups return {@code null} instead of throwing, because a missing
 * class or method usually just means the server is older or newer than the feature.
 */
public final class Reflect {
    private Reflect() {
    }

    /** Returns the first of the given classes that exists, or {@code null}. */
    public static Class<?> findClass(String... names) {
        for (String name : names) {
            try {
                return Class.forName(name, false, Reflect.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError ignored) {
                // try the next one
            }
        }
        return null;
    }

    public static boolean classExists(String name) {
        return findClass(name) != null;
    }

    /** Finds a public method, including inherited ones, or returns {@code null}. */
    public static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        if (owner == null) {
            return null;
        }
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException | SecurityException | LinkageError e) {
            return null;
        }
    }

    /** Finds a method declared anywhere in the class hierarchy (any visibility) and makes it accessible. */
    public static Method findDeclaredMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, parameterTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException | SecurityException | LinkageError ignored) {
                // keep looking
            } catch (RuntimeException ignored) {
                // InaccessibleObjectException on newer Java versions
                return null;
            }
        }
        return null;
    }

    /** Finds a public field, including inherited ones, or returns {@code null}. */
    public static Field findField(Class<?> owner, String name) {
        if (owner == null) {
            return null;
        }
        try {
            return owner.getField(name);
        } catch (NoSuchFieldException | SecurityException | LinkageError e) {
            return null;
        }
    }

    public static Constructor<?> findConstructor(Class<?> owner, Class<?>... parameterTypes) {
        if (owner == null) {
            return null;
        }
        try {
            return owner.getConstructor(parameterTypes);
        } catch (NoSuchMethodException | SecurityException | LinkageError e) {
            return null;
        }
    }

    /** Reads a public static field, e.g. a constant of an enum or of a registry-backed interface. */
    public static Object staticValue(Class<?> owner, String name) {
        Field field = findField(owner, name);
        if (field == null || !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            return null;
        }
        try {
            return field.get(null);
        } catch (IllegalAccessException | RuntimeException e) {
            return null;
        }
    }

    /** Invokes a method, unwrapping the exception it throws. */
    public static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Object construct(Constructor<?> constructor, Object... args) {
        try {
            return constructor.newInstance(args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException(cause);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Looks up an enum constant by name without throwing. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object enumConstant(Class<?> enumClass, String name) {
        if (enumClass == null || name == null) {
            return null;
        }
        if (enumClass.isEnum()) {
            try {
                return Enum.valueOf((Class) enumClass, name);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        // Some former enums became interfaces with constants (for example Sound in 1.21.3).
        Object value = staticValue(enumClass, name);
        return value != null && enumClass.isInstance(value) ? value : null;
    }
}
