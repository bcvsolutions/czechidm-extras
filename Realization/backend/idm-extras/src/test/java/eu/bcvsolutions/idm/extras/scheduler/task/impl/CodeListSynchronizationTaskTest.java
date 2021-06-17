package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.persistence.EntityManager;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmCodeListDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmCodeListItemDto;
import eu.bcvsolutions.idm.core.eav.api.dto.filter.IdmCodeListItemFilter;
import eu.bcvsolutions.idm.core.eav.api.service.IdmCodeListItemService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmCodeListService;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.extras.TestHelper;
import eu.bcvsolutions.idm.extras.TestResource;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

public class CodeListSynchronizationTaskTest extends AbstractIntegrationTest {

	@Autowired
	private IdmCodeListService codeListService;
	@Autowired
	private IdmCodeListItemService codeListItemService;
	@Autowired
	private LongRunningTaskManager longRunningTaskManager;
	@Autowired
	protected TestHelper helper;
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private TransactionTemplate transactionTemplate;

	@Test
	public void testImportCreateCodeList() {
		// system does not exist
		Map<String, Object> configOfLRT = new HashMap<>();
		configOfLRT.put(CodeListSynchronizationTask.PARAMETER_NAME, "NAME");
		configOfLRT.put(CodeListSynchronizationTask.PARAMETER_CODE, "nonExistingCodeList");
		configOfLRT.put(CodeListSynchronizationTask.PARAMETER_SYSTEM, UUID.randomUUID());

		CodeListSynchronizationTask lrt = new CodeListSynchronizationTask();
		lrt.init(configOfLRT);
		Optional<OperationResult> operationResult = longRunningTaskManager.executeSync(lrt);
		IdmLongRunningTaskDto task = longRunningTaskManager.getLongRunningTask(lrt);
		while (task.isRunning()) {
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		assertEquals(OperationState.NOT_EXECUTED, operationResult.get().getState());

		// create system
		SysSystemDto testCodeListSyncSystem = helper.createSystem(TestResource.TABLE_NAME, "testCodeListSyncSystem", "status", "name", "modified");

		// code list does not exist
		configOfLRT.put(CodeListSynchronizationTask.PARAMETER_SYSTEM, testCodeListSyncSystem.getId());

		lrt = new CodeListSynchronizationTask();
		lrt.init(configOfLRT);
		operationResult = longRunningTaskManager.executeSync(lrt);
		task = longRunningTaskManager.getLongRunningTask(lrt);
		while (task.isRunning()) {
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		assertEquals(OperationState.NOT_EXECUTED, operationResult.get().getState());

		String codeListCode = "testCodeList";

		IdmCodeListDto codeListDto = new IdmCodeListDto();
		codeListDto.setCode(codeListCode);
		codeListDto.setName(codeListCode);
		codeListDto = codeListService.saveInternal(codeListDto);

		// craete system
		configOfLRT.put(CodeListSynchronizationTask.PARAMETER_CODE, codeListCode);

		lrt = new CodeListSynchronizationTask();
		lrt.init(configOfLRT);
		operationResult = longRunningTaskManager.executeSync(lrt);
		task = longRunningTaskManager.getLongRunningTask(lrt);
		while (task.isRunning()) {
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		assertEquals(OperationState.NOT_EXECUTED, operationResult.get().getState());

		// insert data into test resource
		transactionTemplate.execute(transactionStatus -> {
			entityManager.createNativeQuery("INSERT INTO " + TestResource.TABLE_NAME + " (NAME) VALUES ('one'),('second'),('third');")
					.executeUpdate();
			transactionStatus.flush();
			return null;
		});

		lrt = new CodeListSynchronizationTask();
		lrt.init(configOfLRT);
		operationResult = longRunningTaskManager.executeSync(lrt);
		task = longRunningTaskManager.getLongRunningTask(lrt);
		while (task.isRunning()) {
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		assertEquals(OperationState.EXECUTED, operationResult.get().getState());

		Long count = task.getCount();
		Long total = 3L;
		assertEquals(task.getCounter(), count);
		assertEquals(total, count);

		IdmCodeListItemFilter itemFilter = new IdmCodeListItemFilter();
		itemFilter.setCodeListId(codeListDto.getId());
		List<IdmCodeListItemDto> items = codeListItemService.find(itemFilter, null).getContent();
		assertNotNull(items);
		assertEquals(3, items.size());

		// update
		lrt = new CodeListSynchronizationTask();
		lrt.init(configOfLRT);
		operationResult = longRunningTaskManager.executeSync(lrt);
		task = longRunningTaskManager.getLongRunningTask(lrt);
		while (task.isRunning()) {
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		assertEquals(OperationState.EXECUTED, operationResult.get().getState());

		count = task.getCount();
		total = 3L;
		assertEquals(task.getCounter(), count);
		assertEquals(total, count);

		itemFilter = new IdmCodeListItemFilter();
		itemFilter.setCodeListId(codeListDto.getId());
		items = codeListItemService.find(itemFilter, null).getContent();
		assertNotNull(items);
		assertEquals(3, items.size());

		// delete
		// insert data into test resource
		helper.deleteAllResourceData();

		transactionTemplate.execute(transactionStatus -> {
			entityManager.createNativeQuery("INSERT INTO " + TestResource.TABLE_NAME + " (NAME) VALUES ('one'),('second');")
					.executeUpdate();
			transactionStatus.flush();
			return null;
		});

		lrt = new CodeListSynchronizationTask();
		lrt.init(configOfLRT);
		operationResult = longRunningTaskManager.executeSync(lrt);
		task = longRunningTaskManager.getLongRunningTask(lrt);
		while (task.isRunning()) {
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		assertEquals(OperationState.EXECUTED, operationResult.get().getState());

		count = task.getCount();
		total = 2L;
		assertEquals(task.getCounter(), count);
		assertEquals(total, count);

		itemFilter = new IdmCodeListItemFilter();
		itemFilter.setCodeListId(codeListDto.getId());
		items = codeListItemService.find(itemFilter, null).getContent();
		assertNotNull(items);
		assertEquals(2, items.size());

	}

}