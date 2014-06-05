package org.jenkinsci.plugins.managedscripts;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.tasks.Builder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;


import org.jenkinsci.lib.configprovider.model.Config;
import org.kohsuke.stapler.DataBoundConstructor;

import org.jenkinsci.plugins.managedscripts.ScriptRunner.ArgValue;
import org.jenkinsci.plugins.managedscripts.ScriptRunner.ScriptBuildStepArgs;

/**
 * LibraryBuildStep {@link Builder}.
 * <p>
 * A project that uses this builder can choose a build step from a list of predefined config files that are uses as command line scripts. The hash-bang sequence at the beginning of each file is used
 * to determine the interpreter.
 * <p>
 *
 * @author Norman Baumann
 * @author Dominik Bartholdi (imod)
 * @author Dominik Ruf
 */
public class ScriptPostBuild extends Notifier {
    private static Logger log = Logger.getLogger(ScriptRunner.class.getName());

    private final String buildStepId;
    private final String[] buildStepArgs;

    /**
     * The constructor used at form submission
     *
     * @param buildStepId
     *            the Id of the config file
     * @param scriptBuildStepArgs
     *            whether to save the args and arg values (the boolean is required because of html form submission, which also sends hidden values)
     */
    @DataBoundConstructor
    public ScriptPostBuild(String buildStepId, ScriptBuildStepArgs scriptBuildStepArgs)
    {
        this.buildStepId = buildStepId;
        List<String> l = null;
        if (scriptBuildStepArgs != null && scriptBuildStepArgs.defineArgs
                && scriptBuildStepArgs.buildStepArgs != null) {
            l = new ArrayList<String>();
            for (ArgValue arg : scriptBuildStepArgs.buildStepArgs) {
                l.add(arg.arg);
            }
        }
        this.buildStepArgs = l == null ? null : l.toArray(new String[l.size()]);
    }

    public ScriptPostBuild(String buildStepId, String[] buildStepArgs) {
        this.buildStepId = buildStepId;
        this.buildStepArgs = buildStepArgs;
    }

    public String getBuildStepId() {
        return buildStepId;
    }

    public String[] getBuildStepArgs() {
        return buildStepArgs;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        boolean returnValue = false;
        try {
            FilePath workingDir = build.getWorkspace();
            EnvVars env = build.getEnvironment(listener);
            env.put("BUILD_RESULT", build.getResult().toString());
            Config buildStepConfig = getDescriptor().getBuildStepConfigById(buildStepId);
            returnValue = ScriptRunner.run(buildStepConfig, listener.getLogger(), listener.getLogger(), buildStepId, buildStepArgs, log, env, workingDir, launcher, build);
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError("Cannot create temporary directory for ScriptRunner "));
        } catch (Exception e) {
            e.printStackTrace(listener.fatalError("Caught exception while setting environment for ScriptRunner"));
        }
        return returnValue;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public DescriptorImpl getDescriptor(){
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension(ordinal = 50)
    public static class DescriptorImpl extends ScriptRunner.DescriptorImpl<Publisher>{
    }
}
