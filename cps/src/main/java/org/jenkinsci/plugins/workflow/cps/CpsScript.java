/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.cps;

import org.apache.commons.io.Charsets;
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;
import com.cloudbees.groovy.cps.SerializableScript;
import groovy.lang.GroovyShell;
import hudson.EnvVars;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.Run;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import javax.annotation.CheckForNull;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.DefaultGroovyStaticMethods;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.*;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;

/**
 * The script of a workflow.
 * @author Kohsuke Kawaguchi
 */
@PersistIn(PROGRAM)
public abstract class CpsScript extends SerializableScript {

    private static final String STEPS_VAR = "steps";
    static final String PROP_ENV = "env";
    static final String PROP_BUILD = "currentBuild";

    transient CpsFlowExecution execution;

    /** Default constructor for {@link CpsFlowExecution}. */
    public CpsScript() throws IOException {
        // if the script is instantiated in workflow, automatically set up the contextual
        // 'execution' object. This allows those scripts to invoke workflow steps without
        // any special setup, making it easy to write reusable functions.
        CpsThread c = CpsThread.current();
        if (c!=null) {
            execution = c.getExecution();
            $initialize();
        }
    }

    @SuppressWarnings("unchecked") // Binding
    final void $initialize() throws IOException {
        getBinding().setVariable(STEPS_VAR, new DSL(execution.getOwner()));
        Run<?,?> run = $build();
        if (run != null) {
            EnvVars paramEnv = new EnvVars();
            ParametersAction a = run.getAction(ParametersAction.class);
            if (a != null) {
                for (ParameterValue v : a) {
                    v.buildEnvironment(run, paramEnv);
                }
            }
            EnvVars.resolve(paramEnv);
            getBinding().getVariables().putAll(paramEnv);
        }
    }


    /**
     * We use DSL here to try invoke the step implementation, if there is Step implementation found it's handled or
     * it's an error.
     *
     * <p>
     * sandbox security execution relies on the assumption that CpsScript.invokeMethod() is safe for sandboxed code.
     * That means we cannot let user-written script override this method, hence the final.
     */
    @Override
    public final Object invokeMethod(String name, Object args) {
        DSL dsl = (DSL) getBinding().getVariable(STEPS_VAR);
        return dsl.invokeMethod(name,args);
    }

    @Override
    public Object getProperty(String property) {
        if (property.equals(PROP_ENV)) {
            return $env();
        } else if (property.equals(PROP_BUILD)) {
            try {
                return new RunWrapper($build(), true);
            } catch (IOException x) {
                throw new InvokerInvocationException(x);
            }
        } else if (property.equals("foobar")) {// Test to get the point across
            GroovyShell shell = execution.getShell();
            return shell.parse(new InputStreamReader(shell.getClassLoader().getResourceAsStream("test/FooBar.groovy"), Charsets.UTF_8), "test/FooBar.groovy");
        }

        return super.getProperty(property);
    }

    private @CheckForNull Run<?,?> $build() throws IOException {
        FlowExecutionOwner owner = execution.getOwner();
        Queue.Executable qe = owner.getExecutable();
        if (qe instanceof Run) {
            return (Run) qe;
        } else {
            return null;
        }
    }

    private EnvActionImpl $env() {
        try {
            Run<?,?> run = $build();
            if (run != null) {
                EnvActionImpl action = run.getAction(EnvActionImpl.class);
                if (action == null) {
                    action = new EnvActionImpl();
                    run.addAction(action);
                }
                return action;
            } else {
                return null;
            }
        } catch (RuntimeException x) {
            throw x;
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    @Override
    public Object evaluate(String script) throws CompilationFailedException {
        // this might throw the magic CpsCallableInvocation to execute the script asynchronously
        return $getShell().evaluate(script);
    }

    @Override
    public Object evaluate(File file) throws CompilationFailedException, IOException {
        return $getShell().evaluate(file);
    }

    @Override
    public void run(File file, String[] arguments) throws CompilationFailedException, IOException {
        $getShell().run(file,arguments);
    }

    /**
     * Obtains the Groovy compiler to be used for compiling user script
     * in the CPS-transformed and sandboxed manner.
     */
    private GroovyShell $getShell() {
        return CpsThreadGroup.current().getExecution().getShell();
    }

    protected Object readResolve() {
        execution = CpsFlowExecution.PROGRAM_STATE_SERIALIZATION.get();
        assert execution!=null;
        return this;
    }

    @Override
    public void println() {
        invokeMethod("echo", "");
    }

    @Override
    public void print(Object value) {
        // TODO: handling 'print' correctly requires collapsing multiple adjacent print calls into one Step.
        println(value);
    }

    @Override
    public void println(Object value) {
        invokeMethod("echo", String.valueOf(value));
    }

    @Override
    public void printf(String format, Object value) {
        print(DefaultGroovyMethods.sprintf(this/*not actually used*/, format, value));
    }

    @Override
    public void printf(String format, Object[] values) {
        print(DefaultGroovyMethods.sprintf(this/*not actually used*/, format, values));
    }

    /** Effectively overrides {@link DefaultGroovyStaticMethods#sleep(Object, long)} so that {@code SleepStep} works even in the bare form {@code sleep 5}. */
    public Object sleep(long arg) {
        return invokeMethod("sleep", arg);
    }

    private static final long serialVersionUID = 1L;
}
