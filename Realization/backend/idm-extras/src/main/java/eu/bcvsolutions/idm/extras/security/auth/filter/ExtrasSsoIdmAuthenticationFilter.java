package eu.bcvsolutions.idm.extras.security.auth.filter;

import com.google.common.base.Strings;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.eav.service.impl.DefaultFormService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.core.security.auth.filter.SsoIdmAuthenticationFilter;
import eu.bcvsolutions.idm.core.security.exception.IdmAuthenticationException;
import java.text.MessageFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public abstract class ExtrasSsoIdmAuthenticationFilter extends SsoIdmAuthenticationFilter {
	private static final Logger LOG = LoggerFactory.getLogger(SsoIdmAuthenticationFilter.class);
	static final String PARAMETER_FIELDS = "fields";

	@Autowired//todo: In version 9.7.7 this line need delete
	private LookupService lookupService;
	@Autowired
	private DefaultFormService formService;

	@Override
	public boolean authorize(String token, HttpServletRequest request, HttpServletResponse response) {

		if (super.authorize(token, request, response)) {
			return true;
		}

		LOG.debug("Starting SSO filter authorization, value of the SSO header is: [{}]", token);
		if (Strings.isNullOrEmpty(token)) {
			return true;
		}
		// Remove suffix from the token - typically the domain
		String userName = removeUidSuffix(token);
		Set<UUID> uuids = new LinkedHashSet<>();

		List<String> fieldNames = getConfigurationService()
				.getValues(getConfigurationPropertyName(PARAMETER_FIELDS));

		for (String fieldName : fieldNames) {
			if (StringUtils.isEmpty(fieldName)) {
				return false;
			}
			Page<AbstractDto> owners;

			try {
				owners = formService.findOwners(IdmIdentityContract.class, fieldName, userName, null);
				for (AbstractDto owner : owners) {
					uuids.add(((IdmIdentityContractDto) owner).getIdentity());
				}
			} catch (IllegalArgumentException ignored) {
			}

			try {
				owners = formService.findOwners(IdmIdentity.class, fieldName, userName, null);
				for (AbstractDto owner : owners) {
					uuids.add(owner.getId());
				}
			} catch (IllegalArgumentException ignored) {
			}
		}

		if (!uuids.isEmpty()) {
			if (uuids.size() > 1) {
				throw new IdmAuthenticationException(MessageFormat.format(
						"The login [{0}] is not unique. SSO authentication is not passable.",
						userName));
			}
			IdmIdentityDto identity = (IdmIdentityDto) lookupService.lookupDto(IdmIdentityDto.class, uuids.iterator().next());
			if (null == identity) {
				return false;
			}
			return super.authorize(identity.getUsername(), request, response);
		}

		return false;
	}

	/**
	 * todo: In version 9.7.7 this line need delete
	 * @param token
	 * @return
	 */
	private String removeUidSuffix(String token) {
		List<String> suffixes = getConfigurationService().getValues(getConfigurationPropertyName(PARAMETER_UID_SUFFIXES));
		if (CollectionUtils.isEmpty(suffixes)) {
			return token;
		}
		for (String suffix : suffixes) {
			if (token.endsWith(suffix)) {
				return token.substring(0,  token.length() - suffix.length());
			}
		}
		return token;
	}


}
