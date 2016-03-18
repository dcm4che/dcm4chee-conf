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

package org.dcm4chee.conf.storage;

import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.normalization.DefaultsAndNullFilterDecorator;
import org.dcm4che3.conf.core.storage.InMemoryReadOnlyConfiguration;
import org.dcm4che3.conf.dicom.CommonDicomConfiguration;
import org.dcm4che3.conf.dicom.CommonDicomConfigurationWithHL7;
import org.dcm4chee.conf.DicomConfigManagerProducer;

import javax.inject.Inject;
import java.util.Map;

/**
 * @author Roman K
 */
public class ConfigurationIntegrityCheck {

    private static final String DISABLE_INTEGRITY_CHECK_PROPERTY = "org.dcm4che.conf.disableIntegrityCheck";

    // temporarily, to re-use extension resolvers
    @Inject
    DicomConfigManagerProducer dicomConfigManagerProducer;


    public void performCheck(Map<String, Object> configurationRoot) throws ConfigurationException {

        // check if the check is not disabled
        if (System.getProperty(DISABLE_INTEGRITY_CHECK_PROPERTY) != null)
            return;

        // temporarily just 'materialize' full config
        // TODO later will be replaced with proper referential consistency analysis

        Configuration storage = new InMemoryReadOnlyConfiguration(configurationRoot);

        // TODO: maybe we should re-use existing infinispan index not to stress the heap too much
//        storage = new IndexingDecoratorWithInit(storage, configurationRoot);
        storage = new DefaultsAndNullFilterDecorator(storage, dicomConfigManagerProducer.resolveExtensionsList(), CommonDicomConfiguration.createDefaultDicomVitalizer());

        CommonDicomConfigurationWithHL7 dicomConfiguration = new CommonDicomConfigurationWithHL7(
                storage,
                dicomConfigManagerProducer.resolveExtensionsMap(false)
        );

        for (String deviceName : dicomConfiguration.listDeviceNames())
            dicomConfiguration.findDevice(deviceName);

    }

//    private static class IndexingDecoratorWithInit extends DicomReferenceIndexingDecorator {
//        public IndexingDecoratorWithInit(Configuration storage, Map<String, Object> configurationRootToIndex) {
//            super(storage, new HashMap<>());
//            List<DuplicateUUIDException> duplicateUUIDExceptions = addReferablesToIndex(new ArrayList<>(), configurationRootToIndex);
//
//            if (!duplicateUUIDExceptions.isEmpty())
//                throw duplicateUUIDExceptions.get(0);
//        }
//
//    }
}
