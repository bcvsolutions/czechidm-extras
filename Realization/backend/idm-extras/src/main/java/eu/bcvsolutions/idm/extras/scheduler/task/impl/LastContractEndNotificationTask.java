package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.api.domain.OperationState;
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
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableStatefulExecutor;
import eu.bcvsolutions.idm.extras.ExtrasModuleDescriptor;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * This task sends a notification to those with a specified role and optionally the manager of the contract. The
 * notifications says that the user is leaving in a specified number of days, i. e., the user's last contract
 * ends.
 * 
 * @author Tomáš Doischer
 */
@Service
@Description("Task which will send notification X days before end of the last contract. It is recommended to"
		+ "run for the first time without sending emails.")

public class LastContractEndNotificationTask extends AbstractSchedulableStatefulExecutor<IdmIdentityContractDto> {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(LastContractEndNotificationTask.class);
	protected static final String PARAMETER_DAYS_BEFORE = "How many days before the end of the contract should the notification be sent";
	protected static final String RECIPIENT_ROLE_BEFORE_PARAM = "Recipient role of notification";
	protected static final String SEND_TO_MANAGER_BEFORE_PARAM = "Should the manager of the contract receive the notification?";
	
	private Long daysBeforeEnd;
	private LocalDate currentDate = new LocalDate();
	private LocalDate validMinusXDays = new LocalDate();
	private String fullName;
	private String position;
	private String ppvEnd;
	private UUID recipientRoleBefore = null;
	private Boolean sendToManagerBefore;
	
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

	@Override
	public void init(Map<String, Object> properties) {
		super.init(properties);

		daysBeforeEnd = getParameterConverter().toLong(properties, PARAMETER_DAYS_BEFORE);
		recipientRoleBefore = getParameterConverter().toUuid(properties, RECIPIENT_ROLE_BEFORE_PARAM);
		sendToManagerBefore = getParameterConverter().toBoolean(properties, SEND_TO_MANAGER_BEFORE_PARAM);
		if (daysBeforeEnd == null || daysBeforeEnd.compareTo(0L) <= -1) {
			throw new ResultCodeException(ExtrasResultCode.CONTRACT_END_NOTIFICATION_DAYS_BEFORE,
					ImmutableMap.of("daysBeforeEnd", daysBeforeEnd == null ? "null" : daysBeforeEnd));
		}
	}
	
	@Override
	public Map<String, Object> getProperties() {
		Map<String, Object> props = super.getProperties();
		props.put(PARAMETER_DAYS_BEFORE, daysBeforeEnd);
		props.put(RECIPIENT_ROLE_BEFORE_PARAM, recipientRoleBefore);
		props.put(SEND_TO_MANAGER_BEFORE_PARAM, sendToManagerBefore);
		return props;
	}
	
	@Override
	public Page<IdmIdentityContractDto> getItemsToProcess(Pageable pageable) {
		IdmIdentityContractFilter identityContractFilter = new IdmIdentityContractFilter();
		if (daysBeforeEnd == 0) {
			identityContractFilter.setValidTill(currentDate);
		} else {
			identityContractFilter.setValid(true);
		}
		return identityContractService.find(identityContractFilter, pageable);
	}
	
	@Override
	public Optional<OperationResult> processItem(IdmIdentityContractDto dto) {
		List<IdmIdentityDto> recipients;

		if (dto.getValidTill() == null) {
			// the contract has infinite validity, leave alone
			return Optional.of(new OperationResult.Builder(OperationState.NOT_EXECUTED).build());
		}

		validMinusXDays = dto.getValidTill().minusDays(Math.toIntExact(daysBeforeEnd));
		
		// this we are interested in contracts which end in x days or sooner 
		if (!currentDate.isAfter(validMinusXDays.minusDays(1))) {
			// the contract ends on different times than specified, leave alone
			return Optional.of(new OperationResult.Builder(OperationState.NOT_EXECUTED).build());
		}
		
		if (!isLastContract(dto)) {
			// the contract owner has more contracts valid in the future and is not leaving, leave alone
			return Optional.of(new OperationResult.Builder(OperationState.NOT_EXECUTED).build());
		}
		
		IdmIdentityDto identityDto = DtoUtils.getEmbedded(dto, IdmIdentityContract_.identity, IdmIdentityDto.class);
		fullName = identityDto.getFirstName() + " " + identityDto.getLastName() + " (" + 
				identityDto.getUsername() + ")";

		if (dto.getWorkPosition() != null) {
			position = DtoUtils.getEmbedded(dto, IdmIdentityContract_.workPosition, IdmTreeNodeDto.class).getName();
		}
		
		ppvEnd = dto.getValidTill().toString("dd. MM. YYYY");

		IdmIdentityDto guarantee = getManagerForContract(dto.getId(), identityDto.getId());
		if (guarantee == null) {
			LOG.info(String.format("ContractEndNotificationTask No manager for contract %s.", dto.getId()));
		}
		
		// end of contract today and daysBeforeEnd set to 0
		if (daysBeforeEnd == 0L && currentDate.isEqual(validMinusXDays)) {
			recipients = getRecipients(guarantee);
			sendNotification(ExtrasModuleDescriptor.TOPIC_CONTRACT_END, new ArrayList<>(recipients), guarantee);
			return Optional.of(new OperationResult.Builder(OperationState.EXECUTED).build());
		}

		// end of contract will be in daysBeforeEnd days
		if (daysBeforeEnd > 0L) {
			recipients = getRecipients(guarantee);
			sendNotification(ExtrasModuleDescriptor.TOPIC_CONTRACT_END_IN_X_DAYS, new ArrayList<>(recipients), guarantee);
			return Optional.of(new OperationResult.Builder(OperationState.EXECUTED).build());
		} else {
			return Optional.of(new OperationResult.Builder(OperationState.NOT_EXECUTED).build());
		}
	}
	
	private void sendNotification(String topic, List<IdmIdentityDto> recipients, IdmIdentityDto manager) {
		notificationManager.send(
				topic,
				new IdmMessageDto
						.Builder()
						.setLevel(NotificationLevel.SUCCESS)
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
	private List<IdmIdentityDto> getRecipients(IdmIdentityDto manager) {
		List<IdmIdentityDto> recipients = new ArrayList<>();

		if (sendToManagerBefore && manager != null) {
			recipients.add(manager);
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

	protected void setDatesForTest(LocalDate currentDate, Long daysBeforeEnd) {
		this.currentDate = currentDate;
		this.daysBeforeEnd = daysBeforeEnd;
	}
}
