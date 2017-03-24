angular.module('dcm4che.config.manager', ['dcm4che.appCommon', 'dcm4che.config.core']
).controller('ServiceManagerCtrl', function ($scope, $confirm, appHttp, appNotifications, ConfigEditorService, customizations) {

        $scope.switchToAdvancedView = function () {
            $scope.advancedView = true;
        };

        function checkModified() {
            $scope.selectedDevice.isModified = !angular.equals($scope.selectedDevice.lastPersistedConfig, $scope.selectedDevice.config);
        }

        $scope.$on('configurationChanged', checkModified);

        $scope.configuration = {};

        $scope.cancelChangesDevice = function (device) {
            device = device || $scope.selectedDevice;
            device.config = angular.copy(device.lastPersistedConfig);
            checkModified();
        };

        $scope.reconfigureDevice = function (device) {
            device = device || $scope.selectedDevice;

            appHttp.get("data/config/reconfigure-all-extensions/" + device.deviceName, null, function (data) {
                appNotifications.showNotification({
                    level: "success",
                    text: "The service has successfully reloaded the configuration",
                    details: [data, status]
                })

            }, function (data, status) {
                appNotifications.showNotification({
                    level: "danger",
                    text: "The service was not able to reload the configuration",
                    details: [data, status]
                })
            });
        };
        $scope.loadDeviceConfig = function (device, cb) {
            device = device || $scope.selectedDevice;
            appHttp.get("data/config/device/" + device.deviceName, null, function (data) {
                device.config = data;
                device.lastPersistedConfig = angular.copy(data);

                if (cb) cb();

            }, function (data, status) {
                appNotifications.showNotification({
                    level: "danger",
                    text: "Could not load device config",
                    details: [data, status]
                })
            });

        };

        $scope.saveDeviceConfig = function (device) {
            device = device || $scope.selectedDevice;
            var configToSave = angular.copy(device.config);
            appHttp.post("data/config/device/" + device.deviceName, configToSave, function (data, status) {

                appNotifications.showNotification({
                    level: "success",
                    text: "Configuration successfully saved",
                    details: [data, status]
                });

                // reload this device config right away to populate the updated olock hashes
                $scope.loadDeviceConfig(device, function () {
                    checkModified();
                });

            }, function (data, status) {
                appNotifications.showNotification({
                    level: "danger",
                    text: "Could not save device config",
                    details: [data, status]
                })
            });

        };

        $scope.createDevice = function (deviceName) {
            if (_.contains($scope.deviceNames, deviceName)) {
                appNotifications.showNotification({
                    level: "danger",
                    text: "Device " + deviceName + " already exists",
                    details: ['', '']
                });
            } else {
                var deviceConfig = ConfigEditorService.createNewItem(ConfigEditorService.schemas.device);
                deviceConfig.dicomDeviceName = deviceName;

                appHttp.post("data/config/device/" + deviceName, deviceConfig, function (data, status) {

                    // refresh
                    window.location.reload();

                }, function (data, status) {
                    appNotifications.showNotification({
                        level: "danger",
                        text: "Could not create device",
                        details: [data, status]
                    })
                });
            }
        };

        $scope.deleteThisDevice = function () {
            $confirm("Do you really want to delete this device (" + $scope.selectedDeviceName + ")?").then(
                function () {

                    appHttp.delete("data/config/device/" + $scope.selectedDeviceName, null, function (data, status) {

                        // refresh
                        window.location.reload();

                    }, function (data, status) {
                        appNotifications.showNotification({
                            level: "danger",
                            text: "Could not delete device",
                            details: [data, status]
                        })
                    });

                },
                function () {
                    console.log('cancelled');

                }
            );
        };

        // bootstrap devicelist @ start
        ConfigEditorService.load(function () {
            $scope.devices = ConfigEditorService.devices;
            $scope.deviceNames = _.pluck($scope.devices, 'deviceName');


            var defaultDeviceName = "dcm4chee-arc";
            if (customizations.xdsDeviceName) {
                defaultDeviceName = customizations.xdsDeviceName;
            }

            if (_.contains($scope.deviceNames, defaultDeviceName))
                $scope.selectedDeviceName = defaultDeviceName;

            if ($scope.selectedDeviceName == null && $scope.devices.length > 0)
                $scope.selectedDeviceName = $scope.devices[0].deviceName;

            $scope.addDeviceExtDropdown = ConfigEditorService.makeAddExtensionDropDown('selectedDevice.config', 'Device');
        });


        $scope.$watch("selectedDeviceName", function (value) {

                $scope.isDeviceNavCollapsed = false;

                var selectedDevice = _.findWhere($scope.devices, {deviceName: value});
                if (selectedDevice != null) {
                    $scope.selectedDevice = selectedDevice;
                    if ($scope.selectedDevice.config == null)
                        $scope.loadDeviceConfig();
                }
            }
        );

    })

    .controller('DeviceEditorController', function ($scope, appHttp, appNotifications, ConfigEditorService) {
        $scope.ConfigEditorService = ConfigEditorService;
        $scope.editor = {
            options: null
        };
        $scope.$watchCollection('selectedDevice.config.dicomConnection', function () {
            if ($scope.selectedDevice && $scope.selectedDevice.config)
                $scope.editor.connectionRefs = $scope.selectedDevice ? _.map($scope.selectedDevice.config.dicomConnection, function (connection) {
                    return {
                        name: connection.cn + "," + connection.dcmProtocol + "(" + connection.dicomHostname + ":" + connection.dicomPort + ")",
                        ref: "//*[_.uuid='" + connection['_.uuid'] + "']"
                    };
                }) : {};

        });
        $scope.$watch("selectedDevice.config", function () {
            $scope.selectedConfigNode = null;
            $scope.selectedConfigNodeSchema = null;
            $scope.editor.options = null;

        });
        $scope.selectConfigNode = function (node, schema, parent, parentschema, index, options) {
            if (schema == null) throw "Schema not defined";
            $scope.selectedConfigNode = {
                node: node,
                schema: schema,
                parentNode: parent,
                parentSchema: parentschema,
                index: index,
                options: options
            };
        };
    })

    .controller('DeleteTopLevelElementController', function ($scope, $confirm, ConfigEditorService) {

        $scope.deleteCurrentElement = function () {
            $confirm("Do you really want to delete this " + $scope.selectedConfigNode.schema.class + "?").then(
                function () {

                    var selectedNodeConf = $scope.selectedConfigNode;

                    if (selectedNodeConf.parentSchema.type == 'array')
                        selectedNodeConf.parentNode.splice(selectedNodeConf.index, 1);

                    else
                        delete selectedNodeConf.parentNode[selectedNodeConf.index];

                    $scope.selectedConfigNode.parentSchema = null;
                    $scope.selectedConfigNode.parentNode = null;
                    $scope.selectedConfigNode.node = null;
                    $scope.selectedConfigNode.schema = null;

                    ConfigEditorService.checkModified();

                },
                function () {
                    console.log('cancelled');

                }
            );
        };
    })

    .controller('RawConfigEditor', function ($scope, appHttp, appNotifications) {

        appHttp.get("data/config/exportFullConfiguration/", null, function (data, status) {

            $scope.fullConfig = JSON.stringify(data, null, '  ');

        }, function (data, status) {
            appNotifications.showNotification({
                level: "danger",
                text: "Could not load full config",
                details: [data, status]
            })
        });

        $scope.saveFullConfig = function () {
            try {
                JSON.parse($scope.fullConfig)
            } catch (e) {
                appNotifications.showNotification({
                    level: "danger",
                    text: "Cannot parse JSON",
                    details: ["Check JSON syntax", 0]
                });

            }
        }

    })

    .controller('TransferCapabilitiesEditor', function ($scope, appHttp, appNotifications, ConfigEditorService) {

        $scope.ConfigEditorService = ConfigEditorService;
        $scope.editor = {
            options: null
        };

        ConfigEditorService.load(function () {

        });

        appHttp.get("data/config/transferCapabilities/", null, function (data, status) {

            $scope.tcConfig = data;
            $scope.lastTcConfig = angular.copy(data);

        }, function (data, status) {
            appNotifications.showNotification({
                level: "danger",
                text: "Could not load transfer capabilities",
                details: [data, status]
            })
        });

        $scope.saveTcConfig = function () {

            var tccconfig2Persist = angular.copy($scope.tcConfig);

            appHttp.post("data/config/transferCapabilities/", tccconfig2Persist, function (data, status) {

                $scope.lastTcConfig = tccconfig2Persist;

                checkModified();

                appNotifications.showNotification({
                    level: "success",
                    text: "Transfer capabilities saved",
                    details: [data, status]
                })


            }, function (data, status) {
                appNotifications.showNotification({
                    level: "danger",
                    text: "Could not save transfer capabilities",
                    details: [data, status]
                })
            });

        };

        $scope.cancelChangesTcConfig = function() {
            $scope.tcConfig = angular.copy($scope.lastTcConfig);
            checkModified();
        };

        var checkModified = function() {
            $scope.isTcConfigModified = !angular.equals($scope.tcConfig, $scope.lastTcConfig);
        };

        $scope.$on('configurationChanged', checkModified);

    })


    .controller('MMAEditor', function ($scope, appHttp, appNotifications, ConfigEditorService) {

        $scope.ConfigEditorService = ConfigEditorService;
        $scope.editor = {
            options: null
        };

        ConfigEditorService.load(function () {

        });

        appHttp.get("data/config/node?path=/dicomConfigurationRoot/globalConfiguration/multiMediaArchive&class=com.agfa.mma.config.MMAConfigurationRoot", null, function (data, status) {

            $scope.mmaConfig = data;
            $scope.lastMMAConfig = angular.copy(data);

        }, function (data, status) {
            appNotifications.showNotification({
                level: "danger",
                text: "Could not load MMA config",
                details: [data, status]
            })
        });

        $scope.saveMMAConfig = function () {

            var mmaconfig2Persist = angular.copy($scope.mmaConfig);

            appHttp.post("data/config/node?path=/dicomConfigurationRoot/globalConfiguration/multiMediaArchive&class=com.agfa.mma.config.MMAConfigurationRoot", mmaconfig2Persist, function (data, status) {

                $scope.lastMMAConfig = mmaconfig2Persist;

                checkModified();

                appNotifications.showNotification({
                    level: "success",
                    text: "MMA config saved",
                    details: [data, status]
                })


            }, function (data, status) {
                appNotifications.showNotification({
                    level: "danger",
                    text: "Could not save MMA config",
                    details: [data, status]
                })
            });

        };

        $scope.cancelChangesMMAConfig = function() {
            $scope.mmaConfig = angular.copy($scope.lastMMAConfig);
            checkModified();
        };

        var checkModified = function() {
            $scope.isMMAConfigModified = !angular.equals($scope.mmaConfig, $scope.lastMMAConfig);
        };

        $scope.$on('configurationChanged', checkModified);

    })


    .controller('MetadataEditor', function ($scope, appHttp, appNotifications, ConfigEditorService) {

        $scope.ConfigEditorService = ConfigEditorService;
        $scope.editor = {
            options: null
        };

        ConfigEditorService.load(function () {

        });

        appHttp.get("data/config/metadata/", null, function (data, status) {

            $scope.metadataConfig = data;
            $scope.lastMetadataConfig = angular.copy(data);

        }, function (data, status) {
            appNotifications.showNotification({
                level: "danger",
                text: "Could not metadata",
                details: [data, status]
            })
        });

        $scope.saveMetadataConfig = function () {

            var metadata2Persist = angular.copy($scope.metadataConfig);

            appHttp.post("data/config/metadata/", metadata2Persist, function (data, status) {

                $scope.lastMetadataConfig = metadata2Persist;

                checkModified();

                appNotifications.showNotification({
                    level: "success",
                    text: "Metadata saved",
                    details: [data, status]
                })


            }, function (data, status) {
                appNotifications.showNotification({
                    level: "danger",
                    text: "Could not save metadata",
                    details: [data, status]
                })
            });

        };

        $scope.cancelChangesMetadataConfig = function() {
            $scope.metadataConfig = angular.copy($scope.lastMetadataConfig);
            checkModified();
        };

        var checkModified = function() {
            $scope.isMetadataConfigModified = !angular.equals($scope.metadataConfig, $scope.lastMetadataConfig);
        };

        $scope.$on('configurationChanged', checkModified);

    })

    // 'global' service
    .factory("ConfigEditorService", function ($rootScope, appNotifications, appHttp, $timeout) {

        var hasType = function (schema, type) {
            if (!schema) return false;

            if (_.isArray(schema.type))
                return _.contains(schema.type, type);
            else
                return schema.type == type;
        };

        var modifiedChecksTriggered = 0;

        var conf = {

            groupOrder: [
                "General",
                "Affinity domain",
                "XDS profile strictness",
                "Endpoints",
                "Other",
                "Logging"
            ],

            selectedDevice: null,
            devices: [],
            deviceRefs: [],
            aeRefs: [],

            extensionsPropertyForClass: {
                "Device": "deviceExtensions",
                "ApplicationEntity": "aeExtensions",
                "HL7Application": "hl7AppExtensions",
                "Connection": "connectionExtensions"
            },

            schemas: {},

            //connectathon//    /////// ////////        ////////

            registries: [{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://nist1:9085/tf6/services/xdsregistryb","soapEndpointUrl":"https://nist1:9085/tf6/services/xdsregistryb","systemName":"OTHER_NIST_RED_2014"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://agfa32:8443/xds/registry","soapEndpointUrl":"https://agfa32:8443/xds/registry","systemName":"PACS_AGFA_EE2"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://osvb1:9099/ihe/xds-iti18","soapEndpointUrl":"https://osvb1:9099/ihe/xds-iti42","systemName":"XDSb_REG_OSVB"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://meddex0:8443/MGXDS/DocumentRegistry_Service","soapEndpointUrl":"https://meddex0:8443/MGXDS/DocumentRegistry_Service","systemName":"XDSab_REP_Meddex"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://solinfo2:8443/democrito/registryb","soapEndpointUrl":"https://solinfo2:8443/democrito/registryb","systemName":"EHR_SOLINFO_CARTESIO"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://etiam2:8080/your_url","soapEndpointUrl":"https://etiam2:8080/your_url","systemName":"OTHER_ETIAM_NEXUS"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://gpi3:8643/DocumentRepositoryXDSBWS/DocumentRegistryXDSB","soapEndpointUrl":"https://gpi3:8643/DocumentRepositoryXDSBWS/DocumentRegistryXDSB","systemName":"XDSb_REP_GPI"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://acuo5:17036/XDS/Registry","soapEndpointUrl":"https://acuo5:17036/XDS/Registry","systemName":"XDSb_REG_Acuo_6.0.1"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://arts8:8445/Registry","soapEndpointUrl":"https://arts8:8445/Registry","systemName":"OTHER_ARTS_4_0"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://tie4:7002/tieservice/xdsregistryb","soapEndpointUrl":"https://tie4:7002/tieservice/xdsregistryb","systemName":"OTHER_TIE"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://nist18:9085/tf6/services/xdsregistryb","soapEndpointUrl":"https://nist18:9085/tf6/services/xdsregistryb","systemName":"OTHER_NIST_BLUE_2014"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://forcare14:8443/index/services/registry","soapEndpointUrl":"https://forcare14:8443/index/services/registry","systemName":"XDSab_REG_Forcare_2"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://ideosante0:8443/xdsbRegistry","soapEndpointUrl":"https://ideosante0:8443/xdsbRegistry","systemName":"EHR_IdéoSanté"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://rogan23:2021/IHE-XDS/Registry","soapEndpointUrl":"https://rogan23:2021/IHE-XDS/Registry","systemName":"EHR_Rogan_2"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://bint1:444/csp/healthshare/bintmed/HS.IHE.XDSb.Registry.Services.cls","soapEndpointUrl":"https://bint1:444/csp/healthshare/bintmed/HS.IHE.XDSb.Registry.Services.cls","systemName":"OTHER_BINT"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://visus24/JiveX_XDSRegistry_WS/service","soapEndpointUrl":"https://visus24/JiveX_XDSRegistry_WS/service","systemName":"XDSb_REP_VISUS_2014"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://intersystems3/csp/connect/HS.IHE.XDSb.Registry.Services.cls","soapEndpointUrl":"https://intersystems3/csp/connect/HS.IHE.XDSb.Registry.Services.cls","systemName":"GATEWAY_InterSystems_HealthShare"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://a-thon19:8443/rem-ada-ee-war/services/ADA","soapEndpointUrl":"https://a-thon19/XDS/registry/registry.php","systemName":"XDSab_REG_A-thon_7"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://nist17:9085/tf6/services/xdsregistryb","soapEndpointUrl":"https://nist17:9085/tf6/services/xdsregistryb","systemName":"OTHER_NIST_GREEN_2014"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://icw15:8443/pxs/webservices/rev6/xdsb-storedquery","soapEndpointUrl":"https://icw15:8443/pxs/webservices/rev6/xdsb-registerdocuments","systemName":"EHR_ICW_1"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://tiani---cisco75:8443/XDS3/C1","soapEndpointUrl":"https://tiani---cisco75:8443/XDS3/C1","systemName":"EHR_Tiani - Cisco_SpiritEHR"},{"allowOtherAffinityDomains":false,"createMissingPID":false,"soapEndpointQueryUrl":"https://topicus11:8443/services/registry","soapEndpointUrl":"https://topicus11:8443/services/registry","systemName":"OTHER_topicus_2"}],
            repos: [{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.143","soapEndpointRetrieveUrl":"https://nist1:9085//tf6/services/xdsrepositoryb","soapEndpointUrl":"https://nist1:9085/tf6//services/xdsrepositoryb","systemName":"OTHER_NIST_RED_2014"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.113","soapEndpointRetrieveUrl":"https://agfa32:8443/xds/repository","soapEndpointUrl":"https://agfa32:8443/xds/repository","systemName":"PACS_AGFA_EE2"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.145","soapEndpointRetrieveUrl":"https://osvb1:9099/ihe/xds-iti43","soapEndpointUrl":"https://osvb1:9099/ihe/xds-iti41","systemName":"XDSb_REG_OSVB"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.129","soapEndpointRetrieveUrl":"https://meddex0:8443/MGXDS/DocumentRepository_Service","soapEndpointUrl":"https://meddex0:8443/MGXDS/DocumentRepository_Service","systemName":"XDSab_REP_Meddex"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.137","soapEndpointRetrieveUrl":"https://rvc6/XDSRepositoryRetrieve/DocumentRepositoryService.svc","soapEndpointUrl":"https://rvc6/XDSRepositoryProvide/XDSbRepositoryService.svc","systemName":"EHR_RVC_3"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.141","soapEndpointRetrieveUrl":"https://nist18:9085/tf6/services/xdsrepositoryb","soapEndpointUrl":"https://nist18:9085/tf6/services/xdsrepositoryb","systemName":"OTHER_NIST_BLUE_2014"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.115","soapEndpointRetrieveUrl":"https://tie4:7002/tieservice/xdsrepositoryb","soapEndpointUrl":"https://tie4:7002/tieservice/xdsrepositoryb","systemName":"OTHER_TIE"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.131","soapEndpointRetrieveUrl":"https://ith-icoserve16:5043/Repository/services/RepositoryService","soapEndpointUrl":"https://ith-icoserve16:5043/Repository/services/RepositoryService","systemName":"EHR_ITH-ICOSERVE_sense"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.132","soapEndpointRetrieveUrl":"https://rogan24:2040/XDS/Repository","soapEndpointUrl":"https://rogan24:2040/XDS/Repository","systemName":"EHR_Rogan_2"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.118","soapEndpointRetrieveUrl":"https://acuo6:17026/XDS/Repository","soapEndpointUrl":"https://acuo6:17026/XDS/Repository","systemName":"XDSb_REP_Acuo_6.0.1"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.136","soapEndpointRetrieveUrl":"https://agfa36:9443/g5-xds-ws/g5-xds-ws","soapEndpointUrl":"https://agfa36:9443/g5-xds-ws/g5-xds-ws","systemName":"OTHER_AGFA"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.117","soapEndpointRetrieveUrl":"https://topicus11:9443/services/repository","soapEndpointUrl":"https://topicus11:9443/services/repository","systemName":"OTHER_topicus_2"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.125","soapEndpointRetrieveUrl":"https://ge72:8080/ea-service/XdsRepository","soapEndpointUrl":"https://ge72:8080/ea-service/XdsRepository","systemName":"OTHER_GE_EA 2014"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.144","soapEndpointRetrieveUrl":"https://dedalus29/repo","soapEndpointUrl":"https://dedalus29/repo","systemName":"EHR_DEDALUS_2014"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.111","soapEndpointRetrieveUrl":"https://visbion12:8081/MyRepository","soapEndpointUrl":"https://visbion12:8081/MyRepository","systemName":"PACS_Visbion_IPACS_414"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.130","soapEndpointRetrieveUrl":"https://etiam2:8080/your_url","soapEndpointUrl":"https://etiam2:8080/your_url","systemName":"OTHER_ETIAM_NEXUS"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.122","soapEndpointRetrieveUrl":"https://solinfo2:8080/your_url","soapEndpointUrl":"https://solinfo2:8443/EuleroXDSRepository/repositoryb","systemName":"EHR_SOLINFO_CARTESIO"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.146","soapEndpointRetrieveUrl":"https://gpi3:8643/DocumentRepositoryXDSBWS/DocumentRepositoryXDSB","soapEndpointUrl":"https://gpi3:8643/DocumentRepositoryXDSBWS/DocumentRepositoryXDSB","systemName":"XDSb_REP_GPI"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.114","soapEndpointRetrieveUrl":"https://synedra7:8443/xds/documentrepository","soapEndpointUrl":"https://synedra7:8443/xds/documentrepository","systemName":"PACS_synedra__2014"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.120","soapEndpointRetrieveUrl":"https://arts8:8446/Repository","soapEndpointUrl":"https://arts8:8446/Repository","systemName":"OTHER_ARTS_4_0"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.123","soapEndpointRetrieveUrl":"https://forcare14:8443/store/services/repository","soapEndpointUrl":"https://forcare14:8443/store/services/repository","systemName":"XDSab_REG_Forcare_2"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.138","soapEndpointRetrieveUrl":"https://ideosante0:8443/xdsbRepository","soapEndpointUrl":"https://ideosante0:8443/xdsbRepository","systemName":"EHR_IdÃ©oSantÃ©"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.147","soapEndpointRetrieveUrl":"https://bint1:460/Services/IHE/Repository/DocumentRepository.svc","soapEndpointUrl":"https://bint1:460/Services/IHE/Repository/DocumentRepository.svc","systemName":"OTHER_BINT"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.124","soapEndpointRetrieveUrl":"https://visus24/JiveX_XDSRepository_WS/service","soapEndpointUrl":"https://visus24/JiveX_XDSRepository_WS/service","systemName":"XDSb_REP_VISUS_2014"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.121","soapEndpointRetrieveUrl":"https://intersystems3/csp/repository/HS.IHE.XDSb.Repository.Services.cls","soapEndpointUrl":"https://intersystems3/csp/repository/HS.IHE.XDSb.Repository.Services.cls","systemName":"GATEWAY_InterSystems_HealthShare"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.140","soapEndpointRetrieveUrl":"https://nist17:9085/tf6/services/xdsrepositoryb","soapEndpointUrl":"https://nist17:9085/tf6/services/xdsrepositoryb","systemName":"OTHER_NIST_GREEN_2014"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.127","soapEndpointRetrieveUrl":"https://icw16:8443/pxs/webservices/xdsb-retrievedocuments","soapEndpointUrl":"https://icw16:8443/pxs/webservices/rev6/xdsb-provideandregister","systemName":"EHR_ICW_1"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.110","soapEndpointRetrieveUrl":"https://tiani---cisco76:8443/XDS3/C1","soapEndpointUrl":"https://tiani---cisco76:8443/XDS3/C1","systemName":"EHR_Tiani - Cisco_SpiritEHR"},{"enableRequestCheck":true,"repositoryUid":"1.3.6.1.4.1.21367.2011.2.3.133","soapEndpointRetrieveUrl":"https://xt2:8021/axis2/services/xdsrepositoryb","soapEndpointUrl":"https://xt2:8021/axis2/services/xdsrepositoryb","systemName":"EHR_xt_HIE"}],
            respGWs: [{"homeCommunityID":"urn:oid:1.3.6.1.4.1.21367.2011.2.6.58","queryURL":"https://nist18:9085/tf6/services/rg","retrieveURL":"https://nist18:9085/tf6/services/rg","systemName":"OTHER_NIST_BLUE_2014"},{"homeCommunityID":"urn:oid:1.3.6.1.4.1.21367.2011.2.6.59","queryURL":"https://nist1:9085/tf6/services/rg","retrieveURL":"https://nist1:9085/tf6/services/rg","systemName":"OTHER_NIST_RED_2014"},{"homeCommunityID":"urn:oid:1.3.6.1.4.1.21367.2011.2.6.51","queryURL":"https://forcare14:8443/xca/services/respondingGateway/query","retrieveURL":"https://forcare14:8443/xca/services/respondingGateway/retrieve","systemName":"XDSab_REG_Forcare_2"},{"homeCommunityID":"urn:oid:1.3.6.1.4.1.21367.2011.2.6.56","queryURL":"https://ideosante0:8443/dcr-RespondingGateway/CrossGatewayStoredQuery","retrieveURL":"https://ideosante0:8443/dcr-RespondingGateway/CrossGatewayRetrieve","systemName":"EHR_IdéoSanté"},{"homeCommunityID":"urn:oid:1.3.6.1.4.1.21367.2011.2.6.55","queryURL":"https://bint1:444/csp/healthshare/bintmed/HS.IHE.XCA.RespondingGateway.Services.cls","retrieveURL":"https://bint1:444/csp/healthshare/bintmed/HS.IHE.XCA.RespondingGateway.Services.cls","systemName":"OTHER_BINT"},{"homeCommunityID":"urn:oid:1.3.6.1.4.1.21367.2011.2.6.47","queryURL":"https://agfa32:8443/xca/RespondingGW","retrieveURL":"https://agfa32:8443/xca/RespondingGW","systemName":"PACS_AGFA_EE2"},{"homeCommunityID":"urn:oid:1.3.6.1.4.1.21367.2011.2.6.50","queryURL":"https://intersystems3/csp/connect/HS.IHE.XCA.RespondingGateway.Services.cls","retrieveURL":"https://intersystems3/csp/connect/HS.IHE.XCA.RespondingGateway.Services.cls","systemName":"GATEWAY_InterSystems_HealthShare"},{"homeCommunityID":"urn:oid:1.3.6.1.4.1.21367.2011.2.6.57","queryURL":"https://nist17:9085/tf6/services/rg","retrieveURL":"https://nist17:9085/tf6/services/rg","systemName":"OTHER_NIST_GREEN_2014"},{"homeCommunityID":"urn:oid:1.3.6.1.4.1.21367.2011.2.6.60","queryURL":"https://meddex0:8443/MGXDS/Gateway_Service","retrieveURL":"https://meddex0:8443/MGXDS/Gateway_Service","systemName":"XDSab_REP_Meddex"},{"homeCommunityID":"urn:oid:1.3.6.1.4.1.21367.2011.2.6.52","queryURL":"https://icw15:8443/pxs/webservices/iti38Gateway","retrieveURL":"https://icw15:8443/pxs/webservices/iti39Gateway","systemName":"EHR_ICW_1"},{"homeCommunityID":"urn:oid:1.3.6.1.4.1.21367.2011.2.6.54","queryURL":"https://gpi1:8643/DocumentRepositoryXDSBWS/RespondingGatewayXDSB","retrieveURL":"https://gpi1:8643/DocumentRepositoryXDSBWS/RespondingGatewayXDSB","systemName":"XDSb_REP_GPI"},{"homeCommunityID":"urn:oid:1.3.6.1.4.1.21367.2011.2.6.46","queryURL":"https://tiani---cisco81:8443/XCA/responding","retrieveURL":"https://tiani---cisco81:8443/XCA/responding","systemName":"EHR_Tiani - Cisco_SpiritEHR"}],

            // endof connectathon ///////////////////


            makeAddExtensionDropDown: function (nodestr, nodeClass) {
                var map = _.map(conf.schemas.extensions[nodeClass], function (value, key) {
                    return {
                        "text": key,
                        "click": "ConfigEditorService.addExtension(" + nodestr + ",'" + key + "','" + nodeClass + "')"
                    };
                });
                return map;
            },

            /**
             *
             * @param extName
             * @param extType
             *          aeExtensions
             *          hl7AppExtensions
             *          deviceExtensions
             */
            addExtension: function (node, extName, nodeClass) {
                var extensionsProperty = conf.extensionsPropertyForClass[nodeClass];
                if (!node[extensionsProperty])
                    node[extensionsProperty] = {};
                node[extensionsProperty][extName] = conf.createNewItem(conf.schemas.extensions[nodeClass][extName]);
                conf.checkModified();
            },
            removeExtension: function (node, extName, nodeClass) {
                delete node[conf.extensionsPropertyForClass[nodeClass]][extName];
                conf.checkModified();
            },
            classHasExtensions: function (schema) {
                return _.has(conf.extensionsPropertyForClass, schema.class);
            },


            // initializes things
            load: function (callback) {
                appHttp.get("data/config/devices", null, function (data) {
                    conf.devices = data;

                    conf.deviceRefs = _.map(conf.devices, function (device) {
                        return {
                            name: device.deviceName,
                            ref: "//*[_.uuid='" + device.deviceUuid + "']"
                        }
                    });

                    conf.aeRefs = _.chain(conf.devices)
                        .pluck('appEntities')
                        .flatten()
                        .map(function (ae) {
                            return {
                                name: ae.name,
                                ref: "//*[_.uuid='" + ae.uuid + "']"
                            }
                        })
                        .value();

                    appHttp.get("data/config/schemas?class=com.agfa.mma.config.MMAConfigurationRoot", null, function (data) {
                        conf.schemas = data;
                        callback();
                    }, function (data, status) {
                        appNotifications.showNotification({
                            level: "danger",
                            text: "Could not load the configuration schemas",
                            details: [data, status]
                        });
                        callback();
                    });


                }, function (data, status) {
                    appNotifications.showNotification({
                        level: "danger",
                        text: "Could not load the list of devices",
                        details: [data, status]
                    });
                    callback();
                });
            },

            hasType: hasType,


            // returns a properly editable object for specified schema
            createNewItem: function (schema) {
                var createNewBasicItem = function (schema) {
                    if (schema.default != null)
                        return schema.default;

                    if (schema.type == "boolean") return false;
                    if (schema.type == "string") return null;
                    if (schema.type == "integer") return 0;
                    if (hasType(schema, "enum")) return null;
                    if (schema.type == "array") return [];
                    if (schema.class == "Map") return {};
                };

                if (schema.type != "object")
                    return createNewBasicItem(schema);

                // then it's an object
                var df = schema.distinguishingField ? schema.distinguishingField : 'cn';
                var obj = {};

                angular.forEach(schema.properties, function (value, index) {
                    if (index == df) obj[index] = "new";
                    else
                        obj[index] = createNewBasicItem(value);
                });

                return obj;
            },

            checkModified: function () {

                modifiedChecksTriggered++;

                var delay = 500;

                $timeout(function () {
                    if (modifiedChecksTriggered == 1) $rootScope.$broadcast('configurationChanged');
                    modifiedChecksTriggered--;
                }, delay);

            }
        };

        return conf;

    });



