package org.dcm4chee.conf.storage;

import org.dcm4che3.conf.ConfigurationSettingsLoader;
import org.dcm4che3.conf.core.ExtensionMergingConfiguration;
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.Path;
import org.dcm4che3.conf.core.normalization.DefaultsAndNullFilterDecorator;
import org.dcm4che3.conf.core.olock.HashBasedOptimisticLockingConfiguration;
import org.dcm4che3.conf.dicom.CommonDicomConfiguration;
import org.dcm4chee.conf.ConfigurableExtensionsResolver;
import org.dcm4chee.conf.notif.ConfigNotificationDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.util.List;

@ApplicationScoped
public class ConfigurationKeeper
{

    public static final Logger log = LoggerFactory.getLogger(ConfigurationKeeper.class);

    private static final String DISABLE_OLOCK_PROP = "org.dcm4che.conf.olock.disabled";
    private static final String ENABLE_MERGE_CONFIG = "org.dcm4che.conf.merge.enabled";

    // components
    @Inject
    InfinispanCachingConfigurationDecorator infinispanCachingConfigurationDecorator;

    @Inject
    InfinispanDicomReferenceIndexingDecorator indexingDecorator;

    @Inject
    @Any
    private Instance<Configuration> availableConfigStorage;

    @Inject
    ConfigNotificationDecorator configNotificationDecorator;

    @Inject
    ConfigurableExtensionsResolver extensionsProvider;

    private Configuration configuration;


    @PostConstruct
    @TransactionAttribute( TransactionAttributeType.REQUIRES_NEW)
    public void init() {
        // detect user setting (system property) for config backend type
        String storageType = ConfigurationSettingsLoader.getPropertyWithNotice(
                System.getProperties(),
                Configuration.CONF_STORAGE_SYSTEM_PROP, Configuration.ConfigStorageType.JSON_FILE.name().toLowerCase()
        );
        log.info("Creating dcm4che configuration Singleton EJB. Resolving underlying configuration storage '{}' ...", storageType);

        // resolve the corresponding implementation
        Configuration storage;
        try {
            storage = availableConfigStorage.select(new ConfigurationStorage.ConfigStorageAnno(storageType)).get();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to initialize dcm4che configuration storage '" + storageType + "'", e);
        }

        // decorate with config notifications
        configNotificationDecorator.setDelegate(storage);
        storage = configNotificationDecorator;

        // decorate with cache
        infinispanCachingConfigurationDecorator.setDelegate(storage);
        storage = infinispanCachingConfigurationDecorator;

        // decorate with reference indexing/resolution
        indexingDecorator.setDelegate(storage);
        storage = indexingDecorator;

        List<Class> allExtensionClasses = extensionsProvider.resolveExtensionsList();

        // ExtensionMergingConfiguration
        if ((System.getProperty(ENABLE_MERGE_CONFIG) != null) && Boolean.valueOf(System.getProperty(ENABLE_MERGE_CONFIG))) {
            storage = new ExtensionMergingConfiguration(storage, allExtensionClasses);
        }

        // olocking
        if (System.getProperty(DISABLE_OLOCK_PROP) == null) {
            storage = new HashBasedOptimisticLockingConfiguration(
                    storage,
                    allExtensionClasses);
        }

        // defaults filtering
        storage = new DefaultsAndNullFilterDecorator(storage, allExtensionClasses, CommonDicomConfiguration.createDefaultDicomVitalizer());


        configuration = storage;

        // bootstrap
        configuration.lock();
        configuration.refreshNode( Path.ROOT);

        log.info("dcm4che configuration singleton EJB created");
    }

    public Configuration getConfiguration()
    {
        return configuration;
    }
}
