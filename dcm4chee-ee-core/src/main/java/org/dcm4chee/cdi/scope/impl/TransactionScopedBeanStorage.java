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

package org.dcm4chee.cdi.scope.impl;

import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.spi.Contextual;
import javax.transaction.RollbackException;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionSynchronizationRegistry;

import org.dcm4chee.util.TransactionSynchronization;

/**
 * @author Alexander Hoermandinger <alexander.hoermandinger@agfa.com>
 *
 */
public class TransactionScopedBeanStorage {
    private static final String TX_BEAN_STORAGE_RESOURCE = TransactionScopedBeanStorage.class.getName(); 
    
    private final Map<Contextual<?>, TransactionScopedBeanEntry<?>> contextualInstances = new HashMap<>();
    
    private TransactionScopedBeanStorage() {
        // NOP
    }
    
    public static TransactionScopedBeanStorage getInstance(TransactionSynchronization txSynchronisation) {
        try {
            Transaction tx = txSynchronisation.getTransactionManager().getTransaction();
            if (tx == null) {
                return null;
            }

            TransactionSynchronizationRegistry txSynRegistry = txSynchronisation.getSynchronizationRegistry();
            AfterCommitStorageRemover afterCommitRemover = (AfterCommitStorageRemover)txSynRegistry.getResource(TX_BEAN_STORAGE_RESOURCE);
            if (afterCommitRemover == null) {
                afterCommitRemover = new AfterCommitStorageRemover();
                txSynRegistry.putResource(TX_BEAN_STORAGE_RESOURCE, afterCommitRemover);
                tx.registerSynchronization(afterCommitRemover);
            }

            return afterCommitRemover.getBeanStorage();
        } catch (SystemException | RollbackException e) {
            throw new RuntimeException("Error while accessing transaction scoped bean storage", e);
        }
    }
    
    public <T> TransactionScopedBeanEntry<T> get(Contextual<T> contextual) {
        @SuppressWarnings("unchecked")
        TransactionScopedBeanEntry<T> beanEntry = (TransactionScopedBeanEntry<T>)contextualInstances.get(contextual);
        return beanEntry;
    }
    
    public <T> void put(Contextual<T> contextual, TransactionScopedBeanEntry<T> txBeanEntry) {
        contextualInstances.put(contextual, txBeanEntry);
    }
    
    private static class AfterCommitStorageRemover implements Synchronization {
        private final TransactionScopedBeanStorage beanStorage = new TransactionScopedBeanStorage();
        
        private TransactionScopedBeanStorage getBeanStorage() {
            return beanStorage;
        }
        
        @Override
        public void beforeCompletion() {
            //NOP
        }

        @Override
        public void afterCompletion(int status) {
            beanStorage.destroyBeans();
        }
        
    }
    
    /**
     * Properly destroy all the given beans.
     * @param activeBeans to destroy
     */
    private void destroyBeans()
    {
        for (TransactionScopedBeanEntry beanEntry : contextualInstances.values())
        {
            beanEntry.getBean().destroy(beanEntry.getContextualInstance(), beanEntry.getCreationalContext());
        }
        
        contextualInstances.clear();
    }

}
