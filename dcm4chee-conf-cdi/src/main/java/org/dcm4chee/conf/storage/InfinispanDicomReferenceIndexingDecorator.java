package org.dcm4chee.conf.storage;

import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.api.DuplicateUUIDException;
import org.dcm4che3.conf.core.api.Path;
import org.dcm4che3.conf.dicom.DicomReferenceIndexingDecorator;
import org.dcm4chee.cache.Cache;
import org.dcm4chee.cache.CacheByName;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class InfinispanDicomReferenceIndexingDecorator extends DicomReferenceIndexingDecorator {


    @Inject
    @CacheByName("configuration-uuid-index")
    private Cache<String, Path> uuidIndex;

    public void setDelegate(Configuration delegate) {
        this.delegate = delegate;
    }

    @PostConstruct
    public void init() {
        this.uuidToReferableIndex = uuidIndex;
    }

    @Override
    public void refreshNode(String path) throws ConfigurationException {
        delegate.refreshNode(path);

        Object root = delegate.getConfigurationNode("/", null);

        uuidIndex.clear();

        // Don't fail on initializing/refreshing the index - in case the config already has duplicate UUIDs
        super.addReferablesToIndex(new ArrayList<>(), root);
    }

    /**
     *
     */
    private void clearIndexAndAddReferablesFromRootNotFailingOnDuplicates(Object configNode) {
        uuidIndex.clear();
        super.addReferablesToIndex(new ArrayList<>(), configNode);
    }


    /**
     * Make sure that we fail a transaction in case of finding duplicate references
     */
    @Override
    protected List<DuplicateUUIDException> addReferablesToIndex(List<String> pathItems, Object configNode) {
        List<DuplicateUUIDException> duplicateUUIDExceptions = super.addReferablesToIndex(pathItems, configNode);

        if (!duplicateUUIDExceptions.isEmpty()) {
            throw duplicateUUIDExceptions.get(0);
        }

        return duplicateUUIDExceptions;
    }
}
