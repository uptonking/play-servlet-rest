package com.github.datalking.web.servlet;

import com.github.datalking.common.GenericTypeResolver;
import com.github.datalking.common.LocalVariableTableParameterNameDiscoverer;
import com.github.datalking.common.MethodParameter;
import com.github.datalking.common.ParameterNameDiscoverer;
import com.github.datalking.util.ReflectionUtils;
import com.github.datalking.web.bind.WebDataBinderFactory;
import com.github.datalking.web.context.request.WebRequest;
import com.github.datalking.web.mvc.method.HandlerMethod;
import com.github.datalking.web.support.HandlerMethodArgumentResolverComposite;
import com.github.datalking.web.support.ModelAndViewContainer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * @author yaoo on 5/2/18
 */
public class InvocableHandlerMethod extends HandlerMethod {

    private WebDataBinderFactory dataBinderFactory;

    private HandlerMethodArgumentResolverComposite argumentResolvers = new HandlerMethodArgumentResolverComposite();

    private ParameterNameDiscoverer parameterNameDiscoverer = new LocalVariableTableParameterNameDiscoverer();

    public InvocableHandlerMethod(HandlerMethod handlerMethod) {
        super(handlerMethod);
    }

    public InvocableHandlerMethod(Object bean, Method method) {
        super(bean, method);
    }

    public InvocableHandlerMethod(Object bean, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {

        super(bean, methodName, parameterTypes);
    }

    public void setDataBinderFactory(WebDataBinderFactory dataBinderFactory) {
        this.dataBinderFactory = dataBinderFactory;
    }

    public void setHandlerMethodArgumentResolvers(HandlerMethodArgumentResolverComposite argumentResolvers) {
        this.argumentResolvers = argumentResolvers;
    }

    public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
        this.parameterNameDiscoverer = parameterNameDiscoverer;
    }

    public final Object invokeForRequest(WebRequest request, ModelAndViewContainer mavContainer,
                                         Object... providedArgs) throws Exception {

        Object[] args = getMethodArgumentValues(request, mavContainer, providedArgs);
        if (logger.isTraceEnabled()) {
            StringBuilder sb = new StringBuilder("Invoking [");
            sb.append(getBeanType().getSimpleName()).append(".");
            sb.append(getMethod().getName()).append("] method with arguments ");
            sb.append(Arrays.asList(args));
            logger.trace(sb.toString());
        }
        Object returnValue = doInvoke(args);
        if (logger.isTraceEnabled()) {
            logger.trace("Method [" + getMethod().getName() + "] returned [" + returnValue + "]");
        }
        return returnValue;
    }


    private Object[] getMethodArgumentValues(WebRequest request, ModelAndViewContainer mavContainer,
                                             Object... providedArgs) throws Exception {

        MethodParameter[] parameters = getMethodParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            MethodParameter parameter = parameters[i];
            parameter.initParameterNameDiscovery(this.parameterNameDiscoverer);
            GenericTypeResolver.resolveParameterType(parameter, getBean().getClass());
            args[i] = resolveProvidedArgument(parameter, providedArgs);
            if (args[i] != null) {
                continue;
            }
            if (this.argumentResolvers.supportsParameter(parameter)) {
                try {
                    args[i] = this.argumentResolvers.resolveArgument(
                            parameter, mavContainer, request, this.dataBinderFactory);
                    continue;
                } catch (Exception ex) {
                    if (logger.isDebugEnabled()) {
                        logger.debug(getArgumentResolutionErrorMessage("Error resolving argument", i), ex);
                    }
                    throw ex;
                }
            }
            if (args[i] == null) {
                String msg = getArgumentResolutionErrorMessage("No suitable resolver for argument", i);
                throw new IllegalStateException(msg);
            }
        }
        return args;
    }

    private String getArgumentResolutionErrorMessage(String message, int index) {
        MethodParameter param = getMethodParameters()[index];
        message += " [" + index + "] [type=" + param.getParameterType().getName() + "]";
        return getDetailedErrorMessage(message);
    }

    protected String getDetailedErrorMessage(String message) {
        StringBuilder sb = new StringBuilder(message).append("\n");
        sb.append("HandlerMethod details: \n");
        sb.append("Controller [").append(getBeanType().getName()).append("]\n");
        sb.append("Method [").append(getBridgedMethod().toGenericString()).append("]\n");
        return sb.toString();
    }

    private Object resolveProvidedArgument(MethodParameter parameter, Object... providedArgs) {
        if (providedArgs == null) {
            return null;
        }
        for (Object providedArg : providedArgs) {
            if (parameter.getParameterType().isInstance(providedArg)) {
                return providedArg;
            }
        }
        return null;
    }


    /**
     * Invoke the handler method with the given argument values.
     */
    protected Object doInvoke(Object... args) throws Exception {
        ReflectionUtils.makeAccessible(getBridgedMethod());
        try {
            return getBridgedMethod().invoke(getBean(), args);
        } catch (IllegalArgumentException ex) {
            assertTargetBean(getBridgedMethod(), getBean(), args);
            String message = (ex.getMessage() != null ? ex.getMessage() : "Illegal argument");
            throw new IllegalStateException(getInvocationErrorMessage(message, args), ex);
        } catch (InvocationTargetException ex) {
            // Unwrap for HandlerExceptionResolvers ...
            Throwable targetException = ex.getTargetException();
            if (targetException instanceof RuntimeException) {
                throw (RuntimeException) targetException;
            } else if (targetException instanceof Error) {
                throw (Error) targetException;
            } else if (targetException instanceof Exception) {
                throw (Exception) targetException;
            } else {
                String msg = getInvocationErrorMessage("Failed to invoke controller method", args);
                throw new IllegalStateException(msg, targetException);
            }
        }
    }


    private void assertTargetBean(Method method, Object targetBean, Object[] args) {
        Class<?> methodDeclaringClass = method.getDeclaringClass();
        Class<?> targetBeanClass = targetBean.getClass();
        if (!methodDeclaringClass.isAssignableFrom(targetBeanClass)) {
            String msg = "The mapped controller method class '" + methodDeclaringClass.getName() +
                    "' is not an instance of the actual controller bean class '" +
                    targetBeanClass.getName() + "'. If the controller requires proxying " +
                    "(e.g. due to @Transactional), please use class-based proxying.";
            throw new IllegalStateException(getInvocationErrorMessage(msg, args));
        }
    }

    private String getInvocationErrorMessage(String message, Object[] resolvedArgs) {
        StringBuilder sb = new StringBuilder(getDetailedErrorMessage(message));
        sb.append("Resolved arguments: \n");
        for (int i = 0; i < resolvedArgs.length; i++) {
            sb.append("[").append(i).append("] ");
            if (resolvedArgs[i] == null) {
                sb.append("[null] \n");
            } else {
                sb.append("[type=").append(resolvedArgs[i].getClass().getName()).append("] ");
                sb.append("[value=").append(resolvedArgs[i]).append("]\n");
            }
        }
        return sb.toString();
    }

}