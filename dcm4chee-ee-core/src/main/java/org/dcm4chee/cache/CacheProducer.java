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

package org.dcm4chee.cache;

import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * @author Roman K
 */
@ApplicationScoped
public class CacheProducer {

    private static final String container = System.getProperty("org.dcm4che.infinispan.container", "dcm4chee");
    private static final int maxRetries = Integer.parseInt(System.getProperty("org.dcm4chee.cache.maxRetries", "10"));

    private static final Logger LOG = LoggerFactory.getLogger(CacheProducer.class);

    @SuppressWarnings("unchecked")
    @Produces
    @CacheByName
    public <T, S> Cache<T, S> getCache(InjectionPoint point) {

        String cacheName = point.getAnnotated().getAnnotation(CacheByName.class).value();

        org.infinispan.Cache<Object, Object> cache;
        RuntimeException exception = null;
        for (int i = 0; i < maxRetries; ) {
            exception = null;
            try {
                Context ic = new InitialContext();
                EmbeddedCacheManager defaultCacheManager = (EmbeddedCacheManager) ic.lookup("java:jboss/infinispan/container/" + container);
                cache = defaultCacheManager.getCache(cacheName, false);
                if (cache != null) {
                    return new InfinispanWrapper(cache);
                }
            } catch (Exception e) {
                exception = new RuntimeException("Error while looking up cache '" + cacheName + "' in '" + container + "' container", e);
            }
            LOG.info("Infinispan cache '{}' not ready! Retry [{}/{}] in 500ms", new Object[]{cacheName, ++i, maxRetries});
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
        }
        if (exception != null)
            throw exception;
        throw new IllegalArgumentException("Infinispan cache '" + cacheName + "' not found in '" + container + "' cache container");
    }

}
