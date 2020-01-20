package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestedByType;
import eu.bcvsolutions.idm.core.api.dto.*;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.*;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.ecm.api.service.AttachmentManager;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author Petr Hanák
 * 
 * This task reads username, role and optional contract eav attribute from the csv file and find contract with this eav to assign role.
 *  
 */
@Component
@Description("Parses input CSV (path as parameter) and assigns roles to user contracts. Only role assignment is allowed.")
public class ImportCSVUserContractRolesTaskExecutor extends AbstractSchedulableTaskExecutor<OperationResult> {

	private static final Logger LOG = LoggerFactory.getLogger(ImportCSVUserContractRolesTaskExecutor.class);

	static final String PARAM_CSV_ATTACHMENT = "Import csv file";
	static final String PARAM_ROLES_COLUMN_NAME = "Column with roles";
	static final String PARAM_USERNAME_COLUMN_NAME = "Column with username";
	static final String PARAM_CONTRACT_EAV_COLUMN_NAME = "Column with contract eav";
	static final String PARAM_CONTRACT_EAV_NAME = "Name of contract eav attribute";
	static final String PARAM_COLUMN_SEPARATOR = "Column separator";

	// Defaults
	private static final String COLUMN_SEPARATOR = ";";
	private static final String MULTI_VALUE_SEPARATOR = "\\r?\\n"; // new line separator

	private UUID attachmentId;
	private String rolesColumnName;
	private String usernameColumnName;
	private String contractEavColumnName;
	private String contractEavName;
	private String columnSeparator;
	private String multiValueSeparator;

	@Autowired private IdmIdentityService identityService;
	@Autowired private IdmRoleService roleService;
	@Autowired private IdmIdentityRoleService identityRoleService;
	@Autowired private IdmRoleRequestService roleRequestService;
	@Autowired private IdmConceptRoleRequestService conceptRoleRequestService;
	@Autowired private IdmIdentityContractService identityContractService;
	@Autowired private FormService formService;
	@Autowired private AttachmentManager attachmentManager;
	
	private int nonExistingRolesCounter;

	@Override
	public OperationResult process() {
		//
		Map<UUID, List<UUID>> contractRoles = parseCSV();
		//
		this.count = (long) contractRoles.size() + nonExistingRolesCounter;
		this.counter = 0L;
		
		for (Entry<UUID, List<UUID>> contractEntry : contractRoles.entrySet()) {
			addRolesToContract(contractEntry.getKey(), contractEntry.getValue());
			++this.counter;
			if (!this.updateState()) {
				break;
			}
		}
		return new OperationResult.Builder(OperationState.CREATED).build();
	}
	
	private Map<UUID, List<UUID>> parseCSV() {
		try {
		Map<UUID, List<UUID>> contractRoles = new TreeMap<>();
		// Parse CSV
		CSVParser parser = new CSVParserBuilder().withEscapeChar(CSVParser.DEFAULT_ESCAPE_CHARACTER).withQuoteChar('"')
				.withSeparator(columnSeparator.charAt(0)).build();
		CSVReader reader = null;
		// Check attachment id
		if (attachmentId != null) {
			InputStream attachmentData = attachmentManager.getAttachmentData(attachmentId);
			BufferedReader br = new BufferedReader(new InputStreamReader(attachmentData));
			reader = new CSVReaderBuilder(br).withCSVParser(parser).build();
		} else {
			throw new ResultCodeException(ExtrasResultCode.EMPTY_ATTACHMENT_ID);
		}
		
		String[] header = reader.readNext();
		// find numbers of columns
		int usernameColumnNumber = findColumnNumber(header, usernameColumnName);
		int roleColumnNumber = findColumnNumber(header, rolesColumnName);
		int contractEavColumnNumber = findColumnNumber(header, contractEavColumnName);
		
		for (String[] line : reader) {
			String username = line[usernameColumnNumber];
			String contractEav = line[contractEavColumnNumber];
			String roleName = line[roleColumnNumber];
			
			if (!StringUtils.isEmpty(roleName) && !StringUtils.isEmpty(username)) {
				IdmIdentityDto identity = identityService.getByUsername(username);
				UUID identityId;
				if (identity != null) {
					identityId = identity.getId();
				} else {
					continue;
				}
				if (identityId != null) {
					List<IdmIdentityContractDto> contracts = identityContractService.findAllValidForDate(identityId, LocalDate.now(), null);
					if (!contractEav.isEmpty()) {
						List<IdmIdentityContractDto> foundContracts = getContractsByEav(contracts, contractEav);
						if (!foundContracts.isEmpty()) {
							for (IdmIdentityContractDto contract : foundContracts) {
								if (contract != null) {
									UUID contractId = contract.getId();
									if (!contractRoles.containsKey(contractId)) {
										contractRoles.put(contractId, new ArrayList<>());
									}
									IdmRoleDto role = roleService.getByCode(roleName);
									if (role != null) {
										contractRoles.get(contractId).add(role.getId());
									} else {
										nonExistingRolesCounter++;
										this.logItemProcessed(contract, taskNotCompleted("Role does not exist: " + roleName));
										if (contractRoles.get(contractId).isEmpty()) {
											contractRoles.remove(contractId);
										}
									}
								}
							}
						}
					} else {
						for (IdmIdentityContractDto contract : contracts) {
							UUID contractId = contract.getId();
							if (!contractRoles.containsKey(contractId)) {
								contractRoles.put(contractId, new ArrayList<>());
							}
							contractRoles.get(contractId).add(roleService.getByCode(roleName).getId());
						}
					}
				} else {
					continue;
				}
			}
		}
		return contractRoles;
		
		} catch (IOException e) {
			IdmAttachmentDto attachment = attachmentManager.get(attachmentId);
			LOG.error("An error occurred while reading input CSV file [{}], error: [{}].", attachment.getName(), e);
			// TODO change to reading error
			throw new ResultCodeException(CoreResultCode.NOT_FOUND, "File '" + attachment.getName() + "' not found.", e);
		}
	}
	
	private List<IdmIdentityContractDto> getContractsByEav(List<IdmIdentityContractDto> contracts, String contractEavCsvValue) {
		List<IdmIdentityContractDto> foundContracts = new ArrayList<IdmIdentityContractDto>();
		for (IdmIdentityContractDto contract : contracts) {
			// get contract eav from contract
			Object eavValue = getEavValueForContract(contract.getId(), contractEavName);
			if (eavValue != null) {
				String contractEavValue = eavValue.toString();
				if (contractEavValue != null && contractEavValue.equals(contractEavCsvValue)) {
					foundContracts.add(contract);
				}
			}
		}
		System.out.println(foundContracts);
		return foundContracts;
	}
	
	private void addRolesToContract(UUID contractId, List<UUID> roleIds) {
		// TODO log assigned roles to contract / no roles to assign
		IdmIdentityContractDto contract = identityContractService.get(contractId);
		UUID identityRoleId = null;
		ConceptRoleRequestOperation operation = ConceptRoleRequestOperation.ADD;

		IdmRoleRequestDto roleRequest = new IdmRoleRequestDto();
		roleRequest.setApplicant(contract.getIdentity());
		roleRequest.setRequestedByType(RoleRequestedByType.MANUALLY);
		roleRequest.setExecuteImmediately(true);
		roleRequest = roleRequestService.save(roleRequest);
		
		Iterator<UUID> i = roleIds.iterator();
		while (i.hasNext()) {
			UUID roleId = i.next();
			boolean roleAssigned = false;
			IdmIdentityRoleFilter filter = new IdmIdentityRoleFilter();
			filter.setIdentityContractId(contractId);
			List<IdmIdentityRoleDto> result = identityRoleService.find(filter, null).getContent();
			if (result.size() > 0) {
				for (IdmIdentityRoleDto identityRole : result) {
					if (identityRole.getRole().equals(roleId)) {
						roleAssigned = true;
						i.remove();
						++this.count;
						this.logItemProcessed(contract, taskNotExecuted("Role is already assigned: " + roleService.get(roleId).getCode()));
						break;
					}
				}
			}
			
			if (!roleAssigned) {
				IdmConceptRoleRequestDto conceptRoleRequest = new IdmConceptRoleRequestDto();
				conceptRoleRequest.setRoleRequest(roleRequest.getId());
				conceptRoleRequest.setIdentityContract(contract.getId());
				conceptRoleRequest.setValidFrom(null);
				conceptRoleRequest.setIdentityRole(identityRoleId);
				conceptRoleRequest.setValidTill(null);
				conceptRoleRequest.setRole(roleId);
				conceptRoleRequest.setOperation(operation);
				conceptRoleRequestService.save(conceptRoleRequest);
			}
		}
		
		roleRequestService.startRequestInternal(roleRequest.getId(), true);
		
		if (!roleIds.isEmpty()) {
			this.logItemProcessed(contract, taskCompleted("Assigned roles: " + getAssignedRolesToString(roleIds)));
		} else {
			--this.count;
		}
	}

	private String getAssignedRolesToString(List<UUID> roleIds) {
		List<IdmRoleDto> assignedRoleDtos = new ArrayList<>((int) (roleIds.size() / 0.75));
		for (UUID roleId : roleIds) {
			assignedRoleDtos.add(roleService.get(roleId));
		}
		List<String> assignedRolesList = new ArrayList<>((int) (assignedRoleDtos.size() / 0.75));
		for (IdmRoleDto role : assignedRoleDtos) {
			assignedRolesList.add(role.getCode());
		}
		return String.join(", ", assignedRolesList);
	}
	
	/**
	 * Get eav value for contract
	 *
	 * @param contractId
	 * @param attributeCode
	 * @return
	 */
	public Object getEavValueForContract(UUID contractId, String attributeCode) {
		return getOneValue(formService.getValues(contractId, IdmIdentityContractDto.class, attributeCode).stream().findFirst());
	}
	
	/**
	 * Returns one value if there is any
	 * 
	 * @param value
	 * @return
	 */
	public static Object getOneValue(Optional<IdmFormValueDto> value) {
		if (value.isPresent()) {
			return value.get().getValue();
		}
		return null;
	}
	
	/**
	 * finds number of column
	 * 
	 * @param header
	 * @param columnName
	 * @return
	 */
	private int findColumnNumber(String[] header, String columnName) {
		int counterHeader = 0;
		for (String item : header){
			if(item.equals(columnName)){
				return counterHeader;
			}
			counterHeader++;
		}
		throw new ResultCodeException(ExtrasResultCode.COLUMN_NOT_FOUND, ImmutableMap.of("column name", columnName));
	}

	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		attachmentId = getParameterConverter().toUuid(properties, PARAM_CSV_ATTACHMENT);
		rolesColumnName = getParameterConverter().toString(properties, PARAM_ROLES_COLUMN_NAME);
		usernameColumnName = getParameterConverter().toString(properties, PARAM_USERNAME_COLUMN_NAME);
		contractEavColumnName = getParameterConverter().toString(properties, PARAM_CONTRACT_EAV_COLUMN_NAME);
		contractEavName = getParameterConverter().toString(properties, PARAM_CONTRACT_EAV_NAME);
		columnSeparator = getParameterConverter().toString(properties, PARAM_COLUMN_SEPARATOR);
		// if not filled, init multiValueSeparator and check if csv has description
		if (multiValueSeparator == null) {
			multiValueSeparator = MULTI_VALUE_SEPARATOR;
		}
	}

	@Override
	public Map<String, Object> getProperties() {
		LOG.debug("Start getProperties");
		Map<String, Object> props = super.getProperties();
		props.put(PARAM_CSV_ATTACHMENT, attachmentId);
		props.put(PARAM_ROLES_COLUMN_NAME, rolesColumnName);
		props.put(PARAM_USERNAME_COLUMN_NAME, usernameColumnName);
		props.put(PARAM_CONTRACT_EAV_COLUMN_NAME, contractEavColumnName);
		props.put(PARAM_CONTRACT_EAV_NAME, contractEavName);
		props.put(PARAM_COLUMN_SEPARATOR, columnSeparator);
		return props;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		// csv file attachment
		IdmFormAttributeDto csvAttachment = new IdmFormAttributeDto(PARAM_CSV_ATTACHMENT, PARAM_CSV_ATTACHMENT,
				PersistentType.ATTACHMENT);
		csvAttachment.setRequired(true);
		IdmFormAttributeDto rolesColumnNameAttribute = new IdmFormAttributeDto(PARAM_ROLES_COLUMN_NAME, PARAM_ROLES_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		rolesColumnNameAttribute.setRequired(true);
		IdmFormAttributeDto usernameColumnNameAttribute = new IdmFormAttributeDto(PARAM_USERNAME_COLUMN_NAME, PARAM_USERNAME_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		usernameColumnNameAttribute.setRequired(true);
		IdmFormAttributeDto contractEavColumnNameAttribute = new IdmFormAttributeDto(PARAM_CONTRACT_EAV_COLUMN_NAME, PARAM_CONTRACT_EAV_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		contractEavColumnNameAttribute.setRequired(true);
		IdmFormAttributeDto contractEavNameAttribute = new IdmFormAttributeDto(PARAM_CONTRACT_EAV_NAME, PARAM_CONTRACT_EAV_NAME,
				PersistentType.SHORTTEXT);
		contractEavNameAttribute.setRequired(true);
		IdmFormAttributeDto columnSeparatorAttribute = new IdmFormAttributeDto(PARAM_COLUMN_SEPARATOR, PARAM_COLUMN_SEPARATOR,
				PersistentType.CHAR);
		columnSeparatorAttribute.setDefaultValue(COLUMN_SEPARATOR);
		columnSeparatorAttribute.setRequired(true);
		//
		return Lists.newArrayList(csvAttachment, rolesColumnNameAttribute, usernameColumnNameAttribute, contractEavColumnNameAttribute, contractEavNameAttribute, columnSeparatorAttribute);
	}
	
	private OperationResult taskCompleted(String message) {
		return new OperationResult.Builder(OperationState.EXECUTED).setModel(new DefaultResultModel(ExtrasResultCode.TEST_ITEM_COMPLETED,
				ImmutableMap.of("message", message))).build();
	}
	
	private OperationResult taskNotExecuted(String message) {
		return new OperationResult.Builder(OperationState.NOT_EXECUTED).setModel(new DefaultResultModel(ExtrasResultCode.TEST_ITEM_COMPLETED,
				ImmutableMap.of("message", message))).build();
	}
	
	private OperationResult taskNotCompleted(String message) {
		return new OperationResult.Builder(OperationState.EXCEPTION).setModel(new DefaultResultModel(ExtrasResultCode.TEST_ITEM_COMPLETED,
				ImmutableMap.of("message", message))).build();
	}
}

