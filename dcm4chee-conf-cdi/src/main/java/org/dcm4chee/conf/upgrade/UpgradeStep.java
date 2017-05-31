package org.dcm4chee.conf.upgrade;

import org.dcm4che3.conf.api.upgrade.NumericVersion;
import org.dcm4chee.util.QuickChainable;

class UpgradeStep implements QuickChainable<UpgradeStep>, Comparable<UpgradeStep>
{
    String label;
    Runnable action;
    NumericVersion version;
    int scriptIndex;

    @Override
    public int compareTo(UpgradeStep o) {

        if (scriptIndex<o.scriptIndex) return -1;
        if (scriptIndex>o.scriptIndex) return 1;

        return version.compareTo( o.version );
    }
}
