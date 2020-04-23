package eu.bcvsolutions.idm.extras.security.evaluator.role;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFilter;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.eav.api.domain.BaseFaceType;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.model.entity.IdmForestIndexEntity_;
import eu.bcvsolutions.idm.core.model.entity.IdmRole;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleCatalogue;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleCatalogueRole;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleCatalogueRole_;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleCatalogue_;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractAuthorizationEvaluator;

/**
 * Evaluator add permission for all roles that is in at given role catalogue.
 *
 * @author Ondrej Kopr
 *
 */

@Component
@Description("Permissions for roler by role catalogue.")
public class RoleByRoleCatalogueEvaluator extends AbstractAuthorizationEvaluator<IdmRole> {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RoleByRoleCatalogueEvaluator.class);
	public static final String PARAMETER_ROLE_CATALOGUE = "role-catalogue";
	public static final String EVALUATOR_NAME = "role-by-role-catalogue-evaluator";

	@Autowired
	private IdmRoleCatalogueService roleCatalogueService;
	@Autowired
	private IdmRoleService roleService;

	@Override
	public Predicate getPredicate(Root<IdmRole> root, CriteriaQuery<?> query, CriteriaBuilder builder,
			AuthorizationPolicy policy, BasePermission... permission) {

		// check before apply evaluator
		UUID uuid = getUuid(policy);
		if (uuid == null) {
			return null;
		}

		IdmRoleCatalogueDto roleCatalogueDto = roleCatalogueService.get(uuid);
		if (roleCatalogueDto == null) {
			return null;
		}
		
		// subquery to role catalogue role
		Subquery<IdmRoleCatalogueRole> subquery = query.subquery(IdmRoleCatalogueRole.class);
		Root<IdmRoleCatalogueRole> subRoot = subquery.from(IdmRoleCatalogueRole.class);
		subquery.select(subRoot);

		

		Subquery<IdmRoleCatalogue> subqueryRoleCatalogue = query.subquery(IdmRoleCatalogue.class);
		Root<IdmRoleCatalogue> subRoleCatalogueRoot = subqueryRoleCatalogue.from(IdmRoleCatalogue.class);
		subqueryRoleCatalogue.select(subRoleCatalogueRoot);
		subqueryRoleCatalogue.where(
				builder.and(
						builder.equal(subRoleCatalogueRoot.get(IdmRoleCatalogue_.id), roleCatalogueDto.getId()),
						builder.between(
								subRoot.get(IdmRoleCatalogueRole_.roleCatalogue).get(IdmRoleCatalogue_.forestIndex).get(IdmForestIndexEntity_.lft), 
								subRoleCatalogueRoot.get(IdmRoleCatalogue_.forestIndex).get(IdmForestIndexEntity_.lft),
								subRoleCatalogueRoot.get(IdmRoleCatalogue_.forestIndex).get(IdmForestIndexEntity_.rgt)
                		)
				));				

		subquery.where(
                builder.and(
                		builder.equal(subRoot.get(IdmRoleCatalogueRole_.role), root), // correlation attr
                		builder.exists(subqueryRoleCatalogue)
                		)
                );

		return builder.exists(subquery);
	}
	
	@Override
	public Set<String> getPermissions(IdmRole authorizable, AuthorizationPolicy policy) {
		Set<String> permissions = super.getPermissions(authorizable, policy);
		UUID uuid = getUuid(policy);
		if (uuid == null) {
			return permissions;
		}
		
		IdmRoleCatalogueDto roleCatalogueDto = roleCatalogueService.get(uuid);
		if (roleCatalogueDto == null) {
			return permissions;
		}

		// we try found role by id and role catalogue
		IdmRoleFilter filter = new IdmRoleFilter();
		filter.setId(authorizable.getId());
		filter.setRoleCatalogueId(roleCatalogueDto.getId());
		List<IdmRoleDto> roles = roleService.find(filter, null).getContent();
		
		if (!roles.isEmpty()) {
			permissions.addAll(policy.getPermissions());
		}

		return permissions;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		return Lists.newArrayList(
				new IdmFormAttributeDto(PARAMETER_ROLE_CATALOGUE, PARAMETER_ROLE_CATALOGUE, PersistentType.UUID, BaseFaceType.ROLE_CATALOGUE_SELECT)
				);
	}

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}
	
	@Override
	public List<String> getPropertyNames() {
		List<String> parameters = super.getPropertyNames();
		parameters.add(PARAMETER_ROLE_CATALOGUE);
		return parameters;
	}

	private UUID getUuid(AuthorizationPolicy policy) {
		try {
			return policy.getEvaluatorProperties().getUuid(PARAMETER_ROLE_CATALOGUE);
		} catch (ClassCastException ex) {
			LOG.warn("Wrong uuid for authorization evaluator - skipping.", ex);
			return null;
		}
	}
}
