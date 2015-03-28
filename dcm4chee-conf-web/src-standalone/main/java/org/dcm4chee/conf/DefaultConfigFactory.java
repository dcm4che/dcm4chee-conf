package org.dcm4chee.conf;

import org.dcm4che3.conf.api.internal.DicomConfigurationManager;
import org.dcm4chee.conf.browser.Standalone;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.dicom.DicomConfigurationBuilder;

import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.audit.AuditRecordRepository;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.imageio.ImageReaderExtension;
import org.dcm4che3.net.imageio.ImageWriterExtension;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.conf.ArchiveDeviceExtension;
import org.dcm4chee.archive.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.storage.conf.StorageConfiguration;
import org.dcm4chee.storage.conf.StorageDeviceExtension;
import org.dcm4chee.xds2.conf.*;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

/**
 * @author Roman K
 */
@ApplicationScoped
public class DefaultConfigFactory {

    @Produces
    @ApplicationScoped
    @Standalone
    DicomConfigurationManager createConfig() throws ConfigurationException {
        DicomConfigurationBuilder builder = DicomConfigurationBuilder.newConfigurationBuilder(System.getProperties());

        builder.registerDeviceExtension(HL7DeviceExtension.class);
        builder.registerDeviceExtension(AuditLogger.class);
        builder.registerDeviceExtension(AuditRecordRepository.class);
        builder.registerDeviceExtension(ImageReaderExtension.class);
        builder.registerDeviceExtension(ImageWriterExtension.class);

        builder.registerDeviceExtension(ArchiveDeviceExtension.class);
        builder.registerDeviceExtension(StorageDeviceExtension.class);
        builder.registerAEExtension(ArchiveAEExtension.class);
        builder.registerHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);

        builder.registerDeviceExtension(XdsRegistry.class);
        builder.registerDeviceExtension(XdsRepository.class);
        builder.registerDeviceExtension(XCARespondingGWCfg.class);
        builder.registerDeviceExtension(XCAiRespondingGWCfg.class);
        builder.registerDeviceExtension(XCAiInitiatingGWCfg.class);
        builder.registerDeviceExtension(XCAInitiatingGWCfg.class);
        builder.registerDeviceExtension(XdsSource.class);
        builder.registerDeviceExtension(StorageConfiguration.class);


        return builder.build();
    }


}
