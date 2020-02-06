package eu.bcvsolutions.idm.extras.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.config.domain.ContractSliceConfiguration;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.IdmContractGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmContractSliceDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmContractGuaranteeFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleGuaranteeFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleGuaranteeRoleFilter;
import eu.bcvsolutions.idm.core.api.exception.CoreException;
import eu.bcvsolutions.idm.core.api.script.ScriptEnabled;
import eu.bcvsolutions.idm.core.api.service.IdmContractGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.model.entity.IdmContractGuarantee_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.extras.config.domain.ExtrasConfiguration;

/**
 * Ultra mega awesome extra util for all projects by Roman Kucera
 *
 * @author Roman Kucera
 * @author Ondrej Kopr
 */

@Service("extrasUtils")
public class ExtrasUtils implements ScriptEnabled {

	@Autowired
	private SecurityService securityService;
	@Autowired
	private IdmRoleGuaranteeService roleGuaranteeService;
	@Autowired
	private IdmRoleGuaranteeRoleService roleGuaranteeRoleService;
	@Autowired
	private IdmIdentityRoleService identityRoleService;
	@Autowired
	private ExtrasConfiguration extrasConfiguration;
	@Autowired
	private FormService formService;
	@Autowired
	private IdmIdentityContractService identityContractService;
	@Autowired
	private ContractSliceConfiguration contractSliceConfiguration;
	@Autowired
	private IdmContractGuaranteeService contractGuaranteeService;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private IdmRoleCatalogueService roleCatalogueService;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private IdmRoleCatalogueRoleService roleCatalogueRoleService;

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ExtrasUtils.class);
	
	/*
	 * All Czech available titles. This is only default value, real value is stored in configuration. See ExtrasConfiguration.
	 */
	public static final List<String> TITLES_AFTER = Lists.newArrayList("Ph.D.", "Th.D.", "CSc.", "DrSc.", "dr. h. c.",
			"DiS.", "MBA");
	public static final List<String> TITLES_BEFORE = Lists.newArrayList("Bc.", "BcA.", "Ing.", "Ing. arch.", "MUDr.",
			"MVDr.", "MgA.", "Mgr.", "JUDr.", "PhDr.", "RNDr.", "PharmDr.", "ThLic.", "ThDr.", "prof.", "doc.",
			"PaedDr.", "Dr.", "PhMr.");

	public Predicate getGuaranteePredicate(CriteriaBuilder builder) {
		IdmIdentityDto currentIdentity = securityService.getAuthentication().getCurrentIdentity();
		List<IdmRoleGuaranteeDto> roleGuarantees = getDirectRoleGuarantees(currentIdentity);
		List<IdmRoleGuaranteeRoleDto> roleGuaranteeRole = getRoleGuaranteesByRole(currentIdentity.getId());

		if (!roleGuarantees.isEmpty() || !roleGuaranteeRole.isEmpty()) {
			return builder.conjunction();
		} else {
			return null;
		}
	}

	public Set<String> getGuaranteePermissions(Set<String> permissions, AuthorizationPolicy policy) {
		IdmIdentityDto currentIdentity = securityService.getAuthentication().getCurrentIdentity();
		List<IdmRoleGuaranteeDto> roleGuarantees = getDirectRoleGuarantees(currentIdentity);
		List<IdmRoleGuaranteeRoleDto> roleGuaranteeRole = getRoleGuaranteesByRole(currentIdentity.getId());

		if (!roleGuarantees.isEmpty() || !roleGuaranteeRole.isEmpty()) {
			permissions.addAll(policy.getPermissions());
			// set permission via FE
			return permissions;
		} else {
			// return permission from super class.
			return permissions;
		}
	}

	public List<IdmRoleGuaranteeRoleDto> getRoleGuaranteesByRole(UUID currentId) {
		// we need to check if user is guarantee based on some role
		List<IdmIdentityRoleDto> roles = identityRoleService.findValidRoles(currentId, null).getContent();
		List<IdmRoleGuaranteeRoleDto> roleGuaranteeRole = new ArrayList<>();
		roles.forEach(idmIdentityRoleDto -> {
			IdmRoleGuaranteeRoleFilter roleGuaranteeRoleFilter = new IdmRoleGuaranteeRoleFilter();
			roleGuaranteeRoleFilter.setGuaranteeRole(idmIdentityRoleDto.getRole());
			roleGuaranteeRole.addAll(roleGuaranteeRoleService.find(roleGuaranteeRoleFilter, null).getContent());
		});
		return roleGuaranteeRole;
	}

	public List<IdmRoleGuaranteeDto> getDirectRoleGuarantees(IdmIdentityDto currentIdentity) {
		// for check guarantee we need only one record
		IdmRoleGuaranteeFilter filter = new IdmRoleGuaranteeFilter();
		filter.setGuarantee(currentIdentity.getId());
		return roleGuaranteeService.find(filter, PageRequest.of(0, 1)).getContent();
	}

	/**
	 * Return titles that must by behind name
	 *
	 * @param value
	 * @return
	 */
	public String getTitlesAfter(String value) {
		return getTitles(value, extrasConfiguration.getTitlesAfter());
	}

	/**
	 * Return titles that must by before name
	 *
	 * @param value
	 * @return
	 */
	public String getTitlesBefore(String value) {
		return getTitles(value, extrasConfiguration.getTitlesBefore());
	}

	/**
	 * Private method for same behavior for titles after and before
	 *
	 * @param value
	 * @param dictonary
	 * @return
	 */
	private String getTitles(String value, List<String> dictonary) {
		if (StringUtils.isEmpty(value)) {
			return null;
		}
		List<String> result = new ArrayList<>();

		String[] titles = value.split(" ");
		for (String title : titles) {
			final String finalTitle = title.trim().toLowerCase();

			String exits = dictonary.stream()
					.map(String::trim)
					.map(String::toLowerCase).filter(t -> t.equals(finalTitle))
					.findFirst()
					.orElse(null);
			if (exits != null) {
				result.add(title);
			}
		}

		return String.join(", ", result);
	}

	public static Date convertToDateViaInstant(LocalDate dateToConvert) {
		return java.util.Date.from(dateToConvert.atStartOfDay()
				.atZone(ZoneId.systemDefault())
				.toInstant());
	}
	
	/**
	 * Get contract manager from identity.
	 * 
	 * @param identityDto
	 * @return
	 */
	public IdmIdentityDto getManagerFromIdentity(IdmIdentityDto identityDto) {
		IdmIdentityContractDto primeValidContract = identityContractService.getPrimeValidContract(identityDto.getId());
		if (primeValidContract == null) {
			return null;
		}

		IdmContractGuaranteeFilter contractGuaranteeFilter = new IdmContractGuaranteeFilter();
		contractGuaranteeFilter.setIdentityContractId(primeValidContract.getId());
		List<IdmContractGuaranteeDto> guarantees = contractGuaranteeService.find(contractGuaranteeFilter, null).getContent();
		if (!guarantees.isEmpty()) {
			return DtoUtils.getEmbedded(guarantees.get(0), IdmContractGuarantee_.guarantee, IdmIdentityDto.class);
		}
		return null;
	}

	
	/**
	 * Get eav value for tree node
	 *
	 * @param roleId
	 * @param attributeCode
	 * @return
	 */
	public Object getEavValueForTreeNode(UUID treeNodeId, IdmFormDefinitionDto definition, String attributeCode) {
		List<IdmFormValueDto> values = formService.getValues(treeNodeId, IdmTreeNodeDto.class, definition, attributeCode);
		return getOneValue(values.stream().findFirst());
	}
	
	/**
	 * Get eav value for tree node, or return default value
	 *
	 * @param treeNodeId
	 * @param attributeCode
	 * @param defaultValue
	 * @return
	 */
	public Object getEavValueForTreeNode(UUID treeNodeId, IdmFormDefinitionDto definition, String attributeCode, Object defaultValue) {
		try {
			List<IdmFormValueDto> values = formService.getValues(treeNodeId, IdmTreeNodeDto.class, definition, attributeCode);
			Object value = getOneValue(values.stream().findFirst());
			return value == null ? defaultValue : value;
		} catch (Exception e) {
			LOG.error("Error during get form values by tree node code id: [{}] and attribute code: [{}].", treeNodeId, attributeCode, e);
		}
		return defaultValue;
	}

	/**
	 * Get eav value for identity
	 *
	 * @param roleId
	 * @param definition
	 * @param attributeCode
	 * @return
	 */
	public Object getEavValueForIdentity(UUID identityId, IdmFormDefinitionDto definition, String attributeCode) {
		return getOneValue(formService.getValues(identityId, IdmIdentityDto.class, definition, attributeCode).stream().findFirst());
	}
	
	/**
	 * Get eav value for identity contract
	 *
	 * @param roleId
	 * @param definition
	 * @param attributeCode
	 * @return
	 */
	public Object getEavValueForIdentityContract(UUID identityContractId, IdmFormDefinitionDto definition, String attributeCode) {
		return getOneValue(formService.getValues(identityContractId, IdmIdentityContract.class, definition, attributeCode).stream().findFirst());
	}
	
	/**
	 * Get eav value for identity contract
	 *
	 * @param roleId
	 * @param attributeCode
	 * @return
	 */
	public Object getEavValueForIdentityContract(UUID identityContractId, String attributeCode) {
		return getOneValue(formService.getValues(identityContractId, IdmIdentityContract.class, attributeCode).stream().findFirst());
	}

	/**
	 * Return value from optional, if value is not present return null.
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
	 * Return prime valid contract based on project specific configuration.
	 * Firstly return overriden prime contract (even expired one) then prime valid contract, when identity have not valid contract, return prime contract
	 * @param identityId
	 * @return
	 */
	public IdmIdentityContractDto getPrimeContract(UUID identityId) {
			return identityContractService.getPrimeContract(identityId);
	}
	
	/**
	 * Method calculates, if time slices are in protection gap
	 * 
	 * @param slice
	 * @param nextSlice
	 * @return
	 */
	public boolean isInProtectionInterval(IdmContractSliceDto slice, IdmContractSliceDto nextSlice) {
		int protectionInterval = contractSliceConfiguration.getProtectionInterval();
		if (slice.getContractValidTill() == null || nextSlice.getContractValidFrom() == null) {
			return false;
		}
		long diffInDays = ChronoUnit.DAYS //
				.between( //
						java.time.LocalDate.parse(slice.getContractValidTill().toString()), //
						java.time.LocalDate.parse(nextSlice.getContractValidFrom().toString()));
		
		if (diffInDays <= protectionInterval) {
			return true;
		}
		return false;
	} 
	
	/**
	 * Save EAV to identity
	 * @param entityId
	 * @param formDefinition
	 * @param value
	 * @param attributeName
	 */
	public void saveEavByName(UUID entityId, IdmFormDefinitionDto formDefinition, String value, String attributeName) {
		if (!value.isEmpty()) {
			List<String> values = new ArrayList<>();
			values.add(value);
			List<IdmFormValueDto> result = formService.saveValues(entityId, IdmIdentity.class, formDefinition, attributeName, Lists.newArrayList(values));
			LOG.info("Eav [{}] saved with result [{}]", attributeName, result);
		}
	}

	/**
	 * Removes diacritics from input String.
	 *
	 * @param text
	 * @return
	 */
	public String removeDiacritic(String text) {
		return StringUtils.stripAccents(text);
	}

	/**
	 * Removes whitespaces from input String.
	 *
	 * @param text
	 * @return
	 */
	public String removeWhitespaces(String text) {
		return StringUtils.deleteWhitespace(text);
	}

	/**
	 * Checks is String is null, if so, returns "", if not, return value.
	 * 
	 * @param value
	 * @return
	 */
	public String getStringValue(String value) {
		if (value == null) {
			return "";
		}
		return value;
	}
	
	/**
	 * Return values from EAV as list of object values
	 *
	 * @param values
	 * @return
	 */
	public static List<Object> getValues(List<IdmFormValueDto> values) {
		ArrayList<Object> valuesAsList = new ArrayList<>();
		for (IdmFormValueDto value : values) {
			valuesAsList.add(value.getValue());
		}
		return valuesAsList;
	}

	/**
	 * Return identity by external code, or null if entity isn't found.
	 *
	 * @param externalCode
	 * @return
	 */
	public IdmIdentityDto getIdentityByExternalCode(String externalCode) {
		IdmIdentityFilter filter = new IdmIdentityFilter();
		filter.setExternalCode(externalCode);
		//
		Optional<IdmIdentityDto> first = identityService.find(filter, null).getContent().stream().findFirst();
		if (first.isPresent()) {
			return first.get();
		}
		//
		LOG.warn("For external code [{}] was not found identity.", externalCode);
		return null;
	}

	/**
	 * Return eav value for primary contract by identity id
	 *
	 * @param identityId
	 * @param attributeCode
	 * @return
	 */
	public Object getEavValueForContractByIdentity(UUID identityId, String attributeCode, IdmFormDefinitionDto definitionDto) {
		IdmIdentityContractDto contract = identityContractService.getPrimeContract(identityId);
		if (contract == null) {
			return null;
		}
		try {
			return getOneValue(formService.getValues(contract, definitionDto, attributeCode).stream().findFirst());
		} catch (IllegalArgumentException e) {
			LOG.error("For attribute code [{}] doesnt exist value", attributeCode, e);
			return null;
		}
	}

	/**
	 * Return eav values for primary contract by identity id. Used for multivalued attributes.
	 * 
	 * @param identityId
	 * @param attributeCode
	 * @param definitionDto
	 * @return
	 */
	public List<Object> getEavValuesForContractByIdentity(UUID identityId, String attributeCode, IdmFormDefinitionDto definitionDto) {
		IdmIdentityContractDto contract = identityContractService.getPrimeContract(identityId);
		if (contract == null) {
			return new ArrayList<>();
		}
		List<IdmFormValueDto> values = formService.getValues(contract, definitionDto, attributeCode);
		return getValues(values);
	}
	
	/**
	 * Return a List of owners of EAV with some value.
	 * 
	 * @param attributeCode
	 * @param attributeValue
	 * @return
	 */
	public List<BaseDto> findIdentityOwnersOfEav(String attributeCode, String attributeValue) {
		return formService.findOwners(IdmIdentityDto.class, attributeCode, attributeValue, null).getContent();
	}

	/**
	 * Add role to catalogue
	 *
	 * @param roleCode
	 * @param catalogueCode
	 */
	public void addRoleToCatalogue(String roleCode, String catalogueCode) {
		IdmRoleCatalogueDto catalogue = roleCatalogueService.getByCode(catalogueCode);

		IdmRoleDto roleDto = roleService.getByCode(roleCode);

		if (roleDto == null) {
			LOG.error("Role by code [{}] doesnt exists", roleCode);
			return;
		}

		List<IdmRoleCatalogueRoleDto> catalogues = roleCatalogueRoleService.findAllByRole(roleDto.getId());
		
		List<IdmRoleCatalogueRoleDto> changed = new ArrayList<>();
		
		for (IdmRoleCatalogueRoleDto cat : catalogues) {
			changed.add(cat);
			if (cat.getRoleCatalogue().equals(catalogue.getId())) {
				LOG.info("Role catalogue {} exists for role {}", catalogueCode, roleCode);
				return;
			}
		}

		IdmRoleCatalogueRoleDto roleCatalogueRoleDto = new IdmRoleCatalogueRoleDto();
		roleCatalogueRoleDto.setRole(roleDto.getId());
		roleCatalogueRoleDto.setRoleCatalogue(catalogue.getId());
		roleCatalogueRoleDto = roleCatalogueRoleService.save(roleCatalogueRoleDto);

		changed.add(roleCatalogueRoleDto);
		roleCatalogueRoleService.saveAll(changed);
		roleService.save(roleDto);
	}

	/**
	 * This method transforms object to byte array.
	 *
	 * @param object
	 * @return
	 */
	public static byte[] serialize(Serializable object) {
		ByteArrayOutputStream handleBAOS = new ByteArrayOutputStream(100);
		try {
			ObjectOutputStream handleOOS = new ObjectOutputStream(handleBAOS);
			handleOOS.writeObject(object);

			return handleBAOS.toByteArray();

		} catch (IOException e) {
			LOG.error("ERROR during serialize. ", e);
			throw new CoreException(e);
		} finally {
			try {
				handleBAOS.close();

			} catch (IOException e) {
				// no point throwing an exception, it would only cover any previous exceptions
				// this exception is unlikely anyway
				LOG.error("Cannot close ByteArrayOutputStream.", e);
			}
		}
	}
	
	/**
	 * This method creates object from byte array.
	 *
	 * @param objectBytes
	 * @return
	 */
	public static Object deserialize(byte[] objectBytes) {

		ByteArrayInputStream bais = new ByteArrayInputStream(objectBytes);

		try {
			ObjectInputStream ois = new ObjectInputStream(bais);

			return ois.readObject();

		} catch (IOException | ClassNotFoundException e) {
			LOG.error("ERROR during deserialize. ", e);
			throw new CoreException(e);
		} finally {
			try {
				bais.close();

			} catch (IOException e) {
				// no point throwing an exception, it would only cover any previous exceptions
				// this exception is unlikely anyway
				LOG.error("Cannot close ByteArrayInputStream.", e);
			}
		}
	}
	
	/**
	 * Checks if a role is among IdmIdentityRoleDtos.
	 * 
	 * @param roles
	 * @param roleName
	 * @return
	 */
	public boolean hasRoleOnContract(List<IdmIdentityRoleDto> roles, String roleName) {
		return roles.stream().anyMatch(idmIdentityRoleDto ->
				DtoUtils.getEmbedded(idmIdentityRoleDto, IdmIdentityRole_.role, IdmRoleDto.class).getCode().equals(roleName));
	}
	
	/**
	 * Removes role from an identity, it only works if the identity has the role only once.
	 * 
	 * @param identityId
	 * @param roleCode
	 */
	public void removeRoleFromIdentity(String identityId, String roleCode) {
		IdmRoleDto role = roleService.getByCode(roleCode);
		IdmIdentityRoleFilter filter = new IdmIdentityRoleFilter();
		filter.setIdentityId(UUID.fromString(identityId));
		filter.setRoleId(role.getId());
		@SuppressWarnings("deprecation")
		Page<IdmIdentityRoleDto> result = identityRoleService.find(filter, new PageRequest(0, 50));
		if (result.getContent().size() == 1) {
			identityRoleService.deleteById(result.getContent().get(0).getId());
		}
	}

	/**
	 * Removes role from all of the identity's contracts.
	 * 
	 * @param identityId
	 * @param roleCode
	 */
	public void removeRoleFromAllContracts(String identityId, String roleCode) {
		IdmRoleDto role = roleService.getByCode(roleCode);
		IdmIdentityRoleFilter filter = new IdmIdentityRoleFilter();
		filter.setIdentityId(UUID.fromString(identityId));
		filter.setRoleId(role.getId());
		Page<IdmIdentityRoleDto> result = identityRoleService.find(filter, null);
		result.forEach(idmIdentityRoleDto -> {
			identityRoleService.deleteById(idmIdentityRoleDto.getId());
		});
	}

	/**
	 * Adds role to identity.
	 * 
	 * @param entityId
	 * @param identityRoleDto
	 */
	public void addRoleToIdentity(UUID entityId, IdmIdentityRoleDto identityRoleDto) {
		IdmIdentityContractDto contract = identityContractService.getPrimeContract(entityId);
		if (contract != null) {
			identityRoleDto.setIdentityContract(contract.getId());
			identityRoleService.save(identityRoleDto);
		}
	}

	/**
	 * Adds role to identity.
	 * 
	 * @param entityId
	 * @param roleCode
	 */
	public void addRoleToIdentity(UUID entityId, String roleCode) {
		IdmRoleDto role = roleService.getByCode(roleCode);
		IdmIdentityRoleDto identityRoleDto = new IdmIdentityRoleDto();
		identityRoleDto.setRole(role.getId());
		addRoleToIdentity(entityId, identityRoleDto);
	}

	/**
	 * Save EAV to contract
	 * @param entityId
	 * @param formDefinition
	 * @param values
	 * @param attributeName
	 */
	public void saveEavByNameToContract(UUID entityId, IdmFormDefinitionDto formDefinition, List<String> values, String attributeName) {
		if (values != null && !values.isEmpty()) {
			List<IdmFormValueDto> result = formService.saveValues(entityId, IdmIdentityContract.class, formDefinition, attributeName, Lists.newArrayList(values));
			LOG.info("Eav [{}] saved with result [{}]", attributeName, result);
		}
	}

	/**
	 * Return manager for contract, works only for one manager per contract.
	 * Search for manager by org structure if no manager was found try to found manager which is directly assigned
	 * @param contractId
	 * @return
	 */
	public IdmIdentityDto getManagerForContract(UUID contractId) {
		IdmIdentityFilter filter = new IdmIdentityFilter();
		filter.setManagersByContract(contractId);

		List<IdmIdentityDto> managers = identityService.find(filter, null).getContent();
		if (managers.size() > 1) {
			LOG.error("For contract [{}] was found more than 1 manager! First manager will be returned.", contractId);
		} else if (managers.isEmpty()) {
			IdmContractGuaranteeFilter guaranteeFilter = new IdmContractGuaranteeFilter();
			guaranteeFilter.setIdentityContractId(contractId);
			List<IdmContractGuaranteeDto> guaranteeDtos = contractGuaranteeService.find(guaranteeFilter, null).getContent();
			IdmContractGuaranteeDto contractGuaranteeDto = guaranteeDtos.stream().findFirst().orElse(null);
			if (contractGuaranteeDto != null && contractGuaranteeDto.getGuarantee() != null) {
				return DtoUtils.getEmbedded(contractGuaranteeDto, IdmContractGuarantee_.guarantee, IdmIdentityDto.class);
			}
			return null;
		}

		Optional<IdmIdentityDto> first = managers.stream().findFirst();
		if (first.isPresent()) {
			return first.get();
		}
		return null;
	}
	
	/**
	 * Return all managers for contract.
	 * Search for manager by org structure if no manager was found try to found manager which is directly assigned
	 * @param contractId
	 * @return
	 */
	public List<IdmIdentityDto> getAllManagersForContract(UUID contractId) {
		IdmIdentityFilter filter = new IdmIdentityFilter();
		filter.setManagersByContract(contractId);
		List<IdmIdentityDto> managers = identityService.find(filter, null).getContent();

		return managers;
	}

	/**
	 * Returns a list of identities with a role.
	 * 
	 * @param code
	 * @return
	 */
	public List<IdmIdentityDto> getUsersByRoleName(String code) {
		List<IdmIdentityDto> users = new ArrayList<>();
		IdmRoleDto roleDto = roleService.getByCode(code);

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
	 * Return boolean value of the external attribute of the identities main contract.
	 * 
	 * @param userName
	 * @return
	 */
	public boolean isExternist(String userName){
		IdmIdentityDto identity = identityService.getByUsername(userName);
		IdmIdentityContractDto primeContract = identityContractService.getPrimeContract(identity.getId());
		return primeContract.isExterne();
	}

	/**
	 * Get eav value for tree node
	 *
	 * @param roleId
	 * @param attributeCode
	 * @return
	 */
	public Object getEavValueForTreeNode(UUID treeNodeId, String attributeCode) {
		List<IdmFormValueDto> values = formService.getValues(treeNodeId, IdmTreeNodeDto.class, attributeCode);
		return getOneValue(values.stream().findFirst());
	}

	/**
	 * Get eav value for identity
	 *
	 * @param roleId
	 * @param attributeCode
	 * @return
	 */
	public Object getEavValueForIdentity(UUID identityId, String attributeCode) {
		return getOneValue(formService.getValues(identityId, IdmIdentityDto.class, attributeCode).stream().findFirst());
	}
	
	/**
	 * Get eav value for identity
	 *
	 * @param roleId
	 * @param attributeDto
	 * @return
	 */
	public Object getEavValueForIdentity(UUID identityId, IdmFormAttributeDto attributeDto) {
		return getOneValue(formService.getValues(identityId, IdmIdentityDto.class, attributeDto).stream().findFirst());
	}
	
	/**
	 * Get eav value for identity contract
	 *
	 * @param roleId
	 * @param attributeCode
	 * @return
	 */
}
