package io.github.turbopro.ism.workflow;

import io.github.turbopro.ism.common.api.error.ApiException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BpmnPolicyValidatorTest {
    private final BpmnPolicyValidator validator=new BpmnPolicyValidator();
    @Test void acceptsDeclarativeHumanApproval(){assertDoesNotThrow(()->validator.validate("""
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="ism">
          <process id="supplier_approval" isExecutable="true">
            <startEvent id="start"/><userTask id="review" name="供应商审核" flowable:candidateUsers="1001,1002"/><endEvent id="end"/>
            <sequenceFlow id="f1" sourceRef="start" targetRef="review"/><sequenceFlow id="f2" sourceRef="review" targetRef="end"/>
          </process>
        </definitions>""","supplier_approval"));}
    @Test void rejectsExecutableCode(){ApiException error=assertThrows(ApiException.class,()->validator.validate("""
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="ism"><process id="supplier_approval" isExecutable="true"><startEvent id="start"/><serviceTask id="call"/><endEvent id="end"/></process></definitions>""","supplier_approval"));assertTrue(error.getMessage().contains("serviceTask"));}
    @Test void rejectsExternalEntities(){assertThrows(ApiException.class,()->validator.validate("""
        <!DOCTYPE definitions [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="supplier_approval" isExecutable="true"><startEvent id="start"/><endEvent id="end"/></process></definitions>""","supplier_approval"));}
    @Test void rejectsExpressionsInNodeAttributes(){assertThrows(ApiException.class,()->validator.validate("""
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"><process id="supplier_approval" isExecutable="true"><startEvent id="start"/><userTask id="review" flowable:assignee="${userId}"/><endEvent id="end"/></process></definitions>""","supplier_approval"));}
}
