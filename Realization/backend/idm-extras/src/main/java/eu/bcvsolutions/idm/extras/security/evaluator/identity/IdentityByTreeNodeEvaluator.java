package eu.bcvsolutions.idm.extras.security.evaluator.identity;

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

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityFilter;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.eav.api.domain.BaseFaceType;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.model.entity.IdmContractPosition;
import eu.bcvsolutions.idm.core.model.entity.IdmForestIndexEntity_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract_;
import eu.bcvsolutions.idm.core.model.entity.IdmTreeNode;
import eu.bcvsolutions.idm.core.model.entity.IdmTreeNode_;
import eu.bcvsolutions.idm.core.security.api.domain.AuthorizationPolicy;
import eu.bcvsolutions.idm.core.security.api.domain.BasePermission;
import eu.bcvsolutions.idm.core.security.evaluator.AbstractAuthorizationEvaluator;

/**
 * Evaluator add permission for all identities that has contract on given treenode or below.
 * Evaluator doesn't check organization unit for contract position ({@link IdmContractPosition})
 *
 * @author Ondrej Kopr
 *
 */

@Component
@Description("Permissions for identities by tree node.")
public class IdentityByTreeNodeEvaluator extends AbstractAuthorizationEvaluator<IdmIdentity> {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(IdentityByTreeNodeEvaluator.class);
	public static final String PARAMETER_TREE_NODE = "tree-node";
	public static final String EVALUATOR_NAME = "identity-by-tree-node-evaluator";

	@Autowired
	private IdmTreeNodeService treeNodeService;
	@Autowired
	private IdmIdentityService identityService;
	
	@Override
	public Predicate getPredicate(Root<IdmIdentity> root, CriteriaQuery<?> query, CriteriaBuilder builder,
			AuthorizationPolicy policy, BasePermission... permission) {

		// check before apply evaluator
		UUID uuid = getUuid(policy);
		if (uuid == null) {
			return null;
		}

		IdmTreeNodeDto treeNodeDto = treeNodeService.get(uuid);
		if (treeNodeDto == null) {
			return null;
		}
		
		// subquery to treenode (contract position -> treenode)
		Subquery<IdmIdentityContract> subquery = query.subquery(IdmIdentityContract.class);
		Root<IdmIdentityContract> subRoot = subquery.from(IdmIdentityContract.class);
		subquery.select(subRoot);
		
		Subquery<IdmTreeNode> subqueryTreeNode = query.subquery(IdmTreeNode.class);
		Root<IdmTreeNode> subqueryTreeNodeRoot = subqueryTreeNode.from(IdmTreeNode.class);
		subqueryTreeNode.select(subqueryTreeNodeRoot);
		subqueryTreeNode.where(
				builder.and(
						builder.equal(subqueryTreeNodeRoot.get(IdmTreeNode_.id), treeNodeDto.getId()),
						builder.between(
                				subRoot.get(IdmIdentityContract_.workPosition).get(IdmTreeNode_.forestIndex).get(IdmForestIndexEntity_.lft), 
                				subqueryTreeNodeRoot.get(IdmTreeNode_.forestIndex).get(IdmForestIndexEntity_.lft),
                				subqueryTreeNodeRoot.get(IdmTreeNode_.forestIndex).get(IdmForestIndexEntity_.rgt)
                		)
				));				

		subquery.where(
                builder.and(
                		builder.equal(subRoot.get(IdmIdentityContract_.identity), root), // correlation attr
                		builder.exists(subqueryTreeNode)
                		)
                );

		return builder.exists(subquery);
	}
	
	@Override
	public Set<String> getPermissions(IdmIdentity authorizable, AuthorizationPolicy policy) {
		Set<String> permissions = super.getPermissions(authorizable, policy);
		UUID uuid = getUuid(policy);
		if (uuid == null) {
			return permissions;
		}
		
		IdmTreeNodeDto treeNodeDto = treeNodeService.get(uuid);
		if (treeNodeDto == null) {
			return permissions;
		}
		
		// we try found identity by tree node, identity id and recursively
		IdmIdentityFilter filter = new IdmIdentityFilter();
		filter.setId(authorizable.getId());
		filter.setTreeNode(treeNodeDto.getId());
		filter.setRecursively(true);
		List<IdmIdentityDto> founded = identityService.find(filter, null).getContent();
		
		if (!founded.isEmpty()) {
			permissions.addAll(policy.getPermissions());
		}

		return permissions;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		return Lists.newArrayList(
				new IdmFormAttributeDto(PARAMETER_TREE_NODE, PARAMETER_TREE_NODE, PersistentType.UUID, BaseFaceType.TREE_NODE_SELECT)
				);
	}

	@Override
	public List<String> getPropertyNames() {
		List<String> parameters = super.getPropertyNames();
		parameters.add(PARAMETER_TREE_NODE);
		return parameters;
	}

	@Override
	public String getName() {
		return EVALUATOR_NAME;
	}

	/**
	 * Get uuid for tree node parent from authorization policy
	 *
	 * @param policy
	 * @return
	 */
	private UUID getUuid(AuthorizationPolicy policy) {
		try {
			return policy.getEvaluatorProperties().getUuid(PARAMETER_TREE_NODE);
		} catch (ClassCastException ex) {
			LOG.error("Wrong uuid for authorization evaluator - skipping.", ex);
			return null;
		}
	}
}
