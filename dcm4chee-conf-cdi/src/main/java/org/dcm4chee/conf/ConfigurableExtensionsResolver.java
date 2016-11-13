package org.dcm4chee.conf;

import org.dcm4che.kiwiyard.core.api.ConfigurableClass;
import org.dcm4che.kiwiyard.ee.ConfigurableExtensionsProvider;
import org.dcm4che3.net.AEExtension;
import org.dcm4che3.net.ConnectionExtension;
import org.dcm4che3.net.DeviceExtension;
import org.dcm4che3.net.hl7.HL7ApplicationExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@ApplicationScoped
public class ConfigurableExtensionsResolver implements ConfigurableExtensionsProvider
{

    private final static Logger log = LoggerFactory.getLogger(ConfigurableExtensionsResolver.class);


    // TODO: make as before, inject AEs, Device, HL7 exts..

    @Inject
    private Instance<AEExtension> aeExtensions;

    @Inject
    private Instance<DeviceExtension> deviceExtensions;

    @Inject
    private Instance<HL7ApplicationExtension> hl7ApplicationExtensions;

    @Inject
    private Instance<ConnectionExtension> connectionExtensions;


    /**
     * To avoid logging warning multiple times
     */
    private boolean loggedWarnings = false;


    @Override
    public List<Class> resolveExtensionsList() {
        return resolveExtensionsMap( false )
                .entrySet()
                .stream()
                .flatMap( ( e ) -> e.getValue().stream() )
                .distinct()
                .collect( Collectors.toList() );
    }

    @Override
    public Map<Class, List<Class>> resolveExtensionsMap( boolean doLog ) {

        List<Class> extList = new ArrayList<>();
        Map<Class, List<Class>> extByBaseExtMap = new HashMap<>();

        // Workaround to avoid putting kiwi dependencies in dcm4che for now

        BiConsumer<Class,Iterable> extCollector = (extClazz, exts) ->{
            List<Class> extensionClasses = new ArrayList<>(  );
            for ( Object ext : exts )
            {
                if (ext.getClass().getAnnotation(ConfigurableClass.class) != null)
                {
                    extensionClasses.add( ext.getClass() );
                    extList.add( ext.getClass() );
                }
            }
            extByBaseExtMap.put( extClazz, extensionClasses );
        };

        extCollector.accept( AEExtension.class, aeExtensions);
        extCollector.accept( DeviceExtension.class, deviceExtensions);
        extCollector.accept( HL7ApplicationExtension.class, hl7ApplicationExtensions);
        extCollector.accept( ConnectionExtension.class, connectionExtensions);

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


        // validate simple name uniqueness
        HashSet<String> simpleNames = new HashSet<>();
        HashSet<String> fullNames = new HashSet<>();
        for (Class configurableExtension : extList) {

            String simpleName = configurableExtension.getClass().getSimpleName();
            String fullName = configurableExtension.getClass().getName();
            boolean simpleNameExisted = !simpleNames.add(simpleName);
            boolean fullNameExisted = !fullNames.add(fullName);


            if (simpleNameExisted && !fullNameExisted) {
                // we have a duplicate, let's find out all occurrences
                List<Class> conflicting = extList.stream()
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

        return extByBaseExtMap;
    }

}
