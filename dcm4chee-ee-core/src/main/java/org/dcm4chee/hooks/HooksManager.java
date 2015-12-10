//
/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.hooks;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
    
    private final ConcurrentMap<Type, Collection<Bean<?>>> activeHookBeansMap = new ConcurrentHashMap<>();

    /**
     * 
     * @param injectionPoint
     * @param beanManager
     * @return Returns an ordered list of active hook (bean) instances that are suitable (in CDI terms) for the
     * given injection point.
     */
    public Collection<Object> getOrderedActiveHooks(InjectionPoint injectionPoint, BeanManager beanManager) {
        return resolveHooks(injectionPoint, beanManager);
    }
    
    private Collection<Object> resolveHooks(InjectionPoint injectionPoint, BeanManager beanManager) {
        ParameterizedType type = (ParameterizedType) injectionPoint.getType();
        Type hookType = type.getActualTypeArguments()[0];
        
        Collection<Bean<?>> orderedHookBeans = activeHookBeansMap.get(hookType);
        if(orderedHookBeans == null) {
            orderedHookBeans = getOrderedHookBeansFromConfig(hookType, beanManager);
            activeHookBeansMap.putIfAbsent(hookType, orderedHookBeans);
        }
        
        List<Object> beanInstances = new ArrayList<>();
        for(Bean<?> hookBean : orderedHookBeans) {
            Object hook = getBeanInstance(hookBean, hookType, beanManager);
            beanInstances.add(hook);
        }
        
        return beanInstances;
    }
    
    private Collection<Bean<?>> getOrderedHookBeansFromConfig(Type hookType, BeanManager beanManager) {
        TreeMap<Double,Bean<?>> resolvedHooks = new TreeMap<>();
        Class<?> hookTypeClass = (Class<?>)hookType;
        
        for (Bean<?> hookBean : beanManager.getBeans(hookType)) {
            Class<?> hookBeanClass = hookBean.getBeanClass();
            Double priority = getHookPriority(hookTypeClass, hookBeanClass);
            if(priority != null) {
                resolvedHooks.put(priority, hookBean);
                LOG.debug("Configuring the hook implementation {} for hook type {} with priority {}.", hookBeanClass.getName(), hookTypeClass.getName(), priority);
            } else {
                LOG.debug("Not configuring the hook implementation {} for hook type {} because it is not in the configuration.", hookBeanClass.getName(), hookTypeClass.getName());
            }
        }

        return resolvedHooks.values();
    }
    
    private Double getHookPriority(Class<?> hookTypeClass, Class<?> hookBeanClass) {
        String hookTypeClassName = hookTypeClass.getName();
        HookTypeConfig hookTypeConfig = hooksConfig.getHooks().get(hookTypeClassName);
        
        if (hookTypeConfig == null) {
            LOG.warn("Hook type {} not defined in the hooks configuration.", hookTypeClassName);
            return null;
        }
        
        String hookBeanClassName = hookBeanClass.getName();

        Map<String, Double> hookBean2PriorityMap = hookTypeConfig.getPrioritiesMap();
        // to handle proxies like org.abc.MyDecorator$_$$WeldProxy
        for (Entry<String,Double> entry : hookBean2PriorityMap.entrySet()) {
            if (hookBeanClassName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
 
        return null;
    }
    
    private static Object getBeanInstance(Bean<?> bean, Type beanType, BeanManager beanManager) {
        CreationalContext<?> dependentScopeCreationalContext = beanManager.createCreationalContext(null);
        Object beanInstance = beanManager.getReference(bean, beanType, dependentScopeCreationalContext);
        return beanInstance;
    }
    
}
