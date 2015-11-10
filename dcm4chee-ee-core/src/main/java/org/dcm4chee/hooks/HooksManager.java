package org.dcm4chee.hooks;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;

import org.dcm4chee.hooks.HooksConfig.HookTypeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class HooksManager {
    private static final Logger LOG = LoggerFactory.getLogger(HooksManager.class);

    @Inject
    @ProducedHooksConfig
    private HooksConfig hooksConfig;

    private final ConcurrentMap<Class<?>, Collection<Object>> activeHooksMap = new ConcurrentHashMap<>();

    /**
     * 
     * @param injectionPoint
     * @param beanManager
     * @return Returns an ordered list of active hooks that are suitable (in CDI terms) for the
     * given injection point.
     */
    public <T> Collection<T> getOrderedActiveHooks(InjectionPoint injectionPoint, BeanManager beanManager) {
        ParameterizedType type = (ParameterizedType) injectionPoint.getType();
        Type hookType = type.getActualTypeArguments()[0];
        Class<?> hookTypeClass = (Class<?>)hookType;
        
        Collection<Object> hooks = activeHooksMap.get(hookTypeClass);
        if (hooks == null) {
            hooks = resolveHooks(injectionPoint, beanManager);
            activeHooksMap.putIfAbsent(hookTypeClass, hooks);
        }

        return (Collection<T>)hooks;
    }
    
    /**
     * Matches the available hooks with the configuration and produces an ordered and filtered collection of active hooks
     *
     * @param availableHooks
     * @param clazz
     * @return
     */
    private synchronized Collection<Object> resolveHooks(InjectionPoint injectionPoint, BeanManager beanManager) {
        TreeMap<Double,Object> resolvedHooks = new TreeMap<>();

        ParameterizedType type = (ParameterizedType) injectionPoint.getType();
        Type hookType = type.getActualTypeArguments()[0];
        Class<?> hookTypeClass = (Class<?>)hookType;
        
        Set<Bean<?>> hookBeans = beanManager.getBeans(hookType);
        
        for (Bean<?> hookBean : hookBeans) {
            Class<?> hookClass = hookBean.getBeanClass();
            if (isHookEnabled(hookTypeClass, hookClass)) {
                Double priority = null;

                Map<String, Double> hookImplementationsMap = hooksConfig.getHooks().get(hookTypeClass.getName()).getPrioritiesMap();

                // to handle proxies like org.abc.MyDecorator$_$$WeldProxy
                for (Map.Entry<String, Double> stringDoubleEntry : hookImplementationsMap.entrySet()) {
                    if (hookClass.getName().startsWith(stringDoubleEntry.getKey())) {
                        priority = stringDoubleEntry.getValue();
                        break;
                    }
                }

                if (priority == null)
                    throw new RuntimeException("Hook configuration not found for hook implementation " + hookClass.getName() + " of hookType " + hookTypeClass.getName());

                Object hook = createBeanInstance(hookBean, beanManager);
                
                resolvedHooks.put(priority, hook);
                LOG.debug("Configuring the hook {} with priority {}.", hookType, priority);
            }
        }

        return resolvedHooks.values();
    }
    
    private static Object createBeanInstance(Bean<?> bean, BeanManager beanManager) {
        CreationalContext dependentScopeCreationalContext = beanManager.createCreationalContext(null);
        return bean.create(dependentScopeCreationalContext);
    }

    private boolean isHookEnabled(Class<?> hookType, Class<?> hookClazz) {
        String hookTypeName = hookType.getName();
        HookTypeConfig hookTypeConfig = hooksConfig.getHooks().get(hookTypeName);
        
        if (hookTypeConfig == null) {
            LOG.warn("Hook type {} not defined in the hooks configuration.", hookTypeName);
            return false;
        }

        Map<String, Double> prioritiesMap = hookTypeConfig.getPrioritiesMap();

        // to handle proxies like org.abc.MyDecorator$_$$WeldProxy
        boolean found = false;
        for (String configuredHookClassName : prioritiesMap.keySet())
            if (hookClazz.getName().startsWith(configuredHookClassName))
                found = true;

        if (!found) {
            LOG.debug("Not configuring the decorator {} because it is not in the configuration.", hookClazz);
            return false;
        }

        return true;
    }
    
}
