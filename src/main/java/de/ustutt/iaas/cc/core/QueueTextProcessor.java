package de.ustutt.iaas.cc.core;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.auth.PropertiesFileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.google.common.base.Strings;

import de.ustutt.iaas.cc.TextProcessorConfiguration;

/**
 * A text processor that uses JMS to send text to a request queue and then waits
 * for the processed text on a response queue. For each text processing request,
 * a unique ID is generated that is later used to correlate responses to their
 * original request.
 * <p>
 * The text processing is realized by (one or more) workers that read from the
 * request queue and write to the response queue.
 * <p>
 * This implementation supports ActiveMQ as well as AWS SQS.
 *
 * @author hauptfn
 */
public class QueueTextProcessor implements ITextProcessor {

    private final static Logger logger = LoggerFactory.getLogger(QueueTextProcessor.class);

    private QueueSession session;
    private QueueSender sender;
    private static ConcurrentMap<String, CompletableFuture<String>> openRequests = new ConcurrentHashMap<>();
    private static final String MESSAGE_ID = "id";


    public QueueTextProcessor(TextProcessorConfiguration conf) {
        super();
        try {
            QueueConnection connection = setUpConnection();

            session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

            Queue requestQueue = session.createQueue(conf.requestQueueName);
            Queue responseQueue = session.createQueue(conf.responseQueueName);

            sender = session.createSender(requestQueue);
            QueueReceiver receiver = session.createReceiver(responseQueue);
            receiver.setMessageListener(new ML());

            connection.start();
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    private QueueConnection setUpConnection() throws JMSException {
        QueueConnectionFactory conFactory = SQSConnectionFactory.builder()
                .withRegion(Region.getRegion(Regions.EU_WEST_1))
                .withAWSCredentialsProvider(new PropertiesFileCredentialsProvider("aws.properties"))
                .build();
        return conFactory.createQueueConnection();
    }


    @Override
    public String process(String text) {
        String uuid = UUID.randomUUID().toString();
        CompletableFuture<String> completableFuture = new CompletableFuture<>();
        openRequests.put(uuid, completableFuture);

        // create and send text message
        try {
            Message msg = session.createTextMessage(text);
            msg.setStringProperty(MESSAGE_ID, uuid);
            logger.debug("Sending message {}", msg.getStringProperty(MESSAGE_ID));
            sender.send(msg);
        } catch (JMSException e) {
            e.printStackTrace();
        }

        // wait for result (i.e. for completion of future) with timeout
        try {
            return completableFuture.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
        }

        // in case of any error, return original (unprocessed) text
        return text;
    }


    /**
     * A message listener that receives response messages from a queue and then
     * completes the corresponding future.
     *
     * @author hauptfn
     */
    private static class ML implements MessageListener {
        @Override

        public void onMessage(Message message) {
            if (message instanceof TextMessage) {
                try {
                    String messageId = getMessageId(message);
                    if (messageId == null) return;

                    CompletableFuture<String> completableFuture = getCorrespondingFuture(messageId);
                    if (completableFuture == null) return;

                    // complete future remove it from map
                    completableFuture.complete(((TextMessage) message).getText());
                    openRequests.remove(messageId);
                } catch (JMSException e) {
                    e.printStackTrace();
                }
            }
        }

        private String getMessageId(Message message) throws JMSException {
            String messageId = message.getStringProperty(MESSAGE_ID);
            if (Strings.isNullOrEmpty(messageId)) {
                logger.warn("Received message without ID for correlation.");
                return null;
            }
            logger.debug("Received message {}", messageId);
            return messageId;
        }

        private CompletableFuture<String> getCorrespondingFuture(String messageId) {
            CompletableFuture<String> completableFuture = openRequests.get(messageId);
            if (completableFuture == null) {
                logger.warn("Cannot find request for response {}", messageId);
                return null;
            }
            return completableFuture;
        }

    }

}