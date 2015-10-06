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

package org.dcm4chee.conf.cdi;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.dcm4che3.conf.core.api.ConfigurableClassExtension;
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.dicom.CommonDicomConfigurationWithHL7;
import org.dcm4che3.conf.dicom.DicomConfigurationBuilder;
import org.dcm4chee.conf.storage.ConfigurationEJB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Alexander Hoermandinger <alexander.hoermandinger@agfa.com>
 */
public class CdiDicomConfigurationBuilder extends DicomConfigurationBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(CdiDicomConfigurationBuilder.class);
    

    @Inject
    private CdiUpgradeManager upgradeManager;

    public CdiDicomConfigurationBuilder() {
        super(System.getProperties());
    }


    @Inject
    ConfigurationEJB configurationEJB;

    @Inject
    private Instance<ConfigurableClassExtension> allExtensions;

    @Override
    protected Configuration createConfigurationStorage(List<Class> allExtensions) throws ConfigurationException {
        Configuration storage = super.createConfigurationStorage(allExtensions);

        // strap caching on


        return storage;
    }

    @Override
    public CommonDicomConfigurationWithHL7 build() throws ConfigurationException {

        registerCustomConfigurationStorage(configurationEJB);

        // Add cdi-based config extensions
        registerExtensions();

        // Build the config
        CommonDicomConfigurationWithHL7 commonConfig = super.build();

        // Perform upgrade
        upgradeManager.performUpgrade(commonConfig);

        return commonConfig;
    }

    private void registerExtensions() {
        for (ConfigurableClassExtension extension : allExtensions) {
            LOG.info("Registering {} : {}", extension.getBaseClass().getSimpleName(), extension.getClass().getName());
            registerExtensionForBaseExtension(extension.getBaseClass(), extension.getClass());
        }
    }


}
