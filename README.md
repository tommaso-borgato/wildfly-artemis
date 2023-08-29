# Prefixes `jms.topic.` on topic names and `jms.queue.` on queue names

## Useful links

- https://dev.to/thirumurthis/deploying-apache-artemis-as-container-5am
- https://github.com/apache/activemq-artemis/tree/main/artemis-docker
- https://www.mastertheboss.com/jbossas/jboss-jms/how-to-connect-wildfly-to-a-remote-activemq-artemis-server/

## Artemis 1.x

Back in Artemis 1.x prefixes were mandatory: `jms.topic.` on topic names and `jms.queue.` on queue names;
The prefix would be used to get the type (queue/topic);
WildFly statically added prefixes in the Artemis client (enable1xPrefixes=true) (see [ActiveMQJMSClient.properties](https://github.com/jbossas/jboss-eap8/blob/8.0.x/client/properties/src/main/resources/org.apache.activemq.artemis.api.jms.ActiveMQJMSClient.properties))

```text
┌────────────────────────────────────────┐
│                                        │      ┌─────────────────────────┐
│   WildFly                              │      │      Artemis 1.x        │
│                                        │      │                         │
│                   ┌──────────────────┐ │      │                         │
│                   │Artmeis client lib│ │      │                         │
│  SomeQueue        │                  │ │      │   jms.queue.SomeQueue   │
│            ──────►│   jms.queue.     │ ├─────►│                         │
│  SomeTopic        │   jms.topic.     │ │      │                         │
│                   └──────────────────┘ │      │   jms.topic.SomeTopic   │
│                                        │      │                         │
│                                        │      │                         │
│                                        │      └─────────────────────────┘
└────────────────────────────────────────┘
```

## Create Docker image

```shell
cd ~/projects
git clone https://github.com/apache/activemq-artemis
cd activemq-artemis
git status
git fetch --all --tags 
git checkout tags/2.30.0 -b localversion-2.30.0
cd artemis-distribution
mvn  -Dskip.test=true install
cd artemis-distribution/target/apache-artemis-2.30.0-bin/apache-artemis-2.30.0/
pwd
cd ~/projects/activemq-artemis/artemis-docker/
./prepare-docker.sh --from-local-dist --local-dist-path /home/tborgato/projects/activemq-artemis/artemis-distribution/target/apache-artemis-2.30.0-bin/apache-artemis-2.30.0
cd /home/tborgato/projects/activemq-artemis/artemis-distribution/target/apache-artemis-2.30.0-bin/apache-artemis-2.30.0
podman build -f ./docker/Dockerfile-centos7-11 -t artemis-centos .
```

## Run Docker image

```shell
podman run --rm -it --network=host -e AMQ_USER=artemis -e AMQ_PASSWORD=artemis --name artemis-centos artemis-centos
```

Or:

```shell
podman run --rm -it -p 61616:61616 -p 8161:8161 --name artemis-centos artemis-centos
```

Console: http://0.0.0.0:8161/console artemis/artemis

## Inspect running container

```shell
podman exec -it artemis-centos /bin/bash
cat ./etc/broker.xml
```

We have just `tcp://0.0.0.0:5445?anycastPrefix=jms.queue.;multicastPrefix=jms.topic.` for HornetQ:

```
<!-- Acceptor for every supported protocol -->
<acceptor name="artemis">tcp://0.0.0.0:61616?tcpSendBufferSize=1048576;tcpReceiveBufferSize=1048576;amqpMinLargeMessageSize=102400;protocols=CORE,AMQP,STOMP,HORNETQ,MQTT,OPENWIRE;useEpoll=true;amqpCredits=1000;amqpLowCredits=300;amqpDuplicateDetection=true;supportAdvisory=false;suppressInternalManagementObjects=false</acceptor>

<!-- AMQP Acceptor.  Listens on default AMQP port for AMQP traffic.-->
<acceptor name="amqp">tcp://0.0.0.0:5672?tcpSendBufferSize=1048576;tcpReceiveBufferSize=1048576;protocols=AMQP;useEpoll=true;amqpCredits=1000;amqpLowCredits=300;amqpMinLargeMessageSize=102400;amqpDuplicateDetection=true</acceptor>

<!-- STOMP Acceptor. -->
<acceptor name="stomp">tcp://0.0.0.0:61613?tcpSendBufferSize=1048576;tcpReceiveBufferSize=1048576;protocols=STOMP;useEpoll=true</acceptor>

<!-- HornetQ Compatibility Acceptor.  Enables HornetQ Core and STOMP for legacy HornetQ clients. -->
<acceptor name="hornetq">tcp://0.0.0.0:5445?anycastPrefix=jms.queue.;multicastPrefix=jms.topic.;protocols=HORNETQ,STOMP;useEpoll=true</acceptor>
```

## Run WF

```shell
/tmp/wildfly-30.0.0.Beta1-202308192044-7e816de9/server/bin/standalone.sh --server-config=standalone-full.xml
```

## Connect WF to AMQ

```shell
/socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=remote-artemis:add(host=localhost, port=61616)
/subsystem=messaging-activemq/remote-connector=remote-artemis-connector:add(socket-binding=remote-artemis)
/subsystem=messaging-activemq/pooled-connection-factory=remote-artemis-pcf:add(connectors=[remote-artemis-connector], entries=[java:jboss/RemoteArtemisPCF,java:jboss/exported/jms/RemoteArtemisPCF], enable-amq1-prefix=false, user=artemis, password=artemis)
/subsystem=ejb3:write-attribute(name=default-resource-adapter-name, value=remote-artemis-pcf)
/subsystem=ee/service=default-bindings:write-attribute(name=jms-connection-factory, value=java:jboss/RemoteArtemisPCF)
```

## Deploy wildfly-artemis.war

Copy `target/wildfly-artemis.war` in `standalone/deployments`

Hit URL http://localhost:8080/wildfly-artemis/remote-artemis and you will see in WildFly log:

```log
19:33:01,224 INFO  [class org.example.RemoteArtemisServletClient] (default task-1) Message 29-08-2023 19:33:01 sent to [JMSProviderName: ActiveMQ Artemis, JMSVersion: 3.1]
```

## Connect to Artemis console 

Go to http://0.0.0.0:8161/console and login with username `artemis` password `artemis`

You will see that an address with name `SomeQueue` has been automatically created on the remote broker; it contains a single anycast queue named `SomeQueue`;

**THIS MEANS THE PREFIX `jms.queue.` WAS NOT ADDED TO THE QUEUE NAME CREATED ON THE REMOTE BROKER**

## Connect to the WildFly management console 

If you connect to WildFly console and go to `Runtime --> Messaging --> Server --> Default`, you will see an address with name `jms.queue.SomeOtherQueue` has been automatically created on the embedded broker;

**THIS MEANS THE PREFIX `jms.queue.` WAS ADDED TO THE QUEUE NAME CREATED ON THE EMBEDDED BROKER**

## Connect to WildFly cli

You will see that `enable-amq1-prefix` is present on the `pooled-connection-factory` to the remote broker:

```shell
[standalone@localhost:9990 /] /subsystem=messaging-activemq/pooled-connection-factory=remote-artemis-pcf:read-resource
{
    "outcome" => "success",
    "result" => {
      ...
      "enable-amq1-prefix" => false,
      ...
    }
```

Instead, `enable-amq1-prefix` is NOT present on the `pooled-connection-factory` to the embedded broker:

```shell
[standalone@localhost:9990 /] /subsystem=messaging-activemq/server=default/pooled-connection-factory=activemq-ra:read-resource
{
    "outcome" => "success",
    "result" => {
      ...
      ...

}  }
```

