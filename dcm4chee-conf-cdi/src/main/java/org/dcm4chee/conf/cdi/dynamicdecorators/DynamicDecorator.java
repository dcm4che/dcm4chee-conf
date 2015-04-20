package org.dcm4chee.conf.cdi.dynamicdecorators;

import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface DynamicDecorator {
}
