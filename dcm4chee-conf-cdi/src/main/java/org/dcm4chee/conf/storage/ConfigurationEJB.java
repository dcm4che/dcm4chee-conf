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
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
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
import javax.transaction.RollbackException;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import java.util.Map;

/**
 * A per-deployment configuration singleton that brings the following parts together:
 * <ul>
 * <li> Injects the proper config storage by looking up the system property
 * <li> Sets up dual cache, config notifications
 * <li> Triggers integrity checks on transaction pre-commit
 * </ul>
 */
@SuppressWarnings("unchecked")
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@Local(ConfigurationEJB.class)
public class ConfigurationEJB extends DelegatingConfiguration implements TransactionalConfiguration.TxInfo {

    public static final Logger log = LoggerFactory.getLogger(ConfigurationEJB.class);

    @Inject
    @Any
    private Instance<Configuration> availableConfigStorage;

    @Inject
    private ConfigNotificationDecorator configNotificationDecorator;

    @Inject
    private ConfigurationIntegrityCheck integrityCheck;

    @Inject
    TransactionSynchronization txSync;

    private TransactionalConfiguration txAwareCache;


    @PostConstruct
    public void init() {
        // detect user setting (system property) for config backend type
        String storageType = ConfigurationSettingsLoader.getPropertyWithNotice(
                System.getProperties(),
                Configuration.CONF_STORAGE_SYSTEM_PROP, ConfigStorageType.JSON_FILE.name().toLowerCase()
        );
        log.info("Using configuration storage '{}'", storageType);

        // resolve the corresponding implementation
        Configuration storage = availableConfigStorage.select(new ConfigStorageAnno(storageType)).get();

        // decorate with config notification
        configNotificationDecorator.setDelegate(storage);
        storage = configNotificationDecorator;

        // decorate with cache
        txAwareCache = new DualCachingTxAwareConfiguration(storage);
        txAwareCache.init(this);
        storage = txAwareCache;

        delegate = storage;
    }

    public void persistNode(String path, Map<String, Object> configNode, Class configurableClass) throws ConfigurationException {
        lock();
        super.persistNode(path, configNode, configurableClass);
    }

    public void removeNode(String path) throws ConfigurationException {
        lock();
        super.removeNode(path);
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void runBatch(Batch batch) {
        lock();
        super.runBatch(batch);
    }

    /**
     * Ensures that integrity checking will be done before committing the transaction,
     * and reader cache will be modified after
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
                        afterCommit(i);
                    }
                });
            } catch (RollbackException | SystemException e) {
                throw new RuntimeException("Error when trying to register a pre-commit hook for config change transaction", e);
            }
        }
    }

    private void afterCommit(int i) {
        txAwareCache.afterCommit(i);
    }

    private void beforeCommit() {

        try {
            // perform referential integrity check
            integrityCheck.performCheck(super.getConfigurationRoot());

        } catch (ConfigurationException e) {
            throw new IllegalArgumentException("Configuration integrity violated", e);
        } finally {
            txAwareCache.beforeCommit();
        }
    }

    @Override
    public void lock() {
        super.lock();
        registerTxHooks();
    }

    @Override
    public boolean isPartOfModifyingTransaction() {
        return txSync.getSynchronizationRegistry().getResource(ConfigurationEJB.class) != null;
    }

    /**
     * To be used by whoever needs a tx with 'requires' logic
     * @param batch
     */
    public void runWithRequiresTxWithLock(Batch batch) {
        lock();
        batch.run();
    }

}

