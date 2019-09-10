package eu.bcvsolutions.idm.extras.security.evaluator.identity;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleGuaranteeFilter;
import eu.bcvsolutions.idm.core.api.entity.AbstractEntity_;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractAuthorizationEvaluator;
import eu.bcvsolutions.idm.extras.util.ExtrasUtils;

/**
 * Evaluator will add the option to display all IdmIdentityRole to user who is guarantee at least for one role
 *
 * @author Ondrej Kopr <kopr@xyxy.cz>
 * @author Roman Kucera
 */

@Component
@Description("Access for all identity role when identity is a guarantee at least one role.")
public class IdentityRoleAccessForRoleGuaranteeEvaluator extends AbstractAuthorizationEvaluator<IdmIdentityRole> {

	private final SecurityService securityService;
	private final IdmRoleGuaranteeService roleGuaranteeService;
	private final ExtrasUtils extrasUtils;

	@Autowired
	public IdentityRoleAccessForRoleGuaranteeEvaluator(IdmRoleGuaranteeService roleGuaranteeService,
													   SecurityService securityService, ExtrasUtils extrasUtils) {
		Assert.notNull(roleGuaranteeService);
		Assert.notNull(securityService);
		//
		this.roleGuaranteeService = roleGuaranteeService;
		this.securityService = securityService;
		this.extrasUtils = extrasUtils;
	}

	@Override
	public Predicate getPredicate(Root<IdmIdentityRole> root, CriteriaQuery<?> query, CriteriaBuilder builder,
								  AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasAuthority(securityService.getCurrentId(), policy, permission)) {
			return null;
		}
		IdmIdentityDto currentIdentity = securityService.getAuthentication().getCurrentIdentity();
		IdmRoleGuaranteeFilter filter = new IdmRoleGuaranteeFilter();
		UUID currentIdentityId = currentIdentity.getId();
		filter.setGuarantee(currentIdentityId);
		// for check identity role we want all role guarantee
		List<IdmRoleGuaranteeDto> roleGuarantees = roleGuaranteeService.find(filter, null).getContent();
		// Get role guarantees by role
		List<IdmRoleGuaranteeRoleDto> roleGuaranteeRole = extrasUtils.getRoleGuaranteesByRole(currentIdentityId);

		if (!roleGuarantees.isEmpty() || !roleGuaranteeRole.isEmpty()) {
			Set<UUID> roleIds = roleGuarantees.stream().map(IdmRoleGuaranteeDto::getRole).collect(Collectors.toSet());
			roleIds.addAll(roleGuaranteeRole.stream().map(IdmRoleGuaranteeRoleDto::getRole).collect(Collectors.toSet()));
			return root.get(IdmIdentityRole_.role).get(AbstractEntity_.id).in(roleIds);
		} else {
			return null;
		}
	}

	@Override
	public Set<String> getPermissions(IdmIdentityRole entity, AuthorizationPolicy policy) {
		Set<String> permissions = super.getPermissions(entity, policy);
		return extrasUtils.getGuaranteePermissions(permissions, policy);
	}
}
