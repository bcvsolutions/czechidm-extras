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
import eu.bcvsolutions.idm.core.eav.api.domain.BaseFaceType;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.dto.filter.IdmFormDefinitionFilter;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormDefinitionService;
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

import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;

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
	static final String PARAM_ROLES_ASSIGNED_CONTRACTS_TYPE = "Roles assignment contract type";
	static final String PARAM_MULTI_VALUE_SEPARATOR = "Multi value separator";
	static final String PARAM_IS_ROLE_MULTI_VALUE = "Is roles column multi value?";
	static final String PARAM_FILE_ENCODING = "File encoding type";
	static final String PARAM_CONTRACT_DEFINITION_CODE = "Contract definition";

	// Defaults
	private static final String COLUMN_SEPARATOR = ";";
	private static final String MULTI_VALUE_SEPARATOR = ","; //"\\r?\\n" new line separator
	private static final String FILE_ENCODING = "UTF-8";
	private static final String CONTRACT_DEFINITION = "default";

	private static final String OPTION_ITEM_ALL_CONTRACTS = "allContracts";
	private static final String OPTION_ITEM_PRIME_CONTRACT = "primeContract";
	private static final String OPTION_ITEM_EAV_CONTRACT = "eavContract";

	private UUID attachmentId;
	private String rolesColumnName;
	private String usernameColumnName;
	private String contractEavColumnName;
	private String contractEavName;
	private String columnSeparator;
	private String assignedContractType;
	private String multiValueSeparator;
	private Boolean isMultiValue;
	private String fileEncoding;
	private String contractDefinitionCode;

	@Autowired private IdmIdentityService identityService;
	@Autowired private IdmRoleService roleService;
	@Autowired private IdmIdentityRoleService identityRoleService;
	@Autowired private IdmRoleRequestService roleRequestService;
	@Autowired private IdmConceptRoleRequestService conceptRoleRequestService;
	@Autowired private IdmIdentityContractService identityContractService;
	@Autowired private FormService formService;
	@Autowired private IdmFormDefinitionService formDefinitionService;
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
			BufferedReader br = new BufferedReader(new InputStreamReader(attachmentData,fileEncoding));
			reader = new CSVReaderBuilder(br).withCSVParser(parser).build();
		} else {
			throw new ResultCodeException(ExtrasResultCode.EMPTY_ATTACHMENT_ID);
		}
		
		String[] header = reader.readNext();
		// find numbers of columns
		int usernameColumnNumber = findColumnNumber(header, usernameColumnName);
		int roleColumnNumber = findColumnNumber(header, rolesColumnName);

		for (String[] line : reader) {
			String username = line[usernameColumnNumber];
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
					List<IdmIdentityContractDto> contracts = identityContractService.findAllByIdentity(identityId);
					if (contracts.isEmpty()){
						continue;
					}
					if (assignedContractType.equals(OPTION_ITEM_EAV_CONTRACT)) {
						int contractEavColumnNumber = findColumnNumber(header, contractEavColumnName);
						String contractEav = line[contractEavColumnNumber];
						if (StringUtils.isEmpty(contractEav)){
							continue;
						}
						List<IdmIdentityContractDto> foundContracts = getContractsByEav(contracts, contractEav);
						if (foundContracts.isEmpty()) {
							continue;
						}
						contracts = foundContracts;
					} else if (assignedContractType.equals(OPTION_ITEM_ALL_CONTRACTS)){
						//TODO this could be default option - assign to all valid and future contracts
					} else if (assignedContractType.equals(OPTION_ITEM_PRIME_CONTRACT)){
						contracts = new ArrayList<IdmIdentityContractDto>();
						IdmIdentityContractDto primeContract = identityContractService.getPrimeContract(identityId);
						if (primeContract!=null){
							contracts.add(primeContract);
						}else{
							continue;
						}
					}else{
						//No choice - skip assignment
						//Break can be removed and default option will be: assign to all valid and future contracts
						break;
					}

					//add roles to contracts
					for (IdmIdentityContractDto contract : contracts) {
						if (contract != null) {
							if (!contract.isValidNowOrInFuture()){
								continue;
							}
							UUID contractId = contract.getId();
							if (!contractRoles.containsKey(contractId)) {
								contractRoles.put(contractId, new ArrayList<>());
							}
							System.out.println("roleName line:"+roleName +".");
							String[] roles = {roleName};
							if (isMultiValue){
								 roles = roleName.split(MULTI_VALUE_SEPARATOR);
							}
							for (String roleStr : roles){
								IdmRoleDto role = roleService.getByCode(roleStr);
								if (role != null) {
									contractRoles.get(contractId).add(role.getId());
								} else {
									nonExistingRolesCounter++;
									this.logItemProcessed(contract, taskNotCompleted("Role does not exist: " + roleName));
								}
							}
							if (contractRoles.get(contractId).isEmpty()) {
								contractRoles.remove(contractId);
							}
						}
					}
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
		//EAV contract definition
		if (!StringUtils.isEmpty(contractDefinitionCode)){
			IdmFormDefinitionDto definition = formDefinitionService.findOneByTypeAndCode(IdmIdentityContract.class.getName(),contractDefinitionCode);
			if (definition == null){
				throw new ResultCodeException(ExtrasResultCode.CONTRACT_EAV_NOT_FOUND, ImmutableMap.of("definition", definition));
			}
			return getOneValue(formService.getValues(contractId, IdmIdentityContractDto.class, definition, attributeCode).stream().findFirst());
		}
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
		assignedContractType = getParameterConverter().toString(properties, PARAM_ROLES_ASSIGNED_CONTRACTS_TYPE);
		multiValueSeparator = getParameterConverter().toString(properties, PARAM_MULTI_VALUE_SEPARATOR);
		isMultiValue = getParameterConverter().toBoolean(properties, PARAM_IS_ROLE_MULTI_VALUE);
		fileEncoding = getParameterConverter().toString(properties, PARAM_FILE_ENCODING);
		contractDefinitionCode = getParameterConverter().toString(properties, PARAM_CONTRACT_DEFINITION_CODE);

		if (isMultiValue == null || multiValueSeparator == null || StringUtils.isEmpty(multiValueSeparator)) {
			isMultiValue = Boolean.FALSE;
		}
		if (fileEncoding == null || StringUtils.isEmpty(fileEncoding)){
			fileEncoding = FILE_ENCODING;
		}
		if (contractDefinitionCode == null || StringUtils.isEmpty(contractDefinitionCode)){
			contractDefinitionCode = CONTRACT_DEFINITION;
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
		props.put(PARAM_ROLES_ASSIGNED_CONTRACTS_TYPE, assignedContractType);
		props.put(PARAM_MULTI_VALUE_SEPARATOR, multiValueSeparator);
		props.put(PARAM_IS_ROLE_MULTI_VALUE, isMultiValue);
		props.put(PARAM_FILE_ENCODING, fileEncoding);
		props.put(PARAM_CONTRACT_DEFINITION_CODE, contractDefinitionCode);
		return props;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		// csv file attachment
		List<IdmFormAttributeDto> attributes = new LinkedList<>();
		IdmFormAttributeDto csvAttachment = new IdmFormAttributeDto(PARAM_CSV_ATTACHMENT, PARAM_CSV_ATTACHMENT,
				PersistentType.ATTACHMENT);
		csvAttachment.setRequired(true);
		attributes.add(csvAttachment);

		IdmFormAttributeDto rolesColumnNameAttribute = new IdmFormAttributeDto(PARAM_ROLES_COLUMN_NAME, PARAM_ROLES_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		rolesColumnNameAttribute.setRequired(true);
		attributes.add(rolesColumnNameAttribute);

		IdmFormAttributeDto usernameColumnNameAttribute = new IdmFormAttributeDto(PARAM_USERNAME_COLUMN_NAME, PARAM_USERNAME_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		usernameColumnNameAttribute.setRequired(true);
		attributes.add(usernameColumnNameAttribute);

		IdmFormAttributeDto assignedContractTypeAttribute = new IdmFormAttributeDto(PARAM_ROLES_ASSIGNED_CONTRACTS_TYPE, PARAM_ROLES_ASSIGNED_CONTRACTS_TYPE,
				PersistentType.SHORTTEXT);
		assignedContractTypeAttribute.setDefaultValue(OPTION_ITEM_ALL_CONTRACTS);
		assignedContractTypeAttribute.setDescription("Napište jednu z následujích hodnot. "+OPTION_ITEM_ALL_CONTRACTS+", "+OPTION_ITEM_PRIME_CONTRACT+", "+OPTION_ITEM_EAV_CONTRACT+ ". \r\n\""+OPTION_ITEM_ALL_CONTRACTS+"\" přiřadí role na všechny validní a budoucí kontrakty. \"" +OPTION_ITEM_PRIME_CONTRACT+"\" přiřadí role na hlavní kontrakt. \""+OPTION_ITEM_EAV_CONTRACT+"\" přiřadí role na EAV kontraktu.");
		assignedContractTypeAttribute.setRequired(true);
		attributes.add(assignedContractTypeAttribute);

		IdmFormAttributeDto contractEavColumnNameAttribute = new IdmFormAttributeDto(PARAM_CONTRACT_EAV_COLUMN_NAME, PARAM_CONTRACT_EAV_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		contractEavColumnNameAttribute.setRequired(false);
		contractEavColumnNameAttribute.setDescription("Vyplňte s volbou. "+OPTION_ITEM_EAV_CONTRACT);
		attributes.add(contractEavColumnNameAttribute);

		IdmFormAttributeDto contractEavNameAttribute = new IdmFormAttributeDto(PARAM_CONTRACT_EAV_NAME, PARAM_CONTRACT_EAV_NAME,
				PersistentType.SHORTTEXT);
		contractEavNameAttribute.setRequired(false);
		contractEavNameAttribute.setDescription("Vyplňte s volbou. "+OPTION_ITEM_EAV_CONTRACT);
		attributes.add(contractEavNameAttribute);

		IdmFormAttributeDto columnSeparatorAttribute = new IdmFormAttributeDto(PARAM_COLUMN_SEPARATOR, PARAM_COLUMN_SEPARATOR,
				PersistentType.CHAR);
		columnSeparatorAttribute.setDefaultValue(COLUMN_SEPARATOR);
		columnSeparatorAttribute.setRequired(true);
		attributes.add(columnSeparatorAttribute);

		IdmFormAttributeDto multiValueSeparatorAttribute = new IdmFormAttributeDto(PARAM_MULTI_VALUE_SEPARATOR, PARAM_MULTI_VALUE_SEPARATOR,
				PersistentType.CHAR);
		multiValueSeparatorAttribute.setDefaultValue(MULTI_VALUE_SEPARATOR);
		multiValueSeparatorAttribute.setRequired(false);
		attributes.add(multiValueSeparatorAttribute);

		IdmFormAttributeDto isMultiValueAttribute = new IdmFormAttributeDto(PARAM_IS_ROLE_MULTI_VALUE,PARAM_IS_ROLE_MULTI_VALUE,
				PersistentType.BOOLEAN);
		attributes.add(isMultiValueAttribute);

		IdmFormAttributeDto fileEncodingAttribute = new IdmFormAttributeDto(PARAM_FILE_ENCODING, PARAM_FILE_ENCODING,
				PersistentType.SHORTTEXT);
		fileEncodingAttribute.setRequired(true);
		fileEncodingAttribute.setDefaultValue(FILE_ENCODING);
		attributes.add(fileEncodingAttribute);

		IdmFormAttributeDto contractDefinitionCodeAttribute = new IdmFormAttributeDto(PARAM_CONTRACT_DEFINITION_CODE, PARAM_CONTRACT_DEFINITION_CODE,
				PersistentType.SHORTTEXT);
		contractDefinitionCodeAttribute.setDefaultValue("default");
		contractDefinitionCodeAttribute.setDescription("Vyplňte s volbou. "+OPTION_ITEM_EAV_CONTRACT);
		contractDefinitionCodeAttribute.setRequired(false);

		attributes.add(contractDefinitionCodeAttribute);

		return attributes;
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

