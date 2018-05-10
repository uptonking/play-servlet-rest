package com.github.datalking.web.bind;

import com.github.datalking.beans.MutablePropertyValues;
import com.github.datalking.beans.PropertyEditorRegistry;
import com.github.datalking.beans.PropertyValues;
import com.github.datalking.beans.TypeConverter;
import com.github.datalking.common.MethodParameter;
import com.github.datalking.common.convert.ConversionService;
import com.github.datalking.common.convert.SimpleTypeConverter;
import com.github.datalking.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyEditor;
import java.lang.reflect.Field;

/**
 * 将属性值与target对象绑定
 *
 * @author yaoo on 4/29/18
 */
public class DataBinder implements PropertyEditorRegistry, TypeConverter {

    public static final String DEFAULT_OBJECT_NAME = "target";

    public static final int DEFAULT_AUTO_GROW_COLLECTION_LIMIT = 256;

    protected static final Logger logger = LoggerFactory.getLogger(DataBinder.class);

    private final Object target;

    private final String objectName;

    private SimpleTypeConverter typeConverter;

    private ConversionService conversionService;

    private AbstractPropertyBindingResult bindingResult;

    public DataBinder(Object target) {
        this(target, DEFAULT_OBJECT_NAME);
    }

    public DataBinder(Object target, String objectName) {
        this.target = target;
        this.objectName = objectName;
    }

    public Object getTarget() {
        return this.target;
    }

    public String getObjectName() {
        return this.objectName;
    }

    public void initBeanPropertyAccess() {
        Assert.state(this.bindingResult == null, "DataBinder is already initialized");
        this.bindingResult = new BeanPropertyBindingResult(getTarget(), getObjectName(), isAutoGrowNestedPaths(), getAutoGrowCollectionLimit());
        if (this.conversionService != null) {
            this.bindingResult.initConversion(this.conversionService);
        }
    }

    public void bind(PropertyValues pvs) {
        MutablePropertyValues mpvs = (pvs instanceof MutablePropertyValues) ? (MutablePropertyValues) pvs : new MutablePropertyValues(pvs);
        doBind(mpvs);
    }

    protected void doBind(MutablePropertyValues mpvs) {
//        checkAllowedFields(mpvs);
//        checkRequiredFields(mpvs);
        applyPropertyValues(mpvs);
    }

    protected void applyPropertyValues(MutablePropertyValues mpvs) {

    }

    protected PropertyEditorRegistry getPropertyEditorRegistry() {
//        if (getTarget() != null) {
//            return getInternalBindingResult().getPropertyAccessor();
//        } else {
        return getSimpleTypeConverter();
//        }
    }

    protected TypeConverter getTypeConverter() {
//        if (getTarget() != null) {
//            return getInternalBindingResult().getPropertyAccessor();
//        } else {
        return getSimpleTypeConverter();
//        }
    }

    protected SimpleTypeConverter getSimpleTypeConverter() {
        if (this.typeConverter == null) {

            this.typeConverter = new SimpleTypeConverter();

            if (this.conversionService != null) {

                this.typeConverter.setConversionService(this.conversionService);
            }
        }
        return this.typeConverter;
    }

//    protected AbstractPropertyBindingResult getInternalBindingResult() {
//        if (this.bindingResult == null) {
//            initBeanPropertyAccess();
//        }
//        return this.bindingResult;
//    }

    // ======== PropertyEditorRegistry Interface ========

    @Override
    public void registerCustomEditor(Class<?> requiredType, PropertyEditor propertyEditor) {
        getPropertyEditorRegistry().registerCustomEditor(requiredType, propertyEditor);
    }

    @Override
    public void registerCustomEditor(Class<?> requiredType, String field, PropertyEditor propertyEditor) {
        getPropertyEditorRegistry().registerCustomEditor(requiredType, field, propertyEditor);
    }

    @Override
    public PropertyEditor findCustomEditor(Class<?> requiredType, String propertyPath) {
        return getPropertyEditorRegistry().findCustomEditor(requiredType, propertyPath);
    }

    // ======== TypeConverter Interface ========

    @Override
    public <T> T convertIfNecessary(Object value, Class<T> requiredType) {
        return getTypeConverter().convertIfNecessary(value, requiredType);
    }

    @Override
    public <T> T convertIfNecessary(Object value, Class<T> requiredType, MethodParameter methodParam) {

        return getTypeConverter().convertIfNecessary(value, requiredType, methodParam);
    }

    @Override
    public <T> T convertIfNecessary(Object value, Class<T> requiredType, Field field) {

        return getTypeConverter().convertIfNecessary(value, requiredType, field);
    }

}
