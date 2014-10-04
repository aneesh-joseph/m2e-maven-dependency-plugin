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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.dependency.fromConfiguration.ArtifactItem;
import org.apache.maven.plugin.dependency.fromConfiguration.ProcessArtifactItemsRequest;
import org.apache.maven.plugin.dependency.utils.DependencyUtil;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.archiver.AbstractUnArchiver;
import org.codehaus.plexus.archiver.bzip2.BZip2UnArchiver;
import org.codehaus.plexus.archiver.gzip.GZipUnArchiver;
import org.codehaus.plexus.archiver.tar.TarBZip2UnArchiver;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.archiver.tar.TarUnArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.project.configurator.MojoExecutionBuildParticipant;
import org.sonatype.plexus.build.incremental.BuildContext;

public class CoderPlusBuildParticipant extends MojoExecutionBuildParticipant {



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
		this.excludes = maven.getMojoParameterValue(project, execution, EXCLUDES,String.class, new NullProgressMonitor());
		this.includes = maven.getMojoParameterValue(project, execution, INCLUDES,String.class, new NullProgressMonitor());
		this.ignorePermissions =  Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, IGNORE_PERMISSIONS,Boolean.class, new NullProgressMonitor()));
		this.useJvmChmod =  Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, USE_JVM_CHMOD,Boolean.class, new NullProgressMonitor()));


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
				getBaseVersionMethod = new  PropertyDescriptor(BASE_VERSION, artifactItem.getClass()).getReadMethod();
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
			_artifactItem.setBaseVersion((String) getBaseVersionMethod.invoke(artifactItem));
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

		//refresh the output directories
		final BuildContext buildContext = getBuildContext();
		for(File refreshableDirectory: refreshableDirectories){
			buildContext.refresh(refreshableDirectory);
		}
		return null;	

	}


	private void copyArtifact(ArtifactItem artifactItem) throws Exception {
		File destFile = new File( artifactItem.getOutputDirectory(), artifactItem.getDestFileName() );
		File srcFile = artifactItem.getArtifact().getFile();
		//In case eclipse resolves the artifact to the outputDirectory of a workspace project, just ignore it and get the stuff from the local repo.
		if(srcFile.isDirectory()){
			srcFile = new File(maven.getLocalRepository().getBasedir(),maven.getLocalRepository().pathOf(artifactItem.getArtifact()));
		}

		if(srcFile.exists()){
			FileUtils.copyFile(srcFile, destFile );
		} else {
			throw new Exception("Unable to resolve artifact"+artifactItem.toString());
		}
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

	private Artifact getArtifact(ArtifactItem artifactItem) throws CoreException, InvalidRepositoryException, ComponentLookupException, PlexusContainerException {

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
		ArtifactRepository localRepo = maven.getLocalRepository();
		if ( this.localRepositoryDirectory != null )
		{
			// create a new local repo using existing layout, snapshots, and releases policy
			String url = "file://" + this.localRepositoryDirectory.getAbsolutePath();
			localRepo = system.createArtifactRepository( localRepo.getId(), url, localRepo.getLayout(),localRepo.getSnapshots(), localRepo.getReleases() );

		}

		request.setLocalRepository(localRepo);
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


}
