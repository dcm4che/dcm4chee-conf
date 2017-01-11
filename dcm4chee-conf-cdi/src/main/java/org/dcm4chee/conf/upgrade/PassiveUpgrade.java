package org.dcm4chee.conf.upgrade;

import org.dcm4che3.conf.api.internal.DicomConfigurationManager;
import org.dcm4che3.conf.api.upgrade.ConfigurationMetadata;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PassiveUpgrade
{

    private static Logger log = LoggerFactory.getLogger(PassiveUpgrade.class);

    public static final String PASSIVE_UPGRADE_TIMEOUT = "org.dcm4che.conf.upgrade.passiveTimeoutSec";

    private final DicomConfigurationManager dicomConfigurationManager;
    private final UpgradeSettings upgradeSettings;
    private final String appName;

    public PassiveUpgrade( DicomConfigurationManager dicomConfigurationManager,
            UpgradeSettings upgradeSettings, String appName )
    {
        this.dicomConfigurationManager = dicomConfigurationManager;
        this.upgradeSettings = upgradeSettings;
        this.appName = appName;
    }

    public void waitUntilOtherRunnerUpdatesToTargetConfigurationVersion() {

        Integer timeout, configuredTimeout;
        try {
            configuredTimeout = timeout = Integer.valueOf(System.getProperty(PASSIVE_UPGRADE_TIMEOUT, "300"));
        } catch (NumberFormatException e) {
            throw new RuntimeException(PASSIVE_UPGRADE_TIMEOUT + " property must be an integer", e);
        }

        log.info("This deployment (" + appName + ") is not configured to perform the configuration upgrade." +
                " Waiting for the upgrade to be performed by deployment '" + upgradeSettings.getActiveUpgradeRunnerDeployment() + "'." +
                "\nTimeout: " + configuredTimeout + " sec" +
                "\nExpected configuration version: " + upgradeSettings.getUpgradeToVersion());

        boolean success = false;
        while (timeout > 0) {
            try {
                ConfigurationMetadata configurationMetadata = dicomConfigurationManager
                        .getTypeSafeConfiguration()
                        .load( DicomConfigurationManager.METADATA_ROOT_PATH, ConfigurationMetadata.class );

                if (configurationMetadata != null && configurationMetadata.getVersion() != null &&
                        configurationMetadata.getVersion().equals(upgradeSettings.getUpgradeToVersion())) {
                    success = true;

                    /*
                     * From infinispan wiki:
                     * Infinispan does not support Snapshot isolation or Read Atomic isolation :
                     * if a transaction T1 writes K1 and K2, an overlapping transaction T2 may see both K1 and K2, only K1, only K2, or neither.
                     *
                     * => we need to avoid hitting a gap in visibility of different cache entries in infinispan,
                     * i.e. when metadata is already updated but some devices are not yet visible
                     *
                     * Reproduced with tests including cluster, observed gap 10-50ms
                     * Workaround until a better solution found, 5 sec to be on the safe side
                     */
                    Thread.sleep(5000);

                    break;
                }

                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            timeout--;
        }

        if (!success)
            throw new ConfigurationException("Waited for " + configuredTimeout + " sec, but configuration was not updated to target version ('" + upgradeSettings.getUpgradeToVersion() + "')." +
                    "Configuration will not be initialized.");
        else
            log.info("Detected the expected configuration version ('{}'), proceeding", upgradeSettings.getUpgradeToVersion());
    }
}
