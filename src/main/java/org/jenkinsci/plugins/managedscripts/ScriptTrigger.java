package org.jenkinsci.plugins.managedscripts;

import antlr.ANTLRException;
import hudson.Launcher;
import hudson.FilePath;
import hudson.EnvVars;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Cause;
import hudson.model.TaskListener;
import hudson.model.Item;
import hudson.model.BuildableItem;
import hudson.tasks.BuildStepMonitor;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.managedscripts.ScriptRunner.ArgValue;
import org.jenkinsci.plugins.managedscripts.ScriptRunner.ScriptBuildStepArgs;
import org.jenkinsci.plugins.managedscripts.ScriptConfig.Arg;
import org.jenkinsci.plugins.managedscripts.ScriptConfig.ScriptConfigProvider;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LibraryBuildStep {@link hudson.tasks.Builder}.
 * <p>
 * A project that uses this trigger can choose a script from a list of predefined config files that are uses as command line scripts. The hash-bang sequence at the beginning of each file is used
 * to determine the interpreter. If the script returns with return value 0 a new build is triggered.
 * <p>
 *
 * @author Dominik Ruf
 */
public class ScriptTrigger extends Trigger<BuildableItem> {
    private static Logger log = Logger.getLogger(ScriptTrigger.class.getName());

    private final String buildStepId;
    private final String[] buildStepArgs;

    class ScriptTriggerCause extends Cause {
        private String cause;

        public ScriptTriggerCause(String cause) {
            if (cause != null) {
                this.cause = cause;
            }
        }

        @Override
        public String getShortDescription() {
            return "[managedscript] - " + cause;
        }
    }


    public ScriptTrigger(String spec, List<ScriptConfig> configs){
        this.buildStepId = "asdf";
        List<String> l =  new ArrayList<String>();
        l.add("qwer");
        this.buildStepArgs = l.toArray(new String[l.size()]);
    }
    public ScriptTrigger(){
        this.buildStepId = "asdf";
        List<String> l =  new ArrayList<String>();
        l.add("qwer");
        this.buildStepArgs = l.toArray(new String[l.size()]);
    }
    /**
     * The constructor used at form submission
     *
     * @param buildStepId
     *            the Id of the config file
     * @param scriptBuildStepArgs
     *            whether to save the args and arg values (the boolean is required because of html form submission, which also sends hidden values)
     */
    @DataBoundConstructor
    public ScriptTrigger(String buildStepId, ScriptBuildStepArgs scriptBuildStepArgs) throws ANTLRException {
        super("* * * * *");
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

    public ScriptTrigger(String buildStepId, String[] buildStepArgs) {
        this.buildStepId = buildStepId;
        this.buildStepArgs = buildStepArgs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        try {
            Launcher launcher = new Launcher.LocalLauncher(TaskListener.NULL);
            FilePath workingDir = new FilePath(new FilePath(this.job.getRootDir()), "workspace");
            EnvVars env = new EnvVars();
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            PrintStream psout = new PrintStream(stdout);
            PrintStream pserr = new PrintStream(stderr);
            Config buildStepConfig = getDescriptor().getBuildStepConfigById(buildStepId);
            if(ScriptRunner.run(buildStepConfig, psout, pserr, buildStepId, buildStepArgs, log, env, workingDir, launcher, null)) {
                Cause cause = new ScriptTriggerCause("script \"" + buildStepConfig.name + "\" returned 0 and said <br/>\n" + stdout.toString("ISO-8859-1"));
                job.scheduleBuild(0, cause);
            }
            log.log(Level.FINE, stdout.toString("ISO-8859-1"));
            if(stderr.size() > 0){
                log.log(Level.WARNING, stderr.toString("ISO-8859-1"));
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "Cannot create temporary directory for ScriptRunner ");
            log.log(Level.WARNING, e.getMessage(), e);
        } catch (Exception e) {
            log.log(Level.WARNING, "Caught exception while setting environment for ScriptRunner");
            log.log(Level.WARNING, e.getMessage(), e);
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public String getBuildStepId() {
        return buildStepId;
    }

    public String[] getBuildStepArgs() {
        return buildStepArgs;
    }

    // Overridden for better type safety.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link org.jenkinsci.plugins.managedscripts.ScriptTrigger}.
     */
    @Extension(ordinal = 50)
    public static final class DescriptorImpl extends TriggerDescriptor {
        final Logger logger = Logger.getLogger(ScriptTrigger.class.getName());

        @Override
        public boolean isApplicable(Item item) {
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
         * @return A collection of config files of type {@link org.jenkinsci.plugins.managedscripts.ScriptConfig}.
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
