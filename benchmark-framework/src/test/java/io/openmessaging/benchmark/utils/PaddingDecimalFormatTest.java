package io.openmessaging.benchmark.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class PaddingDecimalFormatTest {

    @Test
    void format() {
        PaddingDecimalFormat format = new PaddingDecimalFormat("0.0", 7);
        assertThat(format.format(1L)).isEqualTo("    1.0");
        assertThat(format.format(1000L)).isEqualTo(" 1000.0");
        assertThat(format.format(10000000L)).isEqualTo("10000000.0");
    }

}