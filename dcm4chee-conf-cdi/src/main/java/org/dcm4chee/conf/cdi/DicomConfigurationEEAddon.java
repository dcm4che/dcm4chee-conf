/*
 * **** BEGIN LICENSE BLOCK *****
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
 *  Portions created by the Initial Developer are Copyright (C) 2014
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

import org.dcm4che3.conf.api.DicomConfigurationBuilderAddon;
import org.dcm4che3.conf.api.internal.DicomConfigurationManager;
import org.dcm4che3.conf.api.migration.MigrationScript;
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.dicom.DicomConfigurationBuilder;
import org.dcm4che3.conf.migration.MigrationRunner;
import org.dcm4che3.net.AEExtension;
import org.dcm4che3.net.DeviceExtension;
import org.dcm4che3.net.hl7.HL7ApplicationExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Centralized handling for all Java EE - based enhancements,
 * like device/ae/hl7app extensions collected with CDI,
 * EE-based configuration storage/decorators like DB storage, Infinispan cache, etc
 *
 * @author Roman K
 */
@ApplicationScoped
public class DicomConfigurationEEAddon implements DicomConfigurationBuilderAddon {

    private static Logger log = LoggerFactory
            .getLogger(DicomConfigurationEEAddon.class);

    @Inject
    Instance<DeviceExtension> deviceExtensions;

    @Inject
    Instance<AEExtension> aeExtensions;

    @Inject
    Instance<HL7ApplicationExtension> hl7ApplicationExtensions;

    @Inject
    Instance<Configuration> customConfigStorage;

    @Inject
    Instance<MigrationScript> migrationScripts;

    @Override
    public void beforeBuild(DicomConfigurationBuilder builder) {
        for (DeviceExtension ext : deviceExtensions) builder.registerDeviceExtension(ext.getClass());
        for (AEExtension ext : aeExtensions) builder.registerAEExtension(ext.getClass());
        for (HL7ApplicationExtension ext : hl7ApplicationExtensions)
            builder.registerHL7ApplicationExtension(ext.getClass());

        if (!customConfigStorage.isUnsatisfied()) {
            builder.registerCustomConfigurationStorage(customConfigStorage.get());
        }

    }

    @Override
    public void afterBuild(DicomConfigurationManager manager) throws ConfigurationException {

        // trigger migration
        List<MigrationScript> scripts = new ArrayList<>();
        for (MigrationScript migrationScript : migrationScripts) scripts.add(migrationScript);

        new MigrationRunner(manager.getConfigurationStorage(), scripts, manager).migrate();

    }
}
