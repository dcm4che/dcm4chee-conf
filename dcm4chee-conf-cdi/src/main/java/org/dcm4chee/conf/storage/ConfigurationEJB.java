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

package org.dcm4chee.conf.storage;

import org.codehaus.jackson.map.ObjectMapper;
import org.dcm4che3.conf.ConfigurationSettingsLoader;
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.util.ConfigNodeUtil;
import org.dcm4chee.conf.cdi.ConfigurationStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.enterprise.inject.Instance;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Singleton
public class ConfigurationEJB implements Configuration {

    public static final Logger log = LoggerFactory.getLogger(ConfigurationEJB.class);

    private Map<String, Object> cachedConfigurationRoot = null;

    @Inject
    private Instance<Configuration> availableConfigStorage;

    private Configuration storage;

    private static class ConfigStorageAnno extends AnnotationLiteral<ConfigurationStorage> implements ConfigurationStorage {

        private static final long serialVersionUID = -142091870920142805L;
        private String value;

        public ConfigStorageAnno(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    @PostConstruct
    public void init() {

        // detect user setting (system property) for config backend type
        String storageType = ConfigurationSettingsLoader.getPropertyWithNotice(
                System.getProperties(),
                Configuration.CONF_STORAGE_SYSTEM_PROP, ConfigStorageType.DB_BLOBS.name()
        );

        // resolve the corresponding implementation
        storage = availableConfigStorage.select(new ConfigStorageAnno(storageType)).get();

    }




    @Override
    public synchronized Map<String, Object> getConfigurationRoot() throws ConfigurationException {

        if (cachedConfigurationRoot == null) {
            cachedConfigurationRoot = storage.getConfigurationRoot();
            log.info("Configuration cache initialized");
        }
        return cachedConfigurationRoot;
    }

    /**
     * Return cached node
     *
     * @param path
     * @param configurableClass
     * @return
     * @throws ConfigurationException
     */
    @Override
    public synchronized Object getConfigurationNode(String path, Class configurableClass) throws ConfigurationException {
        Object node = ConfigNodeUtil.getNode(getConfigurationRoot(), path);

        if (node == null) return null;

        try {
            return deepCloneNode(node);
        } catch (Exception e) {
            throw new ConfigurationException(e);
        }
    }

    @Override
    public Class getConfigurationNodeClass(String path) throws ConfigurationException, ClassNotFoundException {
        return null;
    }

    private Object deepCloneNode(Object node) {
        // clone
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.treeToValue(objectMapper.valueToTree(node), node.getClass());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void persistNode(String path, Map<String, Object> configNode, Class configurableClass) throws ConfigurationException {
        storage.persistNode(path, configNode, configurableClass);
        if (!path.equals("/"))
            ConfigNodeUtil.replaceNode(getConfigurationRoot(), path, configNode);
        else
            cachedConfigurationRoot = configNode;
    }

    @Override
    public synchronized void refreshNode(String path) throws ConfigurationException {
        Object newConfigurationNode = storage.getConfigurationNode(path, null);
        if (path.equals("/"))
            cachedConfigurationRoot = (Map<String, Object>) newConfigurationNode;
        else
            ConfigNodeUtil.replaceNode(getConfigurationRoot(), path, newConfigurationNode);

    }

    @Override
    public synchronized boolean nodeExists(String path) throws ConfigurationException {
        return ConfigNodeUtil.nodeExists(getConfigurationRoot(), path);
    }

    @Override
    public synchronized void removeNode(String path) throws ConfigurationException {
        storage.removeNode(path);
        ConfigNodeUtil.removeNodes(getConfigurationRoot(), path);
    }

    @Override
    public synchronized Iterator search(String liteXPathExpression) throws IllegalArgumentException, ConfigurationException {

        // fully iterate and make copies of all returned results to ensure the consistency and isolation
        List l = new ArrayList();
        final Iterator origIterator = ConfigNodeUtil.search(getConfigurationRoot(), liteXPathExpression);

        while (origIterator.hasNext()) l.add(deepCloneNode(origIterator.next()));

        return l.iterator();
    }

    @Override
    public void lock() {
        storage.lock();
    }

    @Override
    public synchronized void runBatch(ConfigBatch batch) {
        try {
            storage.runBatch(batch);
        }catch (RuntimeException e) {

            // if something goes wrong during batching - invalidate the cache before others are able to read inconsistent data
            // we cannot re-load here since an underlying transaction is likely to be inactive
            cachedConfigurationRoot = null;

            throw e;
        }

    }

}
