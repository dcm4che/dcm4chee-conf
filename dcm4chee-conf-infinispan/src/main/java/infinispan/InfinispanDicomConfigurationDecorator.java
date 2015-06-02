package infinispan;

import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.util.ConfigNodeUtil;
import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.decorator.Decorator;
import javax.decorator.Delegate;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@Decorator
public abstract class InfinispanDicomConfigurationDecorator implements Configuration {


    @Inject
    @Delegate
    Configuration delegate;

    @Resource(lookup = "java:jboss/infinispan/dcm4che-config")
    private EmbeddedCacheManager defaultCacheManager;

    @PostConstruct
    public void init() {
        serializedDevicesCache = defaultCacheManager.getCache();
        metadataCache = defaultCacheManager.getCache("metadata");
    }

    private static Logger log = LoggerFactory
            .getLogger(InfinispanDicomConfigurationDecorator.class);


    private Cache<String, Object> serializedDevicesCache;

    private Cache<String, Object> metadataCache;

    @Override
    public Map<String, Object> getConfigurationRoot() throws ConfigurationException {

        // if cache not inited
        if (!metadataCache.containsKey("deviceNames")) {
            Map<String, Object> configurationRoot = delegate.getConfigurationRoot();

            Map<String, Object> dicomConfigurationRoot = (Map<String, Object>) configurationRoot.get("dicomConfigurationRoot");
            Map<String, Object> dicomDevicesRoot = (Map<String, Object>) dicomConfigurationRoot.get("dicomDevicesRoot");

            for (Map.Entry<String, Object> deviceEntry : dicomDevicesRoot.entrySet()) {
                serializedDevicesCache.put(deviceEntry.getKey(), deviceEntry.getValue());
            }

            metadataCache.put("deviceNames", dicomDevicesRoot.keySet());

            return configurationRoot;
        }

        // recreate full tree from cache
        HashMap<String, Object> root = new HashMap<>();
        HashMap<String, Object> configRoot = new HashMap<>();
        HashMap<String, Object> devicesRoot = new HashMap<>();

        root.put("dicomConfigurationRoot", configRoot);
        configRoot.put("dicomDevicesRoot", devicesRoot);

        Set<String> deviceNames = (Set<String>) metadataCache.get("deviceNames");
        for (String name : deviceNames) {
            devicesRoot.put(name, serializedDevicesCache.get(name));
        }

        return root;
    }

    @Override
    public Object getConfigurationNode(String path, Class configurableClass) throws ConfigurationException {
        return ConfigNodeUtil.getNode(getConfigurationRoot(), path);
    }


    @Override
    public boolean nodeExists(String path) throws ConfigurationException {
        return ConfigNodeUtil.nodeExists(getConfigurationRoot(), path);
    }


    // TODO: opt
    @Override
    public void persistNode(String path, Map<String, Object> configNode, Class configurableClass) throws ConfigurationException {
        delegate.persistNode(path, configNode, configurableClass);
        metadataCache.clear();
        serializedDevicesCache.clear();
    }

    // TODO: opt
    @Override
    public void removeNode(String path) throws ConfigurationException {
        delegate.removeNode(path);
        metadataCache.clear();
        serializedDevicesCache.clear();
    }

    @Override
    public synchronized Iterator search(String liteXPathExpression) throws IllegalArgumentException, ConfigurationException {
        return ConfigNodeUtil.search(getConfigurationRoot(), liteXPathExpression);
    }
}
