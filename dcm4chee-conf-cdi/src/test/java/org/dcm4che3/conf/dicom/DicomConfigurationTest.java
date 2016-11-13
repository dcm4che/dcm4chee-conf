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

package org.dcm4che3.conf.dicom;

import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che.kiwiyard.core.api.ConfigurableClass;

import org.dcm4che.kiwiyard.core.api.ConfigurableProperty;
import org.dcm4che.kiwiyard.core.api.ConfigurationException;
import org.dcm4che3.conf.dicom.configclasses.SomeDeviceExtension;
import org.dcm4che3.net.AEExtension;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * @author Roman K
 */
@RunWith(JUnit4.class)
public class DicomConfigurationTest {

    @Test
    public void renameAETest() throws ConfigurationException {

        CommonDicomConfigurationWithHL7 config = SimpleStorageTest.createCommonDicomConfiguration();


        // create device
        String aeRenameTestDevice = "AERenameTestDevice";

        Device testDevice = createDevice(aeRenameTestDevice);

        config.removeDevice(aeRenameTestDevice);
        config.persist(testDevice);

        // replace connection
        testDevice.getApplicationEntity("aet1").setAETitle("aet2");
        config.merge(testDevice);

        // see if there is only aet2
        Device deviceLoaded = config.findDevice(aeRenameTestDevice);

        Assert.assertEquals("There must stay only 1 ae", 1, deviceLoaded.getApplicationEntities().size());

        Assert.assertEquals("The new aet must have 1 connection", 1, deviceLoaded.getApplicationEntity("aet2").getConnections().size());

    }

    @Test
    public void testSearchByUUID() throws ConfigurationException {
        CommonDicomConfigurationWithHL7 config = SimpleStorageTest.createCommonDicomConfiguration();

        config.purgeConfiguration();

        Device device = new Device("ABC");
        ApplicationEntity ae1 = new ApplicationEntity("myAE1");
        ApplicationEntity ae2 = new ApplicationEntity("myAE2");


        String uuid1 = ae1.getUuid();
        String uuid2 = ae2.getUuid();

        device.addApplicationEntity(ae1);
        device.addApplicationEntity(ae2);
        config.persist(device);

        Device device2 = new Device("CDE");
        ApplicationEntity ae3 = new ApplicationEntity("myAE3");

//        String devUUID = device2.getUuid();

        String uuid3 = ae3.getUuid();

        device2.addApplicationEntity(ae3);
        config.persist(device2);

        Assert.assertEquals("myAE1", config.findApplicationEntityByUUID(uuid1).getAETitle());
        Assert.assertEquals("myAE2", config.findApplicationEntityByUUID(uuid2).getAETitle());
        Assert.assertEquals("myAE3", config.findApplicationEntityByUUID(uuid3).getAETitle());

//        Assert.assertEquals("CDE", config.findDeviceByUUID(devUUID).getDeviceName());


        try {
            config.findApplicationEntityByUUID("nonexistent");
            Assert.fail("An AE should have not been found");
        } catch (ConfigurationNotFoundException e) {
            // noop
        }
    }

    @Test
    public void testByAnyUUIDSearch() {

        CommonDicomConfigurationWithHL7 config = SimpleStorageTest.createCommonDicomConfiguration();

        config.purgeConfiguration();

        Device device = new Device("ABC3");

        String createdDeviceUUid = device.getUuid();

        ApplicationEntity ae1 = new ApplicationEntity("myAE1");
        ApplicationEntity ae2 = new ApplicationEntity("myAE2");

        String uuid1 = ae1.getUuid();
        String uuid2 = ae2.getUuid();

        device.addApplicationEntity(ae1);
        device.addApplicationEntity(ae2);
        config.persist(device);

        config.persist(new Device("ABC1"));
        config.persist(new Device("ABC2"));
        config.persist(new Device("ABC4"));

        String foundDeviceUUID1 = (String) config.getConfigurationStorage().search(DicomPath.DeviceUUIDByAnyUUID.set("UUID", uuid1).path()).next();
        String foundDeviceUUID2 = (String) config.getConfigurationStorage().search(DicomPath.DeviceUUIDByAnyUUID.set("UUID", uuid2).path()).next();

        Assert.assertEquals(createdDeviceUUid, foundDeviceUUID1);
        Assert.assertEquals(createdDeviceUUid, foundDeviceUUID2);
    }


    @ConfigurableClass
    public static class AEExtensionWithReferences extends AEExtension
    {


        @ConfigurableProperty(type = ConfigurableProperty.ConfigurablePropertyType.Reference)
        private ApplicationEntity anotherAERef;

        @ConfigurableProperty(type = ConfigurableProperty.ConfigurablePropertyType.Reference)
        private Device deviceRef;

        public AEExtensionWithReferences() {
        }

        public AEExtensionWithReferences(ApplicationEntity anotherAERef) {
            this.anotherAERef = anotherAERef;
        }

        public AEExtensionWithReferences(ApplicationEntity anotherAERef, Device deviceRef) {
            this.anotherAERef = anotherAERef;
            this.deviceRef = deviceRef;
        }

        public ApplicationEntity getAnotherAERef() {
            return anotherAERef;
        }

        public void setAnotherAERef(ApplicationEntity anotherAERef) {
            this.anotherAERef = anotherAERef;
        }

        public Device getDeviceRef() {
            return deviceRef;
        }

        public void setDeviceRef(Device deviceRef) {
            this.deviceRef = deviceRef;
        }

    }


    @Test
    public void testAEReference() throws ConfigurationException {

        CommonDicomConfigurationWithHL7 commonDicomConfiguration = SimpleStorageTest.createCommonDicomConfiguration();

        commonDicomConfiguration.purgeConfiguration();

        Device oneDeviceWithAE = new Device("oneDeviceWithAE");
        ApplicationEntity myAE = new ApplicationEntity("myAE");
        oneDeviceWithAE.addApplicationEntity(myAE);

        SomeDeviceExtension someDeviceExtension = new SomeDeviceExtension();
        someDeviceExtension.setReferencedEntity(myAE);
        oneDeviceWithAE.addDeviceExtension(someDeviceExtension);

        Device anotherDeviceWithRef = new Device("anotherDeviceWithRef");
        SomeDeviceExtension ext = new SomeDeviceExtension();
        ext.setReferencedEntity(myAE);
        anotherDeviceWithRef.addDeviceExtension(ext);


        commonDicomConfiguration.persist(anotherDeviceWithRef);
        commonDicomConfiguration.persist(oneDeviceWithAE);

        Device loaded = commonDicomConfiguration.findDevice("anotherDeviceWithRef");

        Device loadedWithSelfRef = commonDicomConfiguration.findDevice("oneDeviceWithAE");

        ApplicationEntity referencedEntity = loaded.getDeviceExtension(SomeDeviceExtension.class).getReferencedEntity();
        Assert.assertEquals(referencedEntity.getAETitle(), "myAE");

        ApplicationEntity referencedEntity1 = loadedWithSelfRef.getDeviceExtension(SomeDeviceExtension.class).getReferencedEntity();
        Assert.assertEquals(referencedEntity1.getAETitle(), "myAE");


    }





    @Test
    public void testAECrossRef() {
        CommonDicomConfigurationWithHL7 config = prepareTestConfigWithRefs();

        Device theCoreDevice = new Device("TheCoreDevice");
        ApplicationEntity ae1 = new ApplicationEntity("theFirstAE");
        ApplicationEntity ae2 = new ApplicationEntity("theSecondAE");

        ae1.addAEExtension(new AEExtensionWithReferences(ae2));
        ae2.addAEExtension(new AEExtensionWithReferences(ae1));

        theCoreDevice.addApplicationEntity(ae1);
        theCoreDevice.addApplicationEntity(ae2);

        config.persist(theCoreDevice);

        Device loadedDevice = config.findDevice("TheCoreDevice");

        Assert.assertEquals(
                "TheCoreDevice",
                loadedDevice
                        .getApplicationEntity("theFirstAE")
                        .getAEExtension(AEExtensionWithReferences.class)
                        .getAnotherAERef()
                        .getDevice()
                        .getDeviceName()
        );
        Assert.assertEquals(
                "TheCoreDevice",
                loadedDevice
                        .getApplicationEntity("theSecondAE")
                        .getAEExtension(AEExtensionWithReferences.class)
                        .getAnotherAERef()
                        .getDevice()
                        .getDeviceName()
        );
    }

    private CommonDicomConfigurationWithHL7 prepareTestConfigWithRefs() {



        Map<Class, List<Class>> map = new HashMap<>();
        map.put( AEExtension.class, Arrays.asList( AEExtensionWithReferences.class ) );
        CommonDicomConfigurationWithHL7 config = SimpleStorageTest.createCommonDicomConfiguration(map);

        config.purgeConfiguration();
        return config;
    }

    @Test
    public void testAEselfRef() {
        CommonDicomConfigurationWithHL7 config = prepareTestConfigWithRefs();

        Device theCoreDevice = new Device("TheCoreDevice");
        ApplicationEntity ae1 = new ApplicationEntity("theFirstAE");
        ae1.addAEExtension(new AEExtensionWithReferences(ae1));
        theCoreDevice.addApplicationEntity(ae1);
        config.persist(theCoreDevice);

        Device loadedDevice = config.findDevice("TheCoreDevice");

        Assert.assertEquals(
                "TheCoreDevice",
                loadedDevice
                        .getApplicationEntity("theFirstAE")
                        .getAEExtension(AEExtensionWithReferences.class)
                        .getAnotherAERef()
                        .getDevice()
                        .getDeviceName()
        );
    }

    @Test
    public void testLongRefChain() {
        CommonDicomConfigurationWithHL7 config = prepareTestConfigWithRefs();

        Device theCoreDevice = new Device("TheCoreDevice");
        Device theSecondDevice = new Device("TheSecondDevice");
        ApplicationEntity ae5 = new ApplicationEntity("theThirdAE");
        theSecondDevice.setDefaultAE(ae5);
        theSecondDevice.addApplicationEntity(ae5);
        config.persist(theSecondDevice);

        ApplicationEntity ae1 = new ApplicationEntity("theFirstAE");
        ApplicationEntity ae2 = new ApplicationEntity("theSecondAE");
        ApplicationEntity ae3 = new ApplicationEntity("theThirdAE");

        ae1.addAEExtension(new AEExtensionWithReferences(ae2));
        ae2.addAEExtension(new AEExtensionWithReferences(ae3));
        ae3.addAEExtension(new AEExtensionWithReferences(ae1,theSecondDevice));


        theCoreDevice.addApplicationEntity(ae1);
        theCoreDevice.addApplicationEntity(ae2);
        theCoreDevice.addApplicationEntity(ae3);
        config.persist(theCoreDevice);

        Device loadedDevice = config.findDevice("TheCoreDevice");

        Assert.assertEquals(
                "TheCoreDevice",
                loadedDevice
                        .getApplicationEntity("theFirstAE")
                        .getAEExtension(AEExtensionWithReferences.class)
                        .getAnotherAERef()
                        .getAEExtension(AEExtensionWithReferences.class)
                        .getAnotherAERef()
                        .getAEExtension(AEExtensionWithReferences.class)
                        .getAnotherAERef()
                        .getDevice()
                        .getDeviceName()

        );

        Assert.assertEquals(
                "TheSecondDevice",
                loadedDevice
                        .getApplicationEntity("theFirstAE")
                        .getAEExtension(AEExtensionWithReferences.class)
                        .getAnotherAERef()
                        .getAEExtension(AEExtensionWithReferences.class)
                        .getAnotherAERef()
                        .getAEExtension(AEExtensionWithReferences.class)
                        .getDeviceRef()
                        .getDefaultAE()
                        .getDevice()
                        .getDeviceName()
        );
    }


    private Device createDevice(String aeRenameTestDevice) {
        Device testDevice = new Device(aeRenameTestDevice);
        Connection connection = new Connection();
        connection.setProtocol(Connection.Protocol.DICOM);
        connection.setCommonName("myConn");
        connection.setHostname("localhost");

        ApplicationEntity ae = new ApplicationEntity();
        List<Connection> list = new ArrayList<Connection>();
        list.add(connection);

        testDevice.addConnection(connection);
        ae.setConnections(list);
        ae.setAETitle("aet1");
        testDevice.addApplicationEntity(ae);
        return testDevice;
    }
}
