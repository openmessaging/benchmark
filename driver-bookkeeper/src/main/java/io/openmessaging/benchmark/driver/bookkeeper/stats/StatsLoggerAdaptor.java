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
package io.openmessaging.benchmark.driver.bookkeeper.stats;


import dlshade.com.google.common.collect.Maps;
import java.util.concurrent.ConcurrentMap;
import org.apache.bookkeeper.stats.Gauge;
import org.apache.bookkeeper.stats.StatsLogger;

public class StatsLoggerAdaptor implements dlshade.org.apache.bookkeeper.stats.StatsLogger {

    private final StatsLogger statsLogger;
    private final ConcurrentMap<dlshade.org.apache.bookkeeper.stats.Gauge, Gauge> gauges;
    private final ConcurrentMap<dlshade.org.apache.bookkeeper.stats.StatsLogger, StatsLogger>
            statsLoggers;

    public StatsLoggerAdaptor(StatsLogger statsLogger) {
        this.statsLogger = statsLogger;
        this.gauges = Maps.newConcurrentMap();
        this.statsLoggers = Maps.newConcurrentMap();
    }

    @Override
    public dlshade.org.apache.bookkeeper.stats.OpStatsLogger getOpStatsLogger(String name) {
        return new OpStatsLoggerAdaptor(statsLogger.getOpStatsLogger(name));
    }

    @Override
    public dlshade.org.apache.bookkeeper.stats.Counter getCounter(String name) {
        return new CounterAdaptor(statsLogger.getCounter(name));
    }

    @Override
    public <T extends Number> void registerGauge(
            String name, dlshade.org.apache.bookkeeper.stats.Gauge<T> gauge) {
        Gauge<T> gaugeAdaptor = new GaugeAdaptor<>(gauge);
        statsLogger.registerGauge(name, gaugeAdaptor);
        gauges.put(gauge, gaugeAdaptor);
    }

    @Override
    public <T extends Number> void unregisterGauge(
            String name, dlshade.org.apache.bookkeeper.stats.Gauge<T> gauge) {
        Gauge<T> gaugeAdaptor = gauges.remove(gauge);
        if (null != gaugeAdaptor) {
            statsLogger.unregisterGauge(name, gaugeAdaptor);
        }
    }

    @Override
    public dlshade.org.apache.bookkeeper.stats.StatsLogger scope(String name) {
        StatsLogger scopedStatsLogger = statsLogger.scope(name);
        StatsLoggerAdaptor scopedAdaptor = new StatsLoggerAdaptor(scopedStatsLogger);
        statsLoggers.putIfAbsent(scopedAdaptor, scopedStatsLogger);
        return scopedAdaptor;
    }

    @Override
    public void removeScope(
            String name, dlshade.org.apache.bookkeeper.stats.StatsLogger dlShadeStatsLogger) {
        StatsLogger scopedStatsLogger = statsLoggers.remove(dlShadeStatsLogger);
        if (null != scopedStatsLogger) {
            statsLogger.removeScope(name, scopedStatsLogger);
        }
    }
}
