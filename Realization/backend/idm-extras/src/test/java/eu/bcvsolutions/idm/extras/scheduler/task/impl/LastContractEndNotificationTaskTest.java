package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
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
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * @author Tomáš Doischer
 */

public class LastContractEndNotificationTaskTest extends AbstractIntegrationTest {
	
	private static IdmNotificationTemplateDto templateFuture;
	private static IdmNotificationTemplateDto templateNow;
	public static boolean initRan = false;
	
	@Autowired
	private LongRunningTaskManager lrtManager;
	@Autowired
	private IdmNotificationLogService notificationLogService;
	@Autowired
	private IdmNotificationConfigurationService notificationConfigurationService;
	@Autowired
	private IdmNotificationTemplateService notificationTemplateService;
	@Autowired
	private IdmIdentityContractService identityContractService;
	@Autowired
	private LastContractEndNotificationTask notification;

	@Test
	public void getManagerForContractTest() {
		IdmIdentityDto managed = getHelper().createIdentity();
		IdmIdentityContractDto managedContract = getHelper().getPrimeContract(managed);
		IdmIdentityDto manager = getHelper().createIdentity();
		getHelper().createContractGuarantee(managedContract, manager);
		
		IdmIdentityDto managerFound = notification.getManagerForContract(managedContract.getId(), managed.getId());
		Assert.assertEquals(manager, managerFound);
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
		
		List<IdmIdentityDto> foundIdentities = notification.getUsersByRoleId(roleOne.getId());
		Assert.assertEquals(2, foundIdentities.size());
		Assert.assertTrue(foundIdentities.contains(userOne));
		Assert.assertTrue(foundIdentities.contains(userTwo));
	}
	
	@Test
	public void isLastContractTest() {
		LocalDate now = new LocalDate();
		
		notification.setDatesForTest(now, 7L);
		
		IdmIdentityDto userOne = getHelper().createIdentity();
		IdmIdentityContractDto userOneContract = getHelper().getPrimeContract(userOne); 
		
		Boolean lastOne = notification.isLastContract(userOneContract);
		Assert.assertTrue(lastOne);
		
		IdmIdentityContractDto userOneContractTwo = getHelper().createIdentityContact(userOne);
		Boolean lastTwo = notification.isLastContract(userOneContract);
		Assert.assertFalse(lastTwo);
		
		userOneContract.setValidTill(now);
		identityContractService.saveInternal(userOneContract);
		
		LocalDate later = new LocalDate();
		later = later.plusDays(180);
		
		userOneContractTwo.setValidTill(later);
		identityContractService.saveInternal(userOneContractTwo);
		
		Boolean lastThree = notification.isLastContract(userOneContract);
		Assert.assertFalse(lastThree);
	}
	
	@Before
	public void init() {
		getHelper().loginAdmin();
		if (!initRan) {
			// create notifications
			IdmNotificationTemplateFilter tf = new IdmNotificationTemplateFilter();
			tf.setText("ContractEndInFuture");
			templateFuture = notificationTemplateService.find(tf, null, null).getContent().get(0);

			NotificationConfigurationDto notificationConfigurationFuture = new NotificationConfigurationDto();
			notificationConfigurationFuture.setTopic("extras:contractEndInXDays");
			notificationConfigurationFuture.setNotificationType("email");
			notificationConfigurationFuture.setLevel(NotificationLevel.SUCCESS);
			notificationConfigurationFuture.setTemplate(templateFuture.getId());

			notificationConfigurationService.save(notificationConfigurationFuture);

			IdmNotificationTemplateFilter tfTwo = new IdmNotificationTemplateFilter();
			tfTwo.setText("ContractEndNow");
			templateNow = notificationTemplateService.find(tfTwo, null, null).getContent().get(0);

			NotificationConfigurationDto notificationConfigurationNow = new NotificationConfigurationDto();
			notificationConfigurationNow.setTopic("extras:contractEnd");
			notificationConfigurationNow.setNotificationType("email");
			notificationConfigurationNow.setLevel(NotificationLevel.SUCCESS);
			notificationConfigurationNow.setTemplate(templateNow.getId());

			notificationConfigurationService.save(notificationConfigurationNow);
			
			initRan = true;
		}
	}
	
	@Test
	public void getFormAttributesTest() {
		List<IdmFormAttributeDto> attributes = notification.getFormAttributes();
		Assert.assertEquals(3, attributes.size());
	}
	
	@After
	public void after() {
		getHelper().logout();
	}
	
	@Test
	public void testLrtEndsInFuture() {
		IdmIdentityDto subject = getHelper().createIdentity();
		IdmIdentityContractDto subjectContract = getHelper().getPrimeContract(subject);
		subjectContract.setValidTill(new LocalDate().plusDays(7));
		identityContractService.saveInternal(subjectContract);
		
		IdmIdentityDto manager = getHelper().createIdentity("manager");
		getHelper().createContractGuarantee(subjectContract, manager);
		
		IdmIdentityDto recipient = getHelper().createIdentity("testRecipient");
		IdmIdentityContractDto recipientContract = getHelper().getPrimeContract(recipient);
		
		IdmRoleDto notificationRole = getHelper().createRole(UUID.randomUUID(), "TestNotifications");
		getHelper().createIdentityRole(recipientContract, notificationRole);
		
		Map<String, Object> properties = new HashMap<>();
		UUID recipientRole = notificationRole.getId();
		properties.put(LastContractEndNotificationTask.PARAMETER_DAYS_BEFORE, "7");
		properties.put(LastContractEndNotificationTask.SEND_TO_MANAGER_BEFORE_PARAM, true);
		properties.put(LastContractEndNotificationTask.RECIPIENT_ROLE_BEFORE_PARAM, recipientRole);
		
		LastContractEndNotificationTask notificationThree = new LastContractEndNotificationTask();
		notificationThree.init(properties);

		lrtManager.executeSync(notificationThree);
		
		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient("testRecipient");
		filter.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient
		long count = notificationLogService.count(filter);
		IdmNotificationTemplateDto usedTemplateOne = notificationLogService.find(filter, null, null).getContent().
				get(0).getMessage().getTemplate();
		
		Assert.assertEquals(1, count);
		Assert.assertEquals(templateFuture, usedTemplateOne);

		IdmNotificationFilter filterTwo = new IdmNotificationFilter();
		filterTwo.setRecipient("manager");
		filterTwo.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on manager
		long count2 = notificationLogService.count(filterTwo);
		IdmNotificationTemplateDto usedTemplateTwo = notificationLogService.find(filterTwo, null, null).getContent().
				get(0).getMessage().getTemplate();

		Assert.assertEquals(1, count2);
		Assert.assertEquals(templateFuture, usedTemplateTwo);
		
		getHelper().deleteIdentity(subject.getId());
	}
	
	@Test
	public void testLrtEndsToday() {
		IdmIdentityDto subjectTwo = getHelper().createIdentity();
		IdmIdentityContractDto subjectTwoContract = getHelper().getPrimeContract(subjectTwo);
		subjectTwoContract.setValidTill(new LocalDate());
		identityContractService.saveInternal(subjectTwoContract);
		
		IdmIdentityDto managerTwo = getHelper().createIdentity("alternateManager");
		getHelper().createContractGuarantee(subjectTwoContract, managerTwo);
		
		IdmIdentityDto recipientTwo = getHelper().createIdentity("alternateRecipient");
		IdmIdentityContractDto recipientTwoContract = getHelper().getPrimeContract(recipientTwo);
		
		IdmRoleDto notificationRoleTwo = getHelper().createRole(UUID.randomUUID(), "TestNotifications2");
		getHelper().createIdentityRole(recipientTwoContract, notificationRoleTwo);
		
		Map<String, Object> propertiesTwo = new HashMap<>();
		UUID recipientRoleTwo = notificationRoleTwo.getId();
		propertiesTwo.put(LastContractEndNotificationTask.PARAMETER_DAYS_BEFORE, "0");
		propertiesTwo.put(LastContractEndNotificationTask.SEND_TO_MANAGER_BEFORE_PARAM, false);
		propertiesTwo.put(LastContractEndNotificationTask.RECIPIENT_ROLE_BEFORE_PARAM, recipientRoleTwo);

		LastContractEndNotificationTask notificationTwo = new LastContractEndNotificationTask();
		
		notificationTwo.init(propertiesTwo); 
		
		lrtManager.executeSync(notificationTwo);
		
		IdmNotificationFilter filterThree = new IdmNotificationFilter();
		filterThree.setRecipient("alternateRecipient");
		filterThree.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient
		long countThree = notificationLogService.count(filterThree);
		
		IdmNotificationTemplateDto usedTemplateThree = notificationLogService.find(filterThree, null, null).getContent().
				get(0).getMessage().getTemplate();
		
		Assert.assertEquals(1, countThree);
		Assert.assertEquals(templateNow, usedTemplateThree);

		IdmNotificationFilter filterFour = new IdmNotificationFilter();
		filterFour.setRecipient("alternateManager");
		filterFour.setNotificationType(IdmEmailLog.class);

		// we should find no email notification on manager
		long countFour = notificationLogService.count(filterFour);

		Assert.assertEquals(0, countFour);
		
		getHelper().deleteIdentity(subjectTwo.getId());
	}
}