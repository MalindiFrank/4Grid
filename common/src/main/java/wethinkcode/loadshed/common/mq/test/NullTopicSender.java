package wethinkcode.loadshed.common.mq.test;

import wethinkcode.loadshed.common.mq.MqTopicSender;

import javax.jms.JMSException;

/*
  A test/no-op implementation so tests can pass a sender instance.
  Methods are intentionally empty and match the superclass signatures.
*/
public class NullTopicSender extends MqTopicSender {

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
