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

import org.dcm4che3.conf.api.internal.DicomConfigurationManager;
import org.dcm4che3.conf.api.upgrade.UpgradeScript;
import org.dcm4che3.conf.core.DefaultBeanVitalizer;
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.normalization.DefaultsAndNullFilterDecorator;
import org.dcm4che3.conf.core.storage.SingleJsonFileConfigurationStorage;
import org.dcm4che3.conf.dicom.CommonDicomConfiguration;
import org.dcm4che3.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads configuration from a json file, configures and launches the upgrade runner
 */
@ApplicationScoped
public class CdiUpgradeManager {

    private static Logger log = LoggerFactory.getLogger(CdiUpgradeManager.class);

    private static final String UPGRADE_SETTINGS_PROP = "org.dcm4che.conf.upgrade.settingsFile";

    @Inject
    private Instance<UpgradeScript> upgradeScripts;

    @Resource(lookup="java:app/AppName")
    private String appName;

    public CdiUpgradeManager() {
    }

    public void performUpgrade(DicomConfigurationManager manager) throws ConfigurationException {

        // collect available upgrade scripts
        List<UpgradeScript> scripts = new ArrayList<>();
        for (UpgradeScript upgradeScript : upgradeScripts) scripts.add(upgradeScript);

        // perform upgrade
        String upgradeSettingsFileName = System.getProperty(UPGRADE_SETTINGS_PROP);
        if (upgradeSettingsFileName != null) {

            UpgradeSettings upgradeSettings = loadUpgradeSettings( upgradeSettingsFileName );

            if (appName == null) throw new RuntimeException("Cannot detect deployment name");

            String toVersion = upgradeSettings.getUpgradeToVersion();

            if (toVersion == null) {
                log.warn("Dcm4che configuration init: target upgrade version is null. Upgrade will not be performed. Set the target config version in upgrade settings first.'");
                return;
            }

            if (upgradeSettings.getActiveUpgradeRunnerDeployment() == null) {
                // if ActiveUpgradeRunnerDeployment not set, just run the upgrade no matter in which deployment we are
                UpgradeRunner upgradeRunner = new UpgradeRunner(scripts, manager, upgradeSettings );
                upgradeRunner.upgrade();

            } else {
                if (appName.startsWith(upgradeSettings.getActiveUpgradeRunnerDeployment())) {
                    // if deployment name matches - go ahead with the upgrade
                    UpgradeRunner upgradeRunner = new UpgradeRunner(scripts, manager, upgradeSettings );
                    upgradeRunner.upgrade();
                } else {
                    PassiveUpgrade passiveUpgrade = new PassiveUpgrade( manager, upgradeSettings, appName );
                    passiveUpgrade.waitUntilOtherRunnerUpdatesToTargetConfigurationVersion();
                }
            }
        } else {
            log.info("Dcm4che configuration init: {} property not set, no config upgrade will be performed", UPGRADE_SETTINGS_PROP);
        }
    }

    private UpgradeSettings loadUpgradeSettings( String property )
    {
        String filename = StringUtils.replaceSystemProperties(property);
        Configuration singleJsonFileConfigurationStorage =
                new DefaultsAndNullFilterDecorator(new SingleJsonFileConfigurationStorage(filename), new ArrayList<Class>(), CommonDicomConfiguration.createDefaultDicomVitalizer());
        Map<String, Object> configMap = singleJsonFileConfigurationStorage.getConfigurationRoot();
        UpgradeSettings upgradeSettings = new DefaultBeanVitalizer().newConfiguredInstance(configMap, UpgradeSettings.class);
        upgradeSettings.setUpgradeConfig(configMap);
        return upgradeSettings;
    }

}
