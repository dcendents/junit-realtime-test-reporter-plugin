/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

package org.jenkinsci.plugins.junitrealtimetestreporter;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.tasks.junit.JUnitParser;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.tasks.test.TestResult;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.FilePathUtils;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.StepNode;

@Extension
public class PipelineAttacher implements GraphListener {

    private static final Logger LOGGER = Logger.getLogger(PipelineAttacher.class.getName());

    private final Map<FlowNode, PipelineRealtimeTestResultAction> attachedActions = Collections.synchronizedMap(new WeakHashMap<FlowNode, PipelineRealtimeTestResultAction>());

    @Override
    public void onNewHead(FlowNode node) {
        if (node instanceof BlockStartNode) {
            WorkspaceAction wsa = node.getPersistentAction(WorkspaceAction.class);
            if (wsa != null) {
                FilePath workspace = wsa.getWorkspace();
                if (workspace == null) {
                    return;
                }
                Queue.Executable executable;
                try {
                    executable = node.getExecution().getOwner().getExecutable();
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, null, x);
                    return;
                }
                if (executable instanceof Run) {
                    Run<?, ?> run = (Run<?, ?>) executable;
                    Job<?, ?> job = run.getParent();
                    if (!PerJobConfiguration.getConfig(job).reportInRealtime) {
                        return;
                    }
                    Run<?, ?> last = job.getLastSuccessfulBuild();
                    if (last == null) {
                        return;
                    }
                    // TODO look for all junit on last, and collect all patterns
                    if (node instanceof StepNode && ((StepNode) node).getDescriptor().getFunctionName().equals("step")) {
                        Object delegate = ArgumentsAction.getResolvedArguments(node).get("delegate");
                        if (delegate instanceof UninstantiatedDescribable) {
                            UninstantiatedDescribable ud = (UninstantiatedDescribable) delegate;
                            DescribableModel<?> model = ud.getModel();
                            if (model != null && model.getType() == JUnitResultArchiver.class) {
                                try {
                                    JUnitResultArchiver archiver = ud.instantiate(JUnitResultArchiver.class);
                                    // TODO etc.
                                } catch (Exception x) {
                                    LOGGER.log(Level.WARNING, null, x);
                                }
                            }
                        }
                    }
                    attachedActions.put(node, new PipelineRealtimeTestResultAction(run, workspace, /* TODO */false, /* TODO */"nada"));
                }
            }
        } else if (node instanceof BlockEndNode) {
            PipelineRealtimeTestResultAction action = attachedActions.remove(((BlockEndNode) node).getStartNode());
            if (action != null) {
                action.run.removeAction(action);
            }
        }
    }

    private static class PipelineRealtimeTestResultAction extends AbstractRealtimeTestResultAction {

        private final String node;
        private final String workspace;
        private final boolean keepLongStdio;
        private final String glob;

        PipelineRealtimeTestResultAction(Run<?, ?> owner, FilePath ws, boolean keepLongStdio, String glob) {
            super(owner);
            node = FilePathUtils.getNodeName(ws);
            workspace = ws.getRemote();
            this.keepLongStdio = keepLongStdio;
            this.glob = glob;
        }

        @Override
        protected TestResult parse() throws IOException, InterruptedException {
            return new JUnitParser(keepLongStdio).parseResult(glob, run, FilePathUtils.find(node, workspace), null, null);
        }

    }

}
