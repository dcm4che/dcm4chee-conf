package org.dcm4chee.conf.extensions;

import org.dcm4che3.conf.api.extensions.CommonAEExtension;
import org.dcm4che3.conf.core.api.ConfigurableClass;
import org.dcm4che3.conf.core.api.ConfigurableProperty;

import java.util.*;

/**
 * Alternative representation of Transfer Capabilities for an application entity
 *
 * @author Roman K
 */
@ConfigurableClass
public class TCGroupConfigAEExtension extends CommonAEExtension{

    /**
     * Certain components' logic may be bound to these group names
     */
    public enum DefaultGroup {
        STORAGE,
        PPS,
        QUERY_RETRIEVE,
        STORAGE_COMMITMENT
    }

    public TCGroupConfigAEExtension() {
    }


    @ConfigurableProperty
    Map<String, TCGroupDetails> scuTCs = new TreeMap<String, TCGroupDetails>();

    @ConfigurableProperty
    Map<String, TCGroupDetails> scpTCs = new TreeMap<String, TCGroupDetails>();

    public TCGroupConfigAEExtension(EnumSet<DefaultGroup> scpGroups, EnumSet<DefaultGroup> scuGroups ) {
        for (DefaultGroup defaultGroup : scpGroups) scpTCs.put(defaultGroup.name(), new TCGroupDetails());
        for (DefaultGroup defaultGroup : scuGroups) scuTCs.put(defaultGroup.name(), new TCGroupDetails());
    }

    /**
     * Shortcut to define which default SCP groups are supported, omitting exclusion details
     * @return
     */
    public EnumSet<DefaultGroup> getSupportedDefaultScpGroups() {
        EnumSet<DefaultGroup> groups = EnumSet.noneOf(DefaultGroup.class);
        for (Map.Entry<String, TCGroupDetails> entry : scpTCs.entrySet()) {

            try {
                DefaultGroup group = DefaultGroup.valueOf(entry.getKey());
                groups.add(group);
            } catch (IllegalArgumentException e) {
                //noop
            }
        }
        return groups;
    }

    public Map<String, TCGroupDetails> getScuTCs() {
        return scuTCs;
    }

    public void setScuTCs(Map<String, TCGroupDetails> scuTCs) {
        this.scuTCs = scuTCs;
    }

    public Map<String, TCGroupDetails> getScpTCs() {
        return scpTCs;
    }

    public void setScpTCs(Map<String, TCGroupDetails> scpTCs) {
        this.scpTCs = scpTCs;
    }

    @ConfigurableClass
    public static class TCGroupDetails {

        public TCGroupDetails() {
        }

        @ConfigurableProperty
        private List<String> excludedTransferSyntaxes = new ArrayList<String>();

        @ConfigurableProperty
        private List<String> excludedTransferCapabilities = new ArrayList<String>();

        public List<String> getExcludedTransferSyntaxes() {
            return excludedTransferSyntaxes;
        }

        public void setExcludedTransferSyntaxes(List<String> excludedTransferSyntaxes) {
            this.excludedTransferSyntaxes = excludedTransferSyntaxes;
        }

        public List<String> getExcludedTransferCapabilities() {
            return excludedTransferCapabilities;
        }

        public void setExcludedTransferCapabilities(List<String> excludedTransferCapabilities) {
            this.excludedTransferCapabilities = excludedTransferCapabilities;
        }
    }
}
