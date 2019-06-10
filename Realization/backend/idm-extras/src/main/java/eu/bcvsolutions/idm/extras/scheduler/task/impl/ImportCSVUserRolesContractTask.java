package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.api.domain.ConceptRoleRequestOperation;
import eu.bcvsolutions.idm.core.api.domain.CoreResultCode;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.RoleRequestedByType;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.IdmConceptRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * @author Petr Hanák
 * 
 * This task reads username, role and optional contract eav attribute from the csv file and find contract with this eav to assign role.
 *  
 */
@Component
@Description("Parses input CSV (path as parameter) and assigns roles to user contracts. Only role assignment is allowed.")
public class ImportCSVUserRolesContractTask extends AbstractSchedulableTaskExecutor<OperationResult> {

	private static final Logger LOG = LoggerFactory.getLogger(ImportCSVUserRolesContractTask.class);
	
	private static final String PARAM_CSV_FILE_NAME = "Path to csv file";
	private static final String PARAM_CSV_ATTACHMENT = "Import csv file";
	private static final String PARAM_ROLES_COLUMN_NAME = "Column with roles";
	private static final String PARAM_USERNAME_COLUMN_NAME = "Column with username";
	private static final String PARAM_CONTRACT_EAV_COLUMN_NAME = "Column with contract eav";
	private static final String PARAM_CONTRACT_EAV_NAME = "Name of contract eav attribute";
	private static final String PARAM_COLUMN_SEPARATOR = "Column separator";
	private static final String PARAM_MULTI_VALUE_SEPARATOR = "Multi value separator";
	
	private static final String EAV_CICIN = "cicin";
	private static final String USERNAME_CICIN_KEY_SEPARATOR = ";";

	// Defaults
	private static final String COLUMN_SEPARATOR = ";";
	private static final String MULTI_VALUE_SEPARATOR = "\\r?\\n"; // new line separator
	
	private UUID attachmentId;
	private String csvPath;
	private String rolesColumnName;
	private String usernameColumnName;
	private String contractEavColumnName;
	private String contractEavName;
	private String columnSeparator;
	private String multiValueSeparator;
	private List<IdmRoleDto> idmRoles;

	@Autowired private IdmIdentityService identityService;
	@Autowired private IdmRoleService roleService;
	@Autowired private IdmIdentityRoleService identityRoleService;
	@Autowired private IdmRoleRequestService roleRequestService;
	@Autowired private IdmConceptRoleRequestService conceptRoleRequestService;
	@Autowired private IdmIdentityContractService identityContractService;
	@Autowired private FormService formService;

	@Override
	public OperationResult process() {
		if (csvPath == null) {
			throw new IllegalArgumentException("CSV path must be defined.");
		}
		//
		Map<String, List<String>> userContractsToRoles = new TreeMap<>();
		List<String> allCsvRoles = new ArrayList<>();

		IdmRoleFilter roleFilter = new IdmRoleFilter();
		idmRoles = roleService.find(roleFilter, null).getContent();
		try {
			FileReader reader = new FileReader(csvPath);
			CSVParser csv = getDefaultFormat().parse(reader);
			LOG.info("---rolebulk Headers map: [{}]", csv.getHeaderMap());
			for (CSVRecord record : csv.getRecords()) {
				LOG.info("---rolebulk Processing record: [{}]", record.toMap());
				String csvUsername = record.get(usernameColumnName);
				String csvRole = record.get(rolesColumnName);
				String csvCicin = record.get(contractEavColumnName);
				String usernameCicinKey = csvUsername + USERNAME_CICIN_KEY_SEPARATOR + csvCicin;
				if (csvRole.length() == 0 || csvUsername.length() == 0) {
					continue;
				}
				if (!userContractsToRoles.containsKey(usernameCicinKey)) {
					userContractsToRoles.put(usernameCicinKey, new ArrayList<>());
				}
				if(!allCsvRoles.contains(csvRole)) {
					allCsvRoles.add(csvRole);
				}
				userContractsToRoles.get(usernameCicinKey).add(csvRole);
			}
		} catch (IOException e) {
			LOG.error("An error occurred while reading input CSV file [{}], error: [{}].", csvPath, e);
			throw new ResultCodeException(CoreResultCode.NOT_FOUND, "File '" + csvPath + "' not found.", e);
		}
		
		// map roleName - IdmRoleDto for every assignment, also cleans role names that are not in IdM from the list
		Map<String, IdmRoleDto> namesAndRoles = getRolesToMap(allCsvRoles);
		
		//
		this.count = (long) userContractsToRoles.size();
		this.counter = 0L;
		
		for (String usernameCicin : userContractsToRoles.keySet()) {
			String[] usernameCicinSplit = usernameCicin.split(USERNAME_CICIN_KEY_SEPARATOR);
			String username = usernameCicinSplit[0];
			String cicin = null;
			if (usernameCicinSplit.length > 1) {
				cicin = usernameCicinSplit[1];				
			}
			
			LOG.info("---rolebulk username: [{}], roles: [{}]", username, userContractsToRoles.get(usernameCicin));
			IdmIdentityDto identity = getIdentityByUsername(username);
			if (identity == null) {
				LOG.warn("---rolebulk identity for username [{}] does not exist.", username);
				continue;
			}
			
			List<String> roleNames = userContractsToRoles.get(usernameCicin);
			
			if (roleNames.isEmpty()) {
				this.logItemProcessed(identity, taskNotCompleted("There are no roles to assign for user: " + username + ", cicin: " + cicin + " in CSV file!"));
				LOG.warn("---rolebulk roles are empty for user [{}], cicin [{}] check your input CSV.", username, cicin);
				continue;
			}
			// find contract to add roles by eav cicin. If cicin is null, find all contracts..
			List<IdmIdentityContractDto> contracts = identityContractService.findAllValidForDate(identity.getId(), LocalDate.now(), null);
			if (contracts == null) {
				this.logItemProcessed(identity, taskNotCompleted("User " + username + " does not have any valid contract."));
				LOG.warn("---rolebulk User [{}] does not have any valid contract!", username);
				continue;
			}
			
			// will get role DTO's that exists in IdM from namesAndRoles list			
			List<IdmRoleDto> roles = getContractRoles(namesAndRoles, roleNames);
			
			if (cicin != null) {
				IdmIdentityContractDto contract = getContractByCicin(contracts, cicin);
				if (contract == null) {
					LOG.warn("---rolebulk There is no contract with cicin [{}] for user [{}]", cicin, username);
					this.logItemProcessed(identity, taskNotCompleted("There is no contract with cicin " + cicin + " for user "+ username +" in IdM!"));
					continue;
				}
				// filter already assigned roles
				List<IdmRoleDto> filterOut = getRolesToFilterOut(roles, contract);
				roles.removeAll(filterOut);
				if (!roles.isEmpty()) {
					addRolesToContract(contract, roles);
					String assignedRoles = getAssignedRolesToString(roles);
					this.logItemProcessed(contract, taskCompleted("Assigned roles: " + assignedRoles));					
				} else {
					this.logItemProcessed(contract, taskNotCompleted("No roles to assign!"));
				}
			} else {
				// iterate over contracts and add roles to all of them
				this.count--;
				for (IdmIdentityContractDto contract : contracts) {
					this.count++;
					List<IdmRoleDto> rolesCopy = new ArrayList<IdmRoleDto>(roles); 
					List<IdmRoleDto> filterOut = getRolesToFilterOut(rolesCopy, contract);
					rolesCopy.removeAll(filterOut);
					if (!rolesCopy.isEmpty()) {
						addRolesToContract(contract, rolesCopy);
						String assignedRoles = getAssignedRolesToString(rolesCopy);
						this.logItemProcessed(contract, taskCompleted("Assigned roles: " + assignedRoles));
					} else {
						this.logItemProcessed(contract, taskNotCompleted("No roles to assign!"));
					}
				}
			}
			++this.counter;
			if (!this.updateState()) {
				break;
			}
		}
		return new OperationResult.Builder(OperationState.CREATED).build();
	}

	private final CSVFormat getDefaultFormat() {
		return CSVFormat.DEFAULT
					   .withFirstRecordAsHeader()
					   .withDelimiter(',');
	}
	
	private Map<String, IdmRoleDto> getRolesToMap(List<String> roles) {
		Map<String, IdmRoleDto> namesAndRoles = new HashMap<>();
		roles.forEach(role -> {
			// Name must be unique in the environment where you use it!
			IdmRoleDto roleDto = idmRoles.stream().filter(idmRoleDto -> idmRoleDto.getName().equals(role)).findFirst().orElse(null);
			if (roleDto != null) {
				namesAndRoles.put(role, roleDto);
			} else {
				LOG.warn("---rolebulk cannot find role for name [{}]", role);
			}
		});
		return namesAndRoles;
	}
	
	private IdmIdentityContractDto getContractByCicin(List<IdmIdentityContractDto> contracts, String cicin) {
		for (IdmIdentityContractDto contract : contracts) {
//			get cicin from eav of contract
			if (getEavValueForContract(contract.getId(), EAV_CICIN) != null) {
				String contractEavCicin = getEavValueForContract(contract.getId(), EAV_CICIN).toString();
				if (contractEavCicin != null && contractEavCicin.equals(cicin)) {
					return contract;
				}
			}
		}
		return null;
	}
	
	private List<IdmRoleDto> getContractRoles(Map<String, IdmRoleDto> namesAndRoles, List<String> roleNames) {
		List<IdmRoleDto> roles = new ArrayList<>();
		for (String role : roleNames) {
			if (namesAndRoles.containsKey(role)) {
				roles.add(namesAndRoles.get(role));
			}
		}
		return roles;
	}

	private IdmIdentityDto getIdentityByUsername(String username) {
		return identityService.getByUsername(username);
	}

	private List<IdmRoleDto> getRolesToFilterOut(List<IdmRoleDto> roles, IdmIdentityContractDto contract) {
		// filter already assigned roles
		List<IdmRoleDto> filterOut = new ArrayList<>();
		//	find valid contracts only	
		List<IdmIdentityRoleDto> rolesByContract = identityRoleService.findAllByContract(contract.getId());
		for (IdmIdentityRoleDto assignedRole : rolesByContract) {
			for (IdmRoleDto toBeAssigned : roles) {
				if (toBeAssigned.getId().equals(assignedRole.getRole())) {
					filterOut.add(toBeAssigned);
					break;
				}
			}
		}
		return filterOut;
	}
	
	private void addRolesToContract(IdmIdentityContractDto contract, List<IdmRoleDto> roles) {
		//
		UUID identityRoleId = null;
		ConceptRoleRequestOperation operation = ConceptRoleRequestOperation.ADD;
		// prepare request
		IdmRoleRequestDto roleRequest = new IdmRoleRequestDto();
		roleRequest.setApplicant(contract.getIdentity());
		roleRequest.setRequestedByType(RoleRequestedByType.MANUALLY);
		roleRequest.setExecuteImmediately(true);
		roleRequest = roleRequestService.save(roleRequest);
		for (IdmRoleDto role : roles) {
			IdmConceptRoleRequestDto conceptRoleRequest = new IdmConceptRoleRequestDto();
			conceptRoleRequest.setRoleRequest(roleRequest.getId());
			conceptRoleRequest.setIdentityContract(contract.getId());
			conceptRoleRequest.setValidFrom(null);
			conceptRoleRequest.setIdentityRole(identityRoleId);
			conceptRoleRequest.setValidTill(null);
			conceptRoleRequest.setRole(role.getId());
			conceptRoleRequest.setOperation(operation);
			conceptRoleRequestService.save(conceptRoleRequest);
		}
		roleRequestService.startRequestInternal(roleRequest.getId(), true);
	}
	
	private String getAssignedRolesToString(List<IdmRoleDto> roles) {
		List<String> assignedRolesList = new ArrayList<>();
		for (IdmRoleDto role : roles) {
			assignedRolesList.add(role.getCode());
		}
		String assignedRoles = String.join(", ", assignedRolesList);
		return assignedRoles;
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

	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		attachmentId = getParameterConverter().toUuid(properties, PARAM_CSV_ATTACHMENT);
		csvPath = getParameterConverter().toString(properties, PARAM_CSV_FILE_NAME);
		rolesColumnName = getParameterConverter().toString(properties, PARAM_ROLES_COLUMN_NAME);
		usernameColumnName = getParameterConverter().toString(properties, PARAM_USERNAME_COLUMN_NAME);
		contractEavColumnName = getParameterConverter().toString(properties, PARAM_CONTRACT_EAV_COLUMN_NAME);
		contractEavName = getParameterConverter().toString(properties, PARAM_CONTRACT_EAV_NAME);
		columnSeparator = getParameterConverter().toString(properties, PARAM_COLUMN_SEPARATOR);
		multiValueSeparator = getParameterConverter().toString(properties, PARAM_MULTI_VALUE_SEPARATOR);
		// if not filled, init multiValueSeparator and check if csv has description
		if (multiValueSeparator == null) {
			multiValueSeparator = MULTI_VALUE_SEPARATOR;
		}
	}

	@Override
	public Map<String, Object> getProperties() {
		LOG.debug("Start getProperties");
		Map<String, Object> props = super.getProperties();
		props.put(PARAM_CSV_FILE_NAME, csvPath);
		props.put(PARAM_CSV_ATTACHMENT, attachmentId);
		props.put(PARAM_ROLES_COLUMN_NAME, rolesColumnName);
		props.put(PARAM_USERNAME_COLUMN_NAME, usernameColumnName);
		props.put(PARAM_CONTRACT_EAV_COLUMN_NAME, contractEavColumnName);
		props.put(PARAM_CONTRACT_EAV_NAME, contractEavName);
		props.put(PARAM_COLUMN_SEPARATOR, columnSeparator);
		props.put(PARAM_MULTI_VALUE_SEPARATOR, multiValueSeparator);
		return props;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		// csv file attachment
		IdmFormAttributeDto csvAttachment = new IdmFormAttributeDto(PARAM_CSV_ATTACHMENT, PARAM_CSV_ATTACHMENT,
				PersistentType.ATTACHMENT);
		csvAttachment.setRequired(true);
		IdmFormAttributeDto csvFileNameAttribute = new IdmFormAttributeDto(PARAM_CSV_FILE_NAME, PARAM_CSV_FILE_NAME,
				PersistentType.SHORTTEXT);
		csvFileNameAttribute.setRequired(true);
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
		IdmFormAttributeDto multiValueSeparatorAttribute = new IdmFormAttributeDto(PARAM_MULTI_VALUE_SEPARATOR, PARAM_MULTI_VALUE_SEPARATOR,
				PersistentType.CHAR);
		multiValueSeparatorAttribute.setRequired(false);
		multiValueSeparatorAttribute.setPlaceholder("default is new line");
		//
		return Lists.newArrayList(csvAttachment, csvFileNameAttribute, rolesColumnNameAttribute, usernameColumnNameAttribute, contractEavColumnNameAttribute, contractEavNameAttribute, columnSeparatorAttribute, multiValueSeparatorAttribute);
	}
	
	private OperationResult taskCompleted(String message) {
		return new OperationResult.Builder(OperationState.EXECUTED).setModel(new DefaultResultModel(ExtrasResultCode.TEST_ITEM_COMPLETED,
				ImmutableMap.of("message", message))).build();
	}
	
	private OperationResult taskNotCompleted(String message) {
		return new OperationResult.Builder(OperationState.NOT_EXECUTED).setModel(new DefaultResultModel(ExtrasResultCode.TEST_ITEM_COMPLETED,
				ImmutableMap.of("message", message))).build();
	}
}

