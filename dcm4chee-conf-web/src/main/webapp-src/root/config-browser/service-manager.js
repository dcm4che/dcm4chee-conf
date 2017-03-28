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

            registries: [],
            repos:[],
            respGWs: [],
            xdsiSources: [],
            xcaiRGWs: [],

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

                    appHttp.get("/xdsi/connectathon/respGWs", null, function (data) {
                        conf.respGWs = data;
                    }, function (data, status) {
                        appNotifications.showNotification({
                            level: "danger",
                            text: "Could not load resp gws connectathon config",
                            details: [data, status]
                        });
                    });

                    appHttp.get("/xdsi/connectathon/repos", null, function (data) {
                        conf.repos = data;
                    }, function (data, status) {
                        appNotifications.showNotification({
                            level: "danger",
                            text: "Could not load repo connectathon config",
                            details: [data, status]
                        });
                    });

                    appHttp.get("/xdsi/connectathon/registries", null, function (data) {
                        conf.registries = data;
                    }, function (data, status) {
                        appNotifications.showNotification({
                            level: "danger",
                            text: "Could not load registries connectathon config",
                            details: [data, status]
                        });
                    });

                    appHttp.get("/xdsi/connectathon/xcaiRGWs", null, function (data) {
                        conf.xcaiRGWs = data;
                    }, function (data, status) {
                        appNotifications.showNotification({
                            level: "danger",
                            text: "Could not load xcaiRGWs connectathon config",
                            details: [data, status]
                        });
                    });

                    appHttp.get("/xdsi/connectathon/xdsiSources", null, function (data) {
                        conf.xdsiSources = data;
                    }, function (data, status) {
                        appNotifications.showNotification({
                            level: "danger",
                            text: "Could not load xdsiSources connectathon config",
                            details: [data, status]
                        });
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



