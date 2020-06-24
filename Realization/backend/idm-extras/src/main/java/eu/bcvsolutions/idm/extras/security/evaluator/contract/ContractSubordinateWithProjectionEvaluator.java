package eu.bcvsolutions.idm.extras.security.evaluator.contract;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract_;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.api.service.SecurityService;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractAuthorizationEvaluator;
import eu.bcvsolutions.idm.core.security.evaluator.identity.IdentityByFormProjectionEvaluator;
import eu.bcvsolutions.idm.core.security.evaluator.identity.SubordinateContractEvaluator;

@Component
@Description("Access to contracts of all subordinates, which have certain projection")
public class ContractSubordinateWithProjectionEvaluator extends AbstractAuthorizationEvaluator<IdmIdentityContract> {

	public static final String NAME = "contract-subordinate-and-projection-evaluator";

	private final SecurityService securityService;
	private final SubordinateContractEvaluator subordinateContractEvaluator;
	private final IdentityByFormProjectionEvaluator formProjectionEvaluator;

	@Autowired
	public ContractSubordinateWithProjectionEvaluator(SecurityService securityService, SubordinateContractEvaluator subordinateContractEvaluator,
													  IdentityByFormProjectionEvaluator formProjectionEvaluator) {
		this.securityService = securityService;
		this.subordinateContractEvaluator = subordinateContractEvaluator;
		this.formProjectionEvaluator = formProjectionEvaluator;
	}

	@Override
	public Predicate getPredicate(Root<IdmIdentityContract> root, CriteriaQuery<?> query, CriteriaBuilder builder, AuthorizationPolicy policy, BasePermission... permission) {
		Subquery<IdmIdentity> projectionSubquery = query.subquery(IdmIdentity.class);
		Root<IdmIdentity> subroot = projectionSubquery.from(IdmIdentity.class);
		projectionSubquery.select(subroot);

		Predicate projectionEvaluatorPredicate = formProjectionEvaluator.getPredicate(subroot, query, builder, policy, permission);
		Predicate subordinateContractEvaluatorPredicate = subordinateContractEvaluator.getPredicate(root, query, builder, policy, permission);

		if (projectionEvaluatorPredicate == null || subordinateContractEvaluatorPredicate == null) {
			return null;
		}

		projectionSubquery.where(
				builder.and(
						projectionEvaluatorPredicate,
						builder.equal(subroot, root.get(IdmIdentityContract_.IDENTITY))
				)
		);

		return
				builder.and(
						subordinateContractEvaluatorPredicate,
						builder.exists(projectionSubquery)
				);
	}

	@Override
	public Set<String> getPermissions(IdmIdentityContract authorizable, AuthorizationPolicy policy) {
		Set<String> permissions = super.getPermissions(authorizable, policy);
		if (authorizable == null || !securityService.isAuthenticated()) {
			return permissions;
		}

		Set<String> permissions1 = subordinateContractEvaluator.getPermissions(authorizable, policy);
		Set<String> permissions2 = formProjectionEvaluator.getPermissions(authorizable.getIdentity(), policy);
		Collection<String> intersection = CollectionUtils.intersection(permissions1, permissions2);

		permissions.addAll(intersection);
		return permissions;
	}

	@Override
	public List<String> getPropertyNames() {
		return Stream.concat(
				subordinateContractEvaluator.getPropertyNames().stream(),
				formProjectionEvaluator.getPropertyNames().stream()
		).collect(Collectors.toList());
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		return Stream.concat(
				subordinateContractEvaluator.getFormAttributes().stream(),
				formProjectionEvaluator.getFormAttributes().stream()
		).collect(Collectors.toList());
	}

	@Override
	public String getName() {
		return NAME;
	}
}
