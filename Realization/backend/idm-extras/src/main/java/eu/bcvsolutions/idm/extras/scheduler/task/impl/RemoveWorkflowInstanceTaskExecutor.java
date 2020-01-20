package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import com.google.common.primitives.Ints;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import org.activiti.engine.ProcessEngine;
import org.quartz.DisallowConcurrentExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static eu.bcvsolutions.idm.extras.util.ExtrasUtils.convertToDateViaInstant;
import static org.junit.Assert.assertNotNull;

/**
 * Lrt for deleting historic workflow
 * 
 * @author stloukalp
 *
 */

@Service
@DisallowConcurrentExecution
@Description("Remove historic workflow.")
public class RemoveWorkflowInstanceTaskExecutor extends AbstractSchedulableTaskExecutor<Boolean> {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory
			.getLogger(RemoveWorkflowInstanceTaskExecutor.class);

	@Autowired
	private ProcessEngine processEngine;

	public static final String PARAMETER_PROCESS_DEFINITION_KEY = "workflow_key";
	public static final String PARAMETER_FINISHED_FROM = "number_of_days_start";
	public static final String PARAMETER_FINISHED_TILL = "number_of_days_till";
	private String processDefinitionKey = null;
	private Date from = null;
	private Date till = null;

	@Override
	public void init(Map<String, Object> properties) {
		super.init(properties);
		//
		this.processDefinitionKey = getParameterConverter().toString(properties, PARAMETER_PROCESS_DEFINITION_KEY);
		String numberOfDaysBeforeString = getParameterConverter().toString(properties, PARAMETER_FINISHED_FROM);
		String numberOfDaysTillString = getParameterConverter().toString(properties, PARAMETER_FINISHED_TILL);

		if (numberOfDaysBeforeString != null) {
			numberOfDaysBeforeString = numberOfDaysBeforeString.trim();
		}

		int numberOfDays = Optional.ofNullable(numberOfDaysBeforeString).map(Ints::tryParse).orElse(0);
		this.from = convertToDateViaInstant(LocalDate.now().minusDays(numberOfDays));

		if (numberOfDaysTillString != null) {
			numberOfDaysTillString = numberOfDaysTillString.trim();
		}

		numberOfDays = Optional.ofNullable(numberOfDaysTillString).map(Ints::tryParse).orElse(0);
		this.till = convertToDateViaInstant(LocalDate.now().minusDays(numberOfDays));

	}

	@Override
	public Boolean process() {
		LOG.info(String.format("Starting LRT time: %s", System.currentTimeMillis()));
		this.counter = 0L;
		//
		assertNotNull(processDefinitionKey);

		count = 0L;

		processEngine.getHistoryService().createHistoricProcessInstanceQuery()
				.processDefinitionKey(processDefinitionKey).finishedAfter(from).finishedBefore(till).list()
				.forEach(processInstance -> {
					LOG.info(String.format("Start deleting time: %s, processInstance id: %s", System.currentTimeMillis(), processInstance.getId()));
					processEngine.getHistoryService().deleteHistoricProcessInstance(processInstance.getId());
					counter++;
					count = counter;
					boolean canContinue = updateState();
					if (!canContinue) {
						return;
					}
				});
		return Boolean.TRUE;
	}

	@Override
	public List<String> getPropertyNames() {
		List<String> parameters = super.getPropertyNames();
		parameters.add(PARAMETER_PROCESS_DEFINITION_KEY);
		parameters.add(PARAMETER_FINISHED_FROM);
		parameters.add(PARAMETER_FINISHED_TILL);
		return parameters;
	}
}
