/*******************************************************************************
 * Copyright (c) 2014 Aneesh Joseph
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Aneesh Joseph(coderplus.com)
 * Notes:
 * Most of this code is copied from the maven-dependency-plugin.
 * Did not directly delegate to the plugin to get rid of MDEP-187(eclipse resolves the dependency to the
 * workspace outputDirectory if the artifactItem is present in the workspace)
 *******************************************************************************/
package com.coderplus.m2e.dependencycore;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.LegacyLocalRepositoryManager;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.dependency.fromConfiguration.ArtifactItem;
import org.apache.maven.plugin.dependency.fromConfiguration.ProcessArtifactItemsRequest;
import org.apache.maven.project.DefaultMavenProjectBuilder;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ClassifierFilter;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.GroupIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.archiver.AbstractUnArchiver;
import org.codehaus.plexus.archiver.bzip2.BZip2UnArchiver;
import org.codehaus.plexus.archiver.gzip.GZipUnArchiver;
import org.codehaus.plexus.archiver.tar.TarBZip2UnArchiver;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.archiver.tar.TarUnArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.DefaultArtifactType;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.project.configurator.MojoExecutionBuildParticipant;
import org.sonatype.plexus.build.incremental.BuildContext;

import com.coderplus.apacheutils.DependencyStatusSets;
import com.coderplus.apacheutils.DependencyUtil;
import com.coderplus.apacheutils.translators.ClassifierTypeTranslator;
import com.coderplus.apacheutils.translators.resolvers.DefaultArtifactsResolver;

public class CoderPlusBuildParticipant extends MojoExecutionBuildParticipant {



	private static final String FAIL_ON_MISSING_CLASSIFIER_ARTIFACT = "failOnMissingClassifierArtifact";
	private static final String USE_REPOSITORY_LAYOUT = "useRepositoryLayout";
	private static final String USE_SUB_DIRECTORY_PER_ARTIFACT = "useSubDirectoryPerArtifact";
	private static final String USE_SUB_DIRECTORY_PER_TYPE = "useSubDirectoryPerType";
	private static final String COPY_POM = "copyPom";
	private static final String EXCLUDE_ARTIFACT_IDS = "excludeArtifactIds";
	private static final String INCLUDE_ARTIFACT_IDS = "includeArtifactIds";
	private static final String EXCLUDE_GROUP_IDS = "excludeGroupIds";
	private static final String INCLUDE_GROUP_IDS = "includeGroupIds";
	private static final String EXCLUDE_CLASSIFIERS = "excludeClassifiers";
	private static final String INCLUDE_CLASSIFIERS = "includeClassifiers";
	private static final String EXCLUDE_TYPES = "excludeTypes";
	private static final String INCLUDE_TYPES = "includeTypes";
	private static final String EXCLUDE_SCOPE = "excludeScope";
	private static final String INCLUDE_SCOPE = "includeScope";
	private static final String TAR = "tar";
	private static final String TAR_BZ2 = "tar.bz2";
	private static final String TRUE = "true";
	private static final String OVER_WRITE = "overWrite";
	private static final String DEST_FILE_NAME = "destFileName";
	private static final String CLASSIFIER = "classifier";
	private static final String TYPE = "type";
	private static final String BASE_VERSION = "baseVersion";
	private static final String VERSION = "version";
	private static final String USE_JVM_CHMOD = "useJvmChmod";
	private static final String IGNORE_PERMISSIONS = "ignorePermissions";
	private static final String INCLUDES = "includes";
	private static final String EXCLUDES = "excludes";
	private static final String STRIP_CLASSIFIER = "stripClassifier";
	private static final String USE_BASE_VERSION = "useBaseVersion";
	private static final String STRIP_VERSION = "stripVersion";
	private static final String PREPEND_GROUP_ID = "prependGroupId";
	private static final String LOCAL_REPOSITORY_DIRECTORY = "localRepositoryDirectory";
	private static final String OVER_IF_NEWER = "overIfNewer";
	private static final String OVER_WRITE_SNAPSHOTS = "overWriteSnapshots";
	private static final String OVER_WRITE_RELEASES = "overWriteReleases";
	private static final String GROUP_ID = "groupId";
	private static final String ARTIFACT_ID = "artifactId";
	private static final String ARTIFACT_ITEMS_PROPERTY = "artifactItems";
	private static final String GLOBAL_OUTPUT_DIRECTORY_PROPERTY = "outputDirectory";
	private static final String COPY_GOAL = "copy";
	private static final String COPY_DEPENDENCIES_GOAL = "copy-dependencies";
	private static final String UNPACK_DEPENDENCIES_GOAL = "unpack-dependencies";
	private static final String UNPACK_GOAL = "unpack";
	private static final String JAR = "jar";
	private static final String ZIP = "zip";
	private static final String WAR = "war";
	private static final String EAR = "ear";
	private static final String TAR_GZ = "tar.gz";
	private static final String GZ = "gz";
	private static final String BZ2 = "bz2";
	List<ArtifactItem> _artifactItems = new ArrayList<ArtifactItem>();
	private File globalOutputDirectory;
	private MavenProject project;
	private boolean overWriteReleases;
	private boolean overWriteSnapshots;
	private boolean overWriteIfNewer;
	private File localRepositoryDirectory;
	private boolean prependGroupId;
	private boolean skip;
	private boolean stripVersion;
	private boolean useBaseVersion;
	private boolean stripClassifier;
	private boolean removeVersion;
	private IMaven maven;
	private String excludes;
	private String includes;
	private boolean ignorePermissions;
	private boolean useJvmChmod;
	private String goal;
	private Set<File> refreshableDirectories = new HashSet<File>();
	private boolean excludeTransitive;
	private String includeScope;
	private String excludeScope;
	private String includeTypes;
	private String excludeTypes;
	private String includeClassifiers;
	private String excludeClassifiers;
	private String includeGroupIds;
	private String excludeGroupIds;
	private String includeArtifactIds;
	private String excludeArtifactIds;
	private String classifier;
	private String type;
	private boolean useSubDirectoryPerScope;
	private boolean useSubDirectoryPerType;
	private boolean useSubDirectoryPerArtifact;
	private boolean useRepositoryLayout;
	private boolean failOnMissingClassifierArtifact;
	private boolean copyPom;
	public CoderPlusBuildParticipant(MojoExecution execution) {

		super(execution, true);

	}

	@SuppressWarnings("rawtypes")
	@Override
	public Set<IProject> build(final int kind, final IProgressMonitor monitor) throws Exception {
		final MojoExecution execution = getMojoExecution();
		if (execution == null) {
			return null;
		}
		this.project = getMavenProjectFacade().getMavenProject();
		this.maven = MavenPlugin.getMaven();
		this.goal = execution.getGoal();
		final BuildContext buildContext = getBuildContext();

		final IFile pomFile = (IFile) getMavenProjectFacade().getProject().findMember("pom.xml");
		// skipping the build if it's neither a full Build nor  pom.xml has changed
		if(kind != IncrementalProjectBuilder.FULL_BUILD && !buildContext.hasDelta(pomFile.getLocation().toFile())) {
			return null;
		}

		//gathering plugin configuration
		this.skip =  Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, "skip",Boolean.class, new NullProgressMonitor()));

		//skipping as requested
		if(skip){
			return null;
		}
		this.globalOutputDirectory =maven.getMojoParameterValue(project, execution, GLOBAL_OUTPUT_DIRECTORY_PROPERTY,File.class, new NullProgressMonitor());
		this.overWriteReleases = Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, OVER_WRITE_RELEASES,Boolean.class, new NullProgressMonitor()));
		this.overWriteSnapshots = Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, OVER_WRITE_SNAPSHOTS,Boolean.class, new NullProgressMonitor()));
		this.overWriteIfNewer = Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, OVER_IF_NEWER,Boolean.class, new NullProgressMonitor()));
		this.localRepositoryDirectory = maven.getMojoParameterValue(project, execution, LOCAL_REPOSITORY_DIRECTORY,File.class, new NullProgressMonitor());
		this.prependGroupId =  Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, PREPEND_GROUP_ID,Boolean.class, new NullProgressMonitor()));
		this.stripVersion =  Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, STRIP_VERSION,Boolean.class, new NullProgressMonitor()));
		this.useBaseVersion =  Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, USE_BASE_VERSION,Boolean.class, new NullProgressMonitor()));
		this.stripClassifier =  Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, STRIP_CLASSIFIER,Boolean.class, new NullProgressMonitor()));
		this.useSubDirectoryPerScope = Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, STRIP_CLASSIFIER,Boolean.class, new NullProgressMonitor()));
		this.useSubDirectoryPerType = Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, USE_SUB_DIRECTORY_PER_TYPE,Boolean.class, new NullProgressMonitor()));
		this.useSubDirectoryPerArtifact = Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, USE_SUB_DIRECTORY_PER_ARTIFACT,Boolean.class, new NullProgressMonitor()));
		this.useRepositoryLayout = Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, USE_REPOSITORY_LAYOUT,Boolean.class, new NullProgressMonitor()));
		this.failOnMissingClassifierArtifact = Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, FAIL_ON_MISSING_CLASSIFIER_ARTIFACT,Boolean.class, new NullProgressMonitor()));

		this.excludes = maven.getMojoParameterValue(project, execution, EXCLUDES,String.class, new NullProgressMonitor());
		this.includes = maven.getMojoParameterValue(project, execution, INCLUDES,String.class, new NullProgressMonitor());
		this.ignorePermissions =  Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, IGNORE_PERMISSIONS,Boolean.class, new NullProgressMonitor()));
		this.copyPom =  Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, COPY_POM,Boolean.class, new NullProgressMonitor()));
		this.useJvmChmod =  Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, USE_JVM_CHMOD,Boolean.class, new NullProgressMonitor()));
		this.includeScope = maven.getMojoParameterValue(project, execution, INCLUDE_SCOPE,String.class, new NullProgressMonitor());
		this.excludeScope = maven.getMojoParameterValue(project, execution, EXCLUDE_SCOPE,String.class, new NullProgressMonitor());
		this.includeTypes = maven.getMojoParameterValue(project, execution, INCLUDE_TYPES,String.class, new NullProgressMonitor());
		this.excludeTypes = maven.getMojoParameterValue(project, execution, EXCLUDE_TYPES,String.class, new NullProgressMonitor());
		this.includeClassifiers = maven.getMojoParameterValue(project, execution, INCLUDE_CLASSIFIERS,String.class, new NullProgressMonitor());
		this.excludeClassifiers = maven.getMojoParameterValue(project, execution, EXCLUDE_CLASSIFIERS,String.class, new NullProgressMonitor());
		this.includeGroupIds = maven.getMojoParameterValue(project, execution, INCLUDE_GROUP_IDS,String.class, new NullProgressMonitor());
		this.excludeGroupIds = maven.getMojoParameterValue(project, execution, EXCLUDE_GROUP_IDS,String.class, new NullProgressMonitor());
		this.includeArtifactIds = maven.getMojoParameterValue(project, execution, INCLUDE_ARTIFACT_IDS,String.class, new NullProgressMonitor());
		this.excludeArtifactIds = maven.getMojoParameterValue(project, execution, EXCLUDE_ARTIFACT_IDS,String.class, new NullProgressMonitor());	
		this.classifier = maven.getMojoParameterValue(project, execution, CLASSIFIER,String.class, new NullProgressMonitor());
		String temp=maven.getMojoParameterValue(project, execution, TYPE,String.class, new NullProgressMonitor());
		this.type = temp!=null? temp:"";



		if(COPY_GOAL.equals(goal)||UNPACK_GOAL.equals(goal)){
			final List artifactItems = maven.getMojoParameterValue(project, execution, ARTIFACT_ITEMS_PROPERTY,
					List.class, new NullProgressMonitor());
			//gathering the artifact details using reflection
			Method getOutputDirectoryMethod = null, getArtifactIdMethod=null,getGroupIdMethod=null,getVersionMethod=null,getTypeMethod=null,
					getClassifierMethod=null,getDestFileNameMethod=null,getOverWriteMethod=null,getBaseVersionMethod=null,getExcludesMethod=null,
					getIncludesMethod=null;

			for (Object artifactItem : artifactItems) {
				if (getOutputDirectoryMethod == null) {
					getOutputDirectoryMethod = new PropertyDescriptor(GLOBAL_OUTPUT_DIRECTORY_PROPERTY, artifactItem.getClass()).getReadMethod();
					getArtifactIdMethod = new  PropertyDescriptor(ARTIFACT_ID, artifactItem.getClass()).getReadMethod();
					getGroupIdMethod = new  PropertyDescriptor(GROUP_ID, artifactItem.getClass()).getReadMethod();
					getVersionMethod = new  PropertyDescriptor(VERSION, artifactItem.getClass()).getReadMethod();
					if(useBaseVersion){
						getBaseVersionMethod = new  PropertyDescriptor(BASE_VERSION, artifactItem.getClass()).getReadMethod();
					}
					getTypeMethod = new  PropertyDescriptor(TYPE, artifactItem.getClass()).getReadMethod();
					getClassifierMethod = new  PropertyDescriptor(CLASSIFIER, artifactItem.getClass()).getReadMethod();
					getDestFileNameMethod= new  PropertyDescriptor(DEST_FILE_NAME, artifactItem.getClass()).getReadMethod();
					getOverWriteMethod = new  PropertyDescriptor(OVER_WRITE, artifactItem.getClass()).getReadMethod();
					getIncludesMethod=new  PropertyDescriptor(INCLUDES, artifactItem.getClass()).getReadMethod();
					getExcludesMethod=new  PropertyDescriptor(EXCLUDES, artifactItem.getClass()).getReadMethod();
				}

				ArtifactItem _artifactItem = new ArtifactItem();
				_artifactItem.setOutputDirectory((File) getOutputDirectoryMethod.invoke(artifactItem));
				_artifactItem.setArtifactId((String) getArtifactIdMethod.invoke(artifactItem));
				_artifactItem.setGroupId((String) getGroupIdMethod.invoke(artifactItem));
				_artifactItem.setVersion((String) getVersionMethod.invoke(artifactItem));
				if(useBaseVersion){
					_artifactItem.setBaseVersion((String) getBaseVersionMethod.invoke(artifactItem));
				}
				String type = (String) getTypeMethod.invoke(artifactItem);
				if(StringUtils.isNotEmpty(type)){
					_artifactItem.setType(type);
				}
				_artifactItem.setClassifier((String) getClassifierMethod.invoke(artifactItem));
				_artifactItem.setDestFileName((String) getDestFileNameMethod.invoke(artifactItem));
				_artifactItem.setOverWrite((String) getOverWriteMethod.invoke(artifactItem));
				_artifactItem.setIncludes((String) getIncludesMethod.invoke(artifactItem));
				_artifactItem.setExcludes((String) getExcludesMethod.invoke(artifactItem));
				_artifactItems.add(_artifactItem);
			}

			//processing and resolving the artifacts using the aether framework
			List<ArtifactItem> theArtifactItems = getProcessedArtifactItems(new ProcessArtifactItemsRequest( stripVersion, prependGroupId, useBaseVersion, stripClassifier ) );

			for ( ArtifactItem artifactItem : theArtifactItems )
			{
				if ( artifactItem.isNeedsProcessing() )
				{
					if(COPY_GOAL.equals(goal)){
						//if copy, then copy the artifact
						copyArtifact( artifactItem );

					} else if(UNPACK_GOAL.equals(goal)){
						//if unpack, then unpack the artifact using plexus unarchivers.
						unpack(artifactItem.getArtifact(),artifactItem.getOutputDirectory(), artifactItem.getIncludes(),artifactItem.getExcludes() );
					}
					//add the output directory to the set to be refreshed.
					refreshableDirectories.add(artifactItem.getOutputDirectory());
				}
				else
				{

					//do nothing
				}
			}

		} else if(UNPACK_DEPENDENCIES_GOAL.equals(goal) || COPY_DEPENDENCIES_GOAL.equals(goal)) {

			DependencyStatusSets dss = getDependencySets( this.failOnMissingClassifierArtifact );
			if(UNPACK_DEPENDENCIES_GOAL.equals(goal)){
				for ( Artifact artifact : dss.getResolvedDependencies() )
				{
					File destDir;
					destDir = DependencyUtil.getFormattedOutputDirectory( this.useSubDirectoryPerScope, this.useSubDirectoryPerType,
							this.useSubDirectoryPerArtifact, this.useRepositoryLayout,
							stripVersion, this.globalOutputDirectory, artifact );
					unpack( artifact, destDir, includes, excludes );
				}

				/*	for ( Artifact artifact : dss.getSkippedDependencies() )
				{
					 getLog().info( artifact.getFile().getName() + " already exists in destination." );
				}*/
			} else {
				//copy dependencies
				Set<Artifact> artifacts = dss.getResolvedDependencies();

				if ( !useRepositoryLayout )
				{
					for ( Artifact artifact : artifacts )
					{
						copyArtifact( artifact, stripVersion, this.prependGroupId, this.useBaseVersion,
								this.stripClassifier );
					}
				}
				else
				{
					try
					{
						ContainerConfiguration config = new DefaultContainerConfiguration();
						config.setAutoWiring( true );
						config.setClassPathScanning( PlexusConstants.SCANNING_INDEX );
						RepositorySystem system =new DefaultPlexusContainer( config ).lookup( RepositorySystem.class );

						ArtifactRepository targetRepository = system.createArtifactRepository( "local", globalOutputDirectory.toURL().toExternalForm(),  maven.getLocalRepository().getLayout(), maven.getLocalRepository().getSnapshots(),  maven.getLocalRepository().getReleases() );

						for ( Artifact artifact : artifacts )
						{
							installArtifact( artifact, targetRepository );
						}
					}
					catch ( MalformedURLException e )
					{
						throw new MojoExecutionException( "Could not create outputDirectory repository", e );
					}
				}

				Set<Artifact> skippedArtifacts = dss.getSkippedDependencies();
				for ( Artifact artifact : skippedArtifacts )
				{
					// getLog().info( artifact.getId() + " already exists in destination." ) ;
				}

				if ( copyPom && !useRepositoryLayout )
				{
					copyPoms( globalOutputDirectory, artifacts, this.stripVersion );
					copyPoms( globalOutputDirectory, skippedArtifacts,
							this.stripVersion, this.stripClassifier );  // Artifacts that already exist may not yet have poms
				}

			}

			refreshableDirectories.add(globalOutputDirectory);

		}

		//refresh the output directories
		for(File refreshableDirectory: refreshableDirectories){
			buildContext.refresh(refreshableDirectory);
		}
		return null;	

	}


	private void copyPoms(File destDir, Set<Artifact> artifacts, boolean stripVersion,
			boolean stripClassifier) throws Exception {
        for ( Artifact artifact : artifacts )
        {
            Artifact pomArtifact = getResolvedPomArtifact( artifact );

            // Copy the pom
            if ( pomArtifact.getFile() != null && pomArtifact.getFile().exists() )
            {
                File pomDestFile =
                    new File( destDir, DependencyUtil.getFormattedFileName( pomArtifact, stripVersion, prependGroupId,
                                                                            useBaseVersion, stripClassifier ) );
                if ( !pomDestFile.exists() )
                {
                    copyFile( pomArtifact.getFile(), pomDestFile,pomArtifact );
                }
            }
        }
    }

	private void copyPoms(File destDir, Set<Artifact> artifacts, boolean stripVersion) throws Exception {
		copyPoms( destDir, artifacts, stripVersion, false );
	}

	private void installArtifact(Artifact artifact, ArtifactRepository targetRepository) throws Exception {
		try
		{
			if ( "pom".equals( artifact.getType() ) )
			{
				install( artifact.getFile(), artifact, targetRepository );
				installBaseSnapshot( artifact, targetRepository );
			}
			else
			{
				install( artifact.getFile(), artifact, targetRepository );
				installBaseSnapshot( artifact, targetRepository );

				if ( copyPom )
				{
					Artifact pomArtifact = getResolvedPomArtifact( artifact );
					if ( pomArtifact.getFile() != null && pomArtifact.getFile().exists() )
					{
						install( pomArtifact.getFile(), pomArtifact, targetRepository );
						installBaseSnapshot( pomArtifact, targetRepository );
					}
				}
			}
		}
		catch ( ArtifactInstallationException e )
		{
			// getLog().warn( "unable to install " + artifact, e );
		}
	}

	private void install(File file,Artifact artifact, ArtifactRepository targetRepository) throws Exception{
		ContainerConfiguration config = new DefaultContainerConfiguration();
		config.setAutoWiring( true );
		config.setClassPathScanning( PlexusConstants.SCANNING_INDEX );
		org.eclipse.aether.RepositorySystem  repoSystem =new DefaultPlexusContainer( config ).lookup( org.eclipse.aether.RepositorySystem.class );;
		InstallRequest request = new InstallRequest();

		if(file.isDirectory()){
			file = new File(maven.getLocalRepository().getBasedir(),maven.getLocalRepository().pathOf(artifact));
		}
		Map props = null;
		if ("system".equals(artifact.getScope())) {
			String localPath = (artifact.getFile() != null) ? artifact
					.getFile().getPath() : "";
					props  = Collections.singletonMap("localPath", localPath);
		}

		String version = artifact.getVersion();
		if ((version == null) && (artifact.getVersionRange() != null)) {
			version = artifact.getVersionRange().toString();
		}


		org.eclipse.aether.artifact.Artifact mainArtifact  = new org.eclipse.aether.artifact.DefaultArtifact(
				artifact.getGroupId(), artifact.getArtifactId(),
				artifact.getClassifier(), artifact.getArtifactHandler()
				.getExtension(), version, props, newArtifactType(
						artifact.getType(), artifact.getArtifactHandler()));

		mainArtifact = mainArtifact.setFile(file);
		request.addArtifact(mainArtifact);
		repoSystem.install(LegacyLocalRepositoryManager.overlay(targetRepository, null, null), request);
	}

	public static ArtifactType newArtifactType(String id,
			ArtifactHandler handler) {
		return new DefaultArtifactType(id, handler.getExtension(),
				handler.getClassifier(), handler.getLanguage(),
				handler.isAddedToClasspath(), handler.isIncludesDependencies());
	}

	private Artifact getResolvedPomArtifact(Artifact artifact) {
		Artifact pomArtifact = new DefaultArtifact( artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(),Artifact.SCOPE_COMPILE , "pom", null,new DefaultArtifactHandler("pom"));
		// Resolve the pom artifact using repos
		try
		{
			ContainerConfiguration config = new DefaultContainerConfiguration();
			config.setAutoWiring( true );
			config.setClassPathScanning( PlexusConstants.SCANNING_INDEX );
			RepositorySystem system =new DefaultPlexusContainer( config ).lookup( RepositorySystem.class );
			ArtifactResolutionRequest request = new ArtifactResolutionRequest();
			request.setArtifact(pomArtifact);
			request.setRemoteRepositories(maven.getArtifactRepositories());
			request.setLocalRepository(getLocal());
			//resolve the artifact
			system.resolve(request);
		}
		catch ( Exception e )
		{
			//getLog().info( e.getMessage() );
		}
		return pomArtifact;
	}

	private void installBaseSnapshot(Artifact artifact, ArtifactRepository targetRepository) throws Exception {
		if ( artifact.isSnapshot() && !artifact.getBaseVersion().equals( artifact.getVersion() ) )
		{	
			Artifact baseArtifact = new DefaultArtifact( artifact.getGroupId(), artifact.getArtifactId(), artifact.getBaseVersion(),artifact.getScope() ,artifact.getType(), artifact.getClassifier(),new DefaultArtifactHandler(artifact.getType()));
			install( artifact.getFile(), baseArtifact, targetRepository );
		}
	}

	private void copyArtifact(Artifact artifact, boolean stripVersion2, boolean prependGroupId2,
			boolean useBaseVersion2, boolean stripClassifier2) throws Exception {
		String destFileName = DependencyUtil.getFormattedFileName( artifact, stripVersion2, prependGroupId2, 
				useBaseVersion2, stripClassifier2 );

		File destDir;
		destDir = DependencyUtil.getFormattedOutputDirectory( useSubDirectoryPerScope, useSubDirectoryPerType,
				useSubDirectoryPerArtifact, useRepositoryLayout,
				stripVersion, globalOutputDirectory, artifact );
		File destFile = new File( destDir, destFileName );

		copyFile(artifact.getFile(),destFile,artifact);

	}

	private void copyFile(File srcFile,File destFile, Artifact artifact) throws Exception{
		if(srcFile.isDirectory()){
			srcFile = new File(maven.getLocalRepository().getBasedir(),maven.getLocalRepository().pathOf(artifact));
		}
		if(srcFile.exists()){
			FileUtils.copyFile(srcFile, destFile );
		} else {
			throw new Exception("Unable to resolve artifact"+artifact.getGroupId()+":"+artifact.getArtifactId()+":"+artifact.getVersion());
		}

	}

	private void copyArtifact(ArtifactItem artifactItem) throws Exception {

		copyFile(artifactItem.getArtifact().getFile(),new File( artifactItem.getOutputDirectory(), artifactItem.getDestFileName() ),artifactItem.getArtifact());

	}


	private List<ArtifactItem> getProcessedArtifactItems(ProcessArtifactItemsRequest processArtifactItemsRequest) throws Exception {

		this.removeVersion = processArtifactItemsRequest.isRemoveVersion();
		this.prependGroupId =processArtifactItemsRequest.isPrependGroupId();
		this.useBaseVersion = processArtifactItemsRequest.isUseBaseVersion();
		boolean removeClassifier = processArtifactItemsRequest.isRemoveClassifier();

		if ( _artifactItems == null || _artifactItems.size() < 1 )
		{
			throw new Exception( "There are no artifactItems configured." );
		}

		for ( ArtifactItem artifactItem : _artifactItems )
		{

			if ( artifactItem.getOutputDirectory() == null )
			{
				artifactItem.setOutputDirectory(globalOutputDirectory );
			}
			artifactItem.getOutputDirectory().mkdirs();

			// make sure we have a version.
			if ( StringUtils.isEmpty( artifactItem.getVersion() ) )
			{
				fillMissingArtifactVersion( artifactItem );
			}

			artifactItem.setArtifact( getArtifact( artifactItem ) );

			if ( StringUtils.isEmpty( artifactItem.getDestFileName() ) )
			{
				artifactItem.setDestFileName( DependencyUtil.getFormattedFileName( artifactItem.getArtifact(),
						removeVersion, prependGroupId,
						useBaseVersion, removeClassifier ) );
			}

			artifactItem.setNeedsProcessing( checkIfProcessingNeeded( artifactItem ) );

			if ( StringUtils.isEmpty( artifactItem.getIncludes() ) )
			{
				artifactItem.setIncludes(includes);
			}
			if ( StringUtils.isEmpty( artifactItem.getExcludes() ) )
			{
				artifactItem.setExcludes(excludes);
			}
		}


		return _artifactItems;
	}

	private boolean checkIfProcessingNeeded(ArtifactItem artifactItem) {
		return StringUtils.equalsIgnoreCase( artifactItem.getOverWrite(), TRUE )
				|| writeRequired( artifactItem );
	}

	private boolean writeRequired(ArtifactItem artifactItem) {

		Artifact artifact = artifactItem.getArtifact();

		boolean overWrite =
				( artifact.isSnapshot() && this.overWriteSnapshots )
				|| ( !artifact.isSnapshot() && this.overWriteReleases );

		File destFolder = artifactItem.getOutputDirectory();
		if ( destFolder == null )
		{
			destFolder =
					DependencyUtil.getFormattedOutputDirectory( false, false,
							false, false,
							removeVersion, this.globalOutputDirectory, artifact );
		}

		File destFile;
		if ( StringUtils.isEmpty( artifactItem.getDestFileName() ) )
		{
			destFile = new File( destFolder, DependencyUtil.getFormattedFileName( artifact, this.removeVersion ) );
		}
		else
		{
			destFile = new File( destFolder, artifactItem.getDestFileName() );
		}

		return overWrite || !destFile.exists()
				|| ( overWriteIfNewer && artifact.getFile().lastModified() > destFile.lastModified() );


	}

	private Artifact getArtifact(ArtifactItem artifactItem) throws Exception {

		Artifact artifact = null;
		VersionRange vr;
		try
		{
			vr = VersionRange.createFromVersionSpec( artifactItem.getVersion() );
		}
		catch ( InvalidVersionSpecificationException e1 )
		{
			e1.printStackTrace();
			vr = VersionRange.createFromVersion( artifactItem.getVersion() );
		}
		if ( StringUtils.isEmpty( artifactItem.getClassifier() ) )
		{
			artifact = new DefaultArtifact( artifactItem.getGroupId(), artifactItem.getArtifactId(), vr,Artifact.SCOPE_COMPILE, artifactItem.getType(), null, new DefaultArtifactHandler(artifactItem.getType()) );
		}
		else
		{
			artifact =  new DefaultArtifact( artifactItem.getGroupId(), artifactItem.getArtifactId(), vr,Artifact.SCOPE_COMPILE , artifactItem.getType(), artifactItem.getClassifier(),new DefaultArtifactHandler(artifactItem.getType()));
		}

		ContainerConfiguration config = new DefaultContainerConfiguration();
		config.setAutoWiring( true );
		config.setClassPathScanning( PlexusConstants.SCANNING_INDEX );
		RepositorySystem system =new DefaultPlexusContainer( config ).lookup( RepositorySystem.class );
		ArtifactResolutionRequest request = new ArtifactResolutionRequest();
		request.setArtifact(artifact);
		request.setRemoteRepositories(maven.getArtifactRepositories());
		request.setLocalRepository(getLocal());
		//resolve the artifact
		system.resolve(request);
		return artifact;
	}

	private void fillMissingArtifactVersion(ArtifactItem artifactItem) throws Exception {
		List<Dependency> deps = project.getDependencies();
		List<Dependency> depMngt = project.getDependencyManagement() == null
				? Collections.<Dependency>emptyList()
						: project.getDependencyManagement().getDependencies();

				if ( !findDependencyVersion( artifactItem, deps, false )
						&& ( project.getDependencyManagement() == null || !findDependencyVersion( artifactItem, depMngt, false ) )
						&& !findDependencyVersion( artifactItem, deps, true )
						&& ( project.getDependencyManagement() == null || !findDependencyVersion( artifactItem, depMngt, true ) ) )
				{
					throw new Exception(
							"Unable to find artifact version of " + artifactItem.getGroupId() + ":" + artifactItem.getArtifactId()
							+ " in either dependency list or in project's dependency management." );
				}
	}

	private boolean findDependencyVersion( ArtifactItem artifact, List<Dependency> dependencies, boolean looseMatch ) {
		for ( Dependency dependency : dependencies )
		{
			if ( StringUtils.equals( dependency.getArtifactId(), artifact.getArtifactId() )
					&& StringUtils.equals( dependency.getGroupId(), artifact.getGroupId() )
					&& ( looseMatch || StringUtils.equals( dependency.getClassifier(), artifact.getClassifier() ) )
					&& ( looseMatch || StringUtils.equals( dependency.getType(), artifact.getType() ) ) )
			{
				artifact.setVersion( dependency.getVersion() );

				return true;
			}
		}

		return false;
	}


	/**
	 * Unpacks the archive file.
	 *
	 * @param artifact File to be unpacked.
	 * @param location Location where to put the unpacked files.
	 * @param includes Comma separated list of file patterns to include i.e. <code>**&#47;.xml,
	 *                 **&#47;*.properties</code>
	 * @param excludes Comma separated list of file patterns to exclude i.e. <code>**&#47;*.xml,
	 *                 **&#47;*.properties</code>
	 * @throws Exception 
	 */
	private void unpack( Artifact artifact, File location, String includes, String excludes )
			throws Exception
	{
		File file = artifact.getFile(); 
		try
		{

			location.mkdirs();

			if ( file.isDirectory() )
			{

				file = new File(maven.getLocalRepository().getBasedir(),maven.getLocalRepository().pathOf(artifact));
			}

			if(!file.exists()){
				throw new Exception("Unable to resolve artifact to "+file.getAbsolutePath());
			}
			//wasn't able to properly instantiate the DefaultPlexusContainer, hence using specific unarchivers instead of looking up from the Container.
			AbstractUnArchiver unArchiver = null;

			if(checkType(JAR,artifact.getType(),file) || checkType(ZIP,artifact.getType(),file)  || checkType(WAR,artifact.getType(),file) || checkType(EAR,artifact.getType(),file)){

				unArchiver = new ZipUnArchiver(file);

			} else if(checkType(TAR,artifact.getType(),file)){

				unArchiver = new TarUnArchiver(file);

			}else if(checkType(TAR_BZ2,artifact.getType(),file)){

				unArchiver = new TarBZip2UnArchiver(file);

			}else if(checkType(TAR_GZ,artifact.getType(),file)){

				unArchiver = new TarGZipUnArchiver(file);

			} else if(checkType(GZ,artifact.getType(),file)){

				unArchiver = new GZipUnArchiver(file);

			} else if(checkType(BZ2,artifact.getType(),file)){

				unArchiver = new BZip2UnArchiver(file);

			} else{
				new Exception("Couldn't find a suitable unarchiver for "+artifact.getType()+" or "+artifact.getFile().getAbsolutePath());
			}

			unArchiver.setUseJvmChmod( useJvmChmod );
			unArchiver.setIgnorePermissions( ignorePermissions );
			unArchiver.setSourceFile( file );
			unArchiver.setDestDirectory( location );
			unArchiver.enableLogging(new ConsoleLogger(ConsoleLogger.LEVEL_DEBUG,"Logger"));

			if ( StringUtils.isNotEmpty( excludes ) || StringUtils.isNotEmpty( includes ) )
			{
				IncludeExcludeFileSelector[] selectors =
						new IncludeExcludeFileSelector[]{ new IncludeExcludeFileSelector() };

				if ( StringUtils.isNotEmpty( excludes ) )
				{
					selectors[0].setExcludes( excludes.split( "," ) );
				}

				if ( StringUtils.isNotEmpty( includes ) )
				{
					selectors[0].setIncludes( includes.split( "," ) );
				}

				unArchiver.setFileSelectors( selectors );
			}

			unArchiver.extract();

		}catch ( Exception e )
		{
			throw new Exception(
					"Error unpacking file: " + file + " to: " + location + "\r\n" + e.toString(), e );
		}
	}

	private boolean checkType(String checkWith , String type, File file){
		if(checkWith.equalsIgnoreCase(type) || file.getAbsolutePath().endsWith(checkWith)){
			return true;
		}
		return false;
	}


	/**
	 * Method creates filters and filters the projects dependencies. This method
	 * also transforms the dependencies if classifier is set. The dependencies
	 * are filtered in least specific to most specific order
	 *
	 * @param stopOnFailure
	 * @return DependencyStatusSets - Bean of TreeSets that contains information
	 *         on the projects dependencies
	 * @throws Exception 
	 */
	protected DependencyStatusSets getDependencySets( boolean stopOnFailure, boolean includeParents )
			throws Exception
	{
		// add filters in well known order, least specific to most specific
		FilterArtifacts filter = new FilterArtifacts();

		filter.addFilter( new ProjectTransitivityFilter( project.getDependencyArtifacts(), this.excludeTransitive ) );

		filter.addFilter( new ScopeFilter( DependencyUtil.cleanToBeTokenizedString( this.includeScope ),
				DependencyUtil.cleanToBeTokenizedString( this.excludeScope ) ) );

		filter.addFilter( new TypeFilter( DependencyUtil.cleanToBeTokenizedString( this.includeTypes ),
				DependencyUtil.cleanToBeTokenizedString( this.excludeTypes ) ) );

		filter.addFilter( new ClassifierFilter( DependencyUtil.cleanToBeTokenizedString( this.includeClassifiers ),
				DependencyUtil.cleanToBeTokenizedString( this.excludeClassifiers ) ) );

		filter.addFilter( new GroupIdFilter( DependencyUtil.cleanToBeTokenizedString( this.includeGroupIds ),
				DependencyUtil.cleanToBeTokenizedString( this.excludeGroupIds ) ) );

		filter.addFilter( new ArtifactIdFilter( DependencyUtil.cleanToBeTokenizedString( this.includeArtifactIds ),
				DependencyUtil.cleanToBeTokenizedString( this.excludeArtifactIds ) ) );

		// start with all artifacts.
		@SuppressWarnings( "unchecked" ) Set<Artifact> artifacts = project.getArtifacts();

		if ( includeParents )
		{
			// add dependencies parents
			for ( Artifact dep : new ArrayList<Artifact>( artifacts ) )
			{
				addParentArtifacts( buildProjectFromArtifact( dep ), artifacts );
			}

			// add current project parent
			addParentArtifacts( project, artifacts );
		}

		// perform filtering
		try
		{
			artifacts = filter.filter( artifacts );
		}
		catch ( ArtifactFilterException e )
		{
			throw new MojoExecutionException( e.getMessage(), e );
		}

		// transform artifacts if classifier is set
		DependencyStatusSets status;
		if ( StringUtils.isNotEmpty( this.classifier ) )
		{
			status = getClassifierTranslatedDependencies( artifacts, stopOnFailure );
		}
		else
		{
			status = filterMarkedDependencies( artifacts );
		}

		return status;
	}

	@SuppressWarnings("deprecation")
	private MavenProject buildProjectFromArtifact( Artifact artifact )
			throws MojoExecutionException, Exception
	{
		try
		{

			MavenProjectBuilder projectBuilder = new DefaultMavenProjectBuilder();
			return projectBuilder.buildFromRepository( artifact, maven.getArtifactRepositories(), getLocal() );
		}
		catch ( ProjectBuildingException e )
		{
			throw new MojoExecutionException( e.getMessage(), e );
		}
	}


	private ArtifactRepository getLocal() throws Exception {
		ContainerConfiguration config = new DefaultContainerConfiguration();
		config.setAutoWiring( true );
		config.setClassPathScanning( PlexusConstants.SCANNING_INDEX );
		RepositorySystem system =new DefaultPlexusContainer( config ).lookup( RepositorySystem.class );
		ArtifactResolutionRequest request = new ArtifactResolutionRequest();
		request.setArtifact(project.getArtifact());
		request.setRemoteRepositories(maven.getArtifactRepositories());
		ArtifactRepository localRepo = maven.getLocalRepository();
		if ( this.localRepositoryDirectory != null )
		{
			// create a new local repo using existing layout, snapshots, and releases policy
			String url = "file://" + this.localRepositoryDirectory.getAbsolutePath();
			localRepo = system.createArtifactRepository( localRepo.getId(), url, localRepo.getLayout(),localRepo.getSnapshots(), localRepo.getReleases() );

		}
		return localRepo;
	}

	private void addParentArtifacts( MavenProject project, Set<Artifact> artifacts )
			throws MojoExecutionException, Exception
	{
		while ( project.hasParent() )
		{
			project = project.getParent();

			if ( project.getArtifact() == null )
			{	VersionRange vr;
			try
			{
				vr = VersionRange.createFromVersionSpec( project.getVersion() );
			}
			catch ( InvalidVersionSpecificationException e1 )
			{
				e1.printStackTrace();
				vr = VersionRange.createFromVersion( project.getVersion() );
			}
			// Maven 2.x bug
			Artifact artifact =	 new DefaultArtifact( project.getGroupId(), project.getArtifactId(),vr,Artifact.SCOPE_COMPILE,project.getPackaging(),null,new DefaultArtifactHandler(project.getPackaging()));
			project.setArtifact( artifact );
			}

			if ( !artifacts.add( project.getArtifact() ) )
			{
				// artifact already in the set
				break;
			}
			try
			{
				ContainerConfiguration config = new DefaultContainerConfiguration();
				config.setAutoWiring( true );
				config.setClassPathScanning( PlexusConstants.SCANNING_INDEX );
				RepositorySystem system =new DefaultPlexusContainer( config ).lookup( RepositorySystem.class );
				ArtifactResolutionRequest request = new ArtifactResolutionRequest();
				request.setArtifact(project.getArtifact());
				request.setRemoteRepositories(maven.getArtifactRepositories());
				request.setLocalRepository(getLocal());
				//resolve the artifact
				system.resolve(request);


			}
			catch ( Exception e )
			{
				throw new MojoExecutionException( "Unable to resolve artifact "+e.getMessage(), e );
			}

		}
	}

	protected DependencyStatusSets getClassifierTranslatedDependencies( Set<Artifact> artifacts, boolean stopOnFailure )
			throws  Exception
	{
		Set<Artifact> unResolvedArtifacts = new HashSet<Artifact>();
		Set<Artifact> resolvedArtifacts = artifacts;
		DependencyStatusSets status = new DependencyStatusSets();

		// possibly translate artifacts into a new set of artifacts based on the
		// classifier and type
		// if this did something, we need to resolve the new artifacts
		if ( StringUtils.isNotEmpty( classifier ) )
		{
			ClassifierTypeTranslator translator = new ClassifierTypeTranslator( this.classifier, this.type);
			artifacts = translator.translate( artifacts);

			status = filterMarkedDependencies( artifacts );

			// the unskipped artifacts are in the resolved set.
			artifacts = status.getResolvedDependencies();

			// resolve the rest of the artifacts
			DefaultArtifactsResolver artifactsResolver =
					new DefaultArtifactsResolver(this.getLocal(), maven.getArtifactRepositories(), stopOnFailure );
			resolvedArtifacts = artifactsResolver.resolve( artifacts);

			// calculate the artifacts not resolved.
			unResolvedArtifacts.addAll( artifacts );
			unResolvedArtifacts.removeAll( resolvedArtifacts );
		}

		// return a bean of all 3 sets.
		status.setResolvedDependencies( resolvedArtifacts );
		status.setUnResolvedDependencies( unResolvedArtifacts );

		return status;
	}


	/**
	 * Filter the marked dependencies
	 *
	 * @param artifacts
	 * @return
	 * @throws MojoExecutionException
	 */
	protected DependencyStatusSets filterMarkedDependencies( Set<Artifact> artifacts )
			throws MojoExecutionException
	{
		//ok, don't filter anything based on markers
		return new DependencyStatusSets( artifacts, null, new HashSet<Artifact>() );
	}

	protected DependencyStatusSets getDependencySets( boolean stopOnFailure )
			throws Exception
	{
		return getDependencySets( stopOnFailure, false );
	}
}
