package wethinkcode.loadshed.common.mq.test;

import wethinkcode.loadshed.common.mq.MqTopicSender;

import javax.jms.JMSException;

/**
 * A no-op topic sender used by tests when they need a MqTopicSender instance but don't
 * want to start a real broker connection. Matches the API of MqTopicSender.
 */
public class NullMqSender extends MqTopicSender {

    @Override
    public MqTopicSender init(String brokerUrl) throws JMSException {
        // no-op: return this to match fluent API used in tests
        return this;
    }

    @Override
    public void send(String message) throws JMSException {
        // no-op for tests
    }

    @Override
    public void close() {
        // no-op for tests
    }
}
