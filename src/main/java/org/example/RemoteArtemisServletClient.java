package org.example;

import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSDestinationDefinition;
import jakarta.jms.JMSDestinationDefinitions;
import jakarta.jms.JMSException;
import jakarta.jms.Queue;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

@JMSDestinationDefinitions(
		value = {
				@JMSDestinationDefinition(
						name = "java:/queue/SomeQueue",
						interfaceName = "jakarta.jms.Queue",
						destinationName = "SomeQueue",
						properties = {"enable-amq1-prefix=true"}
				)
		}
)
@WebServlet("/remote-artemis")
public class RemoteArtemisServletClient extends HttpServlet {

	private static final Logger LOGGER = Logger.getLogger(RemoteArtemisServletClient.class.toString());

	@Inject
	private JMSContext context;

	@Resource(lookup = "java:/queue/SomeQueue")
	private Queue queue;

	// /subsystem=messaging-activemq/external-jms-queue=myExternalQueue1:add(entries=[java:jboss/exported/jms/queue/myExternalQueue1], enable-amq1-prefix=false)
	@Resource(lookup = "java:jboss/exported/jms/queue/myExternalQueue1")
	private Queue queue1;

	// /subsystem=messaging-activemq/external-jms-queue=myExternalQueue2:add(entries=[java:jboss/exported/jms/queue/myExternalQueue2], enable-amq1-prefix=true)
	@Resource(lookup = "java:jboss/exported/jms/queue/myExternalQueue2")
	private Queue queue2;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.setContentType("text/html");
		String pattern = "dd-MM-yyyy kk:mm:ss";
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
		String message = "Message " + simpleDateFormat.format(new Date());
		try (PrintWriter out = resp.getWriter()) {
			String queueName = req.getParameter("queue");
			LOGGER.info("Queue Name: " + queueName);
			if (queueName != null && "1".equalsIgnoreCase(queueName)) {
				context.createProducer().send(queue1, message);
				LOGGER.info(message + " sent to " + prettyPrint(context) + " " + prettyPrint(queue1));
			} else if (queueName != null && "2".equalsIgnoreCase(queueName)) {
				context.createProducer().send(queue2, message);
				LOGGER.info(message + " sent to " + prettyPrint(context) + " " + prettyPrint(queue2));
			} else {
				context.createProducer().send(queue, message);
				LOGGER.info(message + " sent to " + prettyPrint(context) + " " + prettyPrint(queue));
			}
			out.write(message);
		}
	}

	private String prettyPrint(Queue queue) {
		StringBuilder sb = new StringBuilder("[");
		if (queue != null) {
			try {
				String tmp = queue.getQueueName();
				sb.append("QueueName: ");
				sb.append(tmp);
			} catch (JMSException ignore) {
			}
		}
		sb.append("]");
		return sb.toString();

	}
	private String prettyPrint(JMSContext context) {
		StringBuilder sb = new StringBuilder("[");
		if (context != null && context.getMetaData() != null) {
			String sep;
			try {
				String tmp = context.getMetaData().getJMSProviderName();
				sb.append("JMSProviderName: ");
				sb.append(tmp);
				sep = ", ";
			} catch (JMSException ignore) {
				sep = "";
			}
			try {
				String tmp = context.getMetaData().getJMSVersion();
				sb.append(sep);
				sb.append("JMSVersion: ");
				sb.append(tmp);
			} catch (JMSException ignore) {
			}
		}
		sb.append("]");
		return sb.toString();
	}
}
