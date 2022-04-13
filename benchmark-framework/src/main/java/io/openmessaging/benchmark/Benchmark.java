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
package io.openmessaging.benchmark;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.openmessaging.benchmark.utils.PlaceHolderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.openmessaging.benchmark.worker.DistributedWorkersEnsemble;
import io.openmessaging.benchmark.worker.LocalWorker;
import io.openmessaging.benchmark.worker.Worker;

import static io.openmessaging.benchmark.utils.PlaceHolderUtils.readAndApplyPlaceholders;

public class Benchmark {

    static class Arguments {

        @Parameter(names = {"-c", "--csv"}, description = "Print results from this directory to a csv file")
        String resultsDir;

        @Parameter(names = { "-h", "--help" }, description = "Help message", help = true)
        boolean help;

        @Parameter(names = { "-d",
                "--drivers" }, description = "Drivers list. eg.: pulsar/pulsar.yaml,kafka/kafka.yaml")//, required = true)
        public List<String> drivers;

        @Parameter(names = { "-w",
                "--workers" }, description = "List of worker nodes. eg: http://1.2.3.4:8080,http://4.5.6.7:8080")
        public List<String> workers;

        @Parameter(names = { "-wf",
                "--workers-file" }, description = "Path to a YAML file containing the list of workers addresses")
        public File workersFile;

        @Parameter(names = { "-cd",
                "--consumer-driver" }, description = "Path to an alternative driver file for consumption")
        public File consumerDriver;

        @Parameter(names = { "-x", "--extra" }, description = "Allocate extra consumer workers when your backlog builds.")
        boolean extraConsumers;

        @Parameter(description = "Workloads")//, required = true)
        public List<String> workloads;
    }

    public static void main(String[] args) throws Exception {
        final Arguments arguments = new Arguments();
        JCommander jc = new JCommander(arguments);
        jc.setProgramName("messaging-benchmark");

        try {
            jc.parse(args);
        } catch (ParameterException e) {
            System.err.println(e.getMessage());
            jc.usage();
            System.exit(-1);
        }

        if (arguments.help) {
            jc.usage();
            System.exit(-1);
        }

        if(arguments.resultsDir != null) {
            ResultsToCsv r = new ResultsToCsv();
            r.writeAllResultFiles(arguments.resultsDir);
            System.exit(0);
        }

        if (arguments.workers != null && arguments.workersFile != null) {
            System.err.println("Only one between --workers and --workers-file can be specified");
            System.exit(-1);
        }

        if (arguments.workers == null && arguments.workersFile == null) {
            File defaultFile = new File("workers.yaml");
            if (defaultFile.exists()) {
                log.info("Using default worker file workers.yaml");
                arguments.workersFile = defaultFile;
            }
        }

        if (arguments.workersFile != null) {
            log.info("Reading workers list from {}", arguments.workersFile);
            arguments.workers = PlaceHolderUtils.readAndApplyPlaceholders(arguments.workersFile, Workers.class).workers;
        }

        // Dump configuration variables
        log.info("Starting benchmark with config: {}", writer.writeValueAsString(arguments));

        Map<String, Workload> workloads = new TreeMap<>();
        for (String path : arguments.workloads) {
            File file = new File(path);
            String name = file.getName().substring(0, file.getName().lastIndexOf('.'));
            Workload workload = PlaceHolderUtils.readAndApplyPlaceholders(file, Workload.class);
            workloads.put(name, workload);
        }

        log.info("Workloads: {}", writer.writeValueAsString(workloads));

        Worker worker;

        if (arguments.workers != null && !arguments.workers.isEmpty()) {
            worker = new DistributedWorkersEnsemble(arguments.workers, arguments.extraConsumers);
        } else {
            // Use local worker implementation
            worker = new LocalWorker();
        }

        workloads.forEach((workloadName, workload) -> {
            arguments.drivers.forEach(driverConfig -> {
                try {
                    File driverConfigFile = new File(driverConfig);
                    DriverConfiguration driverConfiguration = PlaceHolderUtils.readAndApplyPlaceholders(driverConfigFile,
                            DriverConfiguration.class);
                    if (arguments.consumerDriver != null) {
                        DriverConfiguration consumerDriverConfiguration = PlaceHolderUtils.readAndApplyPlaceholders(arguments.consumerDriver,
                                DriverConfiguration.class);
                        driverConfiguration.name += ":";
                        driverConfiguration.name += consumerDriverConfiguration.name;
                    }
                    log.info("--------------- WORKLOAD : {} --- DRIVER : {}---------------", workload.name,
                            driverConfiguration.name);

                    // Stop any left over workload
                    worker.stopAll();

                    try {
                        // consumer driver is only used if the worker is a DistributedWorkersEnsemble
                        worker.initializeDriver(new File(driverConfig), arguments.consumerDriver);
                    } catch (Exception e) {
                        log.error("Failed to initialize driver '{}' - Retry once", driverConfig);
                        worker.initializeDriver(new File(driverConfig), arguments.consumerDriver);
                    }

                    WorkloadGenerator generator = new WorkloadGenerator(driverConfiguration.name, workload, worker);

                    TestResult result = generator.run();

                    String fileName = String.format("%s-%s-%s.json", workloadName, driverConfiguration.name,
                            dateFormat.format(new Date()));

                    log.info("Writing test result into {}", fileName);
                    writer.writeValue(new File(fileName), result);

                    generator.close();
                } catch (Exception e) {
                    log.error("Failed to run the workload '{}' for driver '{}'", workload.name, driverConfig, e);
                } finally {
                    try {
                        worker.stopAll();
                    } catch (IOException e) {
                    }
                }
            });
        });

        worker.close();
    }

    private static final ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();

    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

    private static final Logger log = LoggerFactory.getLogger(Benchmark.class);
}
