package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.quartz.DisallowConcurrentExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmContractGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.eav.api.domain.BaseFaceType;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import eu.bcvsolutions.idm.core.notification.api.domain.NotificationLevel;
import eu.bcvsolutions.idm.core.notification.api.dto.IdmMessageDto;
import eu.bcvsolutions.idm.core.notification.api.service.NotificationManager;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
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
public class CheckExpiredOrMissingManager extends AbstractSchedulableTaskExecutor<Boolean> {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CheckExpiredOrMissingManager.class);
	protected static final String PARAMETER_DAYS_BEFORE = "xdaysbeforecontractexpires";
	protected static final String PARAMETER_DAYS_BEFORE_LESS_THAN = "xdaysbeforecontractexpireslessthan";
	protected static final String PARAMETER_USER_PROJECTION = "externalContractExpiredSenderEmail";
	protected static final String PARAMETER_EMAIL_INFO_MANAGER_ALREADY_EXPIRED = "manageralreadyexpired";
	protected static final String PARAMETER_EMAIL_INFO_MANAGER_MISSING = "managermissing";
	protected static final String PARAMETER_EMAIL_INFO_MANAGER_EXPIRING_X_DAYS = "managerexpiringinxdays";
	protected static final String PARAMETER_RECIPIENT_ROLE_PARAM = "recipientrole";
	protected static final String PARAMETER_RECIPIENT_EMAIL_PARAM = "recipientemail";
	
	public static final String TASK_NAME = "vfn-check-expired-external-users";	
	
	private Long daysBeforeExpired;
	private LocalDate currentDate = LocalDate.now();
	private LocalDate validMinusXDays = LocalDate.now();
	private Boolean lessThanXDays;
	private UUID projectionUserType;
	private Boolean isOptionManagerAlreadyExpired;
	private Boolean isOptionManagerMissing;
	private Boolean isOptionManagerExpiringXDays;
	private UUID recipientRole;
	private String recipientEmail;
	private HashMap managersAlreadyExpired;
	private HashMap managersExpiritingXDays;
	private List managersMissing;
	
	
	
	@Autowired private IdmIdentityService identityService;
	@Autowired private IdmIdentityContractService identityContractService;
	@Autowired private IdmContractGuaranteeService idmContractGuarantee;
	@Autowired private NotificationManager notificationManager;
	@Autowired private ConfigurationService configurationService;

	@Override
	public Boolean process() {
		
		IdmIdentityFilter identityFilter = new IdmIdentityFilter();
		if (projectionUserType!=null) {
			identityFilter.setFormProjection(projectionUserType);	
		}
		
		IdmIdentityDto identity = null;
		Pageable pageable = PageRequest.of(0, 100, new Sort(Direction.ASC, IdmIdentity_.username.getName()));
		do {
			Page<IdmIdentityDto> users = identityService.find(identityFilter, pageable);
			if (count == null) {
				// report extends long running task - show progress by count and counter lrt attributes
				count = users.getTotalElements();
			}
			boolean canContinue = true;

			for (Iterator<IdmIdentityDto> i = users.iterator(); i.hasNext() && canContinue;) {
				identity = i.next();

				if (identity == null) {
					LOG.error("Identity with id [{}] not exists.", identity);
					continue;
				}

				List<IdmIdentityContractDto> contracts = identityContractService.findAllValidForDate(identity.getId(), currentDate, null);

				if(contracts == null || contracts.size()==0) {
					LOG.error("Identity with id [{}] has no contracts.", identity);
					continue;
				}

				for (IdmIdentityContractDto contract : contracts) {
					
					if(contract==null) {
						LOG.error("Contract is null for identity with id [{}].", identity);
						continue;
					}
					
					//search for managers
					IdmIdentityFilter filter = new IdmIdentityFilter();
					filter.setManagersFor(identity.getId());
					filter.setManagersByContract(contract.getId());

					List<IdmIdentityDto> managers = identityService.find(filter, null).getContent();

					if(managers==null || managers.isEmpty()) {
						if (isOptionManagerMissing==true) {
							managersMissing.add(identity);
						}
						continue;
					}

					processManagersContracts(managers, identity);

					StringBuilder fullName = new StringBuilder();
					fullName.append(identity.getFirstName());
					fullName.append(" ");
					fullName.append(identity.getLastName());
					fullName.append(" (");
					fullName.append(identity.getUsername());
					fullName.append(")");
					
					++counter;
					canContinue = updateState();
				} 
			}
			
			pageable = users.hasNext() && canContinue ? users.nextPageable() : null;
		}while (pageable != null);
		
		return true;
	}
	
	private void processManagersContracts(List <IdmIdentityDto> managers, IdmIdentityDto identity) {
		
		for (IdmIdentityDto manager : managers) {
			
			List<IdmIdentityContractDto> contracts = identityContractService.findAllValidForDate(manager.getId(), currentDate, null);

			if(contracts==null || contracts.isEmpty()) {
				if (isOptionManagerAlreadyExpired==true) {
					managersAlreadyExpired.put(identity,manager);
				}
				continue;
			}
			
			for (IdmIdentityContractDto contract : contracts) {
				
				if (contract.getValidTill() == null) {
					// the contract has infinite validity, leave alone
					managersExpiritingXDays.remove(identity);
					break;
				}

				validMinusXDays = contract.getValidTill().minusDays(daysBeforeExpired);

				// this we are interested in contracts which end in x days or sooner 
				if (!currentDate.isAfter(validMinusXDays.minusDays(1))) {
					// the contract ends on different times than specified, leave alone
					managersExpiritingXDays.remove(identity);
					break;
				}

				
				if (isOptionManagerExpiringXDays==true) {
					String ppvEnd = "";
					ppvEnd = contract.getValidTill().format(DateTimeFormatter.ofPattern(configurationService.getDateFormat()));	

					if (lessThanXDays==true) {
						if (currentDate.isBefore(validMinusXDays)) {
							managersExpiritingXDays.put(identity, manager);
						}
					}else {
						if (currentDate.isEqual(validMinusXDays)) {
							managersExpiritingXDays.put(identity, manager);
						}
					}
				}
			}
			
		}
	}
	
	@Override
	public void init(Map<String, Object> properties) {
		super.init(properties);

		daysBeforeExpired = getParameterConverter().toLong(properties, PARAMETER_DAYS_BEFORE);
		lessThanXDays = getParameterConverter().toBoolean(properties, PARAMETER_DAYS_BEFORE_LESS_THAN);
		isOptionManagerAlreadyExpired = getParameterConverter().toBoolean(properties, PARAMETER_EMAIL_INFO_MANAGER_ALREADY_EXPIRED);
		isOptionManagerMissing = getParameterConverter().toBoolean(properties, PARAMETER_EMAIL_INFO_MANAGER_MISSING);
		isOptionManagerExpiringXDays = getParameterConverter().toBoolean(properties, PARAMETER_EMAIL_INFO_MANAGER_EXPIRING_X_DAYS);
		recipientRole = getParameterConverter().toUuid(properties, PARAMETER_RECIPIENT_ROLE_PARAM);
		recipientEmail = getParameterConverter().toString(properties, PARAMETER_RECIPIENT_EMAIL_PARAM);
		projectionUserType = getParameterConverter().toUuid(properties, PARAMETER_USER_PROJECTION);
		
		if(isOptionManagerExpiringXDays!=null && isOptionManagerExpiringXDays==true) {
			if (daysBeforeExpired == null || daysBeforeExpired.compareTo(0L) <= -1) {
				throw new ResultCodeException(ExtrasResultCode.CONTRACT_END_NOTIFICATION_DAYS_BEFORE,
						ImmutableMap.of("daysBeforeExpired", daysBeforeExpired == null ? "null" : daysBeforeExpired));
			}
			managersExpiritingXDays = new HashMap();
		}
		
		if(recipientRole==null && recipientEmail==null) {
			throw new ResultCodeException(ExtrasResultCode.NO_RECIPIENTS_FOUND,
					ImmutableMap.of("recipientRole and recipientEmail is ", "null"));
		}
		
		if (isOptionManagerAlreadyExpired==true) {
			managersAlreadyExpired = new HashMap();
		}

		if (isOptionManagerMissing==true) {
			managersMissing= new ArrayList();	
		}
		

		
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String, Object> props = super.getProperties();
		props.put(PARAMETER_DAYS_BEFORE, daysBeforeExpired);
		props.put(PARAMETER_DAYS_BEFORE_LESS_THAN, lessThanXDays);
		props.put(PARAMETER_USER_PROJECTION, projectionUserType);
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
		attributes.add(recipientEmailAttr);

		IdmFormAttributeDto recipientRoleAttr = new IdmFormAttributeDto(
				PARAMETER_RECIPIENT_ROLE_PARAM,
				PARAMETER_RECIPIENT_ROLE_PARAM,
				PersistentType.UUID);
		recipientRoleAttr.setFaceType(BaseFaceType.ROLE_SELECT);
		recipientRoleAttr.setPlaceholder("Choose role which will be notified...");
		attributes.add(recipientRoleAttr);
		
		IdmFormAttributeDto projectionTypeAttr = new IdmFormAttributeDto(
				PARAMETER_USER_PROJECTION,
				PARAMETER_USER_PROJECTION,
				PersistentType.UUID);
		projectionTypeAttr.setFaceType(BaseFaceType.FORM_PROJECTION_SELECT);
		projectionTypeAttr.setPlaceholder("Choose projection type");
		attributes.add(projectionTypeAttr);
		
		IdmFormAttributeDto managerAlreadyExpiredAttr = new IdmFormAttributeDto(
				PARAMETER_EMAIL_INFO_MANAGER_ALREADY_EXPIRED,
				PARAMETER_EMAIL_INFO_MANAGER_ALREADY_EXPIRED,
				PersistentType.BOOLEAN);
		attributes.add(managerAlreadyExpiredAttr);
		
		IdmFormAttributeDto managerMissingAttr = new IdmFormAttributeDto(
				PARAMETER_EMAIL_INFO_MANAGER_MISSING,
				PARAMETER_EMAIL_INFO_MANAGER_MISSING,
				PersistentType.BOOLEAN);
		attributes.add(managerMissingAttr);
		
		IdmFormAttributeDto managerExpiringXDaysAttr = new IdmFormAttributeDto(
				PARAMETER_EMAIL_INFO_MANAGER_EXPIRING_X_DAYS,
				PARAMETER_EMAIL_INFO_MANAGER_EXPIRING_X_DAYS,
				PersistentType.BOOLEAN);
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
		attributes.add(daysBeforeLessThanAttr);
		
		return attributes;
	}
	
	private void sendNotification(String topic, List<IdmIdentityDto> recipients, String fullName,
			IdmIdentityDto identity, String ppvEnd) {
		notificationManager.send(
				topic,
				new IdmMessageDto
						.Builder()
						.setLevel(NotificationLevel.INFO)
						.addParameter("userIdentity", identity)
						.addParameter("user", fullName)
						.addParameter("ppvEnd", ppvEnd)
						.build(),
				recipients
		);
	}
	
	private Boolean getRecipientsAndSend(List<IdmIdentityDto> recipients, boolean expireToday, 
			String fullName, IdmIdentityDto identity, String ppvEnd) {
		if (recipients.isEmpty()) {
			LOG.info("No manager for identity [{}].", identity);
			return false;
		}
		if (expireToday) {
			//ending today
			sendNotification(VfnModuleDescriptor.TOPIC_EXTERNAL_CONTRACT_EXPIRING, recipients, 
					fullName, identity, ppvEnd);
		} else {
			sendNotification(VfnModuleDescriptor.TOPIC_EXTERNAL_CONTRACT_EXPIRE_IN_X_DAYS, recipients, 
					fullName, identity, ppvEnd);
		}	

		logItemProcessed(identity, new OperationResult.Builder(OperationState.EXECUTED).build());
		return true;
	}
	
	@Override
	public String getName() {
		return TASK_NAME;
	}

}
