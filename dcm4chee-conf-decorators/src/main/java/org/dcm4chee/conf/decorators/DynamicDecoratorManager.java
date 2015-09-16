package org.dcm4chee.conf.decorators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.util.*;

public class DynamicDecoratorManager {

    @Inject
    @ConfiguredDynamicDecorators
    DynamicDecoratorsConfig decoratorsConfig;


    @Inject
    @ConfiguredDynamicDecorators
    Instance<List<String>> disabledDecorators;

    private static final Logger LOG = LoggerFactory.getLogger(DynamicDecoratorManager.class);

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

                Map<String, Double> prioritiesMap = decoratorsConfig.getDecoratedServices().get(clazz).getPrioritiesMap();

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
        if (!decoratorsConfig.getDecoratedServices().containsKey(serviceClazz)) {
            LOG.warn("Service class {} not defined in the dynamic decorators configuration.", serviceClazz);
            return false;
        }


        Map<String, Double> prioritiesMap = decoratorsConfig.getDecoratedServices().get(serviceClazz).getPrioritiesMap();

        // to handle proxies like org.abc.MyDecorator$_$$WeldProxy
        boolean found = false;
        for (String configuredDecoratorClassName : prioritiesMap.keySet())
            if (decoratorClazz.getName().startsWith(configuredDecoratorClassName))
                found = true;

        if (!found) {
            LOG.debug("Not configuring the decorator {} because it is not in the configuration.", decoratorClazz);
            return false;
        }

        if (!disabledDecorators.isUnsatisfied())
            if (disabledDecorators.get().contains(decoratorClazz.getName())) {
                LOG.debug("Not configuring the decorator {} because it is disabled.", decoratorClazz);
                return false;
            }

        return true;
    }
}
