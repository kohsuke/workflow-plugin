package org.jenkinsci.plugins.workflow.cps;

import org.jvnet.hudson.annotation_indexer.Indexed;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author Kohsuke Kawaguchi
 */
@Retention(RetentionPolicy.RUNTIME)
@Indexed
public @interface GlobalProperty {
}
