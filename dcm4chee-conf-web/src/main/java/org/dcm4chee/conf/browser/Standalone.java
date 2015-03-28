package org.dcm4chee.conf.browser;

import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author Roman K
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface Standalone {
}
