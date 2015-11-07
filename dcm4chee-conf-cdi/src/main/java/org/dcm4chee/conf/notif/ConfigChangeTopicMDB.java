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

import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;

import org.dcm4che3.conf.core.api.ConfigChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives JMS messages carrying config change events and distributes 
 * them locally using CDI events.
 * 
 * @author Alexander Hoermandinger <alexander.hoermandinger@agfa.com>
 */
@MessageDriven(name = "ConfigChangeTopicMDB", activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = ConfigChangeTopicMDB.CONFIG_CHANGE_JMS_TOPIC),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")})
public class ConfigChangeTopicMDB implements MessageListener {
    private final static Logger LOGGER = LoggerFactory.getLogger(ConfigChangeTopicMDB.class.toString());

    private final static String JBOSS_NODE_NAME_SYSTEM_PROP = "jboss.node.name";
    
    static final String CONFIG_CHANGE_JMS_TOPIC = "topic/DicomConfigurationChangeTopic";
    static final String QUALIFIED_CONFIG_CHANGE_JMS_TOPIC = "java:/" + CONFIG_CHANGE_JMS_TOPIC;
    static final String SENDING_NODE_MSG_PROP = "SENDING_NODE";
    
    @Inject
    private ConfigNotificationService notifService;

    @Resource(lookup="java:app/AppName")
    private String appName;
    
    @Override
    public void onMessage(Message rcvMessage) {
        ObjectMessage msg = null;
        try {
            if (rcvMessage instanceof ObjectMessage) {
                msg = (ObjectMessage) rcvMessage;
                
                String nodeName = getNodeName();
                String sendingNode = msg.getStringProperty(SENDING_NODE_MSG_PROP);
                
                ConfigChangeEvent event = (ConfigChangeEvent)msg.getObject();
                LOGGER.debug("Node '{}', deployment '{}' received config changed event from sending node {}: {}", nodeName, appName, sendingNode, event);
                
                notifService.sendLocalScopedConfigChangeNotification(event);
            } else {
                LOGGER.warn("JMS message event has wrong type: " + rcvMessage.getClass().getName());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while receiving / forwarding config change JMS message", e);
        }
    }
    
    public static String getNodeName() {
        return System.getProperty(JBOSS_NODE_NAME_SYSTEM_PROP);
    }
}
