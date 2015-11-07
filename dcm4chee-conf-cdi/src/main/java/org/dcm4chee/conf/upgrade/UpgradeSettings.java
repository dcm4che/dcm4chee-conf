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

import org.dcm4che3.conf.core.api.ConfigurableClass;
import org.dcm4che3.conf.core.api.ConfigurableProperty;

import java.util.List;
import java.util.Map;

/**
 * @author Roman K
 */
@ConfigurableClass
public class UpgradeSettings {

    public UpgradeSettings() {
    }

    @ConfigurableProperty
    String upgradeToVersion;

    @ConfigurableProperty(
            description = "List of classes that implement UpgradeScript. Defines which scripts should be run and in which sequence")
    List<String> upgradeScriptsToRun;

    @ConfigurableProperty(
            description = "The exact name or prefix (i.e. startsWith logic) of the deployment which will run the upgrade. " +
                    "Other deployments will just wait until the version of configuration becomes equal to what is specified by #upgradeToVersion")
    String activeUpgradeRunnerDeployment;

    @ConfigurableProperty(
            description = "These key/value properties are available to upgrade scripts"
    )
    Map<String, String> properties;

    public String getActiveUpgradeRunnerDeployment() {
        return activeUpgradeRunnerDeployment;
    }

    public void setActiveUpgradeRunnerDeployment(String activeUpgradeRunnerDeployment) {
        this.activeUpgradeRunnerDeployment = activeUpgradeRunnerDeployment;
    }

    private Map<String,Object> upgradeConfig;

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }
    
    public Map<String, Object> getUpgradeConfig() {
        return upgradeConfig;
    }

    public void setUpgradeConfig(Map<String, Object> upgradeConfig) {
        this.upgradeConfig = upgradeConfig;
    }

    public String getUpgradeToVersion() {
        return upgradeToVersion;
    }

    public void setUpgradeToVersion(String upgradeToVersion) {
        this.upgradeToVersion = upgradeToVersion;
    }

    public List<String> getUpgradeScriptsToRun() {
        return upgradeScriptsToRun;
    }

    public void setUpgradeScriptsToRun(List<String> upgradeScriptsToRun) {
        this.upgradeScriptsToRun = upgradeScriptsToRun;
    }
}
