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
import org.dcm4che3.conf.core.api.BatchRunner.Batch;
import org.dcm4che3.conf.core.api.ConfigurableClass;
import org.dcm4che3.conf.core.api.ConfigurableClassExtension;
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.normalization.DefaultsAndNullFilterDecorator;
import org.dcm4che3.conf.core.olock.HashBasedOptimisticLockingConfiguration;
import org.dcm4che3.conf.core.util.Extensions;
import org.dcm4che3.conf.dicom.CommonDicomConfiguration;
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
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * @Roman K
 */
public class DicomConfigManagerProducer {

    private final static Logger log = LoggerFactory.getLogger(DicomConfigManagerProducer.class);

    private static final String DISABLE_OLOCK_PROP = "org.dcm4che.conf.olock.disabled";

    @EJB
    private ConfigurationEJB providedConfigStorage;

    @Inject
    private Instance<ConfigurableClassExtension> allExtensions;

    @Inject
    private Instance<CdiUpgradeManager> upgradeManagerInstance;


    // temp workaround just for closure
    private CommonDicomConfigurationWithHL7 configurationWithHL7;


    @Produces
    @ApplicationScoped
    public DicomConfigurationManager createDicomConfigurationManager() {

        log.info("Constructing DicomConfiguration ...");

        List<Class> allExtensionClasses = resolveExtensionsList();

        Configuration storage = providedConfigStorage;

        // olocking
        if (System.getProperty(DISABLE_OLOCK_PROP) == null) {
            storage = new HashBasedOptimisticLockingConfiguration(
                    providedConfigStorage,
                    allExtensionClasses,

                    // make sure that OLocking will perform the access to the config within a single transaction (if the tx is not yet provided by the caller)
                    // and use the writer cache when it will first read from storage
                    // so just re-use ConfigurationEJB
                    new BatchRunner() {
                        @Override
                        public void runBatch(Batch batch) {
                            providedConfigStorage.runWithRequiresTxWithLock(batch);
                        }
                    });
        }

        // defaults filtering
        storage = new DefaultsAndNullFilterDecorator(storage, allExtensionClasses, CommonDicomConfiguration.createDefaultDicomVitalizer());


        final Configuration finalStorage = storage;

        // Quick fix, need to refactor later
        // otherwise integrity check is performed before the upgrade
        if (storage.nodeExists("/dicomConfigurationRoot")) {
            configurationWithHL7 = new CommonDicomConfigurationWithHL7(
                    finalStorage,
                    resolveExtensionsMap(true)
            );
        } else
            // run in a batch to ensure we don't lock the ongoing transaction by accident if we init the config
            providedConfigStorage.runBatch(new Batch() {
                @Override
                public void run() {
                    DicomConfigManagerProducer.this.configurationWithHL7 = new CommonDicomConfigurationWithHL7(
                            finalStorage,
                            resolveExtensionsMap(true)
                    );
                }
            });

        if (upgradeManagerInstance.isUnsatisfied()) {
            log.info("Dicom configuration upgrade is not configured for this deployment, skipping");
        } else
            // Perform upgrade
            try {
                upgradeManagerInstance.get().performUpgrade(configurationWithHL7);
            } catch (Exception e) {
                throw new RuntimeException("DicomConfiguration upgrade failure", e);
            }

        log.info("DicomConfiguration created.");

        return configurationWithHL7;
    }


    public List<Class> resolveExtensionsList() {
        List<Class> list = new ArrayList<>();
        for (ConfigurableClassExtension extension : getAllConfigurableExtensions())
            if (!list.contains(extension.getClass()))
                list.add(extension.getClass());

        return list;
    }

    public Map<Class, List<Class>> resolveExtensionsMap(boolean doLog) {

        List<ConfigurableClassExtension> extList = new ArrayList<>();

        for (ConfigurableClassExtension extension : getAllConfigurableExtensions())
            extList.add(extension);

        Map<Class, List<Class>> extByBaseExtMap = Extensions.getAMapOfExtensionsByBaseExtension(extList);


        if (doLog) {

            String extensionsLog = "";
            for (Entry<Class, List<Class>> classListEntry : extByBaseExtMap.entrySet()) {
                extensionsLog += "\nExtension classes of " + classListEntry.getKey().getSimpleName() + ":\n";

                for (Class aClass : classListEntry.getValue())
                    extensionsLog += aClass.getName() + "\n";
            }

            extensionsLog += "\n";

            log.info(extensionsLog);
        }

        return extByBaseExtMap;
    }

    /**
     * @return all extension classes that have a ConfigurableClass annotation
     */
    private List<ConfigurableClassExtension> getAllConfigurableExtensions() {
        List<ConfigurableClassExtension> configurableExtensions = new ArrayList<>();
        for (ConfigurableClassExtension extension : allExtensions) {
            if (extension.getClass().getAnnotation(ConfigurableClass.class) != null)
                configurableExtensions.add(extension);
        }

        // make sure simple class names are unique
        HashSet<String> simpleNames = new HashSet<>();
        HashSet<String> fullNames = new HashSet<>();
        for (ConfigurableClassExtension configurableExtension : configurableExtensions) {

            String simpleName = configurableExtension.getClass().getSimpleName();
            String fullName = configurableExtension.getClass().getName();
            boolean simpleNameExisted = !simpleNames.add(simpleName);
            boolean fullNameExisted = !fullNames.add(fullName);


            if (simpleNameExisted && !fullNameExisted) {
                // we have a duplicate, let's find out all occurrences
                List<ConfigurableClassExtension> conflicting = configurableExtensions.stream()
                        .filter((ext) -> ext.getClass().getSimpleName().equals(simpleName))
                        .collect(Collectors.toList());

                throw new IllegalArgumentException("Simple class names of configurable extensions MUST be unique. This rule was violated by classes:\n" + conflicting);

            } else if (simpleNameExisted && fullNameExisted) {
                // if fullname existed as well, it means we have a class with the same name
                // could happen when deploying duplicate libs, etc, so not as critical, just print a warning

                log.warn("Found duplicate configurable extension class: "+ fullName);
            }
        }

        return configurableExtensions;
    }

}
