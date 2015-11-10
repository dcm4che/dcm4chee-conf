package org.dcm4chee.hooks;

import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This qualifier is just needed for the sake of avoiding CDI ambiguity
 */
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface ProducedHooksConfig {
}
