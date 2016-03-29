package org.jenkinsci.plugins.workflow.cps.refdoc;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.CopyOnWriteMap;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * DSL reference.
 *
 */
@Extension
@Restricted(NoExternalUse.class) // this entire class is for GUI only
public class DslReference {
    private final CopyOnWriteMap<String,VariableReference> vars = new CopyOnWriteMap.Hash<>();
    private final CopyOnWriteMap<String,StepReferenence> steps = new CopyOnWriteMap.Tree<>();

    public Collection<? extends StepDescriptor> getStepDescriptors(boolean advanced) {
        TreeSet<StepDescriptor> t = new TreeSet<StepDescriptor>(new StepDescriptorComparator());
        for (StepDescriptor d : StepDescriptor.all()) {
            if (d.isAdvanced() == advanced) {
                t.add(d);
            }
        }
        return t;
    }

    public Iterable<GlobalVariable> getGlobalVariables() {
        // TODO order TBD. Alphabetical? Extension.ordinal?
        return GlobalVariable.ALL;
    }

    public VariableReference getVariable(String name) {
        for (GlobalVariable v : GlobalVariable.ALL) {
            if (v.getName().equals(name) {
                return new VariableReference(v);
            }
        }
    }

    private void rebuild() {
        Map<String,VariableReference> vars = new TreeMap<>();
        for (GlobalVariable v : GlobalVariable.ALL) {
            vars.put(v.getName(),new VariableReference(v));
        }
        this.vars.replaceBy(vars);

        Map<String,StepReferenence> steps = new HashMap<>();
        TreeSet<StepDescriptor> t = new TreeSet<StepDescriptor>(new StepDescriptorComparator());
        for (StepDescriptor d : StepDescriptor.all()) {
            steps.put(d.getFunctionName(),new StepReferenence(d));
        }
        this.steps.replaceBy(steps);
    }

    private static class StepDescriptorComparator implements Comparator<StepDescriptor>, Serializable {
        @Override
        public int compare(StepDescriptor o1, StepDescriptor o2) {
            return o1.getFunctionName().compareTo(o2.getFunctionName());
        }
        private static final long serialVersionUID = 1L;
    }

    @Initializer(after= InitMilestone.EXTENSIONS_AUGMENTED)
    public static void init() {
        DslReference r = Jenkins.getInstance().getInjector().getInstance(DslReference.class);
        if (r!=null)
            r.rebuild();
    }
}
