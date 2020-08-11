package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

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
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
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
		Map<UUID, List<UUID>> contractRoles = handleRecords(records);
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
	}

	@Override
	protected void processOneDynamicAttribute(String namePrefix, String name, String valuePrefix, String value,
			boolean isEav) {
		throw new UnsupportedOperationException("No dynamic attributes present");
	}
	
	private Map<UUID, List<UUID>> handleRecords(List<CSVRecord> records) {
		Map<UUID, List<UUID>> contractRoles = new TreeMap<>();
		
		records.forEach(record -> {
			String username = record.get(usernameColumnName);
			String roleCode = record.get(rolesColumnName);
			
			if (!StringUtils.isEmpty(roleCode) && !StringUtils.isEmpty(username)) {
				IdmIdentityDto identity = identityService.getByUsername(username);
				UUID identityId;
				if (identity != null) {
					identityId = identity.getId();
					
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

						//add roles to contracts
						for (IdmIdentityContractDto contract : contractsToAssign) {
							if (contract != null) {
								if (!contract.isValidNowOrInFuture()){
									continue;
								}
								UUID contractId = contract.getId();
								if (!contractRoles.containsKey(contractId)) {
									contractRoles.put(contractId, new ArrayList<>());
								}
								System.out.println("roleName line:" + roleCode + ".");
								String[] roles = {roleCode};
								if (isMultiValue){
									 roles = roleCode.split(multiValueSeparator);
								}
								for (String roleStr : roles){
									IdmRoleDto role = roleService.getByCode(roleStr);
									if (role != null) {
										contractRoles.get(contractId).add(role.getId());
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
					}
					
				} 
			}
		});
		
		return contractRoles;
	}
	
	private List<IdmIdentityContractDto> getContractsByEav(List<IdmIdentityContractDto> contracts, CSVRecord record) {
		List<IdmIdentityContractDto> foundContracts = new ArrayList<>();
		List<Pair<String, String>> eavCodesAndValues = getEavs(record);
		for (IdmIdentityContractDto contract : contracts) {
			// get contract eavs from contract
			List<Object> existingEavs = new ArrayList<>();
			for (Pair<String, String> eavCodeAndValue : eavCodesAndValues) {
				Object eavValue = getEavValueForContract(contract.getId(), eavCodeAndValue.getFirst());
				if (eavValue != null) {
					String contractEavValue = eavValue.toString();
					if (contractEavValue != null && contractEavValue.equals(eavCodeAndValue.getSecond())) {
						existingEavs.add(eavValue);
					}
				}
			}
			
			if (!eavCodesAndValues.isEmpty() && eavCodesAndValues.size() == existingEavs.size()) {
				foundContracts.add(contract);
			}
		}
		return foundContracts;
	}
	
	private List<Pair<String, String>> getEavs(CSVRecord record) {
		return processDynamicAttribute(record, contractEavAttributeNamePrefix, contractEavAttributeValuePrefix);
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

	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		rolesColumnName = getParameterConverter().toString(properties, PARAM_ROLES_COLUMN_NAME);
		usernameColumnName = getParameterConverter().toString(properties, PARAM_USERNAME_COLUMN_NAME);
		contractEavAttributeNamePrefix = getParameterConverter().toString(properties, PARAM_CONTRACT_EAV_ATTR_NAME_PREFIX);
		contractEavAttributeValuePrefix = getParameterConverter().toString(properties, PARAM_CONTRACT_EAV_ATTR_VALUE_PREFIX);
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
		contractDefinitionCodeAttribute.setDefaultValue("default");
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