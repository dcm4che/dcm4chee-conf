package org.dcm4chee.conf.decorators.producers;

import org.dcm4che3.conf.core.DefaultBeanVitalizer;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.storage.SingleJsonFileConfigurationStorage;
import org.dcm4chee.conf.decorators.ConfiguredDynamicDecorators;
import org.dcm4chee.conf.decorators.DynamicDecoratorsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.inject.Produces;
import java.util.Map;

public class DynamicDecoratorConfigurationProducer {

    private static final Logger LOG = LoggerFactory
            .getLogger(DynamicDecoratorConfigurationProducer.class);

    @SuppressWarnings("unchecked")
    @Produces
    @ConfiguredDynamicDecorators
    public DynamicDecoratorsConfig getDynamicDecoratorConfiguration() throws ConfigurationException {
        String path = getPath();
        SingleJsonFileConfigurationStorage storage = new SingleJsonFileConfigurationStorage(path);

        DynamicDecoratorsConfig dynamicDecorators = new DefaultBeanVitalizer().newConfiguredInstance((Map<String, Object>) storage
                .getConfigurationNode("/", null), DynamicDecoratorsConfig.class);

        if (LOG.isDebugEnabled()) {
	        for (Map.Entry<String, DynamicDecoratorsConfig.DynamicDecoratoredServiceConfig> entry : dynamicDecorators.getDecoratedServices().entrySet()) {
	            LOG.debug("Dynamic decorators for service {}:",entry.getKey());
	            for (DynamicDecoratorsConfig.DynamicDecoratorConfig dynamicDecoratorConfig : entry.getValue().getDecorators())
	                LOG.debug("Found dynamic decorator {} in configuration.", dynamicDecoratorConfig.getDecoratorClassName());
	        }
        }

        return dynamicDecorators;
    }

    private String getPath() {
        return System.getProperty("org.dcm4che.conf.dynamic.decorator.config",
                "../standalone/configuration/dcm4chee-arc/dynamic-decorators.json");
    }

}
