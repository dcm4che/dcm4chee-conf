//
/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chee.conf.notif;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Resource;
import javax.decorator.Decorator;
import javax.decorator.Delegate;
import javax.enterprise.inject.Any;
import javax.inject.Inject;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

import org.dcm4che3.conf.core.api.ConfigChangeEvent;
import org.dcm4che3.conf.core.api.ConfigChangeEvent.CONTEXT;
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;

/**
 * Decorator to add support for configuration change notifications to the configuration backend.
 * 
 * @author Alexander Hoermandinger <alexander.hoermandinger@agfa.com>
 */
@Decorator
public abstract class ConfigNotificationDecorator implements Configuration {
    public static final String NOTIFICATIONS_ENABLED_PROPERTY = "org.dcm4che.conf.notifications";
    private final Map<Integer,JtaTransactionConfigChangeContainer> transactionMap = new ConcurrentHashMap<>();

    @Inject @Delegate @Any
    private Configuration delegate;
    
    @Inject
    private ConfigNotificationService configNotifService;
    
    @Resource(lookup="java:jboss/TransactionManager")
    private TransactionManager tmManager;

    @Override
    public void persistNode(String path, Map<String, Object> configNode, Class configurableClass) throws ConfigurationException {
        delegate.persistNode(path, configNode, configurableClass);
        recordConfigChange(path);
    }

    @Override
    public void removeNode(String path) throws ConfigurationException {
        delegate.removeNode(path);
        recordConfigChange(path);
    }

    @Override
    public void lock() {
        delegate.lock();
    }
    
    private void recordConfigChange(String path) {
        JtaTransactionConfigChangeContainer container = getEventForActiveTransaction();
        if(container != null) {
            container.addChangedPath(path);
        } else {
            if (Boolean.valueOf(System.getProperty(NOTIFICATIONS_ENABLED_PROPERTY, "true")))
                configNotifService.sendClusterScopedConfigChangeNotification(new ConfigChangeEventImpl(path, CONTEXT.CONFIG_CHANGE));
        }
    }

    private JtaTransactionConfigChangeContainer getEventForActiveTransaction() {
        Transaction tx = null;
        try {
            tx = tmManager.getTransaction();
            if (tx == null || Status.STATUS_ACTIVE != tx.getStatus()) {
                return null;
            }
        } catch (SystemException e) {
            return null;
        }
        
        int transactionId = tx.hashCode();
        JtaTransactionConfigChangeContainer container = transactionMap.get(transactionId);
        if(container == null) {
            container = new JtaTransactionConfigChangeContainer(transactionId);
            try {
                tx.registerSynchronization(container);
            } catch (Exception e) {
                
            } 
            transactionMap.put(transactionId, container);
        }
        
        return container;
    }
    
    private class JtaTransactionConfigChangeContainer implements Synchronization {
        private final int transactionId;
        private List<String> changedPaths = new ArrayList<>();
        private CONTEXT context = CONTEXT.CONFIG_CHANGE;
        
        private JtaTransactionConfigChangeContainer(int transactionId) {
            this.transactionId = transactionId;
        }
        
        private JtaTransactionConfigChangeContainer(String path) {
            transactionId = -1;
            changedPaths.add(path);
        }
        
        private void addChangedPath(String path) {
            changedPaths.add(path);
        }
        
        private void setContext(CONTEXT context) {
            this.context = context;
        }
        
        @Override
        public void afterCompletion(int status) {
            transactionMap.remove(transactionId);

            // only notify if the changes were successfully committed
            if (status == Status.STATUS_COMMITTED)
                if (Boolean.valueOf(System.getProperty(NOTIFICATIONS_ENABLED_PROPERTY, "true")))
                    configNotifService.sendClusterScopedConfigChangeNotification(
                            new ConfigChangeEventImpl(changedPaths, context));
        }

        @Override
        public void beforeCompletion() {
            // NOOP
        }

    }
    
    private static class ConfigChangeEventImpl implements ConfigChangeEvent {
        private static final long serialVersionUID = 4454659631189062807L;
        
        private final List<String> changedPaths;
        private final CONTEXT context;
      
        private ConfigChangeEventImpl(String path, CONTEXT context) {
            this(Arrays.asList(path), context);
        }
        
        private ConfigChangeEventImpl(List<String> changedPaths, CONTEXT context) {
            this.changedPaths = changedPaths;
            this.context = context;
        }

        @Override
        public CONTEXT getContext() {
            return context;
        }

        @Override
        public List<String> getChangedPaths() {
            return changedPaths;
        }
    }
    
    
   
}
