package com.sentinelx.simulation.config;

import java.util.concurrent.Executor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.sentinelx.simulation.domain.SimulationLimits;
import com.sentinelx.simulation.engine.DownstreamTracker;

/**
 * Wiring for the simulation engine: a bounded async executor (one thread per
 * active run, capped by {@code sentinelx.simulation.max-concurrent-runs}) and
 * a manual-ack listener container factory for the {@link DownstreamTracker}.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(SimulationLimits.class)
public class SimulationEngineConfig {

    @Bean(name = "simulationExecutor")
    public Executor simulationExecutor(SimulationLimits limits) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("simulation-run-");
        executor.setCorePoolSize(limits.maxConcurrentRuns());
        executor.setMaxPoolSize(limits.maxConcurrentRuns());
        executor.setQueueCapacity(0); // runs beyond the cap are rejected, not queued forever
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    @Bean(name = "trackerListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> trackerListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(1);
        return factory;
    }
}
