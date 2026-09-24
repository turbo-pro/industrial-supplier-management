package io.github.turbopro.ism.workflow;

import io.github.turbopro.ism.common.infrastructure.tenant.TenantScopedMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface WorkflowMapper extends TenantScopedMapper {
    @Select("SELECT id,tenant_id,definition_code,definition_name,business_type,draft_bpmn,status,current_version,version,updated_at FROM wf_definition WHERE tenant_id=#{tenantId} ORDER BY updated_at DESC,id DESC")
    List<WorkflowModels.DefinitionRow> definitions(long tenantId);
    @Select("SELECT id,tenant_id,definition_code,definition_name,business_type,draft_bpmn,status,current_version,version,updated_at FROM wf_definition WHERE tenant_id=#{tenantId} AND id=#{id}")
    WorkflowModels.DefinitionRow definition(long tenantId,long id);
    @Select("SELECT id,tenant_id,definition_code,definition_name,business_type,draft_bpmn,status,current_version,version,updated_at FROM wf_definition WHERE tenant_id=#{tenantId} AND definition_code=#{code}")
    WorkflowModels.DefinitionRow definitionByCode(long tenantId,String code);
    @Insert("INSERT INTO wf_definition(id,tenant_id,definition_code,definition_name,business_type,draft_bpmn,created_by,updated_by) VALUES(#{id},#{tenantId},#{code},#{name},#{businessType},#{bpmnXml},#{actorId},#{actorId})")
    int insertDefinition(long id,long tenantId,long actorId,String code,String name,String businessType,String bpmnXml);
    @Update("UPDATE wf_definition SET definition_name=#{name},business_type=#{businessType},draft_bpmn=#{bpmnXml},updated_by=#{actorId},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND version=#{version}")
    int updateDefinition(long tenantId,long actorId,long id,String name,String businessType,String bpmnXml,int version);
    @Insert("INSERT INTO wf_definition_version(id,tenant_id,definition_id,definition_version,bpmn_xml,deployment_id,process_definition_id,published_by) VALUES(#{id},#{tenantId},#{definitionId},#{definitionVersion},#{bpmnXml},#{deploymentId},#{processDefinitionId},#{actorId})")
    int insertVersion(long id,long tenantId,long actorId,long definitionId,int definitionVersion,String bpmnXml,String deploymentId,String processDefinitionId);
    @Update("UPDATE wf_definition SET status='PUBLISHED',current_version=#{definitionVersion},updated_by=#{actorId},version=version+1 WHERE tenant_id=#{tenantId} AND id=#{id} AND version=#{version}")
    int markPublished(long tenantId,long actorId,long id,int definitionVersion,int version);
    @Select("SELECT id,definition_id,definition_version,process_definition_id FROM wf_definition_version WHERE tenant_id=#{tenantId} AND definition_id=#{definitionId} AND definition_version=#{definitionVersion}")
    WorkflowModels.DefinitionVersionRow definitionVersion(long tenantId,long definitionId,int definitionVersion);
    @Insert("INSERT INTO wf_business_instance(id,tenant_id,definition_id,definition_version,process_instance_id,starter_id,business_type,business_id) VALUES(#{id},#{tenantId},#{definitionId},#{definitionVersion},#{processInstanceId},#{starterId},#{businessType},#{businessId})")
    int insertInstance(long id,long tenantId,long definitionId,int definitionVersion,String processInstanceId,long starterId,String businessType,String businessId);
    @Select("SELECT id,definition_id,definition_version,process_instance_id,starter_id,business_type,business_id,status,result,started_at,finished_at FROM wf_business_instance WHERE tenant_id=#{tenantId} AND process_instance_id=#{processInstanceId}")
    WorkflowModels.InstanceRow instanceByProcess(long tenantId,String processInstanceId);
    @Update("UPDATE wf_business_instance SET status=#{status},result=#{result},finished_at=CURRENT_TIMESTAMP(3) WHERE tenant_id=#{tenantId} AND process_instance_id=#{processInstanceId} AND status='RUNNING'")
    int finishInstance(long tenantId,String processInstanceId,String status,String result);
}
