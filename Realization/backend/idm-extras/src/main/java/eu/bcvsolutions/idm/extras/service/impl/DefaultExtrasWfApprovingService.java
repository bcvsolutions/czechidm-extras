package eu.bcvsolutions.idm.extras.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.identityconnectors.framework.common.exceptions.PermissionDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import eu.bcvsolutions.idm.core.api.config.domain.RoleConfiguration;
import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleGuaranteeFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleGuaranteeRoleFilter;
import eu.bcvsolutions.idm.core.api.service.IdmContractGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormDefinitionService;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleGuarantee_;
import eu.bcvsolutions.idm.core.script.evaluator.DefaultSystemScriptEvaluator;
import eu.bcvsolutions.idm.extras.config.domain.ExtrasConfiguration;
import eu.bcvsolutions.idm.extras.service.api.ExtrasWfApprovingService;

@Service
public class DefaultExtrasWfApprovingService implements ExtrasWfApprovingService {

	@Autowired
	public IdmFormDefinitionService formDefinitionService;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired	
	private IdmIdentityContractService idmIdentityContractService;
	@Autowired 
	private IdmRoleGuaranteeRoleService idmRoleGuaranteeRoleService;
	@Autowired 
	private IdmRoleGuaranteeService idmRoleGuaranteeService;
	@Autowired
	private DefaultSystemScriptEvaluator defaultSystemScriptEvaluator;
	@Autowired
	private IdmContractGuaranteeService contractGuaranteeService;
	@Autowired
	private ExtrasConfiguration extrasConfiguration;

	
	public List<IdmIdentityDto> getDirectRoleGuarantees(UUID idmRoleUUID, String guaranteeType) {
		List<IdmIdentityDto> result = new ArrayList<IdmIdentityDto>();

		IdmRoleGuaranteeFilter userFilter = new IdmRoleGuaranteeFilter();
		if (guaranteeType != null) {
			userFilter.setType(guaranteeType);
		}
		userFilter.setRole(idmRoleUUID);
		
		List<IdmRoleGuaranteeDto> directGuarantees = idmRoleGuaranteeService.find(userFilter, null).getContent();
		for (IdmRoleGuaranteeDto guarantee : directGuarantees) {
			IdmIdentityDto identity = DtoUtils.getEmbedded(guarantee, IdmRoleGuarantee_.guarantee, IdmIdentityDto.class, null);
			result.add(identity);
		}
		return result;
	}
	
	public List<IdmIdentityDto> getRoleGuaranteesByRole(UUID idmRoleUUID, String guaranteeType) {
		List<IdmIdentityDto> result = new ArrayList<IdmIdentityDto>();
		//Guarantees by role
		IdmRoleGuaranteeRoleFilter rolefilter = new IdmRoleGuaranteeRoleFilter();
		rolefilter.setRole(idmRoleUUID);
		if (guaranteeType != null) {
			rolefilter.setType(guaranteeType);
		}
				
		List<IdmRoleGuaranteeRoleDto> content = idmRoleGuaranteeRoleService.find(rolefilter, null).getContent();
		for (IdmRoleGuaranteeRoleDto item : content) {
			result.addAll(identityService.findAllByRole(item.getGuaranteeRole()));
		}
		return result;
	}
	
	private List<IdmIdentityDto> getRoleGuaranteesByType(UUID idmRoleUUID, String guaranteeType) {
		List<IdmIdentityDto> result = new ArrayList<IdmIdentityDto>();
			result.addAll(getDirectRoleGuarantees(idmRoleUUID, guaranteeType));
			result.addAll(getRoleGuaranteesByRole(idmRoleUUID, guaranteeType));
		return result;
	}
	
	
	public String getRoleGuaranteesForApproval(IdmConceptRoleRequestDto conceptRoleRequest, String guaranteeType) {
		List<IdmIdentityDto> result = new ArrayList<IdmIdentityDto>();
		List<IdmIdentityDto> guaranteesA = new ArrayList<IdmIdentityDto>();
		List<IdmIdentityDto> guaranteesB = new ArrayList<IdmIdentityDto>();
		
		UUID role = conceptRoleRequest.getRole();
		
		String guaranteeCodeA = extrasConfiguration.getRoleGuaranteeTypeA();
		String guaranteeCodeB = extrasConfiguration.getRoleGuaranteeTypeB();
		
		if (guaranteeCodeA != null) {
			guaranteesA = getRoleGuaranteesByType(role, guaranteeCodeA);
		}
		
		if (guaranteeCodeB != null) {
			guaranteesB = getRoleGuaranteesByType(role, guaranteeCodeB);
		}
		
		
		if (guaranteeType.equals(APPROVE_BY_GUARANTEE_A)) {	
			if (!guaranteesA.isEmpty()) {
				result = guaranteesA;
			//if we do not have A type or B type guarantees. We return all guarantees (no type specified) 
			} else if (guaranteesA.isEmpty() && guaranteesB.isEmpty()) {
				result.addAll(getRoleGuaranteesByType(role, null));
			}
		}	
			
		if (guaranteeType.equals(APPROVE_BY_GUARANTEE_B)) {
			result = guaranteesB;
		}	
		return processCandidates(result);
	}
	
	
	public String getContractManagersForApproval(IdmConceptRoleRequestDto roleConcept) {
		List<IdmIdentityDto> result = new ArrayList<IdmIdentityDto>();

		IdmIdentityContractDto idmIdentityContractDto = idmIdentityContractService.get(roleConcept.getIdentityContract());
		
		IdmIdentityFilter managerFilter = new IdmIdentityFilter();
		if (idmIdentityContractDto != null) {
			managerFilter.setManagersByContract(roleConcept.getIdentityContract());
			managerFilter.setManagersFor(idmIdentityContractDto.getIdentity());
			result.addAll(identityService.find(managerFilter, null).getContent());
		}
		return processCandidates(result);
	}
 
	public String getApproversFromScript(IdmConceptRoleRequestDto conceptRoleRequestDto) {
		
		String scriptCode = extrasConfiguration.getCustomApprovalScriptCode();
		
		List<IdmIdentityDto> result = new ArrayList<IdmIdentityDto>();
		if (scriptCode != null) {
			@SuppressWarnings("unchecked")
			List<IdmIdentityDto> scriptResult = (List<IdmIdentityDto>) defaultSystemScriptEvaluator.evaluate(defaultSystemScriptEvaluator.newBuilder()
					.setScriptCode(scriptCode).addParameter("conceptRoleRequestDto", conceptRoleRequestDto).build());
				if (scriptResult != null) {
					result.addAll(scriptResult);
				}
		}
		return processCandidates(result);		
	}

	public String getAdminAsCandidate() {
		List<IdmIdentityDto> result;
		IdmRoleDto idmRoleDto = roleService.getByCode(RoleConfiguration.DEFAULT_ADMIN_ROLE);
		if (null != idmRoleDto) {
			result = identityService.findAllByRole(idmRoleDto.getId());
			if (!result.isEmpty()) {
				return processCandidates(result);
			}
		}
		throw new PermissionDeniedException("Candidate for approve role not found.");
	}
	
	private String processCandidates(List<IdmIdentityDto> candidates) {
		candidates.removeIf(IdmIdentityDto::isDisabled);
		return identityService.convertIdentitiesToString(candidates.stream().distinct().collect(Collectors.toList()));
	}
		
	public boolean isUserInCandidates(String candidates, String user) {
		if (candidates == null || user == null) {
			return false;
		}
		List<String> listOfCandidates = Arrays.asList(candidates.split(","));
		return listOfCandidates.stream().anyMatch(identity -> {
			return identityService.getByUsername(identity).getId().toString().equals(user);
		});
	}
	
}