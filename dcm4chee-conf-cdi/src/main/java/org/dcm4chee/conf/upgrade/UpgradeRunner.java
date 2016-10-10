/*
 * *** BEGIN LICENSE BLOCK *****
 *  Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  Agfa Healthcare.
 *  Portions created by the Initial Developer are Copyright (C) 2015
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 *  ***** END LICENSE BLOCK *****
 */

package org.dcm4chee.conf.upgrade;


import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.conf.api.internal.DicomConfigurationManager;
import org.dcm4che3.conf.api.upgrade.ConfigurationMetadata;
import org.dcm4che3.conf.api.upgrade.ScriptVersion;
import org.dcm4che3.conf.api.upgrade.UpgradeScript;
import org.dcm4che3.conf.core.DefaultBeanVitalizer;
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.api.ConfigurationUpgradeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Properties;

/**
 * @author Roman K
 */
@SuppressWarnings("unchecked")
public class UpgradeRunner {

    public static final String PASSIVE_UPGRADE_TIMEOUT = "org.dcm4che.conf.upgrade.passiveTimeoutSec";

    private static Logger log = LoggerFactory
            .getLogger(UpgradeRunner.class);

    private Collection<UpgradeScript> availableUpgradeScripts;
    private DicomConfigurationManager dicomConfigurationManager;
    private UpgradeSettings upgradeSettings;
    private String appName;

    public UpgradeRunner() {
    }

    public UpgradeRunner(Collection<UpgradeScript> availableUpgradeScripts, DicomConfigurationManager dicomConfigurationManager, UpgradeSettings upgradeSettings, String appName) {
        this.availableUpgradeScripts = availableUpgradeScripts;
        this.dicomConfigurationManager = dicomConfigurationManager;
        this.upgradeSettings = upgradeSettings;
        this.appName = appName;
    }

    public void upgrade() {
        if (upgradeSettings == null) {
            log.info("Dcm4che configuration init: upgrade is not configured, no upgrade will be performed");
            return;
        }

        String toVersion = upgradeSettings.getUpgradeToVersion();

        if (toVersion == null) {
            log.warn("Dcm4che configuration init: target upgrade version is null. Upgrade will not be performed. Set the target config version in upgrade settings first.'");
            return;
        }

        if (upgradeSettings.getActiveUpgradeRunnerDeployment() == null) {
            // if ActiveUpgradeRunnerDeployment not set, just run the upgrade no matter in which deployment we are
            upgradeToVersion(toVersion);
        } else {
            if (appName.startsWith(upgradeSettings.getActiveUpgradeRunnerDeployment())) {
                // if deployment name matches - go ahead with the upgrade
                upgradeToVersion(toVersion);
            } else {
                waitUntilOtherRunnerUpdatesToTargetConfigurationVersion();
            }
        }
    }

    private ConfigurationMetadata loadConfigurationMetadata() {
        Object metadataNode = dicomConfigurationManager
                .getConfigurationStorage()
                .getConfigurationNode(DicomConfigurationManager.METADATA_ROOT_PATH, ConfigurationMetadata.class);
        ConfigurationMetadata configurationMetadata = null;
        if (metadataNode != null)
            configurationMetadata = new DefaultBeanVitalizer().newConfiguredInstance((Map<String, Object>) metadataNode, ConfigurationMetadata.class);
        return configurationMetadata;
    }

    private void persistConfigurationMetadata(ConfigurationMetadata metadata) {
        dicomConfigurationManager
                .getConfigurationStorage()
                .persistNode(
                        DicomConfigurationManager.METADATA_ROOT_PATH,
                        new DefaultBeanVitalizer().createConfigNodeFromInstance(metadata),
                        ConfigurationMetadata.class
                );
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
                ConfigurationMetadata configurationMetadata = loadConfigurationMetadata();

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

    protected void upgradeToVersion(final String toVersion) {
        dicomConfigurationManager.runBatch(() -> {
            try {

                Configuration configuration = dicomConfigurationManager.getConfigurationStorage();

                // load or initialize config metadata
                ConfigurationMetadata configMetadata = loadConfigurationMetadata();
                if (configMetadata == null) {
                    configMetadata = new ConfigurationMetadata();
                }

                if (configMetadata.getVersion()==null)
                    configMetadata.setVersion(UpgradeScript.NO_VERSION);

                String fromVersion = configMetadata.getVersion();

                log.info("Dcm4che configuration init: upgrading configuration from version '{}' to version '{}'", fromVersion, toVersion);

                Properties props = new Properties();
                props.putAll(upgradeSettings.getProperties());

                log.info("Config upgrade scripts specified in settings: {}", upgradeSettings.getUpgradeScriptsToRun());
                log.info("Config upgrade scripts discovered in the deployment: {}", availableUpgradeScripts);

                // run all scripts
                for (String upgradeScriptName : upgradeSettings.getUpgradeScriptsToRun()) {

                    boolean found = false;

                    for (UpgradeScript script : availableUpgradeScripts) {
                        if (script.getClass().getName().startsWith(upgradeScriptName)) {
                            found = true;

                            // fetch upgradescript metadata
                            UpgradeScript.UpgradeScriptMetadata upgradeScriptMetadata = configMetadata.getMetadataOfUpgradeScripts().get(upgradeScriptName);
                            if (upgradeScriptMetadata == null) {
                                upgradeScriptMetadata = new UpgradeScript.UpgradeScriptMetadata();
                                configMetadata.getMetadataOfUpgradeScripts().put(upgradeScriptName, upgradeScriptMetadata);
                            }


                            // check if the script need to be executed
                            ScriptVersion currentScriptVersionAnno = script.getClass().getAnnotation(ScriptVersion.class);

                            String currentScriptVersion;
                            if (currentScriptVersionAnno == null) {
                                currentScriptVersion = UpgradeScript.NO_VERSION;
                                log.warn("Upgrade script '{}' does not have @ScriptVersion defined - using default '{}'",
                                        script.getClass().getName(),
                                        currentScriptVersion);
                            } else {
                                currentScriptVersion = currentScriptVersionAnno.value();
                            }

                            if (upgradeScriptMetadata.getLastVersionExecuted() != null
                                    && upgradeScriptMetadata.getLastVersionExecuted().compareTo(currentScriptVersion) >= 0) {
                                log.info("Upgrade script '{}' is skipped because current version '{}' is not newer than the last executed one ('{}')",
                                        script.getClass().getName(),
                                        currentScriptVersion,
                                        upgradeScriptMetadata.getLastVersionExecuted());
                                continue;
                            }

                            log.info("Executing upgrade script '{}' (this version '{}', last executed version '{}')",
                                    script.getClass().getName(),
                                    currentScriptVersion,
                                    upgradeScriptMetadata.getLastVersionExecuted());


                            // collect pieces and prepare context
                            Map<String, Object> scriptConfig = (Map<String, Object>) upgradeSettings.getUpgradeConfig().get(upgradeScriptName);
                            UpgradeScript.UpgradeContext upgradeContext = new UpgradeScript.UpgradeContext(
                                    fromVersion, toVersion, props, scriptConfig, configuration, dicomConfigurationManager, upgradeScriptMetadata, configMetadata);

                            try {
                                script.upgrade(upgradeContext);
                            } catch (Exception e) {
                                throw new ConfigurationUpgradeException("Error while running upgrade script '" + script.getClass().getName() + "', version '" + currentScriptVersion + "'", e);
                            }

                            // set last executed version from the annotation of the upgrade script if present
                            upgradeScriptMetadata.setLastVersionExecuted(currentScriptVersion);

                        }
                    }

                    if (!found) {
                        if (upgradeSettings.isIgnoreMissingUpgradeScripts()) {
                            log.warn("Missing update script '" + upgradeScriptName + "' ignored! DISABLE 'IgnoreMissingUpgradeScripts' SETTING FOR PRODUCTION ENVIRONMENT.");
                        } else {
                            throw new ConfigurationUpgradeException("Upgrade script '" + upgradeScriptName + "' not found in the deployment");
                        }
                    }
                }

                // update version
                configMetadata.setVersion(toVersion);

                // persist updated metadata
                persistConfigurationMetadata(configMetadata);
            } catch (Exception e) {
                throw new ConfigurationUpgradeException("Error while running the configuration upgrade", e);
            }
        });

        log.info("Configuration upgrade completed successfully");
    }


}

