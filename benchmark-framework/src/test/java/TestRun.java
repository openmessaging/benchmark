import io.openmessaging.benchmark.Benchmark;

public class TestRun {
    public static void main(String[] args) throws Exception {
        Benchmark.main(new String[]{"--drivers", "driver-tdengine/tdengine.yaml", "workloads/td-test.yaml"});
    }
}
