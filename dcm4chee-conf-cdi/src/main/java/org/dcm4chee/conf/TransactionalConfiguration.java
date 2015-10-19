package org.dcm4chee.conf;

import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4chee.conf.storage.TxInfo;

/**
 * @author Roman K
 */
public interface TransactionalConfiguration extends Configuration {

    void init(TxInfo txInfo);

    void beforeCommit();

    void afterCommit(int i);
}
