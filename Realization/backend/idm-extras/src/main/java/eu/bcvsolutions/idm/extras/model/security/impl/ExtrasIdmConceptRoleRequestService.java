package eu.bcvsolutions.idm.extras.model.security.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleGuaranteeFilter;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.model.repository.IdmAutomaticRoleRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmConceptRoleRequestRepository;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmConceptRoleRequestService;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.workflow.service.WorkflowProcessInstanceService;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;
import eu.bcvsolutions.idm.extras.util.ExtrasUtils;

/**
 * Overridden service for concept role request.
 * Throw error when currently logged user try to save concept with roles for which is not guarantee
 * 
 * TODO: service is in bad package
 *
 * @author Ondrej Kopr
 * @author Roman Kucera
 */
public class ExtrasIdmConceptRoleRequestService extends DefaultIdmConceptRoleRequestService {

	@Autowired
	private IdmRoleGuaranteeService roleGuaranteeService;
	@Autowired
	private SecurityService securityService;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private ExtrasUtils extrasUtils;
	@Autowired
	private IdmIdentityContractService identityContractService;

	@Autowired
	public ExtrasIdmConceptRoleRequestService(IdmConceptRoleRequestRepository repository,
											  WorkflowProcessInstanceService workflowProcessInstanceService, LookupService lookupService,
											  IdmAutomaticRoleRepository automaticRoleRepository) {
		super(repository, workflowProcessInstanceService, lookupService, automaticRoleRepository);
	}

	@Override
	public IdmConceptRoleRequestDto save(IdmConceptRoleRequestDto dto, BasePermission... permission) {
		UUID currentId = securityService.getCurrentId();

		// for administrator doesnt work the behavior
		if (securityService.isAdmin()) {
			return super.save(dto, permission);
		}

		// If request isn't check if only changed attribute is a systemState
		if (!this.isNew(dto)) {
			// In future is possible use ObjectDifferBuilder
			IdmConceptRoleRequestDto currentConcept = this.get(dto.getId());
			// If concepts are same we can same it directly
			// In version 9.7.10 and lower isn't checked systemState in equals method for concepts
			if (dto.equals(currentConcept)) {
				return super.save(dto, permission);
			}
		}

		if (dto.getIdentityContract() != null && currentId != null) {

			IdmIdentityContractDto identityForRoleAssignContract = identityContractService.get(dto.getIdentityContract());
			if (identityForRoleAssignContract != null) {
				UUID identityIdForRoleAssign = identityForRoleAssignContract.getIdentity();

				// requesting for myself is allowed
				if (currentId.toString().equals(identityIdForRoleAssign.toString())) {
					return super.save(dto, permission);
				}

				// if I am manager of the user I can assign roles
				List<IdmIdentityDto> managers = identityService.findAllManagers(identityForRoleAssignContract.getIdentity());

				if (managers.stream().anyMatch(identityDto -> identityDto.getId().toString().equals(currentId.toString()))) {
					return super.save(dto, permission);
				}
			}

			IdmRoleGuaranteeFilter filter = new IdmRoleGuaranteeFilter();
			filter.setGuarantee(currentId);
			List<IdmRoleGuaranteeDto> roleGuaranteeList = roleGuaranteeService.find(filter, null).getContent();

			List<IdmRoleGuaranteeRoleDto> roleGuaranteeRole = extrasUtils.getRoleGuaranteesByRole(currentId);

			// if user isn't guarantee, throw error directly
			String UNDEFINED_ROLE_NAME = "undefined";
			if (roleGuaranteeList.isEmpty() && roleGuaranteeRole.isEmpty()) {
				IdmRoleDto roleDto = roleService.get(dto.getRole());
				throw new ResultCodeException(ExtrasResultCode.IDENTITY_ROLE_CANNOT_BE_MODIFIED,
						ImmutableMap.of("role", roleDto != null ? roleDto.getName() : UNDEFINED_ROLE_NAME));
			}

			Optional<IdmRoleGuaranteeDto> any = roleGuaranteeList.stream().filter(guarantee -> guarantee.getRole().equals(dto.getRole())).findAny();
			Optional<IdmRoleGuaranteeRoleDto> anyRole = roleGuaranteeRole.stream().filter(guaranteeRole -> guaranteeRole.getRole().equals(dto.getRole())).findAny();

			// If I am direct guarantee of the specific role or guarantee by role
			if (any.isPresent() || anyRole.isPresent()) {
				return super.save(dto, permission);
			} else {
				IdmRoleDto roleDto = roleService.get(dto.getRole());
				throw new ResultCodeException(ExtrasResultCode.IDENTITY_ROLE_CANNOT_BE_MODIFIED,
						ImmutableMap.of("role", roleDto != null ? roleDto.getName() : UNDEFINED_ROLE_NAME));
			}
		}
		return super.save(dto, permission);
	}
}
