# External Decisioning Example

This project is the integration piece to the external decisioning example.  External decisioning is an extension of the human task concept in BPMN.  Sometimes we
don't want to include everyone as part of the system yet we want to gather human decisions from these users.  This example uses the following concepts to gather
decisions from such users.

1. An Apache Camel route to expose a REST endpoint so the business process can make a decisioning request using a JSON payload with the following information:
    * Process instance ID (pid)
    * Deployment ID (deploymentId)
    * The question to ask (question)
    * The signal to send with the reply (signal)
    * Where/who to send the question to (sendTo)
2. Redis server to store the payload
3. Email server use to send the email
4. Same email server to poll the reply
5. Same Redis server to load the payload
6. JBPM Camel component to send the reply back

# Setting Up Example

1. Email Server

Go to https://james.apache.org/ to see instructions on how to stand up a example email server quickly.  The command to use is:

`docker run -p "25:25" -p "143:143" linagora/james-jpa-sample:3.5.0`

We will be using user01@james.local for the user and user02@james.local as the reply inbox.  So we send email to user01 and from that email the user will send a
reply to user02 and the Camel route will poll the inbox of user02 for the reply.

Use Thunderbird as the email client to read and reply to emails.

2. Redis

Redis is the easiest data store to set up and use for this example.  Download the latest version from https://redis.io/ and locally compile and install it.  Then
run it using

`./redis-server`

from the src folder.

3. Make sure to update the application.properties for your environment.  Start the Camel routes by

`mvn spring-boot:run`

You should see 5 routes started.

4. Use the example test-external-decision.bpmn file in the src/main/resources folder as the business process to begin the interaction.  Make sure to create a task
form to input the question and sendTo fields.  Or use a curl command to start the process.  Start the process using a question and user01@james.local as the sendTo.  The playload will be something like:

`{"question":"Is it raining outside","deploymentId":"test-notify_1.0.0-SNAPSHOT","pid":"8","signal":"answer","sendTo":"user01@james.local"}`

5. Once the request from the KIE server is received by the Camel route, a token is creatd using the payload and stored in Redis.  Then an email will be sent to user01@james.local.  That email will allow you to click on either Yes or No and send the appropriate response.  That reply will be read by another Camel route and pull the token from Redis and send the reply back to the KIE server.
