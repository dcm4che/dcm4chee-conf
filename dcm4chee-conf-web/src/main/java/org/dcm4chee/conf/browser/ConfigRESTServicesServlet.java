package org.dcm4chee.conf.browser;

import org.dcm4che3.conf.api.TCConfiguration;
import org.dcm4che3.conf.api.internal.DicomConfigurationManager;
import org.dcm4che3.conf.core.Nodes;
import org.dcm4che3.conf.core.api.*;
import org.dcm4che3.conf.core.api.internal.ConfigProperty;
import org.dcm4che3.conf.core.util.PathFollower;
import org.dcm4che3.conf.core.util.PathPattern;
import org.dcm4che3.conf.dicom.DicomPath;
import org.dcm4che3.net.*;
import org.dcm4che3.net.hl7.HL7ApplicationExtension;
import org.dcm4che3.conf.api.upgrade.ConfigurationMetadata;
import org.dcm4chee.util.SoftwareVersionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.event.Event;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.*;

@SuppressWarnings( "unchecked" )
@Path("/config")
@Produces(MediaType.APPLICATION_JSON)
public class ConfigRESTServicesServlet {
    public static final String NOTIFICATIONS_ENABLED_PROPERTY = "org.dcm4che.conf.notifications";
    private static final PathPattern referencePattern = new PathPattern(Configuration.REFERENCE_BY_UUID_PATTERN);

    public static final Logger log = LoggerFactory.getLogger(ConfigRESTServicesServlet.class);

    public enum OnlineStatus {
        ONLINE,
        OFFLINE,
        UNSUPPORTED
    }

    // Archive
    private static final String DEVICE_NAME_PROPERTY =
            "org.dcm4chee.archive.deviceName";
    private static final String DEF_DEVICE_NAME =
            "dcm4chee-arc";

    // XDS
    public static final Map<String, String> XDS_REST_PATH = new HashMap<>();

    static {
        XDS_REST_PATH.put("StorageConfiguration", "xds-rep-rs");
        XDS_REST_PATH.put("XdsRegistry", "xds-reg-rs");
        XDS_REST_PATH.put("XdsRepository", "xds-rep-rs");
        XDS_REST_PATH.put("XCAiInitiatingGWCfg", "xcai-rs");
        XDS_REST_PATH.put("XCAiRespondingGWCfg", "xcai-rs");
        XDS_REST_PATH.put("XCAInitiatingGWCfg", "xca-rs");
        XDS_REST_PATH.put("XCARespondingGWCfg", "xca-rs");
    }

    private static class SimpleConfigChangeEvent implements ConfigChangeEvent {

        private static final long serialVersionUID = 1338043186323821619L;

        @Override
        public CONTEXT getContext() {
            return CONTEXT.CONFIG_CHANGE;
        }

        @Override
        public List<String> getChangedPaths() {
            ArrayList<String> strings = new ArrayList<String>();
            strings.add("/");
            return strings;
        }
    }


    public static class DeviceJSON {

        public String deviceName;
        public String deviceDescription;
        public String deviceUuid;
        public Collection<AppEntityJSON> appEntities;
        public Collection<String> deviceExtensions;
        public boolean manageable;

        public static class AppEntityJSON {
            public String name;
            public String uuid;
        }
    }

    public static class SchemasJSON {

        public SchemasJSON() {
        }

        public Map<String, Object> tcgroups;
        public Map<String, Object> metadata;

        public Map<String, Object> device;

        /**
         * Parent class name to map - simple class name to schema
         */
        public Map<String, Map<String, Map>> extensions;

        public Map<String, Object> schemaForClassName = new HashMap<>();

    }

    public static class ConfigObjectJSON {

        public ConfigObjectJSON() {
        }

        /**
         * Object here is either a primitive, an array, a list, or Map<String, Object>
         */
        public Map<String, Object> rootConfigNode;
        public Map<String, Object> schema;

    }

    public static class ExtensionJSON {

        public ExtensionJSON() {
        }

        public String deviceName;
        /**
         * user-friendly name
         */
        public String extensionName;
        /**
         * Classname that will also be used for de-serialization
         */
        public String extensionType;
        /**
         * Can the user restart the device
         */
        public boolean restartable;
        /**
         * Can the user reconfigure the device
         */
        public boolean reconfigurable;

        public ConfigObjectJSON configuration;

    }


    @Inject
    @Manager
    DicomConfigurationManager configurationManager;

    @Inject
    Event<InternalConfigChangeEvent> internalConfigChangeEvent;

    @Inject
    Event<ConfigChangeEvent> configChangeEvent;

    @Inject
    Instance<SoftwareVersionProvider> versionProviderInstance;

    private void fireConfigUpdateNotificationIfNecessary() throws ConfigurationException {
        // fire only if notifications are disabled by a property. Normally they should not be, except special cases like IT testing
        if (!Boolean.valueOf(System.getProperty(NOTIFICATIONS_ENABLED_PROPERTY, "true"))) {

            // no need to refresh nodes - we are assuming non-cluster setup in case if notifications are disabled,
            // and in this case it is already refreshed while calling persistNode/removeNode
            //configurationManager.getConfigurationStorage().refreshNode("/");

            internalConfigChangeEvent.fire(new InternalConfigChangeEvent());
            configChangeEvent.fire(new SimpleConfigChangeEvent());
        }
    }

    @GET
    @Path("/devices")
    @Produces(MediaType.APPLICATION_JSON)
    public List<DeviceJSON> listDevices() throws ConfigurationException {

        List<DeviceJSON> list = new ArrayList<DeviceJSON>();
        for (String deviceName : configurationManager.listDeviceNames()) {
            Device d = configurationManager.findDevice(deviceName);

            DeviceJSON jd = new DeviceJSON();
            jd.deviceName = deviceName;
            jd.deviceDescription = d.getDescription();
            jd.deviceUuid = d.getUuid();
            jd.manageable = false;
            jd.appEntities = new ArrayList<>();

            for (ApplicationEntity applicationEntity : d.getApplicationEntities()) {
                DeviceJSON.AppEntityJSON ae = new DeviceJSON.AppEntityJSON();
                ae.name = applicationEntity.getAETitle();
                ae.uuid = applicationEntity.getUuid();
                jd.appEntities.add(ae);
            }

            jd.deviceExtensions = new ArrayList<String>();
            for (DeviceExtension de : d.listDeviceExtensions()) {
                jd.deviceExtensions.add(de.getClass().getSimpleName());
            }

            list.add(jd);
        }

        return list;
    }

    @GET
    @Path("/device/{deviceName}")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getDeviceConfig(@PathParam(value = "deviceName") String deviceName) throws ConfigurationException {
        return (Map<String, Object>) configurationManager.getConfigurationStorage().getConfigurationNode(DicomPath.devicePath(deviceName), Device.class);
    }

    @GET
    @Path("/transferCapabilities")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getTransferCapabilitiesConfig() throws ConfigurationException {
        return (Map<String, Object>) configurationManager.getConfigurationStorage().getConfigurationNode(DicomPath.TC_GROUPS_PATH, TCConfiguration.class);
    }


    @POST
    @Path("/transferCapabilities")
    @Produces(MediaType.APPLICATION_JSON)
    public void setTransferCapabilitiesConfig(Map<String, Object> config) throws ConfigurationException {
        configurationManager.getConfigurationStorage().persistNode(DicomPath.TC_GROUPS_PATH, config, TCConfiguration.class);
        fireConfigUpdateNotificationIfNecessary();
    }

    @GET
    @Path("/metadata")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getMetadata() throws ConfigurationException {
        return (Map<String, Object>) configurationManager.getConfigurationStorage().getConfigurationNode(DicomConfigurationManager.METADATA_ROOT_PATH, ConfigurationMetadata.class);
    }


    @POST
    @Path("/metadata")
    @Produces(MediaType.APPLICATION_JSON)
    public void setMetadata(Map<String, Object> config) throws ConfigurationException {
        configurationManager.getConfigurationStorage().persistNode(DicomConfigurationManager.METADATA_ROOT_PATH, config, ConfigurationMetadata.class);
        fireConfigUpdateNotificationIfNecessary();
    }


    @GET
    @Path("/exportFullConfiguration")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getFullConfig() throws ConfigurationException {
        return configurationManager.getConfigurationStorage().getConfigurationRoot();
    }

    @POST
    @Path("/importFullConfiguration")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setFullConfig(Map<String, Object> config) throws ConfigurationException {
        configurationManager.getConfigurationStorage().persistNode(org.dcm4che3.conf.core.api.Path.ROOT, config, null);
        fireConfigUpdateNotificationIfNecessary();
    }


    @DELETE
    @Path("/device/{deviceName}")
    @Produces(MediaType.APPLICATION_JSON)
    public void deleteDevice(@PathParam(value = "deviceName") String deviceName) throws ConfigurationException {
        if (deviceName.isEmpty()) throw new ConfigurationException("Device name cannot be empty");
        configurationManager.getConfigurationStorage().removeNode(DicomPath.devicePath(deviceName));
        fireConfigUpdateNotificationIfNecessary();
    }


    @POST
    @Path("/device/{deviceName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response modifyDeviceConfig(@Context UriInfo ctx, @PathParam(value = "deviceName") String deviceName, Map<String, Object> config) throws ConfigurationException {

        if (deviceName.isEmpty()) throw new ConfigurationException("Device name cannot be empty");

        configurationManager.getConfigurationStorage().persistNode(DicomPath.devicePath(deviceName), config, Device.class);

        fireConfigUpdateNotificationIfNecessary();

        return Response.ok().build();
    }

    @GET
    @Path("/pathByUUID/{uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    public org.dcm4che3.conf.core.api.Path getPathByUUID(@PathParam(value = "uuid") String uuid) {
        return configurationManager.getConfigurationStorage().getPathByUUID(uuid);
    }

    /**
     * Returns a fully processed node, i.e. including defaults, hashes, etc
     *
     * @param pathStr
     * @return
     */
    @GET
    @Path("/node")
    @Produces(MediaType.APPLICATION_JSON)
    public Object getConfigurationNode(
            @QueryParam(value = "path") String pathStr,
            @QueryParam( value = "class") String className
    ) throws ClassNotFoundException
    {
        org.dcm4che3.conf.core.api.Path path = org.dcm4che3.conf.core.api.Path.fromSimpleEscapedPath(pathStr);

        Class configurableClass;
        if (className != null) {
            configurableClass = Class.forName( className );
        } else {
            ConfigProperty last = PathFollower.traceProperties(configurationManager.getTypeSafeConfiguration().getRootClass(), path).getLast();
            configurableClass = last.isConfObject() ? last.getRawClass() : null;
        }

        return configurationManager.getConfigurationStorage().getConfigurationNode(path, configurableClass );
    }

    @POST
    @Path("/node")
    @Produces(MediaType.APPLICATION_JSON)
    public void persistConfigurationNode(
            @QueryParam(value = "path") String pathStr,
            @QueryParam( value = "class") String className,
            Map<String, Object> config) throws ClassNotFoundException
    {
        org.dcm4che3.conf.core.api.Path path = org.dcm4che3.conf.core.api.Path.fromSimpleEscapedPath(pathStr);

        Class configurableClass;
        if (className != null) {
            configurableClass = Class.forName( className );
        } else {
            ConfigProperty last = PathFollower.traceProperties(configurationManager.getTypeSafeConfiguration().getRootClass(), path).getLast();
            configurableClass = last.isConfObject() ? last.getRawClass() : null;
        }

        configurationManager.getConfigurationStorage().persistNode(path, config, configurableClass);

        fireConfigUpdateNotificationIfNecessary();
    }


    @DELETE
    @Path( "/node" )
    public void removeConfigurationNode(
            @QueryParam( value = "path" ) String pathStr
    ) {
        configurationManager.getConfigurationStorage().removeNode( org.dcm4che3.conf.core.api.Path.fromSimpleEscapedPath( pathStr ) );
    }

    @GET
    @Path("/schemas")
    @Produces(MediaType.APPLICATION_JSON)
    public SchemasJSON getSchema(@QueryParam(value = "class") List<String> classNames) throws ConfigurationException, ClassNotFoundException
    {


        SchemasJSON schemas = new SchemasJSON();
        schemas.device = getSchemaForConfigurableClass(Device.class);
        schemas.tcgroups = getSchemaForConfigurableClass(TCConfiguration.class);
        schemas.metadata = getSchemaForConfigurableClass(ConfigurationMetadata.class);
        schemas.extensions = new HashMap<>();

        HashMap<String, Map> deviceExtensions = new HashMap<String, Map>();
        schemas.extensions.put("Device", deviceExtensions);
        HashMap<String, Map> aeExtensions = new HashMap<String, Map>();
        schemas.extensions.put("ApplicationEntity", aeExtensions);
        HashMap<String, Map> hl7AppExtensions = new HashMap<String, Map>();
        schemas.extensions.put("HL7Application", hl7AppExtensions);
        HashMap<String, Map> connectionExtensions = new HashMap<String, Map>();
        schemas.extensions.put("Connection", connectionExtensions);

        for (Class<? extends DeviceExtension> deviceExt : configurationManager.getExtensionClassesByBaseClass(DeviceExtension.class))
            deviceExtensions.put(deviceExt.getSimpleName(), getSchemaForConfigurableClass(deviceExt));

        for (Class<? extends AEExtension> aeExt : configurationManager.getExtensionClassesByBaseClass(AEExtension.class))
            aeExtensions.put(aeExt.getSimpleName(), getSchemaForConfigurableClass(aeExt));

        for (Class<? extends HL7ApplicationExtension> hl7Ext : configurationManager.getExtensionClassesByBaseClass(HL7ApplicationExtension.class))
            hl7AppExtensions.put(hl7Ext.getSimpleName(), getSchemaForConfigurableClass(hl7Ext));

        for (Class<? extends ConnectionExtension> connExt : configurationManager.getExtensionClassesByBaseClass(ConnectionExtension.class)) {
            connectionExtensions.put(connExt.getSimpleName(), getSchemaForConfigurableClass(connExt));
        }

        // generic schemas
        for ( String className : classNames )
        {
            schemas.schemaForClassName.put( className, getSchemaForConfigurableClass(Class.forName( className )));
        }

        return schemas;
    }

    private Map<String, Object> getSchemaForConfigurableClass(Class<?> clazz) throws ConfigurationException {
        return configurationManager.getVitalizer().getSchemaForConfigurableClass(clazz);
    }


    /**
     * For troubleshooting purposes - returns all the versions of software components defined in the deployment
     */
    @GET
    @Path("/versions")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> getVersions() {

        if (versionProviderInstance.isUnsatisfied()) {
            log.warn("No versions provider defined");
        }

        Map<String, String> allVersions = new HashMap<>();

        for (SoftwareVersionProvider softwareVersionProvider : versionProviderInstance) {
            allVersions.putAll(softwareVersionProvider.getAllVersions());
        }

        return allVersions;

    }


}
