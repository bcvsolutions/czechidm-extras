package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.activiti.engine.ProcessEngine;
import org.activiti.engine.history.HistoricProcessInstance;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestState;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestedByType;
import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmConceptRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.notification.api.dto.NotificationConfigurationDto;
import eu.bcvsolutions.idm.core.notification.api.service.IdmNotificationConfigurationService;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.core.security.api.domain.GuardedString;
import eu.bcvsolutions.idm.core.workflow.model.dto.WorkflowFilterDto;
import eu.bcvsolutions.idm.core.workflow.model.dto.WorkflowTaskInstanceDto;
import eu.bcvsolutions.idm.core.workflow.service.WorkflowTaskInstanceService;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * Test for: Lrt, which removes historic workflow
 *
 * @author stloukalp
 *
 */
public class RemoveWorkflowInstanceTest extends AbstractIntegrationTest {

	@Autowired
	private ProcessEngine processEngine;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private IdmIdentityContractService identityContractService;
	@Autowired
	private IdmRoleRequestService roleRequestService;
	@Autowired
	private IdmConceptRoleRequestService conceptRoleRequestService;
	@Autowired
	private WorkflowTaskInstanceService workflowTaskInstanceService;
	@Autowired
	private LongRunningTaskManager longRunningTaskManager;
	@Autowired
	private ConfigurationService configurationService;
	@Autowired
	private IdmNotificationConfigurationService notificationConfigurationService;
	//
	private static final String APPROVE_BY_HELPDESK_ENABLE = "idm.sec.core.wf.approval.helpdesk.enabled";
	//
	private String processDefinitionKey = "approve-identity-change-permissions";
	private String notification_topic = "core:disapproveIdentityRoleImplementer";
	private NotificationConfigurationDto notificationConfiguration = null;

	@Before
	public void login() {
		super.loginAsAdmin();
		configurationService.setBooleanValue(APPROVE_BY_HELPDESK_ENABLE, true);
		notificationConfiguration = notificationConfigurationService
				.getConfigurationByTopicAndNotificationTypeAndLevelIsNull(notification_topic, "email");
		notificationConfiguration.setDisabled(true);
		notificationConfigurationService.save(notificationConfiguration);
	}

	@After
	public void logout() {
		super.logout();

		configurationService.setBooleanValue(APPROVE_BY_HELPDESK_ENABLE, false);
		notificationConfiguration.setDisabled(false);
		notificationConfigurationService.save(notificationConfiguration);
	}

	@Ignore
	@Test
	public void eraseHistoricWfCreatedByRoleRequestTest() {
		createHistoricWf();
		createHistoricWf();
		createHistoricWf();
		createHistoricWf();

		List<HistoricProcessInstance> listAfterAssignRole = processEngine.getHistoryService()
				.createHistoricProcessInstanceQuery().processDefinitionKey(processDefinitionKey).list();
		assertEquals(4, listAfterAssignRole.size());

		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(RemoveWorkflowInstanceTaskExecutor.PARAMETER_PROCESS_DEFINITION_KEY, processDefinitionKey);

		RemoveWorkflowInstanceTaskExecutor lrt = new RemoveWorkflowInstanceTaskExecutor();
		lrt.init(properties);
		longRunningTaskManager.execute(lrt);

		List<HistoricProcessInstance> listAfterRemove = processEngine.getHistoryService()
				.createHistoricProcessInstanceQuery().processDefinitionKey(processDefinitionKey).list();
		assertEquals(0, listAfterRemove.size());
	}

	@Ignore
	@Test
	public void eraseHistoricWfTimeTest() {
		createHistoricWf();
		createHistoricWf();

		List<HistoricProcessInstance> listAfterAssignRole = processEngine.getHistoryService()
				.createHistoricProcessInstanceQuery().processDefinitionKey(processDefinitionKey).list();
		assertEquals(2, listAfterAssignRole.size());

		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(RemoveWorkflowInstanceTaskExecutor.PARAMETER_PROCESS_DEFINITION_KEY, processDefinitionKey);

		RemoveWorkflowInstanceTaskExecutor lrt = new RemoveWorkflowInstanceTaskExecutor();
		lrt.init(properties);
		longRunningTaskManager.execute(lrt);

		List<HistoricProcessInstance> listAfterRemove = processEngine.getHistoryService()
				.createHistoricProcessInstanceQuery().processDefinitionKey(processDefinitionKey).list();
		assertEquals(0, listAfterRemove.size());
	}

	@Ignore
	@Test
	public void eraseHistoricWfWrongTimeTest() {
		createHistoricWf();
		createHistoricWf();

		List<HistoricProcessInstance> listAfterAssignRole = processEngine.getHistoryService()
				.createHistoricProcessInstanceQuery().processDefinitionKey(processDefinitionKey).list();
		assertEquals(2, listAfterAssignRole.size());

		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(RemoveWorkflowInstanceTaskExecutor.PARAMETER_PROCESS_DEFINITION_KEY, processDefinitionKey);

		RemoveWorkflowInstanceTaskExecutor lrt = new RemoveWorkflowInstanceTaskExecutor();
		lrt.init(properties);
		longRunningTaskManager.execute(lrt);

		List<HistoricProcessInstance> listAfterRemove = processEngine.getHistoryService()
				.createHistoricProcessInstanceQuery().processDefinitionKey(processDefinitionKey).list();
		assertEquals(0, listAfterRemove.size());
	}

	private void createHistoricWf() {
		loginAsAdmin();

		IdmIdentityDto test1 = createTestUser();
		IdmRoleDto test_role = createRole("test_role" + System.currentTimeMillis());
		loginAsAdmin(test1.getUsername());

		IdmIdentityContractDto contract = identityContractService.getPrimeContract(test1.getId());

		IdmRoleRequestDto request = createRoleRequest(test1);
		request = roleRequestService.save(request);

		IdmConceptRoleRequestDto concept = createRoleConcept(test_role, contract, request);
		concept = conceptRoleRequestService.save(concept);

		roleRequestService.startRequestInternal(request.getId(), true);
		request = roleRequestService.get(request.getId());
		assertEquals(RoleRequestState.IN_PROGRESS, request.getState());

		WorkflowFilterDto taskFilter = new WorkflowFilterDto();
		List<WorkflowTaskInstanceDto> tasks = (List<WorkflowTaskInstanceDto>) workflowTaskInstanceService
				.search(taskFilter).getResources();
		assertEquals(0, tasks.size());

		loginAsAdmin();
		// HELPDESK
		checkAndCompleteOneTask(taskFilter, test1.getUsername(), "disapprove");
	}

	/**
	 * Creates role
	 * 
	 * @param code
	 * @return IdmRoleDto
	 */
	private IdmRoleDto createRole(String code) {
		IdmRoleDto role = new IdmRoleDto();
		role.setName(code);
		role.setCode(code);
		role.setCanBeRequested(true);
		//
		return roleService.save(role);
	}

	/**
	 * Creates testUser with working position and contract
	 * 
	 * @return IdmIdentityDto
	 */
	private IdmIdentityDto createTestUser() {
		IdmIdentityDto testUser = new IdmIdentityDto();
		testUser.setUsername("" + System.currentTimeMillis());
		testUser.setPassword(new GuardedString("heslo"));
		testUser.setFirstName("Test");
		testUser.setLastName("User");
		testUser.setEmail(testUser.getUsername() + "@bscsolutions.eu");
		testUser = this.identityService.save(testUser);

		IdmIdentityContractDto identityWorkPosition2 = new IdmIdentityContractDto();
		identityWorkPosition2.setIdentity(testUser.getId());
		identityWorkPosition2 = identityContractService.save(identityWorkPosition2);

		return testUser;
	}

	/**
	 * Creates concept role request for assign role to identity
	 * 
	 * @param IdmRoleDto, IdmIdentityContractDto, IdmRoleRequestDto
	 * @return IdmConceptRoleRequestDto
	 */
	private IdmConceptRoleRequestDto createRoleConcept(IdmRoleDto adminRole, IdmIdentityContractDto contract,
			IdmRoleRequestDto request) {
		IdmConceptRoleRequestDto concept = new IdmConceptRoleRequestDto();
		concept.setRoleRequest(request.getId());
		concept.setOperation(ConceptRoleRequestOperation.ADD);
		concept.setRole(adminRole.getId());
		concept.setIdentityContract(contract.getId());
		return concept;
	}

	/**
	 * Creates request for identity
	 * 
	 * @param IdmIdentityDto
	 * @return IdmRoleRequestDto
	 */
	private IdmRoleRequestDto createRoleRequest(IdmIdentityDto test1) {
		IdmRoleRequestDto request = new IdmRoleRequestDto();
		request.setApplicant(test1.getId());
		request.setExecuteImmediately(false);
		request.setRequestedByType(RoleRequestedByType.MANUALLY);
		return request;
	}

	/**
	 * Completes one task (as helpdesk/manager/user manager/security)
	 * 
	 * @param taskFilter, userName, decision
	 */
	private void checkAndCompleteOneTask(WorkflowFilterDto taskFilter, String userName, String decision) {
		IdmIdentityDto identity = identityService.getByUsername(userName);
		List<WorkflowTaskInstanceDto> tasks;
		tasks = (List<WorkflowTaskInstanceDto>) workflowTaskInstanceService.search(taskFilter).getResources();
		assertEquals(1, tasks.size());
		assertEquals(identity.getId().toString(), tasks.get(0).getApplicant());

		workflowTaskInstanceService.completeTask(tasks.get(0).getId(), decision);
	}

}