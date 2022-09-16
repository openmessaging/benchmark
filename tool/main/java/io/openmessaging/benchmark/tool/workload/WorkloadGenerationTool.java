package io.openmessaging.benchmark.tool.workload;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.openmessaging.benchmark.Workload;
import java.io.File;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkloadGenerationTool {

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static {
        mapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);
    }

    public static void main(String[] args) throws IOException {
        final WorkloadGenerationTool.Arguments arguments = new WorkloadGenerationTool.Arguments();
        JCommander jc = new JCommander(arguments);
        jc.setProgramName("workload-generator");

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

        // Dump configuration variables
        log.info("Starting benchmark with config: {}", mapper.writeValueAsString(arguments));

        WorkloadSetTemplate template = mapper.readValue(arguments.templateFile, WorkloadSetTemplate.class);
        List<Workload> workloads = new WorkloadGenerator(template).generate();
        for (Workload w : workloads) {
            File outputFile = null;
            try {
                outputFile = new File(arguments.outputFolder, w.name + ".yaml");
                mapper.writeValue(outputFile, w);
            } catch (IOException e) {
                log.error("Could not write file: {}", outputFile);
            }
        }
    }

    static class Arguments {
        @Parameter(names = { "-t",  "--template-file" },
                description = "Path to a YAML file containing the workload template", required = true)
        public File templateFile;

        @Parameter(names = { "-o", "--output-folder" }, description = "Output", required = true)
        public File outputFolder;

        @Parameter(names = { "-h", "--help" }, description = "Help message", help = true)
        boolean help;
    }
}
