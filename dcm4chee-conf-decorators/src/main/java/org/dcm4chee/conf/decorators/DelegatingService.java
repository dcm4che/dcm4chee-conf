package org.dcm4chee.conf.decorators;

import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This qualifier is just needed for the sake of having non-default Delegating service impls to avoid ambiguity
 */
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface DelegatingService {
}
