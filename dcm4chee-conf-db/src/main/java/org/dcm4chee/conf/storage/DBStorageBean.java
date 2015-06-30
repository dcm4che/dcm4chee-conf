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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.codehaus.jackson.map.ObjectMapper;
import org.dcm4che3.conf.core.util.SimpleConfigNodeUtil;

/**
 * @author Roman K
 */
@Stateless
public class DBStorageBean {

    private static final ObjectMapper OM = new ObjectMapper();

    @PersistenceContext(unitName = "dcm4chee-conf")
    private EntityManager em;

    public static final String LOCK_PATH = "/misc/locking/dblock";

    @EJB
    DBStorageBean self;


    public Map<String, Object> getFullTree() {
        Query query = em.createQuery("SELECT n FROM ConfigNodeEntity n");
        List<ConfigNodeEntity> resultList = query.getResultList();

        Map<String, Object> map = new HashMap<String, Object>();

        for (ConfigNodeEntity configNodeEntity : resultList) {
            Map loadedNode = configNodeEntity.getContent() == null ?
                    new HashMap() :
                    fromBytes(configNodeEntity.getContent());
            String path = configNodeEntity.getPath();
            map = SimpleConfigNodeUtil.replaceNode(map, loadedNode, SimpleConfigNodeUtil.fromSimpleEscapedPath(path));
        }

        return map;
    }

    public boolean isEmpty() {
        Query query = em.createQuery("SELECT count (n) FROM ConfigNodeEntity n");
        Long count = (Long) query.getSingleResult();
        return count == 0;
    }

    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    public void lock() {

        Query query = em.createQuery("SELECT count (n) FROM ConfigNodeEntity n WHERE n.path=?1");
        query.setParameter(1, LOCK_PATH);
        Long count = (Long) query.getSingleResult();

        // create locking row in a separate transaction to make sure constraint violations don't rollback the current one
        if (count == 0) try {
            self.createLockingRow();
        } catch (Exception e) {
            // noop
        }

        getLockOnExistingLockEntity();
    }

    private void getLockOnExistingLockEntity() {
        Query query = em.createQuery("SELECT n FROM ConfigNodeEntity n WHERE n.path=?1");
        query.setParameter(1, LOCK_PATH);
        ConfigNodeEntity singleResult = (ConfigNodeEntity) query.getSingleResult();
        em.lock(singleResult, LockModeType.PESSIMISTIC_WRITE);

        // write
        /*byte[] bytes = new byte[10];
        new Random().nextBytes(bytes);
        singleResult.setContent(bytes);
        em.merge(singleResult);*/
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void createLockingRow() {
        ConfigNodeEntity entity = new ConfigNodeEntity();
        entity.setPath(LOCK_PATH);
        em.persist(entity);
    }


    public Object getNode(List<String> pathItemsForDB) {
        ConfigNodeEntity node = getConfigNodeEntityForDBPath(pathItemsForDB);
        Object loadedNode = node.getContent() == null ?
                new HashMap() :
                fromBytes(node.getContent());

        return loadedNode;
    }


    public void removeNode(List<String> pathItemsForDB, List<String> restPathItems) {

        if (restPathItems.size() == 0) {

            Query query;

            // if less than level, allow bulk delete of x/y/*
            if (pathItemsForDB.size() < SemiSerializedDBConfigStorage.level) {
                query = em.createQuery("DELETE FROM ConfigNodeEntity n WHERE n.path like ?1");
                query.setParameter(1, SimpleConfigNodeUtil.toSimpleEscapedPath(pathItemsForDB) + "%" );
            }
            // otherwise must be equals
            else {
                query = em.createQuery("DELETE FROM ConfigNodeEntity n WHERE n.path=?1");
                query.setParameter(1, SimpleConfigNodeUtil.toSimpleEscapedPath(pathItemsForDB));
            }
            query.executeUpdate();
        } else
            try {
                ConfigNodeEntity node = getConfigNodeEntityForDBPath(pathItemsForDB);
                Map<String, Object> map = fromBytes(node.getContent());
                SimpleConfigNodeUtil.removeNode(map, restPathItems);
                node.setContent(toBytes(map));
                em.merge(node);
            } catch (NoResultException e) {
                return;
            }
    }

    public void modifyNode(List<String> pathItemsForDB, List<String> restPathItems, Map<String, Object> configNode) {
        String dbPath = SimpleConfigNodeUtil.toSimpleEscapedPath(pathItemsForDB);

        ConfigNodeEntity node;
        try {
            node = getConfigNodeEntityForDBPath(pathItemsForDB);
        } catch (NoResultException e) {

            // its ok, create new
            node = new ConfigNodeEntity();
            node.setPath(dbPath);
            Map<String, Object> map = SimpleConfigNodeUtil.replaceNode(new HashMap<String, Object>(), configNode, restPathItems);
            node.setContent(toBytes(map));
            em.persist(node);

            return;
        } catch (Exception e) {
            throw new RuntimeException("Cannot persist configuration node @ " + dbPath, e);
        }

        // merge
        Map<String, Object> map = fromBytes(node.getContent());
        map = SimpleConfigNodeUtil.replaceNode(map, configNode, restPathItems);
        node.setContent(toBytes(map));
        em.merge(node);
    }

    public boolean nodeExists(List<String> pathItemsForDB, List<String> restPathItems) {
        try {
            ConfigNodeEntity node = getConfigNodeEntityForDBPath(pathItemsForDB);
            return SimpleConfigNodeUtil.nodeExists(fromBytes(node.getContent()), restPathItems);
        } catch (NoResultException e) {
            return false;
        }
    }

    private Map<String, Object> fromBytes(byte[] content) {
        try {
            return OM.readValue(content, Map.class);
        } catch (IOException e) {
            throw new RuntimeException("Cannot deserialize node", e);
        }
    }

    private byte[] toBytes(Map<String, Object> map) {
        try {
            return OM.writeValueAsBytes(map);
        } catch (IOException e) {
            throw new RuntimeException("Cannot serialize node", e);
        }
    }

    private ConfigNodeEntity getConfigNodeEntityForDBPath(List<String> pathItemsForDB) {
        String dbPath = SimpleConfigNodeUtil.toSimpleEscapedPath(pathItemsForDB);

        Query query = em.createQuery("SELECT n FROM ConfigNodeEntity n WHERE n.path=?1");
        query.setParameter(1, dbPath);

        return (ConfigNodeEntity) query.getSingleResult();
    }
}
