package com.github.datalking.web.servlet;

import com.github.datalking.web.mvc.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 类似于servlet的filter
 *
 * @author yaoo on 4/25/18
 */
public interface HandlerInterceptor {

    boolean preHandle(HttpServletRequest request,
                      HttpServletResponse response,
                      Object handler) throws Exception;

    void postHandle(HttpServletRequest request,
                    HttpServletResponse response,
                    Object handler,
                    ModelAndView modelAndView) throws Exception;

    void afterCompletion(HttpServletRequest request,
                         HttpServletResponse response,
                         Object handler,
                         Exception ex) throws Exception;

    // 异步并发时执行的方法
    void afterConcurrentHandlingStarted(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Object handler) throws Exception;


}
