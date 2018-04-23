package com.github.datalking.aop.support;

import com.github.datalking.aop.ClassFilter;
import com.github.datalking.aop.MethodMatcher;
import com.github.datalking.util.Assert;

import java.io.Serializable;
import java.lang.reflect.Method;

/**
 * @author yaoo on 4/19/18
 */
public abstract class MethodMatchers {

    public static MethodMatcher union(MethodMatcher mm1, MethodMatcher mm2) {
        return new UnionMethodMatcher(mm1, mm2);
    }

    public static MethodMatcher intersection(MethodMatcher mm1, MethodMatcher mm2) {
        return new IntersectionMethodMatcher(mm1, mm2);
    }


    static MethodMatcher union(MethodMatcher mm1, ClassFilter cf1, MethodMatcher mm2, ClassFilter cf2) {
        return new ClassFilterAwareUnionMethodMatcher(mm1, cf1, mm2, cf2);
    }

    private static class UnionMethodMatcher implements MethodMatcher, Serializable {

        private final MethodMatcher mm1;

        private final MethodMatcher mm2;

        public UnionMethodMatcher(MethodMatcher mm1, MethodMatcher mm2) {
            Assert.notNull(mm1, "First MethodMatcher must not be null");
            Assert.notNull(mm2, "Second MethodMatcher must not be null");
            this.mm1 = mm1;
            this.mm2 = mm2;
        }


        public boolean matches(Method method, Class<?> targetClass) {
            return (matchesClass1(targetClass) && this.mm1.matches(method, targetClass)) ||
                    (matchesClass2(targetClass) && this.mm2.matches(method, targetClass));
        }

        protected boolean matchesClass1(Class<?> targetClass) {
            return true;
        }

        protected boolean matchesClass2(Class<?> targetClass) {
            return true;
        }

        public boolean isRuntime() {
            return this.mm1.isRuntime() || this.mm2.isRuntime();
        }

        public boolean matches(Method method, Class<?> targetClass, Object... args) {
            return this.mm1.matches(method, targetClass, args) || this.mm2.matches(method, targetClass, args);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof UnionMethodMatcher)) {
                return false;
            }
            UnionMethodMatcher that = (UnionMethodMatcher) obj;
            return (this.mm1.equals(that.mm1) && this.mm2.equals(that.mm2));
        }

        @Override
        public int hashCode() {
            int hashCode = 17;
            hashCode = 37 * hashCode + this.mm1.hashCode();
            hashCode = 37 * hashCode + this.mm2.hashCode();
            return hashCode;
        }
    }

    private static class IntersectionMethodMatcher implements MethodMatcher, Serializable {

        private final MethodMatcher mm1;

        private final MethodMatcher mm2;

        public IntersectionMethodMatcher(MethodMatcher mm1, MethodMatcher mm2) {
            Assert.notNull(mm1, "First MethodMatcher must not be null");
            Assert.notNull(mm2, "Second MethodMatcher must not be null");
            this.mm1 = mm1;
            this.mm2 = mm2;
        }

        public boolean matches(Method method, Class<?> targetClass, boolean hasIntroductions) {
            return this.mm1.matches(method, targetClass) &&
                    this.mm2.matches(method, targetClass);
        }

        public boolean matches(Method method, Class<?> targetClass) {
            return this.mm1.matches(method, targetClass) && this.mm2.matches(method, targetClass);
        }

        public boolean isRuntime() {
            return this.mm1.isRuntime() || this.mm2.isRuntime();
        }

        public boolean matches(Method method, Class<?> targetClass, Object... args) {

            boolean aMatches = this.mm1.isRuntime() ?
                    this.mm1.matches(method, targetClass, args) : this.mm1.matches(method, targetClass);
            boolean bMatches = this.mm2.isRuntime() ?
                    this.mm2.matches(method, targetClass, args) : this.mm2.matches(method, targetClass);
            return aMatches && bMatches;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof IntersectionMethodMatcher)) {
                return false;
            }
            IntersectionMethodMatcher that = (IntersectionMethodMatcher) other;
            return (this.mm1.equals(that.mm1) && this.mm2.equals(that.mm2));
        }

        @Override
        public int hashCode() {
            int hashCode = 17;
            hashCode = 37 * hashCode + this.mm1.hashCode();
            hashCode = 37 * hashCode + this.mm2.hashCode();
            return hashCode;
        }
    }

    private static class ClassFilterAwareUnionMethodMatcher extends UnionMethodMatcher {

        private final ClassFilter cf1;

        private final ClassFilter cf2;

        public ClassFilterAwareUnionMethodMatcher(MethodMatcher mm1, ClassFilter cf1, MethodMatcher mm2, ClassFilter cf2) {
            super(mm1, mm2);
            this.cf1 = cf1;
            this.cf2 = cf2;
        }

        @Override
        protected boolean matchesClass1(Class<?> targetClass) {
            return this.cf1.matches(targetClass);
        }

        @Override
        protected boolean matchesClass2(Class<?> targetClass) {
            return this.cf2.matches(targetClass);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!super.equals(other)) {
                return false;
            }
            ClassFilter otherCf1 = ClassFilter.TRUE;
            ClassFilter otherCf2 = ClassFilter.TRUE;
            if (other instanceof ClassFilterAwareUnionMethodMatcher) {
                ClassFilterAwareUnionMethodMatcher cfa = (ClassFilterAwareUnionMethodMatcher) other;
                otherCf1 = cfa.cf1;
                otherCf2 = cfa.cf2;
            }
            return (this.cf1.equals(otherCf1) && this.cf2.equals(otherCf2));
        }
    }


}
