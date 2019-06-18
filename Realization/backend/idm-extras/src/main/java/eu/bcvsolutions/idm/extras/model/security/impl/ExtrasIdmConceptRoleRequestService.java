package eu.bcvsolutions.idm.extras.model.security.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleGuaranteeFilter;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.utils.DtoUtils;
import eu.bcvsolutions.idm.core.model.entity.IdmConceptRoleRequest_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import eu.bcvsolutions.idm.core.model.repository.IdmAutomaticRoleRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmConceptRoleRequestRepository;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmConceptRoleRequestService;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.workflow.service.WorkflowProcessInstanceService;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * Overridden service for concept role request.
 * Throw error when currently logged user try to save concept with roles for which is not guarantee
 *
 * @author Ondrej Kopr <kopr@xyxy.cz>
 *
 */
@Primary
@Service
public class ExtrasIdmConceptRoleRequestService extends DefaultIdmConceptRoleRequestService {

	private final IdmRoleGuaranteeService roleGuaranteeService;
	private final SecurityService securityService;
	private final IdmRoleService roleService;
	private final IdmIdentityService identityService;
	private final IdmIdentityRoleService identityRoleService;

	private final String UNDEFINED_ROLE_NAME = "undefined";
	
	@Autowired
	public ExtrasIdmConceptRoleRequestService(IdmConceptRoleRequestRepository repository,
											  WorkflowProcessInstanceService workflowProcessInstanceService, LookupService lookupService,
											  IdmAutomaticRoleRepository automaticRoleRepository,
											  IdmRoleGuaranteeService roleGuaranteeService,
											  SecurityService securityService, IdmRoleService roleService, IdmIdentityService identityService, IdmIdentityRoleService identityRoleService) {
		super(repository, workflowProcessInstanceService, lookupService, automaticRoleRepository);
		//
		this.roleGuaranteeService = roleGuaranteeService;
		this.securityService = securityService;
		this.roleService = roleService;
		this.identityService = identityService;
		this.identityRoleService = identityRoleService;
	}

	@Override
	public IdmConceptRoleRequestDto save(IdmConceptRoleRequestDto dto, BasePermission... permission) {

		// for administrator doesnt work the behavior
		if (securityService.isAdmin()) {
			return super.save(dto, permission);
		}

		UUID currentId = securityService.getCurrentId();
		// this isn't probably possible, but for sure
		if (currentId != null) {

			IdmIdentityRoleDto identityRole = DtoUtils.getEmbedded(dto, IdmConceptRoleRequest_.identityRole, IdmIdentityRoleDto.class, null);
			if (identityRole == null) {
				identityRole = identityRoleService.get(dto.getIdentityRole());
			}

			IdmIdentityContractDto identityContract = DtoUtils.getEmbedded(identityRole, IdmIdentityRole_.identityContract, IdmIdentityContractDto.class);
			IdmIdentityDto identity = DtoUtils.getEmbedded(identityContract, IdmIdentityContract_.identity, IdmIdentityDto.class);

			List<IdmIdentityDto> managers = identityService.findAllManagers(identity.getId());

			if (managers.stream().anyMatch(identityDto -> identityDto.getId().toString().equals(currentId.toString()))) {
				return super.save(dto, permission);
			}

			IdmRoleGuaranteeFilter filter = new IdmRoleGuaranteeFilter();
			filter.setGuarantee(currentId);
			List<IdmRoleGuaranteeDto> roleGuaranteeList = roleGuaranteeService.find(filter, null).getContent();

			// if user isn't guarantee, throw error directly
			if (roleGuaranteeList.isEmpty()) {
				IdmRoleDto roleDto = roleService.get(dto.getRole());
				throw new ResultCodeException(ExtrasResultCode.IDENTITY_ROLE_CANNOT_BE_MODIFIED,
						ImmutableMap.of("role", roleDto != null ? roleDto.getName() : UNDEFINED_ROLE_NAME));
			}

			Optional<IdmRoleGuaranteeDto> any = roleGuaranteeList.stream().filter(guarant -> {
				return guarant.getRole().equals(dto.getRole());
			}).findAny();

			if (any.isPresent()) {
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
