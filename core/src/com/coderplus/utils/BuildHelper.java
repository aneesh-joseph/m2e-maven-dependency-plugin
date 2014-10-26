package com.coderplus.utils;


import java.beans.PropertyDescriptor;
import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.LegacyLocalRepositoryManager;
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
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ClassifierFilter;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.GroupIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
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
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.m2e.core.embedder.IMaven;
import org.sonatype.plexus.build.incremental.BuildContext;

import com.coderplus.apacheutils.translators.ClassifierTypeTranslator;
import com.coderplus.apacheutils.translators.resolvers.DefaultArtifactsResolver;
import com.coderplus.utils.apache.DependencyStatusSets;
import com.coderplus.utils.apache.DependencyUtil;

@SuppressWarnings("deprecation")
public class BuildHelper {

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
	private MojoExecution execution;
	private RepositorySystem repoSystem;
	private RepositorySystemSession session;
	private BuildContext buildContext;

	public BuildHelper(IMaven maven, MojoExecution execution, MavenProject mavenProject, BuildContext buildContext) throws Exception {
		this.execution = execution;
		this.project = mavenProject;
		this.maven=maven;
		this.goal = execution.getGoal();
		this.buildContext = buildContext;
		this.skip =  Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, "skip",Boolean.class, new NullProgressMonitor()));
		this.globalOutputDirectory =maven.getMojoParameterValue(project, execution, Constants.OUTPUT_DIRECTORY,File.class, new NullProgressMonitor());
		this.overWriteReleases = Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, Constants.OVER_WRITE_RELEASES,Boolean.class, new NullProgressMonitor()));
		this.overWriteSnapshots = Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution,Constants. OVER_WRITE_SNAPSHOTS,Boolean.class, new NullProgressMonitor()));
		this.overWriteIfNewer = Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, Constants.OVER_IF_NEWER,Boolean.class, new NullProgressMonitor()));
		this.localRepositoryDirectory = maven.getMojoParameterValue(project, execution, Constants.LOCAL_REPOSITORY_DIRECTORY,File.class, new NullProgressMonitor());
		this.prependGroupId =  Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, Constants.PREPEND_GROUP_ID,Boolean.class, new NullProgressMonitor()));
		this.stripVersion =  Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, Constants.STRIP_VERSION,Boolean.class, new NullProgressMonitor()));
		this.useBaseVersion =  Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, Constants.USE_BASE_VERSION,Boolean.class, new NullProgressMonitor()));
		this.stripClassifier =  Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, Constants.STRIP_CLASSIFIER,Boolean.class, new NullProgressMonitor()));
		this.useSubDirectoryPerScope = Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, Constants.STRIP_CLASSIFIER,Boolean.class, new NullProgressMonitor()));
		this.useSubDirectoryPerType = Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, Constants.USE_SUB_DIRECTORY_PER_TYPE,Boolean.class, new NullProgressMonitor()));
		this.useSubDirectoryPerArtifact = Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, Constants.USE_SUB_DIRECTORY_PER_ARTIFACT,Boolean.class, new NullProgressMonitor()));
		this.useRepositoryLayout = Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, Constants.USE_REPOSITORY_LAYOUT,Boolean.class, new NullProgressMonitor()));
		this.failOnMissingClassifierArtifact = Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, Constants.FAIL_ON_MISSING_CLASSIFIER_ARTIFACT,Boolean.class, new NullProgressMonitor()));
		this.excludes = maven.getMojoParameterValue(project, execution, Constants.EXCLUDES,String.class, new NullProgressMonitor());
		this.includes = maven.getMojoParameterValue(project, execution, Constants.INCLUDES,String.class, new NullProgressMonitor());
		this.ignorePermissions =  Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, Constants.IGNORE_PERMISSIONS,Boolean.class, new NullProgressMonitor()));
		this.copyPom =  Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, Constants.COPY_POM,Boolean.class, new NullProgressMonitor()));
		this.useJvmChmod =  Boolean.TRUE.equals(maven.getMojoParameterValue(project, execution, Constants.USE_JVM_CHMOD,Boolean.class, new NullProgressMonitor()));
		this.includeScope = maven.getMojoParameterValue(project, execution, Constants.INCLUDE_SCOPE,String.class, new NullProgressMonitor());
		this.excludeScope = maven.getMojoParameterValue(project, execution, Constants.EXCLUDE_SCOPE,String.class, new NullProgressMonitor());
		this.includeTypes = maven.getMojoParameterValue(project, execution, Constants.INCLUDE_TYPES,String.class, new NullProgressMonitor());
		this.excludeTypes = maven.getMojoParameterValue(project, execution, Constants.EXCLUDE_TYPES,String.class, new NullProgressMonitor());
		this.includeClassifiers = maven.getMojoParameterValue(project, execution, Constants.INCLUDE_CLASSIFIERS,String.class, new NullProgressMonitor());
		this.excludeClassifiers = maven.getMojoParameterValue(project, execution, Constants.EXCLUDE_CLASSIFIERS,String.class, new NullProgressMonitor());
		this.includeGroupIds = maven.getMojoParameterValue(project, execution, Constants.INCLUDE_GROUP_IDS,String.class, new NullProgressMonitor());
		this.excludeGroupIds = maven.getMojoParameterValue(project, execution, Constants.EXCLUDE_GROUP_IDS,String.class, new NullProgressMonitor());
		this.includeArtifactIds = maven.getMojoParameterValue(project, execution, Constants.INCLUDE_ARTIFACT_IDS,String.class, new NullProgressMonitor());
		this.excludeArtifactIds = maven.getMojoParameterValue(project, execution, Constants.EXCLUDE_ARTIFACT_IDS,String.class, new NullProgressMonitor());	
		this.classifier = maven.getMojoParameterValue(project, execution, Constants.CLASSIFIER,String.class, new NullProgressMonitor());
		String temp=maven.getMojoParameterValue(project, execution, Constants.TYPE,String.class, new NullProgressMonitor());
		this.type = temp!=null? temp:"";

		repoSystem = newRepositorySystem();
		
		session= LegacyLocalRepositoryManager.overlay(getLocal(), null, null);
		
		if(session instanceof  DefaultRepositorySystemSession){
			DefaultRepositorySystemSession tmp = (DefaultRepositorySystemSession) session;
			//Remove the WorkspaceReader so that the artifact is not resolved from the Workspace
			tmp.setWorkspaceReader(null);
			session=tmp;
		}

	}

	public Set<File> processCopyOrUnpack() throws Exception {
		if(!skip){
			@SuppressWarnings("rawtypes")
			final List artifactItems = maven.getMojoParameterValue(project, execution, Constants.ARTIFACT_ITEMS_PROPERTY,List.class, new NullProgressMonitor());
			//gathering the artifact details using reflection
			Method getOutputDirectoryMethod = null, getArtifactIdMethod=null,getGroupIdMethod=null,getVersionMethod=null,getTypeMethod=null,
					getClassifierMethod=null,getDestFileNameMethod=null,getOverWriteMethod=null,getBaseVersionMethod=null,getExcludesMethod=null,
					getIncludesMethod=null;

			for (Object artifactItem : artifactItems) {
				if (getOutputDirectoryMethod == null) {
					getOutputDirectoryMethod = new PropertyDescriptor(Constants.OUTPUT_DIRECTORY, artifactItem.getClass()).getReadMethod();
					getArtifactIdMethod = new  PropertyDescriptor(Constants.ARTIFACT_ID, artifactItem.getClass()).getReadMethod();
					getGroupIdMethod = new  PropertyDescriptor(Constants.GROUP_ID, artifactItem.getClass()).getReadMethod();
					getVersionMethod = new  PropertyDescriptor(Constants.VERSION, artifactItem.getClass()).getReadMethod();
					if(useBaseVersion){
						getBaseVersionMethod = new  PropertyDescriptor(Constants.BASE_VERSION, artifactItem.getClass()).getReadMethod();
					}
					getTypeMethod = new  PropertyDescriptor(Constants.TYPE, artifactItem.getClass()).getReadMethod();
					getClassifierMethod = new  PropertyDescriptor(Constants.CLASSIFIER, artifactItem.getClass()).getReadMethod();
					getDestFileNameMethod= new  PropertyDescriptor(Constants.DEST_FILE_NAME, artifactItem.getClass()).getReadMethod();
					getOverWriteMethod = new  PropertyDescriptor(Constants.OVER_WRITE, artifactItem.getClass()).getReadMethod();
					getIncludesMethod=new  PropertyDescriptor(Constants.INCLUDES, artifactItem.getClass()).getReadMethod();
					getExcludesMethod=new  PropertyDescriptor(Constants.EXCLUDES, artifactItem.getClass()).getReadMethod();
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
					if(Constants.COPY_GOAL.equals(goal)){
						//if copy, then copy the artifact
						copyArtifact( artifactItem );

					} else if(Constants.UNPACK_GOAL.equals(goal)){
						//if unpack, then unpack the artifact using plexus unarchivers.
						unpack(artifactItem.getArtifact(),artifactItem.getOutputDirectory(), artifactItem.getIncludes(),artifactItem.getExcludes() );
						//add the output directory to the set to be refreshed.
						refreshableDirectories.add(artifactItem.getOutputDirectory());
					}
				}
				else
				{

					//do nothing
				}
			}
		}
		return refreshableDirectories;

	}



	public Set<File> processDependencies() throws Exception {
		if(!skip){
			DependencyStatusSets dss = getDependencySets( this.failOnMissingClassifierArtifact );
			if(Constants.UNPACK_DEPENDENCIES_GOAL.equals(goal)){
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
						ArtifactRepository targetRepository = new DefaultArtifactRepository("local",globalOutputDirectory.toURL().toExternalForm(),maven.getLocalRepository().getLayout(),false);

						for ( Artifact artifact : artifacts )
						{
							installArtifact( artifact, targetRepository );
							refreshableDirectories.add(globalOutputDirectory);
						}
					}
					catch ( MalformedURLException e )
					{
						throw new MojoExecutionException( "Could not create outputDirectory repository", e );
					}
				}

				Set<Artifact> skippedArtifacts = dss.getSkippedDependencies();
				/*for ( Artifact artifact : skippedArtifacts )
				{
					 getLog().info( artifact.getId() + " already exists in destination." ) ;
				}*/

				if ( copyPom && !useRepositoryLayout )
				{
					copyPoms( globalOutputDirectory, artifacts, this.stripVersion );
					copyPoms( globalOutputDirectory, skippedArtifacts,this.stripVersion, this.stripClassifier );  // Artifacts that already exist may not yet have poms
				}

			}

			refreshableDirectories.add(globalOutputDirectory);


		}
		return refreshableDirectories;
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

		org.eclipse.aether.artifact.Artifact aetherArtifact = RepositoryUtils.toArtifact(artifact);
		ArtifactRequest request = new ArtifactRequest(aetherArtifact,RepositoryUtils.toRepos(maven.getArtifactRepositories()),"");
		//resolve the artifact
		ArtifactResult result = repoSystem.resolveArtifact(session, request);
		return RepositoryUtils.toArtifact(result.getArtifact());
	}

	private boolean checkIfProcessingNeeded(ArtifactItem artifactItem) {
		return StringUtils.equalsIgnoreCase( artifactItem.getOverWrite(), Constants.TRUE )
				|| writeRequired( artifactItem );
	}

	private ArtifactRepository getLocal() throws Exception {
		ArtifactRepository localRepo = maven.getLocalRepository();
		if ( this.localRepositoryDirectory != null )
		{
			// create a new local repo using existing layout, snapshots, and releases policy
			String url = "file://" + this.localRepositoryDirectory.getAbsolutePath();
			localRepo = new DefaultArtifactRepository(localRepo.getId(),url,maven.getLocalRepository().getLayout(),false);

		}
		return localRepo;
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

	private void copyArtifact(ArtifactItem artifactItem) throws Exception {

		copyFile(artifactItem.getArtifact().getFile(),new File( artifactItem.getOutputDirectory(), artifactItem.getDestFileName() ),artifactItem.getArtifact());

	}

	private void copyArtifact(Artifact artifact, boolean stripVersion, boolean prependGroupId,
			boolean useBaseVersion, boolean stripClassifier) throws Exception {
		String destFileName = DependencyUtil.getFormattedFileName( artifact, stripVersion, prependGroupId, 
				useBaseVersion, stripClassifier );

		File destDir = DependencyUtil.getFormattedOutputDirectory( useSubDirectoryPerScope, useSubDirectoryPerType,
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
			buildContext.refresh(destFile);
		} else {
			throw new Exception("Unable to resolve artifact"+artifact.getGroupId()+":"+artifact.getArtifactId()+":"+artifact.getVersion());
		}

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

			if(checkType(Constants.JAR,artifact.getType(),file) || checkType(Constants.ZIP,artifact.getType(),file)  || checkType(Constants.WAR,artifact.getType(),file) || checkType(Constants.EAR,artifact.getType(),file)){

				unArchiver = new ZipUnArchiver(file);

			} else if(checkType(Constants.TAR,artifact.getType(),file)){

				unArchiver = new TarUnArchiver(file);

			}else if(checkType(Constants.TAR_BZ2,artifact.getType(),file)){

				unArchiver = new TarBZip2UnArchiver(file);

			}else if(checkType(Constants.TAR_GZ,artifact.getType(),file)){

				unArchiver = new TarGZipUnArchiver(file);

			} else if(checkType(Constants.GZ,artifact.getType(),file)){

				unArchiver = new GZipUnArchiver(file);

			} else if(checkType(Constants.BZ2,artifact.getType(),file)){

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

	protected DependencyStatusSets getDependencySets( boolean stopOnFailure )
			throws Exception
	{
		return getDependencySets( stopOnFailure, false );
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
	@SuppressWarnings("unchecked")
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
		Set<Artifact> artifacts = project.getArtifacts();

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

	private Artifact getResolvedPomArtifact(Artifact artifact) {
		Artifact pomArtifact = new DefaultArtifact( artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(),Artifact.SCOPE_COMPILE , "pom", null,new DefaultArtifactHandler("pom"));
		// Resolve the pom artifact using repos
		try
		{
			ArtifactRequest request = new ArtifactRequest(RepositoryUtils.toArtifact(pomArtifact),RepositoryUtils.toRepos(maven.getArtifactRepositories()),"");
			//resolve the artifact
			ArtifactResult result = repoSystem.resolveArtifact(session, request);
			return RepositoryUtils.toArtifact(result.getArtifact());

		}
		catch ( Exception e )
		{
			//getLog().info( e.getMessage() );
		}
		return pomArtifact;
	}

	private MavenProject buildProjectFromArtifact( Artifact artifact )throws Exception
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
				ArtifactRequest request = new ArtifactRequest(RepositoryUtils.toArtifact(project.getArtifact()),RepositoryUtils.toRepos(maven.getArtifactRepositories()),"");
				//resolve the artifact
				ArtifactResult result = repoSystem.resolveArtifact(session, request);
				project.setArtifact(RepositoryUtils.toArtifact(result.getArtifact()));

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
			DefaultArtifactsResolver artifactsResolver = new DefaultArtifactsResolver(this.getLocal(), maven.getArtifactRepositories(), stopOnFailure );
			resolvedArtifacts = artifactsResolver.resolve( artifacts,repoSystem);

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

	private void installBaseSnapshot(Artifact artifact, ArtifactRepository targetRepository) throws Exception {
		if ( artifact.isSnapshot() && !artifact.getBaseVersion().equals( artifact.getVersion() ) )
		{	
			Artifact baseArtifact = new DefaultArtifact( artifact.getGroupId(), artifact.getArtifactId(), artifact.getBaseVersion(),artifact.getScope() ,artifact.getType(), artifact.getClassifier(),new DefaultArtifactHandler(artifact.getType()));
			install( artifact.getFile(), baseArtifact, targetRepository );
		}
	}

	private void install(File file,Artifact artifact, ArtifactRepository targetRepository) throws Exception{
		InstallRequest request = new InstallRequest();

		if(file.isDirectory()){
			file = new File(maven.getLocalRepository().getBasedir(),maven.getLocalRepository().pathOf(artifact));
		}

		org.eclipse.aether.artifact.Artifact mainArtifact  = RepositoryUtils.toArtifact(artifact);
		mainArtifact = mainArtifact.setFile(file);
		request.addArtifact(mainArtifact);
		repoSystem.install(LegacyLocalRepositoryManager.overlay(targetRepository, null, null), request);
	}
	
	private static RepositorySystem newRepositorySystem()
    {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        return locator.getService( RepositorySystem.class );
    }


}
