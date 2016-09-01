/*
 * *** BEGIN LICENSE BLOCK *****
 *  Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  Agfa Healthcare.
 *  Portions created by the Initial Developer are Copyright (C) 2015
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 *  ***** END LICENSE BLOCK *****
 */

package org.dcm4chee.conf.storage;

import org.dcm4che3.conf.ConfigurationSettingsLoader;
import org.dcm4che3.conf.core.DelegatingConfiguration;
import org.dcm4che3.conf.core.ExtensionMergingConfiguration;
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.api.Path;
import org.dcm4che3.conf.core.normalization.DefaultsAndNullFilterDecorator;
import org.dcm4che3.conf.core.olock.HashBasedOptimisticLockingConfiguration;
import org.dcm4che3.conf.dicom.CommonDicomConfiguration;
import org.dcm4chee.conf.ConfigurableExtensionsResolver;
import org.dcm4chee.conf.notif.ConfigNotificationDecorator;
import org.dcm4chee.conf.storage.ConfigurationStorage.ConfigStorageAnno;
import org.dcm4chee.util.TransactionSynchronization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.ejb.*;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.transaction.*;
import java.util.List;
import java.util.Map;

/**
 * A per-deployment configuration singleton that brings the config framework parts together:
 * <ul>
 * <li> Injects the proper config storage by looking up the system property
 * <li> Sets up infinispan cache, reference index
 * <li> Enforces global "one-writer-at-a-time" locking for modification ops and tx demarcation
 * <li> Enables hash-based optimistic locking</li>
 * <li> Enables defaults filtering</li>
 * <li> Triggers integrity checks on transaction pre-commit
 * <li> Triggers config notifications on post-commit
 * </ul>
 * <p>Manages tx demarcation as follows:
 * <ul>
 * <li> runBatch always executes the batch in a new transaction with a special marker
 * <li> if any of state-modifying methods is called outside of a batch tx - executes it in a new transaction
 * <li> if any of state-modifying methods is called within a marked batch tx - executes it as is, i.e. without any extra wrappers
 * </ul>
 * </p>
 */
@SuppressWarnings("unchecked")
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@Local(ConfigurationEJB.class)
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class ConfigurationEJB extends DelegatingConfiguration {

    public static final Logger log = LoggerFactory.getLogger(ConfigurationEJB.class);

    private static final String DISABLE_OLOCK_PROP = "org.dcm4che.conf.olock.disabled";
    private static final String ENABLE_MERGE_CONFIG = "org.dcm4che.conf.merge.enabled";

    // components

    @Inject
    InfinispanCachingConfigurationDecorator infinispanCachingConfigurationDecorator;

    @Inject
    InfinispanDicomReferenceIndexingDecorator indexingDecorator;

    @Inject
    @Any
    private Instance<Configuration> availableConfigStorage;

    @Inject
    ConfigNotificationDecorator configNotificationDecorator;

    @Inject
    private ConfigurationIntegrityCheck integrityCheck;

    @EJB
    ConfigurationEJB self;

    // util

    @Inject
    TransactionSynchronization txSync;

    @Inject
    ConfigurableExtensionsResolver extensionsProvider;

    @PostConstruct
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void init() {
        // detect user setting (system property) for config backend type
        String storageType = ConfigurationSettingsLoader.getPropertyWithNotice(
                System.getProperties(),
                Configuration.CONF_STORAGE_SYSTEM_PROP, ConfigStorageType.JSON_FILE.name().toLowerCase()
        );
        log.info("Creating dcm4che configuration Singleton EJB. Resolving underlying configuration storage '{}' ...", storageType);

        // resolve the corresponding implementation
        Configuration storage;
        try {
            storage = availableConfigStorage.select(new ConfigStorageAnno(storageType)).get();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to initialize dcm4che configuration storage '" + storageType + "'", e);
        }

        // decorate with config notifications
        configNotificationDecorator.setDelegate(storage);
        storage = configNotificationDecorator;

        // decorate with cache
        infinispanCachingConfigurationDecorator.setDelegate(storage);
        storage = infinispanCachingConfigurationDecorator;

        // decorate with reference indexing/resolution
        indexingDecorator.setDelegate(storage);
        storage = indexingDecorator;

        List<Class> allExtensionClasses = extensionsProvider.resolveExtensionsList();

        // ExtensionMergingConfiguration
        if ((System.getProperty(ENABLE_MERGE_CONFIG) != null) && Boolean.valueOf(System.getProperty(ENABLE_MERGE_CONFIG))) {
            storage = new ExtensionMergingConfiguration(storage, allExtensionClasses);
        }

        // olocking
        if (System.getProperty(DISABLE_OLOCK_PROP) == null) {
            storage = new HashBasedOptimisticLockingConfiguration(
                    storage,
                    allExtensionClasses);
        }

        // defaults filtering
        storage = new DefaultsAndNullFilterDecorator(storage, allExtensionClasses, CommonDicomConfiguration.createDefaultDicomVitalizer());


        delegate = storage;

        // bootstrap
        delegate.lock();
        delegate.refreshNode(Path.ROOT);

        log.info("dcm4che configuration singleton EJB created");
    }

    @Override
    public void persistNode(Path path, Map<String, Object> configNode, Class configurableClass) throws ConfigurationException {

        Runnable r = () -> delegate.persistNode(path, configNode, configurableClass);

        if (isBatchTx())
            runInOngoingTx(r);
        else
            self.runInNewTx(r);

    }

    @Override
    public void removeNode(Path path) throws ConfigurationException {

        Runnable r = () -> delegate.removeNode(path);

        if (isBatchTx())
            runInOngoingTx(r);
        else
            self.runInNewTx(r);

    }

    @Override
    public void refreshNode(Path path) throws ConfigurationException {

        Runnable r = () -> delegate.refreshNode(path);

        if (isBatchTx())
            runInOngoingTx(r);
        else
            self.runInNewTx(r);
    }

    @Override
    public void lock() {
        log.warn("Unexpected call to lock(). Locking is handled automatically on a lower layer and should not be called explicitly", new IllegalStateException());
        delegate.lock();
    }

    @Override
    public void runBatch(Batch batch) {
        self.runInNewTx(
                () -> {
                    markBatchTx();

                    // run directly here - we are inside a tx for sure - no need to delegate
                    batch.run();
                });
    }

    /**
     * Ensures that integrity checking will be done before committing the transaction
     */
    private void registerTxHooks() {

        // only register if there is no marker
        Object configTxMarker = txSync.getSynchronizationRegistry().getResource(ConfigurationEJB.class);
        if (configTxMarker == null) {

            // mark this tx as a writertx
            txSync.getSynchronizationRegistry().putResource(ConfigurationEJB.class, new Object());

            // register pre-commit hook
            try {
                txSync.getTransactionManager().getTransaction().registerSynchronization(new Synchronization() {
                    @Override
                    public void beforeCompletion() {
                        beforeCommit();
                    }

                    @Override
                    public void afterCompletion(int i) {
                    }
                });
            } catch (RollbackException | SystemException e) {
                throw new RuntimeException("Error when trying to register a pre-commit hook for config change transaction", e);
            }
        }
    }

    private void beforeCommit() {

        try {
            // perform referential integrity check
            integrityCheck.performCheck(super.getConfigurationRoot());

        } catch (ConfigurationException e) {
            throw new IllegalArgumentException("Configuration integrity violated", e);
        }

        indexingDecorator.beforeCommit();
    }


    private boolean isBatchTx() {
        return txSync.getStatus() != Status.STATUS_NO_TRANSACTION
                && txSync.getSynchronizationRegistry().getResource(Batch.class) != null;
    }

    private void markBatchTx() {
        txSync.getSynchronizationRegistry().putResource(Batch.class, new Object());
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void runInNewTx(Runnable r) {
        delegate.lock();
        registerTxHooks();

        r.run();
    }

    /**
     * to make the stack trace easier to read
     */
    private void runInOngoingTx(Runnable r) {
        r.run();
    }

}


