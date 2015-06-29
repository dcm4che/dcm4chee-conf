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

import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.dicom.CommonDicomConfigurationWithHL7;
import org.dcm4che3.conf.dicom.DicomConfigurationBuilder;
import org.dcm4che3.net.Device;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.EJB;
import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roman K
 */
@RunWith(Arquillian.class)
public class ConfigEETestsIT {

    private static Logger LOG = LoggerFactory
            .getLogger(ConfigEETestsIT.class);


    @Inject
    Configuration dbConfigStorage;

    @EJB
    MyConfyEJB myConfyEJB;

    @Deployment
    public static WebArchive createDeployment() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, "test.war");

        war.addClass(ConfigEETestsIT.class);
        war.addClass(MyConfyEJB.class);

        war.addAsManifestResource(new FileAsset(new File("src/test/resources/META-INF/MANIFEST.MF")), "MANIFEST.MF");
        JavaArchive[] archs = Maven.resolver()
                .loadPomFromFile("testpom.xml")
                .importRuntimeAndTestDependencies()
                .resolve().withoutTransitivity()
                .as(JavaArchive.class);

        for (JavaArchive a : archs) {
            war.addAsLibrary(a);
        }

        war.as(ZipExporter.class).exportTo(
                new File("test.war"), true);
        return war;
    }

    private CommonDicomConfigurationWithHL7 getConfig() throws ConfigurationException {
        DicomConfigurationBuilder builder = new DicomConfigurationBuilder();

        builder.registerCustomConfigurationStorage(dbConfigStorage);
        builder.cache(false);

//        builder.registerDeviceExtension(ArchiveDeviceExtension.class);
//        builder.registerDeviceExtension(StorageDeviceExtension.class);
//        builder.registerDeviceExtension(HL7DeviceExtension.class);
//        builder.registerDeviceExtension(ImageReaderExtension.class);
//        builder.registerDeviceExtension(ImageWriterExtension.class);
//        builder.registerDeviceExtension(AuditRecordRepository.class);
//        builder.registerDeviceExtension(AuditLogger.class);
//        builder.registerAEExtension(ArchiveAEExtension.class);
//        builder.registerHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);


        return builder.build();
    }


    @Test
    public void rollbackTest() throws Exception {

        final CommonDicomConfigurationWithHL7 config = getConfig();
        final Configuration storage = config.getConfigurationStorage();


        storage.removeNode("/");

        myConfyEJB.execInTransaction(new Runnable() {
            @Override
            public void run() {

                try {
                    config.persist(new Device("shouldWork"));
                } catch (ConfigurationException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        Assert.assertNotNull(config.findDevice("shouldWork"));

        try {
            myConfyEJB.execInTransaction(new Runnable() {
                @Override
                public void run() {

                    try {
                        config.persist(new Device("shouldBeRolledBack"));
                        config.findDevice("shouldBeRolledBack");
                    } catch (ConfigurationException e) {
                        throw new RuntimeException(e);
                    }

                    throw new RuntimeException("Let's roll (back)!");
                }
            });
        } catch (Exception e) {
            // it's fine
            Assert.assertEquals(e.getCause().getMessage(), "Let's roll (back)!");
        }

        try {
            config.findDevice("shouldBeRolledBack");
            Assert.fail("device shouldBeRolledBack must not be there");
        } catch (ConfigurationNotFoundException e) {
            //it ok
        }

    }


    @Test
    public void lockTest() throws Exception {

        final CommonDicomConfigurationWithHL7 config = getConfig();
        final Configuration storage = config.getConfigurationStorage();

        storage.removeNode("/dicomConfigurationRoot");

        final Semaphore masterSemaphore = new Semaphore(0);
        final Semaphore childSemaphore = new Semaphore(0);

        final AtomicInteger parallel = new AtomicInteger(0);

        Thread thread1 = new Thread() {
            @Override
            public void run() {
                myConfyEJB.execInTransaction(new Runnable() {
                    @Override
                    public void run() {

                        LOG.info("locking...");
                        storage.lock();
                        LOG.info("locked!");
                        parallel.addAndGet(1);


                        try {
                            config.persist(new Device("someDevice1"));
                        } catch (ConfigurationException e) {
                            throw new RuntimeException(e);
                        }

                        LOG.info("double-locking...");
                        storage.lock();

                        try {
                            masterSemaphore.acquire();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                LOG.info("Committed");
                super.run();
            }
        };
        Thread thread2 = new Thread() {
            @Override
            public void run() {

                myConfyEJB.execInTransaction(new Runnable() {
                    @Override
                    public void run() {

                        LOG.info("locking...");
                        storage.lock();
                        LOG.info("locked!");
                        parallel.addAndGet(1);

                        try {
                            config.persist(new Device("someDevice2"));
                        } catch (ConfigurationException e) {
                            throw new RuntimeException(e);
                        }

                        try {
                            masterSemaphore.acquire();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }

                    }
                });
                LOG.info("Committed");
                super.run();
            }
        };

        thread1.start();
        thread2.start();

        Thread.sleep(500);

        // make sure
        Assert.assertEquals(1, parallel.get());

        masterSemaphore.release();

        Thread.sleep(500);

        // make sure
        Assert.assertEquals(2, parallel.get());

        masterSemaphore.release();

        Thread.sleep(500);


    }

}
