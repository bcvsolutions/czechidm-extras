package eu.bcvsolutions.idm.extras.service.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import eu.bcvsolutions.idm.ic.api.IcConnectorConfiguration;

/**
 * @author Roman Kucera
 */
public interface ExtrasCrossDomainService {

	IcConnectorConfiguration getConfiguration(IcConnectorConfiguration connectorConfig, List<UUID> adSystems);

	Set<String> getAllUsersGroups(List<UUID> adSystems, String userDn);
}
