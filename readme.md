### How to access (read/write) configuration

To access the dicom configuration, inject a `DicomConfiguration` bean with CDI like

    @Inject
    DicomConfiguration config;


[DicomConfiguration](https://github.com/dcm4che/dcm4che/blob/master/dcm4che-conf/dcm4che-conf-api/src/main/java/org/dcm4che3/conf/api/DicomConfiguration.java) is the primary interface for configuration access. It is type-safe and its implementation is supposed to perform thorough validation when persisting changes.
 Always give this interface the preference over other access methods.


### How to implement dedicated APIs

Build dedicated APIs on top of `DicomConfiguration` to wrap up some special configuration functionality.

For special cases, one can inject `DicomConfigurationManager` and call `.getConfigurationStorage()` on it to obtain an instance of [Configuration](https://github.com/dcm4che/dcm4che/blob/master/dcm4che-conf/dcm4che-conf-core-api/src/main/java/org/dcm4che3/conf/core/api/Configuration.java).
This will allow a not-so-safe low-level access to the configuration. Although the validation of changes will still be enforced, it is more probable to introduce inconsistencies while using this layer. The usage of [Configuration](https://github.com/dcm4che/dcm4che/blob/master/dcm4che-conf/dcm4che-conf-core-api/src/main/java/org/dcm4che3/conf/core/api/Configuration.java) interface for making configuration changes is therefore discouraged.

The valid direct use-case of `Configuration` is issuing custom queries (i.e. that rely on some AE/Device/HL7 extensions)

### How to perform migration

Migration allows to use both high-level type-safe API ([DicomConfiguration](https://github.com/dcm4che/dcm4che/blob/master/dcm4che-conf/dcm4che-conf-api/src/main/java/org/dcm4che3/conf/api/DicomConfiguration.java)) and low-level unsafe API ([Configuration](https://github.com/dcm4che/dcm4che/blob/master/dcm4che-conf/dcm4che-conf-core-api/src/main/java/org/dcm4che3/conf/core/api/Configuration.java)).
Migration runner (to be introduced) will call all the migration scripts in proper order and provide the instances of `DicomConfiguration` and `Configuration` for them.