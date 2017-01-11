/*
 * *** BEGIN LICENSE BLOCK *****
 *  Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  Agfa Healthcare.
 *  Portions created by the Initial Developer are Copyright (C) 2015
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 *  ***** END LICENSE BLOCK *****
 */

package org.dcm4chee.conf.upgrade;


import org.dcm4che3.conf.api.internal.DicomConfigurationManager;
import org.dcm4che3.conf.api.upgrade.*;
import org.dcm4che3.conf.core.api.ConfigurationUpgradeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Roman K
 */
@SuppressWarnings("unchecked")
public class UpgradeRunner {

    private static Logger log = LoggerFactory.getLogger(UpgradeRunner.class);

    private Collection<UpgradeScript> availableUpgradeScripts;
    private DicomConfigurationManager dicomConfigurationManager;
    private UpgradeSettings upgradeSettings;

    public UpgradeRunner(Collection<UpgradeScript> availableUpgradeScripts, DicomConfigurationManager dicomConfigurationManager, UpgradeSettings upgradeSettings) {
        this.availableUpgradeScripts = availableUpgradeScripts;
        this.dicomConfigurationManager = dicomConfigurationManager;
        this.upgradeSettings = upgradeSettings;
    }

    public void upgrade() {
        dicomConfigurationManager.runBatch(() -> {
            try {
                final String toVersion = upgradeSettings.getUpgradeToVersion();

                ConfigurationMetadata configMetadata = loadOrInitConfigMetadata();

                if (configMetadata.getVersion() == null)
                    configMetadata.setVersion(UpgradeScript.NO_VERSION);

                String fromVersion = configMetadata.getVersion();

                log.info("Dcm4che configuration init: upgrading configuration from version '{}' to version '{}'", fromVersion, toVersion);

                log.info("Config upgrade scripts specified in settings: {}", upgradeSettings.getUpgradeScriptsToRun());
                log.info("Config upgrade scripts discovered in the deployment: {}", availableUpgradeScripts);

                SortedSet<UpgradeStep> upgradeSteps = collectUpgradeSteps(configMetadata);

                for (UpgradeStep upgradeStep : upgradeSteps) {
                    try {
                        log.info(upgradeStep.label);
                        upgradeStep.action.run();
                    } catch (RuntimeException e) {
                        throw new ConfigurationUpgradeException("Error while running upgrade step " + upgradeStep.label, e);
                    }
                }

                // update last executed version for all enabled scripts
                getOrderedScriptsToRun().forEach((s) -> {
                    getUpgradeScriptMetadata(configMetadata, getScriptName(s))
                            .setLastVersionExecuted(getScriptVersion(s).toString());

                });

                // update version
                configMetadata.setVersion(toVersion);

                // persist updated metadata
                dicomConfigurationManager
                        .getTypeSafeConfiguration()
                        .save(DicomConfigurationManager.METADATA_ROOT_PATH, configMetadata, ConfigurationMetadata.class);

            } catch (RuntimeException e) {
                throw new ConfigurationUpgradeException("Error while running the configuration upgrade", e);
            }
        });

        log.info("Configuration upgrade completed successfully");
    }

    private List<UpgradeScript> getOrderedScriptsToRun() {

        return upgradeSettings.getUpgradeScriptsToRun().stream()
                .map((upgradeScriptName) -> {
                            for (UpgradeScript script : availableUpgradeScripts) {
                                if (getScriptName(script).equals(upgradeScriptName)) {
                                    return Optional.of(script);
                                }
                            }

                            if (upgradeSettings.isIgnoreMissingUpgradeScripts()) {
                                log.warn("Missing upgrade script '" + upgradeScriptName + "' ignored! DISABLE 'IgnoreMissingUpgradeScripts' SETTING FOR PRODUCTION ENVIRONMENT.");
                            } else {
                                throw new ConfigurationUpgradeException("Upgrade script '" + upgradeScriptName + "' not found in the deployment");
                            }

                            // return Optional.empty() won't work due to generics...
                            //noinspection ConstantConditions
                            return Optional.ofNullable((UpgradeScript) null);

                        }
                )
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private SortedSet<UpgradeStep> collectUpgradeSteps(ConfigurationMetadata configMetadata) {
        Properties props = new Properties();
        props.putAll(upgradeSettings.getProperties());

        TreeSet<UpgradeStep> upgradeSteps = new TreeSet<>();

        List<UpgradeScript> orderedScriptsToRun = getOrderedScriptsToRun();
        for (int i = 0; i < orderedScriptsToRun.size(); i++) {

            int scriptIndex = i;
            UpgradeScript script = orderedScriptsToRun.get(i);

            // fetch upgradescript metadata
            UpgradeScript.UpgradeScriptMetadata upgradeScriptMetadata = getUpgradeScriptMetadata(configMetadata, getScriptName(script));

            // check if the script need to be executed
            MMPVersion currentScriptVersion = getScriptVersion(script);
            Optional<MMPVersion> lastExecutedVersion = Optional.ofNullable(upgradeScriptMetadata.getLastVersionExecuted())
                    .map((currVer)->{
                        // conditionally map deprecated non-conforming versions...
                        String mappedVersion = upgradeSettings.getDeprecatedVersionsMapping().get(currVer);
                        if (mappedVersion!=null) {
                            return mappedVersion;
                        } else
                            return currVer;
                    })
                    .map(MMPVersion::fromStringVersion);

            if (lastExecutedVersion.isPresent() && lastExecutedVersion.get().compareTo(currentScriptVersion) == 0) {
                log.info("Skipping upgrade script '{}' because current version '{}' is not newer than the last executed one ('{}')",
                        script.getClass().getName(),
                        currentScriptVersion,
                        upgradeScriptMetadata.getLastVersionExecuted());

                continue;

            } else if (lastExecutedVersion.isPresent() && lastExecutedVersion.get().compareTo(currentScriptVersion) > 0) {

                log.warn("Suspicious state - upgrade script '{}' is skipped because current version '{}' is OLDER than the last executed one ('{}')",
                        script.getClass().getName(),
                        currentScriptVersion,
                        upgradeScriptMetadata.getLastVersionExecuted());

                continue;
            }

            log.info("Including upgrade script '{}' (this version '{}', last executed version '{}')",
                    script.getClass().getName(),
                    currentScriptVersion,
                    lastExecutedVersion.orElse(null));

            // collect pieces and prepare context
            Map<String, Object> scriptConfig = (Map<String, Object>) upgradeSettings.getUpgradeConfig().get(getScriptName(script));
            UpgradeScript.UpgradeContext upgradeContext = new UpgradeScript.UpgradeContext(
                    configMetadata.getVersion(),
                    upgradeSettings.getUpgradeToVersion(),
                    props,
                    scriptConfig,
                    dicomConfigurationManager.getConfigurationStorage(),
                    dicomConfigurationManager,
                    upgradeScriptMetadata,
                    configMetadata
            );

            // include the relevant upgrade steps
            if (script instanceof VersionDrivenUpgradeScript) {

                VersionDrivenUpgradeScript versionDrivenUpgradeScript = (VersionDrivenUpgradeScript) script;
                versionDrivenUpgradeScript.init(upgradeContext);

                if (!lastExecutedVersion.isPresent()) {
                    upgradeSteps.add(new UpgradeStep().edit((s) -> {
                        s.action = () -> invokeMethod(versionDrivenUpgradeScript, versionDrivenUpgradeScript.getFirstRunMethod());
                        s.version = currentScriptVersion;
                        s.scriptIndex = scriptIndex;
                        s.label = "Running upgrade script "+getScriptName(script)+" for the first time - invoking method " + versionDrivenUpgradeScript.getFirstRunMethod().getName();
                    }));

                } else {
                    versionDrivenUpgradeScript.getFixUpMethods(lastExecutedVersion.get())
                            .entrySet().stream()
                            .map((e) ->
                                    new UpgradeStep().edit((s) ->
                                    {
                                        s.action = () -> invokeMethod(versionDrivenUpgradeScript, e.getValue());
                                        s.version = e.getKey();
                                        s.scriptIndex = scriptIndex;
                                        s.label="["+getScriptName(script)+"] Invoking fix-up method "+e.getValue().getName()+" (FixUpTo "+e.getKey()+")";
                                    }))
                            .forEach(upgradeSteps::add);
                }

            } else {
                upgradeSteps.add(new UpgradeStep().edit((s) -> {
                    s.action = () -> script.upgrade(upgradeContext);
                    s.version = currentScriptVersion;
                    s.scriptIndex = scriptIndex;
                    s.label = "Invoking upgrade() method of " + getScriptName(script);
                }));
            }
        }
        return upgradeSteps;
    }

    private String getScriptName(UpgradeScript s) {
        return s.getClass().getName();
    }

    private ConfigurationMetadata loadOrInitConfigMetadata() {

        ConfigurationMetadata configMetadata = dicomConfigurationManager
                .getTypeSafeConfiguration()
                .load(DicomConfigurationManager.METADATA_ROOT_PATH, ConfigurationMetadata.class);
        if (configMetadata == null) {
            configMetadata = new ConfigurationMetadata();
        }
        return configMetadata;
    }

    private MMPVersion getScriptVersion(UpgradeScript script) {
        try {

            ScriptVersion scriptVersion = script.getClass().getAnnotation(ScriptVersion.class);

            // mapping of deprecated non-conformant versions...
            String strValue = Optional.ofNullable(scriptVersion).map(ScriptVersion::value).orElse(UpgradeScript.NO_VERSION);
            if (!strValue.isEmpty()) {
                String mappedVersion = upgradeSettings.getDeprecatedVersionsMapping().get(strValue);
                if (mappedVersion!=null) {
                    return MMPVersion.fromStringVersion(mappedVersion);
                }
            }

            return MMPVersion.fromScriptVersionAnno(scriptVersion);
        } catch (IllegalArgumentException e ) {
            throw new RuntimeException("Upgrade script " + script.getClass().getName() + " has an invalid version", e);
        }
    }

    private UpgradeScript.UpgradeScriptMetadata getUpgradeScriptMetadata(ConfigurationMetadata configMetadata, String upgradeScriptName) {
        UpgradeScript.UpgradeScriptMetadata upgradeScriptMetadata = configMetadata.getMetadataOfUpgradeScripts().get(upgradeScriptName);
        if (upgradeScriptMetadata == null) {
            upgradeScriptMetadata = new UpgradeScript.UpgradeScriptMetadata();
            configMetadata.getMetadataOfUpgradeScripts().put(upgradeScriptName, upgradeScriptMetadata);
        }
        return upgradeScriptMetadata;
    }

    private void invokeMethod(Object upgScr, Method m) {
        try {
            m.invoke(upgScr);
        } catch (Exception e) {
            throw new RuntimeException("Cannot invoke a method of an upgrade script " + upgScr.getClass().getName() + " . " + m.getName(), e);
        }
    }

}

