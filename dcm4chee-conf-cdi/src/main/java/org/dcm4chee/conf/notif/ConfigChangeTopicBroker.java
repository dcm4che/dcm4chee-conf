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
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.Topic;

import org.dcm4che3.conf.core.api.ConfigChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forwards configuration change events to all cluster nodes via JMS.
 *
 * @author Alexander Hoermandinger <alexander.hoermandinger@agfa.com>
 */
public class ConfigChangeTopicBroker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigChangeTopicBroker.class);

    @Resource(mappedName = "java:/ConnectionFactory")
    private ConnectionFactory connectionFactory;

    @Resource(mappedName = ConfigChangeTopicMDB.QUALIFIED_CONFIG_CHANGE_JMS_TOPIC)
    private Topic topic;

    public void forwardToClusterNodes(ConfigChangeEvent event) {
        try {
            Connection connection = null;
            try {
                connection = connectionFactory.createConnection();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                MessageProducer messageProducer = session.createProducer(topic);
                connection.start();

                ObjectMessage msg = session.createObjectMessage(event);

                // attach name of sending node -> allows to filter on receiver side
                msg.setStringProperty(ConfigChangeTopicMDB.SENDING_NODE_MSG_PROP,
                        ConfigChangeTopicMDB.getNodeName());

                LOGGER.info("Sending cluster-wide config change notification (changed paths " + event.getChangedPaths() + ")");

                messageProducer.send(msg);
            } finally {
                connection.close();
            }
        } catch (JMSException e) {
            LOGGER.error("Error while sending JMS message", e);
        }
    }

}
