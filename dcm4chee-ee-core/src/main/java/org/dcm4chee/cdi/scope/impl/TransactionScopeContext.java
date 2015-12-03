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

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.util.Set;

import javax.enterprise.context.spi.Context;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import org.dcm4chee.cdi.scope.TransactionScoped;
import org.dcm4chee.util.TransactionSynchronization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Alexander Hoermandinger <alexander.hoermandinger@agfa.com>
 *
 */
public class TransactionScopeContext implements Context, Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionScopeContext.class);
    
    private static final long serialVersionUID = 76589812365568L;
    
    private volatile TransactionSynchronization txSynchronisation;
    
    private final BeanManager beanManager;
    
    public TransactionScopeContext(BeanManager beanManager) {
        this.beanManager = beanManager;
    }
    
    @Override
    public Class<? extends Annotation> getScope() {
        return TransactionScoped.class;
    }

    @Override
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        TransactionScopedBeanStorage txBeanStorage = getTxBeanStorage();
        
        TransactionScopedBeanEntry<T> txScopedBeanEntry = txBeanStorage.get(contextual);
        if(txScopedBeanEntry != null) {
            return txScopedBeanEntry.getContextualInstance();
        }
       
        // create bean instance
        T beanInstance = contextual.create(creationalContext);
        txScopedBeanEntry = new TransactionScopedBeanEntry<T>(contextual, beanInstance, creationalContext);
        txBeanStorage.put(contextual, txScopedBeanEntry);
        
        return beanInstance;
    }

    @Override
    public <T> T get(Contextual<T> contextual) {
        TransactionScopedBeanStorage txBeanStorage = getTxBeanStorage();
        
        TransactionScopedBeanEntry<T> txScopedBeanEntry = txBeanStorage.get(contextual);
        return (txScopedBeanEntry != null) ? txScopedBeanEntry.getContextualInstance() : null;
    }

    @Override
    public boolean isActive() {
        ensureTxSynchronisation();
        return TransactionScopedBeanStorage.getInstance(txSynchronisation) != null;
    }
    
    private TransactionScopedBeanStorage getTxBeanStorage() {
        ensureTxSynchronisation();
        TransactionScopedBeanStorage txBeanStorage = TransactionScopedBeanStorage.getInstance(txSynchronisation);
        if(txBeanStorage == null) {
            throw new RuntimeException("CDI Transactional custom scope not accessed within JTA transaction");
        }
        
        return txBeanStorage;
    }
    
    private void ensureTxSynchronisation() {
        if(txSynchronisation == null) {
            synchronized(this) {
                if(txSynchronisation == null) {
                    txSynchronisation = getOrCreateTransactionSynchronization();
                }
            }
        }
    }
    
    private TransactionSynchronization getOrCreateTransactionSynchronization() {
        Set<Bean<?>> beans = beanManager.getBeans(TransactionSynchronization.class);
        Bean<?> txSynchronizationBean = beanManager.resolve(beans);
        CreationalContext<?> dependentScopeCreationalContext = beanManager.createCreationalContext(null);
        return (TransactionSynchronization)beanManager.getReference(txSynchronizationBean, txSynchronizationBean.getBeanClass(), 
                dependentScopeCreationalContext);
    }

}
