package org.dcm4chee.conf.storage;

import org.dcm4che3.conf.core.DelegatingConfiguration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.util.ConfigNodeUtil;
import org.dcm4che3.conf.core.util.SimpleConfigNodeUtil;
import org.dcm4chee.cache.Cache;
import org.dcm4chee.cache.CacheByName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class InfinispanCachingConfiguration extends DelegatingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DelegatingConfiguration.class);

    private static final int level = 3;


    private Cache<String, Object> cache;

    public void init() {

        Map<String, Object> root = delegate.getConfigurationRoot();

        ArrayList<Object> keys = new ArrayList<>();

        cache.clear();

        // cache entries below @level
        processTopLayers(root, new ArrayDeque<>(), (s, m) -> {
            cache.put(s, m);
        });

        root.entrySet().stream();

    }

    private void processTopLayers(Map<String, Object> m, Deque<String> pathItems, BiConsumer<String, Map<String, Object>> c) {

        if (pathItems.size() == level) {
            c.accept(pathItems.stream().collect(Collectors.joining()), m);
        } else {
            m.entrySet().forEach((entry) -> {
                pathItems.addLast(entry.getKey());

                try {
                    processTopLayers((Map<String, Object>) entry.getValue(), pathItems, c);
                } catch (ClassCastException e) {
                    // this should not happen, but after all ignore and let pass through
                    log.error("Unexpected node above serialization level: " + entry.getValue());
                }

                pathItems.removeLast();
            });
        }

    }

    private Map<String, Object> getWrappedRoot() {

        HashMap<String, Object> root = new HashMap<>();

        // TODO: cache.entrySet is not really threadsafe..
        // tests show that it works in replicated mode but we need to switch to some proper API call once we move to a newer infinispan
        // for now it's not so critical since most conf calls will go directly to certain entries

        for (Map.Entry<String, Object> stringObjectEntry : cache.entrySet()) {
            SimpleConfigNodeUtil.replaceNode(
                    root,
                    (Map) stringObjectEntry.getValue(),
                    SimpleConfigNodeUtil.getPathItems(stringObjectEntry.getKey())
            );
        }

        return root;
    }


    @Inject
    public InfinispanCachingConfiguration(@CacheByName("configuration") Cache<String, Object> cache) {
        this.cache = cache;
    }

    @Override
    public Map<String, Object> getConfigurationRoot() throws ConfigurationException {
        return getWrappedRoot();
    }

    @Override
    public Object getConfigurationNode(String path, Class configurableClass) throws ConfigurationException {
        // TODO: implement
        return ConfigNodeUtil.getNode(getWrappedRoot(), path);
    }



    @Override
    public void lock() {
        super.lock();
    }
}