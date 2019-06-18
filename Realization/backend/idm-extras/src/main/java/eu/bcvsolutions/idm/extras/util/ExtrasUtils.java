package eu.bcvsolutions.idm.extras.util;

import java.util.List;
import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleGuaranteeFilter;
import eu.bcvsolutions.idm.core.api.script.ScriptEnabled;
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

	public Predicate getIdentityPredicate(CriteriaBuilder builder) {
		IdmIdentityDto currentIdentity = securityService.getAuthentication().getCurrentIdentity();
		IdmRoleGuaranteeFilter filter = new IdmRoleGuaranteeFilter();
		filter.setGuarantee(currentIdentity.getId());
		// for check guaratee we need only one record
		List<IdmRoleGuaranteeDto> roleGuarantees = roleGuaranteeService.find(filter, new PageRequest(0, 1)).getContent();
		if (!roleGuarantees.isEmpty()) {
			return builder.conjunction();
		} else {
			return null;
		}
	}

	public Set<String> getIdentityPermissions(Set<String> permissions, AuthorizationPolicy policy) {
		IdmIdentityDto currentIdentity = securityService.getAuthentication().getCurrentIdentity();
		IdmRoleGuaranteeFilter filter = new IdmRoleGuaranteeFilter();
		filter.setGuarantee(currentIdentity.getId());
		// for check guaratee we need only one record
		List<IdmRoleGuaranteeDto> roleGuarantees = roleGuaranteeService.find(filter, new PageRequest(0, 1)).getContent();
		if (!roleGuarantees.isEmpty()) {
			permissions.addAll(policy.getPermissions());
			// set permission via FE
			return permissions;
		} else {
			// return permission from super class.
			return permissions;
		}
	}
}
