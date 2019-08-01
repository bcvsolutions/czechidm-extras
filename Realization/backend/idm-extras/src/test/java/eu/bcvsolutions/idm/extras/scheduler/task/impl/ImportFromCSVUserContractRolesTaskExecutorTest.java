package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemFilter;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.extras.utils.Pair;

public class ImportFromCSVUserContractRolesTaskExecutorTest extends AbstractRoleExecutorTest {

	private static final String path = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/contractEavRolesTestFile.csv";
    
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private IdmRoleCatalogueService roleCatalogueService;
	@Autowired
	private SysRoleSystemService roleSystemService;
	
	@Test
	public void importRolesTest() {
		setPath(path, "contractEavRolesTestFile.csv");
		CHECK_NAME = "CORE-CLOSE";
		// create system
		Pair<SysSystemDto, Map<String, Object>> pair = createData();
		SysSystemDto system = pair.getFirst();
		Map<String, Object> configOfLRT = pair.getSecond();
		//
		ImportRolesFromCSVExecutor lrt = new ImportRolesFromCSVExecutor();
		lrt.init(configOfLRT);
		longRunningTaskManager.executeSync(lrt);
		IdmLongRunningTaskDto task = longRunningTaskManager.getLongRunningTask(lrt);
		while (task.isRunning()) {
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		Long count = task.getCount();
		Long total = 7L;
		Assert.assertEquals(task.getCounter(), count);
		Assert.assertEquals(total, count);
		SysRoleSystemFilter filter = new SysRoleSystemFilter();
		filter.setSystemId(system.getId());
		List<SysRoleSystemDto> content = roleSystemService.find(filter, null).getContent();
		Assert.assertEquals(7, content.size());
		//SysSystemDto something = systemService.get(content.get(0).getSystem());
		LinkedList<IdmRoleDto> roles = new LinkedList<>();
		content.forEach(r -> roles.add(roleService.get(r.getRole())));
		boolean contains = false;
		IdmRoleDto ourRole = null;
		for (IdmRoleDto role : roles) {
			if (filterContains(role)) {
				ourRole = role;
				contains = true;
				break;
			}
		}
		assertTrue(contains);
		assertNotNull(ourRole);
		//
		List<IdmRoleCatalogueDto> allByRole = roleCatalogueService.findAllByRole(ourRole.getId());
		assertEquals(1, allByRole.size());
		assertEquals(ROLE_ROW, allByRole.get(0).getName());
        
	}
	
}
