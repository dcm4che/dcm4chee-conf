package org.dcm4chee.util;

import java.util.Map;
import java.util.Properties;

/**
 * Enables to retrieve the versions of software components (e.g. to include in troubleshooting tools)
 */
public interface SoftwareVersionProvider {

    /**
     * @return All the versions collected from the known components, key=component name
     */
    Map<String, String> getAllVersions();
}
