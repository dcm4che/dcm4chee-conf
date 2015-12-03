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

package org.dcm4chee.hooks;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

import javax.inject.Inject;

import org.dcm4chee.hooks.producers.HooksConfigProducer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Alexander Hoermandinger <alexander.hoermandinger@agfa.com>
 *
 */
@RunWith(Arquillian.class)
public class ApplicationScopedHooksTest {
    
    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap
                .create(JavaArchive.class)
                .addClass(Hooks.class)
                .addClass(HooksProducer.class)
                .addClass(HooksManager.class)
                .addClass(HooksConfig.class)
                .addClass(HooksConfigProducer.class)
                .addClass(HookA.class)
                .addClass(ServiceWithAppScopedHooks.class)
                .addClass(AppScopedHook.class)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml"); 
    }
    
    @Inject
    private ServiceWithAppScopedHooks hookedServiceA;   
    
    @Before
    public void before() throws URISyntaxException {
        URL hooksConfigURL = this.getClass().getClassLoader().getResource("app-scoped-hook-config.json");
        String path = Paths.get(hooksConfigURL.toURI()).toString();
        System.setProperty("org.dcm4che.conf.hook.config", path);
    }
    
    @Test
    public void testHookInstantiation() {
        hookedServiceA.callHooked();
    }
    
    

}
