package org.dcm4chee.conf.remote;

import org.dcm4che3.conf.core.Nodes;
import org.dcm4che3.conf.core.api.Configuration;
import org.dcm4che3.conf.core.api.ConfigurationException;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Roman K
 */
public class RemoteConfigFactory
{

    public static class RemoteConfiguration implements Configuration
    {

        @Path( "/config" )
        private interface GenericConfigREST
        {

            @GET
            @Path( "/exportFullConfiguration" )
            @Produces( MediaType.APPLICATION_JSON )
            Map<String, Object> getFullConfig();

            @POST
            @Path( "/importFullConfiguration" )
            @Consumes( MediaType.APPLICATION_JSON )
            void setFullConfig( Map<String, Object> config );

            @GET
            @Path( "/pathByUUID/{uuid}" )
            org.dcm4che3.conf.core.api.Path getPathByUUID( @PathParam( value = "uuid" ) String uuid );

            @GET
            @Path( "/node" )
            @Produces( MediaType.APPLICATION_JSON )
            Object getConfigurationNode(
                    @QueryParam( value = "path" ) String pathStr,
                    @QueryParam( value = "class" ) String className
            );

            @POST
            @Path( "/node" )
            @Consumes( MediaType.APPLICATION_JSON )
            void persistConfigurationNode(
                    @QueryParam( value = "path" ) String pathStr,
                    @QueryParam( value = "class" ) String className,
                    Map<String, Object> config
            );

            @DELETE
            @Path( "/node" )
            void removeConfigurationNode(
                    @QueryParam( value = "path" ) String pathStr
            );

        }

        /**
         * jax rs client
         */
        GenericConfigREST remoteEndpoint;

        public RemoteConfiguration()
        {
        }

        public RemoteConfiguration( String remoteEndpointURL )
        {

            // create jax-rs client
            Client client = ClientBuilder.newBuilder().build();
            WebTarget target = client.target( remoteEndpointURL );
            ResteasyWebTarget rtarget = (ResteasyWebTarget)target;
            remoteEndpoint = rtarget.proxy( GenericConfigREST.class );
        }

        public Map<String, Object> getConfigurationRoot() throws ConfigurationException
        {
            return remoteEndpoint.getFullConfig();
        }

        public Object getConfigurationNode( org.dcm4che3.conf.core.api.Path path, Class configurableClass ) throws ConfigurationException
        {
            return remoteEndpoint.getConfigurationNode( path.toSimpleEscapedPath(), configurableClass == null ? null : configurableClass.getName() );
        }


        public boolean nodeExists( org.dcm4che3.conf.core.api.Path path ) throws ConfigurationException
        {
            if ( org.dcm4che3.conf.core.api.Path.ROOT.equals( path ) )
                return true;

            return remoteEndpoint.getConfigurationNode( path.toSimpleEscapedPath(), null) != null;
        }

        public void persistNode( org.dcm4che3.conf.core.api.Path path, Map<String, Object> configNode, Class configurableClass ) throws ConfigurationException
        {
            remoteEndpoint.persistConfigurationNode( path.toSimpleEscapedPath(), configurableClass== null?null:configurableClass.getName(),configNode );
        }

        public void refreshNode( org.dcm4che3.conf.core.api.Path path ) throws ConfigurationException
        {

        }

        public void removeNode( org.dcm4che3.conf.core.api.Path path ) throws ConfigurationException
        {
            remoteEndpoint.removeConfigurationNode( path.toSimpleEscapedPath() );
        }

        @Override
        public org.dcm4che3.conf.core.api.Path getPathByUUID( String uuid )
        {
            return remoteEndpoint.getPathByUUID( uuid );
        }

        public Iterator search( String liteXPathExpression ) throws IllegalArgumentException, ConfigurationException
        {
            Map<String, Object> fullConfig = remoteEndpoint.getFullConfig();
            return Nodes.search( fullConfig, liteXPathExpression );
        }

        public void lock()
        {
            //noop
        }

        public void runBatch( Batch batch )
        {
            batch.run();
        }

    }

}
