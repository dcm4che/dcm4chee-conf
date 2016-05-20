package org.dcm4chee.conf;

import org.dcm4che3.conf.api.internal.DicomConfigurationManager;
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.dicom.CommonDicomConfigurationWithHL7;
import org.dcm4chee.conf.storage.ConfigurationStorage;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.runner.RunWith;

import javax.inject.Inject;

/**
 * @author Roman K
 */
@RunWith(Arquillian.class)
public class ConfigDBStorageTests extends ConfigEETestsIT {

    @Inject
    @ConfigurationStorage("db_blobs")
    Configuration dbBlobsStorage;

    @Inject
    ConfigurableExtensionsResolver configurableExtensionsResolver;

    @Deployment(name = "ConfigDBStorageTests")
    public static WebArchive createDeployment() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, "ConfigDBStorageTests.war");
        ConfigEETestsIT.composeWar(war);
        war.addClass(ConfigDBStorageTests.class);
        return war;
    }

    @Inject
    DicomConfigManagerProducer dicomConfigManagerProducer;


    @Override
    public DicomConfigurationManager getConfig() throws ConfigurationException {
        return new CommonDicomConfigurationWithHL7(dbBlobsStorage, configurableExtensionsResolver.resolveExtensionsMap(true));
    }

    @Override
    public void testIntegrityCheck() throws ConfigurationException {
        // noop this won't work
        //super.testIntegrityCheck();
    }
}
