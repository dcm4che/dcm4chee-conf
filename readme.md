# How to access (read/write) configuration / implement dedicated APIs

To access the dicom configuration, inject a `DicomConfiguration` bean with CDI like

    @Inject
    DicomConfiguration config;


[DicomConfiguration](https://github.com/dcm4che/dcm4che/blob/master/dcm4che-conf/dcm4che-conf-api/src/main/java/org/dcm4che3/conf/api/DicomConfiguration.java) is the primary interface for configuration access. It is type-safe and its implementation is supposed to perform thorough validation when persisting changes.
 Always give this interface the preference over other access methods.

Build dedicated APIs on top of `DicomConfiguration` to wrap up some special configuration functionality.

### Shortcut to access the 'primary device'

The archive contains a device producer so many components can easily lookup the configuration of the primary device that corresponds to the running application, like

    @Inject 
    private Device configDevice;


**IMPORTANT - do not change 'injected' device**

This java object is a singleton and its producer is an application scoped CDI bean, so this Device instance is shared among the application components and is therefore should only be used for reading the config.
To make modifications, use DicomConfiguration to lookup the device yourself (you could get the device name from that very configDevice), modify and merge that fresh isolated instance.
Otherwise, it's not thread-safe and could result into unexpected behavior.       

An attempt to change the injected device will result into an error log message like the following:

    17:33:34,654 ERROR [org.dcm4che3.conf.dicom.CommonDicomConfiguration] (pool-7-thread-5) Persisting the config for the Device object that is marked as read-only. This error is not affecting the behavior for now, but soon it will be replaced with an exception!If you want to make config modifications, use a separate instance of Device! See CSP configuration docs for details.

 If you find such a message in the log related to your config manipulations - then you are doing it wrong.

### Low-level configuration access

For special cases, one can inject `DicomConfigurationManager` and call `.getConfigurationStorage()` on it to obtain an instance of [Configuration](https://github.com/dcm4che/dcm4che/blob/master/dcm4che-conf/dcm4che-conf-core-api/src/main/java/org/dcm4che3/conf/core/api/Configuration.java).
This will allow a not-so-safe low-level access to the configuration. Although the validation of changes will still be enforced, it is more probable to introduce inconsistencies while using this layer. The usage of [Configuration](https://github.com/dcm4che/dcm4che/blob/master/dcm4che-conf/dcm4che-conf-core-api/src/main/java/org/dcm4che3/conf/core/api/Configuration.java) interface for making configuration changes is therefore discouraged.

The valid direct use-case of `Configuration` is issuing custom queries (i.e. that rely on some AE/Device/HL7 extensions)

## Config storage

Currently, only the database config storage backend provides the support for clustering and transactional ACID properties, and is therefore recommended for production deployments.
To use the database as configuration storage, deploy `org.dcm4che.dcm4chee-conf:dcm4chee-conf-db` as an EJB inside the ear and set the property 

    org.dcm4che.conf.storage = db_blobs

Alternatively, e.g. for development purposes, one can simple json file config storage (org.dcm4che.conf.storage = json_file)  

## Transactions and caching

Read access to configuration is always performed against the shared (non-blocking) reader cache.
Writes to the configuration are done in an exclusive manner - one write at most is done at a time within the cluster. This greatly simplifies concurrency concerns and at the same time, 
due to the not-so-volatile nature of configuration (rare updates), is not critical to the overall system performance.  
If no batch is used (see Batching section), a standalone call to dicomConfiguration .persist/.merge is similar to a batch with one operation.
Once a transaction succeeds, reader cache is updated. 
On transaction commit, configuration integrity check is performed.

## Batching

To perform multiple changes as a single atomic operation, one should use `org.dcm4che3.conf.api.DicomConfiguration.runBatch` / `org.dcm4che3.conf.core.api.Configuration.runBatch` methods.
The batch will be executed in a new transaction. It is guaranteed that at most one batch is executed at the same time (cluster-aware pessimistic locking is used).
Accessing the configuration within a batch also gives an isolation guarantee - a batch is given an exclusive cache.
The configuration will be fully reloaded from the backend into that cache, and all the reads/writes will be performed against it.

## Hash-based optimistic locking

When updating something in the configuration, hash-based optimistic locking is used to prevent conflicting changes and to preserve the parts of the configuration object 
that were changed by other user/component, but were not changed by the transaction at hand.     

#### Core principle

The mechanism is based on "fingerprinting" the portions of configuration objects by calculating a hash that represents a certain configuration state. 
If a node is changed (e.g., a parameter is changed or a map entry is removed or the order in a collection is changed) the corresponding hash will change as well.
Those hashes are not stored, but calculated when configuration is loaded. The calculated values are then injected into the properties of type 
org.dcm4che3.conf.core.api.ConfigurableProperty.ConfigurablePropertyType.OptimisticLockingHash.

When the node is merged back, the hashes are calculated again, and compared to old values (that were calculated on loading of this object), and also to the values of those currently in the storage.
The following logic is then applied

- If the user has not changed the node (or its part to which the hash corresponds), then the values for this node (part of the node) from the storage are preserved. 
- If the user has changed the node, then
    - if it was not changed by other users in the meantime, i.e. the hash from the storage equals to the old hash, the new node is persisted
    - if it was also changed by other users in the meantime, i.e. the hash from the storage is different from to the old hash, 
    then such modification is not allowed, and org.dcm4che3.conf.core.api.OptimisticLockException is thrown. 

The algorithm is recursively applied to full configuration node. The boundaries for hash calculation and comparison are marked by configurable properties of type 
org.dcm4che3.conf.core.api.ConfigurableProperty.ConfigurablePropertyType.OptimisticLockingHash.

The mechanism is also applicable to collections if collection elements have UUIDs defined (otherwise there is no straightforward way to conclude that collection elements match).
For example, it is enabled for connections of a device.

#### Example

For example, let three users load the same archive device from the configuration in the same moment in time. 

1. first user modifies the connection of a dicom device by changing its port and persists the change. This modification succeeds.
2. second user modifies the same connection by changing bind address and tries to persist the change. This modification fails, because it conflicts with the previous modification. 
3. third user adds a storage system to storage device extension. This modification succeeds, and even though the device being persisted has the stale configuration for the connection, 
it does not overwrite it because third user has not changed that part, and therefore the values from the config storage are preserved. 

This scenario is implemented as a JUnit test here:

    org.dcm4chee.archive.conf.olock.DicomConfigOptimisticLockingTests#optimisticLockingDemoTest

Other tests that demonstrate the hash-based optimistic locking can be found in the same test class.

#### How it affects components that use configuration 

The users of configuration framework should keep 2 things in mind: 

- When trying to merge configuration, be prepared that OptimisticLockException can be thrown. 
In the most simple case, re-loading the device again, modifying it, and persisting it again should work (be sure to re-execute any conditional logic that could be affected by the new changes).
More sophisticated scenarios (e.g. with config UI) could notify the user of the conflict and provide him/her the options to resolve it. 
- If serializing the configuration objects into some different data structures, be sure to preserve the values from the `olockHash` fields. 

## Config change notifications
Configuration framework triggers cluster-wide notification when a change occurs. 
Interested components may observe [org.dcm4che3.conf.core.api.ConfigChangeEvent](https://github.com/dcm4che/dcm4che/blob/master/dcm4che-conf/dcm4che-conf-core-api/src/main/java/org/dcm4che3/conf/core/api/ConfigChangeEvent.java) CDI event which is fired on each node.
In case of batching, the notification is only triggered when the full batch succeeds.
  
Current implementation uses `topic/DicomConfigurationChangeTopic` JMS topic to distribute the notifications across the cluster. The topic therefore must be added to the server config, e.g. 
    
    jms-topic add --topic-address=DicomConfigurationChangeTopic --entries=/topic/DicomConfigurationChangeTopic

  
## Development
Configuration components can be disabled for development purposes.

- To disable referential integrity check performed before transaction commit, set

        org.dcm4che.conf.disableIntegrityCheck = true

- To disable JMS-based cluster config update notifications, set

        org.dcm4che.conf.notifications = false
# How to perform upgrade/migration

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


Every upgrade script should be marked with `@org.dcm4che3.conf.api.upgrade.ScriptVersion` annotation. The runner will make sure that an upgrade script is only executed when either
- the script was never executed before
- the current version of the script is greater than the last executed version (String.compareTo is used to compare)
If a script has no such annotation - it will be assigned a default version - see constant org.dcm4che3.conf.api.upgrade.UpgradeScript#NO_VERSION .

Example: [DefaultArchiveConfigInitScript](https://github.com/dcm4che/dcm4chee-arc-cdi/blob/master/dcm4chee-arc-conf-default/src/main/java/org/dcm4chee/archive/conf/defaults/DefaultArchiveConfigInitScript.java)
   

# Examples

- [How to create a custom AE extension and use it in a StoreService decorator ](https://github.com/dcm4che/dcm4chee-integration-examples/tree/master/config-extensions-example)
