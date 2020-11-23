package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.quartz.DisallowConcurrentExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.IdmContractGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmContractGuaranteeFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmContractGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.eav.api.domain.BaseFaceType;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import eu.bcvsolutions.idm.core.notification.api.domain.NotificationLevel;
import eu.bcvsolutions.idm.core.notification.api.dto.IdmMessageDto;
import eu.bcvsolutions.idm.core.notification.api.dto.IdmNotificationTemplateDto;
import eu.bcvsolutions.idm.core.notification.api.service.EmailNotificationSender;
import eu.bcvsolutions.idm.core.notification.api.service.IdmNotificationTemplateService;
import eu.bcvsolutions.idm.core.notification.api.service.NotificationManager;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.extras.ExtrasModuleDescriptor;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;


/**
 * Task checks for
 * 1. identity that has assigned manager with expired contract (manager is already expired)
 * 2. identity that has no manager assigned
 * 3. identity that has assigned manager and manager's contract is expiring in X days.
 * @author Roman Kubica
 *
 */

@Service
@DisallowConcurrentExecution
@Description("Task checks for expired or missing managers for identities and sends email to responsible person.")
public class CheckExpiredOrMissingManagerTask extends AbstractSchedulableTaskExecutor<Boolean> {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CheckExpiredOrMissingManagerTask.class);
	protected static final String PARAMETER_DAYS_BEFORE = "xDaysBeforeContractExpires";
	protected static final String PARAMETER_DAYS_BEFORE_LESS_THAN = "xDaysBeforeContractExpiresLessThan";
	protected static final String PARAMETER_USER_PROJECTION = "userProjection";
	protected static final String PARAMETER_ORGANIZATION_UNIT = "organizationUnit";
	protected static final String PARAMETER_EMAIL_INFO_MANAGER_ALREADY_EXPIRED = "managerAlreadyExpired";
	protected static final String PARAMETER_EMAIL_INFO_MANAGER_MISSING = "managerMissing";
	protected static final String PARAMETER_EMAIL_INFO_MANAGER_EXPIRING_X_DAYS = "managerExpiringinXDays";
	protected static final String PARAMETER_RECIPIENT_ROLE_PARAM = "recipientRole";
	protected static final String PARAMETER_RECIPIENT_EMAIL_PARAM = "recipientEmail";
	protected static final String RECIPIENT_EMAIL_DIVIDER = ",";
	
	
	public static final String TASK_NAME = "extras-check-expired-or-missing-manager";	
	
	private Long daysBeforeExpired;
	private LocalDate currentDate = LocalDate.now();
	private Boolean optionLessThanXDays;
	private UUID projectionUserType;
	private Boolean isOptionManagerAlreadyExpired;
	private Boolean isOptionManagerMissing;
	private Boolean isOptionManagerExpiringXDays;
	private UUID recipientRole;
	private String recipientEmail;
	private List<String> recipientEmails;
	private HashMap<String,String> managersAlreadyExpired;
	private HashMap<String,String> managersExpiritingXDays;
	private List<String> managersMissing;
	private UUID organizationUnit;
	
	
	
	@Autowired private IdmIdentityService identityService;
	@Autowired private IdmIdentityContractService identityContractService;
	@Autowired private IdmContractGuaranteeService guaranteeService;
	@Autowired private NotificationManager notificationManager;
	@Autowired private ConfigurationService configurationService;
	@Autowired private EmailNotificationSender emailNotificationSender;
	@Autowired private IdmRoleService roleService;
	@Autowired private IdmNotificationTemplateService notificationTemplateService;

	@Override
	public Boolean process() {
		
		IdmIdentityFilter identityFilter = new IdmIdentityFilter();
		if (projectionUserType!=null) {
			identityFilter.setFormProjection(projectionUserType);	
		}
		
		if (organizationUnit!=null) {
			identityFilter.setTreeNode(organizationUnit);
		}
		
		boolean canContinue = true;
		counter=0l;
		Pageable pageable = PageRequest.of(0, 100, new Sort(Direction.ASC, IdmIdentity_.username.getName()));
		do {
			Page<IdmIdentityDto> users = identityService.find(identityFilter, pageable);
			if (count == null) {
				// report extends long running task - show progress by count and counter lrt attributes
				count = users.getTotalElements();
			}
			

			for(IdmIdentityDto identita : users) {

				if (identita == null) {
					LOG.error("Identity with id [{}] not exists.", identita);
					continue;
				}

				List<IdmIdentityContractDto> contracts = identityContractService.findAllValidForDate(identita.getId(), currentDate, null);

				if(contracts == null || contracts.size() == 0) {
					LOG.error("Identity with id [{}] has no contracts.", identita);
					continue;
				}

				for (IdmIdentityContractDto contract : contracts) {
					
					//search for managers for specific contract
					List<IdmIdentityDto> managers = new ArrayList<IdmIdentityDto>();
					IdmContractGuaranteeFilter filter = new IdmContractGuaranteeFilter();
					filter.setIdentityContractId(contract.getId());
					List<IdmContractGuaranteeDto> managerContracts = guaranteeService.find(filter, null).getContent();
					for (IdmContractGuaranteeDto managerContract : managerContracts) {
						managers.add(identityService.get(managerContract.getGuarantee()));
					}
					
					if(managers.isEmpty()) {
						if (isOptionManagerMissing != null && isOptionManagerMissing == true) {
							managersMissing.add(transformIdentityToString(identita));
							logItemProcessed(identita, new OperationResult.Builder(OperationState.EXECUTED).build());
						}
						continue;
					}

					processManagersContracts(managers, identita);
					
				} 
				
				++counter;
				canContinue = updateState();
				if (!canContinue) {
					break;
				}
			};
			
			pageable = users.hasNext() && canContinue ? users.nextPageable() : null;
		}while (pageable != null);
		
		if (!canContinue) {
			return false;
		}
		return getRecipientsAndSend();
		
	}
	
	//public for test
	public String transformIdentityToString(IdmIdentityDto identity) {

		if (identity == null) {
			return null;
		}
		
		StringBuilder fullName = new StringBuilder();
		fullName.append(identity.getFirstName());
		fullName.append(" ");
		fullName.append(identity.getLastName());
		fullName.append(" (");
		fullName.append(identity.getUsername());
		fullName.append(")");
		
		return fullName.toString();
	}
		
	private void processManagersContracts(List <IdmIdentityDto> managers, IdmIdentityDto identity) {
		
		for (IdmIdentityDto manager : managers) {
			
			List<IdmIdentityContractDto> contracts = identityContractService.findAllValidForDate(manager.getId(), currentDate, null);

			if(contracts == null || contracts.isEmpty()) {
				if (isOptionManagerAlreadyExpired != null && isOptionManagerAlreadyExpired == true) {
					IdmIdentityContractDto expiredContract = identityContractService.findLastExpiredContract(manager.getId(), currentDate);
					String ppvEnd = expiredContract.getValidTill().format(DateTimeFormatter.ofPattern(configurationService.getDateFormat()));	
					addManagerToHashMap(managersAlreadyExpired,manager,identity,ppvEnd);
					logItemProcessed(identity, new OperationResult.Builder(OperationState.EXECUTED).build());
				}
				continue;
			}
			
			if (isOptionManagerExpiringXDays != null && isOptionManagerExpiringXDays == true) {
				
				//search if any contracts are still valid in X days
				List<IdmIdentityContractDto> contractsExpiring = identityContractService.findAllValidForDate(
						manager.getId(),
						currentDate.plusDays(Math.toIntExact(daysBeforeExpired+1)),
						null);
				
				//there are some valid contracts, it is not expiring soon (in x days)
				if(!contractsExpiring.isEmpty()) {
					continue;
				}
				
				IdmIdentityContractDto expiringContract = identityContractService.getPrimeContract(manager.getId());
				if (expiringContract == null) {
					continue;
				}
				String ppvEnd = expiringContract.getValidTill().format(DateTimeFormatter.ofPattern(configurationService.getDateFormat()));
				LocalDate validTill = expiringContract.getValidTill();
				if (optionLessThanXDays != null && optionLessThanXDays == true) {
					if (currentDate.isBefore(validTill) || currentDate.plusDays(daysBeforeExpired).isEqual(validTill) || currentDate.isEqual(validTill)) {
						addManagerToHashMap(managersExpiritingXDays,manager,identity,ppvEnd);
					}
				}else {
					if (currentDate.plusDays(daysBeforeExpired).isEqual(validTill)) {
						addManagerToHashMap(managersExpiritingXDays,manager,identity,ppvEnd);
					}
				}
				logItemProcessed(identity, new OperationResult.Builder(OperationState.EXECUTED).build());

			}
			
		}
	}
	
	private Boolean addManagerToHashMap(HashMap<String,String> managers, IdmIdentityDto manager, IdmIdentityDto identity, String ppvEnd) {
		if (managers == null || manager == null || identity == null) {
			return false;
		}
		String managerStr;
		String identityStr = transformIdentityToString(identity);
		
		if (StringUtils.isBlank(ppvEnd)) {
			managerStr = transformIdentityToString(manager);
		}else{
			managerStr = transformIdentityToString(manager)+" "+ppvEnd;
		}
		
		if (managers.containsKey(identityStr)) {
			String tempValue = managers.get(identityStr);
			managers.put(identityStr, tempValue+", "+managerStr);
		}else {
			managers.put(identityStr, managerStr);
		}
		
		return true;
	}
	
	@Override
	public void init(Map<String, Object> properties) {
		super.init(properties);

		daysBeforeExpired = getParameterConverter().toLong(properties, PARAMETER_DAYS_BEFORE);
		optionLessThanXDays = getParameterConverter().toBoolean(properties, PARAMETER_DAYS_BEFORE_LESS_THAN);
		isOptionManagerAlreadyExpired = getParameterConverter().toBoolean(properties, PARAMETER_EMAIL_INFO_MANAGER_ALREADY_EXPIRED);
		isOptionManagerMissing = getParameterConverter().toBoolean(properties, PARAMETER_EMAIL_INFO_MANAGER_MISSING);
		isOptionManagerExpiringXDays = getParameterConverter().toBoolean(properties, PARAMETER_EMAIL_INFO_MANAGER_EXPIRING_X_DAYS);
		recipientRole = getParameterConverter().toUuid(properties, PARAMETER_RECIPIENT_ROLE_PARAM);
		recipientEmail = getParameterConverter().toString(properties, PARAMETER_RECIPIENT_EMAIL_PARAM);
		projectionUserType = getParameterConverter().toUuid(properties, PARAMETER_USER_PROJECTION);
		organizationUnit = getParameterConverter().toUuid(properties, PARAMETER_ORGANIZATION_UNIT);
		
		if(isOptionManagerExpiringXDays != null && isOptionManagerExpiringXDays == true) {
			if (daysBeforeExpired == null || daysBeforeExpired.compareTo(0L) <= -1) {
				throw new ResultCodeException(ExtrasResultCode.CONTRACT_END_NOTIFICATION_DAYS_BEFORE_NOT_SPECIFIED,
						ImmutableMap.of("daysBeforeExpired", daysBeforeExpired == null ? "null" : daysBeforeExpired));
			}
			managersExpiritingXDays = new HashMap<String,String>();
		}
		
		if(recipientRole == null && StringUtils.isBlank(recipientEmail)) {
			throw new ResultCodeException(ExtrasResultCode.NO_RECIPIENTS_FOUND,
					ImmutableMap.of("recipientRole and recipientEmail is ", "null"));
		}

		if (!StringUtils.isBlank(recipientEmail)) {
			recipientEmails = new ArrayList<String>();
			for (String email : recipientEmail.split(RECIPIENT_EMAIL_DIVIDER)) {
				recipientEmails.add(email.trim());
			}
		}
		
		if (isOptionManagerAlreadyExpired != null && isOptionManagerAlreadyExpired == true) {
			managersAlreadyExpired = new HashMap<String,String>();
		}

		if (isOptionManagerMissing != null && isOptionManagerMissing == true) {
			managersMissing= new ArrayList<String>();	
		}
		

		
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String, Object> props = super.getProperties();
		props.put(PARAMETER_DAYS_BEFORE, daysBeforeExpired);
		props.put(PARAMETER_DAYS_BEFORE_LESS_THAN, optionLessThanXDays);
		props.put(PARAMETER_USER_PROJECTION, projectionUserType);
		props.put(PARAMETER_ORGANIZATION_UNIT,organizationUnit);
		props.put(PARAMETER_RECIPIENT_ROLE_PARAM, recipientRole);
		props.put(PARAMETER_RECIPIENT_EMAIL_PARAM, recipientEmail);
		props.put(PARAMETER_EMAIL_INFO_MANAGER_ALREADY_EXPIRED, isOptionManagerAlreadyExpired);
		props.put(PARAMETER_EMAIL_INFO_MANAGER_MISSING, isOptionManagerMissing);
		props.put(PARAMETER_EMAIL_INFO_MANAGER_EXPIRING_X_DAYS, isOptionManagerExpiringXDays);
		
		return props;
	}
	
	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		List<IdmFormAttributeDto> attributes = new LinkedList<>();

		IdmFormAttributeDto recipientEmailAttr = new IdmFormAttributeDto(
				PARAMETER_RECIPIENT_EMAIL_PARAM,
				PARAMETER_RECIPIENT_EMAIL_PARAM,
				PersistentType.SHORTTEXT);
		recipientEmailAttr.setPlaceholder("Email recipients divided by comma.");
		attributes.add(recipientEmailAttr);

		IdmFormAttributeDto recipientRoleAttr = new IdmFormAttributeDto(
				PARAMETER_RECIPIENT_ROLE_PARAM,
				PARAMETER_RECIPIENT_ROLE_PARAM,
				PersistentType.UUID);
		recipientRoleAttr.setFaceType(BaseFaceType.ROLE_SELECT);
		recipientRoleAttr.setPlaceholder("Choose role which will be notified.");
		attributes.add(recipientRoleAttr);
		
		IdmFormAttributeDto projectionTypeAttr = new IdmFormAttributeDto(
				PARAMETER_USER_PROJECTION,
				PARAMETER_USER_PROJECTION,
				PersistentType.UUID);
		projectionTypeAttr.setFaceType(BaseFaceType.FORM_PROJECTION_SELECT);
		projectionTypeAttr.setPlaceholder("Choose projection type");
		attributes.add(projectionTypeAttr);
		
		IdmFormAttributeDto organizationUnitAttr = new IdmFormAttributeDto(
				PARAMETER_ORGANIZATION_UNIT,
				PARAMETER_ORGANIZATION_UNIT,
				PersistentType.UUID);
		organizationUnitAttr.setFaceType(BaseFaceType.TREE_NODE_SELECT);
		organizationUnitAttr.setPlaceholder("Choose tree node");
		attributes.add(organizationUnitAttr);
				
		IdmFormAttributeDto managerAlreadyExpiredAttr = new IdmFormAttributeDto(
				PARAMETER_EMAIL_INFO_MANAGER_ALREADY_EXPIRED,
				PARAMETER_EMAIL_INFO_MANAGER_ALREADY_EXPIRED,
				PersistentType.BOOLEAN);
		managerAlreadyExpiredAttr.setDefaultValue(Boolean.TRUE.toString());
		attributes.add(managerAlreadyExpiredAttr);
		
		IdmFormAttributeDto managerMissingAttr = new IdmFormAttributeDto(
				PARAMETER_EMAIL_INFO_MANAGER_MISSING,
				PARAMETER_EMAIL_INFO_MANAGER_MISSING,
				PersistentType.BOOLEAN);
		managerMissingAttr.setDefaultValue(Boolean.TRUE.toString());
		attributes.add(managerMissingAttr);
		
		IdmFormAttributeDto managerExpiringXDaysAttr = new IdmFormAttributeDto(
				PARAMETER_EMAIL_INFO_MANAGER_EXPIRING_X_DAYS,
				PARAMETER_EMAIL_INFO_MANAGER_EXPIRING_X_DAYS,
				PersistentType.BOOLEAN);
		managerExpiringXDaysAttr.setDefaultValue(Boolean.TRUE.toString());
		attributes.add(managerExpiringXDaysAttr);
		
		IdmFormAttributeDto daysBeforeAttr = new IdmFormAttributeDto(
				PARAMETER_DAYS_BEFORE,
				PARAMETER_DAYS_BEFORE,
				PersistentType.SHORTTEXT);
		daysBeforeAttr.setDefaultValue("14");
		attributes.add(daysBeforeAttr);
		
		IdmFormAttributeDto daysBeforeLessThanAttr = new IdmFormAttributeDto(
				PARAMETER_DAYS_BEFORE_LESS_THAN,
				PARAMETER_DAYS_BEFORE_LESS_THAN,
				PersistentType.BOOLEAN);
		daysBeforeLessThanAttr.setDefaultValue(Boolean.TRUE.toString());
		attributes.add(daysBeforeLessThanAttr);
		
		return attributes;
	}
	
	private Boolean getRecipientsAndSend() {
		Boolean isRoleSent=false, isEmailSent = false;
		
		if(recipientRole!=null) {
			isRoleSent = sendToRoleRecipients(recipientRole);
		}

		if(recipientEmails!=null && !recipientEmails.isEmpty()) {
			isEmailSent = sendToEmailRecipients(recipientEmails);
		}
		
		return (isRoleSent || isEmailSent);
	}
	
	private Boolean sendToRoleRecipients(UUID roleId) {
		List<IdmIdentityDto> recipients = new ArrayList<IdmIdentityDto>();
		
		if (roleId != null) {
			IdmRoleDto role = roleService.get(roleId);
			if (role != null) {
				List<IdmIdentityDto> recipientsFromRoleBefore = identityService.findAllByRoleName(role.getCode());
				recipients.addAll(recipientsFromRoleBefore);
			}
			
			if (recipients.isEmpty()) {
				LOG.info("No identities in role [{}].", role.getCode());
				return false;
			}
		}
				
		
		
		notificationManager.send(
				ExtrasModuleDescriptor.TOPIC_CHECK_EXPIRED_OR_MISSING_MANAGER,
				new IdmMessageDto
						.Builder()
						.setLevel(NotificationLevel.INFO)
						.addParameter("missingManagers", managersMissing)
						.addParameter("expiredManagers", managersAlreadyExpired)
						.addParameter("expiringManagers", managersExpiritingXDays)
						.addParameter("optionExpired", isOptionManagerAlreadyExpired)
						.addParameter("optionMissing", isOptionManagerMissing)
						.addParameter("optionExpiring", isOptionManagerExpiringXDays)
						.build(),
				recipients
		);

		return true;	
	}
	
	private Boolean sendToEmailRecipients(List<String> emails) {
		
		if (emails.isEmpty()) {
			LOG.info("No recipients [{}].", emails);
			return false;
		}
		
		IdmNotificationTemplateDto template = notificationTemplateService.getByCode("checkExpiredOrMissingManager");
		emailNotificationSender.send(
				new IdmMessageDto
						.Builder()
						.setTemplate(template)
						.setLevel(NotificationLevel.INFO)
						.addParameter("missingManagers", managersMissing)
						.addParameter("expiredManagers", managersAlreadyExpired)
						.addParameter("expiringManagers", managersExpiritingXDays)
						.addParameter("optionExpired", isOptionManagerAlreadyExpired)
						.addParameter("optionMissing", isOptionManagerMissing)
						.addParameter("optionExpiring", isOptionManagerExpiringXDays)
						.build(),
						emails.toArray(new String[0])
		);

		return true;	
	}
		
	@Override
	public String getName() {
		return TASK_NAME;
	}
	
	//Helper test unit getters
	public HashMap<String, String> getManagersExpiritingXDays() {
		return managersExpiritingXDays;
	}

	public HashMap<String, String> getManagersAlreadyExpired() {
		return managersAlreadyExpired;
	}

	public List<String> getManagersMissing() {
		return managersMissing;
	}
}
