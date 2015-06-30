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

package org.dcm4chee.conf.cdi;

import java.util.Set;

import javax.annotation.PostConstruct;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;

import org.dcm4che3.conf.api.DicomConfigurationBuilderAddon;
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.storage.SingleJsonFileConfigurationStorage;
import org.dcm4che3.conf.dicom.DicomConfigurationBuilder;
import org.dcm4che3.conf.dicom.ldap.LdapConfigurationStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Alexander Hoermandinger <alexander.hoermandinger@agfa.com>
 */
public class CdiDicomConfigurationBuilder extends DicomConfigurationBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(CdiDicomConfigurationBuilder.class);
    
    @Inject 
    private BeanManager beanManager;
    
    @Inject
    private Instance<Configuration> customConfigStorage;
    
    @Inject
    private Instance<DicomConfigurationBuilderAddon> builderAddons;
    
    public CdiDicomConfigurationBuilder() {
        super(System.getProperties());
    }
    
    @PostConstruct
    private void init() {
        Configuration customStorage = checkForCustomStorage();
        if(customStorage != null) {
            LOG.info("Registering custom dicom configuration storage: " + customStorage.getClass().getName());
            registerCustomConfigurationStorage(customStorage);
        }
        
        // Allow configuration bootstrap extensibility through CDI
        for (DicomConfigurationBuilderAddon addon : builderAddons) {
            LOG.info("Registering dicom configuration add-on: " + addon.getClass().getName());
            registerAddon(addon);
        }
    }
    
    @Override
    protected SingleJsonFileConfigurationStorage createJsonFileConfigurationStorage() {
        return createBeanInstance(CdiSingleJsonFileConfigurationStorage.class);
    }
    
    private Configuration checkForCustomStorage() {
        for (Configuration configStorage : customConfigStorage) {
            if (!(configStorage instanceof CdiSingleJsonFileConfigurationStorage)
                    && !(configStorage instanceof CdiLdapConfigurationStorage)) {
                return configStorage;
            }
        }

        return null;
    }
    
    @Override
    protected LdapConfigurationStorage createLdapConfigurationStorage() {
        return createBeanInstance(CdiLdapConfigurationStorage.class);
    }
    
    /*
     * Creates CDI bean instance for the given type.
     */
    private <T> T createBeanInstance(Class<T> beanType) {
        Set<Bean<?>> beans = beanManager.getBeans(beanType,
                new AnnotationLiteral<Any>() {
                    private static final long serialVersionUID = 89732984L;
                });
        if (beans == null || beans.isEmpty()) {
            throw new RuntimeException("No CDI implementation found for bean type " + beanType.getName());
        }

        @SuppressWarnings("unchecked")
        Bean<Object> bean = (Bean<Object>) beans.iterator().next();
        CreationalContext<Object> creationalCxt = beanManager.createCreationalContext(null);

        Object beanInstance = bean.create(creationalCxt);
        @SuppressWarnings("unchecked")
        T t = (T)beanInstance;
        return t;
    }
    
    protected static class CdiSingleJsonFileConfigurationStorage extends SingleJsonFileConfigurationStorage {
        // Only override to bring it into CDI scope
    }
    
    protected static class CdiLdapConfigurationStorage extends LdapConfigurationStorage {
        // Only override to bring it into CDI scope
    }
       
}
