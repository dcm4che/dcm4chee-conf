angular.module('dcm4che.config.core', [])
    .directive("confignode", function (RecursionHelper, ConfigEditorService, $filter) {
        var cache = {};
        return {
            scope: {
                editor: '=',
                confignode: '=',
                schema: '=',
                options: '=',
                parentnode: '=',
                index: '=',
                noLabel: '@'
            },
            controller: function ($scope) {

                $scope.hasType = ConfigEditorService.hasType;

                $scope.ConfigEditorService = ConfigEditorService;

                $scope.getLabel = function (node, schema) {

                    if (schema.properties.cn != null)
                        return node.cn;
                    else if (schema.properties.systemName != null)
                        return node.systemName;
                    else if (schema.properties.affinityDomainName != null)
                        return node.affinityDomainName;
                    else if (schema.properties.homeCommunityID != null)
                        return node.homeCommunityID;
                    else
                        return $filter('limitTo')(angular.toJson(node), 20);
                };

                $scope.toggleShowAllProps = function () {
                    !$scope.doShowAllProps ? $scope.doShowAllProps = true : $scope.doShowAllProps = false;
                };


                $scope.$watch("schema", function () {

                    $scope.doShowAllProps = true;
                    $scope.isShowAllTogglable = false;

                    // determine if there are any primary props
                    if ($scope.schema != null)
                        angular.forEach($scope.schema.properties, function (value) {
                            if (_.contains(value.tags, "PRIMARY")) {
                                $scope.doShowAllProps = false;
                                $scope.isShowAllTogglable = true;
                            }
                        });

                    // generate tooltip
                    if ($scope.schema == null)
                        $scope.propertyTooltip = null;
                    else
                        $scope.propertyTooltip = {
                            title: ($scope.schema.description ? $scope.schema.description + "<br/>" : "") +
                            ($scope.schema.default ? "Default: <strong>" + $scope.schema.default + "</strong>" : "")

                        };

                    // enums
                    if ($scope.hasType($scope.schema, "enum")) {

                        $scope.enums = _.map($scope.schema.enum, function (val, ind) {
                            return {
                                value: val,
                                label: $scope.schema.enumRepresentation == 'ORDINAL' ? $scope.schema.enumStrValues[ind] : val
                            };
                        });

                        if ($scope.hasType($scope.schema, "null")) {
                            $scope.enums.unshift({
                                value: null,
                                label: "- none -"
                            });
                        }
                    }

                });


            },
            templateUrl: "config-browser/config-attributes.html",
            compile: function (element) {
                // Use the compile function from the RecursionHelper,
                // And return the linking function(s) which it returns
                return RecursionHelper.compile(element, undefined, cache);

            }
        };
    })
    .controller("CompositeNodeController", function ($scope, ConfigEditorService) {
        $scope.$watch("schema", function () {

            $scope.groups = null;

            if ($scope.schema) {
                $scope.groups = _.chain($scope.schema.properties)
                    .map(function (prop, name) {
                        if ($scope.options && $scope.options.excludeProps && _.contains($scope.options.excludeProps, name)) return null;
                        if (prop.uiGroup) return prop.uiGroup; else return null;
                    })
                    .uniq().without(null).value();
            }

            $scope.$watch('confignode', function () {
                $scope.addExtDropdown = ConfigEditorService.makeAddExtensionDropDown('confignode', $scope.schema.class);
            });

        });

        $scope.resetItem = function(item) {
            $scope.selectedItemConfig = item;
        }




    })
    .controller("CollectionController", function ($rootScope, $scope, $timeout, ConfigEditorService, appNotifications) {

        $scope.resetRepos = function () {
            $scope.parentnode[$scope.index] = angular.copy(ConfigEditorService.repos);
        };
        $scope.resetRGWs = function () {
            $scope.parentnode[$scope.index] = angular.copy(ConfigEditorService.respGWs);
        };


        $scope.$watch('confignode', function () {
            $scope.selectedItemConfig = null;
            $scope.selectedItemIndex = null;
            $scope.editedIndex = null;
        });


        $scope.deleteArrayItem = function (key) {
            $scope.confignode.splice(key, 1);

            // reset the current index if an item is selected
            if ($scope.selectedItemConfig) {
                var newInd = _.indexOf($scope.confignode, $scope.selectedItemConfig);
                if (newInd == -1) {
                    $scope.selectedItemConfig = null;
                    $scope.selectedItemIndex = null;
                } else {
                    $scope.selectedItemIndex = newInd;
                }
            }

            ConfigEditorService.checkModified();
        };

        $scope.deleteMapEntry = function (key) {
            delete $scope.confignode[key];

            if (key == $scope.selectedItemIndex) {
                $scope.selectedItemConfig = null;
                $scope.selectedItemIndex = null;
            }

            ConfigEditorService.checkModified();
        };


        $scope.selectItem = function (key, item) {

            // if selected something else, disable editing
            if ($scope.selectedItemIndex != key) {
                $scope.editedIndex = null;
            }

            // if already selected, go into edit mode
            if ($scope.selectedItemIndex == key) {
                $scope.editedIndex = key;
                // send event to focus on the field
                $timeout(function () {
                    $rootScope.$broadcast('focusOn', key);
                });
            }

            $scope.selectedItemConfig = item;
            $scope.selectedItemIndex = key;
        };

        $scope.isCollectionEmpty = function () {
            return _.isEmpty($scope.confignode);
        };


        $scope.addMapEntryOrArrayItem = function () {
            if ($scope.schema.class == 'Map') {
                $scope.confignode['new'] = ConfigEditorService.createNewItem($scope.schema.properties['*']);

                $scope.editedIndex = 'new';

                // send event to focus on the field
                $timeout(function () {
                    $rootScope.$broadcast('focusOn', 'new');
                });
            } else {
                $scope.confignode.push(ConfigEditorService.createNewItem($scope.schema.items));
            }

            ConfigEditorService.checkModified();
        };


        /**
         * Saves the edited map key
         * @param oldkey
         * @param newkey
         * @returns {boolean} Returns true if the editor should close, false otherwise
         */
        $scope.saveMapKey = function (oldkey, newkey) {

            // check if not empty
            if (newkey == "") {
                appNotifications.showNotification({text: "The key cannot be empty!", level: "warning"});
                return;
            }

            // check if not changed
            if (oldkey == newkey) {
                $scope.editedIndex = null;
                return;
            }

            if (_.has($scope.confignode, newkey)) {
                // if new key already exists - show notification and abort
                appNotifications.showNotification({text: "The key " + newkey + " already exists!", level: "warning"});
                return;
            } else {
                // if not, set new prop, remove old prop
                $scope.confignode[newkey] = $scope.confignode[oldkey];
                delete $scope.confignode[oldkey];

                if ($scope.selectedItemIndex == oldkey)
                    $scope.selectedItemIndex = newkey;

                ConfigEditorService.checkModified();

                $scope.editedIndex = null;
                return;
            }
        };

    })
    .filter("primaryPropsOnly", function () {
        return function (value, showAllProps) {

            if (showAllProps) return value;

            return _.filter(value, function (value) {
                return _.contains(value.tags, "PRIMARY");
            });
        }
    })
    .filter("filterProperties", function () {
        return function (value, options) {

            if (options == null) return value;

            return _.filter(value, function (value) {
                return !_.contains(options.excludeProps, value.$key);
            });
        }
    })
    .filter('toArray', function () {
        return function (obj) {
            if (!(obj instanceof Object)) return obj;
            var map = _.map(obj, function (val, key) {
                return Object.defineProperty(val, '$key', {__proto__: null, value: key});
            });
            return map;
        }
    }).filter('groupSorter', function (ConfigEditorService) {
    return function (groups, options) {
        // force order provided by groupOrder

        var val = _.chain(ConfigEditorService.groupOrder).intersection(groups).union(groups).value();

        if (options && options.showGroups)
            val = _.intersection(val, options.showGroups);

        if (options && options.hideGroups)
            val = _.difference(val, options.hideGroups);

        return val;

    };

}).filter('propsSorter', function (ConfigEditorService) {
    return function (props, options) {

        // TODO: make compatible, filter system _hash things to top etc

        return _.sortBy(props,
            function (prop) {
                if (prop.class == 'Map' && prop.properties['*'].type != 'object') {
                    return "2" + prop['$key'];
                } else if (prop.type == 'object' && prop.class == 'Map') {
                    return "5" + prop['$key'];
                } else if (prop.type == 'object') {
                    return "3" + prop['$key'];
                } else if (prop.type == 'array' && prop.items.type == 'object') {
                    return "4" + prop['$key'];
                } else if (prop.type == 'array') {
                    return "2" + prop['$key'];
                } else if (prop['$key'] == '_.uuid' || prop['$key'] == '_.hash') {
                    return "1" + prop['$key'];
                } else
                    return "2" + prop['$key'];

            });

    };

});

