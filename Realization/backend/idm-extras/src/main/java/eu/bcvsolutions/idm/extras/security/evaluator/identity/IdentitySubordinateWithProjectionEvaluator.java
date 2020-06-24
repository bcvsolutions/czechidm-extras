package eu.bcvsolutions.idm.extras.security.evaluator.identity;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractAuthorizationEvaluator;
import eu.bcvsolutions.idm.core.security.evaluator.identity.IdentityByFormProjectionEvaluator;
import eu.bcvsolutions.idm.core.security.evaluator.identity.SubordinatesEvaluator;

@Component
@Description("Access to all  subordinates, which have certain projection")
public class IdentitySubordinateWithProjectionEvaluator  extends AbstractAuthorizationEvaluator<IdmIdentity> {

	public static final String NAME = "subordinate-and-projection-evaluator";

	private final SecurityService securityService;
	private final IdentityByFormProjectionEvaluator identityByFormProjectionEvaluator;
	private final SubordinatesEvaluator subordinatesEvaluator;


	@Autowired
	public IdentitySubordinateWithProjectionEvaluator(SecurityService securityService, IdentityByFormProjectionEvaluator identityByFormProjectionEvaluator,
													  SubordinatesEvaluator subordinatesEvaluator) {
		this.securityService = securityService;
		this.identityByFormProjectionEvaluator = identityByFormProjectionEvaluator;
		this.subordinatesEvaluator = subordinatesEvaluator;
	}

	@Override
	public Predicate getPredicate(Root<IdmIdentity> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		Predicate subordinatesEvaluatorPredicate = subordinatesEvaluator.getPredicate(root, query, builder, policy, permission);
		Predicate identityByFormProjectionEvaluatorPredicate = identityByFormProjectionEvaluator.getPredicate(root, query, builder, policy, permission);

		if (subordinatesEvaluatorPredicate == null || identityByFormProjectionEvaluatorPredicate == null) {
			return null;
		}

		return builder.and(
				subordinatesEvaluatorPredicate, identityByFormProjectionEvaluatorPredicate
		);

	}

	@Override
	public Set<String> getPermissions(IdmIdentity authorizable, AuthorizationPolicy policy) {
		Set<String> permissions = super.getPermissions(authorizable, policy);
		if (authorizable == null || !securityService.isAuthenticated()) {
			return permissions;
		}
		Set<String> permissions1 = subordinatesEvaluator.getPermissions(authorizable, policy);
		Set<String> permissions2 = identityByFormProjectionEvaluator.getPermissions(authorizable, policy);
		//
		Collection<String> intersection = CollectionUtils.intersection(permissions1, permissions2);
		permissions.addAll(intersection);
		return permissions;
	}

	@Override
	public List<String> getPropertyNames() {
		return Stream.concat(
				subordinatesEvaluator.getPropertyNames().stream(),
				identityByFormProjectionEvaluator.getPropertyNames().stream()
		).collect(Collectors.toList());
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		return Stream.concat(
				subordinatesEvaluator.getFormAttributes().stream(),
				identityByFormProjectionEvaluator.getFormAttributes().stream()
		).collect(Collectors.toList());
	}

	@Override
	public String getName() {
		return NAME;
	}
}
