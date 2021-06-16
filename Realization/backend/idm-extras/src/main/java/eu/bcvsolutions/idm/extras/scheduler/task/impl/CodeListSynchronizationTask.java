package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.quartz.DisallowConcurrentExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.eav.domain.AccFaceType;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmCodeListDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmCodeListItemDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.filter.IdmCodeListItemFilter;
import eu.bcvsolutions.idm.core.eav.api.service.IdmCodeListItemService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmCodeListService;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.ic.api.IcAttribute;
import eu.bcvsolutions.idm.ic.api.IcConnectorConfiguration;
import eu.bcvsolutions.idm.ic.api.IcConnectorInstance;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.ic.api.IcObjectClass;
import eu.bcvsolutions.idm.ic.impl.IcObjectClassImpl;
import eu.bcvsolutions.idm.ic.service.api.IcConnectorFacade;

/**
 * This task is "replacement" for standard synchronization. Sync if code list is not supported, so there is no option how to manage
 * code lists in IdM automatically
 * <p>
 * This task has only few configuration option and that a system select box, code list code
 * When you choose the system. This task use the connection and via connector facade it will get data.
 * Thanks to this approach, this task can be universal, and we can sync code list from multiple sources e.g DB, AD, ...
 *
 * @author Roman Kučera
 */
@Component(CodeListSynchronizationTask.TASK_NAME)
@DisallowConcurrentExecution
@Description("Task for code list synchronization")
public class CodeListSynchronizationTask extends AbstractSchedulableTaskExecutor<Optional<OperationResult>> {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CodeListSynchronizationTask.class);

	public static final String TASK_NAME = "extras-code-list-sync";

	protected static final String PARAMETER_SYSTEM = "system";
	protected static final String PARAMETER_CODE = "code";
	protected static final String PARAMETER_NAME = "nameAttribute";

	private UUID system;
	private String code;
	private IdmCodeListDto codeListDto;
	private String nameAttribute;

	@Autowired
	private SysSystemService systemService;
	@Autowired
	protected IcConnectorFacade connectorFacade;
	@Autowired
	protected SysSystemMappingService systemMappingService;
	@Autowired
	private IdmCodeListItemService codeListItemService;
	@Autowired
	private IdmCodeListService codeListService;

	@Override
	public void init(Map<String, Object> properties) {
		system = getParameterConverter().toUuid(properties, PARAMETER_SYSTEM);
		code = getParameterConverter().toString(properties, PARAMETER_CODE);
		nameAttribute = getParameterConverter().toString(properties, PARAMETER_NAME);
	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String, Object> props = super.getProperties();
		props.put(PARAMETER_SYSTEM, system);
		props.put(PARAMETER_CODE, code);
		props.put(PARAMETER_NAME, nameAttribute);
		return props;
	}

	@Override
	public String getName() {
		return TASK_NAME;
	}

	@Override
	public Optional<OperationResult> process() {
		// get connector facade and load data into code list
		SysSystemDto systemDto = systemService.get(system);
		if (systemDto == null) {
			return Optional.of(new OperationResult.Builder(OperationState.NOT_EXECUTED).setCode("System with code " + system + " not found").build());
		}

		// check if code list exist, it must exist before first sync.
		codeListDto = codeListService.getByCode(code);
		if (codeListDto == null) {
			return Optional.of(new OperationResult.Builder(OperationState.NOT_EXECUTED).setCode("Code list with code " + code + " not found").build());
		}

		LOG.info("CodeListSynchronizationTask get connector details");

		IcConnectorInstance connectorInstance = systemService.getConnectorInstance(systemDto);
		IcConnectorConfiguration connectorConfiguration = systemService.getConnectorConfiguration(systemDto);

		if (connectorInstance == null || connectorConfiguration == null) {
			return Optional.of(new OperationResult.Builder(OperationState.NOT_EXECUTED).setCode("Connector instance or configuration is null").build());
		}

		IcObjectClass objectClass = new IcObjectClassImpl("__ACCOUNT__");
		List<IcConnectorObject> objects = new ArrayList<>();

		LOG.info("CodeListSynchronizationTask starting synchronization");

		connectorFacade.synchronization(connectorInstance, connectorConfiguration, objectClass, null, icSyncDelta -> {
			objects.add(icSyncDelta.getObject());
			return true;
		});

		this.counter = 0L;
		this.count = (long) objects.size();
		LOG.info("CodeListSynchronizationTask found {} items", this.count);

		// This is for safety reason. If system will return 0 items it can be danger do delete all item in code list in IdM
		if (objects.isEmpty()) {
			return Optional.of(new OperationResult.Builder(OperationState.NOT_EXECUTED).setCode("No items were returned from system. Do nothing").build());
		}

		objects.forEach(icConnectorObject -> {
			// try to find item in specific code list
			String uidValue = icConnectorObject.getUidValue();

			LOG.info("CodeListSynchronizationTask processing item {}", uidValue);

			List<IdmCodeListItemDto> items = getIdmCodeListItemByCode(uidValue);

			IcAttribute icAttributeName = icConnectorObject.getAttributeByName(nameAttribute);

			// item not exists, create one in code list
			if (items.isEmpty()) {
				createCodeListItem(uidValue, icAttributeName);
			}

			if (items.size() == 1) {
				// item with this code exist, update name of this item
				IdmCodeListItemDto idmCodeListItemDto = items.get(0);
				updateCodeListItem(icAttributeName, idmCodeListItemDto);
			}

			this.counter++;
		});

		// item is in code list but not in source, delete it from code list
		removeCodeListItems(objects);

		return Optional.of(new OperationResult.Builder(OperationState.EXECUTED).build());
	}

	private List<IdmCodeListItemDto> getIdmCodeListItemByCode(String uidValue) {
		IdmCodeListItemFilter codeListItemFilter = new IdmCodeListItemFilter();
		codeListItemFilter.setCode(uidValue);
		codeListItemFilter.setCodeListId(codeListDto.getId());
		return codeListItemService.find(codeListItemFilter, null).getContent();
	}

	private void removeCodeListItems(List<IcConnectorObject> objects) {
		LOG.info("CodeListSynchronizationTask Check if there are some items which have to be deleted from IdM");

		IdmCodeListItemFilter codeListItemFilterAllItems = new IdmCodeListItemFilter();
		codeListItemFilterAllItems.setCodeListId(codeListDto.getId());
		List<String> allItems = codeListItemService.find(codeListItemFilterAllItems, null).getContent()
				.stream()
				.map(IdmCodeListItemDto::getCode)
				.collect(Collectors.toList());

		List<String> allItemsFromSource = objects.stream()
				.map(IcConnectorObject::getUidValue)
				.collect(Collectors.toList());

		allItems.removeAll(allItemsFromSource);

		LOG.info("CodeListSynchronizationTask There are {} number of items for deletion", +allItems.size());

		List<IdmCodeListItemDto> itemsToDelete = allItems.stream()
				.map(s -> {
					List<IdmCodeListItemDto> idmCodeListItemByCode = getIdmCodeListItemByCode(s);
					if (idmCodeListItemByCode.isEmpty()) {
						LOG.info("CodeListSynchronizationTask item with code {} not found IdM so it's probably already deleted", s);
						return null;
					}
					return idmCodeListItemByCode.get(0);
				})
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

		codeListItemService.deleteAll(itemsToDelete);
		LOG.info("CodeListSynchronizationTask {} number of items was deleted", itemsToDelete.size());
	}

	private void updateCodeListItem(IcAttribute attribute, IdmCodeListItemDto idmCodeListItemDto) {
		LOG.info("CodeListSynchronizationTask Updating item with code {}", idmCodeListItemDto.getCode());
		idmCodeListItemDto.setName((String) attribute.getValue());
		idmCodeListItemDto = codeListItemService.save(idmCodeListItemDto);
		LOG.info("CodeListSynchronizationTask item with code {} updated", idmCodeListItemDto.getCode());
	}

	private void createCodeListItem(String uidValue, IcAttribute attribute) {
		LOG.info("CodeListSynchronizationTask Creating item with code {}", uidValue);
		IdmCodeListItemDto codeListItemDto = new IdmCodeListItemDto();
		codeListItemDto.setCode(uidValue);
		codeListItemDto.setName((String) attribute.getValue());
		codeListItemDto.setCodeList(codeListDto.getId());
		codeListItemService.save(codeListItemDto);
		LOG.info("CodeListSynchronizationTask item with code {} created", uidValue);
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		List<IdmFormAttributeDto> attributes = new LinkedList<>();
		IdmFormAttributeDto systemAttr = new IdmFormAttributeDto(
				PARAMETER_SYSTEM,
				PARAMETER_SYSTEM,
				PersistentType.UUID);
		systemAttr.setFaceType(AccFaceType.SYSTEM_SELECT);
		systemAttr.setPlaceholder("Choose system for synchronization");
		systemAttr.setRequired(true);
		attributes.add(systemAttr);

		IdmFormAttributeDto codeAttr = new IdmFormAttributeDto(
				PARAMETER_CODE,
				PARAMETER_CODE,
				PersistentType.TEXT);
		codeAttr.setRequired(true);
		attributes.add(codeAttr);

		IdmFormAttributeDto nameAttr = new IdmFormAttributeDto(
				PARAMETER_NAME,
				PARAMETER_NAME,
				PersistentType.TEXT);
		nameAttr.setRequired(true);
		attributes.add(nameAttr);

		return attributes;
	}
}
