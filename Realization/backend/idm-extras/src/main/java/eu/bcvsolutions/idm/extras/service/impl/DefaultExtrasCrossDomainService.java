package eu.bcvsolutions.idm.extras.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.identityconnectors.common.security.GuardedString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.acc.domain.AccResultCode;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.exception.ProvisioningException;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.extras.service.api.ExtrasCrossDomainService;
import eu.bcvsolutions.idm.extras.util.GuardedStringAccessor;
import eu.bcvsolutions.idm.ic.api.IcAttribute;
import eu.bcvsolutions.idm.ic.api.IcConfigurationProperty;
import eu.bcvsolutions.idm.ic.api.IcConnectorConfiguration;
import eu.bcvsolutions.idm.ic.api.IcObjectClass;
import eu.bcvsolutions.idm.ic.filter.api.IcFilter;
import eu.bcvsolutions.idm.ic.filter.impl.IcEqualsFilter;
import eu.bcvsolutions.idm.ic.filter.impl.IcOrFilter;
import eu.bcvsolutions.idm.ic.impl.IcAttributeImpl;
import eu.bcvsolutions.idm.ic.impl.IcConfigurationPropertyImpl;
import eu.bcvsolutions.idm.ic.impl.IcObjectClassImpl;
import eu.bcvsolutions.idm.ic.service.api.IcConnectorFacade;

/**
 * @author Roman Kucera
 */
@Service
public class DefaultExtrasCrossDomainService implements ExtrasCrossDomainService {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory
			.getLogger(DefaultExtrasCrossDomainService.class);

	@Autowired
	private SysSystemService systemService;
	@Autowired
	private IcConnectorFacade connectorFacade;

	@Override
	public IcConnectorConfiguration getConfiguration(IcConnectorConfiguration connectorConfig, List<UUID> adSystems) {
		LOG.info("System is AD cross domain - get credentials from all cross ad systems and put them into config for connector");
		List<GuardedString> creds = new ArrayList<>();

		String ADDITIONAL_CREDS_NAME = "additionalCreds";
		// Remove additionalCreds from config if its there, because we dont care about values on FE
		connectorConfig.getConfigurationProperties().getProperties().stream()
				.filter(icConfigurationProperty -> icConfigurationProperty.getName().equals(ADDITIONAL_CREDS_NAME))
				.findFirst()
				.ifPresent(icConfigurationProperty -> connectorConfig.getConfigurationProperties().getProperties().remove(icConfigurationProperty));

		adSystems.forEach(systemId -> {
			SysSystemDto sysSystemDto = systemService.get(systemId);
			IcConnectorConfiguration config = systemService.getConnectorConfiguration(sysSystemDto);
			AtomicReference<String> user = new AtomicReference<>();
			AtomicReference<List<String>> containers = new AtomicReference<>();
			AtomicReference<eu.bcvsolutions.idm.core.security.api.domain.GuardedString> password = new AtomicReference<>();
			config.getConfigurationProperties().getProperties().forEach(icConfigurationProperty -> {
				if (icConfigurationProperty.getName().equals("principal")) {
					user.set(String.valueOf(icConfigurationProperty.getValue()));
				}
				if (icConfigurationProperty.getName().equals("credentials")) {
					GuardedStringAccessor accessor = new GuardedStringAccessor();
					((GuardedString) icConfigurationProperty.getValue()).access(accessor);
					password.set(new eu.bcvsolutions.idm.core.security.api.domain.GuardedString(new String(accessor.getArray())));
					accessor.clearArray();
				}
				if (icConfigurationProperty.getName().equals("baseContextsToSynchronize")) {
					containers.set(Arrays.asList((String[]) icConfigurationProperty.getValue()));
				}
			});

			if (user.get() != null && password.get() != null && containers.get() != null && !containers.get().isEmpty()) {
				containers.get().forEach(container -> {
					creds.add(new GuardedString((container + "\\" + user + "\\" + password.get().asString()).toCharArray()));
				});
			}

		});

		IcConfigurationProperty cred = new IcConfigurationPropertyImpl(ADDITIONAL_CREDS_NAME, creds.toArray(new GuardedString[0]));
		connectorConfig.getConfigurationProperties().getProperties().add(cred);

		return connectorConfig;
	}

	/**
	 * Get groups from all system for user
	 *
	 * @param adSystems systems in which this method will search. In processor we use value of idm.sec.extras.configuration.cross.adSystems
	 * @param userDn    dn of user for which we will search
	 * @return Set of groups DN
	 */
	@Override
	public Set<String> getAllUsersGroups(List<UUID> adSystems, String userDn, String userSid) {
		IcObjectClass groupClass = new IcObjectClassImpl("__GROUP__");
		Set<String> userGroups = new HashSet<>();
		adSystems.forEach(adSystem -> {
			SysSystemDto adSystemDto = systemService.get(adSystem);
			userGroups.addAll(findUserGroupsOnSystem(adSystemDto, groupClass, userDn, userSid));
		});
		return userGroups;
	}

	private Set<String> findUserGroupsOnSystem(SysSystemDto system, IcObjectClass objectClass, String dn, String userSid) {
		Set<String> result = new HashSet<>();
		// Find connector identification persisted in system
		if (system.getConnectorKey() == null) {
			throw new ProvisioningException(AccResultCode.CONNECTOR_KEY_FOR_SYSTEM_NOT_FOUND,
					ImmutableMap.of("system", system.getName()));
		}
		// load connector configuration
		IcConnectorConfiguration connectorConfig = systemService.getConnectorConfiguration(system);
		if (connectorConfig == null) {
			throw new ProvisioningException(AccResultCode.CONNECTOR_CONFIGURATION_FOR_SYSTEM_NOT_FOUND,
					ImmutableMap.of("system", system.getName()));
		}
		//
		List<IcFilter> filters = new ArrayList<>();

		IcAttribute membersAttribute = new IcAttributeImpl("member", dn);
		IcFilter dnFilter = new IcEqualsFilter(membersAttribute);
		filters.add(dnFilter);

		IcConfigurationProperty defaultPeopleContainer = connectorConfig.getConfigurationProperties().getProperties()
				.stream()
				.filter(icConfigurationProperty -> icConfigurationProperty.getName().equals("defaultPeopleContainer"))
				.findFirst()
				.orElse(null);

		if (defaultPeopleContainer != null) {
			String peopleContainer = (String) defaultPeopleContainer.getValue();
			String systemDc = peopleContainer.substring(peopleContainer.indexOf("DC="));

			IcAttribute membersSidAttribute = new IcAttributeImpl("member", "CN=" + userSid + ",CN=ForeignSecurityPrincipals," + systemDc);
			IcFilter sidDnFilter = new IcEqualsFilter(membersSidAttribute);
			filters.add(sidDnFilter);
		}

		IcFilter orFilter = new IcOrFilter(filters);
		connectorFacade.search(system.getConnectorInstance(), connectorConfig, objectClass, orFilter, connectorObject -> {
			IcAttribute groupDn = connectorObject.getAttributeByName("__NAME__");
			if (groupDn != null && !StringUtils.isEmpty(groupDn.getValue())) {
				result.add(String.valueOf(groupDn.getValue()));
			}
			return true;
		});
		return result;
	}
}
