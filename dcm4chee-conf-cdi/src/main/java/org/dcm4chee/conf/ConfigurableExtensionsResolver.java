package org.dcm4chee.conf;

import org.dcm4che3.conf.core.api.ConfigurableClass;
import org.dcm4che3.conf.core.api.ConfigurableClassExtension;
import org.dcm4che3.conf.core.util.Extensions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class ConfigurableExtensionsResolver {

    private final static Logger log = LoggerFactory.getLogger(ConfigurableExtensionsResolver.class);

    @Inject
    private Instance<ConfigurableClassExtension<?>> allExtensions;

    /**
     * To avoid logging warning multiple times
     */
    private boolean loggedWarnings = false;


    public List<Class> resolveExtensionsList() {
        List<Class> list = new ArrayList<>();
        for (ConfigurableClassExtension extension : getAllConfigurableExtensions())
            if (!list.contains(extension.getClass()))
                list.add(extension.getClass());

        return list;
    }

    public Map<Class, List<Class>> resolveExtensionsMap(boolean doLog) {

        Map<Class, List<Class>> extByBaseExtMap = Extensions.getAMapOfExtensionsByBaseExtension(getAllConfigurableExtensions());


        if (doLog) {

            String extensionsLog = "";
            for (Map.Entry<Class, List<Class>> classListEntry : extByBaseExtMap.entrySet()) {
                extensionsLog += "\nExtension classes of " + classListEntry.getKey().getSimpleName() + ":\n";

                for (Class aClass : classListEntry.getValue())
                    extensionsLog += aClass.getName() + "\n";
            }

            extensionsLog += "\n";

            log.info(extensionsLog);
        }

        return extByBaseExtMap;
    }

    /**
     * @return all extension classes that have a ConfigurableClass annotation
     */
    private List<ConfigurableClassExtension> getAllConfigurableExtensions() {
        List<ConfigurableClassExtension> configurableExtensions = new ArrayList<>();
        for (ConfigurableClassExtension extension : allExtensions) {
            if (extension.getClass().getAnnotation(ConfigurableClass.class) != null)
                configurableExtensions.add(extension);
        }

        // make sure simple class names are unique
        HashSet<String> simpleNames = new HashSet<>();
        HashSet<String> fullNames = new HashSet<>();
        for (ConfigurableClassExtension configurableExtension : configurableExtensions) {

            String simpleName = configurableExtension.getClass().getSimpleName();
            String fullName = configurableExtension.getClass().getName();
            boolean simpleNameExisted = !simpleNames.add(simpleName);
            boolean fullNameExisted = !fullNames.add(fullName);


            if (simpleNameExisted && !fullNameExisted) {
                // we have a duplicate, let's find out all occurrences
                List<ConfigurableClassExtension> conflicting = configurableExtensions.stream()
                        .filter((ext) -> ext.getClass().getSimpleName().equals(simpleName))
                        .collect(Collectors.toList());

                throw new IllegalArgumentException("Simple class names of configurable extensions MUST be unique. This rule was violated by classes:\n" + conflicting);

            } else if (simpleNameExisted && fullNameExisted) {
                // if fullname existed as well, it means we have a class with the same name
                // could happen when deploying duplicate libs, etc, so not as critical, just print a warning

                if (!loggedWarnings) log.warn("Found duplicate configurable extension class: " + fullName);
            }
        }

        loggedWarnings = true;

        return configurableExtensions;
    }

}
