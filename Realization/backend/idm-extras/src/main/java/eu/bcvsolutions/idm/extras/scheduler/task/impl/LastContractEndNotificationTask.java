package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.quartz.DisallowConcurrentExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.api.domain.IdentityState;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityContractFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.eav.api.domain.BaseFaceType;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import eu.bcvsolutions.idm.core.notification.api.domain.NotificationLevel;
import eu.bcvsolutions.idm.core.notification.api.dto.IdmMessageDto;
import eu.bcvsolutions.idm.core.notification.api.service.NotificationManager;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmProcessedTaskItemDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.filter.IdmProcessedTaskItemFilter;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableStatefulExecutor;
import eu.bcvsolutions.idm.core.scheduler.api.service.IdmProcessedTaskItemService;
import eu.bcvsolutions.idm.extras.ExtrasModuleDescriptor;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * This task sends a notification to those with a specified role and optionally the manager of the contract or owner of the contract. The
 * notifications says that the user is leaving in a specified number of days, i. e., the user's last contract
 * ends.
 * 
 * @author Tomáš Doischer
 * @author Luboš Čábelka
 */
@Service
@DisallowConcurrentExecution
@Description("Task which will send notification X days before end of the last contract. It is recommended to "
		+ "run for the first time without sending emails.")

public class LastContractEndNotificationTask extends AbstractSchedulableStatefulExecutor<IdmIdentityContractDto> {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(LastContractEndNotificationTask.class);
	protected static final String PARAMETER_DAYS_BEFORE = "How many days before the end of the contract should the notification be sent";
	protected static final String RECIPIENT_ROLE_BEFORE_PARAM = "Recipient role of notification";
	protected static final String SEND_TO_MANAGER_BEFORE_PARAM = "Should the manager of the contract receive the notification?";
	protected static final String PARAMETER_SEND_TO_IDENTITY = "Should the owner of the contract receive the notification?";
	protected static final String PARAMETER_NOTIFICATION_TOPIC = "User specified topic for notification (can be empty)";
	protected static final String PARAMETER_SEND_IF_MANUALLY_DISABLED = "Should the notification be sent when the user is manually disabled?";
	
	private Long daysBeforeEnd;
	private LocalDate currentDate = LocalDate.now();
	private LocalDate validMinusXDays = LocalDate.now();
	private UUID recipientRoleBefore = null;
	private Boolean sendToManagerBefore;
	private Boolean sendToIdentity;
	private String notificationTopic;
	private Boolean sendIfManuallyDisabled;
	
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private NotificationManager notificationManager;
	@Autowired
	private IdmIdentityContractService identityContractService;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private IdmIdentityRoleService identityRoleService;
	@Autowired
	private ConfigurationService configurationService;
	@Autowired
	private IdmProcessedTaskItemService itemService;

	@Override
	public void init(Map<String, Object> properties) {
		super.init(properties);

		daysBeforeEnd = getParameterConverter().toLong(properties, PARAMETER_DAYS_BEFORE);
		recipientRoleBefore = getParameterConverter().toUuid(properties, RECIPIENT_ROLE_BEFORE_PARAM);
		sendToManagerBefore = getParameterConverter().toBoolean(properties, SEND_TO_MANAGER_BEFORE_PARAM);
		if (sendToManagerBefore == null) {
			sendToManagerBefore = Boolean.FALSE;
		}
		sendToIdentity = getParameterConverter().toBoolean(properties, PARAMETER_SEND_TO_IDENTITY);
		if (sendToIdentity == null) {
			sendToIdentity = Boolean.FALSE;
		}
		if (daysBeforeEnd == null || daysBeforeEnd.compareTo(0L) <= -1) {
			throw new ResultCodeException(ExtrasResultCode.CONTRACT_END_NOTIFICATION_DAYS_BEFORE,
					ImmutableMap.of("daysBeforeEnd", daysBeforeEnd == null ? "null" : daysBeforeEnd));
		}
		
		notificationTopic = getParameterConverter().toString(properties, PARAMETER_NOTIFICATION_TOPIC);
		sendIfManuallyDisabled = getParameterConverter().toBoolean(properties, PARAMETER_SEND_IF_MANUALLY_DISABLED);
		if (sendIfManuallyDisabled == null) {
			sendIfManuallyDisabled = Boolean.FALSE;
		}
	}
	
	@Override
	public Map<String, Object> getProperties() {
		Map<String, Object> props = super.getProperties();
		props.put(PARAMETER_DAYS_BEFORE, daysBeforeEnd);
		props.put(RECIPIENT_ROLE_BEFORE_PARAM, recipientRoleBefore);
		props.put(SEND_TO_MANAGER_BEFORE_PARAM, sendToManagerBefore);
		props.put(PARAMETER_SEND_TO_IDENTITY, sendToIdentity);
		props.put(PARAMETER_SEND_IF_MANUALLY_DISABLED, sendIfManuallyDisabled);
		props.put(PARAMETER_NOTIFICATION_TOPIC, notificationTopic);
		return props;
	}
	
	@Override
	public Page<IdmIdentityContractDto> getItemsToProcess(Pageable pageable) {
		IdmIdentityContractFilter identityContractFilter = new IdmIdentityContractFilter();
		if (daysBeforeEnd == 0) {
			identityContractFilter.setValidTill(currentDate);
		} else {
			identityContractFilter.setValid(Boolean.TRUE);
		}
		return identityContractService.find(identityContractFilter, pageable);
	}
	
	@Override
	public Optional<OperationResult> processItem(IdmIdentityContractDto dto) {
		if (dto.getValidTill() == null) {
			// the contract has infinite validity, leave alone
			return Optional.of(new OperationResult.Builder(OperationState.NOT_EXECUTED).build());
		}

		validMinusXDays = dto.getValidTill().minusDays(Math.toIntExact(daysBeforeEnd));
		
		// we are interested in contracts which end in x days or sooner 
		if (!currentDate.isAfter(validMinusXDays.minusDays(1))) {
			// the contract ends on different times than specified, leave alone
			return Optional.of(new OperationResult.Builder(OperationState.NOT_EXECUTED).build());
		}
		
		if (!isLastContract(dto)) {
			// the contract owner has more contracts valid in the future and is not leaving, leave alone
			return Optional.of(new OperationResult.Builder(OperationState.NOT_EXECUTED).build());
		}
		
		IdmIdentityDto identity = DtoUtils.getEmbedded(dto, IdmIdentityContract_.identity, IdmIdentityDto.class);
		if (!sendIfManuallyDisabled && identity.getState() == IdentityState.DISABLED_MANUALLY) {
			LOG.info("Identity [{}] is manually disabled, notification will not be sent.", identity.getUsername());
			return Optional.of(new OperationResult.Builder(OperationState.NOT_EXECUTED).build());
		}
		String fullName = identity.getFirstName() + " " + identity.getLastName() + " (" + 
				identity.getUsername() + ")";

		String position = "";
		if (dto.getWorkPosition() != null) {
			position = DtoUtils.getEmbedded(dto, IdmIdentityContract_.workPosition, IdmTreeNodeDto.class).getName();
		}
		
		String ppvEnd = "";
		if (dto.getValidTill() != null) {
			ppvEnd = dto.getValidTill().format(DateTimeFormatter.ofPattern(configurationService.getDateFormat()));
		}

		IdmIdentityDto guarantee = getManagerForContract(dto.getId(), identity.getId());
		if (guarantee == null) {
			LOG.info("ContractEndNotificationTask No manager for contract [{}].", dto.getId());
		}
		
		// end of contract today and daysBeforeEnd set to 0
		if (daysBeforeEnd == 0L && currentDate.isEqual(validMinusXDays)) {
			return getRecipientsAndSend(guarantee, false, fullName, identity, position, ppvEnd);
		} 

		// end of contract will be in daysBeforeEnd days
		if (daysBeforeEnd > 0L) {
			return getRecipientsAndSend(guarantee, true, fullName, identity, position, ppvEnd);
		} else {
			return Optional.of(new OperationResult.Builder(OperationState.NOT_EXECUTED).build());
		}
	}
	
	private void sendNotification(String topic, List<IdmIdentityDto> recipients, IdmIdentityDto manager, String fullName,
			IdmIdentityDto identity, String position, String ppvEnd) {
		notificationManager.send(
				topic,
				new IdmMessageDto
						.Builder()
						.setLevel(NotificationLevel.INFO)
						.addParameter("userIdentity", identity)
						.addParameter("user", fullName)
						.addParameter("department", position)
						.addParameter("ppvEnd", ppvEnd)
						.addParameter("manager", manager)
						.build(),
				recipients
		);
	}
	
	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		List<IdmFormAttributeDto> attributes = new LinkedList<>();
		IdmFormAttributeDto daysBeforeAttr = new IdmFormAttributeDto(
				PARAMETER_DAYS_BEFORE,
				PARAMETER_DAYS_BEFORE,
				PersistentType.TEXT);
		daysBeforeAttr.setDefaultValue("14");
		attributes.add(daysBeforeAttr);
		
		IdmFormAttributeDto recipientRoleBeforeAttr = new IdmFormAttributeDto(
				RECIPIENT_ROLE_BEFORE_PARAM,
				RECIPIENT_ROLE_BEFORE_PARAM,
				PersistentType.UUID);
		recipientRoleBeforeAttr.setFaceType(BaseFaceType.ROLE_SELECT);
		recipientRoleBeforeAttr.setPlaceholder("Choose role which will be notified...");
		attributes.add(recipientRoleBeforeAttr);
		
		IdmFormAttributeDto sendToManagerBeforeAttr = new IdmFormAttributeDto(
				SEND_TO_MANAGER_BEFORE_PARAM,
				SEND_TO_MANAGER_BEFORE_PARAM,
				PersistentType.BOOLEAN);
		attributes.add(sendToManagerBeforeAttr);

		IdmFormAttributeDto sendToIdentityAttr = new IdmFormAttributeDto(
				PARAMETER_SEND_TO_IDENTITY,
				PARAMETER_SEND_TO_IDENTITY,
				PersistentType.BOOLEAN);
		attributes.add(sendToIdentityAttr);
		
		IdmFormAttributeDto sendIfManuallyDisabledAttr = new IdmFormAttributeDto(
				PARAMETER_SEND_IF_MANUALLY_DISABLED,
				PARAMETER_SEND_IF_MANUALLY_DISABLED,
				PersistentType.BOOLEAN);
		sendIfManuallyDisabledAttr.setDefaultValue("true");
		attributes.add(sendIfManuallyDisabledAttr);
		
		IdmFormAttributeDto notificationTopicAttr = new IdmFormAttributeDto(
				PARAMETER_NOTIFICATION_TOPIC,
				PARAMETER_NOTIFICATION_TOPIC,
				PersistentType.SHORTTEXT);
		attributes.add(notificationTopicAttr);
		
		return attributes;
	}

	/**
	 * Finds the manager for the contract.
	 * 
	 * @param contractId
	 * @param identityId
	 * @return
	 */
	protected IdmIdentityDto getManagerForContract(UUID contractId, UUID identityId) {	
		IdmIdentityFilter filter = new IdmIdentityFilter();
		filter.setManagersFor(identityId);
		filter.setManagersByContract(contractId);

		List<IdmIdentityDto> managers = identityService.find(filter, null).getContent();
		return managers.stream().findFirst().orElse(null);
	}
	
	/**
	 * Puts together the recipients of the notification (those who receive it thanks to a role and 
	 * based on configuration the manager of the user).
	 * 
	 * @param manager
	 * @return
	 */
	private List<IdmIdentityDto> getRecipients(IdmIdentityDto manager, IdmIdentityDto identity) {
		List<IdmIdentityDto> recipients = new ArrayList<>();

		if (sendToManagerBefore && manager != null) {
			recipients.add(manager);
		}
		
		if (sendToIdentity && identity != null) {
			recipients.add(identity);
		}
		
		if (recipientRoleBefore != null) {
			List<IdmIdentityDto> recipientsFromRoleBefore = getUsersByRoleId(recipientRoleBefore);
			recipients.addAll(recipientsFromRoleBefore);
		}

		return recipients;
	}
	
	/**
	 * Returns a list of identities with a role.
	 * 
	 * @param roleId
	 * @return
	 */
	protected List<IdmIdentityDto> getUsersByRoleId(UUID roleId) {
		List<IdmIdentityDto> users = new ArrayList<>();
		IdmRoleDto roleDto = roleService.get(roleId);

		if (roleDto == null) {
			return users;
		}

		IdmIdentityRoleFilter identityRoleFilter = new IdmIdentityRoleFilter();
		identityRoleFilter.setRoleId(roleDto.getId());
		Page<IdmIdentityRoleDto> result = identityRoleService.find(identityRoleFilter, null);

		if (!result.getContent().isEmpty()) {
			users.addAll(result.getContent()
					.stream()
					.map(idmIdentityRoleDto -> {
						IdmIdentityContractDto identityContractDto = DtoUtils.getEmbedded(idmIdentityRoleDto, IdmIdentityRole_.identityContract, IdmIdentityContractDto.class);
						return DtoUtils.getEmbedded(identityContractDto, IdmIdentityContract_.identity, IdmIdentityDto.class);
					})
					.collect(Collectors.toList()));
		}
		
		return users;
	}
	
	/**
	 * Checks that the owner of a contract does not have a contract which is valid in the future
	 * or has unlimited validity.
	 * 
	 * @param checkedContract
	 * @return
	 */
	protected Boolean isLastContract(IdmIdentityContractDto checkedContract) {
		List<IdmIdentityContractDto> contracts = identityContractService.findAllByIdentity(checkedContract.getIdentity());
		if (contracts != null && contracts.size() == 1) {
			return Boolean.TRUE;
		}
		if (contracts == null) {
			return Boolean.TRUE;
		}
		for (IdmIdentityContractDto contract : contracts) {
			if (contract.getValidTill() == null && !contract.isDisabled()) {
				return Boolean.FALSE;
			} 
			if (contract.getValidTill().isAfter(validMinusXDays.plusDays(Math.toIntExact(daysBeforeEnd + 1)))
					&& !contract.isDisabled()) {
				return Boolean.FALSE;
			}
		}
		
		return Boolean.TRUE;
	}
	
	private Optional<OperationResult> getRecipientsAndSend(IdmIdentityDto guarantee, boolean sendBefore, 
			String fullName, IdmIdentityDto identity, String position, String ppvEnd) {
		List<IdmIdentityDto> recipients = getRecipients(guarantee, identity);
		if (recipients.isEmpty()) {
			return Optional.of(new OperationResult.Builder(OperationState.EXCEPTION).
					setModel(new DefaultResultModel(ExtrasResultCode.NO_RECIPIENTS_FOUND)).
					build()); 
		}
		
		if (notificationTopic != null) {
			sendNotification(notificationTopic, new ArrayList<>(recipients), guarantee, 
					fullName, identity, position, ppvEnd);
		} else if (sendBefore) {
			sendNotification(ExtrasModuleDescriptor.TOPIC_CONTRACT_END_IN_X_DAYS, new ArrayList<>(recipients), guarantee, 
				fullName, identity, position, ppvEnd);
		} else {
			sendNotification(ExtrasModuleDescriptor.TOPIC_CONTRACT_END, new ArrayList<>(recipients), guarantee, 
					fullName, identity, position, ppvEnd);
		}

		return Optional.of(new OperationResult.Builder(OperationState.EXECUTED).build());
	}

	protected void setDatesForTest(LocalDate currentDate, Long daysBeforeEnd) {
		this.currentDate = currentDate;
		this.daysBeforeEnd = daysBeforeEnd;
	}

	/**
	 * Overridden method. This ensures that if the contract's validity is extended, the contract will be removed from the processed queue.
	 * 
	 * @param dto
	 * @return
	 */
	@Override
	public boolean isInProcessedQueue(IdmIdentityContractDto dto) {
		if (!supportsQueue()) {
			return false;
		}
		Assert.notNull(dto, "DTO is required for LRT processing.");
		//
		Page<IdmProcessedTaskItemDto> p = getContractItemFromQueue(dto.getId());
		
		return p.getTotalElements() > 0;
	}
	
	/**
	 * Check whether the contract was extended - if so, remove it from the processed queue.
	 * 
	 * @param entityRef
	 * @return
	 */
	private Page<IdmProcessedTaskItemDto> getContractItemFromQueue(UUID entityRef) {
		if (this.getScheduledTaskId() == null) {
			return new PageImpl<>(Collections.emptyList());
		}
		IdmProcessedTaskItemFilter filter = new IdmProcessedTaskItemFilter();
		filter.setReferencedEntityId(entityRef);
		filter.setScheduledTaskId(this.getScheduledTaskId());
		Page<IdmProcessedTaskItemDto> items = itemService.find(filter, PageRequest.of(0, 1));
		if (items.getTotalElements() > 1) {
			LOG.warn("Multiple same item references found in [{}] process queue.", this.getClass());
		}
		for (IdmProcessedTaskItemDto item : items) {
			if (item.getOperationResult().getState() == OperationState.EXECUTED) {
				IdmIdentityContractDto contract = identityContractService.get(entityRef);
				LocalDate dateToSendNotification = contract.getValidTill().minusDays(Math.toIntExact(daysBeforeEnd));
				if (!currentDate.isAfter(dateToSendNotification.minusDays(1))) {
					// the contract was extended since the first run
					super.removeFromProcessedQueue(contract);
					return new PageImpl<>(Collections.emptyList());
				}
			}
		}

		return items;
	}
}
