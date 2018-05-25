package com.github.datalking.common;

import com.github.datalking.util.ClassUtils;
import net.sf.cglib.asm.ClassReader;
import net.sf.cglib.asm.ClassVisitor;
import net.sf.cglib.asm.Label;
import net.sf.cglib.asm.MethodVisitor;
import net.sf.cglib.asm.Opcodes;
import net.sf.cglib.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于ASM获取局部变量表中的方法参数名
 *
 * @author yaoo on 4/29/18
 */
public class LocalVariableTableParameterNameDiscoverer implements ParameterNameDiscoverer {

    private static final Logger logger = LoggerFactory.getLogger(LocalVariableTableParameterNameDiscoverer.class);

    private static final Map<Member, String[]> NO_DEBUG_INFO_MAP = Collections.emptyMap();

    private final Map<Class<?>, Map<Member, String[]>> parameterNamesCache = new ConcurrentHashMap<>(32);

    public String[] getParameterNames(Method method) {
//        Method originalMethod = BridgeMethodResolver.findBridgedMethod(method);
        Method originalMethod = method;
        Class<?> declaringClass = originalMethod.getDeclaringClass();
        Map<Member, String[]> map = this.parameterNamesCache.get(declaringClass);
        if (map == null) {
            map = inspectClass(declaringClass);
            this.parameterNamesCache.put(declaringClass, map);
        }
        if (map != NO_DEBUG_INFO_MAP) {
            return map.get(originalMethod);
        }
        return null;
    }

    public String[] getParameterNames(Constructor<?> ctor) {
        Class<?> declaringClass = ctor.getDeclaringClass();
        Map<Member, String[]> map = this.parameterNamesCache.get(declaringClass);
        if (map == null) {
            map = inspectClass(declaringClass);
            this.parameterNamesCache.put(declaringClass, map);
        }
        if (map != NO_DEBUG_INFO_MAP) {
            return map.get(ctor);
        }
        return null;
    }

    private Map<Member, String[]> inspectClass(Class<?> clazz) {
        InputStream is = clazz.getResourceAsStream(ClassUtils.getClassFileName(clazz));
        if (is == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Cannot find '.class' file for class [" + clazz +
                        "] - unable to determine constructor/method parameter names");
            }
            return NO_DEBUG_INFO_MAP;
        }
        try {
            ClassReader classReader = new ClassReader(is);
            Map<Member, String[]> map = new ConcurrentHashMap<>(32);
            classReader.accept(new ParameterNameDiscoveringVisitor(clazz, map), 0);
            return map;
        } catch (IOException ex) {
            if (logger.isDebugEnabled()) {
                logger.debug("Exception thrown while reading '.class' file for class [" + clazz +
                        "] - unable to determine constructor/method parameter names", ex);
            }
        } catch (IllegalArgumentException ex) {
            if (logger.isDebugEnabled()) {
                logger.debug("ASM ClassReader failed to parse class file [" + clazz +
                        "], probably due to a new Java class file version that isn't supported yet " +
                        "- unable to determine constructor/method parameter names", ex);
            }
        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                // ignore
            }
        }
        return NO_DEBUG_INFO_MAP;
    }


    private static class ParameterNameDiscoveringVisitor extends ClassVisitor {

        private static final String STATIC_CLASS_INIT = "<clinit>";

        private final Class<?> clazz;

        private final Map<Member, String[]> memberMap;

        public ParameterNameDiscoveringVisitor(Class<?> clazz, Map<Member, String[]> memberMap) {
            super(Opcodes.ASM4);
            this.clazz = clazz;
            this.memberMap = memberMap;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            // exclude synthetic + bridged && static class initialization
            if (!isSyntheticOrBridged(access) && !STATIC_CLASS_INIT.equals(name)) {
                return new LocalVariableTableVisitor(clazz, memberMap, name, desc, isStatic(access));
            }
            return null;
        }

        private static boolean isSyntheticOrBridged(int access) {
            return (((access & Opcodes.ACC_SYNTHETIC) | (access & Opcodes.ACC_BRIDGE)) > 0);
        }

        private static boolean isStatic(int access) {
            return ((access & Opcodes.ACC_STATIC) > 0);
        }
    }

    private static class LocalVariableTableVisitor extends MethodVisitor {

        private static final String CONSTRUCTOR = "<init>";

        private final Class<?> clazz;

        private final Map<Member, String[]> memberMap;

        private final String name;

        private final Type[] args;

        private final String[] parameterNames;

        private final boolean isStatic;

        private boolean hasLvtInfo = false;
        private final int[] lvtSlotIndex;

        public LocalVariableTableVisitor(Class<?> clazz, Map<Member, String[]> map, String name, String desc, boolean isStatic) {
            super(Opcodes.ASM4);
            this.clazz = clazz;
            this.memberMap = map;
            this.name = name;
            this.args = Type.getArgumentTypes(desc);
            this.parameterNames = new String[this.args.length];
            this.isStatic = isStatic;
            this.lvtSlotIndex = computeLvtSlotIndices(isStatic, this.args);
        }

        @Override
        public void visitLocalVariable(String name, String description, String signature, Label start, Label end, int index) {
            this.hasLvtInfo = true;
            for (int i = 0; i < this.lvtSlotIndex.length; i++) {
                if (this.lvtSlotIndex[i] == index) {
                    this.parameterNames[i] = name;
                }
            }
        }

        @Override
        public void visitEnd() {
            if (this.hasLvtInfo || (this.isStatic && this.parameterNames.length == 0)) {
                // visitLocalVariable will never be called for static no args methods
                // which doesn't use any local variables.
                // This means that hasLvtInfo could be false for that kind of methods
                // even if the class has local variable info.
                this.memberMap.put(resolveMember(), this.parameterNames);
            }
        }

        private Member resolveMember() {
            ClassLoader loader = this.clazz.getClassLoader();
            Class<?>[] argTypes = new Class<?>[this.args.length];
            for (int i = 0; i < this.args.length; i++) {
                argTypes[i] = ClassUtils.resolveClassName(this.args[i].getClassName(), loader);
            }
            try {
                if (CONSTRUCTOR.equals(this.name)) {
                    return this.clazz.getDeclaredConstructor(argTypes);
                }
                return this.clazz.getDeclaredMethod(this.name, argTypes);
            } catch (NoSuchMethodException ex) {
                throw new IllegalStateException("Method [" + this.name +
                        "] was discovered in the .class file but cannot be resolved in the class object", ex);
            }
        }

        private static int[] computeLvtSlotIndices(boolean isStatic, Type[] paramTypes) {
            int[] lvtIndex = new int[paramTypes.length];
            int nextIndex = (isStatic ? 0 : 1);
            for (int i = 0; i < paramTypes.length; i++) {
                lvtIndex[i] = nextIndex;
                if (isWideType(paramTypes[i])) {
                    nextIndex += 2;
                } else {
                    nextIndex++;
                }
            }
            return lvtIndex;
        }

        private static boolean isWideType(Type aType) {
            return (aType == Type.LONG_TYPE || aType == Type.DOUBLE_TYPE);
        }
    }

}
