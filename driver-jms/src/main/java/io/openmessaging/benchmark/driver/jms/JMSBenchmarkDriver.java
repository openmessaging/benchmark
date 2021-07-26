
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.openmessaging.benchmark.driver.jms;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.Topic;

import org.apache.bookkeeper.stats.StatsLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.openmessaging.benchmark.driver.BenchmarkConsumer;
import io.openmessaging.benchmark.driver.BenchmarkDriver;
import io.openmessaging.benchmark.driver.BenchmarkProducer;
import io.openmessaging.benchmark.driver.ConsumerCallback;
import io.openmessaging.benchmark.driver.jms.config.JMSConfig;

public class JMSBenchmarkDriver implements BenchmarkDriver {


    private ConnectionFactory connectionFactory;
    private JMSConfig config;
    private String destination;
    private URLClassLoader classLoader;

    @Override
    public void initialize(File configurationFile, StatsLogger statsLogger) throws IOException {
        this.config = readConfig(configurationFile);
        log.info("JMS driver configuration: {}", writer.writeValueAsString(config));
        String jmsDriverPath = this.config.jmsDriverJar;
        File file = new File(jmsDriverPath);
        log.info("Loading JMS Driver from {}", file.getAbsolutePath());
        if (!file.isFile()) {
            throw new IOException("Cannot find file " + file.getAbsolutePath());
        }
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        classLoader = new URLClassLoader(new URL[]{file.toURI().toURL()}, previous);
        try
        {
            connectionFactory = doWithClassloader(this::buildConnectionFactory);
        } catch (Throwable t) {
            log.error("Cannot initialize connectionFactoryClassName = "+config.connectionFactoryClassName, t);
            throw new IOException(t);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private ConnectionFactory buildConnectionFactory() throws Exception {
        Class<ConnectionFactory> clazz = (Class<ConnectionFactory>) Class.forName(config.connectionFactoryClassName, true, classLoader);

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
            Map map = mapper.readValue(new StringReader(config.connectionFactoryConfigurationParam), Map.class);
            props.putAll(map);
            return constructor.newInstance(props);
        } catch (NoSuchMethodException ignore) {
        }

        throw new RuntimeException("Cannot find a suitable constructor for " + clazz);
    }

    private <V> V doWithClassloader(Callable<V> t) {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try
        {
            Thread.currentThread().setContextClassLoader(classLoader);
            return t.call();
        } catch (Exception err) {
            throw new RuntimeException(err);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Override
    public String getTopicNamePrefix() {
        return config.topicNamePrefix;
    }

    @Override
    public CompletableFuture<Void> createTopic(String topic, int partitions) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<BenchmarkProducer> createProducer(String topic) {
        return doWithClassloader( ()  -> {
            JMSContext context = connectionFactory.createContext();
            Destination destination = context.createTopic(topic);
            return CompletableFuture.completedFuture(new JMSBenchmarkProducer(connectionFactory.createContext(), destination));
        });
    }

    @Override
    public CompletableFuture<BenchmarkConsumer> createConsumer(String topic, String subscriptionName,
                    ConsumerCallback consumerCallback) {
        return doWithClassloader( ()  -> {
            JMSContext context = connectionFactory.createContext();
            Topic destination = context.createTopic(topic);
            JMSConsumer durableConsumer = context.createSharedDurableConsumer(destination, subscriptionName, config.messageSelector);
            return CompletableFuture.completedFuture(new JMSBenchmarkConsumer(context, durableConsumer, consumerCallback));
        });
    }

    @Override
    public void close() throws Exception {
        log.info("Shutting down JMS benchmark driver");

        if (connectionFactory != null && (connectionFactory instanceof AutoCloseable)) {
            doWithClassloader( ()  -> {
                ((AutoCloseable) connectionFactory).close();
                return null;
            });
        }

        if (classLoader != null) {
            classLoader.close();
        }

        log.info("JMS benchmark driver successfully shut down");
    }

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static JMSConfig readConfig(File configurationFile) throws IOException {
        return mapper.readValue(configurationFile, JMSConfig.class);
    }

    private static final Random random = new Random();

    private static final ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();
    private static final Logger log = LoggerFactory.getLogger(JMSBenchmarkProducer.class);
}
