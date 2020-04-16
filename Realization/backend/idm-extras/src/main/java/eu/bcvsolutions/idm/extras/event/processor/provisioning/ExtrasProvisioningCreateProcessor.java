package eu.bcvsolutions.idm.extras.event.processor.provisioning;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

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
import eu.bcvsolutions.idm.ic.service.api.IcConnectorFacade;

/**
 * Projspec implementation which will send more credentials in system configuration, which is needed for cross domain support
 * Provisioning - create operation
 * It's based on ProvisioningCreateProcessor only difference is loading more config via ExtrasCrossDomainService
 *
 * @author Roman Kucera
 *
 */
@Component
@Description("Executes provisioning operation on connector facade. Depends on [" + PrepareConnectorObjectProcessor.PROCESSOR_NAME + "] result operation type [CREATE].")
public class ExtrasProvisioningCreateProcessor extends AbstractProvisioningProcessor {

	public static final String PROCESSOR_NAME = "extras-provisioning-create-processor";
	private final SysProvisioningOperationService provisioningOperationService;
	private final SysSystemService systemService;

	@Autowired
	private ExtrasConfiguration extrasConfiguration;
	@Autowired
	private ExtrasCrossDomainService additionalCredentialsService;
	@Autowired
	private CodeListManager codeListManager;

	@Autowired
	public ExtrasProvisioningCreateProcessor(
			IcConnectorFacade connectorFacade,
			SysSystemService systemService,
			SysProvisioningOperationService provisioningOperationService,
			SysSystemEntityService systemEntityService) {
		super(connectorFacade, systemService, provisioningOperationService, systemEntityService, 
				ProvisioningEventType.CREATE, ProvisioningEventType.UPDATE);
		//
		Assert.notNull(provisioningOperationService);
		Assert.notNull(systemService);
		//
		this.provisioningOperationService = provisioningOperationService;
		this.systemService = systemService;
	}
	
	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}

	@Override
	public IcUidAttribute processInternal(SysProvisioningOperationDto provisioningOperation, IcConnectorConfiguration connectorConfig) {
		// get system for password policy
		SysSystemDto system = systemService.get(provisioningOperation.getSystem());
		// execute provisioning
		IcConnectorObject connectorObject = provisioningOperation.getProvisioningContext().getConnectorObject();
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
		IcUidAttribute icUid = connectorFacade.createObject(systemService.getConnectorInstance(system), connectorConfig,
				connectorObject.getObjectClass(), transformedIcAttributes);
		//
		// set connector object back to provisioning context
		provisioningOperation.getProvisioningContext().setConnectorObject(connectorObject);
		provisioningOperation = provisioningOperationService.saveOperation(provisioningOperation); // has to be first - we need to replace guarded strings before systemEntityService.save(systemEntity)
		return icUid;
	}
	
	@Override
	public boolean supports(EntityEvent<?> entityEvent) {
		if(!super.supports(entityEvent)) {
			return false;
		}
		return ProvisioningEventType.CREATE == ((ProvisioningOperation)entityEvent.getContent()).getOperationType();
	}

	@Override
	public boolean isDefaultDisabled() {
		return true;
	}
}