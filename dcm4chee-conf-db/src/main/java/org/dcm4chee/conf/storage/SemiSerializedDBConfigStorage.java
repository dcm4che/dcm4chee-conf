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

import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.dcm4che3.conf.core.util.ConfigNodeUtil;
import org.dcm4che3.conf.core.util.SplittedPath;
import org.dcm4che3.conf.dicom.DicomPath;

import javax.ejb.EJB;
import javax.enterprise.context.ApplicationScoped;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Roman K
 */
@ApplicationScoped
public class SemiSerializedDBConfigStorage implements Configuration {

    /**
     * The level from which on all the nodes are serialized
     */
    private static final int level = 3;

    @EJB
    DBStorageBean db;

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
        return ConfigNodeUtil.getNode(db.getFullTree(), path);
    }

    @Override
    public Class getConfigurationNodeClass(String path) throws ConfigurationException, ClassNotFoundException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean nodeExists(String path) throws ConfigurationException {
        if (path.equals(DicomPath.ConfigRoot.path()))
            return !db.isEmpty();

        SplittedPath splittedPath = new SplittedPath(path, level).calc();

        // no need to store those
        if (splittedPath.getTotalDepth() <= level) throw new RuntimeException("Unexpected path "+path);

        return db.nodeExists(splittedPath.getOuterPathItems(), splittedPath.getInnerPathitems());

    }

    @Override
    public void persistNode(String path, Map<String, Object> configNode, Class configurableClass) throws ConfigurationException {

        SplittedPath splittedPath = new SplittedPath(path, level).calc();
        int i = splittedPath.getTotalDepth();
        List<String> pathItemsForDB = splittedPath.getOuterPathItems();
        List<String> restPathItems = splittedPath.getInnerPathitems();

        // no need to store those
        if (i <= level) return;

        db.modifyNode(pathItemsForDB, restPathItems, configNode);

    }

    @Override
    public void refreshNode(String path) throws ConfigurationException {
        //noop
    }

    @Override
    public void removeNode(String path) throws ConfigurationException {
        SplittedPath splittedPath = new SplittedPath(path, level).calc();

        if (splittedPath.getTotalDepth() <= level) throw new RuntimeException("Unable to delete node "+path);

        db.removeNode(splittedPath.getOuterPathItems(), splittedPath.getInnerPathitems());
    }

    @Override
    public Iterator search(String liteXPathExpression) throws IllegalArgumentException, ConfigurationException {
        throw new RuntimeException("Not implemented");
    }

}
