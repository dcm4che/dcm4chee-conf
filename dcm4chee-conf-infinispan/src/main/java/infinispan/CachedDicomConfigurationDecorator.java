package infinispan;

import org.dcm4che3.conf.core.DelegatingConfiguration;
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.util.ConfigNodeUtil;
import org.infinispan.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

//TODO: convert to CDI decorator
public class CachedDicomConfigurationDecorator extends DelegatingConfiguration {


    private static Logger log = LoggerFactory
            .getLogger(CachedDicomConfigurationDecorator.class);



    private Cache<String, Object> serializedDevicesCache;
    private Cache<String, Object> metadataCache;

    public CachedDicomConfigurationDecorator(Configuration delegate, Cache<String, Object> serializedDevicesCache, Cache<String, Object> metadataCache) {
        super(delegate);
        this.serializedDevicesCache = serializedDevicesCache;
        this.metadataCache = metadataCache;
    }

    @Override
    public Map<String, Object> getConfigurationRoot() throws ConfigurationException {

        // if cache not inited
        if (serializedDevicesCache.isEmpty()) {
            Map<String, Object> configurationRoot = super.getConfigurationRoot();

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
        return false;
    }

    @Override
    public void persistNode(String path, Map<String, Object> configNode, Class configurableClass) throws ConfigurationException {
        metadataCache.clear();
        serializedDevicesCache.clear();
    }

    @Override
    public void refreshNode(String path) throws ConfigurationException {
        metadataCache.clear();
        serializedDevicesCache.clear();
    }

    @Override
    public void removeNode(String path) throws ConfigurationException {
        metadataCache.clear();
        serializedDevicesCache.clear();
    }

    @Override
    public synchronized Iterator search(String liteXPathExpression) throws IllegalArgumentException, ConfigurationException {
        return ConfigNodeUtil.search(getConfigurationRoot(), liteXPathExpression);
    }
}
