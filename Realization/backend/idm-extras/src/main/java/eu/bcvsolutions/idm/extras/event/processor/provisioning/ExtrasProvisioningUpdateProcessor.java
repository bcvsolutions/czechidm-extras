package eu.bcvsolutions.idm.extras.event.processor.provisioning;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
import eu.bcvsolutions.idm.core.eav.api.service.CodeListManager;
import eu.bcvsolutions.idm.extras.config.domain.ExtrasConfiguration;
import eu.bcvsolutions.idm.extras.service.api.ExtrasCrossDomainService;
import eu.bcvsolutions.idm.ic.api.IcAttribute;
import eu.bcvsolutions.idm.ic.api.IcConnectorConfiguration;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.ic.api.IcUidAttribute;
import eu.bcvsolutions.idm.ic.impl.IcUidAttributeImpl;
import eu.bcvsolutions.idm.ic.service.api.IcConnectorFacade;

/**
 * Projspec implementation which will send more credentials in system configuration, which is needed for cross domain support
 * Provisioning - update operation
 * It's based on ProvisioningUpdateProcessor only difference is loading more config via ExtrasCrossDomainService
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
	private ExtrasCrossDomainService additionalCredentialsService;
	@Autowired
	private CodeListManager codeListManager;

	@Autowired
	public ExtrasProvisioningUpdateProcessor(
			IcConnectorFacade connectorFacade,
			SysSystemService systemService,
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
			List<UUID> adSystems = codeListManager.getItems(extrasConfiguration.getCrossAdCodeList(), null).stream()
					.map(idmCodeListItemDto -> UUID.fromString(idmCodeListItemDto.getCode()))
					.collect(Collectors.toList());
			if (adSystems.contains(system.getId())) {
				connectorConfig = additionalCredentialsService.getConfiguration(connectorConfig, adSystems);
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