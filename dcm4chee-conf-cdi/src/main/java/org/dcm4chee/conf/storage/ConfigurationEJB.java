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

import org.codehaus.jackson.map.ObjectMapper;
import org.dcm4che3.conf.ConfigurationSettingsLoader;
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.util.ConfigNodeUtil;
import org.dcm4chee.conf.notif.ConfigNotificationDecorator;
import org.dcm4chee.util.TransactionSynchronization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Local;
import javax.ejb.Singleton;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.transaction.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A per-deployment configuration singleton that brings the following parts together:
 * <ul>
 * <li> Injects the proper config storage by looking up the system property
 * <li> Sets up config notifications
 * <li> Performs read/write caching with proper isolation
 * <li> Triggers integrity checks on transaction pre-commit
 * </ul>
 */
@SuppressWarnings("unchecked")
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@Local(ConfigurationEJB.class)
public class ConfigurationEJB implements Configuration {

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

    private Configuration storage;


    // readers cache - shared cache, only rarely and shortly locked for writing just to replace new nodes loaded from backend
    private Map<String, Object> readerConfigurationCache = null;
    private ReadWriteLock readCacheLock = new ReentrantReadWriteLock();

    // writer's cache - exclusive cache for the modifying transaction
    // locked with both the storage lock globally and with the local reentrant lock in case non-lockable storage is used
    private Map<String, Object> writerConfigurationCache = null;
    private ReentrantLock writeCacheLock = new ReentrantLock();
    private Map<String, Object> writerConfigurationCacheBeforeCommit;

    @PostConstruct
    public void init() {

        // detect user setting (system property) for config backend type
        String storageType = ConfigurationSettingsLoader.getPropertyWithNotice(
                System.getProperties(),
                Configuration.CONF_STORAGE_SYSTEM_PROP, ConfigStorageType.DB_BLOBS.name().toLowerCase()
        );
        log.info("Using configuration storage '{}'", storageType);

        // resolve the corresponding implementation
        storage = availableConfigStorage.select(new ConfigurationStorage.ConfigStorageAnno(storageType)).get();

        // decorate with config notification
        configNotificationDecorator.setDelegate(storage);
        storage = configNotificationDecorator;

        // init reader cache
        try {
            readerConfigurationCache = storage.getConfigurationRoot();
            log.info("Configuration cache initialized");
        } catch (ConfigurationException e) {
            throw new RuntimeException("Cannot initialize config cache", e);
        }

    }

    private Object deepCloneNode(Object node) {
        // clone
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.treeToValue(objectMapper.valueToTree(node), node.getClass());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Readers ///////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Map<String, Object> getConfigurationRoot() throws ConfigurationException {

        if (isPartOfModifyingTransaction())
            return (Map<String, Object>) deepCloneNode(getWriterConfigurationCache());

        readCacheLock.readLock().lock();
        try {
            return (Map<String, Object>) deepCloneNode(readerConfigurationCache);
        } finally {
            readCacheLock.readLock().unlock();
        }
    }

    /**
     * Return cached node
     *
     * @param path
     * @param configurableClass
     * @return
     * @throws ConfigurationException
     */
    @Override
    public Object getConfigurationNode(String path, Class configurableClass) throws ConfigurationException {

        if (isPartOfModifyingTransaction())
            return getConfigurationNodeFromCache(path, getWriterConfigurationCache());

        readCacheLock.readLock().lock();
        try {
            return getConfigurationNodeFromCache(path, readerConfigurationCache);
        } finally {
            readCacheLock.readLock().unlock();
        }
    }

    private Object getConfigurationNodeFromCache(String path, Map<String, Object> writerConfigurationCache) {
        Object node = ConfigNodeUtil.getNode(writerConfigurationCache, path);
        if (node == null) return null;
        return deepCloneNode(node);
    }

    @Override
    public boolean nodeExists(String path) throws ConfigurationException {

        if (isPartOfModifyingTransaction())
            ConfigNodeUtil.nodeExists(getWriterConfigurationCache(), path);

        readCacheLock.readLock().lock();
        try {
            return ConfigNodeUtil.nodeExists(readerConfigurationCache, path);
        } finally {
            readCacheLock.readLock().unlock();
        }
    }

    @Override
    public void refreshNode(String path) throws ConfigurationException {

        Object newConfigurationNode = storage.getConfigurationNode(path, null);

        if (isPartOfModifyingTransaction()) {
            if (path.equals("/"))
                writerConfigurationCache = (Map<String, Object>) newConfigurationNode;
            else
                ConfigNodeUtil.replaceNode(getWriterConfigurationCache(), path, newConfigurationNode);

        } else {

            readCacheLock.writeLock().lock();
            try {
                if (path.equals("/"))
                    readerConfigurationCache = (Map<String, Object>) newConfigurationNode;
                else
                    ConfigNodeUtil.replaceNode(readerConfigurationCache, path, newConfigurationNode);
            } finally {
                readCacheLock.writeLock().unlock();
            }
        }
    }

    @Override
    public Iterator search(String liteXPathExpression) throws IllegalArgumentException, ConfigurationException {

        readCacheLock.writeLock().lock();
        try {
            // fully iterate and make copies of all returned results to ensure the consistency and isolation
            List l = new ArrayList();
            final Iterator origIterator = ConfigNodeUtil.search(readerConfigurationCache, liteXPathExpression);

            while (origIterator.hasNext()) l.add(deepCloneNode(origIterator.next()));

            return l.iterator();

        } finally {
            readCacheLock.writeLock().unlock();
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Writers ///////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void persistNode(String path, Map<String, Object> configNode, Class configurableClass) throws ConfigurationException {
        writeCacheLock.lock();
        try {
            storage.lock();
            registerTxHooks();

            storage.persistNode(path, configNode, configurableClass);

            if (!path.equals("/"))
                ConfigNodeUtil.replaceNode(getWriterConfigurationCache(), path, configNode);
            else
                writerConfigurationCache = configNode;
        } finally {
            writeCacheLock.unlock();
        }
    }

    @Override
    public void removeNode(String path) throws ConfigurationException {
        writeCacheLock.lock();
        try {
            storage.lock();
            registerTxHooks();

            storage.removeNode(path);
            ConfigNodeUtil.removeNodes(getWriterConfigurationCache(), path);
        } finally {
            writeCacheLock.unlock();
        }
    }

    @Override
    public void runBatch(ConfigBatch batch) {
        writeCacheLock.lock();
        try {
            storage.lock();
            registerTxHooks();
            batch.run();
        } finally {
            writeCacheLock.unlock();
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Transactions  /////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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

    private void beforeCommit() {
        try {
            // perform referential integrity check
            integrityCheck.performCheck(getWriterConfigurationCache());

        } catch (ConfigurationException e) {
            throw new IllegalArgumentException("Configuration integrity violated", e);

        } finally {
            // remember writer cache and clear it
            writerConfigurationCacheBeforeCommit = writerConfigurationCache;
            writerConfigurationCache = null;
        }
    }

    private void afterCommit(int i) {

        // overwrite reader cache if tx successful
        if (i == Status.STATUS_COMMITTED) {

            readCacheLock.writeLock().lock();
            try {
                readerConfigurationCache = writerConfigurationCacheBeforeCommit;
            } finally {
                readCacheLock.writeLock().unlock();
            }
        }
    }

    private boolean isPartOfModifyingTransaction() {
        return txSync.getSynchronizationRegistry().getResource(ConfigurationEJB.class) != null;
    }

    private Map<String, Object> getWriterConfigurationCache() throws ConfigurationException {
        if (writerConfigurationCache == null) {
            // read fully from the backend to ensure 0 inconsistency
            writerConfigurationCache = storage.getConfigurationRoot();
        }
        return writerConfigurationCache;
    }

    @Override
    public void lock() {
        storage.lock();
    }

}


