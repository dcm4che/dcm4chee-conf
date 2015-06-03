package org.dcm4chee.conf.decorators.producers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.inject.Produces;

import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.storage.SingleJsonFileConfigurationStorage;
import org.dcm4chee.conf.decorators.ConfiguredDynamicDecorators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicDecoratorConfigurationProducer {
    private static final String DECORATOR_CLASS_NAME = "decoratorClassName";
    private static final String PRIORITY = "priority";
    private static final double DEFAULT_PRIORITY = 1.0;
    // TODO: make sure deterministic ordering if priority is the same

    private static final Logger LOG = LoggerFactory
            .getLogger(DynamicDecoratorConfigurationProducer.class);

    @Produces
    @ConfiguredDynamicDecorators
    public Map<String, Double> getDynamicDecoratorConfiguration() throws ConfigurationException {
        String path = getPath();
        SingleJsonFileConfigurationStorage storage = new SingleJsonFileConfigurationStorage(path);
        List<Map<String, Object>> config = (List<Map<String, Object>>) storage
                .getConfigurationNode("dynamicDecorators", null);

        Map<String, Double> configuredDynamicDecorators = new HashMap<String, Double>();
        if (config != null) {
            for (Map<String, Object> object : config) {
                if (object.containsKey(DECORATOR_CLASS_NAME)) {
                    String decoratorClassName = (String) object.get(DECORATOR_CLASS_NAME);
                    double priority = getPriority(object);
                    LOG.debug("Found dynamic decorator {} in configuration with priority {}.", decoratorClassName, priority);
                    configuredDynamicDecorators.put(decoratorClassName, priority);
                }
            }
        }

        return configuredDynamicDecorators;
    }

    private String getPath() {
        return System.getProperty("org.dcm4che.conf.dynamic.decorator.config",
                        "../standalone/configuration/dcm4chee-arc/dynamic-decorators.json");
    }

    private double getPriority(Map<String, Object> map) {
        double priority;
        if (map.containsKey(PRIORITY)) {
            priority = (double) map.get(PRIORITY);
        } else {
            priority = DEFAULT_PRIORITY;
        }
        return priority;
    }
}
