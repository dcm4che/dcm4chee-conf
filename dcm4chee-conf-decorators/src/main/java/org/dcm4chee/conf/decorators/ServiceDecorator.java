package org.dcm4chee.conf.decorators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ServiceDecorator<T> {

    @Inject
    @ConfiguredDynamicDecorators
    DynamicDecoratorsConfig decoratorsConfig;


    @Inject
    @ConfiguredDynamicDecorators
    List<String> disabledDecorators;

    private static final Logger LOG = LoggerFactory.getLogger(ServiceDecorator.class);

    private Collection<DelegatingServiceImpl<T>> orderedDecorators = null;

    public Collection<DelegatingServiceImpl<T>> getOrderedDecorators(Instance<DelegatingServiceImpl<T>> dynamicDecoratorsForService, String clazz) {
        if (orderedDecorators == null) {
            initServiceDecorators(dynamicDecoratorsForService, clazz);
        }
        LOG.trace("Retrieved {}.", orderedDecorators);
        return orderedDecorators;
    }


    private synchronized void initServiceDecorators(Instance<DelegatingServiceImpl<T>> dynamicDecoratorsForService, String clazz) {
        if (orderedDecorators != null) {
            LOG.debug("Decorators for {} were already created. Will not recreate.", clazz);
            return;
        }
        LOG.debug("Creating decorators for {}.", clazz);

        Map<Double, DelegatingServiceImpl<T>> decorators = new TreeMap<Double, DelegatingServiceImpl<T>>();

        for (DelegatingServiceImpl<T> dynamicDecorator : dynamicDecoratorsForService) {

            //need to be careful - if the dynamicDecorator object has ApplicationScoped scope, then we need to go through a weld proxy class to get the annotation
            //we would do this by doing:
            //Class<?> clazz = dynamicDecorator.getClass().getSuperclass();
            Class<?> decoratorClazz = dynamicDecorator.getClass();
            if (isDecoratorEnabled(decoratorClazz, clazz)) {

                Double priority = null;
                try {
                    priority = decoratorsConfig.getDecoratedServices().get(clazz).getPrioritiesMap().get(decoratorClazz.getName());
                } catch (NullPointerException e) {
                    throw new RuntimeException("Dynamic decorator configuration not found for decorator " + decoratorClazz.getName() + " of service " + clazz, e);
                }
                decorators.put(priority, dynamicDecorator);
                LOG.debug("Configuring the decorator {} with priority {}.", decoratorClazz, priority);
            }
        }

        orderedDecorators = decorators.values();
    }

    private boolean isDecoratorEnabled(Class<?> decoratorClazz, String serviceClazz) {
        if (!decoratorsConfig.getDecoratedServices().containsKey(serviceClazz)) {
            LOG.warn("Service class {} not defined in the dynamic decorators configuration.", serviceClazz);
            return false;
        }

        if (!decoratorsConfig.getDecoratedServices().get(serviceClazz).getPrioritiesMap().containsKey(decoratorClazz.getName())) {
            LOG.debug("Not configuring the decorator {} because it is not in the configuration.", decoratorClazz);
            return false;
        }

        if (disabledDecorators.contains(decoratorClazz.getName())) {
            LOG.debug("Not configuring the decorator {} because it is disabled.", decoratorClazz);
            return false;
        }

        return true;
    }

}
