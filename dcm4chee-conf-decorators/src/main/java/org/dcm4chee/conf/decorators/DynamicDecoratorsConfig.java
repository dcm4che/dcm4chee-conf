package org.dcm4chee.conf.decorators;

import org.dcm4che.kiwiyard.core.api.ConfigurableClass;
import org.dcm4che.kiwiyard.core.api.ConfigurableProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Roman K
 */
@ConfigurableClass
public class DynamicDecoratorsConfig {

    /**
     * Key = Service class, e.g. org.dcm4chee.archive.store.StoreService
     */
    @ConfigurableProperty
    Map<String, DynamicDecoratoredServiceConfig> decoratedServices = new TreeMap<>();

    public DynamicDecoratorsConfig() {
    }

    public Map<String, DynamicDecoratoredServiceConfig> getDecoratedServices() {
        return decoratedServices;
    }

    public void setDecoratedServices(Map<String, DynamicDecoratoredServiceConfig> decoratedServices) {
        this.decoratedServices = decoratedServices;
    }

    @ConfigurableClass
    public static class DynamicDecoratoredServiceConfig {

        /**
         * Key = dynamic decorator class
         */
        @ConfigurableProperty
        List<DynamicDecoratorConfig> decorators = new ArrayList<>();

        public DynamicDecoratoredServiceConfig() {
        }

        // populated in the setter of decorators
        private transient Map<String, Double> prioritiesMap = new TreeMap<>();

        public List<DynamicDecoratorConfig> getDecorators() {
            return decorators;
        }

        public void setDecorators(List<DynamicDecoratorConfig> decorators) {
            this.decorators = decorators;
            
            double priority = decorators.size();

            for (DynamicDecoratorConfig decoratorConfig : decorators) {
                prioritiesMap.put(decoratorConfig.getDecoratorClassName(), priority);
                priority--;
            }
        }

        public Map<String, Double> getPrioritiesMap() {
            return prioritiesMap;
        }
    }

    @ConfigurableClass
    public static class DynamicDecoratorConfig {

        @ConfigurableProperty
        String decoratorClassName;

        public DynamicDecoratorConfig() {
        }

        public String getDecoratorClassName() {
            return decoratorClassName;
        }

        public void setDecoratorClassName(String decoratorClassName) {
            this.decoratorClassName = decoratorClassName;
        }
    }
}
