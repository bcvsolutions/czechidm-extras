package eu.bcvsolutions.idm.extras.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.dto.EntityAccountDto;
import eu.bcvsolutions.idm.acc.dto.filter.EntityAccountFilter;
import eu.bcvsolutions.idm.acc.service.api.EntityAccountService;
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
import eu.bcvsolutions.idm.extras.config.domain.ExtrasConfiguration;
import org.springframework.util.StringUtils;

/**
 * Ultra mega awesome extra util for all projects by Roman Kucera
 *
 * @author Roman Kucera
 * @author Ondrej Kopr
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
	@Autowired
	private ExtrasConfiguration extrasConfiguration;

	/*
	 * All Czech available titles. This is only default value, real value is stored in configuration. See ExtrasConfiguration.
	 */
	public static final List<String> TITLES_AFTER = Lists.newArrayList("Ph.D.", "Th.D.", "CSc.", "DrSc.", "dr. h. c.",
			"DiS.", "MBA");
	public static final List<String> TITLES_BEFORE = Lists.newArrayList("Bc.", "BcA.", "Ing.", "Ing. arch.", "MUDr.",
			"MVDr.", "MgA.", "Mgr.", "JUDr.", "PhDr.", "RNDr.", "PharmDr.", "ThLic.", "ThDr.", "prof.", "doc.",
			"PaedDr.", "Dr.", "PhMr.");

	public Predicate getGuaranteePredicate(CriteriaBuilder builder) {
		IdmIdentityDto currentIdentity = securityService.getAuthentication().getCurrentIdentity();
		List<IdmRoleGuaranteeDto> roleGuarantees = getDirectRoleGuarantees(currentIdentity);
		List<IdmRoleGuaranteeRoleDto> roleGuaranteeRole = getRoleGuaranteesByRole(currentIdentity.getId());

		if (!roleGuarantees.isEmpty() || !roleGuaranteeRole.isEmpty()) {
			return builder.conjunction();
		} else {
			return null;
		}
	}

	public Set<String> getGuaranteePermissions(Set<String> permissions, AuthorizationPolicy policy) {
		IdmIdentityDto currentIdentity = securityService.getAuthentication().getCurrentIdentity();
		List<IdmRoleGuaranteeDto> roleGuarantees = getDirectRoleGuarantees(currentIdentity);
		List<IdmRoleGuaranteeRoleDto> roleGuaranteeRole = getRoleGuaranteesByRole(currentIdentity.getId());

		if (!roleGuarantees.isEmpty() || !roleGuaranteeRole.isEmpty()) {
			permissions.addAll(policy.getPermissions());
			// set permission via FE
			return permissions;
		} else {
			// return permission from super class.
			return permissions;
		}
	}

	public List<IdmRoleGuaranteeRoleDto> getRoleGuaranteesByRole(UUID currentId) {
		// we need to check if user is guarantee based on some role
		List<IdmIdentityRoleDto> roles = identityRoleService.findValidRoles(currentId, null).getContent();
		List<IdmRoleGuaranteeRoleDto> roleGuaranteeRole = new ArrayList<>();
		roles.forEach(idmIdentityRoleDto -> {
			IdmRoleGuaranteeRoleFilter roleGuaranteeRoleFilter = new IdmRoleGuaranteeRoleFilter();
			roleGuaranteeRoleFilter.setGuaranteeRole(idmIdentityRoleDto.getRole());
			roleGuaranteeRole.addAll(roleGuaranteeRoleService.find(roleGuaranteeRoleFilter, null).getContent());
		});
		return roleGuaranteeRole;
	}

	public List<IdmRoleGuaranteeDto> getDirectRoleGuarantees(IdmIdentityDto currentIdentity) {
		// for check guarantee we need only one record
		IdmRoleGuaranteeFilter filter = new IdmRoleGuaranteeFilter();
		filter.setGuarantee(currentIdentity.getId());
		return roleGuaranteeService.find(filter, new PageRequest(0, 1)).getContent();
	}

	/**
	 * Return titles that must by behind name
	 *
	 * @param value
	 * @return
	 */
	public String getTitlesAfter(String value) {
		return getTitles(value, extrasConfiguration.getTitlesAfter());
	}

	/**
	 * Return titles that must by before name
	 *
	 * @param value
	 * @return
	 */
	public String getTitlesBefore(String value) {
		return getTitles(value, extrasConfiguration.getTitlesBefore());
	}

	/**
	 * Private method for same behavior for titles after and before
	 *
	 * @param value
	 * @param dictonary
	 * @return
	 */
	private String getTitles(String value, List<String> dictonary) {
		if (StringUtils.isEmpty(value)) {
			return null;
		}
		List<String> result = new ArrayList<String>();

		String[] titles = value.split(" ");
		for (String title : titles) {
			final String finalTitle = title.trim().toLowerCase();

			String exits = dictonary.stream()
					.map(String::trim)
					.map(String::toLowerCase).filter(t -> t.equals(finalTitle))
					.findFirst()
					.orElse(null);
			if (exits != null) {
				result.add(title);
			}
		}

		return String.join(", ", result);
	}

	/**
	 * Find entity by account
	 *
	 * @param accountId
	 * @return
	 */
	public UUID getEntityByAccount(UUID accountId, EntityAccountFilter filter, EntityAccountService service) {
		filter.setAccountId(accountId);
		filter.setOwnership(Boolean.TRUE);
		List<EntityAccountDto> entityAccounts = service
				.find(filter, new PageRequest(0, 1)).getContent();
		if (entityAccounts.isEmpty()) {
			return null;
		} else {
			return entityAccounts.get(0).getEntity();
		}
	}
}
