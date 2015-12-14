package org.dcm4chee.conf.storage;

import org.dcm4che3.conf.core.DelegatingConfiguration;
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.util.ConfigNodeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.transaction.Status;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 *  Performs tx-aware read/write caching with proper isolation
 */
@SuppressWarnings("unchecked")
public class DualCachingTxAwareConfiguration extends DelegatingConfiguration implements TransactionalConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DelegatingConfiguration.class);

    // readers cache - shared cache, only rarely and shortly locked for writing just to replace new nodes loaded from backend
    Map<String, Object> readerConfigurationCache = null;
    ReadWriteLock readCacheLock = new ReentrantReadWriteLock();

    // writer's cache - exclusive cache for the modifying transaction
    // locked with the local reentrant lock in case non-lockable storage is used (supposed to be locked with both the storage lock globally)
    Map<String, Object> writerConfigurationCache = null;
    ReentrantLock writeCacheLock = new ReentrantLock();

    Map<String, Object> writerConfigurationCacheBeforeCommit;
    TxInfo txInfo;

    public DualCachingTxAwareConfiguration(Configuration delegate) {
        super(delegate);
    }

    @Override
    public void init(TxInfo txInfo) {

        this.txInfo = txInfo;

        // init reader cache
        try {
            readerConfigurationCache = super.getConfigurationRoot();
            log.info("Configuration cache initialized");
        } catch (ConfigurationException e) {
            throw new RuntimeException("Cannot initialize config cache", e);
        }

    }

    @Override
    public Map<String, Object> getConfigurationRoot() throws ConfigurationException {

        if (txInfo.isPartOfModifyingTransaction())
            return (Map<String, Object>) ConfigNodeUtil.deepCloneNode(getWriterConfigurationCache());

        readCacheLock.readLock().lock();
        try {
            return (Map<String, Object>) ConfigNodeUtil.deepCloneNode(readerConfigurationCache);
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

        if (txInfo.isPartOfModifyingTransaction())
            return getConfigurationNodeFromCache(path, getWriterConfigurationCache());

        readCacheLock.readLock().lock();
        try {
            return getConfigurationNodeFromCache(path, readerConfigurationCache);
        } finally {
            readCacheLock.readLock().unlock();
        }
    }

    Object getConfigurationNodeFromCache(String path, Map<String, Object> writerConfigurationCache) {
        Object node = ConfigNodeUtil.getNode(writerConfigurationCache, path);
        if (node == null) return null;
        return ConfigNodeUtil.deepCloneNode(node);
    }

    @Override
    public boolean nodeExists(String path) throws ConfigurationException {

        if (txInfo.isPartOfModifyingTransaction())
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

        Object newConfigurationNode = super.getConfigurationNode(path, null);

        if (txInfo.isPartOfModifyingTransaction()) {
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

    Iterator searchEager(String liteXPathExpression, Map<String, Object> configurationRoot) {
        List l = new ArrayList();
        final Iterator origIterator = ConfigNodeUtil.search(configurationRoot, liteXPathExpression);
        while (origIterator.hasNext()) l.add(ConfigNodeUtil.deepCloneNode(origIterator.next()));
        return l.iterator();
    }

    @Override
    public void persistNode(String path, Map<String, Object> configNode, Class configurableClass) {
        writeCacheLock.lock();
        try {

            super.persistNode(path, configNode, configurableClass);

            if (!path.equals("/"))
                ConfigNodeUtil.replaceNode(getWriterConfigurationCache(), path, configNode);
            else
                writerConfigurationCache = configNode;
        } finally {
            writeCacheLock.unlock();
        }
    }

    @Override
    public void removeNode(String path) {
        writeCacheLock.lock();
        try {
            super.removeNode(path);
            ConfigNodeUtil.removeNodes(getWriterConfigurationCache(), path);
        } finally {
            writeCacheLock.unlock();
        }
    }

    @Override
    public void runBatch(Batch batch) {
        writeCacheLock.lock();
        try {
            batch.run();
        } finally {
            writeCacheLock.unlock();
        }
    }

    @Override
    public Iterator search(String liteXPathExpression) throws IllegalArgumentException, ConfigurationException {

        if (txInfo.isPartOfModifyingTransaction())
            return searchEager(liteXPathExpression, getWriterConfigurationCache());

        readCacheLock.readLock().lock();
        try {
            // fully iterate and make copies of all returned results to ensure the consistency and isolation
            return searchEager(liteXPathExpression, readerConfigurationCache);
        } finally {
            readCacheLock.readLock().unlock();
        }
    }

    @Override
    public void beforeCommit() {
        // remember writer cache and clear it
        writerConfigurationCacheBeforeCommit = writerConfigurationCache;
        writerConfigurationCache = null;
    }

    @Override
    public void afterCommit(int i) {

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

    Map<String, Object> getWriterConfigurationCache() throws ConfigurationException {
        if (writerConfigurationCache == null) {
            // read fully from the backend to ensure 0 inconsistency
            writerConfigurationCache = super.getConfigurationRoot();
        }
        return writerConfigurationCache;
    }

    @Override
    public void lock() {
        super.lock();
    }
}