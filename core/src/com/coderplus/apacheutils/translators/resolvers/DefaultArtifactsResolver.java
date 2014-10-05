package com.coderplus.apacheutils.translators.resolvers;

/* 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.    
 */

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;

/**
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @version $Id: DefaultArtifactsResolver.java 1085777 2011-03-26 18:13:19Z hboutemy $
 */
public class DefaultArtifactsResolver
{

    ArtifactRepository local;

    List<ArtifactRepository> remoteRepositories;

    boolean stopOnFailure;

    public DefaultArtifactsResolver( ArtifactRepository theLocal,
                                    List<ArtifactRepository> theRemoteRepositories, boolean theStopOnFailure )
    {
        this.local = theLocal;
        this.remoteRepositories = theRemoteRepositories;
        this.stopOnFailure = theStopOnFailure;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.mojo.dependency.utils.resolvers.ArtifactsResolver#resolve(java.util.Set,
     *      org.apache.maven.plugin.logging.Log)
     */
    public Set<Artifact> resolve( Set<Artifact> artifacts)
        throws MojoExecutionException
    {

        Set<Artifact> resolvedArtifacts = new HashSet<Artifact>();
        for ( Artifact artifact : artifacts )
        {
            try
            {
            	ContainerConfiguration config = new DefaultContainerConfiguration();
        		config.setAutoWiring( true );
        		config.setClassPathScanning( PlexusConstants.SCANNING_INDEX );
        		RepositorySystem system =new DefaultPlexusContainer( config ).lookup( RepositorySystem.class );
        		ArtifactResolutionRequest request = new ArtifactResolutionRequest();
        		request.setArtifact(artifact);
        		request.setRemoteRepositories(remoteRepositories);
        		request.setLocalRepository(local);
        		//resolve the artifact
        		system.resolve(request);
                resolvedArtifacts.add( artifact );
            }
            catch (Exception ex )
            {
                // an error occurred during resolution, log it an continue
                if ( stopOnFailure )
                {
                    throw new MojoExecutionException( "error resolving: " + artifact.getId(), ex );
                }
            }

        }
        return resolvedArtifacts;
    }

}
