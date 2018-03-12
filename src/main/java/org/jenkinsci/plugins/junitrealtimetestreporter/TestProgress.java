package org.jenkinsci.plugins.junitrealtimetestreporter;

import hudson.tasks.junit.TestResult;

public class TestProgress {

	private TestResult previousResult;
    private TestResult result;
    
	public TestProgress(TestResult previousResult, TestResult result) {
		this.previousResult = previousResult;
		this.result = result;
	}
	
	public String getEstimatedRemainingTime() {
		float remaining = Math.max(previousResult.getDuration() - result.getDuration(), 0);
		
		int minutes = (int) Math.floor((double) remaining / 60d);
		int seconds = (int) remaining % 60;
		
		if (minutes > 0) {
			return String.format("%d min %d sec", minutes, seconds);
		}
		
		return String.format("%d sec", seconds);
	}
	
	public int getCompletedPercentage() {
		return Math.min((int) Math.floor((double) getCompletedTests() / (double) getExpectedTests() * 100d), 100);
	}
	
	public int getLeftPercentage() {
		return 100 - getCompletedPercentage();
	}
	
	public int getCompletedTests() {
		return result.getTotalCount();
	}
	
	public int getExpectedTests() {
		return previousResult.getTotalCount();
	}
}
