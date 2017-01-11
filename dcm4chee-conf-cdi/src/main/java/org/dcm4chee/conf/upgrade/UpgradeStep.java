package org.dcm4chee.conf.upgrade;

import org.dcm4che3.conf.api.upgrade.MMPVersion;
import org.dcm4chee.util.QuickChainable;

class UpgradeStep implements QuickChainable<UpgradeStep>, Comparable<UpgradeStep>
{
    String label;
    Runnable action;
    MMPVersion version;
    int scriptIndex;

    @Override
    public int compareTo(UpgradeStep o) {

        if (version.getMajor()<o.version.getMajor()) return -1;
        if (version.getMajor()>o.version.getMajor()) return 1;
        if (version.getMinor()<o.version.getMinor()) return -1;
        if (version.getMinor()>o.version.getMinor()) return 1;

        if (scriptIndex<o.scriptIndex) return -1;
        if (scriptIndex>o.scriptIndex) return 1;

        if (version.getPatch()<o.version.getPatch()) return -1;
        if (version.getPatch()>o.version.getPatch()) return 1;

        return 0;
    }
}
