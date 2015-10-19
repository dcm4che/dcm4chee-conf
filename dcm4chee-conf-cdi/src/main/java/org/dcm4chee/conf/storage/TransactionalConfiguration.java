package org.dcm4chee.conf.storage;

import org.dcm4che3.conf.core.api.Configuration;

/**
 * @author Roman K
 */
public interface TransactionalConfiguration extends Configuration {

    void init(TxInfo txInfo);

    void beforeCommit();

    void afterCommit(int i);

    interface TxInfo {
        boolean isPartOfModifyingTransaction();
    }
}
