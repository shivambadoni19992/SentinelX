package com.sentinelx.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import com.sentinelx.payment.service.DataMasker;

/** Guarantees that sensitive financial/session data never survives masking. */
class DataMaskerTest {

    @Test
    void ipv4KeepsOnlyFirstThreeOctets() {
        assertThat(DataMasker.maskIp("203.0.113.45")).isEqualTo("203.0.113.xxx");
        assertThat(DataMasker.maskIp("10.0.0.1")).isEqualTo("10.0.0.xxx");
    }

    @Test
    void ipv6KeepsOnlyFirstTwoHextets() {
        assertThat(DataMasker.maskIp("2001:db8:85a3:0:0:8a2e:370:7334"))
                .isEqualTo("2001:db8:****");
    }

    @Test
    void deviceIdentifierIsTruncatedAndStarred() {
        String masked = DataMasker.maskDeviceId("device-9f3ab21c");
        assertThat(masked).isEqualTo("devi****");
        assertThat(masked).doesNotContain("9f3a").doesNotContain("b21c");
    }

    @Test
    void cardLikeValuesKeepOnlyLastFour() {
        assertThat(DataMasker.maskTail("4111111111114242")).isEqualTo("****4242");
        assertThat(DataMasker.maskTail("4111-1111-1111-4242")).isEqualTo("****4242");
    }

    @Test
    void shortAndBlankValuesAreFullyMasked() {
        assertThat(DataMasker.maskDeviceId("ab")).isEqualTo("****");
        assertThat(DataMasker.maskDeviceId(" ")).isNull();
        assertThat(DataMasker.maskIp("")).isNull();
        assertThat(DataMasker.maskTail(null)).isNull();
    }

    @Test
    void maskingIsDeterministic() {
        assertThat(DataMasker.maskIp("198.51.100.7")).isEqualTo(DataMasker.maskIp("198.51.100.7"));
        assertThat(DataMasker.maskDeviceId("device-9f3ab21c"))
                .isEqualTo(DataMasker.maskDeviceId("device-9f3ab21c"));
    }
}