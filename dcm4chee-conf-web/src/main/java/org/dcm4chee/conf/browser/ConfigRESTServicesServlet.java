package org.dcm4chee.conf.browser;

import org.dcm4che3.conf.api.TCConfiguration;
import org.dcm4che3.conf.api.internal.DicomConfigurationManager;
import org.dcm4che3.conf.core.api.*;
import org.dcm4che3.conf.core.api.internal.AnnotatedConfigurableProperty;
import org.dcm4che3.conf.core.api.internal.BeanVitalizer;
import org.dcm4che3.conf.core.api.internal.ConfigTypeAdapter;
import org.dcm4che3.conf.dicom.CommonDicomConfiguration;
import org.dcm4che3.conf.dicom.DicomPath;
import org.dcm4che3.net.*;
import org.dcm4che3.net.hl7.HL7ApplicationExtension;
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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

@Path("/config")
@Produces(MediaType.APPLICATION_JSON)
public class ConfigRESTServicesServlet {
    public static final String NOTIFICATIONS_ENABLED_PROPERTY = "org.dcm4che.conf.notifications";

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

        public Map<String, Object> device;

        /**
         * Parent class name to map - simple class name to schema
         */
        public Map<String, Map<String, Map>> extensions;

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
        return (Map<String, Object>) configurationManager.getConfigurationStorage().getConfigurationNode(DicomPath.DeviceByNameForWrite.set("deviceName", deviceName).path(), Device.class);
    }

    @GET
    @Path("/transferCapabilities")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getTransferCapabilitiesConfig() throws ConfigurationException {
        return (Map<String, Object>) configurationManager.getConfigurationStorage().getConfigurationNode(DicomPath.TCGroups.path(), TCConfiguration.class);
    }


    @POST
    @Path("/transferCapabilities")
    @Produces(MediaType.APPLICATION_JSON)
    public void setTransferCapabilitiesConfig(Map<String, Object> config) throws ConfigurationException {
        configurationManager.getConfigurationStorage().persistNode(DicomPath.TCGroups.path(), config, TCConfiguration.class);
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
        configurationManager.getConfigurationStorage().persistNode("/", config, CommonDicomConfiguration.DicomConfigurationRootNode.class);
        fireConfigUpdateNotificationIfNecessary();
    }


    @DELETE
    @Path("/device/{deviceName}")
    @Produces(MediaType.APPLICATION_JSON)
    public void deleteDevice(@PathParam(value = "deviceName") String deviceName) throws ConfigurationException {
        if (deviceName.isEmpty()) throw new ConfigurationException("Device name cannot be empty");
        configurationManager.getConfigurationStorage().removeNode(DicomPath.DeviceByNameForWrite.set("deviceName", deviceName).path());
        fireConfigUpdateNotificationIfNecessary();
    }


    @POST
    @Path("/device/{deviceName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response modifyDeviceConfig(@Context UriInfo ctx, @PathParam(value = "deviceName") String deviceName, Map<String, Object> config) throws ConfigurationException {

        if (deviceName.isEmpty()) throw new ConfigurationException("Device name cannot be empty");

        configurationManager.getConfigurationStorage().persistNode(DicomPath.DeviceByNameForWrite.set("deviceName", deviceName).path(), config, Device.class);

        fireConfigUpdateNotificationIfNecessary();

        return Response.ok().build();
    }

    @GET
    @Path("/schemas")
    @Produces(MediaType.APPLICATION_JSON)
    public SchemasJSON getSchema() throws ConfigurationException {


        SchemasJSON schemas = new SchemasJSON();
        schemas.device = getSchemaForConfigurableClass(Device.class);
        schemas.tcgroups = getSchemaForConfigurableClass(TCConfiguration.class);
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

        return schemas;
    }

    private Map<String, Object> getSchemaForConfigurableClass(Class<?> clazz) throws ConfigurationException {
        BeanVitalizer vitalizer = configurationManager.getVitalizer();
        return vitalizer.lookupDefaultTypeAdapter(clazz).getSchema(new AnnotatedConfigurableProperty(clazz), vitalizer);
    }


    /**
     * For troubleshooting purposes - returns all the versions of software components defined in the deployment
     */
    @GET
    @Path("/versions")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String,String> getVersions() {

        if (versionProviderInstance.isUnsatisfied()) {
            log.warn("No versions provider defined");
        }

        Map<String,String> allVersions = new HashMap<>();

        for (SoftwareVersionProvider softwareVersionProvider : versionProviderInstance) {
            allVersions.putAll(softwareVersionProvider.getAllVersions());
        }

        return allVersions;

    }


    /***
     * this method is just left for backwards-compatibility
     *
     * @param ctx
     * @param extJson
     * @throws ConfigurationException
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    @POST
    @Path("/save-extension")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public void saveConfigForExtension(@Context UriInfo ctx, ExtensionJSON extJson) throws ConfigurationException {


        Class<? extends DeviceExtension> extClass;
        try {
            extClass = (Class<? extends DeviceExtension>) Class.forName(extJson.extensionType);
        } catch (ClassNotFoundException e) {
            throw new ConfigurationException("Extension " + extJson.extensionType + " is not configured", e);
        }

        // check if the supplied classname is actually a configclass
        if (extClass.getAnnotation(ConfigurableClass.class) == null)
            throw new ConfigurationException("Extension " + extJson.extensionType + " is not configured");

        // get current config
        Device d = configurationManager.findDevice(extJson.deviceName);
        DeviceExtension currentDeviceExt = d.getDeviceExtension(extClass);

        ConfigTypeAdapter ad = configurationManager.getVitalizer().lookupDefaultTypeAdapter(extClass);

        // serialize current
        Map<String, Object> configmap = (Map<String, Object>) ad.toConfigNode(currentDeviceExt, null, configurationManager.getVitalizer());

        // copy all the filled submitted fields
        configmap.putAll(extJson.configuration.rootConfigNode);

        // deserialize back

        DeviceExtension de = (DeviceExtension) ad.fromConfigNode(configmap, new AnnotatedConfigurableProperty(extClass), configurationManager.getVitalizer(), null);

        // merge config
        d.removeDeviceExtension(de);
        d.addDeviceExtension(de);

        configurationManager.merge(d);

        // also try to call reconfigure after saving
        try {
            Response response = reconfigureExtension(ctx, extJson.deviceName, extJson.extensionName);
            if (response.getStatus() != 204)
                throw new ConfigurationException("Reconfiguration unsuccessful (HTTP status " + response.getStatus() + ")");
        } catch (ConfigurationException e) {
            log.warn("Unable to reconfigure extension " + extJson.extensionName + " for device " + extJson.deviceName + " after saving", e);
        }

    }

    @Deprecated
    @GET
    @Path("/reconfigure-all-extensions/{deviceName}")
    public void reloadAllExtensionsOfDevice(@Context UriInfo ctx, @PathParam("deviceName") String deviceName) throws ConfigurationException {
        Device device = configurationManager.findDevice(deviceName);

        // xds
        for (DeviceExtension deviceExtension : device.listDeviceExtensions()) {
            String extensionName = deviceExtension.getClass().getSimpleName();
            if (XDS_REST_PATH.get(extensionName) != null)
                reconfigureExtension(ctx, deviceName, extensionName);
        }

        // for local Archive
        Response response = reconfigureAtURL(ctx, "dcm4chee-arc", "/");
        log.info("Archive reconfiguration response = {}", response.getStatus());

    }

    @Deprecated
    @GET
    @Path("/reconfigure-extension/{deviceName}/{extension}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response reconfigureExtension(@Context UriInfo ctx, @PathParam("deviceName") String deviceName, @PathParam("extension") String extension) throws ConfigurationException {

        String connectedDeviceUrl = System.getProperty("org.dcm4chee.device." + deviceName);

        if (connectedDeviceUrl == null)
            throw new ConfigurationException("Device " + deviceName + " is not controlled (connected), please inspect the JBoss configuration");

        // add prefix part if needed
        String ext_prefix = "";
        ext_prefix = XDS_REST_PATH.get(extension);
        if (ext_prefix == null)
            throw new ConfigurationException(String.format("Extension not recognized (%s)", ext_prefix));

        return reconfigureAtURL(ctx, ext_prefix, connectedDeviceUrl);
    }

    private Response reconfigureAtURL(UriInfo ctx, String prefix, String connectedDeviceUrl) throws ConfigurationException {
        if (!connectedDeviceUrl.startsWith("http")) {
            URL url = null;
            try {
                url = ctx.getAbsolutePath().toURL();
            } catch (MalformedURLException e1) {
                throw new ConfigurationException("Unexpected exception - protocol must be http"); // should not happen
            }

            String formatStr;
            if (connectedDeviceUrl.startsWith("/"))
                formatStr = "%s://%s%s";
            else
                formatStr = "%s://%s/%s";

            connectedDeviceUrl = String.format(formatStr, url.getProtocol(), url.getAuthority(), connectedDeviceUrl);
        }


        // // figure out the URL for reloading the config

        String reconfUrl = connectedDeviceUrl + (connectedDeviceUrl.endsWith("/") ? "" : "/") + prefix + (prefix.equals("") ? "" : "/") + "ctrl/reload";

        try {
            URL obj = new URL(reconfUrl);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            log.info("Calling configuration reload @ {} ...", reconfUrl);
            int responseCode = con.getResponseCode();
            return Response.status(con.getResponseCode()).build();
        } catch (java.io.IOException e1) {
            throw new ConfigurationException(e1);
        }
    }
}
