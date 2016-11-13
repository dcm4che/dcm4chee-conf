package org.dcm4chee.hooks.producers;

import org.dcm4che.kiwiyard.ConfigurationSettingsLoader;
import org.dcm4che.kiwiyard.core.DefaultBeanVitalizer;
import org.dcm4che.kiwiyard.core.api.ConfigurationException;
import org.dcm4che.kiwiyard.core.api.Path;
import org.dcm4che.kiwiyard.core.storage.SingleJsonFileConfigurationStorage;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.hooks.HooksConfig;
import org.dcm4chee.hooks.ProducedHooksConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import java.util.Map;


public class HooksConfigProducer {
    private static final Logger LOG = LoggerFactory.getLogger(HooksConfigProducer.class);

    @Produces
    @ProducedHooksConfig
    @ApplicationScoped
    public HooksConfig getHooksConfiguration() throws ConfigurationException {
        String path = getPath();
        SingleJsonFileConfigurationStorage storage = new SingleJsonFileConfigurationStorage(path);

        HooksConfig hooksConfig = new DefaultBeanVitalizer().newConfiguredInstance((Map<String, Object>) storage
                .getConfigurationNode(Path.ROOT, null), HooksConfig.class);

        for (Map.Entry<String, HooksConfig.HookTypeConfig> entry : hooksConfig.getHooks().entrySet()) {
            LOG.info("Hooks for interface {}:", entry.getKey());
            for (HooksConfig.HookImplementation hookImpl : entry.getValue().getHookImplementations())
                LOG.info("Found Hook {} in configuration.", hookImpl.getHookClassName());
        }

        return hooksConfig;
    }

    private String getPath() {
        String fileName = ConfigurationSettingsLoader.getPropertyWithNotice(
                System.getProperties(),
                "org.dcm4che.conf.hook.config",
                "${jboss.server.config.dir}/dcm4chee-arc/dynamic-decorators.json");

        return StringUtils.replaceSystemProperties(fileName);
    }

}
