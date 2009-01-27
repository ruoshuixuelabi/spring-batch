/*
 * Copyright 2006-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.batch.test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.SimpleJob;
import org.springframework.batch.core.job.flow.FlowJob;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.job.flow.support.State;
import org.springframework.batch.core.job.flow.support.state.StepState;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;

/**
 * <p>
 * Base class for testing batch jobs. It provides methods for launching an
 * entire {@link Job}, allowing for end to end testing of individual steps,
 * without having to run every step in the job. Any test classes inheriting from
 * this class should make sure they are part of an {@link ApplicationContext},
 * which is generally expected to be done as part of the Spring test framework.
 * Furthermore, the {@link ApplicationContext} in which it is a part of is
 * expected to have one {@link JobLauncher}, {@link JobRepository}, and a
 * single {@link Job} implementation.
 * 
 * <p>
 * This class also provides the ability to run {@link Step}s from a
 * {@link FlowJob} or {@link SimpleJob} individually. By launching {@link Step}s
 * within a {@link Job} on their own, end to end testing of individual steps can
 * be performed without having to run every step in the job.
 * 
 * <p>
 * It should be noted that using any of the methods that don't contain
 * {@link JobParameters} in their signature, will result in one being created
 * with the current system time as a parameter. This will ensure restartability
 * when no parameters are provided.
 * 
 * @author Lucas Ward
 * @author Dan Garrette
 * @since 2.0
 */
public abstract class AbstractJobTests {

	/** Logger */
	protected final Log logger = LogFactory.getLog(getClass());

	@Autowired
	private JobLauncher launcher;

	@Autowired
	private Job job;

	@Autowired
	private JobRepository jobRepository;

	private StepRunner stepRunner;
	private Map<String, Step> stepMap;

	/**
	 * @return the job repository
	 */
	public JobRepository getJobRepository() {
		return jobRepository;
	}

	/**
	 * @return the job
	 */
	public Job getJob() {
		return job;
	}

	/**
	 * @return the launcher
	 */
	protected JobLauncher getJobLauncher() {
		return launcher;
	}

	/**
	 * Launch the entire job, including all steps.
	 * 
	 * @return JobExecution, so that the test may validate the exit status
	 * @throws Exception
	 */
	public JobExecution launchJob() throws Exception {
		return this.launchJob(this.makeUniqueJobParameters());
	}

	/**
	 * Launch the entire job, including all steps
	 * 
	 * @param jobParameters
	 * @return JobExecution, so that the test may validate the exit status
	 * @throws Exception
	 */
	public JobExecution launchJob(JobParameters jobParameters) throws Exception {
		return getJobLauncher().run(this.job, jobParameters);
	}

	/**
	 * @return a new JobParameters object containing only a parameter for the
	 *         current timestamp, to ensure that the job instance will be unique
	 */
	private JobParameters makeUniqueJobParameters() {
		Map<String, JobParameter> parameters = new HashMap<String, JobParameter>();
		parameters.put("timestamp", new JobParameter(new Date().getTime()));
		return new JobParameters(parameters);
	}

	protected StepRunner getStepRunner() {
		if (this.stepRunner == null) {
			this.stepRunner = new StepRunner(getJobLauncher(), getJobRepository());
		}
		return this.stepRunner;
	}

	/**
	 * Launch just the specified step in the job.
	 * 
	 * @param stepName
	 */
	public JobExecution launchStep(String stepName) {
		return getStepRunner().launchStep(getStep(stepName));
	}

	/**
	 * @param stepName
	 * @return
	 */
	public Step getStep(String stepName) {
		Job job = getJob();
		if (job instanceof FlowJob) {
			return getFlowJobStep(stepName);
		}
		else if (job instanceof SimpleJob) {
			return getSimpleJobStep(stepName);
		}
		else {
			throw new IllegalStateException("Job is neither a FlowJob or a SimpleJob");
		}
	}

	/**
	 * Extract the step from a FlowJob. Throw an exception of the step does not
	 * exist.
	 * 
	 * @param stepName
	 * @return the step
	 */
	private Step getFlowJobStep(String stepName) {
		try{
			State state = ((SimpleFlow) ((FlowJob) getJob()).getFlow()).getState(stepName);
			Assert.notNull(state, "no matching state found in flow for " + stepName);
			Assert.isInstanceOf(StepState.class, state);
			return ((StepState) state).getStep();
		}
		catch(IllegalArgumentException e)
		{
			throw new IllegalStateException("No Step found with name: [" + stepName + "]");
		}
	}

	/**
	 * Extract the step from a SimpleJob. Throw an exception of the step does
	 * not exist.
	 * 
	 * @param stepName
	 * @return the step
	 */
	private Step getSimpleJobStep(String stepName) {
		if (this.stepMap == null) {
			//
			// Populate the step map
			//
			SimpleJob simpleJob = (SimpleJob) getJob();
			this.stepMap = new HashMap<String, Step>();
			for (Step step : simpleJob.getSteps()) {
				this.stepMap.put(step.getName(), step);
			}
		}

		if (!this.stepMap.containsKey(stepName)) {
			throw new IllegalStateException("No Step found with name: [" + stepName + "]");
		}
		return this.stepMap.get(stepName);
	}

	/**
	 * Launch just the specified step in the job.
	 * 
	 * @param stepName
	 * @param jobParameters
	 */
	public JobExecution launchStep(String stepName, JobParameters jobParameters) {
		return getStepRunner().launchStep(getStep(stepName), jobParameters);
	}
}
