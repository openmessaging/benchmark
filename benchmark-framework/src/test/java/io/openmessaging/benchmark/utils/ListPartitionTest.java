package io.openmessaging.benchmark.utils;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ListPartitionTest {

    @Test
    void partitionList() {
        List<Integer> list = asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        List<List<Integer>> lists = ListPartition.partitionList(list, 3);
        assertThat(lists).satisfies(s -> {
            assertThat(s).hasSize(3);
            assertThat(s.get(0)).isEqualTo(asList(1, 4, 7, 10));
            assertThat(s.get(1)).isEqualTo(asList(2, 5, 8));
            assertThat(s.get(2)).isEqualTo(asList(3, 6, 9));
        });
    }
}