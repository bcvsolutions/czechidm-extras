package eu.bcvsolutions.idm.extras.security.evaluator.identity;

import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractAuthorizationEvaluator;
import eu.bcvsolutions.idm.extras.util.ExtrasUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.Set;

/**
 * Evaluator will add the option to see all users if user is guarantee at least for one role
 * 
 * @author Ondrej Kopr <kopr@xyxy.cz>
 * @author Roman Kucera
 *
 */

@Component
@Description("Access for all user when identity is a guarantee at least one role.")
public class IdentityAccessForRoleGuaranteeEvaluator extends AbstractAuthorizationEvaluator<IdmIdentity> {

	private final SecurityService securityService;
	private final ExtrasUtils extrasUtils;

	@Autowired
	public IdentityAccessForRoleGuaranteeEvaluator(SecurityService securityService, ExtrasUtils extrasUtils) {
		Assert.notNull(securityService);
		//
		this.securityService = securityService;
		this.extrasUtils = extrasUtils;
	}
	
	@Override
	public Predicate getPredicate(Root<IdmIdentity> root, CriteriaQuery<?> query, CriteriaBuilder builder,
								  AuthorizationPolicy policy, BasePermission... permission) {
		if (!hasAuthority(securityService.getCurrentId(), policy, permission)) {
			return null;
		}
		return extrasUtils.getGuaranteePredicate(builder);
	}
	
	@Override
	public Set<String> getPermissions(IdmIdentity entity, AuthorizationPolicy policy) {
		Set<String> permissions = super.getPermissions(entity, policy);
		return extrasUtils.getGuaranteePermissions(permissions, policy);
	}
}
