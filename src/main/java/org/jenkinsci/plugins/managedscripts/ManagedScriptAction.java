package org.jenkinsci.plugins.managedscripts;

import hudson.model.BuildBadgeAction;

import java.util.HashMap;

/**
 * Created by rufdo on 04.07.2014.
 */
public class ManagedScriptAction implements BuildBadgeAction {
    public HashMap<String,String> tokens = new HashMap<String, String>();

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }
}
