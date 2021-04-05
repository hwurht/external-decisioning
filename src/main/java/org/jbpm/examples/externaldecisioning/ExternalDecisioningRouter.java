package org.jbpm.examples.externaldecisioning;

import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jbpm.JBPMConstants;
import org.apache.camel.component.jsonvalidator.JsonValidationException;
import org.apache.camel.component.redis.RedisConstants;
import org.springframework.stereotype.Component;

@Component
public class ExternalDecisioningRouter extends RouteBuilder {

    @Override
    public void configure() {
        onException(JsonValidationException.class)
            .handled(true)
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
            .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
            .setBody().constant("Invalid request payload.")
            .log("Exception handled");

        restConfiguration().component("servlet");

        // listen for payload from kie server
        rest("/decision").post("/request").consumes("application/json").to("direct:validate-request");

        // poll email inbox for reply from user
        from("imap://{{email.host}}?username={{email.user}}&password={{email.pass}}&delete=false&unseen=true&delay={{email.retrieve.delay.ms}}")
            .to("seda:handle-reply");

        from("seda:handle-reply")
            // set the decision as an exchange property for use later
            .setProperty("decision", simple("${body}"))
            .log("===> token: ${header.subject}")
            .log("===> decision: ${body}")
            // retrieve token from redis
            .setHeader(RedisConstants.COMMAND, constant("HGETALL"))
            .setHeader(RedisConstants.KEY, simple("${header.subject}"))
            .to("spring-redis://{{redis.host}}:{{redis.port}}")
            .log("===> retrieved from redis: ${body}")
            // send signal back to KIE server
            .setHeader(JBPMConstants.OPERATION, constant("CamelJBPMOperationsignalEvent"))
            .setHeader(JBPMConstants.PROCESS_INSTANCE_ID, simple("${body[pid]}"))
            .setHeader(JBPMConstants.EVENT_TYPE, simple("${body[signal]}"))
            .setHeader(JBPMConstants.EVENT, simple("${exchangeProperty.decision}"))
            .toD("jbpm:{{jbpm.server.url}}?userName={{jbpm.server.user}}&password={{jbpm.server.pass}}&deploymentId=${body[deploymentId]}");

        from("direct:validate-request")
            .streamCaching()
            .to("json-validator:request-schema.json")
            // unmarshall the json payload into a map
            .unmarshal().json()
            .to("seda:handle-request?waitForTaskToComplete=Never");

        from("seda:handle-request")
            .log("---> after unmarshalling: ${body}")
            // generate a random token ID
            .process((Exchange exchange) -> {
                String tokenId = UUID.randomUUID().toString();
                exchange.setProperty("tokenId", tokenId);
            })
            .log("---> token id: ${exchangeProperty.tokenId}")
            // store token into redis
            .setHeader(RedisConstants.COMMAND, constant("HMSET"))
            .setHeader(RedisConstants.KEY, simple("${exchangeProperty.tokenId}"))
            .setHeader(RedisConstants.VALUES, body())
            .to("spring-redis://{{redis.host}}:{{redis.port}}")
            // prepare email headers
            .setProperty("from", simple("{{email.from"))
            .setHeader("from", simple("{{email.from}}"))
            .setHeader("subject", simple("${body[question]}?"))
            .setHeader("to", simple("${body[sendTo]}"))
            .setHeader("Content-Type", simple("text/html"))
            // generate email
            .to("freemarker:email-reply.ftl")
            // send email
            .to("smtp://{{email.host}}:{{email.send.port}}?username={{email.user}}&password={{email.pass}}");
    }

}
