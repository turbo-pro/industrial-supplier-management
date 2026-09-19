package io.github.turbopro.ism.bootstrap;

import io.github.turbopro.ism.bootstrap.schema.SchemaMarker;
import io.github.turbopro.ism.bootstrap.schema.SchemaMarkerMapper;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class B0InfrastructureIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("industrial_supplier")
            .withUsername("ism")
            .withPassword("ism");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private SchemaMarkerMapper schemaMarkerMapper;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Test
    void shouldMigrateDatabaseAndReadWriteThroughMyBatis() {
        String markerCode = "B0_MYBATIS_INTEGRATION";
        schemaMarkerMapper.deleteByCode(markerCode);

        assertThat(schemaMarkerMapper.insert(2L, markerCode)).isOne();

        SchemaMarker marker = schemaMarkerMapper.findByCode(markerCode);
        assertThat(marker).isNotNull();
        assertThat(marker.id()).isEqualTo(2L);
        assertThat(marker.markerCode()).isEqualTo(markerCode);
        assertThat(marker.createdAt()).isNotNull();

        assertThat(schemaMarkerMapper.deleteByCode(markerCode)).isOne();
    }

    @Test
    void shouldDeployStartAndCompleteFlowableProcess() {
        Deployment deployment = repositoryService.createDeployment()
                .addClasspathResource("processes/b0-smoke-process.bpmn20.xml")
                .name("b0-infrastructure-test")
                .deploy();

        try {
            ProcessInstance process = runtimeService.startProcessInstanceByKey("b0SmokeProcess");
            Task task = taskService.createTaskQuery()
                    .processInstanceId(process.getId())
                    .singleResult();

            assertThat(task).isNotNull();
            assertThat(task.getAssignee()).isEqualTo("b0-tester");

            taskService.complete(task.getId());
            assertThat(runtimeService.createProcessInstanceQuery()
                    .processInstanceId(process.getId())
                    .count()).isZero();
        } finally {
            repositoryService.deleteDeployment(deployment.getId(), true);
        }
    }
}
