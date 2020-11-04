package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormProjectionDto;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormProjectionService;
import eu.bcvsolutions.idm.core.notification.api.domain.NotificationLevel;
import eu.bcvsolutions.idm.core.notification.api.dto.IdmNotificationTemplateDto;
import eu.bcvsolutions.idm.core.notification.api.dto.NotificationConfigurationDto;
import eu.bcvsolutions.idm.core.notification.api.dto.filter.IdmNotificationFilter;
import eu.bcvsolutions.idm.core.notification.api.dto.filter.IdmNotificationTemplateFilter;
import eu.bcvsolutions.idm.core.notification.api.service.IdmNotificationConfigurationService;
import eu.bcvsolutions.idm.core.notification.api.service.IdmNotificationLogService;
import eu.bcvsolutions.idm.core.notification.api.service.IdmNotificationTemplateService;
import eu.bcvsolutions.idm.core.notification.entity.IdmEmailLog;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Roman Kubica
 */

public class CheckExpiredOrMissingManagerTaskTest extends AbstractIntegrationTest {
	
	private static IdmNotificationTemplateDto managersTemplate;
	private static IdmFormProjectionDto projection;
	private static IdmTreeNodeDto treeNode;
	
	private IdmIdentityDto identita1;
	private IdmIdentityDto identita2;
	private IdmIdentityDto identita3;
	private IdmIdentityDto identita4;
	private IdmIdentityDto identita5;
	private IdmIdentityDto identita6;
	
	private IdmIdentityContractDto identityContract1;
	private IdmIdentityContractDto identityContract2;
	private IdmIdentityContractDto identityContract3;
	private IdmIdentityContractDto identityContract4;
	private IdmIdentityContractDto identityContract5;
	private IdmIdentityContractDto identityContract6;
	
	private IdmIdentityDto manager1;
	private IdmIdentityDto manager2;
	private IdmIdentityDto manager3;
	private IdmIdentityDto manager4;
	private IdmIdentityDto manager5;
	private IdmIdentityDto manager6;
	
	private IdmIdentityContractDto managerContract1;
	private IdmIdentityContractDto managerContract2;
	private IdmIdentityContractDto managerContract3;
	private IdmIdentityContractDto managerContract4;
	private IdmIdentityContractDto managerContract5;
	private IdmIdentityContractDto managerContract6;
	
	
	
	public static boolean initRan = false;
	
	@Autowired private LongRunningTaskManager lrtManager;
	@Autowired private IdmNotificationLogService notificationLogService;
	@Autowired private IdmNotificationConfigurationService notificationConfigurationService;
	@Autowired private IdmNotificationTemplateService notificationTemplateService;
	@Autowired private IdmIdentityContractService identityContractService;
	@Autowired private CheckExpiredOrMissingManagerTask managersLRT;
	@Autowired private IdmFormProjectionService projectionService;
	@Autowired private LookupService lookupService;
	@Autowired private IdmIdentityService identityService;
	@Autowired private IdmTreeNodeService treeService;


	@Before
	public void init() {
		getHelper().loginAdmin();
		if (!initRan) {
			projection = new IdmFormProjectionDto();
			projection.setCode(getHelper().createName());
			projection.setOwnerType(lookupService.getOwnerType(IdmIdentityDto.class));
			projection = projectionService.save(projection);
	
			treeNode = getHelper().createTreeNode();
			
			// create notifications
			IdmNotificationTemplateFilter tf1 = new IdmNotificationTemplateFilter();
			tf1.setText("checkExpiredOrMissingManager");
			managersTemplate = notificationTemplateService.find(tf1, null).getContent().get(0);

			NotificationConfigurationDto notificationConfigurationManagers = new NotificationConfigurationDto();
			notificationConfigurationManagers.setTopic("vfn:vfnExternalContractExpiredXDays");
			notificationConfigurationManagers.setNotificationType("email");
			notificationConfigurationManagers.setLevel(NotificationLevel.SUCCESS);
			notificationConfigurationManagers.setTemplate(managersTemplate.getId());

			notificationConfigurationService.save(notificationConfigurationManagers);
			
			identita1 = setupIdentity("identita1",true); 
			identityContract1 = setupContract(identita1, 50);
			identita2 = setupIdentity("identita2",true); 
			identityContract2 = setupContract(identita2, 50);
			identita3 = setupIdentity("identita3",true); 
			identityContract3 = setupContract(identita3, 50);
			identita4 = setupIdentity("identita4",false); 
			identityContract4 = setupContract(identita4, 50);
			identita5 = setupIdentity("identita5",false); 
			identityContract5 = setupContract(identita5, 50);
			identita6 = setupIdentity("identita6",false); 
			identityContract6 = setupContract(identita6, 50);
			
			manager1 = setupIdentity("manager1",true); 
			managerContract1 = setupContract(manager1, 50);
			manager2 = setupIdentity("manager2",true); 
			managerContract2 = setupContract(manager2, -5);
			manager3 = setupIdentity("manager3",true); 
			managerContract3 = setupContract(manager3, 3);
			manager4 = setupIdentity("manager4",false); 
			managerContract4 = setupContract(manager4, -10);
			manager5 = setupIdentity("manager5",false); 
			managerContract5 = setupContract(manager5, 20);
			manager6 = setupIdentity("manager6",false); 
			managerContract6 = setupContract(manager6, 50);
			
			getHelper().createContractGuarantee(identityContract1, manager1);
			getHelper().createContractGuarantee(identityContract2, manager2);
			getHelper().createContractGuarantee(identityContract3, manager3);
			getHelper().createContractGuarantee(identityContract4, manager4);
			getHelper().createContractGuarantee(identityContract5, manager5);
			
			managerContract1.setWorkPosition(treeService.save(treeNode).getId());
			identityContractService.save(managerContract1);
						
			initRan = true;
		}
	}
	
	private IdmIdentityDto setupIdentity(String name, Boolean useProjection) {
		IdmIdentityDto identita = getHelper().createIdentity(name+"_username");
		identita.setFirstName("Jmeno-"+name);
		identita.setLastName("Prijmeni-"+name);
		identita.setEmail(name + "@bcvsolutions.eu");
		if(useProjection) {
			identita.setFormProjection(projection.getId());	
		}
		return identityService.save(identita);
	}
	
	private IdmIdentityContractDto setupContract(IdmIdentityDto identita, Integer validDays) {
		IdmIdentityContractDto contract = getHelper().getPrimeContract(identita);
		if(validDays>0) {
			contract.setValidTill(LocalDate.now().plusDays(validDays));	
		}else {
			contract.setValidTill(LocalDate.now().minusDays(validDays*(-1)));
		}
		
		return identityContractService.save(contract);
		
	}
	
	@After
	public void after() {
		getHelper().logout();
	}
	
	@Test
	public void getFormAttributesTest() {
		List<IdmFormAttributeDto> attributes = managersLRT.getFormAttributes();
		Assert.assertEquals(9, attributes.size());
	}
		
	//1. identity that has assigned manager with expired contract (manager is already expired)
	//2. identity that has no manager assigned
	// 3. identity that has assigned manager and manager's contract is expiring in X days.
	@Test
	public void testLrtManagersExpiredWithProjection() {
		
		IdmIdentityDto recipient = getHelper().createIdentity("testRecipient5");
		IdmIdentityContractDto recipientContract = getHelper().getPrimeContract(recipient);
		
		IdmRoleDto notificationRole = getHelper().createRole(UUID.randomUUID(), "TestNotifications5");
		getHelper().createIdentityRole(recipientContract, notificationRole);
		
		Map<String, Object> properties = new HashMap<>();
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_DAYS_BEFORE, "5");
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_DAYS_BEFORE_LESS_THAN, true);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_USER_PROJECTION, projection.getId());
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_RECIPIENT_ROLE_PARAM, notificationRole.getId());
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_RECIPIENT_EMAIL_PARAM, "test6@bcvsolutions.eu");
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_ALREADY_EXPIRED, true);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_MISSING, false);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_EXPIRING_X_DAYS, false);

		CheckExpiredOrMissingManagerTask notificationExpiredManager = new CheckExpiredOrMissingManagerTask();
		notificationExpiredManager.init(properties);

		lrtManager.executeSync(notificationExpiredManager);
		
		HashMap<String,String> expiredManagers = notificationExpiredManager.getManagersAlreadyExpired();
		Assert.assertEquals(1, expiredManagers.size());
		
		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient("testRecipient5");
		filter.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient
		long count = notificationLogService.count(filter);
		
		IdmNotificationTemplateDto usedTemplateOne = notificationLogService.find(filter, null).getContent().
				get(0).getMessage().getTemplate();
		
		Assert.assertEquals(1, count);
		Assert.assertEquals(managersTemplate, usedTemplateOne);
	}
	
	//2. identity that has no manager assigned
	@Test
	public void testLrtManagersMissingAllUsers() {
		
		IdmIdentityDto recipient = getHelper().createIdentity("testRecipient4");
		IdmIdentityContractDto recipientContract = getHelper().getPrimeContract(recipient);
		
		IdmRoleDto notificationRole = getHelper().createRole(UUID.randomUUID(), "TestNotifications4");
		getHelper().createIdentityRole(recipientContract, notificationRole);
		
		Map<String, Object> properties = new HashMap<>();
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_DAYS_BEFORE, "30");
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_DAYS_BEFORE_LESS_THAN, true);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_USER_PROJECTION, projection.getId());
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_RECIPIENT_ROLE_PARAM, notificationRole.getId());
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_RECIPIENT_EMAIL_PARAM, "test5@bcvsolutions.eu,helpdesk@bcvsolutions.eu");
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_ALREADY_EXPIRED, false);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_MISSING, true);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_EXPIRING_X_DAYS, false);

		CheckExpiredOrMissingManagerTask notificationMissingManager = new CheckExpiredOrMissingManagerTask();
		notificationMissingManager.init(properties);

		lrtManager.executeSync(notificationMissingManager);
		
		List<String> missingManagers = notificationMissingManager.getManagersMissing();
		Assert.assertEquals(2, missingManagers.size());
		
		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient("testRecipient4");
		filter.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient
		long count = notificationLogService.count(filter);
		
		IdmNotificationTemplateDto usedTemplateOne = notificationLogService.find(filter, null).getContent().
				get(0).getMessage().getTemplate();
		
		Assert.assertEquals(1, count);
		Assert.assertEquals(managersTemplate, usedTemplateOne);
		
	}
	
	//3. identity that has assigned manager and manager's contract is expiring in X days.
	@Test
	public void testLrtManagersExpiringXDays() {
		
		IdmIdentityDto recipient = getHelper().createIdentity("testRecipient3");
		IdmIdentityContractDto recipientContract = getHelper().getPrimeContract(recipient);
		
		IdmRoleDto notificationRole = getHelper().createRole(UUID.randomUUID(), "TestNotifications3");
		getHelper().createIdentityRole(recipientContract, notificationRole);
		
		Map<String, Object> properties = new HashMap<>();
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_DAYS_BEFORE, "30");
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_DAYS_BEFORE_LESS_THAN, true);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_USER_PROJECTION, null);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_RECIPIENT_ROLE_PARAM, notificationRole.getId());
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_RECIPIENT_EMAIL_PARAM, "test4@bcvsolutions.eu");
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_ALREADY_EXPIRED, false);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_MISSING, false);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_EXPIRING_X_DAYS, true);

		CheckExpiredOrMissingManagerTask notificationExpiringXDaysManager = new CheckExpiredOrMissingManagerTask();
		notificationExpiringXDaysManager.init(properties);

		lrtManager.executeSync(notificationExpiringXDaysManager);
		
		HashMap<String,String> expiringManagers = notificationExpiringXDaysManager.getManagersExpiritingXDays();
		Assert.assertEquals(2, expiringManagers.size());
		
		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient("testRecipient3");
		filter.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient
		long count = notificationLogService.count(filter);
		
		IdmNotificationTemplateDto usedTemplateOne = notificationLogService.find(filter, null).getContent().
				get(0).getMessage().getTemplate();
		
		Assert.assertEquals(1, count);
		Assert.assertEquals(managersTemplate, usedTemplateOne);
		
	}
	
	//3. identity that has assigned manager and manager's contract is expiring in X days. Not less than.
		@Test
		public void testLrtManagersExpiringXDaysExactly() {
			
			IdmIdentityDto recipient = getHelper().createIdentity("testRecipient2");
			IdmIdentityContractDto recipientContract = getHelper().getPrimeContract(recipient);
			
			IdmRoleDto notificationRole = getHelper().createRole(UUID.randomUUID(), "TestNotifications2");
			getHelper().createIdentityRole(recipientContract, notificationRole);
			
			Map<String, Object> properties = new HashMap<>();
			properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_DAYS_BEFORE, "20");
			properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_DAYS_BEFORE_LESS_THAN, true);
			properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_USER_PROJECTION, null);
			properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_RECIPIENT_ROLE_PARAM, notificationRole.getId());
			properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_RECIPIENT_EMAIL_PARAM, "test3@bcvsolutions.eu,helpdesk@bcvsolutions.eu");
			properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_ALREADY_EXPIRED, false);
			properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_MISSING, false);
			properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_EXPIRING_X_DAYS, true);

			CheckExpiredOrMissingManagerTask notificationExpiringXDaysManager = new CheckExpiredOrMissingManagerTask();
			notificationExpiringXDaysManager.init(properties);

			lrtManager.executeSync(notificationExpiringXDaysManager);
			
			HashMap<String,String> expiringManagers = notificationExpiringXDaysManager.getManagersExpiritingXDays();
			Assert.assertEquals(2, expiringManagers.size());
			
			IdmNotificationFilter filter = new IdmNotificationFilter();
			filter.setRecipient("testRecipient2");
			filter.setNotificationType(IdmEmailLog.class);

			// we should find 1 email notification on testRecipient
			long count = notificationLogService.count(filter);
			
			IdmNotificationTemplateDto usedTemplateOne = notificationLogService.find(filter, null).getContent().
					get(0).getMessage().getTemplate();
			
			Assert.assertEquals(1, count);
			Assert.assertEquals(managersTemplate, usedTemplateOne);
			
		}
	
	@Test
	public void testLrtManagersNoRecipient() {
		
		Map<String, Object> properties = new HashMap<>();
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_DAYS_BEFORE, "30");
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_DAYS_BEFORE_LESS_THAN, true);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_USER_PROJECTION, null);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_RECIPIENT_ROLE_PARAM, null);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_RECIPIENT_EMAIL_PARAM, "");
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_ALREADY_EXPIRED, false);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_MISSING, false);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_EXPIRING_X_DAYS, true);

		CheckExpiredOrMissingManagerTask notificationExpiringXDaysManager = new CheckExpiredOrMissingManagerTask();
		try {
			notificationExpiringXDaysManager.init(properties);	
		}catch(ResultCodeException exception){
			Assert.assertEquals(exception.getMessage(),ExtrasResultCode.NO_RECIPIENTS_FOUND.getMessage());
		}
	}
	
	@Test
	public void testLrtManagersXDaysAttributMissing() {
		
		Map<String, Object> properties = new HashMap<>();
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_DAYS_BEFORE, "");
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_DAYS_BEFORE_LESS_THAN, true);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_USER_PROJECTION, null);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_RECIPIENT_ROLE_PARAM, null);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_RECIPIENT_EMAIL_PARAM, "");
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_ALREADY_EXPIRED, false);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_MISSING, false);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_EXPIRING_X_DAYS, true);

		CheckExpiredOrMissingManagerTask notificationExpiringXDaysManager = new CheckExpiredOrMissingManagerTask();
		try {
			notificationExpiringXDaysManager.init(properties);	
		}catch(ResultCodeException exception){
			Assert.assertEquals(exception.getMessage(),ExtrasResultCode.CONTRACT_END_NOTIFICATION_DAYS_BEFORE_NOT_SPECIFIED.getMessage());
		}
	}
	
	
	@Test
	public void getUsersByRoleIdTest() {
		IdmIdentityDto userOne = getHelper().createIdentity();
		IdmIdentityContractDto userOneContract = getHelper().getPrimeContract(userOne); 
		IdmIdentityDto userTwo = getHelper().createIdentity();
		IdmIdentityContractDto userTwoContract = getHelper().getPrimeContract(userTwo);
		IdmRoleDto roleOne = getHelper().createRole("role");
		getHelper().createIdentityRole(userOneContract, roleOne);
		getHelper().createIdentityRole(userTwoContract, roleOne);
		
		List<IdmIdentityDto> foundIdentities = managersLRT.getUsersByRoleId(roleOne.getId());
		Assert.assertEquals(2, foundIdentities.size());
		Assert.assertTrue(foundIdentities.contains(userOne));
		Assert.assertTrue(foundIdentities.contains(userTwo));
	}
	
	@Test
	public void testLrtManagersOrganizationUnit() {
		
		IdmIdentityDto recipient = getHelper().createIdentity("testRecipient1");
		IdmIdentityContractDto recipientContract = getHelper().getPrimeContract(recipient);
		
		IdmRoleDto notificationRole = getHelper().createRole(UUID.randomUUID(), "TestNotifications1");
		getHelper().createIdentityRole(recipientContract, notificationRole);
		
		Map<String, Object> properties = new HashMap<>();
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_ORGANIZATION_UNIT, treeNode.getId());
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_DAYS_BEFORE, "30");
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_DAYS_BEFORE_LESS_THAN, true);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_USER_PROJECTION, null);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_RECIPIENT_ROLE_PARAM, notificationRole.getId());
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_RECIPIENT_EMAIL_PARAM, "test2@bcvsolutions.eu,helpdesk@bcvsolutions.eu");
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_ALREADY_EXPIRED, false);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_MISSING, true);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_EXPIRING_X_DAYS, false);

		CheckExpiredOrMissingManagerTask notificationMissingManager = new CheckExpiredOrMissingManagerTask();
		notificationMissingManager.init(properties);

		lrtManager.executeSync(notificationMissingManager);
		
		List<String> missingManagers = notificationMissingManager.getManagersMissing();
		Assert.assertEquals(1, missingManagers.size());
		
		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient("testRecipient1");
		filter.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient
		long count = notificationLogService.count(filter);
		
		IdmNotificationTemplateDto usedTemplateOne = notificationLogService.find(filter, null).getContent().
				get(0).getMessage().getTemplate();
		
		Assert.assertEquals(1, count);
		Assert.assertEquals(managersTemplate, usedTemplateOne);
	}
	
	@Test
	public void testLrtManagersRoleSent() {
		
		IdmIdentityDto recipient = getHelper().createIdentity("testRecipient");
		IdmIdentityContractDto recipientContract = getHelper().getPrimeContract(recipient);
		
		IdmRoleDto notificationRole = getHelper().createRole(UUID.randomUUID(), "TestNotifications");
		getHelper().createIdentityRole(recipientContract, notificationRole);
				
		Map<String, Object> properties = new HashMap<>();
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_DAYS_BEFORE, "30");
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_DAYS_BEFORE_LESS_THAN, true);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_USER_PROJECTION, null);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_RECIPIENT_ROLE_PARAM, notificationRole.getId());
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_RECIPIENT_EMAIL_PARAM, "");
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_ALREADY_EXPIRED, true);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_MISSING, true);
		properties.put(CheckExpiredOrMissingManagerTask.PARAMETER_EMAIL_INFO_MANAGER_EXPIRING_X_DAYS, true);

		CheckExpiredOrMissingManagerTask notificationExpiringXDaysManager = new CheckExpiredOrMissingManagerTask();
		notificationExpiringXDaysManager.init(properties);

		lrtManager.executeSync(notificationExpiringXDaysManager);
		
		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient("testRecipient");
		filter.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient
		long count = notificationLogService.count(filter);
		
		IdmNotificationTemplateDto usedTemplateOne = notificationLogService.find(filter, null).getContent().
				get(0).getMessage().getTemplate();
		
		Assert.assertEquals(1, count);
		Assert.assertEquals(managersTemplate, usedTemplateOne);
	}
	
}
