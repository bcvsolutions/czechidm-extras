package eu.bcvsolutions.idm.extras.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.bcvsolutions.idm.InitTestData;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityFilter;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.ecm.api.service.AttachmentManager;
import eu.bcvsolutions.idm.core.security.api.domain.GuardedString;
import eu.bcvsolutions.idm.core.security.api.dto.LoginDto;
import eu.bcvsolutions.idm.core.security.api.service.LoginService;
import eu.bcvsolutions.idm.extras.ExtrasModuleDescriptor;
import eu.bcvsolutions.idm.rpt.api.dto.RptReportDto;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;
import eu.bcvsolutions.idm.test.api.TestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Created by peter on 24.7.19.
 */
public class RoleAssignmentReportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestHelper helper;
    @Autowired
    private RoleAssignmentReportExectutor reportExecutor;
    @Autowired
    private IdmIdentityService identityService;
    @Autowired
    private AttachmentManager attachmentManager;
    @Qualifier("objectMapper")
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private LoginService loginService;
    @Autowired
    private RoleAssignmentReportRenderer xlsxRenderer;

    @Before
    public void before() {
        // report checks authorization policies - we need to log in
        loginService.login(new LoginDto(InitTestData.TEST_ADMIN_USERNAME, new GuardedString(InitTestData.TEST_ADMIN_PASSWORD)));
        helper.enableModule(ExtrasModuleDescriptor.MODULE_ID);
    }

    @After
    public void after() {
        super.logout();
    }

    @Test
    @Transactional
    public void testDisabledIdentity() throws IOException {
        // prepare test identities
        IdmIdentityDto identityOne = helper.createIdentity();
        IdmIdentityDto identityDisabled = helper.createIdentity();
        IdmRoleDto role = helper.createRole();
        IdmIdentityContractDto primeContract = helper.getPrimeContract(identityOne);
        helper.assignRoles(primeContract, role);
        //
        // prepare report filter
        RptReportDto report = new RptReportDto(UUID.randomUUID());
        report.setExecutorName(reportExecutor.getName());
        //
        // generate report
        report = reportExecutor.generate(report);
        Assert.assertNotNull(report.getData());
        List<IdmIdentityDto> identityRoles = mapper.readValue(
                attachmentManager.getAttachmentData(report.getData()),
                new TypeReference<List<IdmIdentityDto>>() {
                });
        //
        // test
        Assert.assertTrue(identityRoles.stream().anyMatch(i -> i.equals(identityOne)));
        Assert.assertFalse(identityRoles.stream().anyMatch(i -> i.equals(identityDisabled)));
        //
        attachmentManager.deleteAttachments(report);
    }

    @Test
    @Transactional
    public void testRenderers() {
        IdmIdentityDto identity = helper.createIdentity();
        IdmIdentityDto identity2 = helper.createIdentity();
        IdmIdentityDto identity3 = helper.createIdentity();

        IdmRoleDto role = helper.createRole();
        IdmRoleDto role2 = helper.createRole();

        IdmIdentityContractDto primeContract = helper.getPrimeContract(identity);
        IdmIdentityContractDto primeContract2 = helper.getPrimeContract(identity);
        IdmIdentityContractDto primeContract3 = helper.getPrimeContract(identity);

        IdmRoleRequestDto idmRoleRequestDto = helper.assignRoles(primeContract, role);
        helper.executeRequest(idmRoleRequestDto, true, true);

        idmRoleRequestDto = helper.assignRoles(primeContract2, role, role2);
        helper.executeRequest(idmRoleRequestDto, true, true);

        idmRoleRequestDto = helper.assignRoles(primeContract3, role2);
        helper.executeRequest(idmRoleRequestDto, true, true);

        //
        // prepare report filter
        RptReportDto report = new RptReportDto(UUID.randomUUID());
        report.setExecutorName(reportExecutor.getName());
        //
        // generate report
        report = reportExecutor.generate(report);
        //
        InputStream render = xlsxRenderer.render(report);
        Assert.assertNotNull(render);
    }
}