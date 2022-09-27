/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openmessaging.benchmark.driver.jms;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import io.openmessaging.benchmark.driver.jms.config.JMSConfig;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.Topic;
import org.apache.bookkeeper.stats.StatsLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JMSBenchmarkDriver implements BenchmarkDriver {

    private ConnectionFactory connectionFactory;
    private Connection connection;
    private JMSConfig config;
    private BenchmarkDriver delegateForAdminOperations;

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        this.config = readConfig(configurationFile);
        log.info("JMS driver configuration: {}", writer.writeValueAsString(config));

        if (config.delegateForAdminOperationsClassName != null
                && !config.delegateForAdminOperationsClassName.isEmpty()) {
            log.info(
                    "Initializing Driver for Admin operations {}",
                    config.delegateForAdminOperationsClassName);
            try {
                delegateForAdminOperations =
                        (BenchmarkDriver)
                                Class.forName(
                                                config.delegateForAdminOperationsClassName,
                                                true,
                                                JMSBenchmarkDriver.class.getClassLoader())
                                        .getConstructor()
                                        .newInstance();
                delegateForAdminOperations.initialize(configurationFile, statsLogger);
            } catch (Throwable e) {
                log.error(
                        "Cannot created delegate driver " + config.delegateForAdminOperationsClassName, e);
                throw new IOException(e);
            }
        }

        try {
            connectionFactory = buildConnectionFactory();
            connection = connectionFactory.createConnection();
            connection.start();
        } catch (Throwable t) {
            log.error(
                    "Cannot initialize connectionFactoryClassName = " + config.connectionFactoryClassName, t);
            throw new IOException(t);
        }
    }

    private ConnectionFactory buildConnectionFactory() throws Exception {
        Class<ConnectionFactory> clazz =
                (Class<ConnectionFactory>)
                        Class.forName(
                                config.connectionFactoryClassName,
                                true,
                                Thread.currentThread().getContextClassLoader());

        // constructor with a String (like DataStax Pulsar JMS)
        try {
            Constructor<ConnectionFactory> constructor = clazz.getConstructor(String.class);
            return constructor.newInstance(config.connectionFactoryConfigurationParam);
        } catch (NoSuchMethodException ignore) {
        }

        // constructor with Properties (like Confluent Kafka)
        try {
            Constructor<ConnectionFactory> constructor = clazz.getConstructor(Properties.class);
            Properties props = new Properties();
            ObjectMapper mapper = new ObjectMapper();
            Map map =
                    mapper.readValue(new StringReader(config.connectionFactoryConfigurationParam), Map.class);
            props.putAll(map);
            return constructor.newInstance(props);
        } catch (NoSuchMethodException ignore) {
        }

        throw new RuntimeException("Cannot find a suitable constructor for " + clazz);
    }

    @Override
    public String getTopicNamePrefix() {
        if (delegateForAdminOperations != null) {
            return delegateForAdminOperations.getTopicNamePrefix();
        }
        return config.topicNamePrefix;
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        if (delegateForAdminOperations != null) {
            return delegateForAdminOperations.createTopic(topic, partitions);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        try {
            if (config.sendWithTransactions) {
                return CompletableFuture.completedFuture(
                        new JMSBenchmarkTransactionProducer(
                                connection, topic, config.use20api, config.properties));
            } else {
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination destination = session.createTopic(topic);
                return CompletableFuture.completedFuture(
                        new JMSBenchmarkProducer(session, destination, config.use20api, config.properties));
            }
        } catch (Exception err) {
            CompletableFuture<BenchmarkProducer> res = new CompletableFuture<>();
            res.completeExceptionally(err);
            return res;
        }
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(
            String topic, String subscriptionName, ConsumerCallback consumerCallback) {
        try {
            String selector =
                    config.messageSelector != null && !config.messageSelector.isEmpty()
                            ? config.messageSelector
                            : null;
            Connection connection = connectionFactory.createConnection();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic destination = session.createTopic(topic);
            MessageConsumer durableConsumer;
            if (config.use20api) {
                durableConsumer =
                        session.createSharedDurableConsumer(destination, subscriptionName, selector);
            } else {
                // in JMS 1.0 we should use session.createDurableSubscriber()
                // but it is not supported in Confluent Kafka JMS client
                durableConsumer = session.createConsumer(destination, selector);
            }
            return CompletableFuture.completedFuture(
                    new JMSBenchmarkConsumer(
                            connection, session, durableConsumer, consumerCallback, config.use20api));
        } catch (Exception err) {
            CompletableFuture<BenchmarkConsumer> res = new CompletableFuture<>();
            res.completeExceptionally(err);
            return res;
        }
    }

    @Override
    public void close() throws Exception {
        log.info("Shutting down JMS benchmark driver");

        if (connectionFactory != null && (connectionFactory instanceof AutoCloseable)) {
            ((AutoCloseable) connectionFactory).close();
        }

        log.info("JMS benchmark driver successfully shut down");

        if (delegateForAdminOperations != null) {
            delegateForAdminOperations.close();
        }
    }

    private static final ObjectMapper mapper =
            new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static JMSConfig readConfig(File configurationFile) throws IOException {
        return mapper.readValue(configurationFile, JMSConfig.class);
    }

    private static final Random random = new Random();

    private static final ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();
    private static final Logger log = LoggerFactory.getLogger(JMSBenchmarkDriver.class);
}
