#standalone
mvn clean install 
#xds
mvn clean install -P xds,security-disabled $@
#arc
mvn clean install -P arc,security-disabled $@
#agility
mvn clean install -P xds-asb,security-disabled $@
