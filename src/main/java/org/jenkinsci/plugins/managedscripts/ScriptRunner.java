package org.jenkinsci.plugins.managedscripts;

import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Computer;
import hudson.model.Describable;
import hudson.model.AbstractProject;
import hudson.tasks.Shell;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.managedscripts.ScriptConfig.Arg;
import org.jenkinsci.plugins.managedscripts.ScriptConfig.ScriptConfigProvider;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import org.kohsuke.stapler.DataBoundConstructor;


public class ScriptRunner {

    public static class ArgValue {
        public final String arg;

        @DataBoundConstructor
        public ArgValue(String arg) {
            this.arg = arg;
        }
    }

    public static class ScriptBuildStepArgs {
        public final boolean defineArgs;
        public final ArgValue[] buildStepArgs;

        @DataBoundConstructor
        public ScriptBuildStepArgs(boolean defineArgs, ArgValue[] buildStepArgs)
        {
            this.defineArgs = defineArgs;
            this.buildStepArgs = buildStepArgs;
        }
    }

    /**
     * Perform the build step on the execution host.
     * <p>
     * Generates a temporary file and copies the content of the predefined config file (by using the buildStepId) into it. It then copies this file into the workspace directory of the execution host
     * and executes it.
     */
    public static boolean run(Config buildStepConfig, PrintStream stdout, PrintStream stderr, String buildStepId, String[] buildStepArgs, Logger log, EnvVars env, FilePath workingDir, Launcher launcher, AbstractBuild<?, ?> build) throws IOException, InterruptedException {
        boolean returnValue = true;
        if (buildStepConfig == null) {
            stderr.println(Messages.config_does_not_exist(buildStepId));
            return false;
        }
        stdout.println("executing script '" + buildStepConfig.name + "'");
        FilePath dest = null;
        String data = buildStepConfig.content;

        /*
         * Copying temporary file to remote execution host
         */
        String cl = buildStepConfig.getClass().getName();
        String ext = ".sh";
        if(cl.equals("org.jenkinsci.plugins.managedscripts.WinBatchConfig")){
            ext = ".bat";
        }
        dest = workingDir.createTextTempFile("build_step_template", ext, data, false);
        log.log(Level.FINE, "Wrote script to " + Computer.currentComputer().getDisplayName() + ":" + dest.getRemote());

        /*
         * Analyze interpreter line (and use the desired interpreter)
         */
        ArgumentListBuilder args = new ArgumentListBuilder();
        if (data.startsWith("#!")) {
            String interpreterLine = data.substring(2, data.indexOf("\n"));
            String[] interpreterElements = interpreterLine.split("\\s+");
            // Add interpreter to arguments list
            String interpreter = interpreterElements[0];
            args.add(interpreter);
            log.log(Level.FINE, "Using custom interpreter: " + interpreterLine);
            // Add addition parameter to arguments list
            for (int i = 1; i < interpreterElements.length; i++) {
                args.add(interpreterElements[i]);
            }
        } else {
            // the shell executable is already configured for the Shell
            // task, reuse it
            if(!cl.equals("org.jenkinsci.plugins.managedscripts.WinBatchConfig")) {
                final Shell.DescriptorImpl shellDescriptor = (Shell.DescriptorImpl) Jenkins.getInstance().getDescriptor(Shell.class);
                final String interpreter = shellDescriptor.getShellOrDefault(workingDir.getChannel());
                args.add(interpreter);
            }
        }

        args.add(dest.getRemote());

        // Add additional parameters set by user
        if (buildStepArgs != null) {
            for (String arg : buildStepArgs) {
                try {
                    // TODO: is there a way to also do this for triggers?
                    if(build != null){
                        args.add(TokenMacro.expandAll(build, null, arg, false, null));
                    }else{
                        args.add(arg);
                    }
                } catch (MacroEvaluationException e) {
                    log.log(Level.WARNING, "Error expanding Token: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        /*
         * Execute command remotely
         */
        int r = launcher.launch().cmds(args).envs(env).stderr(stderr).stdout(stdout).pwd(workingDir).join();
        returnValue = (r == 0);

        log.log(Level.FINE, "Finished script step");
        return returnValue;
    }

    /**
     * Descriptor for {@link ScriptRunner}.
     */
    //@Extension(ordinal = 50)
    public static class DescriptorImpl<T extends Describable<T> & BuildStep> extends BuildStepDescriptor<T> {
        final Logger logger = Logger.getLogger(ScriptRunner.class.getName());

        /**
         * Enables this builder for all kinds of projects.
         */
        //@Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        @Override
        public String getDisplayName() {
            return Messages.buildstep_name();
        }

        /**
         * Return all config files (templates) that the user can choose from when creating a build step. Ordered by name.
         *
         * @return A collection of config files of type {@link ScriptConfig}.
         */
        public Collection<Config> getAvailableBuildTemplates() {
            ExtensionList<ConfigProvider> providers = ConfigProvider.all();
            ScriptConfigProvider scriptProviders = providers.get(ScriptConfigProvider.class);
            WinBatchConfig.WinBatchConfigProvider batchProviders = providers.get(WinBatchConfig.WinBatchConfigProvider.class);
            List<Config> allConfigs = new ArrayList<Config>(scriptProviders.getAllConfigs());
            allConfigs.addAll(batchProviders.getAllConfigs());
            Collections.sort(allConfigs, new Comparator<Config>() {
                public int compare(Config o1, Config o2) {
                    return o1.name.compareTo(o2.name);
                }
            });
            return allConfigs;
        }

        /**
         * Returns a Config object for a given config file Id.
         *
         * @param id
         *            The Id of a config file.
         * @return If Id can be found a Config object that represents the given Id is returned. Otherwise null.
         */
        public ScriptConfig getBuildStepConfigById(String id) {
            ExtensionList<ConfigProvider> providers = ConfigProvider.all();
            ScriptConfigProvider scriptProviders = providers.get(ScriptConfigProvider.class);
            WinBatchConfig.WinBatchConfigProvider batchProviders = providers.get(WinBatchConfig.WinBatchConfigProvider.class);
            ScriptConfig r = (ScriptConfig) scriptProviders.getConfigById(id);
            if(r == null){
                r = (ScriptConfig) batchProviders.getConfigById(id);
            }
            return r;
        }

        /**
         * gets the argument description to be displayed on the screen when selecting a config in the dropdown
         *
         * @param configId
         *            the config id to get the arguments description for
         * @return the description
         */
        @JavaScriptMethod
        public String getArgsDescription(String configId) {
            final ScriptConfig config = getBuildStepConfigById(configId);
            if (config != null) {
                if (config.args != null && !config.args.isEmpty()) {
                    StringBuilder sb = new StringBuilder("Required arguments: ");
                    int i = 1;
                    for (Iterator<Arg> iterator = config.args.iterator(); iterator.hasNext(); i++) {
                        Arg arg = iterator.next();
                        sb.append(i).append(". ").append(arg.name);
                        if (iterator.hasNext()) {
                            sb.append(" | ");
                        }
                    }
                    return sb.toString();
                } else {
                    return "No arguments required";
                }
            }
            return "please select a script!";
        }

        @JavaScriptMethod
        public List<Arg> getArgs(String configId) {
            final ScriptConfig config = getBuildStepConfigById(configId);
            return config.args;
        }

        /**
         * validate that an existing config was chosen
         *
         * @param buildStepId
         *            the configId
         * @return
         */
        public FormValidation doCheckBuildStepId(@QueryParameter String buildStepId) {
            final ScriptConfig config = getBuildStepConfigById(buildStepId);
            if (config != null) {
                return FormValidation.ok();
            } else {
                return FormValidation.error("you must select a valid script");
            }
        }

        private ConfigProvider getBuildStepConfigProvider() {
            ExtensionList<ConfigProvider> providers = ConfigProvider.all();
            return providers.get(ScriptConfigProvider.class);
        }

    }
}
