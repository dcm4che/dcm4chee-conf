/*
 * **** BEGIN LICENSE BLOCK *****
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.enterprise.context.ApplicationScoped;

import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.util.ConfigNodeUtil;
import org.dcm4che3.conf.core.util.SplittedPath;
import org.dcm4che3.conf.dicom.DicomPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Roman K
 */
@ApplicationScoped
@ConfigurationStorage(value = "db_blobs")
public class SemiSerializedDBConfigStorage implements Configuration {

    public static final Logger log = LoggerFactory.getLogger(SemiSerializedDBConfigStorage.class);

    /**
     * The level from which on all the nodes are serialized
     */
    public static final int level = 3;

    @EJB
    private DBStorageBean db;
    
    @PostConstruct
    public void init() {
        // create locking row in a separate transaction to make sure constraint violations don't rollback the current one
        try {
            db.createLockingRowIfnotExists();
        } catch (DBStorageBean.UnableToPersistLockingRowException e) {
            if (!db.isLockingRowExists())
                log.error("Unable to init the locking row in the configuration table. ", e);
        }
    }

    @Override
    public Map<String, Object> getConfigurationRoot() throws ConfigurationException {

        Map<String, Object> map = null;
        try {
            map = db.getFullTree();
            return map;
        } catch (Exception e) {
            throw new ConfigurationException("Unable to load configuration from the DB", e);
        }
    }


    @Override
    public Object getConfigurationNode(String path, Class configurableClass) throws ConfigurationException {

        // since some paths are not trivial, e.g. references, just use the root because it will be cached
        // [speedup-spot] still could be optimized for many cases
        return ConfigNodeUtil.getNode(getConfigurationRoot(), path);
    }

    @Override
    public boolean nodeExists(String path) throws ConfigurationException {
        if (path.equals(DicomPath.ConfigRoot.path()))
            return !db.isEmpty();

        SplittedPath splittedPath = new SplittedPath(path, level).calc();

        // no need to store those
        if (splittedPath.getTotalDepth() <= level) throw new RuntimeException("Unexpected path " + path);

        return db.nodeExists(splittedPath.getOuterPathItems(), splittedPath.getInnerPathitems());

    }

    @Override
    public void persistNode(String path, Map<String, Object> configNode, Class configurableClass) throws ConfigurationException {

        SplittedPath splittedPath = new SplittedPath(path, level).calc();
        int i = splittedPath.getTotalDepth();
        List<String> pathItemsForDB = splittedPath.getOuterPathItems();
        List<String> restPathItems = splittedPath.getInnerPathitems();

        if (i <= level) {
            removeNode(path);
            splitTreeAndPersist(pathItemsForDB, configNode);
        } else
            db.modifyNode(pathItemsForDB, restPathItems, configNode);

    }

    private void splitTreeAndPersist(List<String> dbPath, Map<String, Object> configNode) {

        if (dbPath.size() == level) {

            // if reached the level, just modify node
            db.modifyNode(dbPath, new ArrayList<String>(), configNode);
        } else if (dbPath.size() < level) {

            // if not yet reached the level, go deeper for each of this node's children
            for (Map.Entry<String, Object> keyValue : configNode.entrySet()) {
                Object node = keyValue.getValue();

                // skip if null - such 'nodes' are therefore filtered out
                if (node == null) continue;

                // we are still above the serialization level, then this node must be a map
                if (!(node instanceof Map)) throw
                        new IllegalArgumentException("Attempted to persist a node that contains properties in the tree above the serialization level of the storage");

                dbPath.add(keyValue.getKey());
                splitTreeAndPersist(dbPath, (Map<String, Object>) node);
                dbPath.remove(dbPath.size() - 1);
            }
        }
    }

    @Override
    public void refreshNode(String path) throws ConfigurationException {
        //noop
    }

    @Override
    public void removeNode(String path) throws ConfigurationException {
        SplittedPath splittedPath = new SplittedPath(path, level).calc();
        db.removeNode(splittedPath.getOuterPathItems(), splittedPath.getInnerPathitems());
    }

    @Override
    public Iterator search(String liteXPathExpression) throws IllegalArgumentException, ConfigurationException {
        log.warn("Using DB configuration storage without caching. This is not intended usage and will result in very poor performance");
        return ConfigNodeUtil.search(getConfigurationRoot(), liteXPathExpression);
    }

    @Override
    public void lock() {
        db.lock();
    }
    
    @Override
    public void runBatch(ConfigBatch batch) {
        db.runBatch(batch);
    }

}
