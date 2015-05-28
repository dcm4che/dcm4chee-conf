package infinispan;

import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;

import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InfinispanConfigurationCacheFactory {

    @Resource(lookup="java:jboss/infinispan/dcm4che-config")
    private EmbeddedCacheManager defaultCacheManager;

    public Cache getDevicesCache() {
        return defaultCacheManager.getCache();
    }
    public Cache getMetadataCache() {
        return defaultCacheManager.getCache("metadata");
    }

}
