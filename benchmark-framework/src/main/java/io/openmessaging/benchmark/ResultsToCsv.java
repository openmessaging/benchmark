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

package io.openmessaging.benchmark;


import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResultsToCsv {

    private static final Logger log = LoggerFactory.getLogger(ResultsToCsv.class);

    public void writeAllResultFiles(String directory) {
        try {
            File dir = new File(directory);
            File[] directoryListing = dir.listFiles();
            if (directoryListing == null) {
                throw new IllegalArgumentException("Not a directory: " + directory);
            }
            Arrays.sort(directoryListing);

            List<String> lines = new ArrayList<>();
            lines.add(
                    "topics,partitions,message-size,producers-per-topic,consumers-per-topic,"
                            + "prod-rate-min,prod-rate-avg,prod-rate-std-dev,prod-rate-max,"
                            + "con-rate-min,con-rate-avg,con-rate-std-dev,con-rate-max,");

            List<TestResult> results = new ArrayList<>();
            for (File file : directoryListing) {
                if (file.isFile() && file.getAbsolutePath().endsWith(".json")) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    TestResult tr =
                            objectMapper.readValue(new File(file.getAbsolutePath()), TestResult.class);
                    results.add(tr);
                }
            }

            List<TestResult> sortedResults =
                    results.stream()
                            .sorted(
                                    Comparator.comparing(TestResult::getMessageSize)
                                            .thenComparing(TestResult::getTopics)
                                            .thenComparing(TestResult::getPartitions))
                            .collect(Collectors.toList());
            for (TestResult tr : sortedResults) {
                lines.add(extractResults(tr));
            }

            String resultsFileName = "results-" + Instant.now().getEpochSecond() + ".csv";
            try (FileWriter writer = new FileWriter(resultsFileName)) {
                for (String str : lines) {
                    writer.write(str + System.lineSeparator());
                }
                log.info("Results extracted into CSV " + resultsFileName);
            }
        } catch (IOException e) {
            log.error("Failed creating csv file.", e);
        }
    }

    public String extractResults(TestResult tr) {
        try {
            Histogram prodRateHistogram = new Histogram(10000000, 1);
            Histogram conRateHistogram = new Histogram(10000000, 1);

            for (Double rate : tr.publishRate) {
                prodRateHistogram.recordValueWithCount(rate.longValue(), 2);
            }

            for (Double rate : tr.consumeRate) {
                conRateHistogram.recordValueWithCount(rate.longValue(), 2);
            }

            String line =
                    MessageFormat.format(
                            "{0,number,#},{1,number,#},{2,number,#},{3,number,#},{4,number,#},"
                                    + "{5,number,#},{6,number,#},{7,number,#.##},{8,number,#},"
                                    + "{9,number,#},{10,number,#},{11,number,#.##},{12,number,#}",
                            tr.topics,
                            tr.partitions,
                            tr.messageSize,
                            tr.producersPerTopic,
                            tr.consumersPerTopic,
                            prodRateHistogram.getMinNonZeroValue(),
                            prodRateHistogram.getMean(),
                            prodRateHistogram.getStdDeviation(),
                            prodRateHistogram.getMaxValue(),
                            conRateHistogram.getMinNonZeroValue(),
                            conRateHistogram.getMean(),
                            conRateHistogram.getStdDeviation(),
                            conRateHistogram.getMaxValue());

            return line;
        } catch (Exception e) {
            log.error("Error writing results csv", e);
            throw new RuntimeException(e);
        }
    }
}
