/*******************************************************************************
 * Copyright (c) 2012 Ian Brandt
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Ian Brandt - initial API and implementation
 *******************************************************************************/
package com.ianbrandt.tools.m2e.mdp.core;

import java.io.File;
import java.util.Set;

import org.apache.maven.plugin.MojoExecution;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.project.configurator.MojoExecutionBuildParticipant;
import org.eclipse.osgi.util.NLS;
import org.sonatype.plexus.build.incremental.BuildContext;

import com.coderplus.utils.BuildHelper;
import com.coderplus.utils.Constants;

public class MdpBuildParticipant extends MojoExecutionBuildParticipant {




	public MdpBuildParticipant(MojoExecution execution) {

		super(execution, true);
	}

	@Override
	public Set<IProject> build(final int kind, final IProgressMonitor monitor) throws Exception {

		final IMaven maven = MavenPlugin.getMaven();
		final MojoExecution mojoExecution = getMojoExecution();
		final BuildContext buildContext = getBuildContext();
		if (mojoExecution == null) {
			return null;
		}

		final IFile pomFile = (IFile) getMavenProjectFacade().getProject().findMember("pom.xml");

		// skipping the build if not a Full Build and pom.xml has not changed
		if(kind != IncrementalProjectBuilder.FULL_BUILD && !buildContext.hasDelta(pomFile.getLocation().toFile())) {
			return null;
		}

		setTaskName(monitor);

		String goal = mojoExecution.getGoal();
		Set<File> outputDirectories;

		if(Constants.COPY_GOAL.equals(goal)||Constants.UNPACK_GOAL.equals(goal)){
			outputDirectories=new BuildHelper(maven,mojoExecution,getMavenProjectFacade().getMavenProject(),buildContext).processCopyOrUnpack();
		} else {
			outputDirectories=new BuildHelper(maven,mojoExecution,getMavenProjectFacade().getMavenProject(),buildContext).processDependencies();
		}

		for(File outputDirectory: outputDirectories){
			refreshOutputDirectory(buildContext, outputDirectory);
		}
		return null;
	}



	private void setTaskName(IProgressMonitor monitor) {

		if (monitor != null) {

			final String taskName = NLS.bind("Invoking {0} on {1}", getMojoExecution().getMojoDescriptor()
					.getFullGoalName(), getMavenProjectFacade().getProject().getName());

			monitor.setTaskName(taskName);
		}
	}


	private void refreshOutputDirectory(final BuildContext buildContext, final File outputDirectory) {

		if (outputDirectory != null && outputDirectory.exists()) {

			buildContext.refresh(outputDirectory);
		}
	}
}
