package eu.bcvsolutions.idm.extras.security.evaluator.identity;

import java.util.List;
import java.util.Set;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleGuaranteeFilter;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractAuthorizationEvaluator;

/**
 * Evaluator prida moznost zobrazeni vsech uzivatelu identite, ktera je garantem
 * alespon u jedne role
 * 
 * @author Ondrej Kopr <kopr@xyxy.cz>
 *
 */

@Component
@Description("Access for all user when identity is a guarantee at least one role.")
public class IdentityAccessForRoleGuaranteeEvaluator extends AbstractAuthorizationEvaluator<IdmIdentity> {

	private final SecurityService securityService;
	private final IdmRoleGuaranteeService roleGuaranteeService;
	
	@Autowired
	public IdentityAccessForRoleGuaranteeEvaluator(IdmRoleGuaranteeService roleGuaranteeService,
												   SecurityService securityService) {
		Assert.notNull(roleGuaranteeService);
		Assert.notNull(securityService);
		//
		this.roleGuaranteeService = roleGuaranteeService;
		this.securityService = securityService;
	}
	
	@Override
	public Predicate getPredicate(Root<IdmIdentity> root, CriteriaQuery<?> query, CriteriaBuilder builder,
								  AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasAuthority(securityService.getCurrentId(), policy, permission)) {
			return null;
		}
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
	
	@Override
	public Set<String> getPermissions(IdmIdentity entity, AuthorizationPolicy policy) {
		Set<String> permissions = super.getPermissions(entity, policy);
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
