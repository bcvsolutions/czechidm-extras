package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.exception.InvalidFormException;
import eu.bcvsolutions.idm.core.eav.api.dto.*;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestedByType;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.IdmConceptRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormDefinitionService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;
import eu.bcvsolutions.idm.extras.utils.Pair;

/**
 * @author Petr Hanák
 * 
 * This task reads username, role and optional contract eav attribute from the csv file and find contract with this eav to assign role.
 *  
 */
@Component(ImportCSVUserContractRolesTaskExecutor.TASK_NAME)
@Description("Parses input CSV (path as parameter) and assigns roles to user contracts. Only role assignment is allowed.")
public class ImportCSVUserContractRolesTaskExecutor extends AbstractCsvImportTask {
	public static final String TASK_NAME = "extras-import-assinged-roles";
	
	private static final Logger LOG = LoggerFactory.getLogger(ImportCSVUserContractRolesTaskExecutor.class);

	static final String PARAM_ROLES_COLUMN_NAME = "rolecolumn";
	static final String PARAM_USERNAME_COLUMN_NAME = "usernamecolumn";
	static final String PARAM_ROLES_ASSIGNED_CONTRACTS_TYPE = "rolesassignment";
	static final String PARAM_MULTI_VALUE_SEPARATOR = "rolemultivalueseparator";
	static final String PARAM_IS_ROLE_MULTI_VALUE = "isrolecolumnmultivalued";
	static final String PARAM_CONTRACT_DEFINITION_CODE = "contractdefinition";
	public static final String PARAM_CONTRACT_EAV_ATTR_NAME_PREFIX = "contracteavcodeprefix";
	public static final String PARAM_CONTRACT_EAV_ATTR_VALUE_PREFIX = "contracteavvalueprefix";
	public static final String PARAM_ROLE_ATTRIBUTE_ATTR_NAME_PREFIX = "roleattributenameprefix";
	public static final String PARAM_ROLE_ATTRIBUTE_ATTR_VALUE_PREFIX = "roleattributevalueprefix";


	// Defaults
	private static final String CONTRACT_DEFINITION = "default";
	private static final String MULTI_VALUE_SEPARATOR_DEFAULT = ",";

	private static final String OPTION_ITEM_ALL_CONTRACTS = "allContracts";
	private static final String OPTION_ITEM_PRIME_CONTRACT = "primeContract";
	private static final String OPTION_ITEM_EAV_CONTRACT = "eavContract";

	private String rolesColumnName;
	private String usernameColumnName;
	private String assignedContractType;
	private String multiValueSeparator;
	private Boolean isMultiValue;
	private String contractDefinitionCode;
	private String contractEavAttributeNamePrefix;
	private String contractEavAttributeValuePrefix;
	private String roleAttrAttributeNamePrefix;
	private String roleAttrAttributeValuePrefix;

	@Autowired private IdmIdentityService identityService;
	@Autowired private IdmRoleService roleService;
	@Autowired private IdmIdentityRoleService identityRoleService;
	@Autowired private IdmRoleRequestService roleRequestService;
	@Autowired private IdmConceptRoleRequestService conceptRoleRequestService;
	@Autowired private IdmIdentityContractService identityContractService;
	@Autowired private FormService formService;
	@Autowired private IdmFormDefinitionService formDefinitionService;

	private int nonExistingRolesCounter;

	@Override
	public String getName() {
		return TASK_NAME;
	}
	
	@Override
	protected void processRecords(List<CSVRecord> records) {
		Map<UUID, List<IdmIdentityRoleDto>> contractRoles = handleRecords(records);
		//
		this.count = (long) contractRoles.size() + nonExistingRolesCounter;
		this.counter = 0L;
		
		for (Entry<UUID, List<IdmIdentityRoleDto>> contractEntry : contractRoles.entrySet()) {
			addRolesToContract(contractEntry.getKey(), contractEntry.getValue());
			++this.counter;
			if (!this.updateState()) {
				break;
			}
		}
	}

	@Override
	protected void processOneDynamicAttribute(String namePrefix, String name, String valuePrefix, String value,
			boolean isEav) {
		throw new UnsupportedOperationException("No dynamic attributes present");
	}
	
	private Map<UUID, List<IdmIdentityRoleDto>> handleRecords(List<CSVRecord> records) {
		Map<UUID, List<IdmIdentityRoleDto>> contractRoles = new TreeMap<>();
		
		for(CSVRecord record : records) {
			String username = record.get(usernameColumnName);
			String roleCode = record.get(rolesColumnName);
			List<Pair<String, String>> roleAttributes = processDynamicAttribute(record, roleAttrAttributeNamePrefix, roleAttrAttributeValuePrefix);

			if (!StringUtils.isEmpty(roleCode) && !StringUtils.isEmpty(username)) {
				IdmIdentityDto identity = identityService.getByUsername(username);
				
				if (identity != null) {
					List<IdmIdentityContractDto> contractsToAssign = getContracts(identity, record);
					//add roles to contracts
					contractRoles = getRolesToAssign(contractRoles, contractsToAssign, roleCode, roleAttributes);
				} 
			}
		}
		
		return contractRoles;
	}
	
	private List<IdmIdentityContractDto> getContracts(IdmIdentityDto identity, CSVRecord record) {
		UUID identityId = identity.getId();
		
		List<IdmIdentityContractDto> contractsToAssign = new ArrayList<>();
		List<IdmIdentityContractDto> contracts = identityContractService.findAllByIdentity(identityId);
		
		if (!contracts.isEmpty()){
			if (assignedContractType.equals(OPTION_ITEM_EAV_CONTRACT)) {
				contractsToAssign = getContractsByEav(contracts, record);
			} else if (assignedContractType.equals(OPTION_ITEM_ALL_CONTRACTS)){
				contractsToAssign = contracts;
			} else if (assignedContractType.equals(OPTION_ITEM_PRIME_CONTRACT)){
				IdmIdentityContractDto primeContract = identityContractService.getPrimeContract(identityId);
				if (primeContract != null){
					contractsToAssign.add(primeContract);
				}
			} else {
				//No choice - skip assignment
			}
		}
		
		return contractsToAssign;
	}
	
	private Map<UUID, List<IdmIdentityRoleDto>> getRolesToAssign(Map<UUID, List<IdmIdentityRoleDto>> contractRoles, List<IdmIdentityContractDto> contractsToAssign, String roleCode, List<Pair<String, String>> roleAttributes) {
		for (IdmIdentityContractDto contract : contractsToAssign) {
			if (contract != null) {
				if (!contract.isValidNowOrInFuture()){
					continue;
				}
				UUID contractId = contract.getId();
				if (!contractRoles.containsKey(contractId)) {
					contractRoles.put(contractId, new ArrayList<>());
				}
				String[] roles = {roleCode};
				if (isMultiValue){
					 roles = roleCode.split(multiValueSeparator);
				}
				for (String roleStr : roles) {
					IdmRoleDto role = roleService.getByCode(roleStr);
					if (role != null) {
						contractRoles.get(contractId).add(createEmptyIdentityRoleWithEavs(role, contractId, roleAttributes));
					} else {
						nonExistingRolesCounter++;
						this.logItemProcessed(contract, taskNotCompleted("Role does not exist: " + roleCode));
					}
				}
				if (contractRoles.get(contractId).isEmpty()) {
					contractRoles.remove(contractId);
				}
			}
		}
		
		return contractRoles;
	}

	private IdmIdentityRoleDto createEmptyIdentityRoleWithEavs(IdmRoleDto role, UUID contractId, List<Pair<String, String>> roleAttributes) {
		final IdmIdentityRoleDto result = new IdmIdentityRoleDto();
		//
		result.setRole(role.getId());
		result.setIdentityContract(contractId);
		//
		if (role.getIdentityRoleAttributeDefinition() != null) {
			ArrayList<IdmFormInstanceDto> formInstanceList = processRoleAttributes(role, roleAttributes);
			result.setEavs(formInstanceList);
		}
		//
		return result;
	}

	private ArrayList<IdmFormInstanceDto> processRoleAttributes(IdmRoleDto role, List<Pair<String, String>> roleAttributes) {
		final IdmFormInstanceDto formInstance = formService.getFormInstance(role, formDefinitionService.get(role.getIdentityRoleAttributeDefinition()));
		roleAttributes.forEach(attrPair -> {
			IdmFormAttributeDto mappedAttributeByCode = formInstance.getMappedAttributeByCode(attrPair.getFirst());
			List<IdmFormValueDto> allPresentValues = formInstance.getValues().stream()
					.filter(fv -> fv.getFormAttribute().equals(mappedAttributeByCode.getId()))
					.collect(Collectors.toList());
			boolean alreadyContainsValue = allPresentValues.stream().anyMatch(fv -> attrPair.getSecond().equals(fv.getShortTextValue()));
			//
			if ((!alreadyContainsValue && mappedAttributeByCode.isMultiple()) || allPresentValues.isEmpty()) {
				IdmFormValueDto newFormValue = getIdmFormValueDto(attrPair, mappedAttributeByCode);
				List<IdmFormValueDto> currentValues = new ArrayList<>(formInstance.getValues());
				currentValues.add(newFormValue);
				formInstance.setValues(currentValues);
			}
		});
		final ArrayList<IdmFormInstanceDto> formInstanceList = new ArrayList<>();
		formInstanceList.add(formInstance);
		return formInstanceList;
	}

	private IdmFormValueDto getIdmFormValueDto(Pair<String, String> attrPair, IdmFormAttributeDto mappedAttributeByCode) {
		IdmFormValueDto newFormValue = new IdmFormValueDto();
		newFormValue.setShortTextValue(attrPair.getSecond());
		newFormValue.setFormAttribute(mappedAttributeByCode.getId());
		newFormValue.setPersistentType(PersistentType.SHORTTEXT);
		return newFormValue;
	}

	private List<IdmIdentityContractDto> getContractsByEav(List<IdmIdentityContractDto> contracts, CSVRecord record) {
		List<IdmIdentityContractDto> foundContracts = new ArrayList<>();
		List<Pair<String, String>> eavCodesAndValues = getEavs(record);
		for (IdmIdentityContractDto contract : contracts) {
			// get contract eavs from contract
			int existingEavsSize = 0;
			for (Pair<String, String> eavCodeAndValue : eavCodesAndValues) {
				Object eavValue = getEavValueForContract(contract.getId(), eavCodeAndValue.getFirst());
				if (eavValue != null) {
					String contractEavValue = eavValue.toString();
					if (contractEavValue != null && contractEavValue.equals(eavCodeAndValue.getSecond())) {
						existingEavsSize++;
					}
				}
			}
			
			if (!eavCodesAndValues.isEmpty() && eavCodesAndValues.size() == existingEavsSize) {
				foundContracts.add(contract);
			}
		}
		return foundContracts;
	}
	
	private List<Pair<String, String>> getEavs(CSVRecord record) {
		return processDynamicAttribute(record, contractEavAttributeNamePrefix, contractEavAttributeValuePrefix);
	}
	
	private void addRolesToContract(UUID contractId, List<IdmIdentityRoleDto> roleIds) {
		// TODO log assigned roles to contract / no roles to assign
		IdmIdentityContractDto contract = identityContractService.get(contractId);
		UUID identityRoleId = null;
		ConceptRoleRequestOperation operation = ConceptRoleRequestOperation.ADD;

		IdmRoleRequestDto roleRequest = new IdmRoleRequestDto();
		roleRequest.setApplicant(contract.getIdentity());
		roleRequest.setRequestedByType(RoleRequestedByType.MANUALLY);
		roleRequest.setExecuteImmediately(true);
		roleRequest = roleRequestService.save(roleRequest);
		
		Iterator<IdmIdentityRoleDto> i = roleIds.iterator();
		while (i.hasNext()) {
			IdmIdentityRoleDto ir = i.next();
			boolean roleAssigned = false;
			IdmIdentityRoleFilter filter = new IdmIdentityRoleFilter();
			filter.setIdentityContractId(contractId);
			List<IdmIdentityRoleDto> result = identityRoleService.find(filter, null).getContent();
			if (!result.isEmpty()) {
				for (IdmIdentityRoleDto identityRole : result) {
					if (identityRole.getRole().equals(ir.getRole())) {
						roleAssigned = true;
						i.remove();
						++this.count;
						this.logItemProcessed(contract, taskNotExecuted("Role is already assigned: " + roleService.get(ir.getRole()).getCode()));
						break;
					}
				}
			}
			
			if (!roleAssigned) {
				createConcept(contract, identityRoleId, operation, roleRequest, ir);
			}
		}
		/*
		 There is a possibility of creating non unique values of role attributes despite
		 them having set unique value requirement. This is due to the asynchronous nature of role requests.
		 Note that starting request with immediate priority slows import significantly, but as far as i know
		 it is currently the only way to prevent such unwanted behavior. Nevertheless i opted for leaving
		 the issue open since it should not occur very often and i put a note in documentation describing
		 this behavior and how to work around it.
		 TODO: patch this in future versions
		*/
		roleRequestService.startRequestInternal(roleRequest.getId(), true);
		if (!roleIds.isEmpty()) {
			this.logItemProcessed(contract, taskCompleted("Assigned roles: " + getAssignedRolesToString(roleIds)));
		} else {
			--this.count;
		}
	}

	private void createConcept(IdmIdentityContractDto contract, UUID identityRoleId, ConceptRoleRequestOperation operation, IdmRoleRequestDto roleRequest, IdmIdentityRoleDto ir) {
		IdmConceptRoleRequestDto conceptRoleRequest = new IdmConceptRoleRequestDto();
		conceptRoleRequest.setRoleRequest(roleRequest.getId());
		conceptRoleRequest.setIdentityContract(contract.getId());
		conceptRoleRequest.setValidFrom(null);
		conceptRoleRequest.setIdentityRole(identityRoleId);
		conceptRoleRequest.setValidTill(null);
		conceptRoleRequest.setRole(ir.getRole());
		conceptRoleRequest.setOperation(operation);
		conceptRoleRequest.setEavs(ir.getEavs());
		//
		try {
			conceptRoleRequestService.save(conceptRoleRequest);
		} catch (InvalidFormException ex) {
			// Exception could be thrown here in case some of the role attributes do not pass form validation. Not much to
			// do here but to log more information and rethrow the exception.
			throw new ResultCodeException(
					CoreResultCode.FORM_INVALID,
					String.format("Error importing record role %s for contract %s of identity %s. Role attributes did not pass form validation",
							ir.getRole(), contract.getId(), contract.getIdentity()), ex);
		}
	}

	private String getAssignedRolesToString(List<IdmIdentityRoleDto> identityRoles) {
		List<IdmRoleDto> assignedRoleDtos = new ArrayList<>((int) (identityRoles.size() / 0.75));
		for (IdmIdentityRoleDto role : identityRoles) {
			assignedRoleDtos.add(roleService.get(role.getRole()));
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
				throw new ResultCodeException(ExtrasResultCode.CONTRACT_EAV_NOT_FOUND, ImmutableMap.of());
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

	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		rolesColumnName = getParameterConverter().toString(properties, PARAM_ROLES_COLUMN_NAME);
		usernameColumnName = getParameterConverter().toString(properties, PARAM_USERNAME_COLUMN_NAME);
		contractEavAttributeNamePrefix = getParameterConverter().toString(properties, PARAM_CONTRACT_EAV_ATTR_NAME_PREFIX);
		contractEavAttributeValuePrefix = getParameterConverter().toString(properties, PARAM_CONTRACT_EAV_ATTR_VALUE_PREFIX);
		roleAttrAttributeNamePrefix = getParameterConverter().toString(properties, PARAM_ROLE_ATTRIBUTE_ATTR_NAME_PREFIX);
		roleAttrAttributeValuePrefix = getParameterConverter().toString(properties, PARAM_ROLE_ATTRIBUTE_ATTR_VALUE_PREFIX);
		assignedContractType = getParameterConverter().toString(properties, PARAM_ROLES_ASSIGNED_CONTRACTS_TYPE);
		multiValueSeparator = getParameterConverter().toString(properties, PARAM_MULTI_VALUE_SEPARATOR);
		isMultiValue = getParameterConverter().toBoolean(properties, PARAM_IS_ROLE_MULTI_VALUE);
		contractDefinitionCode = getParameterConverter().toString(properties, PARAM_CONTRACT_DEFINITION_CODE);

		if (isMultiValue == null || multiValueSeparator == null || StringUtils.isEmpty(multiValueSeparator)) {
			isMultiValue = Boolean.FALSE;
		}
		if (contractDefinitionCode == null || StringUtils.isEmpty(contractDefinitionCode)){
			contractDefinitionCode = CONTRACT_DEFINITION;
		}


	}

	@Override
	public Map<String, Object> getProperties() {
		LOG.debug("Start getProperties");
		Map<String, Object> props = super.getProperties();
		props.put(PARAM_ROLES_COLUMN_NAME, rolesColumnName);
		props.put(PARAM_USERNAME_COLUMN_NAME, usernameColumnName);
		props.put(PARAM_CONTRACT_EAV_ATTR_NAME_PREFIX, contractEavAttributeNamePrefix);
		props.put(PARAM_CONTRACT_EAV_ATTR_VALUE_PREFIX, contractEavAttributeValuePrefix);
		props.put(PARAM_ROLE_ATTRIBUTE_ATTR_NAME_PREFIX, roleAttrAttributeNamePrefix);
		props.put(PARAM_ROLE_ATTRIBUTE_ATTR_VALUE_PREFIX, roleAttrAttributeValuePrefix);
		props.put(PARAM_ROLES_ASSIGNED_CONTRACTS_TYPE, assignedContractType);
		props.put(PARAM_MULTI_VALUE_SEPARATOR, multiValueSeparator);
		props.put(PARAM_IS_ROLE_MULTI_VALUE, isMultiValue);
		props.put(PARAM_CONTRACT_DEFINITION_CODE, contractDefinitionCode);
		return props;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {	
		List<IdmFormAttributeDto> attributes = super.getFormAttributes();

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
		assignedContractTypeAttribute.setRequired(true);
		attributes.add(assignedContractTypeAttribute);

		IdmFormAttributeDto contractEavAttrNameAttribute = new IdmFormAttributeDto(PARAM_CONTRACT_EAV_ATTR_NAME_PREFIX, PARAM_CONTRACT_EAV_ATTR_NAME_PREFIX,
				PersistentType.SHORTTEXT);
		contractEavAttrNameAttribute.setRequired(false);
		
		attributes.add(contractEavAttrNameAttribute);

		IdmFormAttributeDto contractEavAttrValueAttribute = new IdmFormAttributeDto(PARAM_CONTRACT_EAV_ATTR_VALUE_PREFIX, PARAM_CONTRACT_EAV_ATTR_VALUE_PREFIX,
				PersistentType.SHORTTEXT);
		contractEavAttrValueAttribute.setRequired(false);
		
		attributes.add(contractEavAttrValueAttribute);

		IdmFormAttributeDto roleAttrNameAttribute = new IdmFormAttributeDto(PARAM_ROLE_ATTRIBUTE_ATTR_NAME_PREFIX, PARAM_ROLE_ATTRIBUTE_ATTR_NAME_PREFIX,
				PersistentType.SHORTTEXT);
		roleAttrNameAttribute.setRequired(false);

		attributes.add(roleAttrNameAttribute);

		IdmFormAttributeDto roleAttrValueAttribute = new IdmFormAttributeDto(PARAM_ROLE_ATTRIBUTE_ATTR_VALUE_PREFIX, PARAM_ROLE_ATTRIBUTE_ATTR_VALUE_PREFIX,
				PersistentType.SHORTTEXT);
		roleAttrValueAttribute.setRequired(false);

		attributes.add(roleAttrValueAttribute);

		IdmFormAttributeDto multiValueSeparatorAttribute = new IdmFormAttributeDto(PARAM_MULTI_VALUE_SEPARATOR, PARAM_MULTI_VALUE_SEPARATOR,
				PersistentType.CHAR);
		multiValueSeparatorAttribute.setDefaultValue(MULTI_VALUE_SEPARATOR_DEFAULT);
		multiValueSeparatorAttribute.setRequired(false);
		attributes.add(multiValueSeparatorAttribute);

		IdmFormAttributeDto isMultiValueAttribute = new IdmFormAttributeDto(PARAM_IS_ROLE_MULTI_VALUE,PARAM_IS_ROLE_MULTI_VALUE,
				PersistentType.BOOLEAN);
		attributes.add(isMultiValueAttribute);

		IdmFormAttributeDto contractDefinitionCodeAttribute = new IdmFormAttributeDto(PARAM_CONTRACT_DEFINITION_CODE, PARAM_CONTRACT_DEFINITION_CODE,
				PersistentType.SHORTTEXT);
		contractDefinitionCodeAttribute.setDefaultValue(CONTRACT_DEFINITION);
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