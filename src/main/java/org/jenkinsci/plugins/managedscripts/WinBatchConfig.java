/**
 * 
 */
package org.jenkinsci.plugins.managedscripts;

import hudson.Extension;

import java.util.List;

import org.jenkinsci.lib.configprovider.AbstractConfigProviderImpl;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.lib.configprovider.model.ContentType;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Dominik Bartholdi (imod)
 * 
 */
public class WinBatchConfig extends ScriptConfig {

    @DataBoundConstructor
    public WinBatchConfig(String id, String name, String comment, String content, List<Arg> args) {
        super(id, name, comment, content, args);
    }

    @Extension(ordinal = 70)
    public static class WinBatchConfigProvider extends AbstractConfigProviderImpl {

        public WinBatchConfigProvider() {
            load();
        }

        @Override
        public ContentType getContentType() {
            return ContentType.DefinedType.HTML;
        }

        @Override
        public String getDisplayName() {
            return Messages.win_buildstep_provider_name();
        }

        @Override
        public Config newConfig() {
            String id = getProviderId() + System.currentTimeMillis();
            return new WinBatchConfig(id, "Build Step", "", "echo hello", null);
        }
    }
}
