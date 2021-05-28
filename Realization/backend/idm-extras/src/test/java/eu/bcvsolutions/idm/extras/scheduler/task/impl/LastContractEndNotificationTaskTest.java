package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.core.api.domain.IdentityState;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
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
	private IdmIdentityService identityService;
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
		LocalDate now = LocalDate.now();
		
		notification.setDatesForTest(now, 7L);
		
		IdmIdentityDto userOne = getHelper().createIdentity();
		IdmIdentityContractDto userOneContract = getHelper().getPrimeContract(userOne); 
		
		Boolean lastOne = notification.isLastContract(userOneContract);
		Assert.assertTrue(lastOne);
		
		IdmIdentityContractDto userOneContractTwo = getHelper().createContract(userOne);
		Boolean lastTwo = notification.isLastContract(userOneContract);
		Assert.assertFalse(lastTwo);
		
		userOneContract.setValidTill(now);
		identityContractService.saveInternal(userOneContract);
		
		LocalDate later = LocalDate.now();
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
			templateFuture = notificationTemplateService.find(tf, null).getContent().get(0);

			NotificationConfigurationDto notificationConfigurationFuture = new NotificationConfigurationDto();
			notificationConfigurationFuture.setTopic("extras:contractEndInXDays");
			notificationConfigurationFuture.setNotificationType("email");
			notificationConfigurationFuture.setLevel(NotificationLevel.SUCCESS);
			notificationConfigurationFuture.setTemplate(templateFuture.getId());

			notificationConfigurationService.save(notificationConfigurationFuture);

			IdmNotificationTemplateFilter tfTwo = new IdmNotificationTemplateFilter();
			tfTwo.setText("ContractEndNow");
			templateNow = notificationTemplateService.find(tfTwo, null).getContent().get(0);

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
		Assert.assertEquals(6, attributes.size());
	}
	
	@After
	public void after() {
		getHelper().logout();
	}
	
	@Test
	public void testLrtEndsInFuture() {
		IdmIdentityDto subject = getHelper().createIdentity();
		IdmIdentityContractDto subjectContract = getHelper().getPrimeContract(subject);
		// This may collide with CheckExpiredOrMissingManagerTaskTest so make sure identities from that test do not interfere with followin results
		subjectContract.setValidTill(LocalDate.now().plusDays(2));
		identityContractService.saveInternal(subjectContract);
		
		IdmIdentityDto manager = getHelper().createIdentity("managerContractEnds");
		getHelper().createContractGuarantee(subjectContract, manager);
		
		IdmIdentityDto recipient = getHelper().createIdentity("testRecipientContractEnds");
		IdmIdentityContractDto recipientContract = getHelper().getPrimeContract(recipient);
		
		IdmRoleDto notificationRole = getHelper().createRole(UUID.randomUUID(), "TestNotifications");
		getHelper().createIdentityRole(recipientContract, notificationRole);
		
		Map<String, Object> properties = new HashMap<>();
		UUID recipientRole = notificationRole.getId();
		properties.put(LastContractEndNotificationTask.PARAMETER_DAYS_BEFORE, "2");
		properties.put(LastContractEndNotificationTask.SEND_TO_MANAGER_BEFORE_PARAM, true);
		properties.put(LastContractEndNotificationTask.RECIPIENT_ROLE_BEFORE_PARAM, recipientRole);
		
		LastContractEndNotificationTask notificationThree = new LastContractEndNotificationTask();
		notificationThree.init(properties);

		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient("testRecipientContractEnds");
		filter.setNotificationType(IdmEmailLog.class);
		filter.setTopic("extras:contractEndInXDays");

		long countBefore = notificationLogService.count(filter);

		lrtManager.executeSync(notificationThree);

		// we should find 1 email notification on testRecipient
		long count = notificationLogService.count(filter);
		IdmNotificationTemplateDto usedTemplateOne = notificationLogService.find(filter, null).getContent().
				get(0).getMessage().getTemplate();
		
		Assert.assertEquals(1, count - countBefore);
		Assert.assertEquals(templateFuture, usedTemplateOne);

		IdmNotificationFilter filterTwo = new IdmNotificationFilter();
		filterTwo.setRecipient("managerContractEnds");
		filterTwo.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on manager
		long count2 = notificationLogService.count(filterTwo);
		IdmNotificationTemplateDto usedTemplateTwo = notificationLogService.find(filterTwo, null).getContent().
				get(0).getMessage().getTemplate();

		Assert.assertEquals(1, count2);
		Assert.assertEquals(templateFuture, usedTemplateTwo);
		
		// extend the contract's validity and run again
		subjectContract.setValidTill(LocalDate.now().plusDays(365));
		identityContractService.saveInternal(subjectContract);
		LastContractEndNotificationTask notificationFour = new LastContractEndNotificationTask();
		notificationFour.init(properties);
		lrtManager.executeSync(notificationFour);
		
		// we should still see the same number of notifications
		count = notificationLogService.count(filter);
		Assert.assertEquals(1, count - countBefore);
		count2 = notificationLogService.count(filterTwo);
		Assert.assertEquals(1, count2);
		
		// make the contract's validity short and run again
		subjectContract.setValidTill(LocalDate.now().plusDays(2));
		identityContractService.saveInternal(subjectContract);
		LastContractEndNotificationTask notificationFive = new LastContractEndNotificationTask();
		notificationFive.init(properties);
		lrtManager.executeSync(notificationFive);
		
		// we should see that the notification was sent again
		count = notificationLogService.count(filter);
		Assert.assertEquals(2, count - countBefore);
		count2 = notificationLogService.count(filterTwo);
		Assert.assertEquals(2, count2);
		
		getHelper().deleteIdentity(subject.getId());
	}
	
	@Test
	public void testLrtEndsToday() {
		IdmIdentityDto subjectTwo = getHelper().createIdentity();
		IdmIdentityContractDto subjectTwoContract = getHelper().getPrimeContract(subjectTwo);
		subjectTwoContract.setValidTill(LocalDate.now());
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
		
		IdmNotificationTemplateDto usedTemplateThree = notificationLogService.find(filterThree, null).getContent().
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
	
	@Test
	public void doNotSendForManuallyDisabledContractValidityTest() {
		IdmIdentityDto subject = getHelper().createIdentity();
		IdmIdentityContractDto subjectContract = getHelper().getPrimeContract(subject);
		subjectContract.setValidTill(LocalDate.now());
		identityContractService.saveInternal(subjectContract);
		
		IdmIdentityDto subjectDisabled = getHelper().createIdentity();
		IdmIdentityContractDto subjectDisabledContract = getHelper().getPrimeContract(subjectDisabled);
		subjectDisabledContract.setValidTill(LocalDate.now());
		identityContractService.saveInternal(subjectDisabledContract);
		subjectDisabled.setState(IdentityState.DISABLED_MANUALLY);
		identityService.save(subjectDisabled);
		
		IdmIdentityDto recipientThree = getHelper().createIdentity();
		IdmIdentityContractDto recipientThreeContract = getHelper().getPrimeContract(recipientThree);
		
		IdmRoleDto notificationRoleThree = getHelper().createRole();
		getHelper().createIdentityRole(recipientThreeContract, notificationRoleThree);
		
		Map<String, Object> propertiesThree = new HashMap<>();
		propertiesThree.put(LastContractEndNotificationTask.PARAMETER_DAYS_BEFORE, "0");
		propertiesThree.put(LastContractEndNotificationTask.SEND_TO_MANAGER_BEFORE_PARAM, false);
		propertiesThree.put(LastContractEndNotificationTask.PARAMETER_SEND_IF_MANUALLY_DISABLED, false);
		propertiesThree.put(LastContractEndNotificationTask.RECIPIENT_ROLE_BEFORE_PARAM, notificationRoleThree.getId());

		LastContractEndNotificationTask notificationThree = new LastContractEndNotificationTask();
		
		notificationThree.init(propertiesThree); 
		
		lrtManager.executeSync(notificationThree);
		
		IdmNotificationFilter filterThree = new IdmNotificationFilter();
		filterThree.setRecipient(recipientThree.getUsername());
		filterThree.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient because the manually disabled identity should not be included 
		long countThree = notificationLogService.count(filterThree);
		
		IdmNotificationTemplateDto usedTemplateThree = notificationLogService.find(filterThree, null).getContent().
				get(0).getMessage().getTemplate();
		
		Assert.assertEquals(1, countThree);
		Assert.assertEquals(templateNow, usedTemplateThree);
		
		getHelper().deleteIdentity(subject.getId());
	}
}
