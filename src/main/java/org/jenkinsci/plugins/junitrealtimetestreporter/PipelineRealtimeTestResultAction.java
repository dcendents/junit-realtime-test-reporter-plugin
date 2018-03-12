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

import hudson.AbortException;
import hudson.FilePath;
import hudson.model.Run;
import hudson.tasks.junit.JUnitParser;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.pipeline.JUnitResultsStepExecution;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.workflow.FilePathUtils;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepContext;

public class PipelineRealtimeTestResultAction extends AbstractRealtimeTestResultAction {

	private static final Logger LOGGER = Logger.getLogger(PipelineRealtimeTestResultAction.class.getName());

	final String id;
	private final String node;
	private final StepContext context;
	private final String workspace;
	private final boolean keepLongStdio;
	private final String glob;

	PipelineRealtimeTestResultAction(String id, StepContext context, FilePath ws, boolean keepLongStdio, String glob) {
		this.id = id;
		this.context = context;
		node = FilePathUtils.getNodeName(ws);
		workspace = ws.getRemote();
		this.keepLongStdio = keepLongStdio;
		this.glob = glob;
	}

	@Override
	public String getDisplayName() {
		if (node.isEmpty()) {
			return Messages.PipelineRealtimeTestResultAction_realtime_test_result_on_master();
		} else {
			return Messages.PipelineRealtimeTestResultAction_realtime_test_result_on_(node);
			// TODO include the branch or stage name (nearest enclosing LabelAction), as in
			// jenkinsci/workflow-durable-task-step-plugin#2
		}
	}

	@Override
	public String getUrlName() {
		return "realtimeTestReport-" + id;
	}

	@Override
	protected TestResult parse() throws IOException, InterruptedException {
		FilePath ws = FilePathUtils.find(node, workspace);
		if (ws != null && ws.isDirectory()) {
			LOGGER.log(Level.FINE, "parsing {0} in {1} on node {2} for {3}",
					new Object[] { glob, workspace, node, run });
			return new JUnitParser(keepLongStdio, true).parseResult(glob, run, ws, null, null);
		} else {
			throw new AbortException("skipping parse in nonexistent workspace for " + run);
		}
	}

	@Override
	protected TestResult findPreviousTestResult() throws IOException, InterruptedException {
		FlowNode node = context.get(FlowNode.class);

		List<FlowNode> enclosingBlocks = JUnitResultsStepExecution.getEnclosingStagesAndParallels(node);
		String stageId = !enclosingBlocks.isEmpty() ? enclosingBlocks.get(0).getId() : null;
		
		return findPreviousTestResult(run, stageId);
	}

	private static TestResult findPreviousTestResult(Run<?, ?> build, final String stageId) {
		TestResult tr = findPreviousTestResult(build);
		if (tr != null) {
			Run<?, ?> prevRun = tr.getRun();
			if (prevRun instanceof FlowExecutionOwner.Executable && stageId != null) {
				FlowExecutionOwner owner = ((FlowExecutionOwner.Executable) prevRun).asFlowExecutionOwner();
				if (owner != null) {
					FlowExecution execution = owner.getOrNull();
					if (execution != null) {
						tr = ((hudson.tasks.junit.TestResult) tr).getResultForPipelineBlock(stageId);
					}
				}
			}
		}
		return tr;
	}

}
