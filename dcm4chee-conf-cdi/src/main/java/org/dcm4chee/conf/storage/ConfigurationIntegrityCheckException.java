package org.dcm4chee.conf.storage;

public class ConfigurationIntegrityCheckException extends RuntimeException
{
    private static final long serialVersionUID = 9187121961455814848L;

    public ConfigurationIntegrityCheckException()
    {
        super();
    }

    public ConfigurationIntegrityCheckException( String message, Throwable cause )
    {
        super( message, cause );
    }

    public ConfigurationIntegrityCheckException( String message )
    {
        super( message );
    }

    public ConfigurationIntegrityCheckException( Throwable cause )
    {
        super( cause );
    }

}
