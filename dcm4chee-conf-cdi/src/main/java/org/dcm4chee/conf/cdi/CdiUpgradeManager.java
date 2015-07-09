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

package org.dcm4chee.conf.cdi;

import org.dcm4che3.conf.api.internal.DicomConfigurationManager;
import org.dcm4che3.conf.api.upgrade.UpgradeScript;
import org.dcm4che3.conf.core.DefaultBeanVitalizer;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.storage.SingleJsonFileConfigurationStorage;
import org.dcm4che3.conf.upgrade.UpgradeRunner;
import org.dcm4che3.conf.upgrade.UpgradeSettings;
import org.dcm4che3.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Matches configuration from a json file with upgrade scripts available as CDI beans and launches the upgrade runner
 */
@ApplicationScoped
public class CdiUpgradeManager {

    private static Logger LOG = LoggerFactory.getLogger(CdiUpgradeManager.class);

    public static final String UPGRADE_SETTINGS_PROP = "org.dcm4che.conf.upgrade.settingsFile";

    @Inject
    private Instance<UpgradeScript> upgradeScripts;

    public CdiUpgradeManager() {
    }

    public void performUpgrade(DicomConfigurationManager manager) throws ConfigurationException {
        // collect available upgrade scripts
        List<UpgradeScript> scripts = new ArrayList<>();
        for (UpgradeScript upgradeScript : upgradeScripts) scripts.add(upgradeScript);

        // perform upgrade
        String property = System.getProperty(UPGRADE_SETTINGS_PROP);
        if (property != null) {
            // load upgrade settings
            String filename = StringUtils.replaceSystemProperties(property);
            SingleJsonFileConfigurationStorage singleJsonFileConfigurationStorage = new SingleJsonFileConfigurationStorage(filename);
            UpgradeSettings upgradeSettings = new DefaultBeanVitalizer().newConfiguredInstance(singleJsonFileConfigurationStorage.getConfigurationRoot(), UpgradeSettings.class);

            UpgradeRunner upgradeRunner = new UpgradeRunner(scripts, manager, upgradeSettings);
            upgradeRunner.upgrade();

        } else {
            LOG.info("Dcm4che configuration init: {} property not set, no config upgrade will be performed", UPGRADE_SETTINGS_PROP);
        }
    }
}
