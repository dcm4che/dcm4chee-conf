package org.dcm4chee.conf.decorators;

import org.dcm4che3.conf.core.DefaultBeanVitalizer;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.storage.SingleJsonFileConfigurationStorage;
import org.dcm4che3.net.Device;
import org.dcm4chee.conf.extensibility.PluginSettingsExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.util.*;

@ApplicationScoped
public class DynamicDecoratorManager {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicDecoratorManager.class);
    public static final String DECORATOR_CONFIG_PROPERTY = "org.dcm4che.conf.dynamic.decorator.config";

    @Inject
    Device device;

    private DynamicDecoratorsConfig decoratorsConfig;
    private List<String> disabledDecorators;

    private final Map<Class, Object> activeDecorators = Collections.synchronizedMap(new HashMap<Class, Object>());


    @SuppressWarnings("unchecked")
    public <T> Collection<DelegatingServiceImpl<T>> getOrderedDecorators(Instance<DelegatingServiceImpl<T>> availableDynamicDecorators, Class<? extends T> clazz, boolean usecache) {
        if (!usecache) return resolveDynamicDecorators(availableDynamicDecorators, clazz.getName());

        synchronized (activeDecorators) {

            Object decorators = activeDecorators.get(clazz);
            if (decorators == null) {
                decorators = resolveDynamicDecorators(availableDynamicDecorators, clazz.getName());
                activeDecorators.put(clazz, decorators);
            }
            LOG.trace("Retrieved {}.", activeDecorators);

            return (Collection<DelegatingServiceImpl<T>>) decorators;
        }
    }


    public <T> Collection<DelegatingServiceImpl<T>> getOrderedDecorators(Instance<DelegatingServiceImpl<T>> availableDynamicDecorators, Class<? extends T> clazz) {
        return getOrderedDecorators(availableDynamicDecorators, clazz, true);
    }

    /**
     * Matches the available dynamic decorators with the configuration and produces an ordered and filtered collection of active decorators
     *
     * @param availableDynamicDecorators
     * @param clazz                      service class
     * @return
     */
    private synchronized <T> Collection<DelegatingServiceImpl<T>> resolveDynamicDecorators(Instance<DelegatingServiceImpl<T>> availableDynamicDecorators, String clazz) {

        LOG.debug("Creating decorators for {}.", clazz);

        TreeMap<Double, DelegatingServiceImpl<T>> decorators = new TreeMap<Double, DelegatingServiceImpl<T>>();

        for (DelegatingServiceImpl<T> dynamicDecorator : availableDynamicDecorators) {

            Class<?> decoratorClazz = dynamicDecorator.getClass();
            if (isDecoratorEnabled(decoratorClazz, clazz)) {

                Double priority = null;

                Map<String, Double> prioritiesMap = getDecoratorsConfig().getDecoratedServices().get(clazz).getPrioritiesMap();

                // to handle proxies like org.abc.MyDecorator$_$$WeldProxy
                for (Map.Entry<String, Double> stringDoubleEntry : prioritiesMap.entrySet()) {
                    if (decoratorClazz.getName().startsWith(stringDoubleEntry.getKey())) {
                        priority = stringDoubleEntry.getValue();
                        break;
                    }
                }

                if (priority == null)
                    throw new RuntimeException("Dynamic decorator configuration not found for decorator " + decoratorClazz.getName() + " of service " + clazz);

                decorators.put(priority, dynamicDecorator);
                LOG.debug("Configuring the decorator {} with priority {}.", decoratorClazz, priority);
            }
        }

        return decorators.values();
    }

    private boolean isDecoratorEnabled(Class<?> decoratorClazz, String serviceClazz) {
        if (!getDecoratorsConfig().getDecoratedServices().containsKey(serviceClazz)) {
            LOG.warn("Service class {} not defined in the dynamic decorators configuration.", serviceClazz);
            return false;
        }


        Map<String, Double> prioritiesMap = getDecoratorsConfig().getDecoratedServices().get(serviceClazz).getPrioritiesMap();

        // to handle proxies like org.abc.MyDecorator$_$$WeldProxy
        boolean found = false;
        for (String configuredDecoratorClassName : prioritiesMap.keySet())
            if (decoratorClazz.getName().startsWith(configuredDecoratorClassName))
                found = true;

        if (!found) {
            LOG.debug("Not configuring the decorator {} because it is not in the configuration.", decoratorClazz);
            return false;
        }

        if (getDisabledDecorators().contains(decoratorClazz.getName())) {
            LOG.debug("Not configuring the decorator {} because it is disabled.", decoratorClazz);
            return false;
        }

        return true;
    }

    public synchronized DynamicDecoratorsConfig getDecoratorsConfig() {
        if (decoratorsConfig == null) decoratorsConfig = getDynamicDecoratorConfiguration();
        return decoratorsConfig;
    }

    @SuppressWarnings("unchecked")
    public DynamicDecoratorsConfig getDynamicDecoratorConfiguration() {
        String path = System.getProperty(DECORATOR_CONFIG_PROPERTY, "../standalone/configuration/dcm4chee-arc/dynamic-decorators.json");
        SingleJsonFileConfigurationStorage storage = new SingleJsonFileConfigurationStorage(path);

        DynamicDecoratorsConfig dynamicDecorators = null;
        try {
            dynamicDecorators = new DefaultBeanVitalizer().newConfiguredInstance(
                    (Map<String, Object>) storage.getConfigurationNode("/", null),
                    DynamicDecoratorsConfig.class
            );

        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }

        for (Map.Entry<String, DynamicDecoratorsConfig.DynamicDecoratoredServiceConfig> entry : dynamicDecorators.getDecoratedServices().entrySet()) {
            LOG.info("Dynamic decorators for service {}:", entry.getKey());
            for (DynamicDecoratorsConfig.DynamicDecoratorConfig dynamicDecoratorConfig : entry.getValue().getDecorators())
                LOG.info("Found dynamic decorator {} in configuration.", dynamicDecoratorConfig.getDecoratorClassName());
        }

        return dynamicDecorators;
    }

    public synchronized List<String> getDisabledDecorators() {
        if (disabledDecorators == null)
            disabledDecorators = getDisabledDecoratorsFromConfig();
        return disabledDecorators;
    }

    //TODO: how to detect changes to this property, and re-generate the service decorators after?
    private List<String> getDisabledDecoratorsFromConfig() {
        if (getDisabledDecorators() == null) {
            PluginSettingsExtension devExt = device.getDeviceExtension(PluginSettingsExtension.class);
            if (devExt != null) {
                disabledDecorators = devExt.getDisabledDecorators();
            }
        }
        LOG.debug("Returning disabled decorators: {}", getDisabledDecorators());
        return getDisabledDecorators();
    }

}
