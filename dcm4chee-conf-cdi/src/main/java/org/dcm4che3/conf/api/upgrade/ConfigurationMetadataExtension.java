package org.dcm4che3.conf.api.upgrade;

import org.dcm4che.kiwiyard.core.api.ExtensionBaseClass;

@ExtensionBaseClass
public class ConfigurationMetadataExtension {

    public Class<ConfigurationMetadataExtension> getBaseClass() {
        return ConfigurationMetadataExtension.class;
    }
}
