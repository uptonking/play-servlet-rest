package com.github.datalking.web.bind;

import com.github.datalking.beans.MutablePropertyValues;
import com.github.datalking.util.web.WebUtils;

import javax.servlet.ServletRequest;

/**
 * @author yaoo on 5/2/18
 */
public class ServletRequestDataBinder extends WebDataBinder {

    public ServletRequestDataBinder(Object target) {
        super(target);
    }

    public ServletRequestDataBinder(Object target, String objectName) {
        super(target, objectName);
    }

    /**
     * todo
     */
    public void bind(ServletRequest request) {
//        MutablePropertyValues mpvs = new ServletRequestParameterPropertyValues(request);
//        MultipartRequest multipartRequest = WebUtils.getNativeRequest(request, MultipartRequest.class);
//        if (multipartRequest != null) {
//            bindMultipart(multipartRequest.getMultiFileMap(), mpvs);
//        }
//        addBindValues(mpvs, request);
//        doBind(mpvs);
    }

    protected void addBindValues(MutablePropertyValues mpvs, ServletRequest request) {
    }

    //    public void closeNoCatch() throws ServletRequestBindingException {
    public void closeNoCatch() throws Exception {
        if (getBindingResult().hasErrors()) {
            throw new Exception(
                    "Errors binding onto object '" + getBindingResult().getObjectName() + "'",
                    new Exception(getBindingResult() + ""));
        }
    }

}