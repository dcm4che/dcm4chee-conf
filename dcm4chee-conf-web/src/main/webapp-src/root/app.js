'use strict';

/* App Module */


var dcm4cheApp = angular.module('dcm4cheApp', [
    'ngRoute',
    'ngSanitize',
    'ngAnimate',
    'mgcrea.ngStrap',

    'dcm4che.appCommon',
    'dcm4che.appCommon.customizations',

    'dcm4che.browserLinkedView',

    'dcm4che.xds.common',
    'dcm4che.xds.controllers',
    'dcm4che.xds.REST',

    'dcm4che.config.manager'

]);

dcm4cheApp.config(
    function ($routeProvider, customizations) {
        $routeProvider.
            when('/step/:stepNum', {
                templateUrl: 'xds-browser/xds-browser.html',
                controller: 'XdsBrowserCtrl'
            }).when('/devices/:device', {
                templateUrl: 'config-browser/devices.html',
                controller: 'ServiceManagerCtrl'
            }).when('/devices', {
                templateUrl: 'config-browser/devices.html',
                controller: 'ServiceManagerCtrl'
            }).when('/xds-config', {
                templateUrl: 'xds-config/xds-config.html',
                controller: 'ServiceManagerCtrl'
            }).when('/raw-editor', {
                templateUrl: 'config-browser/raw-editor.html',
                controller: 'RawConfigEditor'
            }).when('/transfer-capabilities', {
                templateUrl: 'config-browser/transfer-capabilities.html',
                controller: 'TransferCapabilitiesEditor'
            }).when('/metadata', {
                templateUrl: 'config-browser/metadata.html',
                controller: 'MetadataEditor'
            }).when('/versions', {
                templateUrl: 'dcm4che-web-common/versions.html',
                controller: 'VersionsController'
            }).otherwise({
                redirectTo: '/devices'
            });
    });

dcm4cheApp.controller('dcm4cheAppController', function ($scope, appConfiguration) {
    $scope.appConfiguration = appConfiguration;
});