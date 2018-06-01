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

	public int getCompletedTestsPercentage() {
		if ( getExpectedTests() == 0) {
			return 100;
		}

		return Math.min((int) Math.floor((double) getCompletedTests() / (double) getExpectedTests() * 100d), 100);
	}

	public int getTestsLeftPercentage() {
		return 100 - getCompletedTestsPercentage();
	}

	public int getCompletedTimePercentage() {
		if ( getExpectedTime() == 0) {
			return 100;
		}

		return Math.min((int) Math.floor((double) getCompletedTime() / (double) getExpectedTime() * 100d), 100);
	}

	public int getTimeLeftPercentage() {
		return 100 - getCompletedTimePercentage();
	}

	public int getCompletedTests() {
		return result.getTotalCount();
	}

	public int getExpectedTests() {
		return previousResult.getTotalCount();
	}

	public float getCompletedTime() {
		return result.getDuration();
	}

	public float getExpectedTime() {
		return previousResult.getDuration();
	}

	public String getStyle() {
		if (result.getFailCount() > 0) {
			return "red";
		}

		return "";
	}
}
