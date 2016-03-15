package org.dcm4chee.conf.storage;

import org.dcm4che3.conf.core.DelegatingConfiguration;
import org.dcm4che3.conf.core.Nodes;
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.util.SplittedPath;
import org.dcm4chee.cache.Cache;
import org.dcm4chee.cache.CacheByName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.*;
import java.util.function.Consumer;

/**
 * IMPORTANT:
 * Infinispan behaves bad when used in sync/replicated mode and does not properly remove entries when 'remove' is called:
 * after commit the entries indeed get removed, however during the transaction if one tries to 'get' an entry that was
 * 'remove'd in this transaction - the latest value will be returned instead of null.
 * <p/>
 * To handle this correctly, we set the entry to 'new HashMap()' (empty node - to be able to reason about it during the transaction)
 * and remove all such entries before committing (so it gets actually removed).
 *
 * Therefore we treat empty nodes on serialization level just like non-existing entries.
 */
@SuppressWarnings("unchecked")
@ApplicationScoped
public class InfinispanCachingConfiguration extends DelegatingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DelegatingConfiguration.class);

    private static final int level = 3;

    @Inject
    @CacheByName("configuration")
    private Cache<String, Map<String, Object>> cache;
    private Consumer<Object> onFullReloadHook;

    public InfinispanCachingConfiguration() {
    }

    public void setDelegate(Configuration delegate) {
        this.delegate = delegate;
    }

    // TODO: convert to refresh, decouple from indexing
    // since refresh is still relevant due to the possibility of hacking config from db directly
    public void reloadFromBackend() {

        lock();
        Map<String, Object> root = delegate.getConfigurationRoot();
        cache.clear();
        persistTopLayerToCache(root, new ArrayList<>());
        if (onFullReloadHook != null) onFullReloadHook.accept(root);
    }

    private void persistTopLayerToCache(Map<String, Object> m, List<String> pathItems) {

        if (pathItems.size() == level) {
            cache.put(Nodes.toSimpleEscapedPath(pathItems), m);
        } else if (pathItems.size() < level) {
            m.entrySet().forEach((entry) -> {
                pathItems.add(entry.getKey());

                try {
                    persistTopLayerToCache((Map<String, Object>) entry.getValue(), pathItems);
                } catch (ClassCastException e) {
                    // this should not happen, but after all ignore and let pass through
                    log.error("Unexpected node above serialization level: " + entry.getValue());
                }

                pathItems.remove(pathItems.size() - 1);
            });
        } else throw new IllegalArgumentException("PathItems size is greater than level (" + level + "):" + pathItems);

    }


    private Map<String, Object> getWrappedRoot() {

        HashMap<String, Object> root = new HashMap<>();

        // TODO: cache.entrySet is not really threadsafe..
        // tests show that this works in replicated mode but we need to switch to some proper API call once we move to a newer infinispan
        // for now it's not so critical since most conf calls will go directly to certain entries

        for (Map.Entry<String, Map<String, Object>> stringObjectEntry : cache.entrySet()) {

            // see the comment on top
            if (stringObjectEntry.getValue().isEmpty()) continue;

            Nodes.replaceNode(
                    root,
                    (Map) stringObjectEntry.getValue(), Nodes.fromSimpleEscapedPath(stringObjectEntry.getKey())
            );
        }

        return root;
    }

    @Override
    public Map<String, Object> getConfigurationRoot() throws ConfigurationException {
        return (Map<String, Object>) Nodes.deepCloneNode(getWrappedRoot());
    }

    @Override
    public Object getConfigurationNode(String path, Class configurableClass) throws ConfigurationException {
        return Nodes.deepCloneNode(getConfigurationNodeFromCache(path));
    }

    private Object getConfigurationNodeFromCache(String path) {
        SplittedPath splittedPath = getSplittedPath(path);

        // fallback if not simple/persistable path
        if (splittedPath == null) {
            try {
                return Nodes.getNode(getWrappedRoot(), path);
            } catch (Exception e) {
                throw new ConfigurationException("Unable to get node under path '" + path + "'", e);
            }
        }

        // fallback if requested one of top levels
        if (splittedPath.getOuterPathItems().size() < level) {
            return Nodes.getNode(getWrappedRoot(), path);
        }

        Map<String, Object> node = cache.get(Nodes.toSimpleEscapedPath(splittedPath.getOuterPathItems()));

        // see the comment on top
        if (node != null && node.isEmpty()) node = null;

        if (splittedPath.getInnerPathitems().size() == 0)
            return node;
        else
            return Nodes.getNode(node, Nodes.toSimpleEscapedPath(splittedPath.getInnerPathitems()));
    }


    @Override
    public void persistNode(String path, Map<String, Object> configNode, Class configurableClass) throws ConfigurationException {

        SplittedPath splittedPath = getSplittedPath(path);

        if (splittedPath == null)
            throw new IllegalArgumentException("Path '" + path + "' is invalid");

        Map<String, Object> clonedNode = (Map<String, Object>) Nodes.deepCloneNode(configNode);

        String levelKey = Nodes.toSimpleEscapedPath(splittedPath.getOuterPathItems());

        // fallback if requested one of top levels
        if (splittedPath.getOuterPathItems().size() < level) {

            removeNodeFromCache(path);
            persistTopLayerToCache(clonedNode, splittedPath.getOuterPathItems());

        } else if (splittedPath.getOuterPathItems().size() > level) {

            Map<String, Object> levelRootNode = (Map<String, Object>) Nodes.deepCloneNode(cache.get(levelKey));
            Nodes.replaceNode(levelRootNode, Nodes.toSimpleEscapedPath(splittedPath.getInnerPathitems()), clonedNode);
            cache.put(levelKey, levelRootNode);

        } else {
            // this should be the one used mostly
            cache.put(levelKey, clonedNode);
        }

        // propagate to backend
        super.persistNode(path, configNode, configurableClass);

    }


    /**
     * This should be executed with global lock
     */
    @Override
    public void removeNode(String path) throws ConfigurationException {

        removeNodeFromCache(path);

        // propagate to storage backend
        super.removeNode(path);

    }

    private void removeNodeFromCache(String path) {
        SplittedPath splittedPath = getSplittedPath(path);

        if (splittedPath == null) {
            Map<String, Object> clonedRoot = (Map<String, Object>) Nodes.deepCloneNode(getWrappedRoot());

            if (clonedRoot == null) return;

            try {
                Nodes.removeNodes(clonedRoot, path);
            } catch (Exception e) {
                throw new ConfigurationException("Unable to remove nodes under path '" + path + "'", e);
            }

            log.warn("Complex xpath expression (" + path + ") used to remove nodes from configuration, this should only be used for exceptional cases as it uses a lot of resources");
            persistTopLayerToCache(clonedRoot, new ArrayList<>());

            return;
        }

        String outerPath = Nodes.toSimpleEscapedPath(splittedPath.getOuterPathItems());

        if (splittedPath.getOuterPathItems().size() < level) {

            ArrayList<String> toDelete = new ArrayList<>();
            for (String s : cache.keySet()) {
                if (s.startsWith(outerPath)) {
                    toDelete.add(s);
                }
            }

            toDelete.forEach((key) -> {
                // see comment on top
                cache.put(key, new HashMap<>());
            });

        } else if (splittedPath.getOuterPathItems().size() > level) {

            Map<String, Object> levelNode = (Map<String, Object>) Nodes.deepCloneNode(cache.get(outerPath));
            Nodes.removeNode(levelNode, splittedPath.getInnerPathitems());
            cache.put(outerPath, levelNode);

        } else {
            // see comment on top
            cache.put(outerPath, new HashMap<>());
        }
    }

    private SplittedPath getSplittedPath(String path) {

        List<String> pathItems = Nodes.simpleOrPersistablePathToPathItemsOrNull(path);
        if (pathItems == null) return null;
        return new SplittedPath(pathItems, level);
    }

    @Override
    public boolean nodeExists(String path) throws ConfigurationException {

        SplittedPath splittedPath = getSplittedPath(path);

        if (splittedPath == null)
            throw new IllegalArgumentException("Path '" + path + "' is not valid");

        String outerPath = Nodes.toSimpleEscapedPath(splittedPath.getOuterPathItems());

        int size = splittedPath.getOuterPathItems().size();
        if (size < level) {

            for (Map.Entry<String, Map<String, Object>> mapEntry : cache.entrySet()) {
                // see comment on top
                if (mapEntry.getKey().startsWith(outerPath) && !mapEntry.getValue().isEmpty()) {
                    return true;
                }
            }
            return false;
        } else if (size > level) {
            Map<String, Object> levelNode = cache.get(outerPath);
            return Nodes.nodeExists(levelNode, splittedPath.getInnerPathitems());
        } else {
            Map<String, Object> stringObjectMap = cache.get(outerPath);
            // see comment on top
            return stringObjectMap != null && !stringObjectMap.isEmpty();
        }
    }

    @Override
    public Iterator search(String liteXPathExpression) throws IllegalArgumentException, ConfigurationException {
        ArrayList<Object> objects = new ArrayList<>();
        Nodes.search(getWrappedRoot(), liteXPathExpression).forEachRemaining((e) -> objects.add(Nodes.deepCloneNode(e)));
        return objects.iterator();
    }

    /**
     * @param hook Consumer that takes the full configuration tree as an arg
     */
    public void onFullReload(Consumer<Object> hook) {
        this.onFullReloadHook = hook;
    }

    // see comment on top
    public void beforeCommit() {

        List<String> keysForEmptyEntries = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> stringMapEntry : cache.entrySet()) {
            if (stringMapEntry.getValue().isEmpty())
                keysForEmptyEntries.add(stringMapEntry.getKey());
        }

        keysForEmptyEntries.forEach(cache::remove);

    }
}