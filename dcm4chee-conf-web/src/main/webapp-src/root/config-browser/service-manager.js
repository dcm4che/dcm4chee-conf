angular.module('dcm4che.config.manager', ['dcm4che.appCommon', 'dcm4che.config.core']
).controller('ServiceManagerCtrl', function ($scope, $timeout, $confirm, appHttp, appNotifications, ConfigEditorService) {

        // modification tracking
        var modifiedChecksTriggered = 0;

        $scope.switchToAdvancedView = function () {
            $scope.advancedView = true;
        };

        function checkModified() {
            $scope.selectedDevice.isModified = !angular.equals($scope.selectedDevice.lastPersistedConfig, $scope.selectedDevice.config);
        }

        $scope.$on('configurationChanged', function () {
            modifiedChecksTriggered++;

            var delay = 500;

            $timeout(function () {
                if (modifiedChecksTriggered == 1) checkModified();
                modifiedChecksTriggered--;
            }, delay);
        });


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
                device.lastPersistedConfig = configToSave;

                checkModified();

                appNotifications.showNotification({
                    level: "success",
                    text: "Configuration successfully saved",
                    details: [data, status]
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

        // load devicelist
        ConfigEditorService.load(function () {
            $scope.devices = ConfigEditorService.devices;
            $scope.deviceNames = _.pluck($scope.devices, 'deviceName');

            if ($scope.devices.length > 0)
                $scope.selectedDeviceName = $scope.devices[0].deviceName;

            $scope.addDeviceExtDropdown = ConfigEditorService.makeAddExtensionDropDown('selectedDevice.config', 'deviceExtensions');
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

    }
).controller('DeviceEditorController', function ($scope, appHttp, appNotifications, ConfigEditorService) {
        $scope.ConfigEditorService = ConfigEditorService;
        $scope.editor = {
            options: null
        };
        $scope.$watchCollection('selectedDevice.config.dicomConnection', function () {
            if ($scope.selectedDevice && $scope.selectedDevice.config)
                $scope.editor.connectionRefs = $scope.selectedDevice ? _.map($scope.selectedDevice.config.dicomConnection, function (connection) {
                    return {
                        name: connection.cn + "," + connection.dcmProtocol + "(" + connection.dicomHostname + ":" + connection.dicomPort + ")",
                        ref: "/dicomConfigurationRoot/dicomDevicesRoot/*[dicomDeviceName='" + $scope.selectedDevice.config.dicomDeviceName.replace("'", "&apos;") + "']/dicomConnection[cn='" + connection.cn.replace("'", "&apos;") + "']"
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
    }
).controller('DeleteTopLevelElementController', function ($scope, $confirm, ConfigEditorService) {

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
    }
)
// 'global' service
    .factory("ConfigEditorService", function ($rootScope, appNotifications, appHttp) {

        var hasType = function (schema, type) {
            if (!schema) return false;

            if (_.isArray(schema.type))
                return _.contains(schema.type, type);
            else
                return schema.type == type;
        };

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

            schemas: {},

            makeAddExtensionDropDown: function (nodestr, extType) {
                var map = _.map(conf.schemas[extType], function (value, key) {
                    return {
                        "text": key,
                        "click": "ConfigEditorService.addExtension(" + nodestr + ",'" + key + "','" + extType + "')"
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
            addExtension: function (node, extName, extType) {
                if (!node[extType])
                    node[extType] = {};
                node[extType][extName] = conf.createNewItem(conf.schemas[extType][extName]);
                conf.checkModified();
            },
            removeExtension: function (node, extName, extType) {
                delete node[extType][extName];
                conf.checkModified();
            },


            // initializes things
            load: function (callback) {
                appHttp.get("data/config/devices", null, function (data) {
                    conf.devices = data;

                    conf.deviceRefs = _.map(conf.devices, function (device) {
                        return {
                            name: device.deviceName,
                            ref: "/dicomConfigurationRoot/dicomDevicesRoot/*[dicomDeviceName='" + device.deviceName.replace("'", "&apos;") + "']"
                        }
                    });

                    appHttp.get("data/config/schemas", null, function (data) {
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
                $rootScope.$broadcast('configurationChanged');
            }
        };

        return conf;

    });



