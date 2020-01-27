package eu.bcvsolutions.idm.extras.event.processor.provisioning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.identityconnectors.common.security.GuardedString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.acc.domain.ProvisioningEventType;
import eu.bcvsolutions.idm.acc.domain.ProvisioningOperation;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningOperationDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.event.processor.provisioning.AbstractProvisioningProcessor;
import eu.bcvsolutions.idm.acc.event.processor.provisioning.PrepareConnectorObjectProcessor;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningOperationService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.event.EntityEvent;
import eu.bcvsolutions.idm.core.notification.api.service.NotificationManager;
import eu.bcvsolutions.idm.extras.config.domain.ExtrasConfiguration;
import eu.bcvsolutions.idm.extras.util.ExtrasUtils;
import eu.bcvsolutions.idm.extras.util.GuardedStringAccessor;
import eu.bcvsolutions.idm.ic.api.IcAttribute;
import eu.bcvsolutions.idm.ic.api.IcConfigurationProperty;
import eu.bcvsolutions.idm.ic.api.IcConnectorConfiguration;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.ic.api.IcUidAttribute;
import eu.bcvsolutions.idm.ic.impl.IcConfigurationPropertyImpl;
import eu.bcvsolutions.idm.ic.impl.IcUidAttributeImpl;
import eu.bcvsolutions.idm.ic.service.api.IcConnectorFacade;

/**
 * Projspec implementation which will send more credentials in system configuration, which is needed for cross domain support
 * Provisioning - update operation
 *
 * @author Roman Kucera
 */
@Component
@Description("Executes provisioning operation on connector facade. Depends on [" + PrepareConnectorObjectProcessor.PROCESSOR_NAME + "] result operation type [UPDATE].")
public class ExtrasProvisioningUpdateProcessor extends AbstractProvisioningProcessor {

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory
			.getLogger(ExtrasProvisioningUpdateProcessor.class);

	public static final String PROCESSOR_NAME = "extras-provisioning-update-processor";

	@Autowired
	private ExtrasConfiguration extrasConfiguration;
	@Autowired
	private ExtrasUtils extrasUtils;

	@Autowired
	public ExtrasProvisioningUpdateProcessor(
			IcConnectorFacade connectorFacade,
			SysSystemService systemService,
			NotificationManager notificationManager,
			SysProvisioningOperationService provisioningOperationService,
			SysSystemEntityService systemEntityService) {
		super(connectorFacade, systemService, provisioningOperationService, systemEntityService,
				ProvisioningEventType.CREATE, ProvisioningEventType.UPDATE);
	}

	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}

	@Override
	public IcUidAttribute processInternal(SysProvisioningOperationDto provisioningOperation, IcConnectorConfiguration connectorConfig) {
		String uid = provisioningOperationService.getByProvisioningOperation(provisioningOperation).getUid();
		IcUidAttribute uidAttribute = new IcUidAttributeImpl(null, uid, null);
		IcConnectorObject connectorObject = provisioningOperation.getProvisioningContext().getConnectorObject();
		if (!connectorObject.getAttributes().isEmpty()) {
			SysSystemDto system = systemService.get(provisioningOperation.getSystem());
			//
			// load more config properties if this system is cross domain AD
			List<UUID> adSystems = extrasConfiguration.getAdSystems();
			if (adSystems.contains(system.getId())) {
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
						if (icConfigurationProperty.getName().equals("user")) {
							user.set(String.valueOf(icConfigurationProperty.getValue()));
						}
						if (icConfigurationProperty.getName().equals("password")) {
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
			}

			// Transform last guarded string into classic string
			List<IcAttribute> transformedIcAttributes = transformGuardedStringToString(provisioningOperation, connectorObject.getAttributes());
			return connectorFacade.updateObject(systemService.getConnectorInstance(system), connectorConfig,
					connectorObject.getObjectClass(), uidAttribute, transformedIcAttributes);
		} else {
			// TODO: appropriate message - provisioning is not executed - attributes don't change
			// Operation was logged only. Provisioning was not executes, because attributes does'nt change.
		}
		return null;
	}

	@Override
	public boolean supports(EntityEvent<?> entityEvent) {
		if (!super.supports(entityEvent)) {
			return false;
		}
		return ProvisioningEventType.UPDATE == ((ProvisioningOperation) entityEvent.getContent()).getOperationType();
	}

	@Override
	public boolean isDefaultDisabled() {
		return true;
	}
}