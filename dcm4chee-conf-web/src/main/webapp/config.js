angular.module('dcm4che.appCommon.customizations', ['dcm4che.config.xds'])
    .constant('customizations', {

        // This file customizes the app's behavior. Possible values:
        // appName: "XDS administration",
        // customConfigIndexPage: 'xds-config/xds-config.html',
        // xdsDeviceName:'dcm4chee-xds',
        // xdsBrowser: true,
        // logoutEnabled: true,
        // useNICETheme: false

        appName: "Configuration manager",
        logoutEnabled: true,
        useNICETheme: false,
        mainVersionKey: "dcm4chee-conf"

    }
);
