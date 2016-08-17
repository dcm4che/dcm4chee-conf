package org.dcm4chee.conf;

import org.dcm4che3.conf.api.TCConfiguration;
import org.dcm4che3.conf.api.TCGroupsProvider;
import org.dcm4che3.conf.api.internal.DicomConfigurationManager;
import org.dcm4che3.conf.core.Nodes;
import org.dcm4che3.conf.core.api.ConfigChangeEvent;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.api.Path;
import org.dcm4che3.conf.dicom.DicomPath;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

@ApplicationScoped
public class CachedTCGroupsManager {

    @Inject
    DicomConfigurationManager config;

    public void onConfigChange(@Observes ConfigChangeEvent configChangeEvent) {
        if (tcsChanged(configChangeEvent)) {
            config.refreshTCGroups();
        }
    }

    private boolean tcsChanged(ConfigChangeEvent configChangeEvent) {
        for (String s : configChangeEvent.getChangedPaths()) {
            Path path = new Path(Nodes.simpleOrPersistablePathToPathItemsOrNull(s));
            if (path.size() < 3 || path.getPathItems().contains("dcmTransferCapabilities")) {
                return true;
            }
        }
        return false;
    }

}
