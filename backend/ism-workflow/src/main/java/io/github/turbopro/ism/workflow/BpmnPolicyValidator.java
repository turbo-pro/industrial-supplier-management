package io.github.turbopro.ism.workflow;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Set;
import org.xml.sax.InputSource;

@Component
public class BpmnPolicyValidator {
    private static final Set<String> ALLOWED=Set.of("definitions","process","startEvent","endEvent","userTask","sequenceFlow","exclusiveGateway","parallelGateway","inclusiveGateway","documentation","conditionExpression","incoming","outgoing");
    public void validate(String xml,String expectedProcessId){
        try{
            var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            Document document=factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList processes=document.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL","process");
            if(processes.getLength()!=1)invalid("流程必须且只能包含一个 BPMN process");
            Element process=(Element)processes.item(0);
            if(!expectedProcessId.equals(process.getAttribute("id"))||!"true".equalsIgnoreCase(process.getAttribute("isExecutable")))invalid("process id 必须等于流程编码且 isExecutable=true");
            inspect(document.getDocumentElement());
            if(document.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL","startEvent").getLength()!=1)invalid("流程必须包含一个开始节点");
            if(document.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL","endEvent").getLength()<1)invalid("流程必须包含结束节点");
        }catch(ApiException e){throw e;}catch(Exception e){invalid("BPMN XML 无效: "+e.getMessage());}
    }
    private void inspect(Node node){
        if(node.getNodeType()==Node.ELEMENT_NODE){String ns=node.getNamespaceURI();String local=node.getLocalName();if(!"http://www.omg.org/spec/BPMN/20100524/MODEL".equals(ns)||!ALLOWED.contains(local))invalid("不允许的 BPMN 元素: "+local);NamedNodeMap attrs=node.getAttributes();for(int i=0;i<attrs.getLength();i++){Node a=attrs.item(i);String v=a.getNodeValue();if(v!=null&&(v.contains("${")||v.contains("#{")))invalid("节点属性不允许使用表达式");}}
        for(Node child=node.getFirstChild();child!=null;child=child.getNextSibling())inspect(child);
    }
    private void invalid(String message){throw new ApiException(CommonErrorCode.VALIDATION_FAILED,message);}
}
