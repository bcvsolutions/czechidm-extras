package eu.bcvsolutions.idm.extras.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleGuaranteeFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleGuaranteeRoleFilter;
import eu.bcvsolutions.idm.core.api.script.ScriptEnabled;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeService;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;

/**
 * @author Roman Kucera
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

	public Predicate getIdentityPredicate(CriteriaBuilder builder) {
		IdmIdentityDto currentIdentity = securityService.getAuthentication().getCurrentIdentity();
		List<IdmRoleGuaranteeDto> roleGuarantees = getRoleGuaranteeIdentity(currentIdentity);
		List<IdmRoleGuaranteeRoleDto> roleGuaranteeRole = getRoleGuaranteeRole(currentIdentity);

		if (!roleGuarantees.isEmpty() || !roleGuaranteeRole.isEmpty()) {
			return builder.conjunction();
		} else {
			return null;
		}
	}

	public Set<String> getIdentityPermissions(Set<String> permissions, AuthorizationPolicy policy) {
		IdmIdentityDto currentIdentity = securityService.getAuthentication().getCurrentIdentity();
		List<IdmRoleGuaranteeDto> roleGuarantees = getRoleGuaranteeIdentity(currentIdentity);
		List<IdmRoleGuaranteeRoleDto> roleGuaranteeRole = getRoleGuaranteeRole(currentIdentity);

		if (!roleGuarantees.isEmpty() || !roleGuaranteeRole.isEmpty()) {
			permissions.addAll(policy.getPermissions());
			// set permission via FE
			return permissions;
		} else {
			// return permission from super class.
			return permissions;
		}
	}

	public List<IdmRoleGuaranteeRoleDto> getRoleGuaranteeRole(IdmIdentityDto currentIdentity) {
		// we need to check if user is guarantee based on some role
		List<IdmIdentityRoleDto> roles = identityRoleService.findAllByIdentity(currentIdentity.getId());
		List<IdmRoleGuaranteeRoleDto> roleGuaranteeRole = new ArrayList<>();
		roles.forEach(idmIdentityRoleDto -> {
			IdmRoleGuaranteeRoleFilter roleGuaranteeRoleFilter = new IdmRoleGuaranteeRoleFilter();
			roleGuaranteeRoleFilter.setGuaranteeRole(idmIdentityRoleDto.getRole());
			roleGuaranteeRole.addAll(roleGuaranteeRoleService.find(roleGuaranteeRoleFilter, null).getContent());
		});
		return roleGuaranteeRole;
	}

	public List<IdmRoleGuaranteeDto> getRoleGuaranteeIdentity(IdmIdentityDto currentIdentity) {
		// for check guaratee we need only one record
		IdmRoleGuaranteeFilter filter = new IdmRoleGuaranteeFilter();
		filter.setGuarantee(currentIdentity.getId());
		return roleGuaranteeService.find(filter, new PageRequest(0, 1)).getContent();
	}
}
