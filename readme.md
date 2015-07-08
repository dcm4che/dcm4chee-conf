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

### How to perform upgrade/migration

Upgrade mechanism allows to use both high-level type-safe API ([DicomConfiguration](https://github.com/dcm4che/dcm4che/blob/master/dcm4che-conf/dcm4che-conf-api/src/main/java/org/dcm4che3/conf/api/DicomConfiguration.java)) and low-level unsafe API ([Configuration](https://github.com/dcm4che/dcm4che/blob/master/dcm4che-conf/dcm4che-conf-core-api/src/main/java/org/dcm4che3/conf/core/api/Configuration.java)).
To create an upgrade routine one needs to

1. crate a class that implements `org.dcm4che3.conf.api.upgrade.UpgradeScript` interface,
2. make sure this class is contained in the deployment and is visible by CDI,
3. include the full class name in the upgrade setting file.

Upgrade is performed on server startup before any method can access the configuration. The steps are the following:

1.  The upgrade runner reads the upgrade settings file. The filename should be specified by `org.dcm4che.conf.upgrade.settingsFile` system property. If the property is not set, the upgrade mechanism is disabled.
    Example of an upgrade settings file:

        {
         "upgradeToVersion": "1.5",
         "upgradeScriptsToRun": [
           "com.mycompany.upgrade.UpgradeFirst",
           "com.mycompany.upgrade.UpgradeSecond",
          ],
          "properties":{
            "aPropertyForMyUpgradeScripts":"aValue"
          }
        }
2. The runner will call all the referenced upgrade scripts in the sequence specified by `upgradeScriptsToRun`, making sure that the upgrade is executed on a single node and in an atomic manner (i.e. all-or-nothing).
`org.dcm4che3.conf.api.upgrade.UpgradeScript` interface provides both typesafe and not typesafe access to the configuration, as well as to upgrade context properties, including
        `fromVersion`, `toVersion`, and `properties` (these properties are populated from the upgrade settings file, see above);

3. If the upgrade succeeds, the changes are committed and the startup process proceeds. If there is an error during the upgrade, the changes are rolled back and the deployment fails.

Example: [DefaultArchiveConfigInitScript](https://github.com/dcm4che/dcm4chee-arc-cdi/blob/master/dcm4chee-arc-conf-default/src/main/java/org/dcm4chee/archive/conf/defaults/DefaultArchiveConfigInitScript.java)


# Examples

- [How to create a custom AE extension and use it in a StoreService decorator ](https://github.com/dcm4che/dcm4chee-integration-examples/tree/master/config-extensions-example)
