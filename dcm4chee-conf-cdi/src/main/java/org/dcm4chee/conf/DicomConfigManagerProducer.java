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

package org.dcm4chee.conf;

import org.dcm4che3.conf.api.internal.DicomConfigurationManager;
import org.dcm4che3.conf.core.api.BatchRunner;
import org.dcm4che3.conf.core.api.ConfigurableClassExtension;
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.normalization.DefaultsAndNullFilterDecorator;
import org.dcm4che3.conf.core.olock.HashBasedOptimisticLockingConfiguration;
import org.dcm4che3.conf.core.util.Extensions;
import org.dcm4che3.conf.dicom.CommonDicomConfigurationWithHL7;
import org.dcm4chee.conf.storage.ConfigurationEJB;
import org.dcm4chee.conf.upgrade.CdiUpgradeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.EJB;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @Roman K
 */
public class DicomConfigManagerProducer {

    private final static Logger log = LoggerFactory.getLogger(DicomConfigManagerProducer.class);

    @EJB
    private ConfigurationEJB configStorage;

    @Inject
    private Instance<ConfigurableClassExtension> allExtensions;

    @Inject
    private CdiUpgradeManager upgradeManager;


    @Produces
    @ApplicationScoped
    public DicomConfigurationManager createDicomConfigurationManager() {

        Configuration storage;

        List<Class> allExtensionClasses = resolveExtensionsList();

        // olocking
        storage = new HashBasedOptimisticLockingConfiguration(
                configStorage,
                allExtensionClasses,

                // make sure that OLocking will perform the access to the config within a single transaction (if the tx is not yet provided by the caller)
                // and use the writer cache when it will first read from storage
                // so just re-use ConfigurationEJB
                new BatchRunner() {
                    @Override
                    public void runBatch(Batch batch) {
                        configStorage.runWithRequiresTxWithLock(batch);
                    }
                });

        // defaults filtering
        storage = new DefaultsAndNullFilterDecorator(storage, allExtensionClasses);


        CommonDicomConfigurationWithHL7 configurationWithHL7 = new CommonDicomConfigurationWithHL7(
                storage,
                resolveExtensionsMap(true)
        );

        // Perform upgrade
        try {
            upgradeManager.performUpgrade(configurationWithHL7);
        } catch (Exception e) {
            throw new RuntimeException("DicomConfiguration upgrade failure", e);
        }

        return configurationWithHL7;
    }


    public List<Class> resolveExtensionsList() {
        List<Class> list = new ArrayList<>();
        for (ConfigurableClassExtension extension : allExtensions)
            if (!list.contains(extension.getClass()))
                list.add(extension.getClass());

        return list;
    }

    public Map<Class, List<Class>> resolveExtensionsMap(boolean doLog) {

        List<ConfigurableClassExtension> extList = new ArrayList<>();

        for (ConfigurableClassExtension extension : allExtensions)
            extList.add(extension);

        Map<Class, List<Class>> extByBaseExtMap = Extensions.getAMapOfExtensionsByBaseExtension(extList);


        if (doLog) {
            for (Entry<Class, List<Class>> classListEntry : extByBaseExtMap.entrySet()) {
                log.info("Extension classes of {}", classListEntry.getKey().getSimpleName());
                for (Class aClass : classListEntry.getValue()) log.info(aClass.getName());
            }
        }

        return extByBaseExtMap;
    }

}
