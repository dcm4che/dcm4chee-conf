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

@SuppressWarnings("unchecked")
@ApplicationScoped
public class InfinispanCachingConfiguration extends DelegatingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DelegatingConfiguration.class);

    private static final int level = 3;


    @Inject
    @CacheByName("configuration")
    private Cache<String, Map<String, Object>> cache;

    public InfinispanCachingConfiguration() {
    }

    public void setDelegate(Configuration delegate) {
        this.delegate = delegate;
    }

    public void reloadFromBackend() {

        lock();
        Map<String, Object> root = delegate.getConfigurationRoot();
        ArrayList<Object> keys = new ArrayList<>();

        cache.clear();
        persistTopLayer(root, new ArrayList<>());

    }

    private void persistTopLayer(Map<String, Object> m, List<String> pathItems) {

        if (pathItems.size() == level) {
            cache.put(Nodes.toSimpleEscapedPath(pathItems), m);
        } else if (pathItems.size() < level) {
            m.entrySet().forEach((entry) -> {
                pathItems.add(entry.getKey());

                try {
                    persistTopLayer((Map<String, Object>) entry.getValue(), pathItems);
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
        // tests show that it works in replicated mode but we need to switch to some proper API call once we move to a newer infinispan
        // for now it's not so critical since most conf calls will go directly to certain entries

        for (Map.Entry<String, Map<String, Object>> stringObjectEntry : cache.entrySet()) {
            Nodes.replaceNode(
                    root,
                    (Map) stringObjectEntry.getValue(),
                    Nodes.fromSimpleEscapedPath(stringObjectEntry.getKey())
            );
        }

        return root;
    }

    @Override
    public Map<String, Object> getConfigurationRoot() throws ConfigurationException {
        return getWrappedRoot();
    }

    @Override
    public Object getConfigurationNode(String path, Class configurableClass) throws ConfigurationException {

        List<String> pathItems = Nodes.fromSimpleEscapedPathOrNull(path);

        // fallback if not simple path
        if (pathItems == null) {
            return Nodes.getNode(getWrappedRoot(), path);
        }

        SplittedPath splittedPath = new SplittedPath(pathItems, level);

        // fallback if requested one of top levels
        if (splittedPath.getOuterPathItems().size() < level) {
            return Nodes.getNode(getWrappedRoot(), path);
        }

        Map<String, Object> node = cache.get(Nodes.toSimpleEscapedPath(splittedPath.getOuterPathItems()));

        if (splittedPath.getInnerPathitems().size() == 0)
            return node;
        else
            return Nodes.getNode(node, Nodes.toSimpleEscapedPath(splittedPath.getInnerPathitems()));

    }


    @Override
    public void persistNode(String path, Map<String, Object> configNode, Class configurableClass) throws ConfigurationException {
        SplittedPath splittedPath = getSplittedPath(path);

        String levelKey = Nodes.toSimpleEscapedPath(splittedPath.getOuterPathItems());

        // fallback if requested one of top levels
        if (splittedPath.getOuterPathItems().size() < level) {

            removeNode(path);
            persistTopLayer(configNode, splittedPath.getOuterPathItems());

        } else if (splittedPath.getOuterPathItems().size() > level) {

            Map<String, Object> levelRootNode = (Map<String, Object>) Nodes.deepCloneNode(cache.get(levelKey));
            Nodes.replaceNode(levelRootNode, Nodes.toSimpleEscapedPath(splittedPath.getInnerPathitems()), configNode);
            cache.put(levelKey, levelRootNode);

        } else {
            // this should be the one used mostly
            cache.put(levelKey, configNode);
        }

        // also propagate to backend
        super.persistNode(path, configNode, configurableClass);
    }


    /**
     * This should be executed with global lock
     */
    @Override
    public void removeNode(String path) throws ConfigurationException {

        SplittedPath splittedPath = getSplittedPath(path);
        String outerPath = Nodes.toSimpleEscapedPath(splittedPath.getOuterPathItems());

        if (splittedPath.getOuterPathItems().size() < level) {

            ArrayList<String> toDelete = new ArrayList<>();
            for (String s : cache.keySet()) {
                if (s.startsWith(outerPath)) {
                    toDelete.add(s);
                }
            }

            toDelete.forEach(cache::remove);

        } else if (splittedPath.getOuterPathItems().size() > level) {

            Map<String, Object> levelNode = (Map<String, Object>) Nodes.deepCloneNode(cache.get(outerPath));
            Nodes.removeNode(levelNode, splittedPath.getInnerPathitems());
            cache.put(outerPath, levelNode);

        } else {
              cache.remove(outerPath);
        }

        // also propagate to backend
        super.removeNode(path);
    }

    private SplittedPath getSplittedPath(String path) {

        // TODO: make extra check - getPathItems will silently swallow xpaths with attributes...
        // not using fromSimpleEscapedPath since we still want to use it to preserve old way of referring nodes (i.e. yy[@name='zzz']) for now

        List<String> pathItems = Nodes.getPathItems(path);
        return new SplittedPath(pathItems, level);
    }

    @Override
    public boolean nodeExists(String path) throws ConfigurationException {

        SplittedPath splittedPath = getSplittedPath(path);
        String outerPath = Nodes.toSimpleEscapedPath(splittedPath.getOuterPathItems());

        int size = splittedPath.getOuterPathItems().size();
        if (size < level) {

            for (String s : cache.keySet()) {
                if (s.startsWith(outerPath)) {
                    return true;
                }
            }
            return false;
        } else if (size > level) {
            Map<String, Object> levelNode = cache.get(outerPath);
            return Nodes.nodeExists(levelNode, splittedPath.getInnerPathitems());
        } else {
            return cache.containsKey(outerPath);
        }
    }

    @Override
    public Iterator search(String liteXPathExpression) throws IllegalArgumentException, ConfigurationException {
        // TODO: make uuid index

        ArrayList<Object> objects = new ArrayList<>();
        Nodes.search(getWrappedRoot(), liteXPathExpression).forEachRemaining(objects::add);
        return objects.iterator();
    }
}