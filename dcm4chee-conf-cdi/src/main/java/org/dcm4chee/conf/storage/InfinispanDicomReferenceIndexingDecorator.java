package org.dcm4chee.conf.storage;

import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.api.DuplicateUUIDException;
import org.dcm4che3.conf.core.api.Path;
import org.dcm4che3.conf.dicom.DicomReferenceIndexingDecorator;
import org.dcm4chee.cache.Cache;
import org.dcm4chee.cache.CacheByName;
import org.dcm4chee.util.TransactionSynchronization;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/* IMPORTANT:
* Infinispan behaves bad when used in sync/replicated mode and does not properly remove entries when 'remove' is called:
* after commit the entries indeed get removed, however during the transaction if one tries to 'get' an entry that was
* 'remove'd in this transaction - the latest value will be returned instead of null.
* <p/>
* To handle this correctly, we set the entry to 'new Path()' (empty path - to be able to reason about it during the transaction),
* and clean such entries up before tx commit.
*
* Therefore we treat empty paths just like non-existing entries.
*/

@SuppressWarnings("unchecked")
@ApplicationScoped
public class InfinispanDicomReferenceIndexingDecorator extends DicomReferenceIndexingDecorator {


    @Inject
    @CacheByName("configuration-uuid-index")
    private Cache<String, Path> uuidIndex;

    @Inject
    TransactionSynchronization txSync;

    public void setDelegate(Configuration delegate) {
        this.delegate = delegate;
    }

    @PostConstruct
    public void init() {
        this.uuidToReferableIndex = uuidIndex;
    }

    @Override
    public void refreshNode(String path) throws ConfigurationException {

        Object oldRoot = delegate.getConfigurationNode("/", null);
        removeOldReferablesFromIndex(oldRoot);

        delegate.refreshNode(path);
        Object root = delegate.getConfigurationNode("/", null);

        // Don't fail on initializing/refreshing the index - in this case the config already has duplicate UUIDs and we can do nothing about it
        super.addReferablesToIndex(new ArrayList<>(), root);
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


    @Override
    protected void removeFromCache(String uuid) {
        addAsCandidateForDelete(uuid);
        uuidToReferableIndex.put(uuid, new Path());
    }

    private void addAsCandidateForDelete(String uuid) {
        List<String> candidatesToDelete = (List<String>) txSync.getSynchronizationRegistry().getResource(InfinispanDicomReferenceIndexingDecorator.class);
        if (candidatesToDelete == null) {
            candidatesToDelete = new ArrayList<>();
            txSync.getSynchronizationRegistry().putResource(InfinispanDicomReferenceIndexingDecorator.class, candidatesToDelete);
        }
        candidatesToDelete.add(uuid);
    }


    /**
     * Remove the empty entries
     */
    public void beforeCommit() {

        List<String> candidatesToDelete = (List<String>) txSync.getSynchronizationRegistry().getResource(InfinispanDicomReferenceIndexingDecorator.class);
        if (candidatesToDelete != null) {

            candidatesToDelete.forEach(uuid -> {
                Path path = uuidToReferableIndex.get(uuid);
                if (path != null && path.getPathItems().size() == 0)
                    uuidToReferableIndex.remove(uuid);
            });

        }

    }
}
